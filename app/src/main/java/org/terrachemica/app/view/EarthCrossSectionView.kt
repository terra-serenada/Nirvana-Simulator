package org.terrachemica.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import org.terrachemica.app.core.AtmosphericWeatherState
import org.terrachemica.app.core.EarthEvolutionModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Conceptual cutaway scene for Earth evolution.
 * Weather effects are driven by the app's single representative atmospheric column;
 * geological layers are explanatory artwork, not a solved geodynamic cross-section.
 */
class EarthCrossSectionView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(225, 238, 245)
        textSize = dp(10f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }

    private var currentEon = EarthEvolutionModel.Eon.ARCHAEAN
    private var lookbackGa = EarthEvolutionModel.ARCHAEAN_LOOKBACK_GA
    private var weatherEnabled = true
    private var weatherState: AtmosphericWeatherState? = null
    private var lastLightningEventSeen = 0L
    private var lastLightningAtUptimeMs = -10_000L

    fun setWeatherState(state: AtmosphericWeatherState?) {
        if (state != null && state.lightningEventCount > lastLightningEventSeen) {
            lastLightningEventSeen = state.lightningEventCount
            lastLightningAtUptimeMs = SystemClock.uptimeMillis()
        }
        if (state == null) lastLightningEventSeen = 0L
        weatherState = state
        invalidate()
    }

    fun setWeatherEnabled(enabled: Boolean) {
        weatherEnabled = enabled
        invalidate()
    }

    fun setSnapshot(snapshot: EarthEvolutionModel.Snapshot) {
        currentEon = snapshot.eon
        lookbackGa = snapshot.lookbackMa / 1000.0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = SystemClock.uptimeMillis()
        val eonAccent = when (currentEon) {
            EarthEvolutionModel.Eon.HADEAN -> Color.rgb(238, 112, 66)
            EarthEvolutionModel.Eon.ARCHAEAN -> Color.rgb(67, 190, 184)
            EarthEvolutionModel.Eon.PROTEROZOIC -> Color.rgb(77, 167, 215)
            EarthEvolutionModel.Eon.PHANEROZOIC -> Color.rgb(100, 177, 225)
        }

        // Upper scene: early atmosphere, sunlit haze and clouds.
        fill.shader = LinearGradient(0f, 0f, 0f, h * 0.57f,
            intArrayOf(Color.rgb(4, 15, 31), Color.rgb(12, 43, 62), Color.rgb(28, 69, 78)),
            floatArrayOf(0f, 0.60f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.60f, fill)
        fill.shader = null
        drawSun(canvas, w * 0.12f, h * 0.17f, min(w, h) * 0.055f, t)
        drawAtmosphereBands(canvas, w, h)
        if (weatherEnabled) drawClouds(canvas, w, h, t)

        // Subsurface cutaway: sea, primitive basaltic crust, mantle and core.
        drawMantle(canvas, w, h, t)
        drawOceanAndCrust(canvas, w, h, eonAccent, t)
        drawCore(canvas, w, h)
        drawLayerLabels(canvas, w, h)
        if (weatherEnabled) drawRainAndLightning(canvas, w, h, t)

        // Motion remains visible while the weather clock is active, even if geology is paused.
        if (weatherEnabled && weatherState != null) postInvalidateDelayed(40L)
    }

    private fun drawSun(canvas: Canvas, x: Float, y: Float, r: Float, time: Long) {
        val pulse = 1f + 0.035f * sin(time / 1300.0).toFloat()
        fill.shader = RadialGradient(x, y, r * 3.5f,
            intArrayOf(Color.argb(85, 255, 181, 97), Color.argb(18, 255, 161, 92), Color.TRANSPARENT),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r * 3.5f, fill)
        fill.shader = null
        fill.color = Color.rgb(255, 202, 128)
        canvas.drawCircle(x, y, r * pulse, fill)
        fill.color = Color.rgb(255, 231, 184)
        canvas.drawCircle(x - r * 0.20f, y - r * 0.20f, r * 0.48f, fill)
    }

    private fun drawAtmosphereBands(canvas: Canvas, w: Float, h: Float) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(48, 145, 216, 225)
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
        }
        canvas.drawLine(0f, h * 0.39f, w, h * 0.39f, line)
        canvas.drawLine(0f, h * 0.47f, w, h * 0.47f, line)
        drawSmallLabel(canvas, "ATMOSFERA AWAL", w * 0.50f, h * 0.075f, Color.rgb(185, 216, 225))
        drawSmallLabel(canvas, "TROPOSFERA · UAP AIR", w * 0.58f, h * 0.43f, Color.rgb(188, 220, 228))
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float, time: Long) {
        val state = weatherState ?: return
        val coverage = state.cloudFraction.coerceIn(0.0, 1.0).toFloat()
        val liquid = state.cloudLiquidWaterMm.coerceAtLeast(0.0).toFloat()
        val convection = state.convectionIndex.coerceIn(0.0, 1.0).toFloat()
        val rain = state.precipitationRateMmPerHour.coerceAtLeast(0.0).toFloat()
        if (coverage <= 0.005f && liquid <= 0.005f) return
        val drift = sin(time / 2600.0).toFloat() * w * 0.035f
        val cloudCount = when {
            coverage > 0.72f -> 4
            coverage > 0.38f -> 3
            coverage > 0.12f -> 2
            else -> 1 // Thin cloud remains visible at low modelled cloud fraction.
        }
        val alpha = (42f + coverage * 190f + min(liquid, 2f) * 10f).toInt().coerceIn(42, 220)
        val darkness = (rain / 15f).coerceIn(0f, 1f)
        val cloudColor = Color.rgb(
            (173f - darkness * 76f).toInt(),
            (194f - darkness * 72f).toInt(),
            (202f - darkness * 64f).toInt()
        )
        for (i in 0 until cloudCount) {
            val fraction = (i + 1f) / (cloudCount + 1f)
            val x = w * (0.22f + fraction * 0.62f) + drift * (if (i % 2 == 0) 1f else -0.7f)
            val y = h * (0.25f + (i % 2) * 0.075f - convection * 0.025f)
            val scale = (0.38f + coverage * 1.08f) * (if (cloudCount == 1) 0.82f else 0.74f)
            drawCloud(canvas, x, y, w * 0.12f * scale, h * 0.055f * scale, cloudColor, alpha)
        }
    }

    private fun drawCloud(canvas: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int, alpha: Int) {
        fill.color = color
        fill.alpha = alpha
        canvas.drawOval(x - rx, y - ry * 0.20f, x + rx, y + ry * 0.85f, fill)
        canvas.drawCircle(x - rx * 0.52f, y, ry * 0.86f, fill)
        canvas.drawCircle(x - rx * 0.10f, y - ry * 0.40f, ry * 1.12f, fill)
        canvas.drawCircle(x + rx * 0.48f, y - ry * 0.05f, ry * 0.91f, fill)
        fill.color = Color.rgb(220, 235, 236)
        fill.alpha = (alpha * 0.42f).toInt()
        canvas.drawOval(x - rx * 0.62f, y - ry * 0.78f, x + rx * 0.30f, y - ry * 0.10f, fill)
        fill.alpha = 255
    }

    private fun drawOceanAndCrust(canvas: Canvas, w: Float, h: Float, accent: Int, time: Long) {
        val surface = h * 0.535f
        val wave = sin(time / 1150.0).toFloat() * dp(2f)
        val sea = Path().apply {
            moveTo(0f, surface + wave)
            cubicTo(w * 0.16f, surface - wave, w * 0.34f, surface + wave, w * 0.52f, surface)
            lineTo(w * 0.52f, h * 0.66f)
            lineTo(0f, h * 0.66f)
            close()
        }
        fill.shader = LinearGradient(0f, surface, 0f, h * 0.68f,
            intArrayOf(Color.rgb(22, 106, 128), Color.rgb(5, 43, 67)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(sea, fill)
        fill.shader = null
        stroke.color = Color.argb(190, 112, 216, 220)
        stroke.strokeWidth = dp(2f)
        canvas.drawPath(Path().apply {
            moveTo(0f, surface + wave)
            cubicTo(w * 0.16f, surface - wave, w * 0.34f, surface + wave, w * 0.52f, surface)
        }, stroke)

        val land = Path().apply {
            moveTo(w * 0.49f, surface)
            cubicTo(w * 0.57f, surface - h * 0.025f, w * 0.58f, surface - h * 0.065f, w * 0.67f, surface - h * 0.055f)
            cubicTo(w * 0.75f, surface - h * 0.09f, w * 0.80f, surface - h * 0.015f, w * 0.88f, surface - h * 0.04f)
            cubicTo(w * 0.94f, surface - h * 0.035f, w * 0.96f, surface, w, surface - h * 0.01f)
            lineTo(w, h * 0.655f)
            lineTo(w * 0.49f, h * 0.655f)
            close()
        }
        fill.shader = LinearGradient(0f, surface - h * 0.08f, 0f, h * 0.72f,
            intArrayOf(Color.rgb(116, 75, 55), Color.rgb(71, 48, 44)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(land, fill)
        fill.shader = null

        // Thin, dark basaltic early crust follows the primitive land/sea boundary.
        val crust = Path().apply {
            moveTo(0f, h * 0.655f)
            cubicTo(w * 0.20f, h * 0.665f, w * 0.37f, h * 0.65f, w * 0.51f, h * 0.66f)
            cubicTo(w * 0.68f, h * 0.64f, w * 0.83f, h * 0.665f, w, h * 0.65f)
            lineTo(w, h * 0.705f)
            cubicTo(w * 0.78f, h * 0.72f, w * 0.60f, h * 0.69f, w * 0.48f, h * 0.71f)
            cubicTo(w * 0.28f, h * 0.69f, w * 0.14f, h * 0.72f, 0f, h * 0.70f)
            close()
        }
        fill.color = Color.rgb(42, 37, 39)
        canvas.drawPath(crust, fill)
        fill.color = accent
        fill.alpha = 90
        canvas.drawLine(w * 0.53f, h * 0.665f, w * 0.93f, h * 0.665f, fill)
        fill.alpha = 255
    }

    private fun drawMantle(canvas: Canvas, w: Float, h: Float, time: Long) {
        val mantle = Path().apply {
            moveTo(0f, h * 0.69f)
            cubicTo(w * 0.25f, h * 0.70f, w * 0.42f, h * 0.68f, w * 0.55f, h * 0.70f)
            cubicTo(w * 0.74f, h * 0.68f, w * 0.86f, h * 0.70f, w, h * 0.69f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        fill.shader = LinearGradient(0f, h * 0.68f, 0f, h,
            intArrayOf(Color.rgb(112, 53, 42), Color.rgb(61, 31, 35)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(mantle, fill)
        fill.shader = null

        // Slow convection markers illustrate the mantle layer; they are not solved flow vectors.
        val phase = (time % 6000L) / 6000f
        val convectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(145, 248, 151, 86)
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0..2) {
            val x = w * (0.20f + i * 0.29f)
            val y = h * (0.79f + 0.035f * sin((phase + i * 0.3f) * 6.28f))
            val path = Path().apply {
                moveTo(x - w * 0.035f, y + h * 0.05f)
                cubicTo(x - w * 0.06f, y - h * 0.01f, x + w * 0.045f, y - h * 0.02f, x + w * 0.018f, y - h * 0.07f)
            }
            canvas.drawPath(path, convectionPaint)
        }
    }

    private fun drawCore(canvas: Canvas, w: Float, h: Float) {
        val cx = w * 0.68f
        val cy = h * 1.12f
        fill.shader = RadialGradient(cx, cy, w * 0.34f,
            intArrayOf(Color.rgb(255, 194, 104), Color.rgb(197, 89, 48), Color.rgb(104, 42, 39)),
            floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP)
        canvas.drawOval(cx - w * 0.34f, cy - h * 0.46f, cx + w * 0.34f, cy + h * 0.46f, fill)
        fill.shader = null
        fill.color = Color.rgb(246, 166, 81)
        canvas.drawOval(cx - w * 0.15f, cy - h * 0.22f, cx + w * 0.15f, cy + h * 0.22f, fill)
        fill.color = Color.rgb(255, 221, 147)
        canvas.drawOval(cx - w * 0.075f, cy - h * 0.105f, cx + w * 0.075f, cy + h * 0.105f, fill)
    }

    private fun drawLayerLabels(canvas: Canvas, w: Float, h: Float) {
        drawSmallLabel(canvas, "SAMUDRA", w * 0.20f, h * 0.625f, Color.rgb(183, 231, 238))
        drawSmallLabel(canvas, "KERAK BASALTIK", w * 0.70f, h * 0.70f, Color.rgb(244, 207, 170))
        drawSmallLabel(canvas, "MANTEL", w * 0.18f, h * 0.83f, Color.rgb(255, 196, 155))
        drawSmallLabel(canvas, "INTI", w * 0.73f, h * 0.90f, Color.rgb(255, 235, 192))
        drawSmallLabel(canvas, String.format(java.util.Locale.US, "PENAMPANG SKEMATIK · %.2f Ga", lookbackGa), w * 0.50f, h * 0.985f, Color.rgb(196, 214, 225))
    }

    private fun drawRainAndLightning(canvas: Canvas, w: Float, h: Float, time: Long) {
        val state = weatherState ?: return
        val rain = state.precipitationRateMmPerHour.coerceAtLeast(0.0).toFloat()
        val coverage = state.cloudFraction.coerceIn(0.0, 1.0).toFloat()
        if (rain > 0.025f && coverage > 0.02f) {
            val count = (6 + rain * 2.1f + coverage * 18f).toInt().coerceIn(6, 42)
            val speed = 0.28f + (rain / 12f).coerceIn(0f, 1f) * 0.55f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(155, 220, 246)
                alpha = (110 + 120 * (rain / 12f).coerceIn(0f, 1f)).toInt()
                strokeWidth = dp(1.4f)
                strokeCap = Paint.Cap.ROUND
            }
            for (i in 0 until count) {
                val phase = ((time % 1700L) / 1700f * speed + i * 0.193f) % 1f
                val x = w * (0.28f + (i % 9) * 0.064f)
                val y = h * 0.37f + phase * h * 0.19f
                canvas.drawLine(x, y, x - dp(2f), y + dp(9f), paint)
            }
        }

        // A bolt appears only after the engine's event counter increments.
        val flashAge = time - lastLightningAtUptimeMs
        if (flashAge in 0L..620L) {
            val x = w * 0.67f
            val y = h * 0.30f
            val bolt = Path().apply {
                moveTo(x, y)
                lineTo(x - w * 0.035f, y + h * 0.075f)
                lineTo(x + w * 0.006f, y + h * 0.073f)
                lineTo(x - w * 0.025f, y + h * 0.16f)
                lineTo(x + w * 0.045f, y + h * 0.075f)
                lineTo(x + w * 0.009f, y + h * 0.078f)
                close()
            }
            stroke.color = Color.argb(150, 110, 188, 255)
            stroke.strokeWidth = dp(7f)
            canvas.drawPath(bolt, stroke)
            fill.color = Color.rgb(255, 248, 205)
            fill.alpha = (255f * (1f - flashAge / 680f)).toInt().coerceIn(0, 255)
            canvas.drawPath(bolt, fill)
            fill.alpha = 255
            fill.color = Color.WHITE
            fill.alpha = (26f * (1f - flashAge / 680f)).toInt().coerceIn(0, 26)
            canvas.drawRect(0f, 0f, w, h * 0.57f, fill)
            fill.alpha = 255
        }
    }

    private fun drawSmallLabel(canvas: Canvas, value: String, x: Float, y: Float, color: Int) {
        textPaint.color = color
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(9f)
        canvas.drawText(value, x, y, textPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
