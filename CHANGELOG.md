# Changelog

All notable changes to flip2shhh are documented in this file.

## [1.0.7] - 2026-11-24

- **Framework-Free Detection Core**: Extracted `StillnessDetector` and `FlipStateMachine` as pure Kotlin classes with injected hardware capabilities and `elapsedRealtime` timestamps, so flip detection is unit-testable without Android.
- **No-Gyroscope Fallback**: Devices without a gyroscope no longer dead-loop through the 2 s countdown. They use accelerometer-only variance with a slightly tighter threshold and a 2.5 s stillness window.
- **Gyro Bias & Impact Handling**: Replaced raw gyro energy with three-axis de-meaned variance, and discard the first 300 ms of every high-rate window to ignore cold-start transients and landing impacts. Disturbances reset both the countdown and the sample buffers.
- **Battery & Retry Safety**: Added a 3-attempt stillness circuit breaker that drops back to low-power listening until the phone is lifted, and simplified wake-lock renewal to a single timeout-extending `acquire()`.
- **Low-Power Sensor Modes**: Idle orientation uses non-wake-up sensors with 500 ms FIFO batching. After flip-down, significant motion plus wake-up proximity replace continuous gravity when supported, restoring gravity only for the exit debounce.
- **DND Bounce-Back Guard**: An external DND-off is latched until the phone is lifted, so a still-face-down phone cannot immediately re-enable DND.
- **Proximity First-Frame Race**: The first optical proximity sample is awaited for up to 500 ms with a stillness fallback, avoiding mid-air entry when the HAL is cold.
- **Dedicated Sensor Thread**: 50 Hz callbacks, debounce timers and state changes run on a private `HandlerThread`; the DND receiver is registered with `RECEIVER_NOT_EXPORTED`.
- **Tests**: Rewrote `StillnessDetectorTest` against production code and added `FlipStateMachineTest`, covering 11 tabletop, tremor, bias, low-rate impact, no-gyro, retry-cooldown and DND-suppression scenarios.

## [1.0.6] - 2026-11-19

