package org.terrachemica.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.os.SystemClock
import org.terrachemica.app.core.EarthEvolutionModel
import org.terrachemica.app.core.AtmosphericWeatherState
import kotlin.math.min

/** Conceptual globe renderer; weather visuals are driven by the reduced local column model. */
class EarthGlobeView(context: Context) : View(context) {
    private val spherePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val landPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.8f)
        color = Color.argb(50, 210, 235, 255)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var currentEon = EarthEvolutionModel.Eon.HADEAN
    private var ageLabel = ""
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
        ageLabel = when {
            snapshot.lookbackMa >= 1000 -> "${fmt(snapshot.lookbackMa / 1000.0)} Ga sebelum sekarang"
            else -> "${fmt(snapshot.lookbackMa)} Ma sebelum sekarang"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(5, 13, 25))
        val cx = width / 2f
        val cy = height / 2f - dp(4f)
        val radius = min(width * 0.43f, height * 0.43f).coerceAtLeast(dp(75f))

        val rimColor = when (currentEon) {
            EarthEvolutionModel.Eon.HADEAN -> Color.rgb(255, 125, 60)
            EarthEvolutionModel.Eon.ARCHAEAN -> Color.rgb(80, 180, 160)
            EarthEvolutionModel.Eon.PROTEROZOIC -> Color.rgb(75, 170, 210)
            EarthEvolutionModel.Eon.PHANEROZOIC -> Color.rgb(70, 155, 235)
        }
        glowPaint.shader = RadialGradient(cx, cy, radius * 1.22f,
            intArrayOf(Color.argb(70, Color.red(rimColor), Color.green(rimColor), Color.blue(rimColor)), Color.TRANSPARENT),
            floatArrayOf(0.74f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 1.22f, glowPaint)
        glowPaint.shader = null

        val oceanColor = when (currentEon) {
            EarthEvolutionModel.Eon.HADEAN -> Color.rgb(76, 39, 30)
            EarthEvolutionModel.Eon.ARCHAEAN -> Color.rgb(17, 72, 91)
            EarthEvolutionModel.Eon.PROTEROZOIC -> Color.rgb(14, 85, 125)
            EarthEvolutionModel.Eon.PHANEROZOIC -> Color.rgb(12, 82, 142)
        }
        spherePaint.shader = RadialGradient(cx - radius * 0.28f, cy - radius * 0.36f, radius * 1.35f,
            intArrayOf(Color.rgb(120, 165, 190), oceanColor, Color.rgb(3, 13, 30)),
            floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, spherePaint)
        spherePaint.shader = null

        val clip = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
        val save = canvas.save()
        canvas.clipPath(clip)
        drawStylizedLand(canvas, cx, cy, radius)
        drawGrid(canvas, cx, cy, radius)
        if (weatherEnabled && weatherState != null) drawStorm(canvas, cx, cy, radius)
        canvas.restoreToCount(save)
        if (weatherEnabled && weatherState != null) postInvalidateDelayed(45L)

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.5f)
            color = Color.argb(155, 145, 205, 245)
        }
        canvas.drawCircle(cx, cy, radius, outline)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(12f)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(ageLabel, cx, cy + radius + dp(24f), labelPaint)
    }

    /** Renders clouds, rain, and lightning from the local atmospheric weather-column state. */
    private fun drawStorm(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val state = weatherState ?: return
        val t = SystemClock.uptimeMillis()
        val coverage = state.cloudFraction.coerceIn(0.0, 1.0).toFloat()
        val rainRate = state.precipitationRateMmPerHour.coerceAtLeast(0.0).toFloat()
        val convection = state.convectionIndex.coerceIn(0.0, 1.0).toFloat()
        if (coverage > 0.06f) {
            val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val stormDarkness = (rainRate / 12f).coerceIn(0f, 1f)
                val red = (76f - 32f * stormDarkness).toInt()
                val green = (91f - 35f * stormDarkness).toInt()
                val blue = (112f - 30f * stormDarkness).toInt()
                color = Color.rgb(red, green, blue)
                alpha = (105f + 140f * coverage).toInt().coerceIn(0, 245)
                style = Paint.Style.FILL
            }
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(132, 151, 168)
                alpha = (75f + 90f * coverage).toInt().coerceIn(0, 190)
            }
            val drift = kotlin.math.sin(t / 2100.0).toFloat() * r * 0.035f
            val widthFactor = 0.24f + 0.30f * coverage
            val x = cx + r * 0.12f + drift
            val y = cy - r * (0.48f - 0.04f * convection)
            val cloudR = r * (0.10f + 0.12f * coverage)
            canvas.drawOval(x-r*widthFactor, y-cloudR*0.03f, x+r*widthFactor, y+cloudR*0.80f, cloudPaint)
            canvas.drawCircle(x-r*widthFactor*0.55f, y, cloudR*0.90f, cloudPaint)
            canvas.drawCircle(x-r*widthFactor*0.12f, y-cloudR*0.45f, cloudR*1.12f, cloudPaint)
            canvas.drawCircle(x+r*widthFactor*0.48f, y-cloudR*0.05f, cloudR*0.94f, cloudPaint)
            canvas.drawOval(x-r*widthFactor*0.65f, y-cloudR*0.68f, x+r*widthFactor*0.36f, y+cloudR*0.10f, highlightPaint)

            // Rain is emitted only when the model's condensation/precipitation budget is positive.
            if (rainRate > 0.025f) {
                val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(150, 215, 250)
                    alpha = (120f + 110f * (rainRate / 12f).coerceIn(0f, 1f)).toInt()
                    strokeWidth = dp(1.25f + (rainRate / 10f).coerceIn(0f, 1f))
                    strokeCap = Paint.Cap.ROUND
                }
                val dropCount = (10 + rainRate * 2.7f + coverage * 12f).toInt().coerceIn(8, 52)
                val speed = 0.10f + (rainRate / 10f).coerceIn(0f, 1f) * 0.55f
                for (i in 0 until dropCount) {
                    val phase = (((t % 1800L).toFloat() / 1800f * (0.45f + speed)) + i * 0.173f) % 1f
                    val column = (i % 13) / 13f
                    val row = i / 13
                    val rx = x-r*widthFactor*0.94f + column*r*widthFactor*1.88f
                    val baseY = y+cloudR*0.48f + row*r*0.075f
                    val ry = baseY + phase*r*0.55f
                    canvas.drawLine(rx, ry, rx-r*0.018f, ry+r*0.075f, rainPaint)
                }
            }

            // The bolt is an event pulse from the electrical-charge budget, not a periodic decoration.
            val flashAge = t - lastLightningAtUptimeMs
            if (flashAge in 0L..260L) {
                val bolt = Path().apply {
                    moveTo(x+r*0.035f, y+cloudR*0.15f)
                    lineTo(x-r*0.035f, y+r*0.31f)
                    lineTo(x+r*0.075f, y+r*0.29f)
                    lineTo(x-r*0.005f, y+r*0.58f)
                    lineTo(x+r*0.205f, y+r*0.25f)
                    lineTo(x+r*0.09f, y+r*0.27f)
                    close()
                }
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(115, 190, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = dp(8f)
                    alpha = 120
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(bolt, glow)
                val flash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(255, 250, 208)
                    alpha = (255f * (1f - flashAge / 300f)).toInt().coerceIn(0, 255)
                }
                canvas.drawPath(bolt, flash)
                val flashAlpha = (54f * (1f - flashAge / 300f)).toInt().coerceIn(0, 54)
                canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    alpha = flashAlpha
                })
            }
        }
    }

    private fun drawStylizedLand(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val landColor = when (currentEon) {
            EarthEvolutionModel.Eon.HADEAN -> Color.rgb(196, 91, 48)
            EarthEvolutionModel.Eon.ARCHAEAN -> Color.rgb(104, 142, 103)
            EarthEvolutionModel.Eon.PROTEROZOIC -> Color.rgb(106, 157, 115)
            EarthEvolutionModel.Eon.PHANEROZOIC -> Color.rgb(108, 174, 107)
        }
        landPaint.color = landColor
        landPaint.style = Paint.Style.FILL
        landPaint.alpha = 220
        val patches = when (currentEon) {
            EarthEvolutionModel.Eon.HADEAN -> listOf(
                floatArrayOf(-0.47f, -0.35f, 0.30f, 0.18f),
                floatArrayOf(0.12f, 0.05f, 0.34f, 0.25f),
                floatArrayOf(-0.16f, 0.46f, 0.30f, 0.13f)
            )
            EarthEvolutionModel.Eon.ARCHAEAN -> listOf(
                floatArrayOf(-0.48f, -0.30f, 0.27f, 0.16f),
                floatArrayOf(-0.05f, 0.10f, 0.24f, 0.16f),
                floatArrayOf(0.37f, -0.25f, 0.24f, 0.13f),
                floatArrayOf(0.22f, 0.46f, 0.22f, 0.10f)
            )
            EarthEvolutionModel.Eon.PROTEROZOIC -> listOf(
                floatArrayOf(-0.44f, -0.28f, 0.35f, 0.18f),
                floatArrayOf(0.04f, -0.02f, 0.28f, 0.24f),
                floatArrayOf(0.42f, -0.28f, 0.22f, 0.17f),
                floatArrayOf(-0.10f, 0.45f, 0.25f, 0.12f)
            )
            EarthEvolutionModel.Eon.PHANEROZOIC -> listOf(
                floatArrayOf(-0.48f, -0.28f, 0.30f, 0.19f),
                floatArrayOf(-0.13f, -0.43f, 0.34f, 0.18f),
                floatArrayOf(0.18f, 0.05f, 0.27f, 0.22f),
                floatArrayOf(0.46f, -0.24f, 0.18f, 0.12f),
                floatArrayOf(-0.17f, 0.46f, 0.18f, 0.11f)
            )
        }
        patches.forEachIndexed { index, p ->
            val path = Path()
            val x = cx + p[0] * r
            val y = cy + p[1] * r
            val w = p[2] * r
            val h = p[3] * r
            path.moveTo(x - w * 0.50f, y - h * 0.05f)
            path.cubicTo(x - w * 0.40f, y - h * 0.80f, x + w * 0.25f, y - h * 0.85f, x + w * 0.50f, y - h * 0.10f)
            path.cubicTo(x + w * 0.30f, y + h * 0.80f, x - w * 0.24f, y + h * 0.88f, x - w * 0.50f, y - h * 0.05f)
            path.close()
            landPaint.alpha = if (index % 2 == 0) 220 else 180
            canvas.drawPath(path, landPaint)
        }
        landPaint.alpha = 255
    }

    private fun drawGrid(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        for (fraction in listOf(-0.66f, -0.33f, 0f, 0.33f, 0.66f)) {
            val y = cy + fraction * r
            val half = kotlin.math.sqrt((1f - fraction * fraction).coerceAtLeast(0f)) * r
            canvas.drawOval(cx - half, y - r * 0.10f, cx + half, y + r * 0.10f, gridPaint)
        }
        canvas.drawOval(cx - r * 0.32f, cy - r, cx + r * 0.32f, cy + r, gridPaint)
        canvas.drawOval(cx - r * 0.68f, cy - r, cx + r * 0.68f, cy + r, gridPaint)
        canvas.drawLine(cx - r, cy, cx + r, cy, gridPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)
}
