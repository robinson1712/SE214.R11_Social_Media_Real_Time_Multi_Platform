# Nhật ký nối màn hình và viewers — 24/10/2026

## Phạm vi

Giữ layout và các lớp CSS hiện có của Thiên Sơn. Thay các cập nhật dữ liệu mô phỏng trong `screens.tsx` và `viewers.tsx` bằng action bất đồng bộ từ social provider. Không sửa provider, adapter, API client hoặc dependencies trong phần việc này.

## Thay đổi

1. Messages dùng user ID cho URL, conversation key, draft và action. Deep link tải profile + mở conversation thật; bỏ nhãn “sent locally”. New-message picker chỉ đóng/chuyển trang sau khi mở conversation thành công. Search picker gọi tìm kiếm server có debounce 300 ms.
2. Friends dùng user ID cho friends/requests/following, accept/decline, follow, gửi lời mời và unfriend. Following được ghi rõ là tập profile đã tải vì backend chưa có API liệt kê toàn bộ graph.
3. Explore nhận danh sách category động từ group backend; group ID dùng làm key/route, group name dùng làm nhãn. Join/leave gọi action. Discussion tạo qua group post; chỉ cho chọn group đã tham gia. Topic deep link tải dữ liệu và reply qua comment action.
4. Profile tải theo user ID và lọc bài/media theo authorId. Settings chỉ cập nhật profile qua server, bỏ kiểm tra tên trùng và việc đổi tên mọi bài/chat ở client. Theme/default audience/notification preference vẫn lưu trên browser.
5. Notifications dùng ID/read state server. Stories bỏ dữ liệu demo cố định, đọc feed server, ghi view/reaction/reply bằng action; hỗ trợ image/video, guard story biến mất và giữ motion/keyboard/focus. Màn Stories có file input tạo story thật, giới hạn 50 MB và whitelist image/video; upload/server action do provider thực hiện; giữ file và có nút thử lại khi publish lỗi. Avatar/cover gọi `assetUrl` hỗ trợ URL thật và fallback.

Form mutation có trạng thái pending/error; giữ dữ liệu khi lỗi và chỉ clear/đóng sau success. Khóa input đang gửi để thành công không xóa nội dung mới nhập trong lúc đợi.

## Kiểm tra

- Đã đọc `frontend-web/AGENTS.md` và tài liệu Next.js 16.4 cài sẵn về Link/useRouter trước khi sửa.
- `npx eslint src/components/screens.tsx src/components/viewers.tsx`: PASS, 0 lỗi/0 cảnh báo.
- `python -m py_compile frontend-web/tests/test_connected_screens.py`: PASS.
- `git diff --check` trên các file phần việc: PASS.
- `npm run typecheck`: lần kiểm tra đầu đỏ vì provider đang được root thay song song, chưa xuất actions mới. Cần chạy lại sau khi ráp provider.
- Đã thêm `tests/test_connected_screens.py`, export `async run(browser)` để runner fixture gọi: login thật qua form; settings failure/retry; profile deep link ID; accept friendship; discussion/reply failure/retry; notification read; story view/react/reply STOMP và upload qua file input thật. Chưa ghi nhận browser PASS cho tới khi runner chạy với provider mới.

## Cách xác minh tiếp

Sau production build dùng fixture origin, chạy `python tests/run_connected.py test_connected_screens` trong `frontend-web`. Fixture HTTP/STOMP xác minh payload/request và DOM; không thay thế kết quả chạy backend Java native thật.

## Quy tắc bàn giao

Giữ tiếng Việt, `i-have-adhd` cho cách trình bày, `ponytail full` cho code. Hợp tác tích cực và thực tế: dẫn bằng điều đã làm được, nói rõ điều đã chạy, và gắn điểm còn mở với bước khả thi; không biến giới hạn thành rào cản hoặc tự mở rộng phạm vi thí nghiệm.

## Rà hai khoảng trống cuối — 25/10/2026

- Traced `sharePost` → STOMP `content` chứa URL tuyệt đối → message DTO chỉ có text, không có `sharedPost`. Vì thế nhánh card hiện có chưa giúp người nhận mở bài được. Regression mới gửi URL nội bộ, external URL, `javascript:` và HTML qua form/STOMP thật, tải lại history rồi kiểm DOM và click route.
- Settings dùng uncontrolled `defaultValue`, trong khi `self` ban đầu chưa hydrate. Regression đọc tên/bio/location ngay sau direct navigation trước khi sửa tên, rồi assert bio/location trên server được giữ nguyên khi lưu.
- Đã đọc tài liệu Next.js cài sẵn `Link`/`use client`, hợp đồng fixture và hướng dẫn phạm vi. `python -m py_compile frontend-web/tests/test_connected_screens.py`: PASS. Browser runner đang chờ lượt dùng port 3300/3399 từ root; chưa ghi nhận browser PASS cho các regression mới.
- Đã sửa `MessageText` dùng `URL` có sẵn: chỉ HTTP(S), cùng origin, pathname đúng `/post/:id` mới thành Next Link trỏ về route nội bộ. Mọi chuỗi còn lại vẫn là React text. Settings form thêm `key={self.id}` để reset uncontrolled fields khi profile hydrate.
- `npx.cmd eslint src/components/screens.tsx`: PASS; `py_compile` và scoped `git diff --check`: PASS. Hai lần gọi screens runner trong sandbox (session 82342, 5168) không xuất log/không thấy cổng mở, đã Ctrl+C đúng session và nhả cổng cho chat worker; không coi đó là RED hay PASS. Root đã chỉ đạo tiếp tục fix sau bằng chứng luồng dữ liệu và chạy browser trên build tổng hợp sau.
