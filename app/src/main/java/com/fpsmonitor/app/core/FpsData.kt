package com.fpsmonitor.app.core

/**
 * Encapsulates real-time FPS performance statistics.
 */
data class FpsData(
    val currentFps: Double = 0.0,
    val frameTimeMs: Double = 0.0,
    val avgFps: Double = 0.0,
    val minFps: Double = 0.0,
    val maxFps: Double = 0.0,
    val low1PercentFps: Double = 0.0,
    val low01PercentFps: Double = 0.0,
    val stutterRatePercent: Double = 0.0,
    val totalFrames: Long = 0L,
    val targetSurface: String = "Active Screen",
    val historyFrameTimes: List<Float> = emptyList()
)
