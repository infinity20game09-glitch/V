package com.v.assistant

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class Stt(private val ctx: Context, private val listener: Listener) {
    interface Listener {
        fun onPartial(t: String)
        fun onFinal(t: String)
    }

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start(lang: String) {
        stop()
        running = true
        thread = Thread { run(lang) }.also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(2000)
        thread = null
    }

    private fun unpack(name: String): File {
        val dest = File(ctx.filesDir, "vosk/$name")
        val marker = File(dest, ".ok")
        if (marker.exists()) return dest
        dest.deleteRecursively()
        copyAssets(name, dest)
        marker.writeText("ok")
        return dest
    }

    private fun copyAssets(path: String, dest: File) {
        val list = ctx.assets.list(path) ?: emptyArray()
        if (list.isEmpty()) {
            try {
                dest.parentFile?.mkdirs()
                ctx.assets.open(path).use { i -> FileOutputStream(dest).use { o -> i.copyTo(o) } }
            } catch (e: java.io.FileNotFoundException) {
                dest.mkdirs()
            }
        } else {
            dest.mkdirs()
            for (c in list) copyAssets("$path/$c", File(dest, c))
        }
    }

    private fun run(lang: String) {
        var model: Model? = null
        var rec: Recognizer? = null
        var ar: AudioRecord? = null
        try {
            val dir = unpack("model-$lang")
            model = Model(dir.absolutePath)
            rec = Recognizer(model, 16000f)
            val minBuf = AudioRecord.getMinBufferSize(
                16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            ar = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, 16000) * 2
            )
            val sid = ar.audioSessionId
            try { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sid)?.enabled = true } catch (e: Throwable) {}
            try { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sid)?.enabled = true } catch (e: Throwable) {}
            try { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sid)?.enabled = true } catch (e: Throwable) {}
            ar.startRecording()

            val buf = ShortArray(2048)
            var lastPartial = ""
            var muted = false
            while (running) {
                val n = ar.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (Core.ttsActive) { muted = true; continue }
                if (muted) { rec.reset(); muted = false; lastPartial = "" }

                var sum = 0.0
                for (i in 0 until n) { val v = buf[i].toDouble(); sum += v * v }
                val rms = sqrt(sum / n) / 5000.0
                Core.level = Core.level * 0.55f + min(1f, rms.toFloat() * 2.5f) * 0.45f

                if (rec.acceptWaveForm(buf, n)) {
                    val t = JSONObject(rec.result).optString("text")
                    lastPartial = ""
                    if (t.isNotBlank()) listener.onFinal(t)
                } else {
                    val p = JSONObject(rec.partialResult).optString("partial")
                    if (p.isNotBlank() && p != lastPartial) { lastPartial = p; listener.onPartial(p) }
                }
            }
        } catch (e: Throwable) {
            Log.e("V-Stt", "erreur", e)
        } finally {
            try { ar?.stop() } catch (e: Throwable) {}
            try { ar?.release() } catch (e: Throwable) {}
            try { rec?.close() } catch (e: Throwable) {}
            try { model?.close() } catch (e: Throwable) {}
        }
    }
}
