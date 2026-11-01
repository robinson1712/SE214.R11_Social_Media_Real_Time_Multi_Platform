# QA nền tảng xã hội — 11/10/2026

Phạm vi được chọn: hoàn chỉnh trải nghiệm hiện có, lưu dữ liệu trên trình duyệt. Đây là ứng dụng một người dùng với dữ liệu mẫu; không phải bản tương đương toàn bộ Instagram hay dịch vụ nhiều tài khoản.

## Đối chiếu luồng

| Luồng | Kết quả trong phạm vi hiện tại |
| --- | --- |
| Đăng nhập | Kiểm tra tài khoản mẫu, báo lỗi, ẩn/hiện mật khẩu, bàn phím và mobile; không tạo phiên xác thực thật |
| Bảng tin | Recent/Friends/Popular, bài của bạn bè và tác giả đang theo dõi, trạng thái trống |
| Tạo bài | Văn bản, ảnh, video, tài liệu, vị trí và nhãn đối tượng; bản nháp được lưu |
| Bài viết | Đường dẫn trực tiếp, sửa bài của mình, hủy/xác nhận xóa, ngày đăng được giữ lại |
| Tương tác | Đổi/gỡ cảm xúc không cộng trùng, lưu/bỏ lưu, sao chép liên kết |
| Bình luận | Sắp xếp, trả lời lồng nhau, sửa bình luận của mình, xóa cả nhánh có xác nhận, phục hồi focus |
| Chia sẻ | Chọn nhiều bạn bè, hủy không gửi, mỗi người nhận một bản chụp có liên kết đến bài |
| Tin nhắn | Tìm kiếm, mở cuộc trò chuyện, xóa badge chưa đọc, gửi văn bản và chia sẻ; bản nháp riêng từng người |
| Hồ sơ | Bài/ảnh/đã lưu, chỉnh sửa thông tin và đồng bộ tên tác giả qua các luồng |
| Quan hệ | Chấp nhận/từ chối yêu cầu, theo dõi/bỏ theo dõi, đồng bộ trạng thái và số lượng |
| Stories | Xem, chuyển, vuốt, tạm dừng, tim, trạng thái đã xem và trả lời vào Messages |
| Khám phá/ảnh/video | Cộng đồng, thảo luận, tìm kiếm, bộ lọc, xem ảnh/video từ bài và hồ sơ, quay về nguồn |
| Notifications | Thay mục Media; tin nhắn đến và lời mời đang chờ, bộ lọc chưa đọc, đánh dấu đã đọc, lưu trên trình duyệt và mở nội dung liên quan |
| Cài đặt | Hồ sơ, theme, đối tượng mặc định, phản hồi lưu và cảnh báo lỗi lưu trữ |
| Điều hướng/mobile | Tải trực tiếp, Back/Forward, trang không tồn tại, focus, bàn phím, sáng/tối và 320–1440px |
| Dữ liệu | IndexedDB, Blob của tệp đính kèm, khôi phục sau reload, chống ghi đè từ tab cũ và giữ dữ liệu lỗi để phục hồi |

