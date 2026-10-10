# Lấy bản audit để review — 10/10/2026

**Nhánh `codex/team-handoff-audit` dùng để đọc source/test và phối hợp frontend React. Chưa đạt gate merge main hoặc triển khai production.** Backend: 521 test pass, 6 skip; frontend: 5 test pass, analyze/build web pass. Kết quả và điểm chặn: [báo cáo audit](TEAM_HANDOFF_2026-10-10.md).

## 1. Lấy source vào thư mục riêng

Bạn đang có code React chưa push nên chạy lệnh dưới đây ở thư mục cha, không phải bên trong checkout React đang làm:

```bash
git clone --branch codex/team-handoff-audit --single-branch https://github.com/robinson1712/SE214.R11_Social_Media_Real_Time_Multi_Platform.git social-media-review
cd social-media-review
```

Nhánh xuất phát từ commit reverse `a181e73`, lấy snapshot source cá nhân rồi bổ sung sửa lỗi audit. Không nhập lịch sử commit cá nhân có credential. Env thật, Azure login, SSH key, tool local và build output không được bàn giao qua Git. Source React chưa push của bạn không nằm trong nhánh này.

## 2. Xem frontend đang hoạt động

Cài Flutter 3.47.5, chạy từ thư mục `frontend`:

```bash
flutter pub get --enforce-lockfile
flutter run -d chrome --web-port 3001 --dart-define=API_BASE_URL=https://doan1-165c7def.centralindia.cloudapp.azure.com
```

Lệnh trên chạy UI và gọi backend Azure hiện có. VM phải được bật, backend khởi động xong và CORS phải cho phép `http://localhost:3001`. Nếu chỉ xem UI/form khi backend tắt, có thể thêm `--dart-define=BACKEND_PENDING=true`; đây không phải demo đăng nhập/chat thật.

**Push nhánh review không cập nhật JAR trên VM.** Runtime cloud đang có có thể khác source audit. Workflow CI push hiện chỉ nhận `main`, PR vào `main` hoặc manual dispatch; workflow publish backend chỉ nhận CI main thành công. Chưa chạy CI nhánh review và chưa bật/deploy VM trong lần bàn giao này. Cloudflare Git integration cấu hình riêng có thể tạo preview, tùy branch settings; preview cũng không tự deploy backend.

Muốn chạy backend riêng, xem [NO_DOCKER_SETUP.md](../../NO_DOCKER_SETUP.md). Full JDK 17 và các dependency/runtime cần thiết phải được cài riêng. Build/test Java bằng `mvn -B -Pnative-runtime verify`; lệnh này không tự cấp database/credential để chạy ứng dụng. Không copy credential đã lộ từ lịch sử repo cá nhân.

## 3. Điểm đối chiếu khi làm React

| Phần | Source cần đọc |
|---|---|
| Luồng tổng thể/service | [README](../../README.md), [runtime setup](../../NO_DOCKER_SETUP.md) |
| URL API, Bearer token, giới hạn refresh 401 và single-flight | [dio_client.dart](../../frontend/lib/core/api/dio_client.dart), [token_storage.dart](../../frontend/lib/core/auth/token_storage.dart) |
| Login/register/refresh và model | [auth_repository.dart](../../frontend/lib/features/auth/auth_repository.dart), [auth_models.dart](../../frontend/lib/core/models/auth_models.dart) |
| Response envelope `{success,message,data,timestamp}` | [api_response.dart](../../frontend/lib/core/models/api_response.dart) |
| Chat/notification STOMP và upload | [stomp_service.dart](../../frontend/lib/core/ws/stomp_service.dart), [chat_repository.dart](../../frontend/lib/features/chat/chat_repository.dart), [notification_repository.dart](../../frontend/lib/features/notifications/notification_repository.dart), [media_repository.dart](../../frontend/lib/core/api/media_repository.dart) |

