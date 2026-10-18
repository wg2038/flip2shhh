# Changelog

All notable changes to flip2shhh are documented in this file.

## [2.1.5.3] - 2026-10-18

Final Memorial Edition release. This last revision was completed with the DeepSeek v4.1 Flash model.

- **WAKE_LOCK permission restored.** The service creates and acquires a partial wake lock during debounce countdowns, but this branch's manifest had dropped the permission, so every acquisition was rejected with `SecurityException`. The permission is back and the timed debounce now actually holds the CPU awake.
- **All 1.0.4 foundation fixes synced.** Proximity detection falls back to stillness for missing or stale readings; the wake lock is re-armed with a generation token and a safety release; the auto-lock switch always reflects the stored preference; DND permission revocation stops the service; flip-up haptics survive an external DND-off; onboarding pages scroll; the notification permission is requested once; the hero control has switch semantics; the boot receiver is no longer exported; data-extraction rules are declared.
- **Audio focus recovery.** The terminal player now resumes after `AUDIOFOCUS_GAIN` while the screen is in the foreground, instead of staying paused until a lifecycle resume.
- **Terminal recomposition reduced.** Playback position is read through `derivedStateOf`, so the 80 ms poll only invalidates the active lyric line (and line-boundary UI) instead of recomposing the whole screen and re-scanning the lyric list on every tick.

## [2.1.5.2] - 2026-10-15

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
- **Memorial Edition Easter Egg Preserved**: All core foundation enhancements synchronized cleanly without modifying the Coral Sea terminal music player.

## [2.1.5.1] - 2026-10-12

- **Samsung & One UI Dynamic Color Fix**: Replaced custom HSL palette generation and raw wallpaper pixel extraction with native Material 3 `dynamicDarkColorScheme` / `dynamicLightColorScheme`. Flawlessly adopts One UI system color palettes or default blue.
- **Robust Proximity & Flip Detection**: Synced core stability fixes ensuring flip detection is never locked out on Samsung and devices with restricted proximity sensors.
- **Android 14+ FGS Compliance**: Explicitly specified `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` for foreground service startups on API 34+.
- **Notification Channel Locale Migration**: Dynamic notification channels now migrate immediately upon language changes.
- **Foundation Synchronized**: Incorporated all stability, debounce grace window, and gyroscope power optimizations from the 1.0.1/1.0.2 foundation while keeping the Coral Sea easter egg player completely intact.

## [2.1.5] - 2026-09-07

Easter egg branch. Same foundation as 1.0.0, plus a terminal player easter egg: seven taps on the version number from the About page. It ships an embedded song with synced lyrics behind a Linux-terminal-style interface, complete with a hand-tremor-aware typing effect and an ending that deletes the whole filesystem. The date on the first line of neofetch is not a placeholder.

The app itself works exactly like 1.0.0.

## [1.0.0] - 2026-09-07

Initial release.

- Flip detection from the gravity sensor with a 2 second stillness window, asymmetric exit thresholds, and a hand-tremor filter that oversamples the motion sensors during the countdown.
- Do Not Disturb state machine with ownership tracking: it only restores what it started.
- Optional flip-to-lock through the accessibility service, keeping biometric unlock intact.
- Material 3 settings page with visible permission states and an immersive layout.
- Trilingual interface (Simplified Chinese, Traditional Chinese, English) with in-app language override.
- No internet permission, no analytics, nothing collected.
