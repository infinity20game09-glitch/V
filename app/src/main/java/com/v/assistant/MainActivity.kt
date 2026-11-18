package com.v.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var orb: OrbView
    private lateinit var detector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        orb = OrbView(this)
        root.addView(orb, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        orb.setOnClickListener {
            if (Core.serviceRunning) {
                startService(Intent(this, VoiceService::class.java).setAction(VoiceService.ACTION_TALK))
            }
        }
        setContentView(root)

        detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val w = resources.displayMetrics.widthPixels
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (e1.x > w * 0.40f && e1.x < w * 0.95f && dx < -w * 0.20f &&
                    abs(dx) > abs(dy) * 1.5f && vx < -600f
                ) {
                    openSettings()
                    return true
                }
                return false
            }
        })

        if (!Core.prefs.getBoolean("hintShown", false)) {
            Core.prefs.edit().putBoolean("hintShown", true).apply()
            Toast.makeText(this, "Glisse vers la gauche pour ouvrir les paramètres.", Toast.LENGTH_LONG).show()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    override fun onStart() {
        super.onStart()
        Core.appVisible = true
        startFlow()
    }

    override fun onStop() {
        Core.appVisible = false
        super.onStop()
    }

    private fun startFlow() {
        if (Core.serviceRunning) return
        val need = ArrayList<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1) else afterPermissions()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                afterPermissions()
            else
                Toast.makeText(this, "Le micro est nécessaire pour V.", Toast.LENGTH_LONG).show()
        }
    }

    private fun afterPermissions() {
        if (Core.serviceRunning) return
        if (Core.screenShare) {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), 2)
        } else {
            launchService(0, null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2) launchService(resultCode, data)
    }

    private fun launchService(code: Int, data: Intent?) {
        val i = Intent(this, VoiceService::class.java)
        i.putExtra("code", code)
        if (data != null) i.putExtra("data", data)
        startForegroundService(i)
        if (!Core.overlayAsked && !Settings.canDrawOverlays(this)) {
            Core.overlayAsked = true
            Toast.makeText(this, "Autorise l'affichage par-dessus les autres applis pour voir V en bas de l'écran.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }
}
