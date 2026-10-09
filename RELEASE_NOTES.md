# Release Notes

## Unreleased - 2026-10-10 06:31 +07

Generated before push from commits:

- `bcbb235` feat(car): support Mode Lite (1 map + 2 native widgets) & Mode 3 with auto-launch

## Unreleased - 2026-10-10 06:40 +07

Generated before push from commits:

- `feat(car)`: render multi-display Presentation on Android Auto surface and auto-launch Google Maps, Vietmap Live, and YouTube Music

## Unreleased - 2026-10-09 22:28 +07

Generated before push from commits:

- `c7a4690` fix: launch with windowingMode 1 for edge-to-edge maps and filter out system/battery text from speed limit

## Unreleased - 2026-10-09 22:24 +07

Generated before push from commits:

- `9a4d8cd` fix(ui): ultra-thin 4dp dividers without default handles and log accessibility nodes

## Unreleased - 2026-10-09 22:16 +07

Generated before push from commits:

- `00ef3d6` style: switch to clean square cockpit widgets with album art background and fix button clipping

## Unreleased - 2026-10-09 22:00 +07

Generated before push from commits:

- `4b99e2d` feat: add AccessibilityService to scrape speed limit from Vietmap Live bubble

## Unreleased - 2026-10-09 21:33 +07

Generated before push from commits:

- `57edaf6` fix(display): fix aspect ratio, remove letterboxing, and stay on car smart cockpit

## Unreleased - 2026-10-09 21:28 +07

Generated before push from commits:

- `5c96b8b` feat(car): open Google Maps full-screen via CarContext navigation intent

## Unreleased - 2026-10-09 21:19 +07

Generated before push from commits:

- `ce38ecc` fix(build): use AGP built-in debug signingConfig for release builds

## Unreleased - 2026-10-09 21:16 +07

Generated before push from commits:

- `ef90e99` ci: use preinstalled Android SDK on GitHub Actions runner

## Unreleased - 2026-10-09 21:15 +07

Generated before push from commits:

- `ed604a5` ci: auto build APK and update GitHub Release on main push/merge

## Unreleased - 2026-10-09 21:14 +07

Generated before push from commits:

- `ab74c33` ci: add GitHub Actions workflow to auto build and release APK

## [v1.0.0] - 2026-10-09

### Initial Release
- **Android Auto Split-Screen**: Multi-display support using Android `DisplayManager` and `androidx.car.app:app:1.4.0`.
- **Shizuku Privilege Integration**: Wireless ADB / ADB privileged execution without root via `dev.rikka.shizuku:api:13.1.5`.
- **Touch Injection**: Coordinate normalization and touch injection via `input -d <id> tap <x> <y>`.
- **Automated Virtual Displays**: Three-display preview support (Top display, Bottom-left, Bottom-right) on phone UI (`MainActivity.kt`).
- **Quick App Launcher**: Pre-configured app launch actions (e.g., Vietmap, YouTube Music).
- **Gradle Wrapper**: Standardized Gradle build wrapper for reproducible builds.

### Mode Selection & Smart Cockpit Widgets
- **Dual Display Modes**:
  - **Mode Nhẹ (Lite / Smart Cockpit Mode)**: Chạy 1 VirtualDisplay duy nhất cho Google Maps/bản đồ dẫn đường; 2 ô góc dưới hiển thị native widgets tối ưu tài nguyên, không nóng máy.
  - **Mode 3 (Đa nhiệm 3 App)**: Khởi chạy song song 3 ứng dụng trên 3 VirtualDisplay độc lập qua Shizuku.
- **Widget Cảnh báo Tốc độ & Vietmap Live**:
  - Tích hợp đồng hồ tốc độ GPS thời gian thực kèm cảnh báo vượt tốc độ đỏ.
  - Tự động bắt thông báo nền và biển báo giới hạn tốc độ từ Vietmap Live thông qua `DuoNotificationService`.
  - Nút bấm khởi chạy Vietmap ngầm một chạm.
- **Widget Điều khiển Nhạc Native**:
  - Điều khiển đa phương tiện trực tiếp (Play/Pause, Next, Previous) qua `MediaSessionManager` và `MediaController`.
  - Hiển thị tên bài hát, nghệ sĩ, và ảnh bìa album từ mọi ứng dụng phát nhạc (Spotify, YouTube Music, Zing MP3,...).
