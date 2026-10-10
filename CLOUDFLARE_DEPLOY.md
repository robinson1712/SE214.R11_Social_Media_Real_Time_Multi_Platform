# Deploy bản Flutter hiện tại lên Cloudflare Pages

Repo đích: `Jandz01/DoAn1` (private). Theo yêu cầu của chủ repo, `.runtime/env.ps1` được đưa vào Git để thử nghiệm. File này dùng cho backend; build frontend không chép env vào assets.

## Trạng thái ngày 06/10/2026

VM đã được Deallocate theo yêu cầu chủ đồ án để tiết kiệm credit. Pages vẫn phục vụ giao diện; API không hoạt động khi VM tắt. Bản sửa lỗi compiler metadata đã được kiểm tra local và chờ cập nhật backend sau khi VM được bật lại. Xem [hướng dẫn bật/tắt](infra/azure/VM_CONTROL_VI.md).

Cloudflare Pages project `doan1` đã nối GitHub `Jandz01/DoAn1`, nhánh `main`, domain `doan1-8kk.pages.dev`. Build dùng `bash infra/cloudflare/build-pages.sh`, output `frontend/build/web`.

Backend đã triển khai trên Azure Students VM `doan1-backend`: Ubuntu 24.04, E2as v4 2 CPU/16 GB, Central India. API origin thật là `https://doan1-165c7def.centralindia.cloudapp.azure.com`. Pages production đã đặt `API_BASE_URL` thành origin này và `BACKEND_PENDING=false`. Xem [cấu hình/bật tắt VM](infra/azure/DEPLOYMENT.md).

API local không được mở công khai và Quick Tunnel chưa được khởi chạy. Người dùng chọn Azure for Students để tránh xác minh thẻ Oracle. Runtime cloud hiện chạy x64 native, không Docker; chưa có kết quả ARM.

Bản giao diện ban đầu commit `710514d` đã deploy thành công qua Git integration. `/`, `/login`, `/flutter_bootstrap.js`, `/main.dart.js` đều trả HTTP 200. Bản tiếp theo dùng backend Azure và bỏ chế độ chờ backend.

Probe chạy trực tiếp trên VM đạt toàn bộ Supabase/MongoDB/Valkey/Storage; 19 Java health endpoint đều UP. HTTPS/CORS/login validation/invalid login, bảo vệ API và chặn actuator public đã kiểm tra đạt. Chưa nghiệm thu đầy đủ CRUD/media/realtime bằng tài khoản thực qua trình duyệt.

Subscription Azure for Students đã Enabled, spending limit On. VM dùng credit sinh viên, không phải miễn phí vô hạn. Không nâng cấp subscription trả phí; xem chi phí và giới hạn trong tài liệu Azure.

## Phần chạy ở đâu

```text
Trình duyệt → Cloudflare Pages (Flutter Web)
            → HTTPS/WSS API trên máy backend
                         → Java JAR + Kafka native
                         → Supabase / MongoDB Atlas / Valkey / Storage
```

19 service là 19 chương trình Java hiện có trong repo, chạy bằng `java -jar`, không cần Docker. Cloudflare Pages phục vụ static frontend; nó không khởi chạy các JAR/Kafka. Cloudflare Containers có thể chạy runtime khác, nhưng cần container image và Workers Paid, nên không thuộc phương án no-Docker/$0 hiện tại.

Để backend hoạt động khi laptop tắt, cần một VM/VPS Linux chạy liên tục. Với máy đang bật, có thể thử tunnel tới runtime local, nhưng đó không phải hosting backend độc lập.

## Liên kết trực tiếp GitHub với Cloudflare Pages

Trong Cloudflare: **Workers & Pages → Create application → Pages → Import an existing Git repository**. Kết nối GitHub và cho Cloudflare GitHub App truy cập repo private `Jandz01/DoAn1`, rồi chọn repo đó.

| Thiết lập Pages | Giá trị |
|---|---|
| Production branch | `main` |
| Framework preset | `None` |
| Build command | `bash infra/cloudflare/build-pages.sh` |
| Build output directory | `frontend/build/web` |
| Root directory | Gốc repo, để trống |
| Build variable `API_BASE_URL` | HTTPS origin thật của backend |

