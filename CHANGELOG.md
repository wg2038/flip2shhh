# Changelog

All notable changes to flip2shhh are documented in this file.

## [1.0.0] - 2026-09-07

Initial release.

- Flip detection from the gravity sensor with a 2 second stillness window, asymmetric exit thresholds, and a hand-tremor filter that oversamples the motion sensors during the countdown.
- Do Not Disturb state machine with ownership tracking: it only restores what it started.
- Optional flip-to-lock through the accessibility service, keeping biometric unlock intact.
- Material 3 settings page with visible permission states and an immersive layout.
- Trilingual interface (Simplified Chinese, Traditional Chinese, English) with in-app language override.
- No internet permission, no analytics, nothing collected.
