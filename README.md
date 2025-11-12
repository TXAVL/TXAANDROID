# TXA Hub - Android App

Ứng dụng Android chính thức của TXA Hub, sử dụng WebView để hiển thị giao diện web với các tính năng độc quyền dành riêng cho app.

## Tính năng

- ✅ WebView tích hợp với trang web https://txahub.click
- ✅ User Agent tự động với chuỗi nhận diện `TXAAPP_>>>>>>>`
- ✅ Hỗ trợ Deep Link: `txahub://`
- ✅ Unlock các tính năng app-only khi chạy trong app
- ✅ Xử lý navigation và back button
- ✅ Dark mode support

## Yêu cầu

- Android Studio Hedgehog | 2023.1.1 trở lên
- JDK 8 trở lên
- Android SDK 24 (Android 7.0) trở lên
- Target SDK 34 (Android 14)

## Cài đặt

1. Clone hoặc tải project về
2. Mở project trong Android Studio
3. Đợi Gradle sync hoàn tất
4. Chạy app trên thiết bị hoặc emulator

## Cấu trúc Project

```
TXA Hub/
├── app/
│   ├── build.gradle              # App-level build configuration
│   ├── proguard-rules.pro        # ProGuard rules
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/txahub/app/
│           │   └── MainActivity.kt    # Main activity với WebView
│           └── res/
│               ├── layout/
│               │   └── activity_main.xml
│               └── values/
│                   ├── strings.xml
│                   ├── colors.xml
│                   └── themes.xml
├── build.gradle                  # Project-level build configuration
├── settings.gradle
├── gradle.properties
└── README.md
```

## User Agent Configuration

**QUAN TRỌNG**: App tự động thêm chuỗi `TXAAPP_>>>>>>>` vào user agent của WebView để phía web có thể nhận diện đang truy cập từ app chính chủ.

Code trong `MainActivity.kt`:

```kotlin
val originalUserAgent = webSettings.userAgentString
val customUserAgent = "$originalUserAgent$USER_AGENT_SUFFIX"
webSettings.userAgentString = customUserAgent
```

Với `USER_AGENT_SUFFIX = " TXAAPP_>>>>>>>"`

## Deep Link

App hỗ trợ deep link với scheme `txahub://`:

- `txahub://home` - Mở trang chủ
- `txahub://open?url=https://example.com` - Mở URL cụ thể
- `txahub://` - Mở trang chủ (mặc định)

Cấu hình trong `AndroidManifest.xml`:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="txahub" />
</intent-filter>
```

## JavaScript Injection

App tự động inject JavaScript sau khi trang web load xong để unlock các tính năng app-only:

```javascript
window.dispatchEvent(new CustomEvent('txaapp:ready', {
    detail: { isApp: true }
}));

if (window.txaAppUnlock) {
    window.txaAppUnlock();
}
```

## Build APK

### Debug APK
```bash
./gradlew assembleDebug
```
APK sẽ được tạo tại: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK
```bash
./gradlew assembleRelease
```
APK sẽ được tạo tại: `app/build/outputs/apk/release/app-release.apk`

**Lưu ý**: Release APK cần được ký (signed) trước khi phân phối.

## Cấu hình Signing (Release)

Tạo file `keystore.properties` trong thư mục root:

```properties
storeFile=path/to/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

Cập nhật `app/build.gradle` để thêm signing config (xem tài liệu Android chính thức).

## Lưu ý

1. **User Agent**: Đảm bảo chuỗi `TXAAPP_>>>>>>>` luôn được thêm vào user agent để web có thể nhận diện app.

2. **Internet Permission**: App cần quyền `INTERNET` để load web content (đã có trong manifest).

3. **Cleartext Traffic**: App cho phép HTTP traffic (cho development). Nên tắt trong production nếu chỉ dùng HTTPS.

4. **JavaScript**: JavaScript được bật mặc định để web app hoạt động đầy đủ.

5. **Back Button**: App xử lý nút back để quay lại trang trước trong WebView thay vì thoát app.

## Phát triển

### Thêm tính năng mới

1. Tạo Activity/Fragment mới nếu cần
2. Cập nhật `AndroidManifest.xml` nếu cần permission mới
3. Thêm dependencies vào `app/build.gradle` nếu cần

### Debug

- Sử dụng Chrome DevTools để debug WebView: `chrome://inspect`
- Enable WebView debugging trong Developer Options trên thiết bị

## Liên hệ

- Website: https://txahub.click
- X: https://x.com/TxaVlog
- Facebook: fb.com/vlog.txa.2311
- YouTube: https://youtube.com/@admintxa

## License

Copyright © TXA Hub. All rights reserved.

