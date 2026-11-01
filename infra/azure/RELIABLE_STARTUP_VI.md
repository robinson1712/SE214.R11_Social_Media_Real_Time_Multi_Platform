# Bật VM và sử dụng backend ổn định

## Hành vi sau khi áp dụng

1. GitHub kiểm thử, build Java 17 và đóng gói 19 JAR executable vào release private trên Azure Blob.
2. Khi VM bật, `doan-start` chạy bản đã cài: Kafka → Eureka → Config Server → các service nghiệp vụ → Gateway. Mỗi service phải qua health-check trước bước tiếp theo.
3. Khởi động thành công được ghi `BACKEND_READY` vào journal của `doan-start`. Sau đó timer mới chạy updater; việc lấy bản mới không chặn boot bản hiện có.
4. Updater tải JAR, kiểm checksum, chuẩn bị cấu hình/database, chuyển bản và rollback nếu có lỗi. VM không gọi Maven để build release mới.
5. Service lỗi được retry sau 60 giây, tối đa 3 lần start trong cửa sổ 15 phút; health-check có ngân sách 300 giây. Các lỗi cần xử lý sẽ xuất hiện rõ trong systemd/journal.

VM `Running` là trạng thái máy, chưa phải trạng thái backend. Cần chờ native startup hoàn tất; thời gian thực tế sẽ được đo trong lần cold boot nghiệm thu. Database/Redis/MongoDB phải truy cập được. Giữ VM deallocated khi không dùng theo yêu cầu tiết kiệm credit.

## Cài một lần trên VM hiện có

Đừng chỉ merge workflow mới: updater đang cài là Python cũ và không tự đổi code khi source repository đổi. Phải nâng updater và boot policy trước khi phát hành định dạng JAR mới.

1. Đưa bản sửa đã review lên nhánh `ui-ha-tang`, ghi nhận **full commit SHA**. Bước push nhánh này không publish production; chưa merge `main`.
2. Chờ lượt updater cũ hoàn tất (`ActiveState=inactive` hoặc `failed`). Mở Azure VM → Operations → Run command → RunShellScript. Chạy đoạn dưới sau khi thay `FULL_REVIEWED_COMMIT_SHA` bằng SHA đã review:

```bash
set -e
revision='FULL_REVIEWED_COMMIT_SHA'
bootstrap=$(mktemp /tmp/se214-bootstrap.XXXXXXXX)
trap 'rm -f -- "$bootstrap"' EXIT
curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 \
  "https://raw.githubusercontent.com/robinson1712/SE214.R11_Social_Media_Real_Time_Multi_Platform/$revision/infra/azure/apply-startup-fix.sh" \
  --output "$bootstrap"
bash "$bootstrap" "$revision"
```

Script tải đúng bốn file từ cùng commit, dùng lại storage account trong cấu hình VM, cài updater và boot script, sửa ordering, áp dụng policy cho các unit hiện có và gỡ đúng override tạm `99-se214-recovery.conf`. Nó giữ chung khóa với boot/deploy và từ chối cài nếu đang boot hoặc deploy; không ngắt updater đang chạy. Nó không đổi VM size, NSG, credentials hoặc resource Azure, và không bật/tắt VM. Lần cài đặt này khởi động lại trình điều phối để khởi động các service còn thiếu.

Repository hiện tải được raw source công khai qua HTTPS. Nếu đổi repo sang private, dùng kênh chuyển file đã được xác thực thay vì đưa token vào lệnh/chụp màn hình.

3. Khi `BACKEND_READY` và thử login bản cũ thành công, merge bản sửa vào `main`. CI và Publish backend release chạy; updater mới nhận release JAR. Không bật lại publisher repo Jandz.
4. Sau khi release mới healthy, được phép cold boot VM một lần để nghiệm thu. Khởi động lại VM là thao tác riêng cần được chủ VM cho phép; script cài đặt không thực hiện.

## Nghiệm thu

Trên VM:

```bash
systemctl show doan-start doan-auto-update doan-api-gateway doan-auth-service \
  -p Id -p ActiveState -p SubState -p Result --no-pager
journalctl -u doan-start -n 15 --no-pager -o cat
journalctl -u doan-auto-update -n 15 --no-pager -o cat
```

- Boot phải có `BACKEND_READY`; Gateway/Auth `active/running`.
- Cấu hình mới không có `Wants=doan-auto-update.service`/`After=doan-auto-update.service` trên boot. Updater có `After=doan-start.service`.
- Trong boot không có Maven/javac. Với `latest.json` không truy cập được, bản cài sẵn vẫn phải boot; lỗi kiểm tra update được ghi riêng.
- Kiểm tra qua Worker: gửi login thiếu thông tin phải nhận lỗi ứng dụng dự kiến thay vì 502; tài khoản thật phải đăng nhập được. Kiểm tra thêm feed và chat với người dùng thật.
- Sau cold boot, xác nhận cùng các điều kiện trên và ghi thời gian từ VM Running tới backend ready. Local test không thay thế bước này.

## Khôi phục

Binary updater giữ cơ chế rollback unit/Caddy khi promotion thất bại. Nếu một service bị rate-limit, đọc lỗi gốc trước; sau khi sửa dependency/config dùng `systemctl reset-failed doan-<service>` và khởi động lại `doan-start`. Source-only release sẽ bị updater mới từ chối trước khi dừng backend đang chạy; phát hành commit mới có JAR thay vì xóa trạng thái failed revision để ép compile.

Kiểm tra cục bộ: 18 test hạ tầng qua; build và kiểm định dạng đóng gói đủ 19 JAR, application bytecode Java 17; Bash syntax và PowerShell parser qua; finding quan trọng của review đã xử lý. Các kiểm tra này không xác nhận trạng thái production. Chỉ xác nhận vận hành thật sau khi có kết quả nghiệm thu từ VM và Worker.
