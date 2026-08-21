package com.fpsmonitor.app.core

import android.os.Handler
import android.os.Looper
import android.view.Choreographer

/**
 * In-app FPS measurement utilizing Android's Choreographer FrameCallback.
 * Zero-permission method ideal for standalone testing, benchmarking, and UI profiling.
 */
class ChoreographerMonitor : IFpsMonitor {

    private val fpsCalculator = FpsCalculator(windowSize = 120)
    private var lastFrameTimeNanos = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var updateCallback: ((FpsData) -> Unit)? = null

    override var isRunning: Boolean = false
        private set

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            if (lastFrameTimeNanos > 0L) {
                val frameDurationNanos = frameTimeNanos - lastFrameTimeNanos
                val frameDurationMs = frameDurationNanos / 1_000_000.0f
                if (frameDurationMs in 0.5f..500.0f) {
                    fpsCalculator.recordFrame(frameDurationMs)
                }
            }
            lastFrameTimeNanos = frameTimeNanos

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val dispatchRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val fpsData = fpsCalculator.calculate("In-App Choreographer")
            updateCallback?.invoke(fpsData)
            mainHandler.postDelayed(this, 100L)
        }
    }

    override fun start(onFpsUpdate: (FpsData) -> Unit) {
        if (isRunning) return
        isRunning = true
        updateCallback = onFpsUpdate
        fpsCalculator.reset()
        lastFrameTimeNanos = 0L

        mainHandler.post {
            Choreographer.getInstance().postFrameCallback(frameCallback)
            mainHandler.post(dispatchRunnable)
        }
    }

    override fun stop() {
        isRunning = false
        mainHandler.post {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            mainHandler.removeCallbacks(dispatchRunnable)
        }
    }
}
