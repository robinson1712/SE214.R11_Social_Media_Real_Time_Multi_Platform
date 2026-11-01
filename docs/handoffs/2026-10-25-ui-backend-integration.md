# Handoff: review UI Next.js nối backend native

Chat review độc lập đã mở: `01a171de-8de0-7b12-a4a6-4be8ab26281e`, title “Review UI Thiên Sơn nối backend native”. Snapshot đầu `3e6afd6` đã review xong; 5 lỗi P2 được sửa tại `24e8e7e`. Reviewer đã xác nhận đóng F1–F5, chạy mới9/9 regression đạt, không phát hiện lỗi trực tiếp bổ sung trong phạm vi kiểm tra lại. Đọc [findings và kết luận mục5](../reviews/2026-10-25-ui-backend-review.md) và [nhật ký sửa](../worklogs/2026-10-25-ui-review-fixes.md) trước khi làm tiếp.

Sau sửa: unit21/21; lint/typecheck đạt; browser fixture3/3; build production cuối đã xác minh cả API/WS đều trỏ gateway18080. Java/cloud không chạy lại trong lượt sửa frontend này; bằng chứng live bên dưới thuộc lượt tích hợp trước đó. Cấu hình UI native đã chuẩn bị nhưng chưa deploy cloud. Các process native thuộc lượt probe đã dừng, state processes rỗng. COMMENT notification chưa có mapping post nguồn nên giữ `/notifications`; cơ chế SEND chưa có backend correlation ID, không khẳng định phân biệt echo ID mới cùng payload từ tab khác.

## Yêu cầu gốc của người dùng

1. Ghi quy trình vào file trong lúc làm để sau này đọc lại và đi báo cáo.
2. Tiến hành nối UI với hạ tầng đã dựng không Docker.
3. Sau khi nối xong, hand-off và mở session/chat mới để review.

Người dùng xác nhận thêm: **giữ UI của Thiên Sơn dùng tech stack gì thì làm theo tech stack đó**. Vì vậy giữ Next.js/React/TypeScript, không chuyển Flutter hoặc framework khác.

## Đọc trước khi review

- `docs/worklogs/2026-10-24-ui-backend-integration.md`: nhật ký, quyết định và tiến độ24–25/10.
- `docs/reports/2026-10-25-ui-backend-integration.md`: báo cáo tiếng Việt để trình bày, phạm vi và mức xác minh.
- `docs/verification/2026-10-25-ui-backend-integration.json`: snapshot kết quả đã chạy, không chứa credential.
- `docs/worklogs/2026-10-24-backend-contracts.md`: hợp đồng backend từ controller/DTO/service.
- `docs/superpowers/specs/2026-10-24-ui-backend-integration-design.md` và plan cùng tên trong `plans/`.
- `docs/worklogs/2026-10-25-saved-transaction.md`: regression transaction, RED/GREEN và lệnh Maven.
- `frontend-web/AGENTS.md`, `frontend-web/README.md`, `infra/no-docker/nextjs/README.md`.

Đọc diff thật và kiểm tra kết quả; không chỉ tin tóm tắt này. Không lặp lại việc đã hoàn tất hoặc pull/cherry-pick UI thêm lần nữa.

## Git và phạm vi

- Workspace: `D:\SE214.R11_Social_Media_Real_Time_Multi_Platform`.
- Nhánh: `codex/ui-backend-integration`; bắt đầu từ `2d64d70` sau khi lấy UI BoyinGlasses `35da28f` vào repo.
- Commit nền: `d43d749` HTTP/session/DTO + design/plan/worklog.
- Commit tích hợp: `fd1d614` UI, API/realtime, browser suite, native runtime/CI/Caddy opt-in.
- Commit sửa backend: `be0e899` transaction cho bỏ lưu.
- Có thể có commit tài liệu bàn giao mới sau các mốc code này. Review phạm vi `git diff 2d64d70..HEAD` của nhánh tích hợp, không mặc định diff với origin/main đã lệch lịch sử.
- Chưa merge main, push hay tạo PR, chưa deploy website/cloud.

