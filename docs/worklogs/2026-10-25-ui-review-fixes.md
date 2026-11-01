# Sửa phát hiện review UI ngày 25/10/2026

Review độc lập ở chat `01a171de-8de0-7b12-a4a6-4be8ab26281e` đã kiểm tra snapshot `3e6afd6` và ghi 5 phát hiện P2 tại `docs/reviews/2026-10-25-ui-backend-review.md`. Đã đối chiếu lại source trước khi sửa, giữ Next.js/React/TypeScript và hạ tầng native.

| Finding | Nguyên nhân | Thay đổi |
|---|---|---|
| F1 reconnect thiếu tin | Socket nối lại nhưng không GET lịch sử; poll bỏ qua khi connected | Tải bù khi CONNECTED, mark-read cuộc trò chuyện đang mở |
| F2 snapshot xóa tin mới | REST thay toàn bộ mảng/map sau khi MESSAGE đã append | Functional merge theo ID, sắp theo sentAt; giữ conversation realtime ngoài snapshot |
| F3 echo cũ xác nhận SEND mới | Chỉ so content/media, không kiểm tra ID đã biết | Chụp tập ID trước SEND; chỉ echo ID mới và khớp story metadata mới xác nhận |
| F4 bài share trống | Adapter bỏ sharedPostId | Giữ ID và render Link “Xem bài viết gốc”; dùng trang post hiện có xử lý bài gốc không khả dụng |
| F5 notification sai route | Dùng comment ID như post ID | Chỉ POST target tạo post route; COMMENT chưa có mapping nguồn ở notification DTO giữ `/notifications` |

Không thêm dependency, endpoint hoặc thay giao thức STOMP. Hạn chế còn lại của xác nhận SEND: backend chưa có client correlation ID; một echo **ID mới** từ tab khác có cùng content/media/story metadata vẫn không phân biệt được với SEND hiện tại. Bản sửa xử lý đúng lỗi replay ID cũ được review tái hiện; không tự đổi hợp đồng backend trong lượt này.

## Regression và lệnh chạy

- `tests/provider-sync.test.mjs` chạy callback thật của provider sau transpile TSX trong bộ nhớ, dùng stub hooks/HTTP/socket để điều khiển thứ tự. Chỉ thêm export/điểm quan sát trong bộ nhớ, không sao chép thuật toán product. Đây là logic regression, không phải browser/live Java.
- RED: 6/6 test fail đúng assertion: mất tin sau open/refresh; reconnect không GET; echo cũ và sai story vẫn resolve; COMMENT thành post route.
- GREEN: 6/6 đạt sau sửa. Tăng độ phủ cùng test cho ID cũ đã tải qua REST và conversation khác được mở trong lúc snapshot đang chờ.
- `tests/shared-post.test.mjs`: RED 2 fail/1 pass; GREEN 3/3, gồm adapter và render card thật bằng React server renderer.
- `npm run test:unit`: **21/21 đạt**, 0 skip, chạy ngoài sandbox để HTTP/WebSocket loopback hoạt động.
- `npm run lint`, `npm run typecheck`: exit0.
- Build fixture: `GATEWAY_UPSTREAM=http://127.0.0.1:3399 npm run build` (PowerShell dùng `$env:`), Next16.4.3 Webpack đạt.

- Browser production fixture sau sửa: **3/3 nhóm đạt** (posts/screens/chat). Lượt đầu 2/3 vì test bấm tạo topic trong lúc loadGroup còn pending; test đã chờ đúng trạng thái nút enabled trước thao tác, không tăng sleep hoặc sửa product để né lỗi.
- Chưa chạy lại Java/cloud vì không đổi backend và môi trường live đã dừng theo handoff. Sau browser đã build lại gateway thật18080, kiểm tra `.next/routes-manifest.json`: API, WS và WS-notifications đều đúng18080. State native process rỗng, các hash file local người dùng giữ nguyên.

## Snapshot kiểm tra lại

Commit `24e8e7e` chứa toàn bộ bản sửa và regression. Cùng chat review đã kiểm tra lại giới hạn F1–F5, chạy mới9/9 regression đạt và xác nhận có thể đóng cả5 finding; không phát hiện lỗi trực tiếp bổ sung trong phạm vi được giao. Reviewer không build/browser/Java/cloud để tránh ảnh hưởng runtime chung. Tài liệu tổng hợp được cập nhật riêng sau snapshot, không đổi product code. Toàn bộ yêu cầu nối UI, ghi quy trình và mở chat review đã hoàn tất; chưa deploy cloud.
