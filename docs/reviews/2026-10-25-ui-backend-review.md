# Review độc lập: UI Next.js nối backend native

Ngày 25/10/2026, múi giờ Asia/Bangkok.

**Kết luận mới nhất sau kiểm tra lại commit `24e8e7e`: F1–F5 đã được xử lý theo đúng trigger đã báo; regression chạy độc lập đạt 9/9. Không có finding còn mở hoặc lỗi trực tiếp mới được ghi nhận trong phạm vi bản sửa.** Xem mục 5 về bằng chứng và giới hạn.

**Kết luận lượt đầu tại `3e6afd6`: có 5 findings P2 cần xử lý; không ghi nhận P0/P1 trong phạm vi đã kiểm tra.** Các điểm này tập trung ở đồng bộ chat và ánh xạ nội dung/đường dẫn. Nội dung mục 1–4 bên dưới được giữ làm lịch sử review trước sửa.

## 1. Phạm vi và phương pháp

- Diff chính xác: `2d64d70..3e6afd631213cc922305a045b3e5fa053eed46ea`, nhánh `codex/ui-backend-integration`. Đã xác nhận HEAD bằng `git rev-parse HEAD`; không dùng `origin/main` làm base.
- Đã đọc handoff, worklog, report, verification JSON, design/plan, backend contracts, saved-transaction worklog và tài liệu frontend/native. Đối chiếu trực tiếp source HTTP/session, DTO/adapter, provider, component/action, STOMP, Next rewrites, CI, native runner, Caddy/auto-deploy và transaction bỏ lưu.
- Lần theo Java producer/controller/service cho chat, notification, comment, shared post và feed; đọc test để phân biệt hành vi đã phủ với các khoảng trống dưới đây.
- Tái hiện logic bằng harness **trong bộ nhớ**: transpile source TSX hiện tại bằng TypeScript đã cài, thay React hooks/API/socket bằng stub điều khiển được thứ tự, gọi chính callback của provider. Harness chỉ thêm export/điểm quan sát vào bản source trong bộ nhớ, không ghi hoặc sửa product code. Đây là kiểm tra logic xác định, **không phải browser hoặc Java live**.
- Không sửa mã sản phẩm, commit/push/merge/deploy, đổi branch/credential hoặc bật VM. Không đọc credential/private session. Giữ nguyên các thay đổi local có trước theo handoff.

## 2. Findings, theo thứ tự ưu tiên xử lý

### F1 — [P2] Tải bù lịch sử khi chat kết nối lại

**Vị trí:** `frontend-web/src/components/social-provider.tsx:366–372`, liên quan `380–383`.

**Trigger:** socket mất kết nối ngay sau một nhịp polling, người kia gửi tin trong lúc ngắt; reconnect sau khoảng 5 giây, trước nhịp polling 15 giây tiếp theo.

**Nguyên nhân:** `onStatus(true)` chỉ cập nhật `chatConnected`. Poll chỉ gọi `loadConversations()` khi socket đang disconnected. Khi reconnect nhanh, không có lần GET lịch sử nào để lấy các tin phát sinh trong khoảng trống. Java `ChatStompController` chỉ push tới subscription đang tồn tại; simple broker không có cơ chế replay lịch sử Mongo khi client SUBSCRIBE lại.

**Tác động:** người dùng đã kết nối lại và nhận được tin mới về sau, nhưng tin gửi trong khoảng ngắt vẫn thiếu cho tới khi mở lại cuộc trò chuyện, refresh dữ liệu hoặc reload trang.

**Bằng chứng mới:** harness khởi tạo socket effect, phát trạng thái `false → true`, sau đó chạy nhịp polling kế tiếp; số request `/api/chat/*` vẫn bằng **0**. Đối chiếu `services/chat-service/.../controller/ChatStompController.java:93–95` và `.../config/WebSocketConfig.java` xác nhận backend push trực tiếp, không replay khi subscribe.

**Sửa nhỏ nhất:** gọi đồng bộ lịch sử khi socket chuyển sang connected, dùng cơ chế merge theo message ID của F2; với cuộc trò chuyện đang mở, đồng bộ cả trạng thái read. Không cần endpoint hoặc thư viện mới.

