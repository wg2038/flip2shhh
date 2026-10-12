# Changelog

All notable changes to flip2shhh are documented in this file.

## [1.0.2] - 2026-10-12

- **Samsung & One UI Dynamic Color Fix**: Replaced custom HSL palette generation and raw wallpaper pixel extraction with native Material 3 `dynamicDarkColorScheme` / `dynamicLightColorScheme`. On Samsung devices, the UI now flawlessly adopts the system One UI color palette or standard system blue without muddy colors or broken contrast.
- **Robust Proximity & Flip Detection**: Fixed a critical issue on Samsung and devices with restricted proximity sensors where a non-reporting proximity sensor would permanently block flip-to-DND detection. Stillness verification now safely leverages the high-precision 50Hz gyroscope and accelerometer hand-tremor filter.
- **Android 14+ FGS Compliance**: Explicitly specified `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` when starting foreground service on API 34+.
- **Dynamic Notification Channels**: Implemented locale-aware notification channel lifecycle migration, ensuring channel name and description refresh immediately upon in-app language change.

## [1.0.1] - 2026-09-24

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
