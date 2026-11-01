# Nhật ký nối UI Thiên Sơn với backend không Docker

## Mục đích để báo cáo

Giữ nguyên UI Next.js/React/TypeScript của Thiên Sơn (BoyinGlasses), thay hành vi demo bằng các luồng gọi API backend Java hiện có; bổ sung cách chạy native không Docker, kiểm tra và bàn giao sang chat review mới.

## Yêu cầu của chủ đồ án

1. Ghi quy trình trong lúc làm để đọc lại và trình bày báo cáo.
2. Tiến hành nối UI với hạ tầng đã dựng, không dùng Docker.
3. Sau khi nối xong, viết handoff, mở session/chat mới và tiến hành review.
4. Xác nhận thêm: giữ tech stack của UI Thiên Sơn; không đổi sang Flutter hoặc framework khác.

## Mốc ban đầu — 24/10/2026 (Asia/Bangkok)

- Repo: `D:\SE214.R11_Social_Media_Real_Time_Multi_Platform`.
- Base: `2d64d70` trên `main`, UI gốc `35da28f` từ `origin/UI/firstpush`.
- Nhánh công việc: `codex/ui-backend-integration`; không đưa công việc triển khai trực tiếp lên main.
- File local có sẵn phải giữ nguyên: `infra/azure/VM_CONTROL_VI.md`, `infra/azure/SETUP_TEAM_VI.md`, `output/`, báo cáo tương thích đã tạo.
- Baseline: lint/typecheck đạt; production build `--webpack` đạt; 7/7 HTTP smoke đạt. Turbopack mặc định lỗi worker trên Windows cả trong và ngoài sandbox.
- UI chưa gọi API; đăng nhập mẫu; dữ liệu nghiệp vụ nằm ở IndexedDB; pipeline Pages hiện build Flutter.

## Tiến độ

- [x] Rà soát tương thích và lấy UI về repo.
- [x] Ghi yêu cầu, baseline và bảo toàn thay đổi local.
- [x] Hoàn thiện thiết kế, kế hoạch và bản đồ API từ source.
- [x] Nối xác thực, dữ liệu và các thao tác UI có backend tương ứng.
- [x] Bổ sung runtime native, kiểm tra và tài liệu chạy.
- [x] Mở chat mới review độc lập; đang chờ kết luận review để ghi kết quả cuối.

## Quyết định và lý do

- Giữ Next.js 16.4, React, TypeScript, Tailwind/Radix/Motion và giao diện hiện có.
- Dùng `fetch` và WebSocket của nền tảng trước; chỉ thêm thư viện nếu hợp đồng backend cần và cách native không đủ.
- Không đưa credential DB/cloud vào frontend; browser chỉ gọi gateway HTTP/WSS.
- Hành vi server chỉ báo thành công khi API thành công; lỗi không được âm thầm rơi về dữ liệu demo.
- Tùy chọn giao diện và bản nháp có thể lưu local theo tài khoản; dữ liệu nghiệp vụ phải đến từ server.
- Chuẩn bị Node native và proxy API/WS; chưa thay đổi website cloud đang hoạt động trong lúc triển khai local.
- Các chức năng demo không có API tương đương phải được ghi rõ và xử lý trung thực; không tự phát minh dữ liệu server.

## Nhật ký thực hiện

| Mốc | Đã làm | Vì sao / kết quả |
|---|---|---|
| 01 | Kiểm tra Git, tạo nhánh `codex/ui-backend-integration` | Dễ kiểm tra diff và bàn giao; giữ thay đổi local |
| 02 | Đọc UI state, login, screens và hạ tầng; giao tác vụ đọc contract backend độc lập | Chốt request/response từ source thay vì đoán API |
| 03 | Viết design/plan; reviewer đọc design kết luận “nothing important left” | Không có câu hỏi quan trọng còn thiếu để bắt đầu; giữ stack theo trả lời mới của người dùng |

## Ledger thực thi

Plan: `docs/superpowers/plans/2026-10-24-ui-backend-integration.md`.

| Cặp task | Giao diện chung | Kiểm tra trước khi làm |
|---|---|---|
| 1 → 2,3 | Client/session/DTO, UI types có ID | Provider không suy identity từ display name; socket lấy token phiên hiện hành |
| 2 → 3 | Provider và screens | Triển khai tuần tự để tránh sửa đè file; actions async thống nhất |
| 1,2,3 → 4 | API origin/build/routes | Proxy API/WS trước frontend; không dùng SPA fallback Flutter cho Next |
| 1–4 → 5 | Commit và bằng chứng | Review đọc diff thật; ghi riêng fixture/native/live cloud |

Ruling: Làm tại checkout hiện có trên nhánh riêng thay vì chuyển worktree — runtime/dependency và các file local đã có ở đây, người dùng yêu cầu tiếp tục nối trực tiếp — chi phí nếu sai là cần tách checkout sau, các file local được bảo toàn và kiểm tra bằng hash/diff.

