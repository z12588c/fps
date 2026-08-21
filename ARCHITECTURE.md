# Android FPS Monitor 架构与核心原理分析

## 1. 核心检测原理 (FPS Measurement Engine)

本项目采用多引擎自适应设计，针对不同权限场景（免 Root / Shizuku / Root / 应用内测试）提供三种采集方案：

### 引擎 A: SurfaceFlinger Latency 引擎 (系统全局与游戏测试推荐)
- **命令来源**: `dumpsys SurfaceFlinger --latency <SurfaceName>`
- **数据结构**:
  - 第一行: 屏幕刷新周期（纳秒，例如 60Hz 对应 16666666ns，120Hz 对应 8333333ns）。
  - 后续行: 每行 3 个时间戳 `[app_desired_present_time, actual_present_time, driver_ready_time]`。
- **算法实现**:
  1. 过滤未呈现或异常标记帧（如时间戳为 0 或 `Long.MAX_VALUE` 即 `9223372036854775807`）。
  2. 记录上一帧的 `actual_present_time`，计算连续帧之间的纳秒差 `Δt = actual_present_time - prev_actual_present_time`。
  3. 将 `Δt` 转换为毫秒 `frameDurationMs = Δt / 1,000,000.0`。
  4. 计算实时帧率 `FPS = 1000.0 / frameDurationMs`。

### 引擎 B: Choreographer FrameCallback 引擎 (应用内 UI 与压力测试)
- **API**: `Choreographer.getInstance().postFrameCallback(FrameCallback)`
- **机制**:
  - 直接挂载于 Android 主线程的 VSYNC 渲染循环。
  - 回调提供精确的 `frameTimeNanos`，零权限即可精准反映应用自身 UI 的渲染流水线负载、掉帧与卡顿。

### 引擎 C: GfxInfo Framestats 引擎 (细粒度渲染管线分析)
- **命令来源**: `dumpsys gfxinfo <package_name> framestats`
- **解析指标**:
  - `IntendedVsync`, `Vsync`, `HandleInputStart`, `AnimationStart`, `PerformTraversalsStart`, `DrawStart`, `SyncQueued`, `SyncStart`, `IssueDrawCommandsStart`, `SwapBuffers`, `FrameCompleted`。
  - 提供从输入响应到合成完成的全链路耗时监控。

---

## 2. 统计学核心指标计算

- **实时 FPS**: 最新单帧耗时的倒数换算。
- **滑动平均 FPS (Avg FPS)**: 过去滑动窗口（如 120 帧）内各帧耗时均值的倒数。
- **1% Low FPS 与 0.1% Low FPS**:
  - 将滑动窗口内的帧耗时升序排序，取第 99 百分位（最慢的 1% 帧）和第 99.9 百分位的帧耗时换算为帧率。
  - 反映游戏在团战、复杂特效或加载时的瞬时微卡顿与掉帧下限。
- **卡顿率 (Stutter / Jank Rate)**:
  - 帧耗时超过 33.33ms（严重掉帧）的帧数占总帧数的百分比。

---

## 3. UI 与悬浮窗交互设计

- **Mini 药丸模式 (Compact Pill)**:
  - 极简胶囊形状，仅展示当前实时 FPS 与瞬时帧耗时（如 `119.8 FPS | 8.3 ms`）。
  - 根据帧率动态变色（绿色：流畅稳定，黄色：轻微波动，红色：严重掉帧）。
- **HUD 扩展卡片模式 (Expanded HUD)**:
  - 点击即可展开，展示当前焦点应用 / Surface 名称、滑动平均 FPS、1% Low FPS、卡顿率百分比。
  - 内置基于 Canvas 贝塞尔曲线的高性能实时折线波形图（`FpsWaveformView`），动态渲染最近 60 帧的耗时波动。
- **全屏自由拖拽与手势识别**:
  - 通过 `WindowManager.LayoutParams` 实现平滑拖动与边界防溢出处理。

---

## 4. 权限与集成方案

| 模式 | 运行环境 | 权限要求 | 适用场景 |
| :--- | :--- | :--- | :--- |
| **Shizuku 模式** | 普通未 Root 手机 | Shizuku ADB 授权 | 免电脑免 Root，日常监测王者荣耀、原神、崩铁等游戏 |
| **Root 模式** | Magisk / KernelSU / APatch | Root (su) 授权 | 深度玩家与极客测试 |
| **ADB CLI 模式** | PC / Mac / Termux | USB/无线调试 ADB | 自动化性能评测与数据导出 |
| **Choreographer 模式** | 任意 Android 设备 | 零权限 (仅需悬浮窗) | 自研 App 性能调优与基准测试 |