Instagram dùng Reels theo luồng video dọc và cho phép tương tác/chia sẻ; ứng dụng này hiện có video đính kèm và thư viện, chưa có trình biên tập hay feed Reels riêng. [Nguồn Meta: Reels](https://about.fb.com/news/2020/08/introducing-instagram-reels/).

Instagram có chia sẻ nội dung qua tin nhắn và repost được ghi công tác giả. Bản hiện tại hỗ trợ chia sẻ bài vào các cuộc trò chuyện cục bộ, chưa có repost lên hồ sơ. [Nguồn Meta: kết nối và repost](https://about.fb.com/news/2025/08/new-instagram-features-help-you-connect/).

Quyền riêng tư trên Instagram được thực thi giữa các tài khoản. Các nhãn Public/Friends/Only Me ở bản một người dùng không phải cơ chế phân quyền. [Nguồn Meta: tài khoản riêng tư](https://about.fb.com/news/2021/07/instagram-safe-and-private-for-young-people/).

## Những lỗi đã sửa

- Tải lại làm mất bài, tương tác, tin nhắn, quan hệ và hồ sơ; URL Blob cũ không mở lại được.
- Bản nháp bài/tệp và tin nhắn mất khi rời trang; tin nhắn chưa có bản nháp riêng theo người nhận.
- Bài và bình luận của mình thiếu thao tác sửa; bình luận thiếu xóa nhánh có xác nhận.
- Theo dõi chưa ảnh hưởng bộ lọc Friends; báo cáo bài chưa tạo bản ghi thực.
- Dữ liệu ở tab cũ có thể ghi đè thay đổi mới; lỗi/quota lưu trữ chưa có phản hồi và phục hồi an toàn.
- Bài đã lưu vẫn ghi “Just now”; hủy xóa bình luận chưa trả focus về nút mở xác nhận.

## Bằng chứng kiểm thử

Kết quả cuối trên production `127.0.0.1:3001`: **16/16 nhóm PASS**, **140/140 trường hợp ma trận PASS**, **4/4 kiểm tra sidebar PASS**, không có ngoại lệ trình duyệt chưa được xử lý. `lint`, `typecheck`, `build` và bộ kiểm thử đăng nhập riêng đều kết thúc với exit code 0. Đây là tỷ lệ của bộ kiểm thử hiện tại, không phải chứng nhận tương đương 100% tính năng Instagram.

Lệnh và kết quả cuối được ghi trong `tests/social_platform_qa_final.log`, `tests/social_platform_final_results.json` và `tests/social_platform_login.log`. Bộ kiểm thử bao gồm 16 nhóm, ma trận 140 trường hợp và bốn kiểm tra chiều cao sidebar. Các kiểm thử lỗi lưu trữ dùng API trình duyệt công khai để mô phỏng quota, quyền bị chặn và bản ghi hỏng; không dùng trạng thái nội bộ React.

Ảnh kiểm chứng thao tác bình luận trên mobile: `screenshots/next_content_controls_mobile.png`. Bộ ma trận còn lưu ảnh từng trang/kích thước/theme.

## Giới hạn

Cập nhật thay Media bằng Notifications: **17/17 nhóm PASS trong một lần chạy**, **140/140 trường hợp giao diện PASS**, **4/4 kiểm tra sidebar PASS**; `lint`, `typecheck`, `build` đều exit 0. Bằng chứng: `tests/notifications_results.json`, `tests/notifications_qa.log`, `screenshots/next_notifications_desktop.png` và `screenshots/next_notifications_mobile.png`. Đã kiểm tra đọc từng thông báo, đọc tất cả, bộ lọc chưa đọc, liên kết nội dung, lưu sau reload, giữ dữ liệu cũ và chuyển tiếp URL Media. Stories chuyển sang `/stories`; ảnh/video vẫn xem từ bài và hồ sơ. Không chạy lại bộ login riêng trong lần thay điều hướng này.

Không triển khai backend, đăng ký, khôi phục mật khẩu, xác thực thật, nhận tin nhắn thời gian thực, phân quyền, đội ngũ kiểm duyệt, gọi thoại/video, Reels riêng hoặc đăng Stories mới. Stories hiện tại vẫn là dữ liệu mẫu. Notifications lấy tin nhắn đến và lời mời từ dữ liệu cục bộ, không nhận hoạt động từ máy chủ hoặc gửi push notification.

Dữ liệu gắn với browser profile và origin, bao gồm cổng. Xóa site data sẽ mất dữ liệu; không có đồng bộ cloud. Khi có xung đột tab, cần sao chép văn bản chưa lưu rồi reload. Chỉ xác minh trên Windows Chrome và viewport/touch mô phỏng; chưa kiểm tra Firefox, Safari, thiết bị thật hoặc trình đọc màn hình. Không chạy lại bản HTML/JS cũ vì mã đó không thay đổi.
