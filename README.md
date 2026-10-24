# Undelete Messages

Xem lại tin nhắn đã bị thu hồi. Ứng dụng tự lưu mọi tin nhắn đến ngay khi thông báo hiện lên — người gửi có thu hồi thì bản gốc vẫn còn trên máy bạn.

**[⬇️ Tải bản mới nhất (APK)](https://github.com/dung997tb/Undelete-messenger/releases/latest/download/Undelete-Messages.apk)** · [Tất cả các bản phát hành](https://github.com/dung997tb/Undelete-messenger/releases)

> Bản phát hành đầu tiên trên GitHub: **v1.3.1**

## Tính năng

- Lưu tin nhắn đến từ Messenger, Zalo, Telegram, WhatsApp, LINE, Viber, Signal… ngay tại thời điểm thông báo xuất hiện
- Tin bị người gửi thu hồi được đánh dấu thẻ đỏ **ĐÃ THU HỒI** — không thể nhầm với tin thường
- Đọc được cả tin nhắn SMS, kể cả mã OTP ngân hàng (đồng bộ từ hộp thư SMS của hệ thống)
- Tìm kiếm trong toàn bộ tin nhắn đã lưu; ghim ứng dụng / cuộc trò chuyện quan trọng lên đầu
- Sao lưu & khôi phục ra file JSON — khi sao lưu chọn được từng ứng dụng, từng cuộc trò chuyện
- Tự khôi phục các thông báo còn nằm trên thanh thông báo sau khi máy khởi động lại hoặc ứng dụng bị dừng
- Cơ sở dữ liệu mã hoá bằng SQLCipher với khoá riêng theo từng máy — dữ liệu không rời khỏi điện thoại, không máy chủ, không tài khoản

## Cài đặt

1. Tải file APK từ link ở đầu trang
2. Cài **đè** lên bản cũ nếu đã có — ⚠️ **tuyệt đối không gỡ bản cũ trước**: gỡ ứng dụng là mất toàn bộ tin nhắn đã lưu và khoá mã hoá
3. Mở ứng dụng và cấp quyền **Truy cập thông báo** khi được hỏi
4. (Tuỳ chọn) Cấp quyền SMS trong Cài đặt của ứng dụng để lưu cả tin nhắn văn bản và mã OTP

## Build từ mã nguồn

Yêu cầu: JDK 21, Android SDK 35, Gradle 8.10.2

```bash
gradle :core:assembleRelease
```

Bản build được ký bằng keystore của hệ sinh thái ứng dụng, đặt tại `~/keystores/notify-ecosystem.jks` (bí danh `notify-ecosystem`); mật khẩu lấy từ biến môi trường `ECOSYSTEM_STORE_PASS` hoặc file `~/keystores/notify-ecosystem.pass`. Keystore **không** nằm trong repo — tự tạo keystore của bạn và sửa `core/build.gradle.kts` nếu muốn tự build bản ký riêng.

## Cấu trúc project

| Module | Vai trò |
|---|---|
| `:core` | Ứng dụng chính — `com.dung.notifycore`: dịch vụ lắng nghe thông báo, cơ sở dữ liệu mã hoá, giao diện |
| `:contract` | Thư viện dùng chung: AIDL + hằng số của giao thức IPC |
| `:testclient` `:testsender` `:rogueclient` | Công cụ kiểm thử nội bộ (đọc dữ liệu qua IPC, phát thông báo mẫu, thử truy cập trái phép) |

File `rogue-test.jks` (keystore ký cho `:rogueclient` khi kiểm thử) không kèm trong repo — tự tạo nếu cần build module đó.

## Riêng tư

Mọi dữ liệu nằm trên điện thoại của bạn. File sao lưu JSON **không** mã hoá — hãy giữ file đó ở nơi an toàn.
