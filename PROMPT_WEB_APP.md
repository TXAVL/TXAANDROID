# PROMPT AI - TẠO WEB APP TXA HUB

## Copy prompt dưới đây và paste vào AI (ChatGPT, Claude, Gemini, v.v.)

---

**PROMPT:**

Hãy viết mã nguồn một web app HTML/CSS/JS với các yêu cầu sau:

### 1. Nhúng trang web chính
- Khi người dùng truy cập, web app nhúng trang https://txahub.click toàn màn hình bằng iframe
- Nếu iframe không được phép (X-Frame-Options, CSP), thì chuyển hướng (redirect) bằng JavaScript đến https://txahub.click
- Tối ưu cho mobile và desktop

### 2. Tính năng độc quyền App Android (APP ONLY FEATURES)
- Giao diện có thêm một số tính năng độc quyền của app Android (APP ONLY FEATURES)
- Nếu truy cập từ trình duyệt thông thường, các tính năng này bị disabled (không thể nhấn vào, làm mờ)
- Có tooltip ghi rõ: "Tính năng này chỉ dành cho app TXA Hub trên Android. Vui lòng mở app để sử dụng."
- Tooltip hiển thị khi hover vào các tính năng bị lock

### 3. Thông báo đầu trang
- Ở đầu trang có thông báo về các tính năng chỉ dành riêng cho app Android, chưa hỗ trợ Windows
- Thông báo có thể đóng được (có nút X)
- Style: nền vàng cam nhẹ, text đen, có icon cảnh báo

### 4. Modal khi click vào tính năng bị lock
Khi bấm vào các tính năng bị lock, hoặc vào dòng thông báo, sẽ hiện modal:

**Trường hợp 1: Phát hiện user agent có chuỗi TXAAPP_>>>>>>>**
- Hiện modal với ảnh 1 (placeholder: "app-ready.jpg" hoặc div với text "Ảnh 1: App đã sẵn sàng")
- Hướng dẫn: "Bạn đang sử dụng app TXA Hub chính chủ. Các tính năng đã được mở khóa!"
- Có nút "Đóng"

**Trường hợp 2: Không phát hiện user agent như trên**
- Hiện modal với ảnh 2 (placeholder: "install-app.jpg" hoặc div với text "Ảnh 2: Hướng dẫn cài app")
- Hướng thị: "Để sử dụng các tính năng độc quyền, vui lòng cài đặt app TXA Hub trên Android"
- Có 2 nút:
  - "Tải APK" - link tải file APK (placeholder: "https://txahub.click/download/txahub.apk")
  - "Mở App" - deep link: `txahub://home` (nếu app đã cài, sẽ mở app; nếu chưa, có thể hiện thông báo)
- Có nút "Đóng"

### 5. Chặn tính năng Admin (TXAPRO...)
- Các tính năng sẵn có của trang txahub.click thuộc admin (TXAPRO...) cũng bị chặn tương tự
- Tooltip và logic giống như trang admin view khi thiếu giấy phép TXAPRO
- Tooltip: "Tính năng này yêu cầu quyền TXAPRO. Vui lòng nâng cấp tài khoản hoặc sử dụng app TXA Hub trên Android."

### 6. Nhận diện Android app
- Nhận diện Android app qua user agent: Nếu có chuỗi `TXAAPP_>>>>>>>` thì unlock các tính năng
- Nếu không có chuỗi này thì lock như trên
- Code JavaScript để check user agent: `navigator.userAgent.includes('TXAAPP_>>>>>>>')`

### 7. Cấu trúc file
- `index.html` - File HTML chính
- `styles.css` - File CSS cho styling, tooltip, modal, responsive
- `app.js` - File JavaScript với logic nhận diện, lock/unlock, modal
- `readme_app.md` - File mô tả chi tiết cấu trúc/thành phần/logic

### 8. Yêu cầu kỹ thuật
- Sử dụng vanilla JavaScript (không dùng framework)
- CSS responsive, tối ưu mobile-first
- Modal có animation fade in/out
- Tooltip có animation slide up
- Code clean, có comment tiếng Việt
- Tương thích với các trình duyệt hiện đại (Chrome, Firefox, Safari, Edge)

### 9. Thông tin website
- Website chính: txahub.click
- Website sản phẩm: software.txahub.click
- Deep link: txahub://
- X: https://x.com/TxaVlog
- FB: fb.com/vlog.txa.2311
- YT: https://youtube.com/@admintxa

### 10. File readme_app.md
File này phải mô tả:
- Cấu trúc dự án web app
- Logic nhận diện user agent và unlock/lock tính năng
- Cách tổ chức code (HTML/CSS/JS)
- Hướng dẫn cho developer Android:
  - App Android phải set user agent WebView với chuỗi `TXAAPP_>>>>>>>`
  - Ví dụ code Android: `webView.getSettings().setUserAgentString(webView.getSettings().getUserAgentString() + " TXAAPP_>>>>>>>");`
  - Deep link: `txahub://home` để mở app
- Các lưu ý khi phát triển app Android tích hợp web app này

### 11. Ghi chú về ảnh
- Ảnh 1, 2 chừa chỗ ghi chú để dev tự bổ sung sau
- Có thể dùng placeholder div với text tạm thời

### 12. Tính năng bổ sung
- Có thể thêm loading spinner khi iframe đang load
- Có thể thêm error handling nếu iframe không load được
- Có thể thêm button "Mở trong app" ở header nếu phát hiện mobile nhưng chưa có user agent TXAAPP

---

**Yêu cầu trả về:**
1. Mã code hoàn chỉnh cho web app (index.html, styles.css, app.js)
2. File readme_app.md mô tả chi tiết
3. Code phải sẵn sàng chạy, chỉ cần thay thế ảnh placeholder bằng ảnh thật

---

**Lưu ý đặc biệt:**
- User agent check phải chính xác: `navigator.userAgent.includes('TXAAPP_>>>>>>>')`
- Deep link phải đúng format: `txahub://home`
- Tooltip và modal phải có animation mượt mà
- Code phải tối ưu performance, không block UI thread

