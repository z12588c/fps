package com.fpsmonitor.app.core

import android.util.Log
import com.fpsmonitor.app.util.ShellUtils
import com.fpsmonitor.app.util.ShizukuUtils
import kotlinx.coroutines.*

/**
 * Monitors real-time system and gaming FPS by querying dumpsys SurfaceFlinger --latency.
 * Works across root, Shizuku, or ADB shell environments.
 */
class SurfaceFlingerMonitor(
    private val useRoot: Boolean = false,
    private val useShizuku: Boolean = false,
    private val pollIntervalMs: Long = 200L
) : IFpsMonitor {

    private val tag = "SurfaceFlingerMonitor"
    private val fpsCalculator = FpsCalculator(windowSize = 240)
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var lastActualPresentTimeNs = 0L
    private var activeSurfaceName = "Global Window"
    override var isRunning: Boolean = false
        private set

    override fun start(onFpsUpdate: (FpsData) -> Unit) {
        if (isRunning) return
        isRunning = true
        fpsCalculator.reset()
        lastActualPresentTimeNs = 0L

        job = scope.launch {
            while (isActive && isRunning) {
                try {
                    // Step 1: Detect active surface if needed
                    val currentSurface = detectActiveSurface()
                    if (currentSurface.isNotEmpty()) {
                        activeSurfaceName = currentSurface
                    }

                    // Step 2: Fetch latency data
                    val command = if (activeSurfaceName.isNotEmpty() && activeSurfaceName != "Global Window") {
                        "dumpsys SurfaceFlinger --latency \"$activeSurfaceName\""
                    } else {
                        "dumpsys SurfaceFlinger --latency"
                    }

                    val result = execShell(command)
                    if (result.isSuccess && result.stdout.isNotEmpty()) {
                        parseSurfaceFlingerLatency(result.stdout)
                    }

                    val fpsData = fpsCalculator.calculate(surfaceName = activeSurfaceName)
                    withContext(Dispatchers.Main) {
                        onFpsUpdate(fpsData)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error in SurfaceFlinger poll loop", e)
                }

                delay(pollIntervalMs)
            }
        }
    }

    override fun stop() {
        isRunning = false
        job?.cancel()
        job = null
    }

    private fun detectActiveSurface(): String {
        val cmd = "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"
        val res = execShell(cmd)
        if (res.isSuccess && res.stdout.isNotEmpty()) {
            val lines = res.stdout.lines()
            for (line in lines) {
                if (line.contains("mCurrentFocus")) {
                    val match = Regex("""(?:mCurrentFocus=Window\{[^ ]+ [^ ]+ )([^}]+)""").find(line)
                    if (match != null && match.groupValues.size > 1) {
                        val windowName = match.groupValues[1]
                        return windowName.trim()
                    }
                }
            }
        }
        return ""
    }

    private fun parseSurfaceFlingerLatency(output: String) {
        val lines = output.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return

        // First line: Nanoseconds per refresh cycle
        val refreshPeriodNs = lines[0].trim().toLongOrNull() ?: 16666666L

        var previousPresentTimeNs = lastActualPresentTimeNs

        for (i in 1 until lines.size) {
            val tokens = lines[i].trim().split("\\s+".toRegex())
            if (tokens.size < 3) continue

            val desiredPresentTimeNs = tokens[0].toLongOrNull() ?: 0L
            val actualPresentTimeNs = tokens[1].toLongOrNull() ?: 0L
            val frameReadyTimeNs = tokens[2].toLongOrNull() ?: 0L

            // Filter out pending / unpresented frames (0 or Long.MAX_VALUE)
            if (actualPresentTimeNs <= 0L || actualPresentTimeNs == Long.MAX_VALUE || actualPresentTimeNs == 0x7fffffffffffffffL) {
                continue
            }

            if (actualPresentTimeNs > previousPresentTimeNs) {
                if (previousPresentTimeNs > 0L) {
                    val frameDurationNs = actualPresentTimeNs - previousPresentTimeNs
                    val frameDurationMs = frameDurationNs / 1_000_000.0f
                    // Valid frame duration between 0.5ms (2000fps) and 500ms (2fps)
                    if (frameDurationMs in 0.5f..500.0f) {
                        fpsCalculator.recordFrame(frameDurationMs)
                    }
                }
                previousPresentTimeNs = actualPresentTimeNs
            }
        }

        if (previousPresentTimeNs > lastActualPresentTimeNs) {
            lastActualPresentTimeNs = previousPresentTimeNs
        }
    }

    private fun execShell(command: String): ShellUtils.CommandResult {
        return when {
            useShizuku && ShizukuUtils.hasShizukuPermission() -> ShizukuUtils.execShizukuCommand(command)
            useRoot -> ShellUtils.execCommand(command, useRoot = true)
            else -> ShellUtils.execCommand(command, useRoot = false)
        }
    }
}