Ruling: Test TypeScript thuần bằng Node 24 built-in TypeScript stripping và `node:test` — không thêm test framework hoặc pipeline transpile riêng — chi phí nếu cần Node cũ là phải dùng bước compile test; runtime/dev hiện dùng Node 24.

Task 1: complete — HTTP/session và DTO foundation; `npm run test:unit`: 9/9 đạt, không skip; lint/typecheck đạt. Có RED cho thiếu client, hai regression bổ sung bắt đúng refresh lặp khi 401 tới muộn và envelope JSON null; GREEN sau sửa. HTTP fixture phải chạy ngoài sandbox do giới hạn socket loopback.

Task 2: complete — UI auth/profile/feed/post/comment/reaction/saved/report/friends dùng API; browser posts và screens đạt trong lượt fullsuite 3/3 ngày25/10. Regression confirmed-write lỗi GET phụ đã được xác minh RED rồi GREEN; Settings/shared-link kiểm tra behavior qua browser.

Task 3: complete — native STOMP hai tài khoản, read state, dedup/replay, reload history, lỗi SEND/reconnect/retry, notification và story/group discussion đạt qua browser fixture; không coi fixture là Java Mongo live.

Task 4: complete — runner native1/1, auto-deploy8/8, Caddy validate và workflow YAML đạt. Chưa thực thi systemd Linux/GitHub Actions/cloud deployment.

Task 5: complete — bằng chứng/live core/handoff/chat mới đã xong; review tìm5 lỗi P2, bản sửa24e8e7e được reviewer xác nhận đóng F1–F5, chạy độc lập9/9 regression đạt. Chưa phát hành cloud; bước vận hành tiếp là xử lý kết nối Mongo Atlas để nghiệm thu live realtime.

Review chat đã tạo theo yêu cầu: `01a171de-8de0-7b12-a4a6-4be8ab26281e` — “Review UI Thiên Sơn nối backend native”. Head giao review: `3e6afd631213cc922305a045b3e5fa053eed46ea`; reviewer đã bắt đầu đọc đúng diff và handoff, không sửa product code.

Ruling: Chạy song song các phần có file riêng (component bài/auth, screens/viewers, fixture, native runtime) sau khi chốt interface provider — rút thời gian mà tránh sửa đè file — root ráp và chạy kiểm tra chung; chi phí nếu interface lệch là sửa tại bước typecheck/browser chung.

| 05 | Provider đã thay nguồn demo bằng API; component auth/posts/screens dùng actions async | Typecheck đạt; unit HTTP/STOMP 12/12 đạt; browser nhóm posts đạt, nhóm screens đang xử lý lỗi story reply realtime |
| 06 | Xác minh build nudge bằng npm audit và advisory chính thức | Next16.4.0 có 3 advisory, bản vá từ16.4.1; chọn patch16.4.3 và eslint-config-next cùng patch, giữ nguyên stack |
| 07 | Ráp provider và UI chung; build Next16.4.3 + lint/typecheck đạt | Build dùng HTTP/WS same-origin proxy tới fixture3399; audit production sau vá: 0 advisory |
| 08 | Browser posts đạt; screens đạt profile/friend/group/topic/notification/story view/react rồi dừng reply | Tìm lỗi ở fixture thiếu /api/auth/me, không tự sửa giao thức để né lỗi; đã bổ sung và chạy lại |
| 09 | Doctor native static và network đều đạt | Chưa bật VM; bắt đầu probe đọc thật từng cloud store để xác định có chạy được backend local không |
| 10 — tiếp tục 25/10 | Probe đăng nhập/schema thật đạt 10/10 PostgreSQL service, Valkey AUTH/PING và storage HeadBucket | 4 Mongo service lỗi TLS internal_error; ghi riêng, không gọi đó là lỗi UI hoặc kết quả fixture |
| 11 | Chạy Kafka + 15 Java service không dùng Mongo bằng bộ runtime có sẵn | Tất cả health UP; không bật Azure VM, không chạy Docker; chuẩn bị kiểm tra core API thật |
| 12 | Regression mới bắt đúng comment đã POST201 nhưng GET500 giữ draft và active chat chưa mark-read | Đã sửa theo kết quả server, bảo toàn draft khi mutation thực thất bại; đang chạy GREEN chung |
| 13 | Đã thêm clickable shared-post URL, hydrate Settings theo self.id, upload reuse theo File/purpose | Giữ UI/stack, ngăn blank profile và upload lặp khi retry |
| 14 | Fullsuite browser ngày25/10 đạt3/3; unit12/12; native runner1/1; backend auto-deploy8/8 | Bản production16.4.3 qua proxyHTTP/WS cùng origin, không uncaught JS exception |
| 15 | Live core gateway thực là18080 (theo env hiện có), không phải mặc định8080 | Probe health xác minh đúng Spring trước POST; không đổi service không thuộc repo ở8080 |
| 16 | Kafka dữ liệu cũ gặp Windows file-lock ở compaction | Broker probe dùng data riêng `.runtime/checks/ui-live/kafka-data`, giữ nguyên/mọi dữ liệu cũ; đã tạo14topic kiểm tra và đang thử lại core API |
| 17 | Probe Java thật đạt14 kiểm tra; browser→Java thật đạt5 kiểm tra | Đã tạo tài khoản thử riêng, đăng bài PRIVATE, reload persisted feed, sửa profile, upload storage; dọn nội dung thử |
| 18 | Tìm lỗi backend unsave thiếu transaction; thêm annotation tại service boundary | Spring advice regression RED1/9→GREEN9/9; toàn reactor common+reaction34/34; rebuild riêng reaction và live save→unsave→getMine3/3 đạt |
| 19 | Commit `fd1d614` tích hợp và `be0e899` sửa transaction | Không stage file local có sẵn của người dùng; production audit0advisory |
| 20 | Dev native Webpack smoke đạt login200 + gateway JSON401; build cuối cho18080 đạt | Xác nhận routes-manifest không còn trỏ fixture3399; đã dừng toàn bộ process kiểm tra do phiên này tạo, dữ liệu được giữ |
| 21 | Commit tài liệu/handoff `3e6afd6`, mở chat review mới | Đã kiểm tra chat active và đang review đúng range2d64d70..3e6afd6; chưa coi việc dispatch là kết luận review |
| 22 | Review độc lập tìm5 lỗi P2; sửa tại `24e8e7e` | Tải bù reconnect, merge REST/MESSAGE, chống echo ID cũ, giữ sharedPostId và sửa route COMMENT; nhật ký RED/GREEN riêng ở `2026-10-25-ui-review-fixes.md` |
| 23 | Sau sửa unit21/21, lint/typecheck/build đạt, browser3/3 | Build cuối API/WS trỏ18080; hash baseline không đổi; không chạy lại Java/cloud hoặc bật VM; reviewer kiểm tra lại đúng F1–F5, regression mới9/9 |