Các thay đổi có trước phiên phải giữ nguyên: `infra/azure/VM_CONTROL_VI.md`, `infra/azure/SETUP_TEAM_VI.md`, `output/` (có PDF của người dùng), assessment cũ `infra/no-docker/UI_NEXTJS_COMPATIBILITY_2026-10-24.md`. Không đưa chúng vào findings như thay đổi code mới hoặc xóa để làm sạch status.

Hash đã kiểm tra lại không đổi:

- VM_CONTROL: `6C89BCB68A7074AB8A76B8DAB34E93DC5DF5D792E76B153EFC0B20080C6DFB6D`.
- SETUP_TEAM: `C66803F043886714C86A69024B8DECBBBDF62A6942A2804D2EF466C809AFCF02`.

Không đọc/in credential từ `.runtime/env.ps1`, account/session private JSON, SSH key hoặc cloud token để review code. Credential kiểm tra chỉ nằm trong `.runtime/` Git-ignored; báo cáo đã có kết quả an toàn để đọc.

## Kiến trúc và thay đổi chính

- `src/lib/api-client.ts`: fetch, ApiResponse, Bearer, sessionStorage theo tab, refresh single-flight/401 muộn, session epoch ngăn response cũ sau logout.
- `backend.ts`/`adapters.ts`/`types.ts`: IDs thật; DTO→view model, URL avatar/media và privacy enums.
- `social-provider.tsx`: nguồn dữ liệu/action server; local chỉ theme/preferences/drafts theo account ID. Mutation chờ xác nhận, không fallback sang snapshot demo.
- Component giữ layout/motion hiện có; form có pending/error, giữ nội dung khi mutation thất bại. Chờ profile thật trước khi mount khu vực đăng nhập; Settings key theo self.id.
- `realtime.ts`: STOMP trên native WebSocket, hai endpoint riêng; gửi chat qua `/app/chat.send`, nhận user queue, server echo, dedup/read/reconnect. Không phát minh REST send endpoint.
- Next rewrites `/api`, `/ws`, `/ws-notifications`; default cùng origin, origin public riêng là tùy chọn build-time.
- Node runner/build/dev dùng Webpack. Next/eslint-config-next vá16.4.3 vì advisory thực phát hiện trong build; không đổi stack.
- Native Caddy/systemd mẫu và job CI Next. Backend auto-updater chỉ bật fallback Next khi có flag loopback hợp lệ; không flag giữ default404, rollback cũ còn nguyên.
- `SavedItemService.unsave` thêm `@Transactional`; test Spring transaction advice thật xác minh transaction đang hoạt động lúc derived delete.

## Bằng chứng đã đạt

| Phạm vi | Kết quả |
|---|---|
| Frontend unit/HTTP/native STOMP + regression sau review | 21/21, không skip |
| Browser production fixture | 3/3 nhóm: posts, screens, hai tài khoản chat; không uncaught JS error |
| Lint/typecheck/build | Đạt trên Next16.4.3 Webpack |
| Native runner | 1/1; đã bổ sung dev Webpack regression và smoke dev login200/API JSON401 |
| Caddy/auto-deploy | 8/8 unit, ba cấu hình Caddy validate; YAML workflow parse |
| Common + reaction suite | 34/34; regression unsave RED trước annotation, GREEN sau |
| Java core API thật | 14 kiểm tra, gồm register/login/profile/feed/post/comment/reaction/saved/upload/refresh |
| Browser→Java thật | 5 kiểm tra: login/profile, post PRIVATE, reload persisted feed, profile save, logout |
| Unsave Java thật sau sửa | 3/3: save tồn tại, DELETE200, getMine null |
| Audit production | 0 advisory sau patch |