Script cài Flutter 3.47.5 native, giữ lockfile, build Web và chép `_headers`. Sau khi kết nối Git, Pages build/deploy khi push `main`. Cách này không cần Cloudflare API token trong GitHub. Đừng bật thêm `CLOUDFLARE_DEPLOY_ENABLED` của phương án Direct Upload bên dưới khi đang dùng Git integration.

Nếu chưa có HTTPS origin của backend, cần hoàn thành bước đó trước để giao diện deploy gọi được API; một bản static frontend chưa có backend không phải ứng dụng hoàn chỉnh.

## Phương án tùy chọn: Direct Upload qua GitHub Actions

1. Backend có HTTPS origin thật, ví dụ `https://api.example.com`, hỗ trợ cả HTTP và WebSocket. Không dùng `localhost`, không thêm `/api` vào origin.
2. Trên backend, đặt `CORS_ALLOWED_ORIGINS` chứa chính xác origin Pages, ví dụ `https://doan1.pages.dev`, rồi restart Gateway. Trong Atlas thêm IP egress của máy backend; IP laptop chỉ dùng cho runtime local.
3. Tạo Cloudflare Pages project `doan1` (Direct Upload), production branch `main`. Chỉ chọn phương án này nếu không dùng Git integration ở trên.
4. Trong GitHub Settings → Secrets and variables → Actions, đặt:

| Loại | Tên | Giá trị |
|---|---|---|
| Secret | `CLOUDFLARE_API_TOKEN` | Token có quyền Account → Cloudflare Pages → Edit cho đúng account |
| Variable | `CLOUDFLARE_ACCOUNT_ID` | Account ID Cloudflare |
| Variable | `CLOUDFLARE_PAGES_PROJECT` | `doan1`, hoặc tên project đã tạo |
| Variable | `API_BASE_URL` | HTTPS origin của backend |
| Variable | `CLOUDFLARE_DEPLOY_ENABLED` | `true` sau khi backend sẵn sàng |

Workflow hiện có sẽ chạy Maven/native PostgreSQL tests trước. Job Pages build Flutter, nhúng duy nhất API origin công khai và upload assets bằng Wrangler. Push `main` deploy khi đã bật variable; cũng có thể chạy workflow thủ công và nhập API origin. PR không deploy.

## Build/deploy từ Windows

```powershell
.\infra\cloudflare\build-pages.ps1 -ApiBaseUrl 'https://api.example.com'
npx --yes wrangler@4.147.0 login
npx --yes wrangler@4.147.0 pages project create doan1 --production-branch main
npx --yes wrangler@4.147.0 pages deploy frontend/build/web --project-name doan1 --branch main
```

Chỉ tạo project nếu chưa có. Khi có nhiều Cloudflare account, đặt `CLOUDFLARE_ACCOUNT_ID` cho đúng account trước khi chạy CLI. Không build bằng URL ví dụ rồi gọi đó là deploy ứng dụng hoạt động; cần API origin thật.

Pages xử lý SPA fallback khi không có top-level `404.html`, nên `/login` vẫn mở được. API/WebSocket của frontend dùng `API_BASE_URL`, không gọi localhost trên máy người xem. Build cloud sẽ thay output `frontend/build/web`; nếu muốn trở lại Caddy local cùng origin, build lại Flutter không truyền `API_BASE_URL`.

## Kiểm tra sau deploy

- Mở URL Pages bằng mạng ngoài máy backend: `/` và `/login` tải được.
- Đăng ký/đăng nhập, xem feed, upload media và chat/realtime hoạt động qua HTTPS/WSS.
- Kiểm tra CORS, API không trả HTML của SPA, các service/DB/Kafka vẫn hoạt động sau restart.
- Laptop tắt mà API vẫn chạy chỉ đạt khi backend đã chuyển lên máy chủ độc lập.

Public deployment vẫn cần đáp ứng các gate dữ liệu/quyền truy cập trong PLAN và ACCEPTANCE_MATRIX. Kết quả local không tự chứng minh các gate đó.

Nguồn: [Pages static sites](https://developers.cloudflare.com/pages/framework-guides/deploy-anything/), [Direct Upload CI](https://developers.cloudflare.com/pages/how-to/use-direct-upload-with-continuous-integration/), [SPA routing](https://developers.cloudflare.com/pages/configuration/serving-pages/), [Cloudflare Containers](https://developers.cloudflare.com/containers/).
