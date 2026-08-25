package com.fpsmonitor.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fpsmonitor.app.R
import com.fpsmonitor.app.core.*
import com.fpsmonitor.app.ui.FloatingFpsView
import com.fpsmonitor.app.ui.MainActivity

/**
 * Foreground service hosting the floating FPS monitor overlay.
 */
class FloatingFpsService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_USE_ROOT = "EXTRA_USE_ROOT"
        const val EXTRA_USE_SHIZUKU = "EXTRA_USE_SHIZUKU"

        const val MODE_SURFACE_FLINGER = "SURFACE_FLINGER"
        const val MODE_CHOREOGRAPHER = "CHOREOGRAPHER"
        const val MODE_GFXINFO = "GFXINFO"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "fps_monitor_channel"

        fun startService(
            context: Context,
            mode: String = MODE_SURFACE_FLINGER,
            useRoot: Boolean = false,
            useShizuku: Boolean = false
        ) {
            val intent = Intent(context, FloatingFpsService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_USE_ROOT, useRoot)
                putExtra(EXTRA_USE_SHIZUKU, useShizuku)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingFpsService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var floatingView: FloatingFpsView? = null
    private var monitor: IFpsMonitor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SURFACE_FLINGER
                val useRoot = intent.getBooleanExtra(EXTRA_USE_ROOT, false)
                val useShizuku = intent.getBooleanExtra(EXTRA_USE_SHIZUKU, false)
                startFloatingMonitor(mode, useRoot, useShizuku)
            }
            ACTION_STOP -> {
                stopFloatingMonitor()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startFloatingMonitor(mode: String, useRoot: Boolean, useShizuku: Boolean) {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        if (floatingView == null) {
            floatingView = FloatingFpsView(this) {
                stopFloatingMonitor()
                stopSelf()
            }
            floatingView?.attachToWindow()
        }

        monitor?.stop()
        monitor = when (mode) {
            MODE_SURFACE_FLINGER -> SurfaceFlingerMonitor(useRoot = useRoot, useShizuku = useShizuku)
            MODE_GFXINFO -> GfxInfoMonitor(useRoot = useRoot, useShizuku = useShizuku)
            else -> ChoreographerMonitor()
        }

        monitor?.start { data ->
            floatingView?.updateMetrics(data)
        }
    }

    private fun stopFloatingMonitor() {
        monitor?.stop()
        monitor = null
        floatingView?.detachFromWindow()
        floatingView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFloatingMonitor()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FPS Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running real-time FPS overlay"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingFpsService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("FPS 实时悬浮窗运行中...")
            .setSmallIcon(R.drawable.ic_fps)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_fps, "停止监控", stopIntent)
            .setOngoing(true)
            .build()
    }
}
