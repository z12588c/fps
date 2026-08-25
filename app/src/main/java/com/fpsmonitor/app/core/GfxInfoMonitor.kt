package com.fpsmonitor.app.core

import android.util.Log
import com.fpsmonitor.app.util.ShellUtils
import com.fpsmonitor.app.util.ShizukuUtils
import kotlinx.coroutines.*

/**
 * FPS Monitor using dumpsys gfxinfo <package> framestats.
 * Provides detailed frame breakdown for specific target packages.
 */
class GfxInfoMonitor(
    private var targetPackage: String = "",
    private val useRoot: Boolean = false,
    private val useShizuku: Boolean = false,
    private val pollIntervalMs: Long = 300L
) : IFpsMonitor {

    private val tag = "GfxInfoMonitor"
    private val fpsCalculator = FpsCalculator(windowSize = 120)
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override var isRunning: Boolean = false
        private set

    fun setTargetPackage(packageName: String) {
        this.targetPackage = packageName
    }

    override fun start(onFpsUpdate: (FpsData) -> Unit) {
        if (isRunning) return
        isRunning = true
        fpsCalculator.reset()

        job = scope.launch {
            while (isActive && isRunning) {
                try {
                    val pkg = if (targetPackage.isNotBlank()) targetPackage else detectTopPackage()
                    if (pkg.isNotBlank()) {
                        val command = "dumpsys gfxinfo $pkg framestats"
                        val result = execShell(command)
                        if (result.isSuccess && result.stdout.isNotEmpty()) {
                            parseGfxInfo(result.stdout)
                        }
                    }

                    val fpsData = fpsCalculator.calculate(surfaceName = if (pkg.isNotBlank()) pkg else "Top App")
                    withContext(Dispatchers.Main) {
                        onFpsUpdate(fpsData)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error polling gfxinfo", e)
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

    private fun detectTopPackage(): String {
        val res = execShell("dumpsys activity activities | grep -E 'ResumedActivity'")
        if (res.isSuccess && res.stdout.isNotEmpty()) {
            val match = Regex("""(?:u0\s+)([^/]+)""").find(res.stdout)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return ""
    }

    private fun parseGfxInfo(output: String) {
        val lines = output.lines()
        var inFramestats = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("---PROFILEDATA---")) {
                inFramestats = true
                continue
            }
            if (inFramestats) {
                if (trimmed.startsWith("Flags,")) continue // header row
                val tokens = trimmed.split(",")
                if (tokens.size >= 14) {
                    // IntendedVsync is index 1, FrameCompleted is index 13 (nanoseconds)
                    val intendedVsync = tokens[1].toLongOrNull() ?: 0L
                    val frameCompleted = tokens[13].toLongOrNull() ?: 0L
                    if (intendedVsync > 0L && frameCompleted > intendedVsync) {
                        val durationMs = (frameCompleted - intendedVsync) / 1_000_000.0f
                        if (durationMs in 0.5f..500.0f) {
                            fpsCalculator.recordFrame(durationMs)
                        }
                    }
                }
            }
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
