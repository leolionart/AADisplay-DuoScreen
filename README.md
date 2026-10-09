# DuoScreen AA (Open-Source Multi-Display for Android Auto)

Dự án ứng dụng chia đôi màn hình độc lập (Dual/Split-screen) trên nền tảng **Android Auto** sử dụng đặc quyền **Shizuku (Wireless ADB / ADB)** không yêu cầu Root máy.

## 1. Kiến trúc kỹ thuật (Architecture)
- **CarAppService**: Tích hợp chuẩn giao diện bản đồ / định vị `NavigationTemplate` của Google Android Auto (`androidx.car.app:app:1.4.0`) để sở hữu toàn bộ quyền vẽ trực tiếp lên `Surface` của màn hình xe.
- **DuoVirtualDisplayManager**: Chia toạ độ màn hình xe thành 2 màn hình ảo độc lập (`VirtualDisplay 1` và `VirtualDisplay 2`) thông qua Android `DisplayManager`.
- **Shizuku Integration**: Sử dụng Shizuku IPC (`dev.rikka.shizuku:api:13.1.5`) để thực thi các lệnh hệ thống cấp cao qua shell:
  - Khởi chạy app bất kỳ vào màn hình ảo: `am start --display <id> <package>/<activity>`
  - Bắn sự kiện cảm ứng (Touch Injection): `input -d <id> tap <x> <y>` khi người dùng thao tác trên màn hình xe.
  - Hỗ trợ chạy ngầm hoàn toàn độc lập ngay cả khi tắt màn hình điện thoại.

## 2. Cấu trúc thư mục
- `app/src/main/java/com/cva/duoscreen/`:
  - `MainActivity.kt`: Màn hình giao diện trên điện thoại để kiểm tra trạng thái và cấp quyền Shizuku một chạm.
  - `shizuku/ShizukuHelper.kt`: Module wrapper kết nối Shizuku Binder, thực thi lệnh shell và điều khiển display.
  - `car/DuoCarAppService.kt`: Dịch vụ máy chiếu Android Auto cho xe hơi.
  - `car/DuoMainScreen.kt`: Quản lý giao diện và Action điều khiển nhanh trên màn hình xe.
  - `car/DuoVirtualDisplayManager.kt`: Khởi tạo và quản lý bộ nhớ màn hình ảo.

## 3. Cách build & Triển khai
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=$HOME/android-sdk
gradle assembleDebug
```
File APK đầu ra tại: `app/build/outputs/apk/debug/app-debug.apk`.
