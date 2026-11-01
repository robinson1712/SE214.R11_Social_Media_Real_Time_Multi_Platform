# Tự deploy backend Java từ main

## Quy tắc đã chọn

- Push/merge vào `main` của `Jandz01/DoAn1` chạy CI.
- Chỉ khi CI thành công, workflow `Publish backend release` tạo gói source/env private và ghi con trỏ bản mới nhất vào Azure Blob.
- GitHub dùng OIDC, chỉ có quyền Blob Data Contributor trên container `releases`. Không có SSH key, Run Command, quyền bật/tắt/resize/xóa VM.
- VM có managed identity chỉ đọc container đó. Bộ cập nhật trên VM kiểm tra trước khi khởi động ứng dụng và mỗi 2 phút khi máy đang chạy.
- VM đang tắt sẽ giữ nguyên trạng thái tắt. Bạn bật từ Azure Portal hoặc script; khi khởi động, VM lấy bản đã test mới nhất.
- Bước khởi động ứng dụng và updater dùng chung khóa để không chạy chồng nhau. Khi bật máy, chờ vài phút; nếu có bản backend mới thì cần thêm thời gian build.
- UI/docs-only thay đổi không làm restart Java nếu nội dung backend/config không đổi.

## Phạm vi tự động

Backend source, dependency Maven, gateway routes, cấu hình service và credential đã khai báo được đóng gói lại. Mô-đun mới được phát hiện từ root POM khi có plugin Spring Boot và `application.yml` chứa numeric `server.port` không trùng. Các module không phải executable Spring Boot không bị chạy như service.

Service PostgreSQL phải có profile cloud-free dùng `CLOUD_POSTGRES_JDBC_URL`; schema mặc định là `sma_<tên bỏ -service, thay - bằng _>`. MongoDB dùng `CLOUD_MONGODB_URI` và credential key mặc định `MONGODB_<TÊN_SERVICE>_URI`. Missing credential/port/module cấu hình sẽ chặn package; hệ thống không tự đoán password/API key.

Nếu cần tên/schema/port khác quy ước, thêm `infra/azure/service-overrides.json`, ví dụ:

```json
{
  "new-feature-service": {
    "port": 8097,
    "store": "postgres",
    "schema": "sma_new_feature",
    "env_prefix": "NEW_FEATURE",
    "environment_keys": ["EXTERNAL_API_KEY"]
  }
}
```

Khai báo credential tương ứng trong env hiện dùng, thêm module vào POM và gateway route vào source. Đó là cấu hình trong commit; không cần Connect/SSH để tự tạo systemd unit.

SQL init schema trong repo được áp dụng khi thay đổi. Các migration `.sql` trong `infra/azure/migrations` chạy theo tên file, một lần mỗi file và kiểm tra checksum. Không sửa nội dung migration đã áp dụng; tạo file mới. Rollback ở đây khôi phục application/config/unit, **không tự đảo ngược dữ liệu hoặc schema SQL**.

## Deploy và rollback

VM kiểm checksum gói, build clean bằng Java 17/Maven với user `doan` không phải root, dựng env/unit cho tất cả service, lưu cấu hình cũ, restart theo thứ tự và kiểm tra từng health endpoint. Khi health lỗi, khôi phục unit/config/bản JAR trước. Giữ bản hiện tại và bản tốt trước để tiết kiệm ổ đĩa. Không tự tăng cấu hình VM hoặc nâng cấp subscription nếu thiếu RAM/quota.

Theo dõi trên GitHub Actions phần CI và Publish backend release. Trên VM có:

```bash
sudo systemctl status doan-auto-update
sudo journalctl -u doan-auto-update -n 30 --no-pager
sudo cat /var/lib/doan-deploy/state.json
```

Log chi tiết từng bản ở `/var/log/doan-deploy/<commit>.log`, có thể chứa thông tin backend; không đăng log nguyên bản ra công khai. Kho Blob private chứa env phục vụ backend, không phải assets Cloudflare hoặc artifact GitHub công khai.

## Trạng thái cài đặt

Cloud identities, federation, quyền Blob và GitHub variables đã được cấu hình. Bộ package/updater đã qua 7 regression test local và GitHub CI, gồm module mới, missing credential, archive traversal, digest và rollback.

Agent đã được cài trên VM ngày 06/10/2026. Workflow đã phát hành gói private; VM tự tải, kiểm checksum, build và deploy thành công. Sau reboot, log xác nhận updater chạy trước ứng dụng, timer vẫn hoạt động, toàn bộ service health UP và HTTPS hoạt động. Xem [báo cáo kiểm tra](AUTO_DEPLOY_VERIFICATION_2026-10-06.md).

Không có tác vụ tự bật VM. Sau lần cài và kiểm tra được chủ VM cho phép, VM được Deallocate lại. Khi bạn bật VM lần sau, cơ chế này đã sẵn sàng sử dụng.
