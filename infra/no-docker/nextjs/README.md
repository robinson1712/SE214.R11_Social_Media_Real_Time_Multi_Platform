# Chạy UI Next.js bằng Node native

UI Next.js/React/TypeScript dùng gateway Java hiện có. Không cần Docker. CI production phát hành UI này lên Cloudflare Workers; Caddy trên VM là phương án chạy riêng.

## 1. Chạy trên Windows hoặc Linux

Cần Node.js 24 trở lên và npm trong `PATH`; kiểm tra `node --version` và `npm --version`. Dùng dependency từ lockfile (Next.js 16.4.x đã cập nhật bản vá), không cài Next toàn cục. Mở terminal ở root repository:

```powershell
cd frontend-web
npm ci --no-audit --no-fund
cd ..
$env:GATEWAY_UPSTREAM = 'http://127.0.0.1:8080'
node infra/no-docker/nextjs/run.mjs dev
```

Trên Linux, thay dòng gán biến bằng `export GATEWAY_UPSTREAM=http://127.0.0.1:8080`. Mở `http://127.0.0.1:3000`, dùng `Ctrl+C` để dừng. Runner chạy foreground, bind loopback; không tự cài dependency, bật VM hay khởi động backend. Nếu `node` không tồn tại, cài Node trước khi chạy runner. Thiếu dependency/build hoặc cấu hình sai sẽ trả mã lỗi và chỉ rõ bước sửa.

Production trên cả hai hệ điều hành, tại root repository:

```text
node infra/no-docker/nextjs/run.mjs build
node infra/no-docker/nextjs/run.mjs start
```

`build` gọi `next build --webpack`, tương đương `npm run build`; Webpack là đường build đã kiểm tra trên Windows. `PORT` mặc định `3000`, chỉ nhận số từ 1024 tới 65535. `FRONTEND_DIR` tùy chọn trỏ tới checkout `frontend-web` khác. Runner giữ mã thoát của Next và chuyển tín hiệu dừng cho tiến trình con.

## 2. Cấu hình URL đúng thời điểm

Dev cũng dùng Webpack, cùng đường biên dịch đã kiểm tra cho production trên Windows. Cấu hình đang dùng tại máy chủ đồ án lúc kiểm tra ngày25/10 là gateway `18080`; thay `8080` trong ví dụ bằng cổng thực của `.runtime/env.ps1` khi chạy lại.

| Biến | Giá trị mặc định | Khi áp dụng |
|---|---|---|
| `GATEWAY_UPSTREAM` | `http://127.0.0.1:8080` | Next rewrites `/api/*`, `/ws/*`, `/ws-notifications/*`; đặt trước build và giữ cùng giá trị khi start |
| `NEXT_PUBLIC_API_BASE_URL` | Để trống: cùng origin trình duyệt | Origin HTTP(S) của API riêng; nhúng vào browser bundle lúc build |
| `NEXT_PUBLIC_WS_BASE_URL` | Để trống: cùng origin trình duyệt | Origin realtime riêng; nhúng vào browser bundle lúc build |

URL phải là origin, không có user/password, path `/api`, query hay token. Các biến `NEXT_PUBLIC_*` chỉ chứa URL công khai; thay chúng cần **build lại**. Không dùng build fixture với gateway cổng 3399 để phát hành production. Khi đổi `GATEWAY_UPSTREAM`, build lại để rewrites không giữ đích cũ.

Backend cần đang chạy và cho phép đúng origin người dùng mở trong `CORS_ALLOWED_ORIGINS` của gateway, chat và notification. Ví dụ `http://127.0.0.1:3000` khác `http://localhost:3000`; WebSocket vẫn gửi `Origin` qua reverse proxy. Với Caddy mẫu ở bước 3, mở `http://localhost:3001` và cho phép origin đó trong CORS. Khi phát hành HTTPS, dùng chính origin HTTPS đó trong allowlist, browser dùng WSS; không mở wildcard.

## 3. Bật Caddy Next.js cục bộ khi cần

Mẫu [Caddyfile.example](Caddyfile.example) là tùy chọn, nghe cổng 3001: API/WS đi gateway 8080 trước, các request còn lại đi Node 3000. Caddy tự hỗ trợ WebSocket upgrade.

```text
caddy validate --config infra/no-docker/nextjs/Caddyfile.example --adapter caddyfile
caddy run --config infra/no-docker/nextjs/Caddyfile.example --adapter caddyfile
```

Trên Windows có thể dùng `.runtime/tools/caddy/caddy.exe` đã cài bởi bộ runtime. Caddy dùng `GATEWAY_UPSTREAM` dạng `127.0.0.1:8080` (**không** có scheme), khác biến cùng tên của Next dùng URL có `http://`. Chạy Caddy ở terminal riêng với biến này bỏ trống để dùng mặc định, hoặc đặt đúng định dạng. `NEXTJS_UPSTREAM` của Caddy mặc định `127.0.0.1:3000`.

## 4. Cài service Linux tùy chọn

