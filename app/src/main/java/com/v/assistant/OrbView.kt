package com.v.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** Cercle vert uni, sans rien autour. Il clignote et pulse avec la voix. */
class OrbView(ctx: Context, private val compact: Boolean = false) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val t0 = SystemClock.elapsedRealtime()
    private var lvl = 0f

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = if (compact) h * 0.58f else h / 2f
        val t = (SystemClock.elapsedRealtime() - t0) / 1000f
        val st = Core.state

        val target = when (st) {
            VState.SPEAKING -> abs(sin(t * 7f)) * 0.55f + abs(sin(t * 13.3f + 1f)) * 0.3f + 0.1f
            VState.THINKING -> 0.2f + 0.12f * sin(t * 4f)
            else -> Core.level
        }
        lvl += (target - lvl) * 0.3f
        val l = lvl.coerceIn(0f, 1f)
        val active = st != VState.IDLE
        val breath = 0.5f + 0.5f * sin(t * 1.6f)

        val base = min(w, h) * (if (compact) 0.20f else 0.17f)
        val r: Float
        val alpha: Float
        if (active) {
            r = base * (0.85f + 0.5f * l)
            alpha = 0.6f + 0.4f * l          // le cercle « clignote » avec la voix
        } else {
            r = base * (0.78f + 0.05f * breath + 0.3f * l)
            alpha = 0.5f + 0.12f * breath + 0.3f * l
        }

        fill.color = Color.argb((alpha * 255).toInt().coerceIn(0, 255), 0, 230, 118)
        c.drawCircle(cx, cy, r, fill)
        fill.color = Color.argb((alpha * 90).toInt().coerceIn(0, 255), 190, 255, 220)
        c.drawCircle(cx - r * 0.18f, cy - r * 0.18f, r * 0.55f, fill)

        postInvalidateOnAnimation()
    }
}
