# Release Notes

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