**Regression phù hợp:** giữ client offline dưới 15 giây, gửi một tin từ tài khoản còn lại trong khoảng đó, reconnect và xác nhận tin xuất hiện đúng một lần mà không điều hướng/reload.

### F2 — [P2] Không ghi đè MESSAGE mới bằng snapshot REST cũ

**Vị trí:** `frontend-web/src/components/social-provider.tsx:284–287`; cùng cách ghi đè ở `138–147`.

**Trigger:** mở cuộc trò chuyện hoặc tải lại lịch sử; GET messages đã chụp danh sách nhưng response/read request còn chậm. Trong lúc đó, STOMP đưa một tin mới vào state. GET/read hoàn tất sau đó.

**Nguyên nhân:** `receiveMessage` append tin vào state, nhưng `openConversation` thay toàn bộ `messages` bằng `data` đã lấy trước đó. `loadConversations` cũng thay toàn bộ conversation map bằng snapshot REST. Dedup trong `receiveMessage` không bảo vệ được lần ghi đè sau này.

**Tác động:** tin đã hiển thị biến mất khỏi timeline; có thể mất cả bubble của tin người dùng vừa gửi dù server đã xác nhận. Khi socket còn connected, poll hiện tại không tự sửa trạng thái đó.

**Bằng chứng mới:** harness giữ GET messages với snapshot `[]`, gọi `receiveMessage` cho `new-message`, xác nhận state có **1** tin; thả response GET và hoàn tất `openConversation`, state còn **0** tin. Chạy chính callback từ source hiện tại, không viết lại thuật toán.

**Sửa nhỏ nhất:** ở cả hai đường tải lịch sử, dùng functional update merge snapshot với các tin nhận trong lúc request đang chạy, dedup theo ID và sắp theo `sentAt`. Snapshot server nên cập nhật các ID đã có; giữ các ID mới đến sau thời điểm bắt đầu tải. Tránh thay cả map nếu một cuộc trò chuyện khác vừa được thêm realtime.

**Regression phù hợp:** trì hoãn response lịch sử, push MESSAGE trước khi trả response, rồi xác nhận tin vẫn còn đúng một lần sau khi tải xong.

### F3 — [P2] Không dùng MESSAGE trùng ID để xác nhận SEND mới

**Vị trí:** `frontend-web/src/components/social-provider.tsx:299–301`, liên quan `308–310`.

**Trigger:** lịch sử đã có tin do chính người dùng gửi với nội dung `same text`. Người dùng gửi lại cùng nội dung, pending SEND chưa được xác nhận. Một MESSAGE cũ cùng ID/nội dung được phát lại trong lúc đang pending.

**Nguyên nhân:** kiểm tra duplicate chỉ nằm trong updater của `setConversations`. Sau đó callback vẫn chạy phần xác nhận pending, chỉ so sender/content/mediaUrl. ID cũ không bị loại khỏi đường xác nhận.

**Tác động:** promise của SEND mới resolve và component xóa draft dù chưa có bản ghi mới được xác nhận. Nếu SEND mới bị lỗi hoặc chưa đến server, UI vẫn đã báo gửi xong. Đây là đường lỗi riêng của cơ chế dedup/server confirmation, không cần coi mỗi MESSAGE phát lại là một tin mới.

**Bằng chứng mới:** harness nhận tin ID `already-seen` một lần, đặt pending mới có cùng nội dung, phát lại đúng DTO đó. Timeline vẫn chỉ có **1** bubble nhưng bộ đếm xác nhận pending tăng lên **1**, pending bị xóa, không có ID mới.

**Sửa nhỏ nhất:** theo dõi các ID đã biết trước lúc SEND và chỉ cho ID mới hợp lệ xác nhận pending; duplicate vẫn có thể được xử lý read nhưng không xác nhận thao tác mới. Khi gửi story reply, so khớp thêm metadata tương ứng. Không bắt buộc đổi giao thức backend để xử lý trường hợp tái hiện này.