Đặt một checkout UI đã chọn tại `/opt/doan/ui`, gồm `frontend-web` và `infra/no-docker/nextjs`; tài khoản `doan` hiện có cần quyền ghi thư mục UI để tạo `.next`/cache. Cài Node 24 trên host, xác nhận đường dẫn Node (`command -v node`) rồi sửa `ExecStart` trong mẫu nếu khác `/usr/bin/node`.

```bash
cd /opt/doan/ui/frontend-web
sudo -u doan npm ci --no-audit --no-fund
cd /opt/doan/ui
sudo -u doan env GATEWAY_UPSTREAM=http://127.0.0.1:8080 node infra/no-docker/nextjs/run.mjs build
sudo install -m 644 infra/no-docker/nextjs/doan-nextjs.service /etc/systemd/system/doan-nextjs.service
sudo systemctl daemon-reload
sudo systemctl enable --now doan-nextjs
sudo systemctl status doan-nextjs --no-pager
```

Log: `sudo journalctl -u doan-nextjs -n 100 --no-pager`. Sửa source cần build lại rồi `sudo systemctl restart doan-nextjs`. Tránh build trực tiếp vào `.next` đang phục vụ: `sudo systemctl stop doan-nextjs` trước khi thay source/build; cách này có khoảng dừng ngắn. Cần giữ một bản source/build đã chạy tốt để phục hồi thủ công nếu build mới thất bại. Không thêm hệ thống release UI mới trong phạm vi này.

**Backend auto-update không đóng gói hoặc triển khai UI**: `infra/azure/package_backend.py` vẫn chỉ gói backend/config/infra. Cài Node, chuyển checkout UI, `npm ci`, build, service và cập nhật UI là thao tác riêng. Next service không nằm trong danh sách Java services nên không bị agent stop/start khi promote backend.

## 5. Giữ route Next qua lần auto-update backend

Chỉ làm trên máy chủ khi đã chọn phục vụ Next bằng chính domain backend và Node service đã chạy. Mặc định `deploy_agent.py` tiếp tục tạo fallback `respond 404`. Flag opt-in `/opt/doan/config/nextjs-upstream` cho phép agent giữ fallback tới Node qua promote/rollback sau này; chỉ nhận `127.0.0.1:PORT` hoặc `localhost:PORT` (1024–65535). Config lỗi chặn trước khi dừng service Java.

1. Cài phiên bản agent từ checkout đã review; installer cũ không tự lấy thay đổi agent theo mỗi backend release:

   ```bash
   cd /opt/doan/ui
   sudo install -m 700 infra/azure/deploy_agent.py /opt/doan/deploy-agent/deploy_agent.py
   printf '%s\n' '127.0.0.1:3000' | sudo tee /opt/doan/config/nextjs-upstream >/dev/null
   ```

2. Sao lưu Caddyfile đang dùng, mở `sudoedit /etc/caddy/Caddyfile`, đổi **duy nhất** fallback `respond 404` thành `reverse_proxy 127.0.0.1:3000`. Giữ nguyên domain HTTPS và handle API/WS. Mẫu `:3001` ở bước 3 chỉ dành local, không chép đè domain public.

   ```bash
   sudo cp /etc/caddy/Caddyfile /etc/caddy/Caddyfile.before-nextjs
   sudoedit /etc/caddy/Caddyfile
   sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
   sudo systemctl reload caddy
   ```

3. Kiểm tra `/login`, `/_next/static/...`, `/api/auth/me` (401 khi chưa đăng nhập là hợp lệ), login/feed và chat/notification bằng hai tài khoản. Xác nhận `CORS_ALLOWED_ORIGINS` chứa domain HTTPS người dùng mở. Flag chỉ giữ route frontend; không tự sửa allowlist backend. Khi package backend cho UI và API chung domain, truyền chính origin đó làm frontend origin của package.

Tắt opt-in: xóa riêng flag bằng `sudo rm /opt/doan/config/nextjs-upstream`, phục hồi Caddyfile đã sao lưu hoặc đổi fallback về `respond 404`, validate rồi reload. `sudo systemctl disable --now doan-nextjs` nếu không dùng Node nữa. Xóa flag chỉ đổi template của lần promote sau, không tự reload Caddy hiện tại. Rollback của agent vẫn phục hồi nguyên Caddyfile đã backup khi promote lỗi.

## Kiểm tra và CI

```text
node --test infra/no-docker/nextjs/run.test.mjs
python -m unittest discover -s infra/azure/tests -v
```

Job CI `nextjs-ui` dùng Node 24, lockfile, runner check, lint, unit contracts, typecheck, build Webpack và browser fixture HTTP/STOMP qua Python/Chrome. Build CI trỏ fixture localhost; job production deploy Next.js Worker chỉ chạy sau khi UI và backend checks đều qua. Fixture chứng minh hợp đồng tích hợp browser; kiểm thử với Java native thật vẫn cần backend và tài khoản thực tế ở môi trường triển khai.
