package com.v.assistant

import android.app.Application

class VApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Core.init(this)
    }
}