Fixture là HTTP/WebSocket server test-only có trạng thái. Browser login bằng form, không inject session/React internals. Hai profile xác minh gửi/nhận, sender echo, replay cùng ID, readBy, reload, SEND lỗi giữ draft và reconnect/retry. Live browser sử dụng proxy chỉ chuyển tiếp Java18080, không dùng fixture trả dữ liệu.

## Cách chạy lại

Từ `frontend-web/`, Node24:

```powershell
npm test
npm run lint
npm run typecheck
$env:GATEWAY_UPSTREAM='http://127.0.0.1:3399'
$env:NEXT_PUBLIC_API_BASE_URL=''
$env:NEXT_PUBLIC_WS_BASE_URL=''
npm run build
npm run test:connected
```

Browser cần Chrome/Python websockets; loopback bị sandbox chặn thì chạy ngoài sandbox. Runner dùng cổng3300/3399, có ownership/cleanup. Snapshot lỗi bị Git ignore. Bộ `test_next.py`/`test_login.py` cũ thuộc demo; package giữ alias `test:legacy-demo`, không coi requirement tài khoản mẫu của bộ cũ là regression API mới.

Sau fixture, build lại cho gateway thật: `$env:GATEWAY_UPSTREAM='http://127.0.0.1:18080'; npm run build`. Gateway máy thực tế là18080 theo config, template mặc định8080. `.env.local` local hiện có URL18080, không chứa secret và không commit. Build cuối bàn giao được trả lại18080; `npm start` không tự khởi động Java.

Native checks từ root:

```powershell
node --test infra/no-docker/nextjs/run.test.mjs
python -m unittest discover -s infra/azure/tests -p test_auto_deploy.py
```

Python atomic rename test trên Windows sandbox có thể cần `TEMP`/`TMP` là thư mục con workspace như `output/native-nextjs-test-tmp`; không sửa logic atomic để né giới hạn. Lệnh Maven với JDK21/ByteBuddy đã ghi chính xác trong worklog saved-transaction; không in credentials hoặc chạy probe cloud không cần thiết để review.

## Giới hạn thực tế và trạng thái môi trường

- Kafka +15 Java service không dùng Mongo đã được khởi chạy native để kiểm tra live. Nội dung bài/comment/media thử đã dọn; tài khoản kiểm tra riêng được giữ để tái lập. Root dừng các process thử nghiệm do mình tạo trước bàn giao, không bật VM.
- Bốn Mongo service probe TLS `internal_error` từ môi trường này. Chat/story/notification/reels chưa nghiệm thu Mongo live; hợp đồng/browser fixture đã đạt. Bước hợp lý tiếp theo là kiểm tra TLS/network/Atlas allowlist, giữ TLS bật rồi chạy live các service đó.
- Kafka data cũ gặp Windows compaction file-lock. Probe live dùng data riêng `.runtime/checks/ui-live/kafka-data`, không xóa/di chuyển dữ liệu cũ. Không áp cấu hình probe này lên production.
- Chưa chạy systemd Linux/GitHub hosted workflow hoặc deploy Azure/Pages. Existing backend release không đóng gói frontend; native README ghi cách quản lý UI riêng trước khi bật Caddy flag.
- Location composer là draft vì Post API chưa có field; browser theme/preferences/drafts là local. Following list là known-loaded accounts vì backend không có list-following; không coi đó là toàn bộ graph. Chỉ các tính năng có UI/API tương ứng nằm trong scope, không tự thêm dating/page/admin UI mới.

## Việc của chat review mới

Review độc lập code/test từ base2d64d70 đến head được giao, ưu tiên lỗi hành vi thực: auth/session races và account isolation; mutation success/error/retry; identity/DTO/permissions; STOMP lifecycle/dedup/read; routing/CORS/build origin; native opt-in/rollback; transaction bỏ lưu. Nêu file/line, trigger, ảnh hưởng và cách sửa tối thiểu. Phân biệt bug mới với giới hạn đã ghi hoặc dữ liệu test.

