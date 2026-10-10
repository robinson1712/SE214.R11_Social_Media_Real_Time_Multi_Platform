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

## Bản sửa đang chờ triển khai

Lỗi thiếu tên tham số được xác định do Maven trên VM dùng compiler plugin 3.1, không hỗ trợ `parameters`. POM đã được sửa để pin compiler 3.13.0 và Surefire 3.2.5; hai regression HTTP cho path/query binding đã pass trên máy phát triển. VM được giữ tắt theo yêu cầu của chủ đồ án, nên **JAR đang nằm trên VM chưa được cập nhật bản sửa này**. Sau khi bật VM để test tiếp, cần build và redeploy backend trước khi xác nhận lỗi trên cloud đã hết.
