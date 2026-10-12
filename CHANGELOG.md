# Changelog

## v1.2.0

### Features
- Chinese (Simplified) localization with an in-app language picker (per-app locale via `LocaleManager`)
- "Don't Interrupt" app whitelist — the rest overlay is skipped while a whitelisted app is in the foreground

### Fixes
- No screen flash when switching language (`configChanges="locale|layoutDirection"`)
- The rest alarm now fires reliably while the app is backgrounded (`setAlarmClock`), and the OS-restart path re-arms the alarm instead of losing the countdown

### Build
- The release signing key is resolved from `BLINK_KEYSTORE_FILE` (or a local `blink-release.jks`) and no longer hard-fails the build when absent — the release variant is left unsigned instead
- GitHub Actions workflow that builds the APKs on every push to `main`

### Note
This release is signed with a new key (the original signing key was lost). Users on v1.1.0 or older must uninstall before installing this build.

## v1.1.0

### Reliability Improvements
- Service calls `startForeground()` in `onCreate()` — closes the empty-process kill window
- Timer state persisted across OS-initiated restarts (no longer cleared in `onDestroy`)
- Watchdog alarm fires every 5 minutes via `setExactAndAllowWhileIdle` to auto-restart dead service
- Direct boot support (`LOCKED_BOOT_COMPLETED`) — service starts before device unlock
- `AlarmReceiver` independently restarts service if found dead

### UX Improvements
- OEM-specific battery settings guide (Xiaomi, Samsung, OPPO/Realme/OnePlus, Vivo, Motorola, Huawei, ASUS, Nokia)
- Each OEM guide includes auto-start whitelist steps and force-stop warning
- Shizuku is now fully optional — app works without it (only used for one-tap battery whitelist)

### Housekeeping
- Package renamed from `com.eyecare.daemon` to `com.cflat.blink`
- License: GNU General Public License v3

## v1.0.0

- Initial release
- 20-20-20 rule timer with exact alarms
- Foreground service with persistent notification
- Full-screen overlay with 3 styles (Expressive, Calm, Minimal)
- Dynamic Material 3 theming
- Shizuku integration for battery whitelist
- Boot receiver for auto-start
- Configurable work/rest durations, overlay opacity, dismiss mode