Không deploy/push/merge, thay credential, bật VM hoặc mở rộng scope khi review. Có thể chạy kiểm tra phù hợp và ghi findings tại `docs/reviews/2026-10-25-ui-backend-review.md`; nếu không có lỗi quan trọng, ghi rõ phạm vi đã xem và mức xác minh. Không khẳng định “đã sạch” chỉ dựa vào report.

## Quy tắc cộng tác bắt buộc — giữ đầy đủ qua mọi handoff

Người dùng yêu cầu `i-have-adhd` cho mọi task/response: tiếng Việt, dẫn bằng kết quả/hành động, bước đánh số ngắn khi nhiều bước, tiến độ rõ, không tangent/filler; khi còn việc kết thúc bằng một hành động cụ thể. Đây là lựa chọn trình bày, không suy chẩn đoán. Giữ đến khi người dùng yêu cầu normal/stop ADHD mode.

Coding luôn dùng `ponytail` full: hiểu luồng thật trước, chọn code có sẵn/standard library/native/installed dependency trước; giải pháp đơn giản nhưng đầy đủ. Không bỏ correctness, validation, accessibility, error handling hoặc tính năng đã yêu cầu. Giữ đến khi người dùng đổi chế độ.

Quy định cộng tác tích cực của người dùng (2026-10-20), phải áp dụng ngay và chuyển tiếp đầy đủ:

- Giữ thái độ tích cực, chủ động và cùng người dùng tìm đường tiến tới paper. Mỗi câu trả lời cần đem lại một hướng khả thi, một bước tiến rõ ràng hoặc động lực có cơ sở; không mặc định bàn lùi khi gặp khó khăn.
- Người dùng cần một đóng góp tương đối có giá trị trong phạm vi phù hợp, không đòi sự hoàn hảo hay chắc chắn tuyệt đối trước khi tiến hành nghiên cứu hoặc chuẩn bị bài báo. Không biến mọi giới hạn thành điều kiện chặn, không liên tục nâng tiêu chuẩn hoàn thành hay tự thêm thí nghiệm.
- Tránh lặp các câu như “chưa có kiểm chứng”, “chưa đủ bằng chứng” hoặc các đoạn cảnh báo làm mất động lực. Khi một điểm chưa rõ thực sự ảnh hưởng quyết định, diễn đạt ngắn gọn và gắn ngay với cách tiến lên: “Giả thuyết làm việc là X; bước phù hợp để đánh giá là Y”. Không biến một ý tưởng hợp lý thành kết quả đã đo.
- Khi phân tích kết quả, dẫn bằng điều đã làm được, giá trị có thể khai thác và hướng cải thiện ưu tiên. Khi phản biện, giải thích điểm cần xử lý rồi đưa giải pháp cụ thể, vừa sức với quota và phạm vi đã thống nhất. Không hứa chắc thắng Base hoặc được nhận bài; vẫn giữ số liệu, nguồn và trạng thái công việc trung thực.
- Người dùng nhấn mạnh rằng nghiên cứu có thể được xuất bản dù kết quả chỉ có giá trị tương đối và nơi công bố có tỷ lệ nhận thấp (ví dụ họ nêu20%). Hiểu đây là yêu cầu về tinh thần thực tế và kiên trì, không phải một tỷ lệ đã xác minh hay bảo đảm xuất bản. Áp dụng cách cộng tác này lâu dài cho đến khi người dùng yêu cầu đổi.
- Bắt buộc tuân thủ trong phiên hiện tại, các phiên tiếp nối và mọi handoff. Khi bàn giao, ghi rõ và chuyển tiếp đầy đủ quy định cộng tác tích cực này cùng i-have-adhd và ponytail; không chỉ chuyển số liệu, giới hạn hay cảnh báo rồi bỏ mất yêu cầu về cách cộng tác. Phiên nhận bàn giao phải áp dụng ngay, không yêu cầu người dùng nhắc lại.