React dùng lại API gateway/Java và dữ liệu hiện có. Base URL là origin, không thêm `/api` vào base; từng request tự có path `/api/...`. Không tự gửi `X-User-Id`/`X-User-Roles` thay cho JWT. CORS dùng danh sách origin rõ ràng; nếu React chạy port khác 3001 thì cần cấu hình origin đó hoặc dùng dev proxy, không bật wildcard. Kiểm tra thêm origin của WebSocket nếu dùng chat/notification.

Chỉ port hành vi cần thiết sang React và test lại login → refresh → hết session, feed/post, upload và chat. Flutter HTTP regression nằm trong `frontend/test`; test Flutter không thay thế test React. Quyền đọc media và advisory dependency còn mở trong báo cáo audit.

## 4. Mời thành viên bật Azure VM bằng tài khoản riêng

Có thể dùng Microsoft Entra B2B Guest + Azure RBAC, không chia sẻ email/password chủ subscription. Mời vào directory không tự cấp quyền VM.

1. Azure Portal → Microsoft Entra ID → Users → New user → Invite external user. Nhập email của thành viên; họ chấp nhận lời mời bằng tài khoản riêng.
2. Tạo custom role `DoAn VM Start` với đúng ba Actions: `Microsoft.Compute/virtualMachines/read`, `Microsoft.Compute/virtualMachines/instanceView/read`, `Microsoft.Compute/virtualMachines/start/action`. Định nghĩa role có AssignableScopes ở resource group `doan1-demo-rg`.
3. VM `doan1-backend` → Access control (IAM) → Add role assignment → chọn role này, chọn guest hoặc security group của nhóm; scope cấp quyền là đúng VM.
4. Thành viên đăng nhập Azure Portal, chọn đúng directory và mở [VM doan1-backend](https://portal.azure.com/#@/resource/subscriptions/165c7def-d5c5-4d34-8648-cdd073c4381e/resourceGroups/doan1-demo-rg/providers/Microsoft.Compute/virtualMachines/doan1-backend/overview) → Start. Nếu một số phần của Portal thiếu quyền read phụ trợ, dùng `az vm start`/`az vm get-instance-view` với đúng subscription/resource group/VM; không mở rộng thành Contributor chỉ để bỏ lỗi giao diện.
5. Nếu nhóm cần tắt tiết kiệm compute, chủ subscription có thể bổ sung riêng `Microsoft.Compute/virtualMachines/deallocate/action` và thử quyền. Chỉ Start không cho phép Stop. Xem [bật/tắt VM](../azure/VM_CONTROL_VI.md).

Người cấu hình cần quyền mời guest của directory, quyền `roleDefinitions/write` tại AssignableScopes để tạo role, và `roleAssignments/write` trên VM để cấp role. Quyền subscription và quyền quản trị directory là hai loại khác nhau. Nếu nút invite/cấp role bị khóa, cần người có quyền tương ứng thao tác. Không cấp Owner/Contributor hoặc Virtual Machine Contributor cho nhu cầu chỉ bật máy: Virtual Machine Contributor còn cho phép sửa/xóa VM và chạy script.

Các bước Azure trên là hướng dẫn; chưa tạo guest/role assignment hay kiểm thử bằng tài khoản thành viên trong phiên này. Bật VM tiêu credit của subscription chủ; deallocate ngừng compute nhưng disk/IP có thể tiếp tục tính phí.

Nguồn Microsoft: [mời guest và cấp RBAC](https://learn.microsoft.com/en-us/azure/role-based-access-control/role-assignments-external-users), [custom roles](https://learn.microsoft.com/en-us/azure/role-based-access-control/custom-roles), [Compute permissions](https://learn.microsoft.com/en-us/azure/role-based-access-control/permissions/compute), [Virtual Machine Contributor](https://learn.microsoft.com/en-us/azure/role-based-access-control/built-in-roles/compute#virtual-machine-contributor), [VM states/billing](https://learn.microsoft.com/en-us/azure/virtual-machines/states-billing).
