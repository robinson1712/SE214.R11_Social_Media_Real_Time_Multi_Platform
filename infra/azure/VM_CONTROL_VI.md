# Bật / tắt máy chủ đồ án

## Cách dễ nhất: Azure Portal

Mở [VM doan1-backend](https://portal.azure.com/#@/resource/subscriptions/165c7def-d5c5-4d34-8648-cdd073c4381e/resourceGroups/doan1-demo-rg/providers/Microsoft.Compute/virtualMachines/doan1-backend/overview).

- **Tắt để tiết kiệm credit:** Overview → Stop → xác nhận nếu Azure hỏi → đợi **Stopped (deallocated)**.
- **Bật:** Overview → Start → đợi VM Running và các service Java khởi động xong. Backend khởi động tuần tự nên chưa hoạt động ngay khi VM vừa Running.
- **Restart:** khởi động lại VM, không chuyển sang trạng thái dừng tính tiền compute.

Đừng dùng Delete để tắt máy. Đừng chỉ shutdown từ Ubuntu nếu mục tiêu là ngừng tiền CPU/RAM; trạng thái Stopped (allocated) vẫn có thể tính phí.

## Từ terminal trong repo trên máy này

```powershell
./infra/azure/manage-vm.ps1 -Action Status
./infra/azure/manage-vm.ps1 -Action Stop
./infra/azure/manage-vm.ps1 -Action Start
```

`Stop` gọi `az vm deallocate`. Azure CLI phải đăng nhập đúng Azure for Students. Nếu phiên hết hạn, cần đăng nhập CLI lại. SSH key/token được giữ trong runtime local và không phải file giao diện.

## Tắt / bật riêng backend Java trên VM

Chỉ dùng khi đã SSH vào VM:

```bash
# Tắt các service của ứng dụng, giữ VM bật.
sudo systemctl stop 'doan-*'

# Khởi động lại Kafka và các service còn thiếu theo thứ tự.
sudo systemctl restart doan-start

# Khởi động lại riêng gateway khi cần áp dụng cấu hình/JAR mới.
sudo systemctl restart doan-api-gateway
```

Tắt Java/Kafka **không ngừng tính tiền VM**. Muốn tiết kiệm compute thì dùng Stop/Deallocate ở Azure Portal hoặc script PowerShell.

## Runtime local trên laptop Windows

```powershell
./infra/no-docker/scripts/Stop.ps1
./infra/no-docker/scripts/Start.ps1
```

Các lệnh này chỉ điều khiển runtime trên laptop, không bật/tắt Azure VM. Start kiểm tra credential trước khi chạy. Không xóa env hoặc database để tắt máy.

## Cloudflare và chi phí khi VM tắt

Cloudflare Pages vẫn phục vụ giao diện ở `https://doan1-8kk.pages.dev`. Khi VM tắt, đăng nhập/feed/chat/upload không hoạt động vì không có API backend. Có thể yêu cầu trợ lý “bật VM” hoặc “tắt VM” để thao tác nếu phiên Azure còn hợp lệ.

Deallocate ngừng compute; ổ đĩa và IP public vẫn có thể tiêu credit. E2as v4 hiện có giá compute $0.08/giờ; không ai dùng ứng dụng mà VM vẫn Running thì compute vẫn tính phí. [Quy định tính phí Azure](https://learn.microsoft.com/en-us/azure/virtual-machines/states-billing).

## Tự cập nhật khi bật VM

Lỗi thiếu tên tham số đã được sửa bằng compiler 3.13.0/Surefire 3.2.5, deploy và kiểm tra lại trên VM ngày 06/10/2026. Bộ tự cập nhật đã được cài: push vào main repo được liên kết, CI đạt rồi publish release private; VM lấy bản mới khi bật hoặc theo timer mỗi 2 phút nếu đang chạy. Commit không tự bật VM. Khi bật, chờ build/start hoàn tất; không cần thay JAR bằng tay. Xem [cơ chế tự deploy](AUTO_DEPLOY_VI.md) và [hướng dẫn chuyển sang repo chính/dựng mới](SETUP_TEAM_VI.md).