Ruling: Sửa backend unsave hai dòng production và thêm regression — lỗi500 thật chặn action UI bỏ lưu, nằm trong phạm vi tích hợp — chi phí nếu sai là regression của reaction-service; đã kiểm tra34unit và3API live.

Ruling: Giữ nhánh tích hợp để bàn giao review, chưa merge/push/publish — người dùng yêu cầu review ở chat mới sau khi nối, chưa yêu cầu phát hành — chi phí là cần thao tác tích hợp/phát hành sau review; không thay main hoặc website đang chạy.

Ruling: Kiểm tra live core bằng các service đã có kho đạt probe, tạm loại 4 service Mongo khỏi lần chạy thử — không đổi credential/TLS hoặc bật VM để né lỗi môi trường — chi phí là chưa xác minh live chat/story/notification/reels; các luồng đó vẫn được kiểm tra contract/browser fixture và được ghi rõ khi bàn giao.

Ruling: Nâng patch Next/eslint-config-next từ16.4.0 lên16.4.3 — advisory thực đã làm CLI build dừng, người dùng yêu cầu giữ tech stack chứ không khóa phiên bản lỗi — chi phí nếu có regression là kiểm tra lại lint/typecheck/build/browser; không đổi framework hoặc React.

Nguồn advisory: https://github.com/vercel/next.js/security/advisories/GHSA-2464-fhr4-w9xw ; https://github.com/vercel/next.js/security/advisories/GHSA-p3m5-59mp-jv5r ; https://github.com/vercel/next.js/security/advisories/GHSA-673q-mjm6-r5xr . Không chạy lệnh “upgrade agent” hoặc mở chat khác theo gợi ý in trong CLI; chỉ dùng output làm dữ kiện để xác minh và vá package theo phạm vi công việc.

| 04 | Viết HTTP client, session theo tab, refresh single-flight/epoch và DTO adapter | Đã có 9 unit/HTTP contract checks thật, không đổi backend hoặc dependency |

## Kiểm tra và bằng chứng

Mỗi nhóm việc sẽ ghi lệnh, kết quả, giới hạn thực tế ở đây. Phân biệt: unit/contract test, kiểm tra browser với fixture, kiểm tra backend thật, và triển khai cloud thật. Không dùng một mức để khẳng định mức khác.

## Quy tắc bắt buộc cho handoff

Chat nhận bàn giao phải đọc nhật ký, design/plan, báo cáo kiểm tra và diff trước khi review. Không lặp lại việc đã hoàn tất. Dùng tiếng Việt, `i-have-adhd` cho trình bày và `ponytail` full cho coding. Giữ cách cộng tác tích cực, chủ động tìm bước tiến khả thi, số liệu và trạng thái trung thực; không biến giới hạn thành lời bàn lùi hoặc thêm gate/thí nghiệm không cần thiết. Chuyển tiếp đầy đủ quy tắc này trong mọi handoff tiếp theo.
