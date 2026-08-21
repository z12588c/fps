package com.fpsmonitor.app.core

import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.max
import kotlin.math.min

/**
 * Calculates comprehensive FPS metrics including real-time FPS, moving averages,
 * 1% Low FPS, 0.1% Low FPS, and stutter/jank rates.
 */
class FpsCalculator(private val windowSize: Int = 120) {

    private val frameTimesMs = ConcurrentLinkedDeque<Float>()
    private var totalFrameCount = 0L
    private var stutterFrameCount = 0L

    /**
     * Records a frame duration in milliseconds.
     */
    fun recordFrame(frameDurationMs: Float) {
        if (frameDurationMs <= 0f || frameDurationMs > 1000f) return

        frameTimesMs.addLast(frameDurationMs)
        totalFrameCount++

        // Threshold for stutter (e.g. frame duration > 33.3ms or 2x refresh period)
        if (frameDurationMs > 33.33f) {
            stutterFrameCount++
        }

        while (frameTimesMs.size > windowSize) {
            frameTimesMs.pollFirst()
        }
    }

    /**
     * Compute current comprehensive FPS metrics.
     */
    fun calculate(surfaceName: String = "Global"): FpsData {
        val snapshot = frameTimesMs.toList()
        if (snapshot.isEmpty()) {
            return FpsData(targetSurface = surfaceName)
        }

        val lastFrameTime = snapshot.last()
        val currentFps = if (lastFrameTime > 0f) (1000.0 / lastFrameTime) else 0.0

        val avgFrameTime = snapshot.average()
        val avgFps = if (avgFrameTime > 0.0) 1000.0 / avgFrameTime else 0.0

        var minFpsVal = Double.MAX_VALUE
        var maxFpsVal = 0.0

        for (ft in snapshot) {
            if (ft > 0f) {
                val fps = 1000.0 / ft
                minFpsVal = min(minFpsVal, fps)
                maxFpsVal = max(maxFpsVal, fps)
            }
        }
        if (minFpsVal == Double.MAX_VALUE) minFpsVal = 0.0

        // 1% Low and 0.1% Low calculation:
        // Sort frame times in ascending order. The worst 1% longest frame times represent the lowest 1% FPS.
        val sortedFrameTimes = snapshot.sorted()
        val low1PercentIndex = (sortedFrameTimes.size * 0.99).toInt().coerceAtMost(sortedFrameTimes.size - 1)
        val low01PercentIndex = (sortedFrameTimes.size * 0.999).toInt().coerceAtMost(sortedFrameTimes.size - 1)

        val low1PercentFrameTime = sortedFrameTimes[low1PercentIndex]
        val low01PercentFrameTime = sortedFrameTimes[low01PercentIndex]

        val low1PercentFps = if (low1PercentFrameTime > 0f) 1000.0 / low1PercentFrameTime else 0.0
        val low01PercentFps = if (low01PercentFrameTime > 0f) 1000.0 / low01PercentFrameTime else 0.0

        val stutterRate = if (totalFrameCount > 0) {
            (stutterFrameCount.toDouble() / totalFrameCount) * 100.0
        } else 0.0

        return FpsData(
            currentFps = currentFps,
            frameTimeMs = lastFrameTime.toDouble(),
            avgFps = avgFps,
            minFps = minFpsVal,
            maxFps = maxFpsVal,
            low1PercentFps = low1PercentFps,
            low01PercentFps = low01PercentFps,
            stutterRatePercent = stutterRate,
            totalFrames = totalFrameCount,
            targetSurface = surfaceName,
            historyFrameTimes = snapshot
        )
    }

    /**
     * Resets accumulated metrics.
     */
    fun reset() {
        frameTimesMs.clear()
        totalFrameCount = 0L
        stutterFrameCount = 0L
    }
}
