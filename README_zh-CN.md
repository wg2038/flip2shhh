<div align="center">

# flip2shhh

**Flip to Shhh** · 翻过去，安静下来

<img src="docs/screenshots/zh-CN/main_screen.png" width="270"><img src="docs/screenshots/zh-CN/settings_page.png" width="270"><img src="docs/screenshots/zh-CN/about_screen.png" width="270">

一个开源的 Android 小工具：手机屏幕朝下放平两秒，自动开启勿扰；拿起来或翻回正面，恢复之前的状态。可选在翻面时顺手锁屏。

简体中文 · [English](README.md) · [繁體中文](README_zh-TW.md)

</div>

---

它只做一件事：用传感器判断手机是否正面朝下平放在桌面上，然后替你操作系统的勿扰开关。没有网络权限，没有分析统计，不需要账号。

## 实现原理

### 翻面检测

- **重力方向。** 手机平放时重力传感器约为 `(0, 0, −9.8) m/s²`；屏幕朝下时 Z 轴明显为负。进入判定要求 `Z ≤ −9.0` 且水平分量 `√(X²+Y²) ≤ 2.5`，为相机凸起和手机壳留出约 23° / 15° 的容差。
- **两秒静止与方差分析。** 候选姿态必须连续保持 2.0 秒。倒计时期间原始加速度计与陀螺仪提升到约 50 Hz 过采样并汇入滑动窗口；通过统计方差分析严格区分桌面刚性静止（方差 < 0.0015 m²/s⁴）与人体生理微颤（方差 ≥ 0.0035 m²/s⁴），彻底杜绝悬空手持时的误触发。
- **硬件接近传感器门控。** 真实光学接近传感器必须确认屏幕紧贴物理表面（NEAR）。空中悬空时距离传感器保持 FAR 并立即阻断触发；无光学接近传感器的设备则由多样本静止方差引擎严格把关。
- **不对称退出。** 拿起或倾斜使用更宽松的阈值（`Z > −7.5` 或水平分量 > 3.5），在桌面上小幅挪动不会误关勿扰。
- **按需采样。** 陀螺仪和高速采样只在倒计时期间开启，空闲时使用低功耗 UI 采样率；短暂持有 `PARTIAL_WAKE_LOCK`，保证熄屏时计时器和传感器回调继续运行。

### 勿扰状态机

确认翻面后，应用调用 `NotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY)` 开启勿扰，并记录这次勿扰是不是自己开启的：

- 翻面前勿扰是关闭的：翻回来时恢复 `INTERRUPTION_FILTER_ALL`。
- 翻面前勿扰已经开着（系统睡眠排程、手动开关或其他应用）：服务不会擅自关闭它。
- 通过 `ACTION_INTERRUPTION_FILTER_CHANGED` 监听外部变化；用户手动关掉勿扰时同步清理内部状态。
- 状态会持久化保存，服务进程重启后先核对当前系统勿扰状态再决定是否延续。通知策略权限被撤销时，服务立即停止。

### 翻面锁屏（可选）

通过无障碍服务调用系统的 `GLOBAL_ACTION_LOCK_SCREEN`，翻面时顺手熄屏锁定。服务声明了 `canRetrieveWindowContent="false"`，不读取任何屏幕内容，指纹和面部解锁不受影响。

### 服务与界面

检测逻辑运行在 `specialUse` 前台服务中，常驻通知使用最低重要性；开启开机自启后会随系统启动，并返回 `START_STICKY`。Compose 界面包含引导页、权限状态、开机自启与自动锁屏开关、语言选择（简体中文 / 繁體中文 / English）、主题切换和 Material 3 动态取色。

## 两个版本

- **标准版 —— `main` 分支，v1.0.5**：上述日常使用版本。
- **纪念版 —— `easter-egg` 分支，v2.1.5.4**：相同底座，外加一个终端音乐播放器彩蛋；在关于页连续点击版本号七次，会打开一个 Linux 风格终端，播放内嵌歌曲并显示同步歌词。

两个版本使用相同的包名 `shhh.cicada.flip` 和同一签名，安装其中一个后再装另一个需要先卸载。

## 下载

从 [Releases](https://github.com/wg2038/flip2shhh/releases) 下载 APK。系统要求 Android 13 及以上。

## 构建

```bash
git clone https://github.com/wg2038/flip2shhh.git
cd flip2shhh
./gradlew assembleDebug
```

产物在 `app/build/outputs/apk/debug/`。需要 Android SDK 34。

## 隐私

应用不申请网络权限，不包含分析或追踪 SDK。传感器读数只在内存中用于姿态判断，不落盘、不上传。

## 最新更新

**2026-11-02** · 1.0.5 版本重构了翻转判定引擎，引入 50Hz 原始加速度计滑动窗口方差统计与持久化硬件接近传感器门控，彻底解决悬空手持误触发问题。

## 许可

[MIT](LICENSE)
