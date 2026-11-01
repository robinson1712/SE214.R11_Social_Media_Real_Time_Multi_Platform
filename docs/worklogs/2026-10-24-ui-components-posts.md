# Nối UI auth, shell và bài viết — 24/10/2026

Phạm vi: giữ Next.js/React/TypeScript, bố cục/CSS và thư viện hiện có của Thiên Sơn. Áp dụng i-have-adhd khi trình bày, ponytail full khi chọn giải pháp; cộng tác tích cực, ghi rõ kết quả đã chạy và phần còn cần xác minh. Không thêm dependency, không chạm backend, không tự stage/commit.

## Các thay đổi

1. `login-screen.tsx`: thay đăng nhập demo bằng `login(email,password)`, thêm chế độ đăng ký/fullName qua `register`, chặn gửi lặp và giữ thông tin khi HTTP lỗi. Avatar ở phần artwork là hình trang trí từ data cũ; không dùng nó làm phiên đăng nhập. Hộp quên mật khẩu ghi đúng tình trạng backend chưa có API.
2. `app-shell.tsx`: chờ khôi phục phiên rồi bảo vệ màn hình bằng session thật; chuyển về `/login` khi mất phiên. Thêm logout, loading/error/retry, avatar URL server, profile/follow theo ID thật. Recommendations dùng categories của provider (group ID thật), không còn nhóm demo.
3. `composer.tsx`: gọi `createPost` và chỉ xóa draft sau khi API thành công. Giữ File trong attachment cho provider upload trước khi tạo bài; blob URL chỉ dùng preview. Bỏ picker document cho POST; chỉ nhận MIME JPEG/PNG/GIF/WebP/MP4/WebM/MOV dưới 50 MiB. Vị trí được ghi rõ chỉ là draft vì Post backend không có trường location, không gửi location lên server. Khóa điều khiển trong lúc gửi.
4. `post-card.tsx`: nối reaction, saved, report, share, sửa/xóa bài, bình luận/reply/sửa/xóa bình luận bằng action async. Chờ thành công mới đóng form; giữ text/error để retry. Ownership và profile route dùng authorId, chỉ tác giả bình luận được sửa/xóa. Lấy commentCount và reactionCount từ server, không cộng reaction người dùng hai lần. Mở dialog nạp comments thật, có loading/error/reload.
5. `feed.tsx` và `post-detail.tsx`: feed mặc định Recents; không lọc bỏ server feed dựa trên tập friends/following mới nạp một phần; Popular sort theo reaction count server. Tab Friends giải thích đang dùng server feed. Có nút nạp trang tiếp theo. Deep link gọi API theo ID với loading/error/retry và quay lại feed.

## Kiểm tra đã chạy ở nhánh triển khai UI

- Đọc `frontend-web/AGENTS.md`, thiết kế tích hợp, hợp đồng backend và guide `node_modules/next/dist/docs/01-app/03-api-reference/04-functions/use-router.md` trước khi sửa.
- `node_modules/.bin/eslint.cmd src/components/login-screen.tsx src/components/app-shell.tsx src/components/composer.tsx src/components/post-card.tsx src/components/post-detail.tsx src/components/feed.tsx`: pass sau khi sửa effect loading của PostDetail để không setState đồng bộ trong effect.
- `python -m py_compile tests/test_connected_posts.py`: pass.
- `git diff --check -- <sáu component và regression posts>`: pass.

## Regression browser giao cho lượt kiểm tra tích hợp

`tests/test_connected_posts.py` xuất `async run(browser)` để runner chung chạy với HTTP/STOMP fixture và Chrome profile mới. Dùng CDP helpers sẵn có; không đọc source, không chỉnh React internals hoặc seed session/storage. Kiểm tra login lỗi giữ mật khẩu, feed/author ID thật, owner bài không được xóa comment người khác, post/comment lỗi giữ draft và retry, reaction counts/toggle, saved/report payload, sửa bài lỗi giữ form, xóa bài và deep link. Có audit method/path/status từ request log fixture; account thứ hai tạo comment qua API HTTP thật của fixture để kiểm tra quyền UI.

ESLint và biên dịch Python là kiểm tra tĩnh đã chạy; browser regression/typecheck/build toàn dự án do lượt kiểm tra chung thực hiện sau khi provider và các nhóm UI hoàn tất. Fixture kiểm chứng hợp đồng tích hợp, không được ghi thành đã kiểm thử Java native thật.
