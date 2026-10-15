# Release Notes

## Unreleased - 2026-10-11

### Android Box Mode & Aggressive Wi-Fi Hotspot Reconnect
- **Tự động mở khóa & Chế độ Android Box chuyên dụng**: Tự động vô hiệu hóa lockscreen (`lockscreen.disabled = 1`, `locksettings set-disabled true`), duy trì sáng màn hình khi cắm sạc/USB xe (`stay_on_while_plugged_in = 7`), tự động đánh thức màn hình (`KEYCODE_WAKEUP` / `wm dismiss-keyguard`).
- **Tự động phê duyệt quyền & kết nối Android Auto**: `DuoAccessibilityService` tự động nhận diện và nhấn phê duyệt các hộp thoại ghép nối ("Cho phép", "Bắt đầu ngay", "Tiếp tục", "Đồng ý", "OK") mà không cần tài xế phải chạm vào điện thoại.
- **Triệt tiêu hoàn toàn cảnh báo ngưng hỗ trợ Android Auto**: Khóa kênh thông báo `gearhead_alerts` (`mImportance = 0`), bổ sung bộ lọc trong `DuoNotificationService` tự động hủy mọi thông báo cảnh báo "không được hỗ trợ / ngừng hoạt động", và `DuoAccessibilityService` tự động bấm bỏ qua các popup cảnh báo.
- **Hotspot Reconnect Watchdog quyết liệt**: Thêm `VehicleBoxManager` và `VehicleBoxService` chạy ngầm 24/7, tự động nạp cấu hình mạng ("AI Phone") vào `WifiConfigStore.xml` và `WifiNetworkSuggestion`, tắt throttling quét Wi-Fi (`wifi_scan_throttle_enabled = 0`, `wifi_sleep_policy = 2`), quét và ép kết nối lại liên tục ngay khi tài xế bật điểm phát sóng trên điện thoại chính.
- **Giao diện cấu hình Android Box trực quan**: Thêm thẻ điều khiển chuyên dụng trong `MainActivity` cho phép nhập SSID/mật khẩu Hotspot, công tắc kích hoạt kết nối quyết liệt, xem trạng thái kết nối trực tiếp và nút tối ưu hệ thống 1-chạm.

### Android Auto cockpit UI
- Redesigned the Android Auto canvas with the phone app's dark cockpit palette, teal accents, rounded cards, and compact status hierarchy.
- Replaced oversized instructional content with readable navigation, speed/Vietmap, and media information panels while preserving the existing ActionStrip launch actions.

### Supervisor verification
- Added an immediate black-canvas clear and native speed bubble render in `CarScreen.onSurfaceAvailable` before the first Google Maps `ImageReader` frame.
- Hardened `VirtualDisplayController` creation/teardown and `ImageReader` listener cleanup; synchronized shared GPS-source ownership across phone and car clients.
- Replaced `ALLOW_ALL_HOSTS_VALIDATOR` with runtime SHA-256 allowlisting for installed Google Android Auto host packages.
- Verified `bash tools/aa/build.sh`, fake-vending installation, DHU startup/forwarding, and final screenshots under `/tmp/aa-supervisor-verify-final` (`phone-1791689581.png`, `dhu-1791689581.png`).

## Unreleased - 2026-10-10 16:02 +07

Generated before push from commits:

- `3fca77c` fix: handle empty release signing secrets

## Unreleased - 2026-10-10

### Android Auto hardening
- Hardened virtual-display cleanup after partial creation failures.
- Prevented duplicate taps caused by injecting `input tap` on both touch-down and touch-up.
- Added package-name/display/coordinate validation before privileged shell operations.
- Added shell timeout and main-thread callback delivery for asynchronous launch results.
- Made release signing explicit: production keystore values come from environment variables; debug release signing is opt-in for local smoke tests only.
- Documented KingInstaller/fake-vending installation and the remaining requirement for real head-unit validation.
- Android Auto host validation now derives SHA-256 allowlist entries only for known Google host packages installed on the device; unknown callers are rejected. Deployment still requires verifying the target host package/certificate combination.

## Unreleased - 2026-10-10 08:36 +07

Generated before push from commits:

- `bcff3b8` feat(car): implement direct ActionStrip navigation buttons for Google Maps, Vietmap Live & YT Music

## Unreleased - 2026-10-10 08:18 +07

Generated before push from commits:

- `98442fc` feat(car): stream live Google Maps directly to Canvas with native GPS & Media widgets

## Unreleased - 2026-10-10 08:11 +07

Generated before push from commits:

- `7de0593` docs: record KingInstaller fake-vending installation rule in AGENTS.md

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
