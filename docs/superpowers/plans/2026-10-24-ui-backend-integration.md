# UI Backend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Nối UI Next.js của Thiên Sơn với các API Java hiện có, chạy native không Docker, có nhật ký/bằng chứng và chat review mới sau bàn giao.

**Architecture:** Dùng một HTTP client và DTO adapter dùng chung; SocialProvider quản lý phiên/dữ liệu và action server, các component gọi action thay vì giả lập bằng setters. STOMP qua native WebSocket cho chat/notification. Giữ layout và dependency, chuẩn bị Node/systemd/Caddy cùng các kiểm tra theo mức độ xác minh.

**Tech Stack:** Next.js 16.4, React 19, TypeScript 6, fetch/WebSocket, Node built-in test runner, Python/headless Chrome hiện có; backend Java/Spring chạy native.

**Spec:** `docs/superpowers/specs/2026-10-24-ui-backend-integration-design.md`

## Global Constraints

- Giữ Next.js/React/TypeScript và UI hiện có; không chuyển Flutter hoặc Docker.
- Không thêm dependency nếu standard library hoặc dependency hiện tại đáp ứng.
- Không thay đổi file local có sẵn, không push/main merge/publish cloud trong giai đoạn tích hợp local.
- API/WS chỉ qua gateway; không đưa credential backend vào bundle.
- Không báo lưu server thành công khi chỉ thay đổi local; bản nháp/tùy chọn local phải theo tài khoản.
- Giới hạn upload theo backend 50 MB và purpose/MIME; ID người dùng làm khóa, tên chỉ để hiển thị.
- Ghi nhật ký tiếng Việt trong lúc làm; dùng build `--webpack` đã xác minh ở môi trường Windows này.

## Review Focus

1. Token hết hạn, hai request 401 đồng thời và refresh thất bại: refresh tối đa một lần, không lặp vô hạn, không giữ tài khoản cũ.
2. Logout/đổi tài khoản khi request cũ đang chạy: không làm lộ dữ liệu hoặc hồi sinh phiên cũ.
3. Hai người cùng display name, URL/avatar/media thật: không nhầm người, không tạo `/assets/https://...`.
4. Lỗi server khi tạo/sửa/xóa/upload/gửi tin: form không báo thành công và không mất nội dung chưa lưu.
5. Reconnect/duplicate STOMP frame, deep link và Caddy auto-update: không trùng tin, mất route hoặc proxy API thành HTML.

---

### Task 1: HTTP/session và DTO

**Files:** Create `frontend-web/src/lib/api-client.ts`, `backend.ts`, `adapters.ts`, `tests/api.test.mjs`; modify `src/lib/types.ts`, `package.json`, `.gitignore`.

**Interfaces:** `ApiClient.request<T>(path: string, options?: RequestOptions): Promise<T>`, `setSession(session: Session|null)`, `restoreSession()`, `login(email,password)`, `logout()`; `Session = {accountId,email,accessToken,refreshToken}`. `backend.ts` định nghĩa DTO đúng source. Adapter nhận DTO + profiles/currentUserId và trả UI types có ID thật. Session storage theo tab, không snapshot demo.

- [x] Viết test HTTP fixture thực: unwrap envelope, login đúng payload, Bearer, refresh single-flight, refresh fail, response không JSON/network/timeout, không dùng response cũ sau logout; adapter identity/privacy/media.
- [x] Chạy `npm run test:unit`, ghi RED do thiếu hành vi.
- [x] Implement client và adapter tối thiểu theo contract đã đọc; dùng session epoch bảo vệ kết quả cũ.
- [x] Chạy unit tests đến GREEN; lint/typecheck phần tương ứng.
- [x] Ghi kết quả nhật ký và commit đúng file của task.

### Task 2: Phiên, feed/post/profile/friends và thao tác dữ liệu

**Files:** Modify `src/components/social-provider.tsx`, `login-screen.tsx`, `app-shell.tsx`, `feed.tsx`, `post-card.tsx`, `post-detail.tsx`, `composer.tsx`, `screens.tsx`, `viewers.tsx`; add utility nhỏ trong `src/lib/` nếu cần.

**Consumes:** Task 1 client/DTO/adapters.
**Produces:** Provider có `login/register/logout/refreshData`, `createPost/editPost/deletePost/addComment/editComment/deleteComment/reactToPost/toggleSave/reportPost`, `updateProfile/toggleFollow/acceptFriend/declineFriend/requestFriend`. Tất cả trả Promise với kết quả server; component chỉ đóng form khi thành công.