**Regression phù hợp:** phát lại một echo cũ có cùng nội dung khi SEND mới pending; draft/promise phải tiếp tục chờ cho tới echo có ID mới hoặc lỗi thật.

### F4 — [P2] Giữ tham chiếu bài gốc khi ánh xạ bài chia sẻ

**Vị trí:** `frontend-web/src/lib/adapters.ts:15–22`; render liên quan `frontend-web/src/components/post-card.tsx:315–325`.

**Trigger:** feed hoặc profile chứa post được tạo bằng backend `POST /api/posts/{id}/share` mà không kèm lời dẫn. Đây là payload hợp lệ của backend, gồm `sharedPostId`, content null và media rỗng.

**Nguyên nhân:** `PostDto` có `sharedPostId`, nhưng `toPost` bỏ trường này và `Post`/`PostCard` không giữ đường đến nội dung gốc. Java `PostService.sharePost` tạo bài tham chiếu, không copy content/media gốc vào DTO của share.

**Tác động:** bài chia sẻ hợp lệ trở thành card không có nội dung; người đọc không mở được bài gốc từ card. Nếu có lời dẫn thì chỉ thấy lời dẫn, vẫn mất nguồn được chia sẻ.

**Bằng chứng mới:** gọi adapter thật với `{id:'share', authorId:'friend', sharedPostId:'original', content:null, mediaUrls:[]}` cho kết quả `text === ''`, `media.length === 0`, `sharedPostId === undefined`. Đối chiếu `services/post-service/.../service/PostService.java:249–270` và JSX card xác nhận không có đường render nguồn gốc thay thế.

**Sửa nhỏ nhất:** giữ `sharedPostId` trong view model và hiển thị ít nhất một Link đến `/post/{sharedPostId}`; có thể tải preview qua API hiện có nếu muốn giữ cách trình bày card. Khi bài gốc không còn khả dụng, hiển thị trạng thái đó. Không cần đổi thao tác share qua chat đang có.

**Regression phù hợp:** hydrate một share không có lời dẫn; card phải có liên kết mở bài gốc, không phải nội dung trống.

### F5 — [P2] Không dùng comment ID làm post ID trong notification

**Vị trí:** `frontend-web/src/components/social-provider.tsx:131–133`.

**Trigger:** người dùng nhận reaction trên bình luận của mình. Backend phát notification `type:REACTION`, `targetType:COMMENT`, `targetId:<comment ID>`; người dùng bấm notification.

**Nguyên nhân:** nhánh điều hướng gộp `POST` và `COMMENT` thành `/post/{targetId}`. `NotificationEventListener.onReaction` giữ nguyên targetType/targetId từ ReactionEvent, nên ID ở đây thực sự là comment ID.

**Tác động:** mở route post với ID không tồn tại và hiện lỗi/not found; notification sau đó còn có thể được mark read dù đích điều hướng sai.

**Bằng chứng mới:** harness trích chính khai báo `renderNotifications` bằng AST TypeScript, chạy notification COMMENT `comment-123`; href sinh ra là **`/post/comment-123`**. Đối chiếu `services/notification-service/.../event/NotificationEventListener.java:54–55` xác nhận payload backend.

**Sửa nhỏ nhất:** chỉ tạo `/post/{id}` trực tiếp cho targetType POST. Với COMMENT, dùng post nguồn khi đã có mapping hợp lệ; nếu chưa giải được nguồn thì giữ đích thông báo hợp lệ, không dựng URL post từ comment ID. Không cần tự thêm endpoint cho review này.

**Regression phù hợp:** tạo hai notification REACTION có target POST và COMMENT với ID khác nhau; không được tạo post route từ comment ID.

## 3. Kiểm tra chạy mới trong phiên review

| Kiểm tra | Kết quả mới |
|---|---|
| `npm run test:unit` | **12/12 đạt**, 0 skip; chạy ngoài sandbox vì loopback bị chặn trong sandbox |
| `npm run lint` | Exit **0** |
| `npm run typecheck` | Exit **0** |
| `node --test infra/no-docker/nextjs/run.test.mjs` | **1/1 đạt** |
| `python -m unittest discover -s infra/azure/tests -p test_auto_deploy.py` | **8/8 đạt**, đặt TEMP/TMP vào thư mục tạm trong workspace |
| Harness logic provider/adapter | Tái hiện **5/5** trường hợp mô tả ở trên; assertions xác nhận hành vi lỗi hiện tại |

