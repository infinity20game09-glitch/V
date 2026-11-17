#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include "llama.h"

#define TAG "VCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static int g_nctx = 4096;
static int g_threads = 4;
static std::atomic<bool> g_stop(false);
static bool g_inited = false;

// Retourne seulement les caractères UTF-8 complets (jette les octets invalides et les emojis 4 octets)
static std::string take_valid(std::string & pending) {
    std::string out;
    size_t i = 0, n = pending.size();
    while (i < n) {
        unsigned char c = (unsigned char) pending[i];
        size_t len;
        if (c < 0x80) len = 1;
        else if ((c >> 5) == 0x6) len = 2;
        else if ((c >> 4) == 0xE) len = 3;
        else if ((c >> 3) == 0x1E) len = 4;
        else { i++; continue; }
        if (i + len > n) break;
        bool ok = true;
        for (size_t k = 1; k < len; k++) {
            if ((((unsigned char) pending[i + k]) & 0xC0) != 0x80) { ok = false; break; }
        }
        if (ok && len < 4) out.append(pending, i, len);
        i += ok ? len : 1;
    }
    pending.erase(0, i);
    return out;
}

static std::string jstr(JNIEnv * env, jstring s) {
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string r(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return r;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_v_assistant_Llm_nativeLoad(JNIEnv * env, jobject, jstring jpath, jint nctx, jint threads) {
    std::lock_guard<std::mutex> lk(g_mutex);
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    if (!g_inited) { llama_backend_init(); g_inited = true; }
    std::string path = jstr(env, jpath);
    g_nctx = nctx;
    g_threads = threads;
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(path.c_str(), mp);
    if (!g_model) { LOGE("echec chargement %s", path.c_str()); return JNI_FALSE; }
    LOGI("modele charge: %s", path.c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_v_assistant_Llm_nativeUnload(JNIEnv *, jobject) {
    g_stop = true;
    std::lock_guard<std::mutex> lk(g_mutex);
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_v_assistant_Llm_nativeStop(JNIEnv *, jobject) { g_stop = true; }

extern "C" JNIEXPORT jstring JNICALL
Java_com_v_assistant_Llm_nativeGenerate(JNIEnv * env, jobject, jobjectArray roles, jobjectArray contents,
                                        jint maxTokens, jfloat temp, jobject cb) {
    std::lock_guard<std::mutex> lk(g_mutex);
    g_stop = false;
    if (!g_model) return env->NewStringUTF("ERR:no-model");

    jsize count = env->GetArrayLength(roles);
    std::vector<std::string> rs, cs;
    size_t total = 0;
    for (jsize i = 0; i < count; i++) {
        jstring r = (jstring) env->GetObjectArrayElement(roles, i);
        jstring c = (jstring) env->GetObjectArrayElement(contents, i);
        rs.push_back(jstr(env, r));
        cs.push_back(jstr(env, c));
        total += cs.back().size() + rs.back().size();
        env->DeleteLocalRef(r);
        env->DeleteLocalRef(c);
    }
    std::vector<llama_chat_message> msgs;
    for (size_t i = 0; i < rs.size(); i++) msgs.push_back({rs[i].c_str(), cs[i].c_str()});

    const char * tmpl = llama_model_chat_template(g_model, nullptr);
    std::vector<char> buf(total * 2 + 2048);
    int32_t n = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), (int32_t) buf.size());
    if (n < 0) {
        tmpl = "chatml";
        n = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), (int32_t) buf.size());
    }
    if (n > (int32_t) buf.size()) {
        buf.resize(n + 16);
        n = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), (int32_t) buf.size());
    }
    if (n < 0) return env->NewStringUTF("ERR:template");
    std::string prompt(buf.data(), n);

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    int nt = -llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), nullptr, 0, true, true);
    if (nt <= 0) return env->NewStringUTF("ERR:tokenize");
    std::vector<llama_token> toks(nt);
    if (llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), toks.data(), (int32_t) toks.size(), true, true) < 0)
        return env->NewStringUTF("ERR:tokenize");
    if ((int) toks.size() >= g_nctx - 32) return env->NewStringUTF("ERR:prompt-too-long");

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = g_nctx;
    cp.n_batch = g_nctx;
    cp.n_threads = g_threads;
    cp.n_threads_batch = g_threads;
    llama_context * ctx = llama_init_from_model(g_model, cp);
    if (!ctx) return env->NewStringUTF("ERR:context");

    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    jclass cls = env->GetObjectClass(cb);
    jmethodID mid = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)Z");

    int limit = maxTokens;
    if (limit > g_nctx - (int) toks.size() - 8) limit = g_nctx - (int) toks.size() - 8;

    std::string pending, all;
    llama_token id = 0;
    llama_batch batch = llama_batch_get_one(toks.data(), (int32_t) toks.size());
    int generated = 0;
    bool stop = false;
    std::string err;

    while (generated < limit && !stop && !g_stop) {
        if (llama_decode(ctx, batch) != 0) { err = "ERR:decode"; break; }
        id = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;
        char piece[256];
        int pn = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, true);
        if (pn < 0) break;
        pending.append(piece, pn);
        std::string chunk = take_valid(pending);
        if (!chunk.empty()) {
            all += chunk;
            jstring js = env->NewStringUTF(chunk.c_str());
            jboolean cont = env->CallBooleanMethod(cb, mid, js);
            env->DeleteLocalRef(js);
            if (env->ExceptionCheck()) { env->ExceptionClear(); stop = true; }
            if (!cont) stop = true;
        }
        batch = llama_batch_get_one(&id, 1);
        generated++;
    }

    llama_sampler_free(smpl);
    llama_free(ctx);
    if (!err.empty()) return env->NewStringUTF(err.c_str());
    return env->NewStringUTF(all.c_str());
}
