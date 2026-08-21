package com.fpsmonitor.app.util

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object ShellUtils {
    private const val TAG = "ShellUtils"

    /**
     * Checks if the device has Root access and su is executable.
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: ""
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes a shell command with either root (su) or standard shell (sh).
     */
    fun execCommand(command: String, useRoot: Boolean = false): CommandResult {
        var process: Process? = null
        var reader: BufferedReader? = null
        var errorReader: BufferedReader? = null
        val output = StringBuilder()
        val error = StringBuilder()

        return try {
            val shell = if (useRoot) "su" else "sh"
            process = Runtime.getRuntime().exec(arrayOf(shell, "-c", command))

            reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            errorReader = BufferedReader(InputStreamReader(process.errorStream))
            while (errorReader.readLine().also { line = it } != null) {
                error.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            CommandResult(exitCode, output.toString().trim(), error.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing command: $command", e)
            CommandResult(-1, "", e.message ?: "Exception occurred")
        } finally {
            try { reader?.close() } catch (_: Exception) {}
            try { errorReader?.close() } catch (_: Exception) {}
            process?.destroy()
        }
    }

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }
}
