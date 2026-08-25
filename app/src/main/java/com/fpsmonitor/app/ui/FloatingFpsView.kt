package com.fpsmonitor.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.fpsmonitor.app.R
import com.fpsmonitor.app.core.FpsData
import java.util.Locale
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class FloatingFpsView(
    private val context: Context,
    private val onCloseRequested: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rootView: View
    private val params: WindowManager.LayoutParams

    private val tvMiniFps: TextView
    private val tvMiniFrameTime: TextView
    private val miniContainer: LinearLayout
    private val expandedContainer: LinearLayout

    private val tvFullFps: TextView
    private val tvAvgFps: TextView
    private val tvLow1Percent: TextView
    private val tvStutterRate: TextView
    private val tvSurfaceName: TextView
    private val waveformView: FpsWaveformView
    private val btnClose: ImageButton

    private var isExpanded = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isClickEvent = true

    init {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        rootView = LayoutInflater.from(context).inflate(R.layout.view_floating_fps, null)

        miniContainer = rootView.findViewById(R.id.container_mini)
        expandedContainer = rootView.findViewById(R.id.container_expanded)
        tvMiniFps = rootView.findViewById(R.id.tv_mini_fps)
        tvMiniFrameTime = rootView.findViewById(R.id.tv_mini_frametime)

        tvFullFps = rootView.findViewById(R.id.tv_full_fps)
        tvAvgFps = rootView.findViewById(R.id.tv_avg_fps)
        tvLow1Percent = rootView.findViewById(R.id.tv_low1_fps)
        tvStutterRate = rootView.findViewById(R.id.tv_stutter_rate)
        tvSurfaceName = rootView.findViewById(R.id.tv_surface_name)
        waveformView = rootView.findViewById(R.id.waveform_view)
        btnClose = rootView.findViewById(R.id.btn_close_floating)

        setupTouchDrag()
        setupListeners()
    }

    private fun setupListeners() {
        miniContainer.setOnClickListener {
            toggleExpandedState()
        }

        expandedContainer.setOnClickListener {
            toggleExpandedState()
        }

        btnClose.setOnClickListener {
            onCloseRequested()
        }
    }

    private fun toggleExpandedState() {
        isExpanded = !isExpanded
        if (isExpanded) {
            miniContainer.visibility = View.GONE
            expandedContainer.visibility = View.VISIBLE
        } else {
            miniContainer.visibility = View.VISIBLE
            expandedContainer.visibility = View.GONE
        }
        updateWindowLayout()
    }

    private fun setupTouchDrag() {
        rootView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClickEvent = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isClickEvent = false
                    }
                    params.x = (initialX + dx).toInt()
                    params.y = (initialY + dy).toInt()
                    updateWindowLayout()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClickEvent) {
                        toggleExpandedState()
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun updateMetrics(data: FpsData) {
        val fpsStr = String.format(Locale.US, "%.1f", data.currentFps)
        val ftStr = String.format(Locale.US, "%.1f ms", data.frameTimeMs)

        tvMiniFps.text = "$fpsStr FPS"
        tvMiniFrameTime.text = ftStr

        // Dynamic color
        val fpsColor = when {
            data.currentFps >= 58.0 -> Color.parseColor("#00E676")
            data.currentFps >= 40.0 -> Color.parseColor("#FFD600")
            else -> Color.parseColor("#FF1744")
        }
        tvMiniFps.setTextColor(fpsColor)
        tvFullFps.setTextColor(fpsColor)

        if (isExpanded) {
            tvFullFps.text = "$fpsStr FPS"
            tvAvgFps.text = String.format(Locale.US, "%.1f", data.avgFps)
            tvLow1Percent.text = String.format(Locale.US, "%.1f", data.low1PercentFps)
            tvStutterRate.text = String.format(Locale.US, "%.1f%%", data.stutterRatePercent)
            tvSurfaceName.text = data.targetSurface

            if (data.historyFrameTimes.isNotEmpty()) {
                waveformView.updateData(data.historyFrameTimes)
            } else {
                waveformView.addDataPoint(data.frameTimeMs.toFloat())
            }
        }
    }

    fun attachToWindow() {
        if (rootView.parent == null) {
            windowManager.addView(rootView, params)
        }
    }

    fun detachFromWindow() {
        if (rootView.parent != null) {
            windowManager.removeView(rootView)
        }
    }

    private fun updateWindowLayout() {
        if (rootView.parent != null) {
            windowManager.updateViewLayout(rootView, params)
        }
    }
}
