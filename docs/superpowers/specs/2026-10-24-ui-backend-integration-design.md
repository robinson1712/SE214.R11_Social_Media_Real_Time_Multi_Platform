# Thiết kế nối UI Next.js với backend native

## Ý định đã được người dùng yêu cầu

Biến UI của Thiên Sơn thành frontend cho backend Java hiện có, giữ tech stack Next.js/React/TypeScript và cách chạy không Docker. Có nhật ký trong lúc làm để chủ đồ án báo cáo. Khi tích hợp và kiểm tra xong, tạo tài liệu bàn giao và mở chat mới thực hiện review.

## Phạm vi hoàn thành

Nối tất cả thao tác đã có UI và có API tương ứng: đăng nhập/đăng ký/khôi phục phiên/đăng xuất; profile; feed và chi tiết bài viết; đăng/sửa/xóa bài, upload media, bình luận/reply, reaction, saved/report/share; bạn bè/follow; chat/lịch sử/realtime; thông báo/read; stories; explore/discussion bằng các tài nguyên group/post/comment nếu hợp đồng phù hợp. Media/profile hiển thị URL thật; xử lý loading/empty/401/lỗi HTTP và mất kết nối.

Các thao tác demo không có API tương đương phải được ghi vào ma trận và hiển thị trạng thái trung thực. Không tạo backend mới chỉ để giữ mô phỏng. Không coi thao tác cập nhật state local là đã lưu server. Giữ nguyên các màn hình và phần trình bày có thể tái sử dụng; chỉ điều chỉnh điều khiển/nhãn cần thiết cho tích hợp.

## Kiến trúc được chọn bởi implementer

1. Một HTTP client TypeScript dùng `fetch`, hiểu `ApiResponse`, Bearer token, refresh một lần khi 401 và hủy phiên nếu refresh thất bại. Token chỉ tồn tại phía browser; không đặt backend secret vào bundle. Tránh lưu dữ liệu tài khoản trong snapshot demo chung.
2. DTO adapter ánh xạ backend sang UI; giữ ID thật cho user/post/comment/conversation/story/group, display name chỉ để hiển thị. Avatar/media nhận URL HTTPS và fallback asset cục bộ. Không suy quyền server từ tên hiển thị.
3. Social provider quản lý phiên, tải dữ liệu và gọi các action API. Component gọi action rõ ràng, đợi kết quả để đóng form/báo thành công; không ghi nghiệp vụ trực tiếp bằng setters.
4. STOMP trên WebSocket cho chat `/ws/websocket` và notification `/ws-notifications/websocket`, đăng nhập bằng JWT, subscription theo người dùng. Ngắt kết nối khi logout; xử lý reconnect và deduplicate message. Có thể dùng REST để gửi tin nếu backend hỗ trợ; nhận realtime vẫn theo giao thức backend.
5. Node native cho Next.js. Local reverse proxy hoặc Next rewrites cho API; WebSocket có API origin riêng hoặc Caddy proxy. Runtime production có systemd/Caddy mẫu và hướng dẫn, không thay đổi dịch vụ cloud đang chạy khi chưa có bản được kiểm tra.

## Giá trị cố định và ràng buộc

- Giữ dependency/lockfile hiện có trừ khi có lý do bắt buộc được ghi lại.
- Build kiểm tra bằng `next build --webpack` do baseline Turbopack lỗi trên Windows; ghi rõ lựa chọn này.
- Upload theo giới hạn multipart backend 50 MB và whitelist/purpose từng loại, không gửi `blob:` URL lên server.
- API origin mặc định local phải khớp cổng gateway native hiện có; cho phép cấu hình origin production bằng biến môi trường công khai chỉ chứa URL.
- CORS/WS origin phải chính xác; không mở wildcard để chữa lỗi tích hợp.
- Giữ các thay đổi local có sẵn, không đưa secrets vào commit, không push/main merge/publish ngoài yêu cầu hiện tại.
- Tài liệu dùng tiếng Việt, ghi những gì đã làm, lý do, lệnh kiểm tra, kết quả và mức xác minh.

## Kiểm tra nghiệm thu

- Test contract HTTP/session/DTO và lỗi bằng công cụ có sẵn; kiểm tra RED trước khi viết logic tương ứng.
- Test UI với backend fixture có request thực để bắt lỗi payload và thao tác giả thành công; ghi rõ fixture không phải backend thật.
- Chạy lint, typecheck, production build, HTTP smoke và bộ test hiện hành phù hợp sau khi cập nhật hợp đồng demo cũ.
- Nếu backend native thật chạy được trong môi trường, kiểm tra luồng login/feed/post/media và hai tài khoản chat; không bật VM đang tắt chỉ để làm bước này.
- Handoff ghi commit/diff, command, kết quả, cách chạy, phần còn phụ thuộc môi trường; chat review mới được yêu cầu kiểm tra độc lập, không tin riêng lời tóm tắt.

## Lựa chọn còn giao implementer

Cách tách file, tên action, chiến lược tải dữ liệu theo màn hình, thao tác optimistic khi có rollback đầy đủ, và cách quản lý runtime native được chọn theo code hiện hữu và `ponytail`. Các chi tiết này không thay đổi yêu cầu giữ UI/stack và dùng backend thật.
