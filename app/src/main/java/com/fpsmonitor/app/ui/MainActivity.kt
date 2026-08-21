package com.fpsmonitor.app.ui

import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fpsmonitor.app.R
import com.fpsmonitor.app.core.ChoreographerMonitor
import com.fpsmonitor.app.core.FpsData
import com.fpsmonitor.app.databinding.ActivityMainBinding
import com.fpsmonitor.app.service.FloatingFpsService
import com.fpsmonitor.app.util.PermissionUtils
import com.fpsmonitor.app.util.ShellUtils
import com.fpsmonitor.app.util.ShizukuUtils
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var inAppMonitor: ChoreographerMonitor? = null
    private var stressAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEnvironmentCheck()
        setupListeners()
        setupInAppPreview()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun setupEnvironmentCheck() {
        refreshPermissionStatus()

        binding.btnGrantOverlay.setOnClickListener {
            PermissionUtils.requestOverlayPermission(this)
        }

        binding.btnRequestShizuku.setOnClickListener {
            if (ShizukuUtils.isShizukuAvailable()) {
                ShizukuUtils.requestShizukuPermission()
            } else {
                Toast.makeText(this, "未检测到 Shizuku 服务，请确保已安装并启动 Shizuku", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshPermissionStatus() {
        val hasOverlay = PermissionUtils.canDrawOverlays(this)
        binding.tvOverlayStatus.text = if (hasOverlay) "悬浮窗权限: 已授权" else "悬浮窗权限: 未授权"
        binding.tvOverlayStatus.setTextColor(getColor(if (hasOverlay) R.color.green_accent else R.color.red_accent))
        binding.btnGrantOverlay.isEnabled = !hasOverlay

        val isRoot = ShellUtils.isRootAvailable()
        binding.tvRootStatus.text = if (isRoot) "Root 权限: 已获取" else "Root 权限: 未获取"
        binding.tvRootStatus.setTextColor(getColor(if (isRoot) R.color.green_accent else R.color.text_secondary))

        val hasShizuku = ShizukuUtils.hasShizukuPermission()
        binding.tvShizukuStatus.text = if (hasShizuku) "Shizuku 权限: 已授权" else "Shizuku 权限: 未授权"
        binding.tvShizukuStatus.setTextColor(getColor(if (hasShizuku) R.color.green_accent else R.color.text_secondary))
    }

    private fun setupListeners() {
        binding.btnStartOverlay.setOnClickListener {
            if (!PermissionUtils.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限！", Toast.LENGTH_SHORT).show()
                PermissionUtils.requestOverlayPermission(this)
                return@setOnClickListener
            }

            val mode = when (binding.rgEngineMode.checkedRadioButtonId) {
                R.id.rb_surface_flinger -> FloatingFpsService.MODE_SURFACE_FLINGER
                R.id.rb_gfxinfo -> FloatingFpsService.MODE_GFXINFO
                else -> FloatingFpsService.MODE_CHOREOGRAPHER
            }

            val useRoot = ShellUtils.isRootAvailable()
            val useShizuku = ShizukuUtils.hasShizukuPermission()

            FloatingFpsService.startService(
                context = this,
                mode = mode,
                useRoot = useRoot,
                useShizuku = useShizuku
            )
            Toast.makeText(this, "FPS 悬浮窗已启动", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopOverlay.setOnClickListener {
            FloatingFpsService.stopService(this)
            Toast.makeText(this, "FPS 悬浮窗已关闭", Toast.LENGTH_SHORT).show()
        }

        // Stress test toggle
        binding.switchStressTest.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startStressTestAnimation()
            } else {
                stopStressTestAnimation()
            }
        }
    }

    private fun setupInAppPreview() {
        inAppMonitor = ChoreographerMonitor()
        inAppMonitor?.start { data: FpsData ->
            binding.tvPreviewFps.text = String.format(Locale.US, "%.1f", data.currentFps)
            binding.tvPreviewFrametime.text = String.format(Locale.US, "%.1f ms", data.frameTimeMs)
            binding.tvPreviewAvg.text = String.format(Locale.US, "Avg: %.1f", data.avgFps)
            binding.tvPreviewLow1.text = String.format(Locale.US, "1%% Low: %.1f", data.low1PercentFps)
            binding.previewWaveform.addDataPoint(data.frameTimeMs.toFloat())
        }
    }

    private fun startStressTestAnimation() {
        stressAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                binding.ivStressIndicator.rotation = anim.animatedValue as Float
                // Heavy load simulation for testing stutter detection
                if (binding.switchHeavyLoad.isChecked) {
                    var dummy = 0.0
                    for (i in 0..150000) {
                        dummy += Math.sin(i.toDouble()) * Math.cos(i.toDouble())
                    }
                }
            }
            start()
        }
    }

    private fun stopStressTestAnimation() {
        stressAnimator?.cancel()
        stressAnimator = null
        binding.ivStressIndicator.rotation = 0f
    }

    override fun onDestroy() {
        super.onDestroy()
        inAppMonitor?.stop()
        stopStressTestAnimation()
    }
}
