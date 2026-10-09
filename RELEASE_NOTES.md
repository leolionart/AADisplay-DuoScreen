# Release Notes

## [v1.0.0] - 2026-10-09

### Initial Release
- **Android Auto Split-Screen**: Multi-display support using Android `DisplayManager` and `androidx.car.app:app:1.4.0`.
- **Shizuku Privilege Integration**: Wireless ADB / ADB privileged execution without root via `dev.rikka.shizuku:api:13.1.5`.
- **Touch Injection**: Coordinate normalization and touch injection via `input -d <id> tap <x> <y>`.
- **Automated Virtual Displays**: Three-display preview support (Top display, Bottom-left, Bottom-right) on phone UI (`MainActivity.kt`).
- **Quick App Launcher**: Pre-configured app launch actions (e.g., Vietmap, YouTube Music).
- **Gradle Wrapper**: Standardized Gradle build wrapper for reproducible builds.
