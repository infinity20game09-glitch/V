package com.v.assistant

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

enum class VState { IDLE, LISTENING, THINKING, SPEAKING }

class VApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Core.init(this)
    }
}

object Core {
    lateinit var app: Context
    lateinit var prefs: SharedPreferences

    @Volatile var state = VState.IDLE
    @Volatile var level = 0f
    @Volatile var appVisible = false
    @Volatile var serviceRunning = false
    @Volatile var modelReady = false
    @Volatile var ttsActive = false

    fun init(c: Context) {
        app = c.applicationContext
        prefs = app.getSharedPreferences("v", Context.MODE_PRIVATE)
    }

    var lang: String
        get() = prefs.getString("lang", "fr") ?: "fr"
        set(v) { prefs.edit().putString("lang", v).apply() }
    var voiceName: String?
        get() = prefs.getString("voiceName", null)
        set(v) { prefs.edit().putString("voiceName", v).apply() }
    var ttsRate: Float
        get() = prefs.getFloat("ttsRate", 1.0f)
        set(v) { prefs.edit().putFloat("ttsRate", v).apply() }
    var ttsPitch: Float
        get() = prefs.getFloat("ttsPitch", 1.0f)
        set(v) { prefs.edit().putFloat("ttsPitch", v).apply() }
    var modelPath: String?
        get() = prefs.getString("modelPath", null)
        set(v) { prefs.edit().putString("modelPath", v).apply() }
    var modelName: String?
        get() = prefs.getString("modelName", null)
        set(v) { prefs.edit().putString("modelName", v).apply() }
    var wakeOverride: String?
        get() = prefs.getString("wakeOverride", null)
        set(v) { prefs.edit().putString("wakeOverride", v).apply() }
    var visionAsked: Boolean
        get() = prefs.getBoolean("visionAsked", false)
        set(v) { prefs.edit().putBoolean("visionAsked", v).apply() }
    var visionPath: String?
        get() = prefs.getString("visionPath", null)
        set(v) { prefs.edit().putString("visionPath", v).apply() }
    var screenShare: Boolean
        get() = prefs.getBoolean("screenShare", true)
        set(v) { prefs.edit().putBoolean("screenShare", v).apply() }
    var overlayAsked: Boolean
        get() = prefs.getBoolean("overlayAsked", false)
        set(v) { prefs.edit().putBoolean("overlayAsked", v).apply() }

    fun aiAllow(key: String) = prefs.getBoolean("allow_$key", false)
    fun setAiAllow(key: String, v: Boolean) { prefs.edit().putBoolean("allow_$key", v).apply() }
}
