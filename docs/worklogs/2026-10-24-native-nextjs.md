# Nhật ký runtime native và CI Next.js — 24/10/2026

## Kết quả trong phạm vi

- Thêm `infra/no-docker/nextjs/run.mjs`: một runner Node dùng trên Windows/Linux, chạy foreground, chọn `dev|build|start`; build Webpack, bind loopback, kiểm tra dependency/build, origin gateway, port và chuyển mã thoát/tín hiệu dừng. Không thêm dependency.
- Thêm Caddy mẫu opt-in trên 3001 → gateway 8080 cho API/WS, Node 3000 cho frontend; không sửa Caddy Flutter hiện có.
- Thêm mẫu `doan-nextjs.service` và README tiếng Việt với lệnh local/production, biến build/runtime, origin/CORS/WS, cài service, cập nhật thủ công và phục hồi.
- Agent backend đọc flag tùy chọn `/opt/doan/config/nextjs-upstream` để giữ fallback Node khi promote. Không flag vẫn tạo fallback `respond 404` như cũ. Flag chỉ nhận loopback + port 1024–65535; lỗi được phát hiện trước khi dừng Java services. Cơ chế backup/rollback Caddy hiện có được giữ.
- CI thêm job Next độc lập Node 24: npm ci, runner regression, lint, unit contracts, typecheck, production build Webpack, Python/Chrome browser fixture. Job Java/Flutter/Cloudflare hiện hành giữ nguyên. Build CI dùng gateway fixture cổng 3399, không phải artifact để phát hành production.

## Bằng chứng đã chạy

1. RED: runner regression thất bại vì runner chưa tồn tại; kiểm tra opt-in thất bại vì agent chưa có `caddy_config`. Sau đó mới viết implementation.
2. GREEN: `node --test infra/no-docker/nextjs/run.test.mjs` → **1/1 đạt**. Kiểm tra chạy child thực bằng CLI fixture trong thư mục tạm: thiếu dependency/build, origin/port sai, mode sai, args build/start, biến môi trường và mã thoát 7 của child.
3. `python -m unittest discover -s infra/azure/tests -v` → **8/8 đạt**, gồm default 404, hai loopback hợp lệ, nhiều cấu hình sai và chặn promote trước khi tạo release/dừng service; test rollback hiện hành vẫn đạt.
4. `.runtime/tools/caddy/caddy.exe validate --config infra/no-docker/nextjs/Caddyfile.example --adapter caddyfile` → **Valid configuration**. Đã sinh template agent với flag vắng mặt và bật `127.0.0.1:3000`, cả hai đều được cùng Caddy validate chấp nhận.
5. PyYAML đọc workflow và xác nhận đủ 4 job (`nextjs-ui`, Java, Windows runtime, Cloudflare); `git diff --check` cho các file tracked trong phạm vi → exit 0.

Lần đầu suite Python dùng thư mục tạm sandbox Windows mặc định lỗi `WinError 5` tại `os.replace` trong test rollback có sẵn. Chạy lại với `TEMP` và `TMP` trỏ vào `output/native-nextjs-test-tmp` trong workspace → cả suite đạt; không sửa logic atomic của agent để né giới hạn môi trường.

## Mức xác minh và phạm vi giữ nguyên

- Máy thực thi hiện tại là Windows/Node 24.15.0; chưa chạy systemd trên Linux hoặc workflow GitHub hosted. Caddy validate chỉ kiểm tra cấu hình, không khởi động listener hoặc gọi cloud.
- Root agent quản lý production build/bộ kiểm tra browser của UI tích hợp; không chạy build đồng thời để tránh tranh `.next`. Kết quả đó được ghi ở nhật ký tổng hợp của root.
- Root phát hiện advisory ở Next 16.4.0 trong build tích hợp và cập nhật patch Next/eslint-config-next 16.4.3; README runtime mô tả bản vá 16.4.x, vẫn giữ Webpack.
- Không bật VM, cài service, ghi flag hoặc thay Caddy trên server. Không stage/commit/push. Không sửa `frontend-web`, `infra/azure/SETUP_TEAM_VI.md`, `infra/azure/VM_CONTROL_VI.md` hoặc các thay đổi local khác.
- Backend package vẫn không chứa frontend; README yêu cầu chuyển/build source UI và quản lý service riêng. Không tạo hệ thống phát hành UI mới.

## Quy tắc bàn giao tiếp tục áp dụng

Tiếp tục dùng `i-have-adhd` cho trình bày tiếng Việt và `ponytail` mặc định full cho code. Giữ thái độ hợp tác tích cực, chủ động tìm bước tiến vừa sức; dẫn bằng giá trị đã hoàn thành, mô tả giới hạn thật ngắn và gắn với hướng xử lý cụ thể. Không nâng yêu cầu nghiên cứu/kiểm thử ngoài phạm vi chỉ để trì hoãn; không biến giả thuyết hay fixture thành kết quả Java/cloud thực tế. Mọi chat nhận bàn giao phải giữ cả quy tắc cộng tác này, không chỉ số liệu và giới hạn.
