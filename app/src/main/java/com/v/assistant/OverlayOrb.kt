package com.v.assistant

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager

/** Affiche l'orbe en bas de l'écran, par-dessus les autres applications (comme Siri). */
class OverlayOrb(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var view: OrbView? = null

    fun show() {
        main.post {
            if (view != null || !Settings.canDrawOverlays(ctx)) return@post
            val v = OrbView(ctx, true)
            val h = (208 * ctx.resources.displayMetrics.density).toInt()
            val lp = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.BOTTOM
            try { wm.addView(v, lp); view = v } catch (e: Throwable) {}
        }
    }

    fun hide() {
        main.post {
            view?.let { try { wm.removeView(it) } catch (e: Throwable) {} }
            view = null
        }
    }
}
