# DuoScreen AA Test Harness

This document outlines the verified testing pipeline for DuoScreen AA using the Desktop Head Unit (DHU).
The provided scripts automate the build, fake-vending installation, DHU startup, and screenshot capture.

## Prerequisites & Facts
- **Device Details**: Xiaomi Redmi Note 8 Pro (arm64, SDK 30).
- **Installed Apps**: Android Auto, VietMap Live, Shizuku.
- **Android Auto Dev Mode**: Must be enabled. The Head Unit Server is started via the companion settings activity overflow menu.
- **Installation Rule**: Due to Android Auto restrictions, all test APKs *must* be installed via the fake vending flow (`pm install -i com.android.vending`) to bypass the "unknown source" block on the head unit.

## Scripts Overview
All scripts are located in `tools/aa/` and are non-interactive.

1. **`env.sh`**: Central configuration for `JAVA_HOME`, `ANDROID_HOME`, `ADB`, `DEVICE_SERIAL`, and `DHU`. Sourced by all other scripts.
2. **`build.sh`**: Builds the debug APK using `./gradlew assembleDebug`.
3. **`install.sh`**: Pushes the debug APK to `/data/local/tmp` and installs it via fake vending.
4. **`dhu-up.sh`**: Idempotent script that:
    - Opens the AA companion settings on the phone.
    - Automates tapping the overflow menu and "Start head unit server" (uses customizable coordinates via `AA_OVERFLOW_X`, etc.).
    - Forwards port `5277` (`adb forward tcp:5277 tcp:5277`).
    - Starts the `desktop-head-unit` process detached.
5. **`screenshot.sh [outdir]`**: Captures a screenshot of both the phone and the DHU window (using macOS `screencapture -R`). Output defaults to `/tmp/duoscreen-shots`.


## Supervisor verification — 2026-10-11

Executed from the repository root:

```bash
bash tools/aa/build.sh
bash tools/aa/install.sh
bash tools/aa/dhu-up.sh
bash tools/aa/screenshot.sh /tmp/aa-supervisor-verify-final
```

Observed results:
- `assembleDebug`: `BUILD SUCCESSFUL`; APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Fake-vending install: `Success` using `pm install -i com.android.vending -r`.
- Phone screenshot: `/tmp/aa-supervisor-verify-final/phone-1791689581.png` (1080×2340 PNG).
- DHU screenshot: `/tmp/aa-supervisor-verify-final/dhu-1791689581.png` (1600×1016 capture containing the 800×508 DHU window).
- The final DHU capture showed an unrelated white host window/document, not the DuoScreen surface; this run does not claim end-to-end Maps or bubble rendering. Earlier same-session captures (`dhu-1791688561.png`) showed a Maps surface before the host state changed.

The final build emitted no Kotlin compiler warnings; compilation and packaging succeeded.

Host validation is now runtime SHA-256 allowlisted for installed Google Android Auto host variants. No `CarApp.Val` rejection or `AndroidRuntime` crash appeared in the captured device log query; the DHU screenshot itself was not a DuoScreen render.

The car surface now clears and renders the native canvas/bubble before the first `ImageReader` frame, preventing a black startup frame while Maps launches.

## End-to-End Workflow

```bash
# 1. Build the APK
bash tools/aa/build.sh

# 2. Install using fake vending
bash tools/aa/install.sh

# 3. Start DHU and Head Unit Server
bash tools/aa/dhu-up.sh

# 4. Interact with the DHU UI manually (select DuoScreen AA)
# ...

# 5. Capture results
bash tools/aa/screenshot.sh
```

**Note**: The car app must successfully render its custom surface on the DHU to verify full end-to-end Shizuku + VirtualDisplay integration.
