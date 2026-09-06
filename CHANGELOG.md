# Changelog

All notable changes to flip2shhh are documented in this file.

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
