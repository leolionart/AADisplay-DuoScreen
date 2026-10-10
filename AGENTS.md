# AGENTS.md

Context and operational guidelines for AI agents working on **AADisplay-DuoScreen (DuoScreen AA)**.

---

## 1. Project Overview & Architecture

**DuoScreen AA** is an open-source Android Auto application that enables multi-display / split-screen on vehicle head units using **Shizuku (Wireless ADB / ADB)** without requiring device root.

### Core Modules:
- `com.cva.duoscreen.MainActivity`: Android phone UI. Checks Shizuku service status, requests permissions, displays virtual display previews (`TextureView`), and provides quick launch controls.
- `com.cva.duoscreen.shizuku.ShizukuHelper`: IPC helper interfacing with Shizuku Binder. Executes privileged shell commands (`newProcess`), launches target apps onto virtual displays (`am start --display <id> ...`), and injects touch events (`input -d <id> tap <x> <y>`).
- `com.cva.duoscreen.car.DuoCarAppService`: Android Auto `CarAppService` entry point registered with `androidx.car.app.category.NAVIGATION`. Configures `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` and hosts the car session.
- `com.cva.duoscreen.car.DuoMainScreen`: Android Auto `NavigationTemplate` screen. Sets up the surface callback, action strip (quick app launchers), and handles car lifecycle transitions.
- `com.cva.duoscreen.car.DuoVirtualDisplayManager`: Manages lifecycle of Android `VirtualDisplay` instances created via `DisplayManager`. Maps touch events from the host view to virtual display coordinates and executes shell taps.

---

## 2. Technology Stack & Prerequisites

- **Language**: Kotlin 1.9.23
- **Android Gradle Plugin (AGP)**: 8.3.2
- **Compile SDK**: 34, **Target SDK**: 34, **Min SDK**: 29 (Android 10+)
- **JVM Target / Java Version**: Java 17 (`/opt/homebrew/opt/openjdk@17`)
- **Android SDK Path**: `/Users/admin/android-sdk` (or `$HOME/android-sdk`)
- **Key Dependencies**:
  - `androidx.car.app:app:1.4.0` (Android Auto Car App Library)
  - `dev.rikka.shizuku:api:13.1.5` & `dev.rikka.shizuku:provider:13.1.5` (Shizuku IPC)
  - `androidx.core:core-ktx:1.12.0`
  - `androidx.appcompat:appcompat:1.6.1`
  - `com.google.android.material:material:1.11.0`

---

## 3. Build & Verification Commands

All build commands must use Java 17 and point to the local Android SDK.

```bash
# Environment setup
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=$HOME/android-sdk

# Build debug APK
./gradlew assembleDebug

# Clean build
./gradlew clean

# Dry-run build validation
./gradlew --dry-run assembleDebug
```

Output APK:
`app/build/outputs/apk/debug/app-debug.apk`

---
### 3.1. Android Auto Installation Rule (CRITICAL)
To ensure Android Auto (`Gearhead`) recognizes and displays the application without being blocked as an unknown source, all APK deployments to the test device MUST follow the KingInstaller / fake-vending workflow:
```bash
# 1. Push APK to device temporary storage
adb -s <device_ip>:5555 push app/build/outputs/apk/release/app-release.apk /data/local/tmp/app-release.apk

# 2. Install via package manager with Google Play Store installer package identity (-i com.android.vending)
adb -s <device_ip>:5555 shell pm install -i com.android.vending -r /data/local/tmp/app-release.apk
```
*Do not use plain `adb install` or direct adb streaming install for testing on vehicle head units.*


## 4. Key Rules & Invariants

1. **Non-blocking Shell & IPC**:
   - `ShizukuHelper.executeShell` blocks until the underlying process terminates. Never invoke intensive or multiple shell calls directly on the Android UI/main thread.
2. **VirtualDisplay Lifecycle**:
   - Always release `VirtualDisplay` and `Surface` instances when the activity or car surface is destroyed (`DuoVirtualDisplayManager.release()`) to prevent display and binder leaks.
3. **Android Auto Navigation Template**:
   - The car screen relies on `NavigationTemplate` and `ACCESS_SURFACE` permissions to render custom surfaces. Do not remove automotive metadata or manifest declarations without verifying Android Auto compatibility.
4. **Shizuku Availability Guarding**:
   - Always check `ShizukuHelper.isShizukuAvailable()` and `ShizukuHelper.hasPermission()` before attempting binder calls or shell operations. Gracefully update UI status when the binder is dead or disconnected.
5. **Coordinate Mapping**:
   - Touch coordinates from car or phone surfaces must be normalized and scaled to the target `VirtualDisplay` width/height before calling `input -d <id> tap <x> <y>`.

---

## 5. Working Mode & Delegation (Parent AGENTS.md)

This project resides under `/Volumes/DATA/Coding Projects` and follows the default collaboration rules:
- **Codex** is the coordinator: scopes tasks, reviews diffs, manages git/releases, and verifies results.
- **Antigravity CLI (`agy`)** in `tmux` handles complex, multi-file implementation tasks when delegated.
- Run the narrowest relevant checks first (`./gradlew assembleDebug` or targeted tests) before concluding work.

---

## 6. Git & Release Policy

- Pre-push hook (`.codex-tools/git-hooks/pre-push`) enforces release notes on push.
- Keep `RELEASE_NOTES.md` updated with user-facing and technical changes.
- For published releases, use `gh release create` / `gh release edit` with appropriate tag and notes.
