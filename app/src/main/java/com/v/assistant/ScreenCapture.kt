package com.v.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

class ScreenCapture(private val ctx: Context, private val mp: MediaProjection) {
    private var reader: ImageReader? = null
    private var vd: VirtualDisplay? = null
    private val ht = HandlerThread("v-capture").apply { start() }
    private val handler = Handler(ht.looper)
    private var stopped = false
    private var lastT = 0L

    @Volatile var lastFrame: Bitmap? = null

    fun start() {
        val dm = ctx.resources.displayMetrics
        val scale = minOf(1f, 720f / dm.widthPixels)
        val w = (dm.widthPixels * scale).toInt()
        val h = (dm.heightPixels * scale).toInt()
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { release() }
        }, handler)
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        vd = mp.createVirtualDisplay(
            "v-capture", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler
        )
        r.setOnImageAvailableListener({ rd ->
            val img = rd.acquireLatestImage()
            if (img != null) {
                try {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastT >= 1500 && Core.screenShare) {
                        lastT = now
                        val plane = img.planes[0]
                        val rowPad = plane.rowStride - plane.pixelStride * w
                        val bmp = Bitmap.createBitmap(w + rowPad / plane.pixelStride, h, Bitmap.Config.ARGB_8888)
                        bmp.copyPixelsFromBuffer(plane.buffer)
                        lastFrame = Bitmap.createBitmap(bmp, 0, 0, w, h)
                        bmp.recycle()
                    }
                } finally {
                    img.close()
                }
            }
        }, handler)
    }

    private fun release() {
        if (stopped) return
        stopped = true
        try { vd?.release() } catch (e: Throwable) {}
        try { reader?.close() } catch (e: Throwable) {}
        ht.quitSafely()
    }

    fun stop() {
        release()
        try { mp.stop() } catch (e: Throwable) {}
    }
}
