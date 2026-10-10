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

### 3.1. Android Auto verification and installation

Build success alone does not prove compatibility with a vehicle head unit. Verify the APK on the target Android phone and Android Auto host, including reconnecting the car surface, changing surface size, launching each configured app, and stopping/restarting displays.

For head-unit testing, install through the Play Store installer identity required by Android Auto instead of plain `adb install`:

```bash
adb -s <device_ip>:5555 push app/build/outputs/apk/release/app-release.apk /data/local/tmp/app-release.apk
adb -s <device_ip>:5555 shell pm install -i com.android.vending -r /data/local/tmp/app-release.apk
```

The project does not contain a production signing key. Release signing is supplied through `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. `ALLOW_DEBUG_RELEASE_SIGNING=true` is for local/device smoke testing only and MUST NOT be used for published releases.

Known limitation: this checkout has no Android Auto head-unit or Desktop Head Unit smoke test in CI, so real-car behavior remains deployment-dependent.