Lượt unit trong sandbox ban đầu có 7 failures do không kết nối được HTTP/WebSocket loopback; cùng bộ test đạt 12/12 khi được chạy ngoài sandbox. Lượt auto-deploy đầu gặp WinError 5 tại `os.replace` trong temp sandbox; chạy lại với TEMP/TMP trong workspace đạt 8/8. Hai điểm môi trường này không được tính là finding sản phẩm.

Bộ test chat hiện có đã kiểm tra replay sau khi SEND hoàn tất và reconnect rồi retry SEND; chưa phủ replay **trong lúc pending**, tin gửi **trong khoảng offline**, hoặc response REST cũ kết thúc **sau MESSAGE mới**. Việc 12 unit đạt không mâu thuẫn với các trường hợp tái hiện trên.

Không build lại `.next` hoặc chạy browser fixture trong review này, để giữ build cuối trỏ gateway thật `18080`. Không chạy lại Java/cloud; các con số browser fixture 3/3, Java core 14, browser→Java 5 và reaction/common 34/34 trong bàn giao là **bằng chứng của phiên tích hợp**, không được trình bày thành lượt chạy mới ở đây.

## 4. Điểm đã đối chiếu và giới hạn

- HTTP client có epoch khi đổi phiên, refresh single-flight và xử lý 401 muộn; các unit hiện có cho những đường này đạt trong lượt chạy mới. Không có finding bổ sung về auth/account isolation qua phạm vi đã đọc; không suy rộng thành bảo đảm mọi interleaving.
- Các mutation post/comment/reaction/saved/profile đã gọi backend; kiểm tra UI quyền edit/delete comment dựa trên author ID. Regression lỗi GET sau POST comment đã có đường giữ kết quả ghi thành công. Không yêu cầu tái kiến trúc provider.
- Next rewrites/API origin, Caddy opt-in/validation trước promote, native runner và transaction `unsave` đã được đọc trực tiếp. Native runner/auto-deploy có kết quả chạy mới nêu trên; transaction không được chạy Maven lại trong phiên review.
- Bốn service Mongo TLS `internal_error`, Kafka Windows file-lock cũ, chưa chạy systemd/GitHub hosted/cloud là giới hạn đã ghi trong handoff; không đưa thành lỗi UI mới. Giữ TLS bật và dữ liệu cũ nguyên trạng.
- Quy định cộng tác tiếp nối vẫn theo đầy đủ [handoff](../handoffs/2026-10-25-ui-backend-integration.md#quy-tắc-cộng-tác-bắt-buộc--giữ-đầy-đủ-qua-mọi-handoff): tiếng Việt, `i-have-adhd` mọi phản hồi, `ponytail full` cho coding/review; tích cực, chủ động, số liệu trung thực, bước sửa vừa phạm vi; không nâng gate hoặc thêm thí nghiệm ngoài yêu cầu. Nếu có handoff tiếp phải chuyển đầy đủ quy định gốc đó.

**Bước tiếp theo của lượt đầu:** chat tích hợp xử lý F1–F3 trong cơ chế đồng bộ/xác nhận chat, thêm regression đúng trigger, rồi xử lý hai ánh xạ F4–F5 trước khi chốt bàn giao. Đã được kiểm tra lại ở mục 5.

## 5. Kiểm tra lại sau sửa — commit `24e8e7e`

Ngày 25/10/2026, cùng quy định cộng tác và phạm vi cho phép. Đã đọc `docs/worklogs/2026-10-25-ui-review-fixes.md`, diff **`3e6afd6..24e8e7e`**, source và hai file regression mới; xác nhận HEAD đầy đủ là `24e8e7ecb14fd533728b040251831019259129ed`. Lượt này chỉ xác minh F1–F5 và lỗi trực tiếp có thể phát sinh từ bản sửa, không mở rộng audit.

| Finding | Trạng thái sau kiểm tra lại | Source/bằng chứng |
|---|---|---|
| **F1 — reconnect thiếu tin** | **Đã xử lý** | `social-provider.tsx:384–386` gọi `loadConversations()` ngay khi connected; đường tải lịch sử `146–148` mark-read cuộc trò chuyện đang mở. Regression xác nhận GET history/read xuất hiện ngay khi reconnect, không cần đợi polling. |
| **F2 — snapshot xóa MESSAGE mới** | **Đã xử lý** | Functional merge ở `social-provider.tsx:153–160` và `302`, helper dedup theo ID/sắp `sentAt` ở `18–19`; snapshot giữ tin mới và cuộc trò chuyện khác được thêm trong lúc request chờ. Hai regression trì hoãn GET ở cả open và refresh đều đạt. |
| **F3 — echo cũ xác nhận SEND mới** | **Đã xử lý đối với trigger đã báo** | `social-provider.tsx:326` chụp `knownIds` trước SEND; `317–318` loại ID cũ và so thêm story metadata. ID từ REST được ghi nhận tại `149`, `300`; tập ID được clear khi đổi account. Regression xác nhận echo cũ không resolve, ID mới hợp lệ mới resolve; echo khác story cũng không resolve. |
| **F4 — share card trống** | **Đã xử lý** | `adapters.ts:20` giữ `sharedPostId`, `types.ts` bổ sung trường tương ứng; `post-card.tsx:327–332` render Link “Xem bài viết gốc” với ID được encode. Adapter và render card thật qua React server renderer đạt, gồm cả post thường không xuất hiện link thừa. |
| **F5 — comment ID thành post route** | **Đã xử lý** | `social-provider.tsx:135` chỉ dùng post route cho targetType POST; COMMENT đi `/notifications`. Regression phân biệt hai target và xác nhận không tạo `/post/comment-id`. |

**Lệnh chạy độc lập trong chat review:**

```powershell
# Từ frontend-web/
node --experimental-strip-types --test tests/provider-sync.test.mjs tests/shared-post.test.mjs
```

Kết quả mới: **9 tests, 9 pass, 0 fail, 0 skip, exit 0**; gồm 6 provider regressions và 3 adapter/render-card regressions. Đã đọc các test để xác nhận chúng gọi callback/adapter/component từ source thật thay vì sao chép thuật toán. Provider tests là logic harness với hooks/API/socket stub; render-card tests dùng React server renderer, không phải browser hoặc Java live.

Root báo unit toàn bộ 21/21, lint/typecheck exit 0, build fixture và browser 3/3 trong worklog; **không trình bày các con số đó là lượt chạy mới của chat review**. Theo yêu cầu tránh tranh runtime, không build `.next`, chạy browser/Java/cloud hoặc thay đổi mã sản phẩm trong lượt kiểm tra lại; chỉ sửa báo cáo này, không stage/commit/push hoặc bật VM.

Hai giới hạn được giữ đúng phạm vi, không coi thành finding mới:

1. **F3:** backend chưa có client correlation ID; echo ID mới từ tab khác với cùng content/media/story metadata vẫn không thể phân biệt chắc chắn. Bản sửa đóng trường hợp replay ID đã biết trước SEND được báo trong F3, không khẳng định bảo đảm correlation đầy đủ giữa các tab.
2. **F5:** notification COMMENT không có mapping post nguồn trong payload. Giữ `/notifications` là fallback hợp lệ, đóng lỗi dựng post URL sai; không khẳng định đã có deep link tới bình luận/post nguồn.

**Kết luận kiểm tra lại:** có thể đóng F1–F5 của lượt review này. Không phát hiện lỗi trực tiếp bổ sung cần xử lý trước khi chốt bản sửa trong phạm vi được giao.

**Bước tiếp theo:** chat tích hợp ghi nhận kết quả kiểm tra lại này và chốt bàn giao sau khi hoàn tất build cuối trỏ gateway thật `18080` đang do chat đó quản lý.
