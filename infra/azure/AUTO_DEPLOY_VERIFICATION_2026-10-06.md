# Kiểm tra tự deploy — 06/10/2026

## Kết quả thực tế

- CI/CD cho commit `89b4e74e592ba3dc80e1cbaf273990cf641d2a7e`: success, bao gồm Maven tests và 7 regression test updater.
- Publish backend release: success, run `37410823096`, lần 2 sau khi sửa OIDC subject đúng immutable owner/repository IDs đã đối chiếu với GitHub API.
- Gói Blob private 257658 byte; SHA256 tải xuống khớp con trỏ latest: `873b2d6eb89c742eed8afabe5b897bf6dc58b395b1419e7f5a65257b31738c40`.
- Updater trên VM tự tải gói, build clean bằng Java 17/Maven và deploy; state `healthy`, active revision đúng commit trên.
- Tất cả 19 service hiện tại báo `UP` sau deploy và sau reboot.
- UserProfileController trong JAR deployed có MethodParameters. API nội bộ `/internal/posts/batch` với query String không khai báo tên trả HTTP 200, data rỗng; không tạo hoặc sửa dữ liệu test.
- Sau reboot, updater chạy từ 7.34 đến 7.94 giây; boot ứng dụng bắt đầu ở 8.40 giây. Log có `DEPLOY_UNCHANGED` cho đúng revision.
- Timer enabled và tiếp tục kiểm tra sau reboot. Systemd units qua `systemd-analyze verify`; boot script và updater dùng chung deployment lock.
- HTTPS sau boot: protected API 401, login validation 400, actuator public 404, đúng kỳ vọng.

## Quyền và phạm vi

GitHub identity chỉ được ghi private Blob container `releases`; VM managed identity chỉ được đọc. Không cấp GitHub quyền SSH, Run Command hoặc bật/tắt VM. Blob public access và shared-key access đều tắt. Credentials trong gói dùng cho backend, không đưa vào Cloudflare assets.

Các module mới cần root Maven module/plugin, port không trùng và cloud profile/credential tương ứng. Missing config chặn publish. Rollback regression test kiểm tra khôi phục application units/Caddy khi health lỗi; chưa cố tình gây deployment hỏng trên VM live. Migration SQL không được tự đảo ngược khi rollback application.

Chưa kiểm tra một lượt tất cả tính năng bằng tài khoản người dùng trên trình duyệt. Kết quả trên xác nhận build/deploy, boot, health, HTTPS và lỗi parameter metadata.

## Trạng thái cuối

Chủ VM cho phép bật tạm để cài và kiểm tra, rồi Deallocate lại. VM tắt không tự bật bởi commit. Khi chủ VM bật máy, nó lấy bản mới nhất trên main đã vượt qua CI. Các commit sửa hướng dẫn/installer sau báo cáo này vẫn theo cùng workflow; revision deployed ở trên là bản đã kiểm tra trực tiếp.
