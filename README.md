# Android FPS Monitor (安卓高精度实时帧率检测工具)

一套专为 Android 平台设计的高精度实时 FPS 帧率与性能监控套件，包含**完整的 Android Studio 原生应用工程（支持悬浮窗实时显示与波形图）**以及**开箱即用的 Python / Termux 命令行实时监控脚本**。

---

## 🌟 核心特性

1. **多引擎支持**:
   - **SurfaceFlinger 模式**: 深入 Android 渲染合成器底层的 `--latency` 纳秒级时间戳解析，支持全局游戏（原神、崩坏：星穹铁道、王者荣耀等）与应用的真实屏幕呈现帧率。
   - **GfxInfo 模式**: 解析 `dumpsys gfxinfo framestats`，深入测量绘制各阶段耗时。
   - **Choreographer 模式**: 免 Root 免 ADB 权限的应用内 UI 渲染与基准测试引擎。
2. **多维专业性能指标**:
   - 实时 FPS、瞬时帧耗时 (ms)
   - 滑动平均 FPS (Avg FPS)
   - **1% Low FPS** & **0.1% Low FPS**（衡量游戏关键掉帧与顿挫感）
   - 卡顿率 (Stutter Rate) 与总帧数统计
3. **高颜值灵动悬浮窗**:
   - **Mini 极简药丸模式**: 占用极小屏幕区域，动态色彩预警（绿/黄/橙/红）。
   - **HUD 扩展详情面板**: 查看平均帧率、1% Low、当前 Surface、自定义 Canvas 实时波形曲线。
   - 自由全屏触控拖拽移动。
4. **灵活运行环境**:
   - 完美适配 **Shizuku**（免 Root 免 PC）、**Root (Magisk/KernelSU)** 以及 **PC/Termux ADB**。

---

## 📂 项目结构

```
AndroidFpsMonitor/
├── app/                                 # Android Studio 原生 App 工程
│   ├── src/main/java/com/fpsmonitor/app/
│   │   ├── core/                        # 核心计算与数据采集引擎
│   │   │   ├── FpsCalculator.kt         # 滑动窗口、1% Low、卡顿率算法
│   │   │   ├── SurfaceFlingerMonitor.kt # SurfaceFlinger 纳秒时间戳采集器
│   │   │   ├── ChoreographerMonitor.kt  # Android Choreographer 回调采集器
│   │   │   ├── GfxInfoMonitor.kt        # Gfxinfo framestats 采集器
│   │   │   └── FpsData.kt               # 性能统计数据结构
│   │   ├── service/
│   │   │   └── FloatingFpsService.kt    # 悬浮窗前台服务与生命周期管理
│   │   ├── ui/
│   │   │   ├── FloatingFpsView.kt       # 悬浮窗视图与手势拖拽控制
│   │   │   ├── FpsWaveformView.kt       # Canvas 动态帧率折线波形图
│   │   │   └── MainActivity.kt          # 主界面、权限检查与压力测试
│   │   └── util/
│   │       ├── ShellUtils.kt            # Root / Shell 执行工具
│   │       ├── ShizukuUtils.kt          # Shizuku Binder IPC 桥接
│   │       └── PermissionUtils.kt       # 悬浮窗等权限校验
│   └── src/main/res/                    # 布局、资源与深色主题
├── scripts/                             # 命令行与自动化脚本
│   ├── fps_monitor.py                   # Python 3 终端高刷新仪表盘 (支持 PC & Termux)
│   ├── fps_monitor_termux.sh            # Termux 纯 Shell 快速检测脚本
│   └── build_apk_guide.sh               # APK 编译指引
├── ARCHITECTURE.md                      # 核心算法与底层原理文档
├── build.gradle.kts                     # Gradle 根构建配置
└── settings.gradle.kts
```

---

## 🚀 使用方法

### 方式一：编译并安装 Android 悬浮窗 App

1. 使用 **Android Studio** 打开 `AndroidFpsMonitor` 目录。
2. 连接手机（开启 USB 调试）或使用本地 Gradle 执行构建：
   ```bash
   ./gradlew assembleDebug
   ```
3. 生成的 APK 文件位于 `app/build/outputs/apk/debug/app-debug.apk`。
4. 打开 App 后：
   - 点击 **“授予悬浮窗权限”** 允许悬浮窗显示。
   - 若已配置 Shizuku，点击 **“请求 Shizuku 授权”**（或直接使用 Root 权限）。
   - 选择 **SurfaceFlinger 模式**，点击 **“开启 FPS 悬浮窗”** 即可在任意游戏或应用上方实时查看帧率！

---

### 方式二：使用 Python 脚本实时监测 (PC 端 / Termux)

1. 确保已安装 Python 3 并配置好 ADB（或在 Termux 中运行）：
2. 运行监控脚本：
   ```bash
   python3 scripts/fps_monitor.py
   ```
3. 高级参数：
   ```bash
   # 指定刷新间隔为 0.1 秒并输出统计至 CSV 文件
   python3 scripts/fps_monitor.py -i 0.1 -o game_benchmark.csv
   
   # 指定特定设备或特定窗口
   python3 scripts/fps_monitor.py -s <device_serial> -w "SurfaceView[com.miHoYo.hkrpg/...]"
   ```

---

### 方式三：Termux 纯 Shell 极速监控

在 Android 手机 Termux 终端中直接运行：
```bash
sh scripts/fps_monitor_termux.sh
```
无需安装额外 Python 依赖，即可在终端单行实时输出当前帧率与耗时。