- [x] Viết browser/fixture tests cho login, restore/logout, register, feed, post/media, comment/reaction/save/report, profile/friends; pin cả server error và display-name trùng.
- [x] Chạy test để thấy RED ở hành vi demo.
- [x] Nối provider/components, dùng ID ở route/collection, URL media thật; bỏ snapshot dữ liệu mẫu khỏi phiên thật. Account prefs/drafts tách riêng và loading/error/empty hiện rõ.
- [x] Chạy fixture tests, unit tests, lint/typecheck; xử lý lỗi gốc.
- [x] Ghi ma trận tính năng và commit các thay đổi đã kiểm tra.

### Task 3: Chat/realtime, thông báo, stories và explore

**Files:** Create `src/lib/realtime.ts`, `tests/realtime.test.mjs`; modify provider/screens/viewers và adapter cần thiết.

**Consumes:** Session access token, DTO và actions Task 1–2.
**Produces:** `StompConnection` dùng native WebSocket: connect/subscribe/send/disconnect và reconnect; provider `openConversation/sendMessage/sharePost/readNotifications/viewStory/reactStory/replyStory`, group/discussion actions theo API hiện hữu. Không giả sử REST send chat tồn tại.

- [x] Test STOMP frame/heartbeat/content-length, SEND trước CONNECTED, reconnect/disconnect/duplicate message; browser fixture chat/read notifications/story/group/topic và lỗi.
- [x] Chạy RED rồi implement theo `/ws/websocket?token=`, `/ws-notifications/websocket?token=`; gửi `/app/chat.send`, nhận `/user/queue/messages` và `/user/queue/notifications`.
- [x] Nối lịch sử chat, ID conversation, read server, story thật; map discussion sang group-post/comment nếu đủ contract, ghi rõ phần không tương đương.
- [x] Chạy tests GREEN + lint/typecheck; đảm bảo logout dừng socket/reconnect.
- [x] Ghi nhật ký và commit.

### Task 4: Runtime native và pipeline kiểm tra

**Files:** Modify `frontend-web/next.config.ts`, `.env.example`, `README.md`, `.github/workflows/ci-cd.yml`; add `infra/no-docker` scripts/systemd/Caddy mẫu cần thiết; tránh thay deploy backend đang dùng nếu chưa cần.

**Consumes:** Next build và API/WS origin từ các task trước.
**Produces:** Local chạy được Node + gateway; cấu hình production Node native có route API/WS trước frontend, tài liệu rõ quan hệ với Pages Flutter và auto-updater.

- [x] Test/probe routing API không thành HTML; kiểm tra deep link và redirect; kiểm tra origin không wildcard.
- [x] Implement proxy/env/scripts tối thiểu và job lint/unit/typecheck/build Next trong CI, không bỏ job backend/Flutter còn dùng.
- [x] Chạy lint/typecheck/build `--webpack`, native HTTP smoke và script validation.
- [x] Ghi lệnh chạy và cập nhật setup/báo cáo mà không ghi đè file local có sẵn.
- [x] Commit file của task.

### Task 5: Nghiệm thu, handoff và review mới

**Files:** Update worklog; create `docs/handoffs/2026-10-24-ui-backend-integration.md`, báo cáo verification và feature matrix; update tests README.

- [x] Chạy toàn bộ bộ test phù hợp, lint/typecheck/build và smoke production; thử backend native thật nếu sẵn sàng, ghi tách kết quả fixture/live.
- [x] Đọc diff, kiểm tra không thay đổi baseline local hoặc thêm secrets; cập nhật checklist và hạn chế có tác động thật.
- [x] Tạo handoff có base/head, file/command, cách chạy, kết quả, phạm vi và toàn bộ quy tắc i-have-adhd/ponytail/cộng tác tích cực.
- [x] Theo yêu cầu người dùng, mở chat mới ở đúng project để review độc lập; prompt yêu cầu đọc handoff và review code/test, không tự deploy.
- [x] Chờ kết quả review, xử lý phát hiện có cơ sở, ghi trạng thái cuối và cung cấp link chat mới. Chat `01a171de-8de0-7b12-a4a6-4be8ab26281e` xác nhận đóng F1–F5 tại `24e8e7e`, regression chạy mới9/9 đạt.
