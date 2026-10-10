# Kiểm chứng chạy local — 06/10/2026

## Kết quả

- `Test-CloudConnections.ps1` trả exit code 0: cả 10 schema Supabase, 4 database MongoDB, Valkey AUTH/PING và Storage HeadBucket đều PASS. Credential không được ghi vào báo cáo.
- MongoDB kết nối thành công sau khi người dùng thêm IP client vào Atlas. Lỗi TLS/timeout trước đó đã hết.
- Doctor trả exit code 0; các JAR, frontend, tool và cổng cần thiết đều đạt.
- Lần khởi động mới trả exit code 0. Status xác nhận 19/19 Spring service đang chạy, cùng Kafka, Alloy và Caddy.
- Health trực tiếp của cả 19 service trả `UP`.
- Caddy trả 200 cho `/`, `/login`, `/flutter_bootstrap.js`; `/api/posts` không có token trả 401.
- Login qua Caddy/gateway: body thiếu trường trả 400; tài khoản không tồn tại trả 401. Cả hai trường hợp vẫn đạt khi client gửi header `Forwarded: malformed`.
- Metric Hikari của cả 10 service PostgreSQL xác nhận `max=2`, `min=0`.

Địa chỉ hiện tại: Flutter Web `http://localhost:3001`, API Gateway `http://localhost:18080`, Eureka `http://localhost:8761`.

## Lỗi đã sửa

1. Supabase thiếu schema và thứ tự cấp role sai: xem [báo cáo 05/10](RUNTIME_REPORT_2026-10-05.md). Script `Initialize-Supabase.ps1` áp dụng SQL versioned trong một transaction; không đổi mật khẩu hay xóa dữ liệu.
2. Khởi động đồng thời làm hết slot PostgreSQL (`53300`). Launcher đặt pool Hikari qua biến môi trường và khởi động từng đợt 2 service, chờ health trước khi sang đợt tiếp theo. Repo chưa khai báo Config Client trong các POM nên không thể dựa riêng vào giới hạn pool trong `config-repo/application-cloud-free.yml`.
3. Caddy gửi header `Forwarded` rỗng khiến `ForwardedHeadersFilter` của gateway ném NullPointerException và API login trả 500. Đổi sang `header_up -Forwarded` để loại bỏ header; reload Caddy và kiểm tra lại đường login đạt.

## Phạm vi kiểm chứng

Chỉ kiểm chứng local với credential cloud và các request cơ bản không tạo tài khoản/bản ghi nghiệp vụ thử nghiệm. Khi service khởi động, Hibernate có tạo/cập nhật bảng trong các schema ứng dụng theo cấu hình hiện có.

Đây chưa phải nghiệm thu đầy đủ CRUD, Kafka/realtime, media privacy, Grafana ingest, tải/khôi phục hay triển khai cloud. Flutter vẫn chạy trên máy Windows này, laptop phải bật. Các gate cloud trong PLAN và ACCEPTANCE_MATRIX còn mở.

Artifact kiểm tra không chứa bí mật nằm trong `.runtime/state/runtime-smoke-2026-10-06.json` (lần đầu phát hiện lỗi login), `login-route-smoke-2026-10-06.json` (sau sửa header) và `runtime-smoke-final-2026-10-06.json` (tổng hợp kết quả đã kiểm chứng).
