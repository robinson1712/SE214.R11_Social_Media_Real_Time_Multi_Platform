# Kiểm chứng runtime sau refactor — 02/10/2026

## Phạm vi

Windows local, giữ working tree chưa commit. Chưa nghiệm thu cloud target trong PLAN.md. Không chạy Docker, không đổi password/key trên nhà cung cấp, không tạo fixture nghiệp vụ hoặc thay schema cloud. Credential không được ghi vào báo cáo. Các tiến trình thử được dừng sau kiểm tra.

## Sửa trong repo

- Import env cũ ghép thiết lập mặc định mới trong bộ nhớ, giữ credential hiện có; khép lỗi thiếu CORS_ALLOWED_ORIGINS khi bật StrictMode.
- Thay currentSchema đã có trong JDBC URL thay vì nối thêm tham số trùng.
- Sửa timestamp process JSON giữa PowerShell 5 và 7; không chuyển DateTime UTC thành chuỗi theo locale trước khi so sánh. Health wait phát hiện process đã chết sớm.
- Doctor kiểm TLS mode, credential DB nằm riêng URL, session pooler port, cặp user/password từng service, database trong URI Mongo.
- Test-CloudConnections.ps1 dùng driver từ JAR để kiểm SQL login/schema USAGE/CREATE, Mongo ping/list collections, Valkey AUTH/PING, S3 HeadBucket. TLS Valkey xác minh hostname; chỉ in trạng thái hoặc loại lỗi/SQLSTATE. Credential qua environment của process, được phục hồi sau probe; không in SDK exception message. Một credential SQL thất bại không bị thử lại cho từng service dùng chung.
- Start hiện hiển thị Doctor và kiểm cloud login/schema trước khi mở service.
- Profile Maven native-runtime build vào `<module>/.runtime/build`; Build.ps1 chạy clean package/tests, copy đủ 19 JAR vào apps sau khi build thành công. Tránh output target cũ không xóa được trên máy này.
- Gateway port cấu hình qua GATEWAY_PORT, đồng bộ Caddy/Alloy; runtime hiện dùng 18080 do một process Java khác đã giữ 8080. Config Server quảng bá loopback phù hợp địa chỉ listen.
- Caddy dùng handle riêng cho API/WebSocket để SPA try_files không đổi API thành index.html. Lần smoke trước sửa đã phát hiện GET /api/posts trả HTML 200.
- Flutter 3.47.5 từ tag chính thức, Dart 3.13.4; đồng bộ pubspec.lock do pubspec hiện có url_launcher nhưng lockfile cũ thiếu dependency. CI khóa Flutter 3.47.5 và enforce lockfile. Flutter tự bổ sung analyzer exclude build/web. Generated frontend output đã được Git ignore.

## Kết quả

| Kiểm tra | Kết quả | Giới hạn |
|---|---|---|
| Maven package trên target cũ | PASS | Có nguy cơ class cũ; không dùng làm fresh artifact |
| Maven clean package trên target cũ | FAIL | Không xóa được inputFiles.lst; không sửa ACL hoặc xóa source |
| Build.ps1 -SkipFrontend -Offline / native-runtime | PASS | 21 module, 19 executable JAR mới; JDK 21 compile target 17 |
| Test từ clean build | 518 pass, 6 skip, 0 failure/error | PostgreSQL native integration skip, không chứng minh cloud nghiệp vụ |
| Env defaults và thay currentSchema | PASS | Regression probe không dùng secret thật |
| Process timestamp dạng DateTime và ISO string | PASS | Chạy trên PowerShell 7; smoke Stop bằng PowerShell 5 |
| Script PowerShell parse | PASS | Không thay thế kiểm thử mọi nhánh lỗi |
| Flutter build web --release | PASS | Output frontend/build/web; có cảnh báo dependency chưa hỗ trợ Wasm, bản JS vẫn build đạt |
| Eureka / Config Server / Gateway | PASS | Health HTTP 200 trên loopback; Config trả profile cloud-free, registry đọc được; không có business service SQL/Mongo |
| Caddy route smoke sau sửa | PASS | GET /, /login, /flutter_bootstrap.js trả 200; GET /api/posts thiếu token trả 401 qua Gateway; chưa kiểm browser thao tác hoặc WebSocket |
| Valkey AUTH/PING | PASS | Không kiểm persistence, rate-limit load hoặc recovery |
| S3 authenticated HeadBucket | PASS | Không kiểm upload/delete/Range/media privacy |
| Supabase SQL login | FAIL: 28P01 | Host/user/password hiện tại bị server từ chối; chưa kiểm được schema |
| MongoDB | BLOCKED | Cả bốn URI vẫn chứa `<db_password>` |
| Doctor cuối lượt | FAIL | Credential Mongo còn placeholder; frontend build đã có |

Log build local (ignored): `.runtime/native-build.log`, `.runtime/frontend-build.log`. Route smoke lưu status tại `.runtime/state/route-smoke.json` khi assertions đạt. Không publish runtime logs chứa cấu hình provider lên PR.

## Để chạy đủ nghiệp vụ

1. Điền password Mongo Database User vào bốn URI, URL-encode ký tự dành riêng; database lần lượt story_db, reels_db, chat_db, notification_db. Không suy ra password này từ Supabase hoặc tài khoản Atlas.
2. Sửa kết nối Supabase bằng đúng host/user/database password của cùng project; không tự ghép host aws-0 theo region. Probe hiện báo 28P01. Khi login đạt, probe tiếp tục xác nhận schema USAGE/CREATE; nếu schema chưa có, dùng init-schemas.sql sau khi xác nhận đúng project.
3. Chạy Doctor.ps1, Test-CloudConnections.ps1, Start.ps1. Sau khi 19 service lên, mới chạy luồng đăng ký/login/post/chat qua Gateway và ghi assertion/side effect theo acceptance matrix.

Các key/password đã được đưa vào chat cần được thay trước khi dùng dữ liệu thật. Chưa xác minh Grafana ingest, DB isolation/TLS verify-full, media privacy, ARM64, deployment/recovery, React parity hoặc soak. Không đánh PASS cloud từ kết quả build và smoke này.
