package com.fpsmonitor.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Real-time dynamic canvas rendering frame times and FPS waveform.
 * Visualizes frame pacing, micro-stutters, and lag spikes with color gradients.
 */
class FpsWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dataPoints = ArrayList<Float>()
    private val maxDataPoints = 60

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private val path = Path()
    private val fillPath = Path()

    fun updateData(frameTimes: List<Float>) {
        dataPoints.clear()
        val takeCount = min(frameTimes.size, maxDataPoints)
        val startIndex = frameTimes.size - takeCount
        for (i in startIndex until frameTimes.size) {
            dataPoints.add(frameTimes[i])
        }
        postInvalidate()
    }

    fun addDataPoint(frameTimeMs: Float) {
        if (dataPoints.size >= maxDataPoints) {
            dataPoints.removeAt(0)
        }
        dataPoints.add(frameTimeMs)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()

        // Reference lines (16.6ms / 60fps line, 8.3ms / 120fps line)
        val line60FpsY = h - (16.67f / 50f * h).coerceIn(0f, h)
        val line120FpsY = h - (8.33f / 50f * h).coerceIn(0f, h)

        canvas.drawLine(0f, line60FpsY, w, line60FpsY, gridPaint)
        canvas.drawLine(0f, line120FpsY, w, line120FpsY, gridPaint)

        if (dataPoints.size < 2) return

        path.reset()
        fillPath.reset()

        val stepX = w / (maxDataPoints - 1)
        val maxScaleMs = 40.0f // 40ms max on vertical axis

        var startX = 0f
        var startY = h

        for (i in dataPoints.indices) {
            val ft = dataPoints[i]
            val x = i * stepX
            val y = (h - (ft / maxScaleMs * h)).coerceIn(0f, h)

            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
                startX = x
                startY = y
            } else {
                val prevX = (i - 1) * stepX
                val prevFt = dataPoints[i - 1]
                val prevY = (h - (prevFt / maxScaleMs * h)).coerceIn(0f, h)

                // Smooth cubic bezier curve
                val controlX = (prevX + x) / 2
                path.cubicTo(controlX, prevY, controlX, y, x, y)
                fillPath.cubicTo(controlX, prevY, controlX, y, x, y)
            }
        }

        val lastX = (dataPoints.size - 1) * stepX
        fillPath.lineTo(lastX, h)
        fillPath.close()

        // Gradient for fill
        val lastFt = dataPoints.lastOrNull() ?: 16.6f
        val colorPrimary = when {
            lastFt <= 12f -> Color.rgb(0, 230, 118) // Green (smooth > 80 FPS)
            lastFt <= 20f -> Color.rgb(255, 214, 0) // Yellow (normal 50-60 FPS)
            lastFt <= 33.3f -> Color.rgb(255, 145, 0) // Orange (minor lag)
            else -> Color.rgb(255, 23, 68) // Red (stutter < 30 FPS)
        }

        linePaint.color = colorPrimary
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.argb(100, Color.red(colorPrimary), Color.green(colorPrimary), Color.blue(colorPrimary)),
            Color.argb(10, Color.red(colorPrimary), Color.green(colorPrimary), Color.blue(colorPrimary)),
            Shader.TileMode.CLAMP
        )

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}