- **3-Tier Monet & Samsung One UI Dynamic Color**: Rebuilt `MonetThemeResolver` to use native `dynamicDarkColorScheme`/`dynamicLightColorScheme` on Pixel/AOSP, read the underlying system tonal palette (`system_accent*` / `system_neutral*`) or generate HCT schemes via `MaterialKolor` on Samsung One UI (bypassing One UI 9's `#387AFF` / `#94B8FF` static blue override on `system_primary`), and fall back cleanly to the canonical M3E purple palette when dynamic color is unavailable.
- **Single-Screen M3E Interface**: Removed the secondary Settings screen and consolidated controls into a borderless M3E `ExpressiveFloatingToolbar` at the bottom of the home screen with three icon-only 48dp actions: Auto-start on Boot (`AutoMode`), Flip to Lock Screen (`Lock`), and About & MIT License (`Info`).
- **System-Native Language & Theme**: Removed the redundant in-app language and theme pickers; appearance now follows the system dark/light mode automatically, and all UI/notification strings are unified under `res/values*/strings.xml` with Android 13+ `locales_config.xml` per-app language support.
- **Dead Code Cleanup**: Removed obsolete `SettingsScreen`, `ExpressiveSettingsCard`, `Localization`, `AppStrings`, and unused icon/shape/typography/motion tokens.
- **Memorial Edition (v2) Discontinued**: The easter-egg v2 branch has been permanently discontinued; all future maintenance focuses on the single-screen standard edition.

## [1.0.5] - 2026-11-02

- **Eliminate Mid-Air False Triggers**: Replaced frame-to-frame delta checks on low-passed gravity data with 50Hz raw accelerometer sliding-window statistical variance and gyroscope energy analysis. Rigid tabletop placement ($\sigma_a^2 < 0.0015\,\text{m}^2/\text{s}^4$) is strictly separated from handheld hovering and physiological tremor ($\sigma_a^2 \ge 0.0035\,\text{m}^2/\text{s}^4$).
- **Persistent Hardware Proximity Gating**: Fixed proximity sensor categorization by removing `"ear"` from virtual exclusions (restoring genuine TMD3725/STK33911 earpiece optical sensors). Removed the 5s staleness timeout on `ON_CHANGE` proximity readings so mid-air open-air holding (`FAR`) immediately blocks flip-down entry without expiring.
- **Eliminate Debounce Grace Loophole**: Replaced the 250ms debounce grace recheck with a full 2.0s continuous stillness window, preventing handheld tremor from opportunistically triggering DND on single-sample zero crossings.
- **Symmetrical Lift Detection**: Lifting the phone off a table surface immediately transitions proximity to `FAR` with dynamic acceleration, restoring DND cleanly without requiring a $39^\circ$ tilt.
- **Unit Test Coverage**: Added `StillnessDetectorTest` verifying mathematical separation between rigid tabletop placement and handheld mid-air tremor.

## [1.0.4] - 2026-10-18

Final Standard Edition release. This last revision was completed with the DeepSeek v4.1 Flash model.

- **Proximity detection no longer locks out.** A missing proximity sample is treated as "unknown" instead of "far", a reading older than 5 s is considered stale, and NEAR is recognized as any value below the sensor's maximum range. Flip detection now falls back to the stillness check on devices whose proximity sensor never reports or stops reporting.
- **Wake lock renewal fixed.** Each countdown re-arms an untimed partial wake lock with a generation token and an explicit safety release, avoiding both the stale 3 s timeout and the AOSP pitfall where a timed `acquire()` leaves an old releaser behind.
- **Auto-lock switch can always be turned off.** The switch reflects the stored preference rather than the effective permission state, shows a "permission required" subtitle when accessibility is disabled, and no longer silently re-enables itself.
- **DND permission revocation stops the service immediately.** The interruption-filter receiver now resets ownership state, persists it, and calls `stopSelf()` instead of waiting for the activity to resume.
- **Flip-up feedback restored.** A confirmed flip-up now always plays the subtle haptic, even if DND was turned off externally while the phone was face down.
- **Onboarding pages scroll.** Both onboarding pages use a vertically scrollable layout with a minimum viewport height, so permission cards and the only completion button stay reachable on small screens and at large font scales.
- **Notification permission is requested once.** A persisted flag prevents the runtime notification-permission dialog from being launched on every app start.
- **Accessible hero control.** The centerpiece is now a semantics `Switch` with a screen-reader description instead of an unlabeled clickable `Box`.
- **Manifest and resource hardening.** The boot receiver is no longer exported, and Android 12+ data-extraction rules explicitly cover the preference store.
- **Cleanup.** Removed the obsolete API-level branch in `startForeground`, replaced autoboxed Compose state with `mutableIntStateOf`, and removed the redundant programmatic portrait lock (the manifest still declares portrait).

## [1.0.3] - 2026-10-15

- **Continuous Stillness Verification**: Removed proximity bypass on motion stillness check, ensuring any physical movement or vibration during the 2.0s countdown resets the timer even when the proximity sensor is occluded.
- **Surface Proximity Fusion & Debounce Cancellation Fix**: Integrated optical proximity check into flat-down entry detection and ensured exit debounce timers are not inadvertently cancelled while the device is in motion off the table.
- **Initial Sensor Sample Warmup**: Prevented false ~9.8 m/s² acceleration spikes and premature DND exits upon service restart by deferring state evaluation until valid initial orientation samples are recorded.
- **Partial WakeLock During Debounce**: Held a targeted, timed `PARTIAL_WAKE_LOCK` strictly during debounce countdowns to prevent CPU deep sleep from freezing sensor event delivery and timer execution.
- **API 33+ Hardware Feedback Haptic & Lock Separation**: Upgraded vibrations to `VibrationAttributes.USAGE_HARDWARE_FEEDBACK` to prevent DND from muting haptics, and introduced a 150ms delay for auto-lock with `powerManager.isInteractive` guard to prevent haptic truncation and redundant lock events.
- **DND State & Lifecycle Synchronization**: Added a broadcast receiver for `ACTION_INTERRUPTION_FILTER_CHANGED`, auto-stopped service if DND permissions are revoked, and updated hero centerpiece states and auto-start logic on Activity resume.
- **Static Notification Channel & System Locale Refresh**: Consolidated notification channels into a single stable ID with in-place name/description updates and added `onConfigurationChanged` callback to instantly refresh notifications on system language changes.
- **Settings & About Page Navigation Integrity**: Reorganized `SettingsScreen` and `AboutScreen` hierarchy with `AnimatedVisibility` overlay, preserving settings scroll position and state upon returning from the About screen.
- **Compose State Thread Safety & Zero-Flash Initialization**: Synchronously initialized permission states and ensured all mutable state updates occur on the main UI thread.
- **Accessibility Service Lifecycle Protection**: Enforced `private set` and identity check on `FlipLockAccessibilityService.instance` teardown to eliminate unbind/destroy race conditions.

## [1.0.2] - 2026-10-12

- **Samsung & One UI Dynamic Color Fix**: Replaced custom HSL palette generation and raw wallpaper pixel extraction with native Material 3 `dynamicDarkColorScheme` / `dynamicLightColorScheme`. On Samsung devices, the UI now flawlessly adopts the system One UI color palette or standard system blue without muddy colors or broken contrast.
- **Robust Proximity & Flip Detection**: Fixed a critical issue on Samsung and devices with restricted proximity sensors where a non-reporting proximity sensor would permanently block flip-to-DND detection. Stillness verification now safely leverages the high-precision 50Hz gyroscope and accelerometer hand-tremor filter.
- **Android 14+ FGS Compliance**: Explicitly specified `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` when starting foreground service on API 34+.
- **Dynamic Notification Channels**: Implemented locale-aware notification channel lifecycle migration, ensuring channel name and description refresh immediately upon in-app language change.

## [1.0.1] - 2026-10-24

- **Optical Proximity Correction**: Removed vendor-level Samsung exclusions from optical proximity detection, restoring hardware proximity verification and clean face-up exit detection on Samsung devices.
- **Notification Channel Stability**: Retained a stable notification channel across in-app language switches instead of deleting active channels at runtime, eliminating Foreground Service notification crashes.
- **Debounce Stillness Grace Window**: Added a 250ms grace recheck when the device is strictly flat face-down but experiences transient micro-vibrations at the 2.0s boundary, preventing endless 2000ms debounce loop resets.
- **Power & Gyroscope Optimization**: Dynamically register the high-power gyroscope only during the 2.0s stillness verification window, keeping it powered down during normal idle states to save battery.
- **Haptic & Accessibility Hardening**: Added amplitude control capability check before applying waveform vibration, made the accessibility service instance `@Volatile`, verified master accessibility switch, and requested `WAKE_LOCK` for reliable screen-off wake-up sensor dispatching.

## [1.0.0] - 2026-09-07

Initial release.

- Flip detection from the gravity sensor with a 2 second stillness window, asymmetric exit thresholds, and a hand-tremor filter that oversamples the motion sensors during the countdown.
- Do Not Disturb state machine with ownership tracking: it only restores what it started.
- Optional flip-to-lock through the accessibility service, keeping biometric unlock intact.
- Material 3 settings page with visible permission states and an immersive layout.
- Trilingual interface (Simplified Chinese, Traditional Chinese, English) with in-app language override.
- No internet permission, no analytics, nothing collected.
