# Chuyển CI/CD sang repository SE214

Repository chính: `robinson1712/SE214.R11_Social_Media_Real_Time_Multi_Platform`.
Nhánh tích hợp: `ui-ha-tang`; production chỉ phát hành từ `main`.

## Nội dung chuyển

- Frontend Next.js/React và các thay đổi tích hợp backend hiện có.
- CI kiểm tra frontend, backend và phát hành Cloudflare Workers.
- Workflow backend nạp `BACKEND_ENV_PS1` từ GitHub Secret trước khi export settings, rồi xóa các file credential tạm trong bước cleanup.
- Runtime native không phụ thuộc Flutter, Docker hoặc launcher BAT đã loại bỏ.
- Giữ hạ tầng và dữ liệu hiện tại. Nhánh này không migrate dữ liệu, sửa schema hay tự bật VM.

Code được chuyển trên nền `main` của repo môn học. Không merge lịch sử nhánh nguồn chứa `.runtime/env.ps1`; file credential thật không nằm trong commit hoặc bản vá. Không đưa `.runtime`, `.wrangler`, private workspace metadata hoặc artifact báo cáo cá nhân vào release source.

## Cấu hình repository

GitHub Actions Secrets:

- `CLOUDFLARE_API_TOKEN`: quyền deploy Worker trong account đã chọn.
- `BACKEND_ENV_PS1`: toàn bộ cấu hình PowerShell backend, định nghĩa `$CloudEnvironment`. Không commit giá trị này, kể cả khi repo private.

GitHub Actions Variables:

- `CLOUDFLARE_ACCOUNT_ID`, `AZURE_BACKEND_HOST`, `AZURE_PAGES_ORIGIN`.
- `AZURE_BACKEND_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AZURE_RELEASE_STORAGE_ACCOUNT`.
- `AZURE_BACKEND_DEPLOY_ENABLED=false` trong lúc chuẩn bị; chỉ chuyển thành `true` khi được duyệt phát hành.

Azure federated credential phải khớp subject thực của repo SE214 và branch `main`; credential dành cho repo Jandz không thay thế được trust này. Dùng lại identity và tài nguyên hiện có chỉ khi người quản lý đã cấp quyền phù hợp.

## Thứ tự chuyển chính thức

1. Tắt `CI/CD` và `Publish backend release` ở repo nguồn; đợi các lượt deploy đang chạy kết thúc.
2. Push `ui-ha-tang`, mở PR vào `main` repo SE214. Pull request chỉ chạy kiểm tra; chưa merge hoặc bật backend deploy.
3. Kiểm tra CI, secrets, variables và Azure trust. Xác nhận Worker production vẫn là `doan1-next` và backend/CORS dùng đúng origin hiện tại.
4. Sau khi duyệt, bật backend release và merge PR. Merge/push `main` có thể phát hành Worker ngay; `AZURE_BACKEND_DEPLOY_ENABLED` chỉ điều khiển backend, không khóa frontend deploy.
5. Xác nhận CI, Worker và backend release/VM updater; thử login, upload, bài viết, chat và thông báo. Khi bản mới hoạt động, thu hồi quyền phát hành của repo nguồn nếu không còn cần.

Giữ tên workflow `CI/CD`, vì `backend-release.yml` được kích hoạt bằng `workflow_run` theo tên đó. Nếu đổi branch production hoặc URL, cập nhật đồng bộ trigger, kiểm tra latest commit, OIDC subject và CORS/build variables.

## Quay lại đường phát hành cũ

Nếu cần rollback quyền publisher, tắt deploy từ repo SE214 trước, chờ lượt đang chạy kết thúc, rồi mới bật lại repo nguồn. Không để hai repo cùng ghi `latest.json` hoặc deploy vào cùng Worker. Rollback application không tự rollback database schema; bản chuyển này không chủ động thực hiện thay đổi schema.
