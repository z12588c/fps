package com.fpsmonitor.app.util

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuUtils {

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return if (isShizukuAvailable()) {
            if (Shizuku.isPre_V11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } else {
            false
        }
    }

    fun requestShizukuPermission(requestCode: Int = 1001) {
        if (isShizukuAvailable() && !hasShizukuPermission()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    fun execShizukuCommand(command: String): ShellUtils.CommandResult {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val exitCode = process.waitFor()
            ShellUtils.CommandResult(exitCode, output.toString().trim(), "")
        } catch (e: Exception) {
            ShellUtils.CommandResult(-1, "", e.message ?: "Shizuku exec error")
        }
    }
}
