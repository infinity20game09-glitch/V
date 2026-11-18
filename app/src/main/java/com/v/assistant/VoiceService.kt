package com.v.assistant

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors

class VoiceService : Service(), Stt.Listener {
    companion object {
        const val ACTION_STOP = "com.v.assistant.STOP"
        const val ACTION_TALK = "com.v.assistant.TALK"
    }

    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private var stt: Stt? = null
    private var tts: Tts? = null
    private var overlay: OverlayOrb? = null
    private var capture: ScreenCapture? = null
    private var projection: MediaProjection? = null
    private val history = ArrayList<Pair<String, String>>()
    @Volatile private var generating = false
    @Volatile private var awaitUntil = 0L
    private var started = false

    private val idleRunnable = Runnable {
        if (Core.state == VState.LISTENING && SystemClock.elapsedRealtime() >= awaitUntil) goIdle()
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "lang" -> Thread { stt?.start(Core.lang); main.post { tts?.apply() } }.start()
            "voiceName", "ttsRate", "ttsPitch" -> main.post { tts?.apply() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopAll(); return START_NOT_STICKY }
        if (intent?.action == ACTION_TALK) { main.post { if (started) activate() }; return START_NOT_STICKY }
        if (started) return START_NOT_STICKY
        started = true

        val code = intent?.getIntExtra("code", 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra("data", Intent::class.java)
        else
            @Suppress("DEPRECATION") intent?.getParcelableExtra("data")
        val hasProj = code == Activity.RESULT_OK && data != null

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (hasProj) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        try {
            startForeground(1, buildNotification(), type)
        } catch (e: Throwable) {
            Log.e("V", "startForeground", e)
            started = false
            stopSelf()
            return START_NOT_STICKY
        }
        Core.serviceRunning = true

        if (hasProj) {
            try {
                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projection = mpm.getMediaProjection(code, data!!)
                capture = ScreenCapture(this, projection!!).also { it.start() }
            } catch (e: Throwable) {
                Log.e("V", "projection", e)
            }
        }

        overlay = OverlayOrb(this)
        tts = Tts(this) { onSpeakingDone() }
        stt = Stt(this, this).also { it.start(Core.lang) }
        Core.prefs.registerOnSharedPreferenceChangeListener(prefListener)
        Thread { ModelManager.ensureLoaded() }.start()
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("v", "V", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, VoiceService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "v")
            .setSmallIcon(R.drawable.ic_stat_v)
            .setContentTitle("V")
            .setContentText("À l'écoute…")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(0, "Quitter", stop).build())
            .build()
    }

    // ---- reconnaissance vocale ----
    override fun onPartial(t: String) { main.post { handlePartial(t) } }
    override fun onFinal(t: String) { main.post { handleFinal(t) } }

    private fun handlePartial(t: String) {
        if (Core.state == VState.IDLE) {
            if (Wake.find(t) != null) activate()
        } else if (Core.state == VState.LISTENING) {
            awaitUntil = SystemClock.elapsedRealtime() + 4000
            main.removeCallbacks(idleRunnable)
            main.postDelayed(idleRunnable, 4100)
        }
    }

    private fun handleFinal(t: String) {
        val st = Core.state
        if (st == VState.THINKING || st == VState.SPEAKING) return
        val rest = Wake.find(t)
        if (rest != null) {
            if (rest.trim().length >= 2) ask(rest.trim()) else activate()
            return
        }
        if (st == VState.LISTENING && SystemClock.elapsedRealtime() < awaitUntil) ask(t)
    }

    private fun activate() {
        Core.state = VState.LISTENING
        awaitUntil = SystemClock.elapsedRealtime() + 4000
        if (!Core.appVisible) overlay?.show()
        main.removeCallbacks(idleRunnable)
        main.postDelayed(idleRunnable, 4100)
    }

    private fun goIdle() {
        Core.state = VState.IDLE
        overlay?.hide()
    }

    // ---- cerveau ----
    private fun ask(q: String) {
        Core.state = VState.THINKING
        if (!Core.appVisible) overlay?.show()
        main.removeCallbacks(idleRunnable)
        history.add("user" to q)
        generating = true
        exec.execute { runTurn() }
    }

    private fun systemPrompt(): String =
        if (Core.lang == "en")
            "You are V, a local voice assistant. Answer briefly (1 to 3 sentences) in plain spoken language, with no markdown, no lists and no emojis. Reply in the user's language."
        else
            "Tu es V, un assistant vocal local. Réponds brièvement (1 à 3 phrases) en langage parlé, sans markdown, sans listes, sans émojis. Réponds dans la langue de l'utilisateur."

    private fun runTurn() {
        try {
            if (!ModelManager.ensureLoaded()) {
                speakPart(
                    if (Core.modelPath == null) "Aucun modèle n'est connecté. Glisse vers la gauche pour ouvrir les paramètres et en ajouter un."
                    else "Le modèle n'a pas pu être chargé. Ferme d'autres applications et réessaie."
                )
                return
            }
            val roles = ArrayList<String>()
            val contents = ArrayList<String>()
            roles.add("system"); contents.add(systemPrompt())
            var recent = history.takeLast(10)
            if (recent.isNotEmpty() && recent[0].first != "user") recent = recent.drop(1)
            for (m in recent) { roles.add(m.first); contents.add(m.second) }

            val sb = StringBuilder()
            val full = StringBuilder()
            val r = Llm.nativeGenerate(
                roles.toTypedArray(), contents.toTypedArray(), 384, 0.7f,
                TokenCallback { p -> full.append(p); sb.append(p); flush(sb, false); true }
            )
            flush(sb, true)
            if (r.startsWith("ERR:")) {
                Log.e("V", "llm $r")
                if (full.isEmpty()) speakPart("Une erreur est survenue avec le modèle.")
            }
            history.add("assistant" to full.toString().trim())
        } catch (e: Throwable) {
            Log.e("V", "turn", e)
        } finally {
            generating = false
            main.post { if (tts?.busy() != true) finishTurn() }
        }
    }

    private fun flush(sb: StringBuilder, force: Boolean) {
        while (true) {
            var cut = -1
            for (i in 11 until sb.length) {
                val c = sb[i]
                if (c == '.' || c == '!' || c == '?' || c == '…' || c == '\n') { cut = i; break }
            }
            if (cut < 0) break
            val part = sb.substring(0, cut + 1)
            sb.delete(0, cut + 1)
            speakPart(part)
        }
        if (force && sb.isNotBlank()) {
            speakPart(sb.toString())
            sb.setLength(0)
        }
    }

    private fun speakPart(text: String) {
        if (Core.state != VState.IDLE) Core.state = VState.SPEAKING
        tts?.speak(text)
    }

    private fun onSpeakingDone() { main.post { if (!generating) finishTurn() } }

    private fun finishTurn() {
        if (generating) return
        Core.state = VState.LISTENING
        awaitUntil = SystemClock.elapsedRealtime() + 4000
        main.removeCallbacks(idleRunnable)
        main.postDelayed(idleRunnable, 4100)
    }

    // ---- arrêt ----
    private fun stopAll() {
        try { Llm.nativeStop() } catch (e: Throwable) {}
        try { Core.prefs.unregisterOnSharedPreferenceChangeListener(prefListener) } catch (e: Throwable) {}
        Thread { stt?.stop(); stt = null }.start()
        tts?.shutdown(); tts = null
        capture?.stop(); capture = null
        overlay?.hide()
        Core.serviceRunning = false
        Core.state = VState.IDLE
        started = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (started) stopAll()
        super.onDestroy()
    }
}
