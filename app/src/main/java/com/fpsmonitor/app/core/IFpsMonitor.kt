package com.fpsmonitor.app.core

interface IFpsMonitor {
    fun start(onFpsUpdate: (FpsData) -> Unit)
    fun stop()
    val isRunning: Boolean
}
