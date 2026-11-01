# UI web Next.js — đã nối backend Java native

Giữ UI Next.js/React/TypeScript của Thiên Sơn (BoyinGlasses), dùng backend Java/Kafka/cloud stores hiện có, **không Docker**. Đã thay tài khoản và dữ liệu demo trong luồng nghiệp vụ bằng API/JWT thật. Ảnh artwork trên trang đăng nhập vẫn là asset giao diện.

## Chạy ở máy hiện tại

Cần Node24 và npm. Backend native phải đang hoạt động; gateway trong `.runtime/env.ps1` của máy kiểm tra là **18080**, khác mặc định8080 của template.

```powershell
cd frontend-web
npm ci --no-audit --no-fund
$env:GATEWAY_UPSTREAM = 'http://127.0.0.1:18080'
npm run dev
```

Mở `http://127.0.0.1:3000`. Sao chép `.env.example` thành `.env.local` và đặt cổng gateway thực nếu muốn giữ cấu hình local. File `.env.local` không đưa vào Git. `npm run dev` và `npm run build` dùng Webpack để thống nhất đường chạy đã kiểm tra trên Windows.

Backend phải cho phép đúng origin UI trong `CORS_ALLOWED_ORIGINS` ở gateway/chat/notification. Launcher local mở UI tại `http://localhost:3000`; cấu hình CORS phải cho phép origin đó. Không mở wildcard.

Production native:

```powershell
$env:GATEWAY_UPSTREAM = 'http://127.0.0.1:18080'
npm run build
npm run start
```

Gateway được ghi vào rewrites lúc build; thay URL cần build lại. Không phát hành `.next` đã build cho fixture3399. Backend cần chạy riêng; Next không tự bật Azure VM hoặc khởi động các Java service.

## Cấu hình

| Biến | Ý nghĩa |
|---|---|
| `GATEWAY_UPSTREAM` | URL gateway cho Next proxy `/api/*`, `/ws/*`, `/ws-notifications/*`; mặc định8080, máy kiểm tra18080 |
| `NEXT_PUBLIC_API_BASE_URL` | Để trống để dùng cùng origin; chỉ đặt khi API có public origin riêng |
| `NEXT_PUBLIC_WS_BASE_URL` | Để trống để dùng cùng origin; public origin HTTP(S)/WS(S) riêng nếu cần |

Các biến public chỉ chứa URL, không chứa DB password, token hoặc credential cloud. Session nằm trong sessionStorage theo tab; dữ liệu nghiệp vụ lấy từ server. Tùy chọn/bản nháp lưu local theo account ID; file đang chọn chỉ ở bộ nhớ, không coi blob URL là media đã upload.

## Các luồng đã nối

- Auth register/login/restore/refresh/logout; profile, tìm người, friend requests, follow.
- Feed, post detail/CRUD, ảnh/video multipart, bình luận/reply/edit/delete, reaction, saved và report.
- Chat theo conversation ID qua native WebSocket/STOMP; đợi server echo, chống trùng message ID, read state và reconnect; share post bằng URL nội bộ có thể bấm.
- Notification IDs/read từ server; stories/feed/view/react/reply/upload.
- Explore dùng group→post→comment; title/body thảo luận ghép trong post content theo `title + "\n\n" + body`.

Location của composer chỉ là bản nháp vì backend Post chưa có trường đó. Theme, popup preference và draft là tùy chọn trình duyệt. Danh sách following hiển thị các tài khoản đã tải và kiểm tra trạng thái, vì backend chưa có endpoint list-following. Đây không phải nguồn dữ liệu giả thay thế backend.

Upload POST/STORY nhận MIME image/video theo backend; tệp dưới50MiB có chừa overhead multipart. Document/audio chỉ thuộc hợp đồng CHAT. Avatar/media từ backend dùng URL thật, không ghép URL HTTPS vào `/assets/`.

## Kiểm tra

```powershell
npm test
npm run lint
npm run typecheck
```

Browser regression dùng HTTP/STOMP fixture có trạng thái:

```powershell
$env:GATEWAY_UPSTREAM = 'http://127.0.0.1:3399'
$env:NEXT_PUBLIC_API_BASE_URL = ''
$env:NEXT_PUBLIC_WS_BASE_URL = ''
npm run build
npm run test:connected
```

Cần Chrome và Python `websockets`. Runner tự tạo profile Chrome tạm, khởi động/tắt fixture+Next của chính nó. Fixture không phải Java backend thật. Sau kiểm tra, build lại cho gateway thật18080 trước khi dùng production. `npm run test:legacy-demo` giữ bộ kiểm tra lịch sử của bản demo local; không phải bộ nghiệm thu backend hiện hành.

## Nhật ký, báo cáo và vận hành

- [Nhật ký triển khai](../docs/worklogs/2026-10-24-ui-backend-integration.md)
- [Hợp đồng backend](../docs/worklogs/2026-10-24-backend-contracts.md)
- [Báo cáo để trình bày](../docs/reports/2026-10-25-ui-backend-integration.md)
- [Runtime Node/systemd/Caddy không Docker](../infra/no-docker/nextjs/README.md)

CI production phát hành UI Next.js lên Cloudflare Workers sau khi backend và UI checks trên `main` đều qua. GitHub cần biến `AZURE_BACKEND_HOST`, `CLOUDFLARE_ACCOUNT_ID` và secret `CLOUDFLARE_API_TOKEN` có quyền Workers Scripts Edit. URL `workers.dev` xuất hiện trong job deploy.

Các file `MIGRATION.md`, `README.source.md`, `UI_REVIEW.md`, `UI_UX_PLAN.md`, `QA_SOCIAL_PLATFORM.md` và test snapshot trước đây là lịch sử nguồn UI; không dùng làm kết quả kiểm tra mới.
