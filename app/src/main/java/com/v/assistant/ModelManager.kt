package com.v.assistant

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object ModelManager {
    private val main = Handler(Looper.getMainLooper())
    @Volatile var info: Gguf.Info? = null
    private val loadLock = Any()

    fun ensureLoaded(): Boolean {
        var ok = false
        synchronized(loadLock) {
            ok = if (Core.modelReady) true else loadSaved()
        }
        return ok
    }

    private fun post(r: () -> Unit) { main.post { r() } }
    private fun dir(): File = File(Core.app.filesDir, "models").apply { mkdirs() }
    fun threads(): Int = maxOf(2, minOf(6, Runtime.getRuntime().availableProcessors() - 2))

    fun loadSaved(): Boolean {
        val p = Core.modelPath ?: return false
        val f = File(p)
        if (!f.exists()) return false
        val ok = Llm.nativeLoad(p, 4096, threads())
        Core.modelReady = ok
        if (ok && info == null) info = Gguf.read(f)
        return ok
    }

    fun disconnect() {
        Llm.nativeUnload()
        Core.modelReady = false
        Core.modelPath?.let { File(it).delete() }
        Core.modelPath = null
        Core.modelName = null
        Core.visionAsked = false
        info = null
    }

    private fun queryName(uri: Uri, fallback: String): Pair<String, Long> {
        var name = fallback
        var size = -1L
        Core.app.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: fallback
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        return Pair(name, size)
    }

    private fun copy(uri: Uri, dest: File, size: Long, from: Int, to: Int, onProgress: (Int, String) -> Unit) {
        Core.app.contentResolver.openInputStream(uri)!!.use { input ->
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(1 shl 20)
                var done = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (size > 0) {
                        val pct = from + ((to - from) * done / size).toInt()
                        if (pct != last) { last = pct; post { onProgress(pct, "Copie du modèle…") } }
                    }
                }
            }
        }
    }

    fun importModel(uri: Uri, onProgress: (Int, String) -> Unit, onDone: (Boolean, String) -> Unit) {
        Thread {
            try {
                val (name, size) = queryName(uri, "model.gguf")
                post { onProgress(0, "Préparation…") }
                Llm.nativeUnload()
                Core.modelReady = false
                dir().listFiles()?.forEach { it.delete() }
                val dest = File(dir(), name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                copy(uri, dest, size, 0, 90, onProgress)
                post { onProgress(92, "Lecture des informations…") }
                val inf = Gguf.read(dest)
                if (inf == null) {
                    dest.delete()
                    post { onDone(false, "Ce fichier n'est pas un modèle GGUF valide.") }
                    return@Thread
                }
                post { onProgress(95, "Chargement en mémoire…") }
                val ok = synchronized(loadLock) { Llm.nativeLoad(dest.absolutePath, 4096, threads()) }
                if (!ok) {
                    post { onDone(false, "Chargement impossible (mémoire insuffisante ou format non pris en charge).") }
                    return@Thread
                }
                Core.modelPath = dest.absolutePath
                Core.modelName = name
                Core.visionAsked = false
                Core.modelReady = true
                info = inf
                post { onProgress(100, "Connecté"); onDone(true, "") }
            } catch (e: Throwable) {
                post { onDone(false, e.message ?: "Erreur") }
            }
        }.start()
    }

    fun importVision(uri: Uri, onProgress: (Int, String) -> Unit, onDone: (Boolean, String) -> Unit) {
        Thread {
            try {
                val (name, size) = queryName(uri, "vision.gguf")
                val dest = File(Core.app.filesDir, "vision_" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                copy(uri, dest, size, 0, 100, onProgress)
                Core.visionPath = dest.absolutePath
                Core.visionAsked = true
                post { onDone(true, "") }
            } catch (e: Throwable) {
                post { onDone(false, e.message ?: "Erreur") }
            }
        }.start()
    }
}
