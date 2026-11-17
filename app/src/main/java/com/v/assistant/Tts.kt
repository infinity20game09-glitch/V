package com.v.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class Tts(ctx: Context, private val onIdle: () -> Unit) : UtteranceProgressListener() {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val pending = AtomicInteger(0)
    private val main = Handler(Looper.getMainLooper())

    init {
        tts = TextToSpeech(ctx, TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(this)
                ready = true
                apply()
            }
        })
    }

    fun apply() {
        val t = tts ?: return
        if (!ready) return
        t.language = if (Core.lang == "en") Locale.US else Locale.FRANCE
        val vn = Core.voiceName
        if (vn != null) {
            try {
                t.voices?.firstOrNull { it.name == vn }?.let { t.voice = it }
            } catch (e: Throwable) {}
        }
        t.setSpeechRate(Core.ttsRate)
        t.setPitch(Core.ttsPitch)
    }

    fun busy() = pending.get() > 0

    fun speak(text: String) {
        val clean = text
            .replace(Regex("[*#`_>~|]"), " ")
            .replace(Regex("[\\p{So}\\p{Cs}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isEmpty() || !ready) return
        Core.ttsActive = true
        pending.incrementAndGet()
        tts?.speak(clean, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    fun stop() {
        pending.set(0)
        Core.ttsActive = false
        tts?.stop()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private fun finished() {
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            main.postDelayed({
                if (pending.get() == 0) {
                    Core.ttsActive = false
                    onIdle()
                }
            }, 400)
        }
    }

    override fun onStart(utteranceId: String?) {}
    override fun onDone(utteranceId: String?) { finished() }
    @Deprecated("Deprecated in Java")
    override fun onError(utteranceId: String?) { finished() }
    override fun onError(utteranceId: String?, errorCode: Int) { finished() }
}
