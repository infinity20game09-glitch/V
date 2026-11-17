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
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var orb: OrbView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val d = resources.displayMetrics.density

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        orb = OrbView(this)
        root.addView(orb, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        orb.setOnClickListener {
            if (Core.serviceRunning) {
                startService(Intent(this, VoiceService::class.java).setAction(VoiceService.ACTION_TALK))
            }
        }

        val gear = GearView(this)
        val size = (52 * d).toInt()
        val lp = FrameLayout.LayoutParams(size, size)
        lp.leftMargin = (12 * d).toInt()
        lp.topMargin = (36 * d).toInt()
        root.addView(gear, lp)
        gear.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        setContentView(root)
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
