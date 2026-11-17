package com.v.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Sphère holographique 3D façon Jarvis : points, méridiens, anneaux orbitaux, pulsations audio. */
class OrbView(ctx: Context, private val compact: Boolean = false) : View(ctx) {
    private val dots = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val n = 340
    private val ux = FloatArray(n)
    private val uy = FloatArray(n)
    private val uz = FloatArray(n)
    private val t0 = SystemClock.elapsedRealtime()
    private var lvl = 0f
    private var rotY = 0f
    private val d = ctx.resources.displayMetrics.density
    private var ox = 0f
    private var oy = 0f
    private var oz = 0f

    init {
        for (i in 0 until n) {
            val y = 1f - 2f * (i + 0.5f) / n
            val r = sqrt(1f - y * y)
            val th = Math.PI * (1.0 + sqrt(5.0)) * (i + 0.5)
            ux[i] = (cos(th) * r).toFloat()
            uy[i] = y
            uz[i] = (sin(th) * r).toFloat()
        }
        glow.style = Paint.Style.FILL
    }

    private fun proj(x: Float, y: Float, z: Float, ca: Float, sa: Float, cb: Float, sb: Float) {
        val x1 = x * ca + z * sa
        val z1 = -x * sa + z * ca
        val y2 = y * cb - z1 * sb
        val z2 = y * sb + z1 * cb
        ox = x1; oy = y2; oz = z2
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = if (compact) h * 0.58f else h / 2f
        val t = (SystemClock.elapsedRealtime() - t0) / 1000f
        val st = Core.state

        val target = when (st) {
            VState.SPEAKING -> abs(sin(t * 7f)) * 0.5f + abs(sin(t * 13.3f + 1f)) * 0.3f + 0.15f
            VState.THINKING -> 0.22f + 0.1f * sin(t * 3f)
            else -> Core.level
        }
        lvl += (target - lvl) * 0.25f
        val active = st != VState.IDLE
        val bright = if (active) 1f else 0.6f
        rotY += (if (active) 0.016f else 0.005f) + lvl * 0.025f

        val base = min(w, h) * (if (compact) 0.20f else 0.24f)
        val rad = base * (1f + lvl * 0.28f)
        val ca = cos(rotY); val sa = sin(rotY)
        val cb = cos(0.4f); val sb = sin(0.4f)

        // halo
        glow.style = Paint.Style.FILL
        for (k in 0..3) {
            val a = ((16f + lvl * 40f) * bright / (k + 1)).toInt().coerceIn(0, 255)
            glow.color = Color.argb(a, 0, 229, 255)
            c.drawCircle(cx, cy, rad * (0.9f + 0.2f * k), glow)
        }

        // points de la sphère
        for (i in 0 until n) {
            proj(ux[i], uy[i], uz[i], ca, sa, cb, sb)
            val pulse = 1f + lvl * 0.35f * sin(i * 0.9f + t * 9f)
            val k = 1f + oz * 0.18f
            val sx = cx + ox * rad * pulse * k
            val sy = cy + oy * rad * pulse * k
            val depth = (oz + 1f) / 2f
            dots.color = Color.argb(((60f + 195f * depth) * bright).toInt().coerceIn(0, 255), 0, 229, 255)
            c.drawCircle(sx, sy, (0.9f + 1.8f * depth) * d, dots)
        }

        // fil de fer : latitudes et méridiens
        line.strokeWidth = 0.8f * d
        line.color = Color.argb((90 * bright).toInt(), 120, 240, 255)
        for (li in -2..2) {
            val lat = li * 0.3f
            val rr = sqrt(1f - lat * lat)
            path.reset()
            for (s in 0..48) {
                val a = s / 48f * 6.2831853f
                proj(cos(a) * rr, lat, sin(a) * rr, ca, sa, cb, sb)
                val k = 1f + oz * 0.18f
                val px = cx + ox * rad * k
                val py = cy + oy * rad * k
                if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            c.drawPath(path, line)
        }
        for (mi in 0 until 4) {
            val a0 = mi * 0.7853982f
            path.reset()
            for (s in 0..48) {
                val a = s / 48f * 6.2831853f
                proj(cos(a) * cos(a0), sin(a), cos(a) * sin(a0), ca, sa, cb, sb)
                val k = 1f + oz * 0.18f
                val px = cx + ox * rad * k
                val py = cy + oy * rad * k
                if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            c.drawPath(path, line)
        }

        // anneaux orbitaux
        for (k in 0..2) {
            val rr = rad * (1.5f + 0.22f * k) * (1f + lvl * 0.08f)
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
            line.strokeWidth = (1.2f + k * 0.4f) * d
            val dir = if (k % 2 == 0) 1f else -1f
            val ang = dir * t * (35f + 25f * k)
            line.color = Color.argb(((70f + 120f * lvl) * bright).toInt().coerceIn(0, 255), 0, 229, 255)
            c.drawArc(rect, ang, 50f + 110f * lvl, false, line)
            c.drawArc(rect, ang + 180f, 30f + 70f * lvl, false, line)
            line.color = Color.argb((28 * bright).toInt(), 0, 229, 255)
            c.drawCircle(cx, cy, rr, line)
        }

        postInvalidateOnAnimation()
    }
}

/** Roue dentée dessinée à la main (aucune ressource nécessaire). */
class GearView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 229, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val s = min(width, height).toFloat()
        val r = s * 0.26f
        p.strokeWidth = s * 0.07f
        c.drawCircle(cx, cy, r, p)
        c.drawCircle(cx, cy, r * 0.4f, p)
        for (i in 0 until 8) {
            val a = i * Math.PI / 4.0
            val x1 = (cx + cos(a) * r * 1.1).toFloat()
            val y1 = (cy + sin(a) * r * 1.1).toFloat()
            val x2 = (cx + cos(a) * r * 1.5).toFloat()
            val y2 = (cy + sin(a) * r * 1.5).toFloat()
            c.drawLine(x1, y1, x2, y2, p)
        }
    }
}
