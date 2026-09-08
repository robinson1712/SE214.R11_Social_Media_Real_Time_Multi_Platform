# TODO — Social Media Backend

Theo dõi tiến độ đồ án: đã làm gì, service nào xong, còn thiếu gì để hoàn chỉnh.

## Tổng quan

18 module Maven (`common-lib` + 3 service hạ tầng + 14 business service) đã **scaffold xong, build sạch, VÀ đã chạy thật qua Docker Compose thành công** — `mvn clean install` sạch + `docker compose up -d --build` từ trạng thái sạch hoàn toàn (`down -v`) lên đủ 16/16 service trên Eureka, test end-to-end qua gateway thật (đăng ký → login → post → feed → comment → group → dating swipe/match → auto-tạo chat conversation → notification) đều pass. Quá trình chạy thật này lộ ra **6 lỗi runtime thật** mà build/compile không bắt được — đã tìm và sửa hết, xem mục ngay dưới. Còn thiếu chủ yếu là: **chưa có test tự động**, và một số logic nghiệp vụ nâng cao vẫn ở mức đơn giản hoá có chủ đích.

---

## Đã hoàn thành — buổi trước (scaffold ban đầu)

- Thiết kế kiến trúc: Eureka + Config Server + API Gateway, JWT issue ở `auth-service` / xác thực tập trung ở gateway, forward `X-User-Id`/`X-User-Roles` xuống downstream.
- `common-lib`: `ApiResponse`/`PageResponse`, exception hierarchy (`ApiException` + 5 subclass + `GlobalExceptionHandler`), `JwtTokenProvider`, `CurrentUserContext`/`HeaderAuthFilter`, 11 Kafka event record + `KafkaTopics`, enum dùng chung.
- 3 service hạ tầng: `eureka-server`, `config-server` (native, đọc `config-repo/`), `api-gateway` (routing 14 service, JWT filter, CORS, WebSocket proxy).
- 14 business service — scaffold đầy đủ entity/repository/service/controller/DTO, Kafka producer/consumer đúng chỗ, Feign client nơi cần (comment→post, story/feed→user, feed→group/fanpage/post), WebSocket/STOMP thật cho chat + notification.
- `docker-compose.yml` (Postgres 9 DB, MongoDB 4 DB, Redis, Kafka KRaft, MinIO + 17 service) + Dockerfile từng service + `README.md`.
- Build verify: từng service riêng lẻ + toàn bộ reactor từ root — sạch, không lỗi.
- 2 artifact tài liệu: sequence diagram 4 luồng chính (auth/feed/chat/dating), catalog đầy đủ 191 class kèm minh hoạ 4 nguyên lý OOP bằng code thật.

## Đã hoàn thành — hôm nay

- **`ServiceUnavailableException`** (common-lib) — bổ sung vào exception hierarchy, dùng cho lỗi hạ tầng (503) phân biệt với lỗi nghiệp vụ (404/409...).
- **`group-service`**: sửa `GET /api/groups` để trả cả group PRIVATE mà caller đã là thành viên APPROVED (trước đó chỉ trả PUBLIC). Đổi JPQL entity name `Group` → `SocialGroup` để tránh xung đột với keyword `GROUP BY`.
- **Resilience4j circuit breaker** trên toàn bộ Feign client (`comment-service`, `story-service`, `feed-service`) — mỗi client có `fallbackFactory` riêng, cấu hình sliding window/failure threshold/half-open, phân biệt lỗi 404 hợp lệ với lỗi service sập.
- **Feed ranking theo engagement** (`feed-service`) — công thức kiểu Hacker News (`recencyScore × (1 + log(1 + reaction + 2×comment))`), tính lúc đọc chứ không phải lúc ghi.
- **Dating matching algorithm** (`dating-service`) — thêm `gender`/`birthDate` vào `DatingProfile` (trước đó thiếu, không đủ dữ liệu để tính tương thích thật), lọc tương thích giới tính hai chiều + chấm điểm 0-100 (tuổi 60đ, sở thích chung Jaccard 40đ), trả kèm điểm số qua `CandidateResponse`.
- Cập nhật `README.md` phản ánh đúng những gì đã hoàn thiện.
- **Rate limiting ở `api-gateway`** (Redis token-bucket, `RequestRateLimiter` của Spring Cloud Gateway) — bean `userOrIpKeyResolver` khoá theo `X-User-Id` nếu đã đăng nhập, fallback theo IP cho request công khai. Route `/api/auth/**` bị giới hạn chặt (1 req/s, burst 5) để chống brute-force login/spam đăng ký; 13 route còn lại dùng giới hạn chung thoáng hơn (20 req/s, burst 40 mỗi user). Route WebSocket (`/ws/**`) chưa áp rate-limit (rủi ro tương tác với handshake chưa kiểm chứng được vì chưa chạy Docker thật).

---

## 6 lỗi runtime tìm ra khi chạy Docker thật (build/compile không bắt được)

Đây là lý do vì sao bước "chạy thử thật" quan trọng — cả 6 lỗi dưới đây đều compile sạch, chỉ lộ ra khi thực sự gọi API qua container:

1. **17 jar không phải executable jar** — `spring-boot-maven-plugin` thiếu `<executions>` bind goal `repackage` vào phase `package` (root pom không kế thừa `spring-boot-starter-parent` nên không tự động có). → Sửa 1 chỗ ở root `pom.xml`.
2. **Script init multi-database Postgres lỗi** — `psql` thiếu `--dbname`, mặc định nối vào DB trùng tên user (không tồn tại). → Sửa `docker/postgres/init-multi-db.sh`.
3. **Mọi Kafka consumer lỗi deserialize** (`No type information in headers`) — producer tắt `spring.json.add.type.headers` nhưng consumer không có type mặc định thay thế. Lỗi hệ thống trên **12 service**. → Bỏ dòng tắt header ở cả 12 `application.yml`, dùng lại default (Spring Kafka tự thêm `__TypeId__` header).
4. **Toàn bộ `@PathVariable`/`@RequestParam` không tên tường minh bị lỗi 500** (`Name ... not specified, and parameter name information not available via reflection`) — thiếu cờ compiler `-parameters` (cũng do không kế thừa `spring-boot-starter-parent`). → Sửa 1 chỗ ở root `pom.xml` (`<parameters>true</parameters>`).
5. **`Account.roles` (auth-service) lazy-init exception** khi serialize `/api/auth/me` — `@ElementCollection` thiếu `fetch = FetchType.EAGER` trong khi `open-in-view: false`. → Thêm EAGER.
6. **`Unable to access lob stream`** trên post/comment/dating/group/fanpage — dùng `@Lob` cho field text thường trên Postgres (tạo kiểu `oid` cần stream trong transaction, vỡ khi session đã đóng). → Đổi cả 5 field sang `@Column(columnDefinition = "TEXT")`.
7. *(bonus, phát hiện cùng đợt)* **`function lower(bytea) does not exist`** ở `GET /api/groups` — tham số JPQL `null` khiến Postgres JDBC không suy được kiểu text. → Đổi sang truyền chuỗi rỗng `""` thay vì `null`.

Sau khi sửa hết, đã build sạch từ đầu (`mvn clean install`) + `docker compose down -v && up -d --build` một lần nữa và test lại toàn bộ — pass hết, không cần patch tay.

## Lỗi thứ 8: WebSocket của notification-service không thể truy cập qua gateway

Phát hiện khi test STOMP thật (dùng `java.net.http.WebSocket` viết tay, không có node/wscat trong máy): `chat-service` và `notification-service` đều đăng ký STOMP endpoint tại **cùng path `/ws`**, nhưng `api-gateway` chỉ có một route `/ws/**` → `CHAT-SERVICE`. Nghĩa là client **không bao giờ gọi được** WebSocket của notification-service qua cổng công khai (8080) — chỉ có cách bypass gateway gọi thẳng port 8093 (không qua xác thực JWT).

→ Đổi endpoint của notification-service thành `/ws-notifications` (`WebSocketConfig.java`), thêm route riêng `notification-service-ws` → `NOTIFICATION-SERVICE` trong gateway. Verify lại bằng chương trình Java tự viết (`java.net.http.WebSocket`, không cần cài node/wscat):

- **Chat**: Alice & Bob kết nối `ws://localhost:8080/ws/websocket?token=...` qua gateway (JWT qua query param, gateway set `X-User-Id`, `CONNECTED` frame trả đúng `user-name`) → Alice gửi `SEND /app/chat.send` → **Bob nhận `MESSAGE` frame real-time đúng nội dung** trên `/user/queue/messages`.
- **Notification**: Bob subscribe `ws://localhost:8080/ws-notifications/websocket?token=...` → Alice gửi friend-request qua REST → **Bob nhận `MESSAGE` frame real-time** trên `/user/queue/notifications` với đúng nội dung `FriendRequestEvent`.

Đây đúng là loại lỗi mà `TODO.md` đã cảnh báo trước — unit test (mock) không bắt được vì nó nằm ở tầng routing/hạ tầng, chỉ lộ ra khi gọi qua gateway thật.

## Unit test — 279 test trên toàn bộ 14/14 business service

Thêm `CurrentUserContext.setForTests()/clearForTests()` (public, common-lib) làm "cửa hậu" test-only cho service dùng `CurrentUserContext.getUserId()` — `set()`/`clear()` gốc vẫn package-private, chỉ `HeaderAuthFilter` gọi được ở production. Một số service (`user-service`, `media-service`) nhận `currentUserId` qua tham số method thay vì đọc `CurrentUserContext` trực tiếp nên không cần seam này.

Bắt đầu với 3 service lõi (`auth-service`, `post-service`, `dating-service` — 40 test, tự làm), sau đó giao 6 batch song song cho agent viết nốt 11 service còn lại (mỗi agent tự đọc source thật, viết test theo đúng pattern, tự chạy `mvn test` verify trước khi báo cáo).

| Service | Số test | Điểm đáng chú ý |
|---|---|---|
| `auth-service` | 14 | register/login/refresh/logout/me — mọi nhánh lỗi |
| `post-service` | 14 | ownership check, denormalized count clamp về 0 |
| `dating-service` | 12 | **điểm tương thích khớp chính xác 80** — trùng con số verify qua curl thật |
| `comment-service` | 16 | soft-delete, Feign 404→ResourceNotFound |
| `reaction-service` | 11 | upsert cùng 1 row, projection `TypeCount` |
| `story-service` | 11 | delta hết hạn 24h chính xác, Set idempotent |
| `reels-service` | 18 | listener denormalized count (comment/reaction) |
| `group-service` | 28 | `""` thay `null` cho JDBC type-inference, memberCount clamp |
| `fanpage-service` | 24 | chặn xoá owner cuối cùng của page |
| `feed-service` | 22 | **verify công thức ranking bằng số liệu tính tay** (viral ≈0.345 vs fresh ≈0.333) |
| `chat-service` | 22 | dedup PRIVATE conversation, MessageEvent recipientId theo loại hội thoại |
| `notification-service` | 21 | cả 6 nhánh `@KafkaListener` + toàn bộ điều kiện skip |
| `user-service` | 50 | friend request/block/unblock — nhánh quyền requester/addressee |
| `media-service` | 16 | MinIO put/remove qua captor, sanitize filename |

**Không có bug sản xuất nào bị phát hiện** trong toàn bộ 11 service ở đợt này — mọi hành vi khớp đúng với những gì đã thiết kế/verify qua Docker trước đó.

Build+test toàn reactor (`mvn clean install`, không `-DskipTests`): **18/18 module, 279/279 test pass**, không có xung đột dù 6 agent chỉnh sửa song song.

---

## Trạng thái từng service

| Service | Port | Trạng thái | Còn thiếu / có thể nâng cấp |
|---|---|---|---|
| `eureka-server` | 8761 | ✅ Xong | — |
| `config-server` | 8888 | ✅ Xong | Đang bị dùng ít — có thể đẩy thêm config dùng chung (vd. resilience4j defaults) sang đây |
| `api-gateway` | 8080 | ✅ Xong + rate limit (kể cả route WebSocket) | — |
| `auth-service` | 8081 | ✅ Xong + admin allowlist | Chưa có: reset mật khẩu, xác minh email, khoá tài khoản sau N lần login sai, OAuth2/social login |
| `user-service` | 8082 | ✅ Xong + tìm kiếm user | Chưa có: gợi ý bạn chung |
| `media-service` | 8083 | ✅ Xong + whitelist content-type + giới hạn size theo purpose | Chưa có: resize ảnh/tạo thumbnail, transcode video |
| `post-service` | 8084 | ✅ Xong + profanity filter + xoá theo report + ghim bài + share/tag/custom-audience + enforce privacy lúc đọc | Chưa có: lịch sử chỉnh sửa |
| `comment-service` | 8085 | ✅ Xong + circuit breaker + profanity filter + xoá theo report | — |
| `reaction-service` | 8086 | ✅ Xong | Đơn giản hoá có chủ đích: nhận `targetOwnerId` từ client thay vì tự resolve qua Feign |
| `story-service` | 8087 | ✅ Xong + circuit breaker | Chưa có: story highlights (lưu vĩnh viễn), reply story qua chat |
| `reels-service` | 8088 | ✅ Xong + engagement ranking (giống feed-service) | — |
| `group-service` | 8089 | ✅ Xong + fix visibility + vai trò MODERATOR | Chưa có: kiểm duyệt bài đăng trong group |
| `fanpage-service` | 8090 | ✅ Xong | Chưa có: thống kê/insight cho page |
| `dating-service` | 8091 | ✅ Xong + matching algorithm + unmatch | Chưa có: report trong ngữ cảnh dating, xác minh ảnh |
| `chat-service` | 8092 | ✅ Xong (WebSocket thật) + xoá tin nhắn (sender-only) | Chưa có: sửa tin nhắn, typing indicator, read-receipt chi tiết (hiện chỉ có mark-all-read) |
| `notification-service` | 8093 | ✅ Xong (WebSocket thật) | Chưa có: push notification ra mobile (FCM/APNs), cài đặt loại thông báo |
| `feed-service` | 8094 | ✅ Xong + engagement ranking | Chưa gộp story/reels vào cùng feed logic |
| `moderation-service` | 8095 | ✅ Xong (report + admin review queue) | Đã mở rộng đủ 6 loại: post/comment/reels/story/group/fanpage đều nghe `ContentRemovedEvent` |
| `search-service` | 8096 | ✅ Xong (tìm kiếm tổng hợp, không có DB riêng) | Chưa có: xếp hạng kết quả theo độ liên quan (hiện chỉ trả theo thứ tự mỗi service tự sắp) |

**16/16 business service + 3/3 hạ tầng đều đã hoàn thành scaffold và build được** — không có service nào còn ở dạng rỗng/chưa code. (16 = 15 service ban đầu + `search-service` mới thêm; `moderation-service` đã tính trong 15 đó.)

---

## ✅ Integration test (Testcontainers) — đã verify PASS thật trên GitHub Actions

Đã viết `GroupRepositoryIntegrationTest` (`group-service`) — chạy chính JPQL `findVisibleGroups` (câu query từng gây lỗi `lower(bytea)` thật) trên **Postgres thật** qua Testcontainers, không phải mock. 5 test: filter rỗng không lỗi, private group chỉ hiện với member đã APPROVED (không hiện với PENDING/người lạ/ẩn danh), filter tên không phân biệt hoa-thường, sanity check mapping entity `SocialGroup`→bảng `groups`.

**Không chạy được trong môi trường sandbox local**: Testcontainers không kết nối được Docker daemon (`docker` CLI hoạt động bình thường nhưng `docker-java` gọi thẳng Docker API qua named pipe bị chặn/trả response rỗng — giới hạn bảo mật tầng host, không phải lỗi code). Đã cấu hình `maven-surefire-plugin` loại `**/*IntegrationTest.java` khỏi `mvn test` mặc định để không chặn build local.

**Đã xác nhận đúng dự đoán khi CI/CD chạy trên GitHub Actions** (xem mục CI/CD dưới) — bước "Run Testcontainers integration test (group-service)" **pass thật** trên runner Ubuntu có Docker không giới hạn, đúng 20 giây, không cần patch gì thêm. Đây là bằng chứng trực tiếp: code hoàn toàn đúng, chỉ là môi trường dev local đặc thù mới bị chặn.

## ✅ Swagger/OpenAPI UI — đã thêm vào cả 14/14 business service

`springdoc-openapi-starter-webmvc-ui` (version đã khai sẵn ở root `pom.xml`) được thêm vào tất cả 14 service. Verify trực tiếp qua Docker thật (`/swagger-ui/index.html` HTTP 200, `/v3/api-docs` trả đúng OpenAPI JSON với schema request/response chính xác) trên `post-service`, `dating-service`, `group-service`, và cả `chat-service` (service dùng WebSocket — REST endpoint vẫn generate đúng, STOMP tất nhiên không thuộc OpenAPI vì khác giao thức). Build lại toàn reactor + redeploy Docker: **18/18 module, 279/279 test vẫn pass**.

Truy cập: mỗi service có Swagger UI riêng trên port của nó, ví dụ `http://localhost:8084/swagger-ui/index.html` (post-service). Chưa gộp qua gateway thành 1 điểm truy cập chung (mỗi service vẫn expose riêng lẻ) — có thể cải thiện sau nếu cần.

## ✅ Observability — distributed tracing (Zipkin) + centralized logging (Loki/Promtail/Grafana)

Chọn bộ nhẹ thay vì ELK (Elasticsearch quá nặng RAM so với tài nguyên máy đang có, từng thấy rõ khi 22 container đã gây tranh chấp CPU):

- **Tracing**: `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` thêm vào `api-gateway` + 14 business service (15 service, bỏ qua eureka-server/config-server vì không nằm trong luồng request). `management.tracing.sampling.probability: 1.0` (100% để dễ demo — production thật nên hạ xuống). Container `zipkin` (port 9411).
- **Kafka tracing**: mặc định Spring Boot 3 **tắt** observation cho Kafka producer/consumer — phải bật tường minh `spring.kafka.template.observation-enabled: true` + `spring.kafka.listener.observation-enabled: true` (13 service có Kafka) mới nối được nhánh async vào cùng trace.
- **Logging tập trung**: `loki` (lưu trữ/query log) + `promtail` (tự động discover mọi container qua Docker socket, ship log lên Loki, gắn nhãn theo tên service — không cần cấu hình riêng khi thêm service mới) + `grafana` (UI hợp nhất, anonymous admin access cho demo, đã provision sẵn 2 datasource Loki + Zipkin).

**Đã verify bằng dữ liệu thật** (không chỉ container "Up"):
- Trace 1 request `POST /api/auth/register` cho ra **5 span xuyên 3 service với cùng 1 traceId**: `api-gateway` (SERVER+CLIENT) → `auth-service` (SERVER, rồi PRODUCER gửi `UserRegisteredEvent`) → `user-service` (CONSUMER nhận event) — thấy được toàn bộ đường đi kể cả nhánh Kafka bất đồng bộ, không chỉ phần REST.
- Query Loki xác nhận log của mọi container đều được hút về, và **mỗi dòng log tự động có `[traceId-spanId]`** (Spring Boot tự thêm khi có Micrometer Tracing) — nghĩa là từ 1 trace trong Zipkin có thể tra ngược đúng log chi tiết trong Loki bằng traceId.
- Grafana `/api/health` 200, 2 datasource (Loki, Zipkin) provision đúng qua file, không cần cấu hình tay.

Build+test toàn reactor + redeploy Docker 2 lần (thêm tracing dep, rồi bật Kafka observation): **18/18 module, 279/279 test vẫn pass**.

Truy cập: Zipkin UI `http://localhost:9411`, Grafana `http://localhost:3000` (anonymous admin), Loki API `http://localhost:3100` (thường dùng qua Grafana Explore chứ không gọi trực tiếp).

## ✅ CI/CD pipeline (GitHub Actions)

Repo đã có remote GitHub thật (`robinson1712/SE347-TSON-social-media-real-time-system`) nên dùng GitHub Actions, file `.github/workflows/ci-cd.yml`, 2 job:

1. **`build-and-test`** (mọi push/PR vào `main`): `mvn clean install` (build 18 module + chạy 279 unit test) → chạy riêng `GroupRepositoryIntegrationTest` (Testcontainers) — runner Ubuntu của GitHub có Docker thật, không bị giới hạn như môi trường sandbox local, nên đây chính là nơi verify được test này thay vì máy dev. Upload surefire report làm artifact để debug khi fail.
2. **`docker-images`** (cần job 1 xanh): build lại jar (`-DskipTests`, đã test ở job 1) → build Docker image cho cả 17 service (validate Dockerfile không vỡ trên **mọi** PR) → **chỉ push lên GHCR** (`ghcr.io/<owner>/<repo>-<service>:latest` + `:<sha>`) khi là push thẳng vào `main`, dùng `GITHUB_TOKEN` có sẵn, không cần secret riêng.

**Sửa kèm 1 lỗi phát hiện lúc làm CI**: `.gitignore` thiếu `target/` — **158 file build artifact** (jar, class, log Maven) đã bị lỡ track vào git từ trước. Đã cập nhật `.gitignore` (`target/`, `.idea/`, IDE noise) và `git rm --cached` gỡ tracking (file vẫn còn trên đĩa, chỉ không theo dõi nữa).

**Đã commit + push (`adc4245`) và verify PASS 100% thật trên GitHub Actions** (run #1, ~5.5 phút, [xem log](https://github.com/robinson1712/SE347-TSON-social-media-real-time-system/actions/runs/33243483057)):
- `build-and-test`: `mvn clean install` xanh + **`GroupRepositoryIntegrationTest` (Testcontainers) pass thật** trong 20s trên runner có Docker không giới hạn — xác nhận đúng dự đoán, không phải lỗi code.
- `docker-images`: build + push cả 17 image lên GHCR thành công, dùng `GITHUB_TOKEN` có sẵn không cần secret riêng.

**Lỗi phát hiện ở run #5 (sau khi thêm moderation-service + mở rộng sang reels/story/group/fanpage),
đã sửa**: job `docker-images` fail ở bước push — log cho thấy `feed-service` (service cuối trong danh
sách) bị lỗi `unknown blob` ngay sau dòng `Mounted from ...notification-service` (Docker cố cross-repo
blob-mount 1 layer base dùng chung giữa 2 image GHCR, registry chưa kịp commit blob ở image trước).
Đây là race condition đã biết của GHCR khi push nhiều image chia sẻ layer gốc liên tiếp trong cùng 1
job — không phải lỗi code/Dockerfile (đã xác nhận: build lại cả 17 image y hệt bước CI ngay trên máy
local, tất cả build thành công, không lỗi). Fix: bọc mỗi lệnh `docker push` trong hàm `push_with_retry`
(retry tối đa 3 lần, nghỉ 10s giữa các lần). Tiện thể phát hiện và sửa luôn 1 gap riêng: `moderation-service`
(thêm từ 2 commit trước) chưa từng có trong danh sách `SERVICES` của CI — chưa từng được build/push.
Cũng cập nhật job name/step name từ "279 unit tests" (số cũ, đã lỗi thời) lên đúng **334 unit tests**
hiện tại.

## ✅ Content moderation

Ba lớp bổ sung nhau, không thay thế nhau:

- **Profanity filter tự động** (`common-lib` → `ProfanityFilter`): danh sách từ cấm tiếng Anh + tiếng
  Việt, so khớp substring sau khi normalize Unicode **NFC** (compose, không decompose) và chỉ bỏ
  ký tự không phải chữ/số (để chặn né tránh kiểu chèn dấu câu/khoảng trắng). **Cố ý giữ nguyên dấu
  thanh tiếng Việt** — không dùng NFKD/strip combining marks, vì dấu thanh tiếng Việt mang nghĩa
  (khác dấu phụ tiếng Pháp): bug thật đã phát hiện lúc viết test — "đi chơi" (đi, bỏ dấu) từng bị
  chuẩn hoá trùng với "đĩ" (một từ tục) khi dùng NFKD, gây false positive nghiêm trọng cho câu hoàn
  toàn vô hại. Đã sửa và thêm test `cleanText_isNotFlagged` để khoá lại hành vi đúng. Áp dụng ở
  `post-service` (`createPost`/`updatePost`) và `comment-service` (`createComment`/`updateComment`)
  — chặn ngay lúc tạo/sửa, trả lỗi 400 trước khi ghi DB. Có giới hạn đã biết và ghi rõ trong Javadoc:
  so khớp substring gây false positive kiểu "Scunthorpe problem" (từ vô hại chứa chuỗi con là từ
  cấm) — chấp nhận đánh đổi vì đơn giản, không cần NLP.
- **User report + admin review queue** (`moderation-service`, port 8095, Postgres `moderation_db`,
  service thứ 18): user gọi `POST /api/moderation/reports` báo cáo nội dung (targetType/targetId/
  reason/description), tự xem lại report của mình qua `GET /api/moderation/reports/mine`. Admin
  (role `ADMIN`, gate bằng `CurrentUserContext.getRoles()`) xem hàng đợi `GET /api/moderation/reports`
  (filter theo status/targetType), xử lý qua `POST /api/moderation/reports/{id}/resolve` với 2 hành
  động: `DISMISS` (đóng report, không hành động) hoặc `REMOVE_CONTENT` (đánh dấu `ACTION_TAKEN` +
  publish `ContentRemovedEvent` lên Kafka topic `content-removed-events`).
- **Xoá nội dung xuyên service qua Kafka** (event-driven, không gọi REST đồng bộ): `post-service` và
  `comment-service` đều có `@KafkaListener` mới lắng nghe `ContentRemovedEvent`, lọc theo
  `targetType` (`"POST"`/`"COMMENT"`), gọi `removeForModeration(targetId)` — post-service xoá cứng,
  comment-service soft-delete (nhất quán với cách xoá thường của từng service). Đây là lần đầu
  comment-service có `@KafkaListener` (trước đó chỉ producer).
- **Cấp quyền ADMIN lúc đăng ký** (`auth-service` → `AdminEmailAllowlist`): đọc danh sách email admin
  từ `ADMIN_EMAILS` (comma-separated, env var, mặc định rỗng) — email nào khớp allowlist được gán
  role `["USER","ADMIN"]` lúc `register()`, còn lại chỉ `["USER"]`. Đơn giản hoá có chủ đích cho đồ
  án (không cần UI quản trị cấp quyền riêng) — ghi rõ trong `application.yml` với comment giải thích.

Build+test toàn reactor (18 module, gồm `moderation-service` mới — 19 module thật ra vẫn tính là
18 service nghiệp vụ+hạ tầng vì `common-lib` không tính là service riêng): **mvn clean install pass,
`ModerationServiceTest` 11/11 pass** (createReport có/không auth, getMyReports, listReports/getReport
gate ADMIN, resolveReport 2 nhánh DISMISS/REMOVE_CONTENT + verify đúng `ContentRemovedEvent` publish
qua `ArgumentCaptor`, resolveReport report đã xử lý rồi → 409 Conflict).

`docker-compose.yml`: thêm `moderation_db` vào `POSTGRES_MULTIPLE_DATABASES`, container
`moderation-service` (port 8095), `ADMIN_EMAILS: admin@social.app` vào `x-app-env` dùng chung.
`api-gateway`: thêm route `/api/moderation/**` → `MODERATION-SERVICE` (cùng rate-limit mặc định như
các service khác).

**Đã verify bằng luồng thật qua Docker + curl (không chỉ unit test mock)**, redeploy full 18 service:
1. Đăng ký `admin@social.app` (nằm trong `ADMIN_EMAILS`) → token JWT có đúng `"roles":["USER","ADMIN"]`; đăng ký email thường → chỉ `["USER"]`.
2. `POST /api/posts` với nội dung tục ("...fucking...") → **400 "Content violates community guidelines"**, không tạo được.
3. Tạo post sạch, user thường report qua `POST /api/moderation/reports` → 200, status `PENDING`.
4. User thường gọi `GET /api/moderation/reports` (hàng đợi) → **403 "Admin access required"** — đúng gate.
5. Admin gọi cùng endpoint → thấy đúng report vừa tạo, status `PENDING`.
6. Admin gọi `PUT /api/moderation/reports/{id}/resolve` với `REMOVE_CONTENT` → 200, status chuyển `ACTION_TAKEN`, `reviewedBy` đúng accountId admin.
7. Gọi lại `GET /api/posts/{id}` (không qua moderation-service, thẳng post-service) → **404 "Post not found"** — xác nhận `ContentRemovedEvent` đã đi qua Kafka thật, `post-service` nhận và xoá đúng object trong Postgres, không phải giả lập.

**Sự cố phát hiện lúc verify (đã fix, không phải bug code)**: volume Postgres đã tồn tại từ lần chạy
trước, nên script `init-multi-db.sh` (chỉ chạy khi khởi tạo volume rỗng) không tự tạo `moderation_db`
mới — phải tạo thủ công (`CREATE DATABASE moderation_db`) rồi restart container. Đây là đặc thù môi
trường dev cục bộ (volume tái sử dụng), không ảnh hưởng lúc `docker compose down -v` sạch hoặc deploy
lần đầu.

## ✅ Mở rộng content moderation sang reels/story/group/fanpage

Áp dụng đúng pattern đã có ở post/comment cho 4 service còn lại trong danh sách `ReportTargetType`
(POST/COMMENT/REEL/STORY/GROUP/FANPAGE — nay đã đủ 6/6, không còn service nào thiếu):

- **Profanity filter lúc tạo nội dung**: `ReelService.createReel()` (field `caption`),
  `StoryService.createStory()` (field `caption`, có thể null vì story chủ yếu là media — filter xử lý
  null/blank an toàn), `GroupService.createGroup()` (`name` + `description`),
  `FanpageService.createPage()` (`name` + `description`). Cả 4 service này vốn chỉ có create, không
  có update — nên không cần đụng vào luồng edit.
- **`removeForModeration(id)`** thêm vào cả 4 service, theo đúng kiểu idempotent (no-op nếu đã bị xoá)
  như post/comment. Khác biệt so với post (xoá đơn giản, 1 bảng): `group-service` và `fanpage-service`
  có bảng phụ thuộc (`GroupMember`, `PageAdmin`/`PageFollower`) — phải xoá cascade thủ công trước khi
  xoá entity chính (không có cascade ORM sẵn), nên thêm `deleteByGroupId`/`deleteByPageId` vào các
  repository tương ứng.
- **Kafka listener nghe `ContentRemovedEvent`**: `reels-service` đã có `ReelEventListener` sẵn (dùng
  cho comment/reaction denormalized count) — chỉ thêm 1 method mới vào đó. 3 service còn lại
  (`story-service`, `group-service`, `fanpage-service`) **chưa từng có `@KafkaListener` nào** — đây là
  lần đầu chúng trở thành Kafka consumer chứ không chỉ producer, tương tự việc comment-service lần đầu
  có listener ở đợt post/comment trước đó. **Phát hiện gap cấu hình**: `group-service` và
  `fanpage-service`'s `application.yml` trước đó chỉ có `spring.kafka.producer`, hoàn toàn thiếu
  `spring.kafka.consumer` — phải thêm cả block consumer (group-id, deserializer, trusted-packages) thì
  `${spring.kafka.consumer.group-id}` trong `@KafkaListener` mới resolve được, nếu không sẽ lỗi khởi
  động ngay khi bean được tạo.

Build+test toàn reactor (18 module): **mvn clean install pass**. Test mới/cập nhật: `ReelServiceTest`
13 test (thêm 3: profanity + removeForModeration hit/miss), `ReelEventListenerTest` 10 test (constructor
đổi vì thêm `ReelService` dependency, thêm 2 test `onContentRemoved`), `StoryServiceTest` 14 test (+3),
`StoryEventListenerTest` 2 test (file mới), `GroupServiceTest` 32 test (+4, bao gồm verify
`deleteByGroupId` được gọi trước `delete`), `GroupModerationListenerTest` 2 test (file mới),
`FanpageServiceTest` 28 test (+4, verify cả `deleteByPageId` cho follower lẫn admin), 
`FanpageModerationListenerTest` 2 test (file mới) — tổng 103 test qua 8 file, tất cả pass.

**Đã verify bằng luồng thật qua Docker + curl** (redeploy 4 service, không chỉ unit test mock), chọn
2 case phức tạp nhất — `group-service` và `fanpage-service` — vì đây là 2 service duy nhất có cascade
cleanup (bảng phụ thuộc `GroupMember`, `PageAdmin`/`PageFollower`):
1. Tạo group tên tục ("...fucking...") → **400**; tạo fanpage description tục → **400** — profanity filter chặn đúng ở cả 2.
2. Tạo group/fanpage sạch, report qua `POST /api/moderation/reports` với `targetType: GROUP`/`FANPAGE`.
3. Admin `PUT /api/moderation/reports/{id}/resolve` với `REMOVE_CONTENT` → 200, `resolutionNote` lưu đúng.
4. `GET /api/groups/{id}` và `GET /api/pages/{id}` sau đó → **404** — xác nhận `ContentRemovedEvent` đã xoá thật qua Kafka.
5. Log container xác nhận đúng listener xử lý: `GroupModerationListener` và `FanpageModerationListener` đều log dòng "Removing {type} {id} — moderation report ..." trước khi gọi `removeForModeration`.

`reels-service`/`story-service` dùng đúng pattern đơn giản hơn (không có bảng phụ thuộc, giống hệt
post-service) nên không lặp lại live-verify — đã được cover đầy đủ qua unit test.

## ✅ Metrics/dashboard (Prometheus + Grafana)

Hoàn thiện mảnh cuối của observability (đã có tracing qua Zipkin + log tập trung qua Loki từ trước):

- **`micrometer-registry-prometheus`** thêm vào `common-lib` (1 chỗ, tự động có ở mọi service phụ
  thuộc common-lib thay vì sửa tay 15 pom riêng lẻ) + thêm trực tiếp vào `eureka-server`/`config-server`
  (2 service không phụ thuộc common-lib). Bật `/actuator/prometheus` (`exposure.include`) và
  `management.metrics.distribution.percentiles-histogram.http.server.requests: true` (cần cho
  `histogram_quantile` tính p99 — thiếu dòng này thì Prometheus không có bucket data) ở cả 18 file
  `application.yml`.
- **Prometheus tự phát hiện service qua Eureka** (`eureka_sd_configs`) thay vì liệt kê tay từng
  service/port trong `prometheus.yml` — cố ý tránh lặp lại đúng lỗi vừa gặp ở CI (`moderation-service`
  bị quên trong danh sách cứng `SERVICES` của `ci-cd.yml`, xem mục CI/CD ở trên). Riêng `eureka-server`
  tự nó không đăng ký làm Eureka client (`register-with-eureka: false` vì nó chính là registry) nên
  không thể được Eureka SD phát hiện — phải scrape tĩnh riêng 1 job cho nó.
- **Dashboard Grafana "Service Overview" provision sẵn** (file JSON + provider config mount qua
  Docker volume, không cần tự tạo tay lúc demo): 6 panel — services up (stat), request rate theo
  service, p99 latency theo service, error rate 5xx % theo service, JVM heap dùng theo service, CPU
  usage theo service.

**Đã verify bằng dữ liệu Prometheus/Grafana thật** (không chỉ container "Up"), sau khi redeploy full
18 service + container `prometheus` mới:
1. `GET /api/v1/targets` → **18/18 target `health: up`** (17 service qua `eureka-discovered-services`
   job tự phát hiện + 1 `eureka-server` scrape tĩnh) — đúng số lượng service hiện có.
2. `GET /actuator/prometheus` trên `post-service` → có `http_server_requests_seconds_bucket` (69
   dòng, đủ bucket cho `histogram_quantile`) và `jvm_memory_used_bytes` (10 dòng).
3. Tạo traffic thật qua gateway (đăng ký user, 20 lần tạo post + đọc, vài request 404) rồi query trực
   tiếp đúng 4 công thức dashboard dùng: **request rate theo service có số liệu thật** (`POST-SERVICE`
   0.65 req/s, `API-GATEWAY` 0.6 req/s — cao nhất, đúng nơi traffic đổ vào), **p99 latency theo service
   có số liệu thật** cho toàn bộ 16/16 service có traffic, **error rate 5xx trả tập rỗng đúng** (chưa
   có lỗi 5xx nào xảy ra — không phải bug), **services-up count = 18** khớp số target.
4. `GET /api/search` trên Grafana → dashboard `service-overview` đã tự provision, không cần tạo tay.
   `GET /api/datasources` → datasource Prometheus (`uid: prometheus`) đã đăng ký đúng, khớp UID dashboard JSON tham chiếu.
5. `GET /api/dashboards/uid/service-overview` → cả 6 panel load đúng title, không panel nào lỗi do sai UID/query.

Build+test toàn reactor trước khi redeploy: **18/18 module pass** (334 unit test, không đổi vì đây
chỉ là thay đổi cấu hình/dependency, không sửa logic nghiệp vụ).

Truy cập: Prometheus UI `http://localhost:9090` (Status → Targets để xem trạng thái scrape); dashboard
Grafana mở sẵn tại `http://localhost:3000/d/service-overview` (anonymous admin, không cần đăng nhập).

## ✅ Nâng cấp nhỏ theo từng service

8 mục nhỏ, mỗi mục có test riêng (đơn vị + verify thật qua Docker), chọn có chủ đích những thứ thực
sự "nhỏ" — bỏ qua các mục lớn hơn trong danh sách gốc (OAuth2, resize/transcode video, FCM push...):

1. **`api-gateway`**: thêm `RequestRateLimiter` cho 2 route WebSocket `/ws/**` và `/ws-notifications/**`
   (trước đó là 2 route duy nhất chưa có rate-limit trong khi mọi route REST khác đều có).
2. **`user-service`**: `GET /api/users/search?q=` tìm theo `fullName` (substring, không phân biệt hoa
   thường, phân trang) — email không tìm được vì user-service không lưu email (auth-service giữ).
3. **`media-service`**: whitelist content-type (chỉ ảnh jpeg/png/gif/webp + video mp4/webm/quicktime,
   trước đó không chặn gì ngoài dung lượng) và giới hạn riêng 10MB cho ảnh ở các purpose luôn-là-ảnh
   (AVATAR/COVER/PAGE/GROUP) — purpose có thể là video (POST/STORY/REEL/CHAT) vẫn dùng giới hạn 50MB
   sẵn có ở tầng servlet.
4. **`post-service`**: ghim bài (`pinned` field + `PUT`/`DELETE /api/posts/{id}/pin`, chỉ author) —
   `GET /api/posts/author/{id}` giờ sắp bài ghim lên đầu trước khi sắp theo thời gian.
5. **`reels-service`**: áp dụng đúng công thức ranking Hacker-News-style của `feed-service`
   (bounded pool → score → sort → paginate in memory) cho `getFeed` — trước đó thuần theo thời gian.
6. **`dating-service`**: `DELETE /api/dating/matches/{id}` (unmatch) — chỉ 1 trong 2 người của match.
7. **`group-service`**: thêm role `MODERATOR` (giữa `ADMIN`/`MEMBER`) — được duyệt thành viên chờ
   nhưng **không** được xoá thành viên hay đổi role người khác (vẫn ADMIN-only). Thêm
   `PUT /api/groups/{id}/members/{userId}/role` (ADMIN-only, chặn hạ role ADMIN cuối cùng — cùng
   pattern "last owner guard" đã có ở `fanpage-service`).
8. **`chat-service`**: `DELETE /api/chat/messages/{id}` — chỉ sender, soft-delete + xoá sạch
   `content`/`mediaUrl` (thu hồi thật, không chỉ ẩn) — trả về document đã xoá với `deleted: true`.

Build+test toàn reactor: **18/18 module pass, 364 unit test** (tăng từ 334 — 30 test mới/cập nhật
qua 7 service). Test đáng chú ý: `ReelServiceTest` copy nguyên bộ test ranking của `FeedQueryServiceTest`
(cùng age → engagement cao thắng, cùng engagement 0 → mới nhất thắng, engagement khủng thắng cả bài
mới hơn nhiều, phân trang cắt đúng theo thứ tự đã rank chứ không theo thứ tự query gốc).

**Đã verify bằng luồng thật qua Docker + curl, phát hiện và sửa 2 bug schema-migration thật** (giống
đúng kiểu lỗi "chỉ lộ ra khi chạy Docker thật với DB đã có dữ liệu" đã ghi nhận nhiều lần trước đây):
- **Bug 1 — `post-service`**: `ALTER TABLE posts ADD COLUMN pinned boolean NOT NULL` fail với
  `column "pinned" of relation "posts" contains null values` — `@Builder.Default` chỉ set default ở
  phía Java, không sinh ra `DEFAULT` ở DDL, nên Postgres không biết backfill giá trị nào cho các dòng
  đã tồn tại. Sửa: thêm `@Column(columnDefinition = "boolean default false")` vào field `pinned`.
- **Bug 2 — `group-service`**: promote thành viên lên `MODERATOR` fail với
  `violates check constraint "group_members_role_check"` — Hibernate 6 tự sinh CHECK constraint cho
  cột `@Enumerated(STRING)` **tại thời điểm tạo bảng lần đầu** (chỉ biết `ADMIN`/`MEMBER` lúc đó);
  `ddl-auto: update` không tự sửa lại CHECK constraint khi enum Java có thêm giá trị mới. Đây là giới
  hạn đã biết của Hibernate `update` mode (không migrate constraint), không phải lỗi code — đã
  `ALTER TABLE ... DROP/ADD CONSTRAINT` thủ công trên DB dev đang chạy để unblock; một deploy từ volume
  sạch (`docker compose down -v`) sẽ tự sinh đúng CHECK constraint với cả 3 giá trị ngay từ đầu.

Sau khi sửa cả 2, verify lại toàn bộ và pass thật với dữ liệu Docker + curl:
1. Search "Zephyr" trả đúng user vừa đăng ký; query rỗng → 400.
2. Upload `.exe` giả (content-type `application/x-msdownload`) → 400; upload PNG hợp lệ → 200; upload
   ảnh 11MB cho purpose `avatar` → 400 "AVATAR image must be 10MB or smaller".
3. Tạo bài cũ rồi bài mới, ghim bài cũ → `GET /api/posts/author/{id}` trả bài cũ (đã ghim) lên đầu.
4. `reels-service`'s `/api/reels/feed` chạy sạch qua code ranking mới (không lỗi runtime), rỗng vì
   chưa có reel nào — logic ranking đã cover đầy đủ bằng unit test riêng.
5. Group role: chủ group promote thành viên lên MODERATOR → 200; MODERATOR đó duyệt được thành viên
   chờ khác → 200; MODERATOR đó gọi xoá thành viên → **403** (đúng, ADMIN-only).
6. 2 user tạo hồ sơ dating tương thích, swipe LIKE lẫn nhau → match được tạo; `DELETE
   /api/dating/matches/{id}` → 200, `GET /api/dating/matches` sau đó rỗng.
7. Seed 1 message thẳng vào MongoDB, người không phải sender gọi xoá → 403; sender gọi xoá → 200,
   `content`/`mediaUrl` đều `null`, `deleted: true` — xác nhận bằng cách đọc lại thẳng từ MongoDB.
8. `api-gateway`: route `/ws/**`/`/ws-notifications/**` vẫn hoạt động bình thường sau khi thêm
   rate-limit filter (không kiểm thử riêng việc chạm ngưỡng rate-limit).

## ✅ Gộp Swagger UI qua gateway

Trước đây mỗi service có Swagger UI riêng trên port của nó — phải nhớ 15 port khác nhau. Giờ có
**1 điểm truy cập chung** tại `http://localhost:8080/swagger-ui.html`, dùng đúng tính năng aggregation
có sẵn của springdoc (`springdoc.swagger-ui.urls`), không cần viết code custom:

- **`api-gateway`** thêm `springdoc-openapi-starter-webflux-ui` (bản reactive — api-gateway chạy
  Spring Cloud Gateway/WebFlux, không phải MVC nên không dùng chung artifact `-webmvc-ui` mà 15
  service kia đang dùng).
- **15 route mới** dạng `/docs/{service}/v3/api-docs` → `lb://{SERVICE}` + `RewritePath` về `/v3/api-docs`
  thật của từng service — gateway đóng vai trò proxy thuần, không tự sinh OpenAPI doc nào.
- **`springdoc.swagger-ui.urls`** liệt kê cả 15 route trên → UI hiện dropdown chọn service (giống hệt
  cách nhiều dự án dùng Spring Cloud Gateway + springdoc để aggregate docs — không phải pattern tự nghĩ).
- **`JwtAuthenticationFilter`**: thêm `/swagger-ui.html`, `/swagger-ui/**`, `/webjars/**`,
  `/v3/api-docs/**`, `/docs/**` vào danh sách public — nếu không, mở Swagger UI sẽ bị chặn đòi JWT.

**Lưu ý đã biết** (ghi lại để không quên khi thêm service mới, tránh lặp lại đúng kiểu lỗi
"danh sách bị quên" đã gặp ở CI/Prometheus trước đây — khác là ở đây Prometheus tự phát hiện qua Eureka
được, còn route proxy docs thì không thể tự động hoá tương tự vì springdoc's `swagger-ui.urls` cần
biết trước danh sách tên hiển thị, nên đây là 1 trong số ít chỗ còn phải cập nhật tay khi thêm service):
thêm service mới phải nhớ thêm cả route `/docs/{service}/v3/api-docs` lẫn 1 dòng trong
`springdoc.swagger-ui.urls`.

**Đã verify bằng dữ liệu thật qua Docker + curl**:
1. `GET /swagger-ui.html` → redirect 302 → theo redirect ra đúng trang HTML Swagger UI (200).
2. `GET /docs/post-service/v3/api-docs`, `/docs/dating-service/...`, `/docs/group-service/...`,
   `/docs/auth-service/...`, `/docs/moderation-service/...` — cả 5 đều trả đúng OpenAPI JSON thật
   được proxy từ service tương ứng (không phải 401/404); `group-service`'s doc còn thấy đúng cả
   endpoint `PUT /api/groups/{id}/members/{userId}/role` mới thêm ở đợt "nâng cấp nhỏ" trước đó.
3. `GET /v3/api-docs/swagger-config` → JSON liệt kê đúng đủ 15 service cho dropdown.
4. **Regression check**: `GET /api/posts/1` không kèm token → vẫn **401** như trước — xác nhận danh
   sách public-path mới không vô tình mở toang route API thật nào.

Sự cố ngoài lề lúc verify: Docker Desktop bị treo engine (`docker ps` timeout hoàn toàn, không phải
lỗi code) — người dùng tự khởi động lại Docker Desktop; sau khi lên lại, `comment-service` bị kill
(exit 137, đúng lúc Docker Desktop restart) nên tạm thời rớt khỏi Eureka — `docker start` lại là xong,
không phải bug liên quan đến thay đổi lần này.

Build+test toàn reactor trước khi redeploy: **18/18 module pass** (không đổi số test vì đây chỉ là
thay đổi cấu hình/routing, không sửa logic nghiệp vụ).

## ✅ Cho phép hạ tracing sampling probability qua env var (production-readiness)

Trước đó `management.tracing.sampling.probability: 1.0` bị hardcode ở cả 16 service (15 business +
`api-gateway`) — hợp lý cho demo (thấy mọi trace) nhưng không nên dùng nguyên vậy cho production
(overhead + Zipkin phình to). Đổi thành `probability: ${TRACING_SAMPLING_PROBABILITY:1.0}` ở cả 16
file — mặc định vẫn 100% (không đổi hành vi demo/dev hiện tại), nhưng giờ production deploy có thể hạ
xuống chỉ bằng 1 biến môi trường, không cần rebuild image hay đổi code.

**Đã verify bằng dữ liệu Zipkin thật — không chỉ "container khởi động được"**, và phát hiện 1 điều quan
trọng về cách sampling hoạt động trong tracing phân tán (không phải bug, là hành vi đúng cần hiểu rõ):

1. Baseline: gọi `post-service` qua gateway (mặc định 1.0) → trace xuất hiện đúng trong Zipkin.
2. **Test đầu tiên qua gateway bị nhiễu**: tạm set `TRACING_SAMPLING_PROBABILITY=0.0` riêng cho
   `post-service`, gọi 10 request **qua gateway** → vẫn thấy span mới (724→754)! Không phải bug —
   đây là **head-based sampling**: quyết định "có sample hay không" được chốt ở nơi trace *bắt đầu*
   (ở đây là `api-gateway`, vẫn 1.0) rồi truyền xuống qua trace context; service phía sau chỉ *kế thừa*
   quyết định đó chứ không tự quyết lại — đúng theo cách Brave/Micrometer Tracing hoạt động, và đúng ra
   phải vậy (nếu mỗi service tự quyết riêng thì 1 trace có thể bị đứt quãng, thiếu span giữa chừng).
3. **Test đúng cách**: gọi thẳng `post-service` ở port 8084 (bỏ qua gateway → post-service tự là gốc
   trace) với `probability=0.0` → gọi 10 request, **0 span mới** (754→754) — xác nhận biến môi trường
   thật sự có tác dụng khi service là nơi khởi tạo trace.
4. Gỡ override, redeploy lại `post-service` với mặc định → gọi trực tiếp lại → **5 request → 5 span
   mới** (759→764) — tracing hoạt động lại bình thường, xác nhận revert sạch, không để lại cấu hình
   tạm trong `docker-compose.yml` (`git diff` sạch sau khi xong).

Build+test toàn reactor trước khi redeploy: **18/18 module pass** (chỉ đổi giá trị cấu hình, không sửa
logic nghiệp vụ nên số test không đổi).

Cách dùng khi deploy production: set `TRACING_SAMPLING_PROBABILITY=0.1` (hoặc giá trị mong muốn) trong
`x-app-env` của `docker-compose.yml`, hoặc riêng từng service nếu muốn mức sampling khác nhau —
không cần rebuild image.

## ✅ Share/repost, tag người dùng, custom audience privacy, tìm kiếm tổng hợp

4 tính năng "phải có" trước khi bắt tay Frontend Web (theo audit so sánh với Facebook thật — xem đoạn
audit trước đó trong lịch sử làm việc; Events cố ý bỏ qua theo yêu cầu):

1. **Share/repost** (`post-service`): `POST /api/posts/{id}/share` — tạo 1 `Post` mới với
   `sharedPostId` trỏ về bài gốc + `content` là lời bình khi chia sẻ (như hộp thoại share của
   Facebook). Bài gốc tăng `shareCount`. Không cho chia sẻ bài mình không có quyền xem (dùng lại đúng
   logic `canView` bên dưới).
2. **Tag người dùng vào bài viết** (`post-service` + `notification-service`): `Post` có thêm
   `taggedUserIds`. Tạo bài mới → publish 1 event `PostTaggedEvent` (topic `post-tagged-events`) cho
   mỗi người được tag. Sửa bài → chỉ publish event cho người **mới** được tag thêm (diff với danh sách
   cũ), tránh spam thông báo lại cho người đã tag từ trước. `notification-service` thêm
   `NotificationType.TAG` + listener mới.
3. **Custom audience privacy** (`post-service`): enum `Privacy` thêm giá trị `CUSTOM` (đã có sẵn
   `FRIENDS` từ trước, chỉ thiếu `CUSTOM`). `Post` thêm `customAudienceUserIds` — danh sách user được
   xem khi `privacy=CUSTOM`. **Đây là lần đầu tiên post-service thực sự enforce privacy lúc đọc** —
   trước đó `privacy` chỉ là field trang trí, `getPost`/`getPostsByAuthor`/... không hề kiểm tra gì.
   Thêm `canView(post, viewerId)`: PUBLIC luôn thấy, PRIVATE chỉ tác giả, FRIENDS gọi Feign sang
   `user-service` lấy `friend-ids` để kiểm tra, CUSTOM kiểm tra trong danh sách allow-list. Áp dụng ở
   `getPostsByAuthor`/`getPostsByGroup`/`getPostsByPage` (lọc kết quả sau khi query) — **cố ý không**
   áp dụng ở `GET /api/posts/{id}` và `/batch` vì 2 endpoint này còn được gọi nội bộ qua Feign bởi
   comment-service/feed-service (không mang theo viewer identity vì Feign không tự động forward
   header `X-User-Id` — chưa có `RequestInterceptor` cho việc này), enforce ở đó sẽ chặn nhầm mọi
   Feign call. Đây là giới hạn đã biết, ghi rõ trong code.
4. **Tìm kiếm tổng hợp** (`search-service`, service thứ 19, port 8096): service mới **không có DB
   riêng**, chỉ tổng hợp qua 4 Feign client (user/group/fanpage/post-service) — mỗi client có
   circuit breaker + fallback riêng (degrade về rỗng nếu 1 service down, không sập cả search).
   `post-service` phải thêm mới `GET /api/posts/search` (trước đó chưa có tìm nội dung bài viết, cố ý
   giới hạn chỉ tìm bài PUBLIC — an toàn cho mọi caller kể cả Feign không có viewer identity).
   `GET /api/search?q=&limit=` trả về gộp cả 4 loại kết quả.

**Đã verify bằng dữ liệu thật qua Docker + curl**, và trong quá trình verify gặp liên tiếp nhiều sự
cố hạ tầng thật (không phải lỗi logic nghiệp vụ) — ghi lại đầy đủ vì đây là bài học đáng giá:

- **Sự cố Docker Desktop**: engine bị treo hoàn toàn giữa chừng (không phải do session này gây ra) —
  `docker ps` timeout ở mọi shell (Git Bash lẫn PowerShell), trong khi GUI vẫn hiển thị "Engine
  running" (dữ liệu cache). Chẩn đoán qua `wsl --list --verbose`: distro `docker-desktop-data` (chứa
  toàn bộ volume) biến mất khỏi danh sách dù `docker-desktop` (engine) vẫn "Running". File dữ liệu
  thật (`docker_data.vhdx`, ~40GB) vẫn còn nguyên trên đĩa — **không mất dữ liệu**, chỉ là WSL distro
  chưa đăng ký lại. Khắc phục: người dùng Quit hẳn Docker Desktop từ system tray (không chỉ đóng cửa
  sổ) rồi mở lại — engine tự đăng ký lại đúng, mọi container/volume/image phục hồi nguyên vẹn.
- **Bug thật — stale Docker image sau sự cố**: sau khi Docker phục hồi, `post-service` và
  `notification-service` chạy nhầm **image cũ** (thiếu hẳn code mới — API response thiếu
  `taggedUserIds`/`shareCount`/..., `notification-service` thiếu consumer cho topic
  `post-tagged-events`) dù `docker compose up -d --build` trước đó báo thành công. Xác nhận bằng cách
  trích bytecode `Post.class` từ trong container so với JAR thật trên host (host có đủ field, container
  thì không) — kết luận: build bị gián đoạn giữa chừng bởi sự cố Docker, một số service build xong
  trước khi crash, một số thì không, nhưng lệnh `docker compose up -d --build` vẫn trả về exit 0. Sửa:
  `docker compose build` (không cache) lại toàn bộ + `docker compose up -d` — Docker chỉ recreate
  đúng những container có image thay đổi thật (post-service không bị recreate lại vì đã tự sửa trước
  đó bằng `--no-cache` riêng).
- **Bug thật — CHECK constraint cũ trên cột `privacy`**: y hệt lỗi đã gặp với `MemberRole` của
  group-service — Hibernate tự sinh CHECK constraint lúc tạo bảng lần đầu (chỉ biết
  PUBLIC/FRIENDS/PRIVATE), `ddl-auto: update` không tự sửa lại khi enum có thêm `CUSTOM`. Tạo post
  CUSTOM lần đầu fail với `violates check constraint "posts_privacy_check"`. Sửa thủ công trên DB dev
  (`DROP`/`ADD CONSTRAINT` với đủ 4 giá trị) — một volume sạch sẽ tự sinh đúng ngay từ đầu.
- **Sự cố phụ — Postgres hết connection**: `too many clients already` khi cố mở 1 kết nối `psql` thủ
  công, do ~13 service Postgres-backed × HikariCP pool mặc định 10 connection cộng dồn gần chạm
  `max_connections` mặc định 100 của Postgres — càng dễ xảy ra sau 1 đợt restart hàng loạt (mọi pool
  cùng reconnect). Sửa tạm bằng cách restart container `postgres` (giải phóng hết connection, dữ liệu
  an toàn trong volume). **Chưa sửa tận gốc** — nên hạ `spring.datasource.hikari.maximum-pool-size`
  ở từng service hoặc tăng `max_connections` nếu tiếp tục mở rộng số service dùng Postgres.

Sau khi sửa hết, verify lại toàn bộ và pass thật:
1. A tạo bài tag B → response có đúng `taggedUserIds`; B có thông báo `type: TAG` với đúng `targetId`.
2. A tạo bài, B share kèm lời bình → bài share có `sharedPostId` trỏ đúng bài gốc; `GET` lại bài gốc
   thấy `shareCount: 1`.
3. A tạo bài `privacy: CUSTOM, customAudienceUserIds: [B]` → B xem `GET /api/posts/author/A` thấy bài
   này, C (không có trong audience) xem cùng endpoint **không** thấy.
4. `GET /api/search?q=Feature` → trả đúng cả 3 user có tên chứa "Feature"; `?q=share` → trả đúng bài
   viết có nội dung chứa "share" — xác nhận search-service thật sự tổng hợp từ các service thật, không
   phải rơi vào fallback rỗng.

Build+test toàn reactor trước khi redeploy: **19/19 module pass, 389 unit test** (tăng từ 364).

## Việc cần làm tiếp theo

Backlog đề xuất trước đó đã xong hết. Từ audit so sánh với Facebook thật, còn các mục **cố ý chưa làm**
(không nằm trong yêu cầu lần này): Events, Save/Bookmark bài viết, activity log/2FA, và các mục nhỏ
theo từng service đã ghi ở cột "Còn thiếu" trong bảng trạng thái phía trên. Việc tiếp theo nên xuất
phát từ yêu cầu mới của người dùng.

## ✅ Đã test WebSocket thật (chat + notification)

Xem "Lỗi thứ 8" ở trên — cả 2 luồng real-time đã verify PASS qua gateway thật với JWT thật, không phải qua mock.

## ✅ Giai đoạn 2a — Flutter Web Frontend (nền tảng + trải nghiệm Facebook cốt lõi)

Bắt đầu giai đoạn 2 (giao diện) theo yêu cầu: Flutter, bố cục dọc kiểu Facebook (để đồng bộ mobile sau
này), gắn thẳng vào backend thật (không mock). Thư mục mới: `frontend/` (ngang hàng `services/`).

**Đã triển khai (Phase 2a):**
- **Kiến trúc**: `flutter_riverpod` (state), `go_router` (URL routing + auth guard qua `redirect`),
  `dio` (HTTP client, có interceptor tự gắn `Authorization` header + tự refresh token khi gặp 401 rồi
  retry 1 lần), `flutter_secure_storage` (lưu token). Model viết tay (`fromJson`/`toJson` thủ công,
  không dùng `freezed`/`json_serializable`/`build_runner`) — quyết định vì máy dev không có Flutter SDK
  cài sẵn nên không thể debug lỗi codegen tương tác được.
- **Layout**: `AppShell` dùng chung — top bar (logo, tìm kiếm tổng hợp qua `search-service`, chuông lời
  mời kết bạn, menu tài khoản), sidebar trái (điều hướng, các mục Group/Fanpage/Story/Reels/Dating/
  Chat/Notification hiện "Sắp có" — giữ đúng bố cục Facebook thật dù chưa làm), cột giữa 1 cột dọc cho
  nội dung (feed/profile/post detail), sidebar phải để placeholder. Responsive: ẩn 2 sidebar khi màn
  hẹp (<900px).
- **Chức năng đầy đủ theo scope đã chốt**: đăng ký/đăng nhập/đăng xuất, feed cá nhân (`GET
  /api/feed/me`, infinite scroll), tạo/sửa/xoá/ghim bài viết, upload media qua `media-service`, chọn
  quyền riêng tư PUBLIC/FRIENDS/CUSTOM/PRIVATE (CUSTOM mở picker chọn bạn cụ thể), gắn thẻ bạn bè, share/
  repost (kèm lời bình + đổi privacy), comment có reply, reaction 6 loại (like/love/haha/wow/sad/angry)
  cho cả post và comment, xem/sửa profile + đổi avatar/cover, gửi/chấp nhận/từ chối lời mời kết bạn, huỷ
  kết bạn, chặn/bỏ chặn, danh sách bạn bè, tìm kiếm tổng hợp ở top bar (user + post, group/page tạm ẩn
  vì chưa có UI tương ứng).
- **Đóng gói**: `frontend/Dockerfile` (multi-stage `cirruslabs/flutter:stable` build → `nginx:alpine`
  serve, có `try_files` fallback cho SPA routing), thêm service `frontend` (port 3001) vào
  `docker-compose.yml`.

**Verify qua Docker thật** (máy dev không có Flutter SDK cài sẵn nên đây là cách verify chính, không
chỉ đọc code):
- `docker compose build frontend` ban đầu fail 2 lỗi biên dịch Dart thật (không phải lỗi hạ tầng):
  1. Truyền thẳng tear-off `X.fromJson` (kiểu `T Function(Map<String, dynamic>)`) vào tham số kiểu `T
     Function(dynamic)` của `ApiResponse.fromJson`/`PageResponse.fromJson` — dart2js từ chối vì kiểu
     tham số không khớp tĩnh (dù có vẻ hợp lệ khi đọc code). Sửa bằng cách bọc qua hàm top-level tường
     minh kiểu `dynamic` (ví dụ `Post _postFromJson(dynamic json) => Post.fromJson(json as
     Map<String, dynamic>)`), áp dụng cho toàn bộ 6 repository (`post`, `comment`, `reaction`,
     `profile`, `auth`, và các chỗ dùng `PageResponse` lồng bên trong).
  2. 2 chỗ thiếu import khiến provider không resolve được (`profileRepositoryProvider` định nghĩa nhầm
     ở `user_lookup_provider.dart` thay vì `profile_repository.dart`; `profile_page.dart` import nhầm
     `post_repository.dart` thay vì `post_provider.dart` nơi `postRepositoryProvider` thực sự khai
     báo) — dọn lại co-location provider/repository cho đúng.
- Sau khi sửa: `flutter build web --release` thành công, container `sma-frontend` chạy, `http://
  localhost:3001` trả về đúng trang (title "Social", `flutter_bootstrap.js`/`main.dart.js` load 200).
- Test cuốn chiếu qua `curl` thẳng vào `api-gateway` thật với đúng chuỗi request mà Flutter app gọi:
  register → `/api/auth/me` → `/api/users/me` → tạo bài (đủ field `customAudienceUserIds`/
  `taggedUserIds`/`sharedPostId`/`shareCount`) → feed (xác nhận đúng shape `PostDto` rút gọn của
  feed-service, thiếu `customAudienceUserIds`/`updatedAt` — model Dart xử lý đúng bằng default) →
  comment → reaction summary → share. Toàn bộ field JSON khớp chính xác với model Dart đã viết.
- Gặp lại đúng lỗi hạ tầng Docker Desktop đã biết từ trước (`docker-desktop-data` rớt khỏi WSL, 500 "API
  version") giữa lúc build — user Quit hẳn từ tray + mở lại, sau đó `feed-service` cần build lại jar
  Maven thủ công (`mvn package -DskipTests`) vì lần chạy `mvn ... test` trước đó không đóng gói jar.

**Chưa làm ở lượt này** (đã ghi rõ trong sidebar "Sắp có", chưa cần hỏi lại): Group, Fanpage, Story,
Reels, Dating, Chat real-time UI, Notification real-time UI (mới có nút chuông cho lời mời kết bạn qua
REST polling thủ công, chưa nối WebSocket), trang kết quả tìm kiếm riêng, trang quản trị Moderation.

**Giới hạn đã biết của lượt verify này**: chưa test bằng trình duyệt thật (môi trường này không có công
cụ điều khiển browser) — mới verify được (1) build production thành công, (2) trang tĩnh serve đúng,
(3) toàn bộ contract API khớp field-by-field với backend thật qua curl. Chưa tận mắt xác nhận
tương tác UI (click, dialog, responsive layout) hoạt động đúng trên trình duyệt.

## ✅ Giai đoạn 2b — Flutter Web Frontend: Group, Fanpage, Story, Reels, Dating, Chat, Notification, Moderation, Search

Hoàn thành toàn bộ phần backlog còn lại của giao diện (liệt kê "chưa làm" ở mục Phase 2a phía trên).
Giờ giao diện phủ đủ chức năng của tất cả 16 business service.

**Đã triển khai:**
- **Group**: danh sách nhóm (`/groups`), trang chi tiết nhóm (`/groups/:id`) — tham gia/rời/duyệt thành
  viên/đổi vai trò/kick, đăng bài trong nhóm (tái dùng `PostComposerDialog` với `groupId`).
- **Fanpage**: danh sách trang (`/pages`), chi tiết trang (`/pages/:id`) — follow/unfollow, thêm/xoá
  admin theo vai trò OWNER/ADMIN/EDITOR, đăng bài trong trang.
- **Story**: dải story 24h ở đầu feed (`stories_strip.dart`), dialog tạo story, story viewer full-screen.
- **Reels**: feed reels dạng cuộn dọc xếp hạng theo engagement (`/reels`), player video, tạo reel mới.
- **Dating**: thiết lập hồ sơ hẹn hò, màn hình vuốt (swipe like/pass) hiển thị điểm tương thích, danh
  sách match.
- **Chat real-time**: danh sách hội thoại (`/chat`), màn hình chat (`/chat/:id`) — kết nối STOMP qua
  WebSocket thật (`/ws`, gói `stomp_dart_client`, xác thực bằng `?token=` trên URL kết nối vì browser
  WebSocket không tự gắn header `Authorization` được), gửi qua `/app/chat.send`, nhận qua
  `/user/queue/messages` (echo cả tin nhắn của chính mình, đúng theo thiết kế backend).
- **Notification real-time**: chuông thông báo ở top bar kết nối STOMP tới `/ws-notifications`, nhận
  qua `/user/queue/notifications`, cộng dồn `unreadCount` ngay khi có sự kiện mới (không cần polling);
  trang danh sách đầy đủ (`/notifications`) + đánh dấu đã đọc từng cái/tất cả.
- **Moderation**: nút báo cáo nội dung (dialog chọn loại đối tượng + lý do), trang quản trị
  (`/admin/reports`, chỉ hiện trong sidebar và cho phép truy cập khi tài khoản có role `ADMIN` — gate
  cả ở `app_router.dart` lẫn ẩn nav item) để duyệt hàng đợi báo cáo, dismiss hoặc remove-content.
- **Search page**: trang kết quả tìm kiếm đầy đủ (`/search?q=`) hiển thị cả 4 loại (user/group/page/
  post), khác với dropdown ở top bar chỉ hiện gợi ý nhanh.
- `AppShell` sidebar: toàn bộ mục trước đây "Sắp có" nay trỏ route thật; mục Moderation chỉ hiện với
  admin.

**Verify qua Docker thật**: `docker compose build frontend` lần đầu ra đúng 3 lỗi biên dịch Dart thật
(1 chỗ thiếu `import '../../core/models/enums.dart';` ở `dating_provider.dart` khiến `SwipeAction`
không resolve được dù type này được re-export gián tiếp qua `dating.dart`; 2 chỗ tương tự ở
`notifications_page.dart`/`notification_bell_button.dart` khiến extension getter `.icon` trên
`NotificationType` không nằm trong scope) — sửa xong build sạch. Sau đó test cuốn chiếu qua curl thẳng
vào `api-gateway` thật: tạo group, tạo fanpage, tạo story, tạo reel, tạo/lưu hồ sơ dating, list chat
conversations, notifications unread-count, tạo moderation report, tìm kiếm tổng hợp (trả về đúng cả
group mới tạo) — toàn bộ field JSON khớp chính xác với model Dart.

**Sự cố hạ tầng gặp phải khi build lượt này** (không phải lỗi code, ghi lại để lần sau khỏi mất thời
gian chẩn đoán lại):
- Docker Desktop crash lặp lại nhiều lần trong đêm (500 "API version", `docker-desktop-data` rớt khỏi
  WSL) — cùng loại lỗi đã ghi nhận trước đó, khắc phục bằng Quit hẳn từ tray + mở lại nhiều lần.
- Người dùng thử chuyển vị trí lưu dữ liệu Docker (disk image location) từ ổ C sang ổ D qua Settings —
  **phát hiện bug thật của Docker Desktop**: robocopy copy dữ liệu (~46GB) sang D thành công (verify
  bằng cách so kích thước file 2 bên khớp tuyệt đối), nhưng Docker Desktop hiểu sai exit code 1 của
  robocopy (nghĩa là "copy thành công" theo tài liệu Microsoft) thành lỗi, nên huỷ áp dụng setting —
  lặp lại y hệt ở lần thử lại. Không có file cấu hình host-side để tự sửa tay (giá trị này lưu trong
  filesystem ext4 của VM WSL, không phải JSON trên Windows) nên đành bỏ qua, xoá bản D thừa, giữ dữ
  liệu ở C (còn 33GB trống, đủ dùng).
  - Thử `wsl --import-in-place` để đăng ký lại `docker-desktop-data` — thất bại với
    `WSL_E_NOT_A_LINUX_DISTRO`, vì bản Docker Desktop này không dùng distro dữ liệu riêng nữa (chỉ có
    1 distro `docker-desktop`, vhdx được mount như đĩa phụ) — việc `docker-desktop-data` không xuất
    hiện trong `wsl --list` là **bình thường** ở bản này, không phải dấu hiệu hỏng như đã tưởng nhầm
    trước đó trong phiên.
- **Nguyên nhân sâu xa của hàng loạt lần build frontend bị crash giữa chừng** (`rpc error: code =
  Unavailable`, `http2: ... file has already been closed`, cả ở BuildKit lẫn legacy builder — loại trừ
  được nguyên nhân do BuildKit): RAM cấp cho WSL2 không đủ khi vừa chạy 24+ container backend vừa chạy
  `dart2js` (rất tốn RAM lúc biên dịch bản `--release`). Máy host chỉ 16GB RAM, WSL2 dùng mặc định
  (không có `.wslconfig`) nên bị giới hạn thấp. Khắc phục: tạo `%USERPROFILE%\.wslconfig` với
  `memory=12GB` + `wsl --shutdown` + mở lại Docker Desktop, đồng thời tạm tắt observability stack
  (Grafana/Prometheus/Loki/Promtail/Zipkin — không cần thiết lúc build) để tăng vùng đệm. Sau đó build
  chạy trọn vẹn dù chậm hơn (267s thay vì ~90s bình thường, do RAM vẫn eo hẹp chứ không thiếu hẳn nữa).

**Giới hạn đã biết**: vẫn chưa test bằng trình duyệt thật (không có công cụ điều khiển browser trong
môi trường này) — WebSocket chat/notification đã verify đúng theo tài liệu contract backend (đọc kỹ
source `ChatStompController`/`WebSocketConfig` của cả 2 service) nhưng **chưa test round-trip thật qua
STOMP** (chỉ test được REST endpoints qua curl, không test được phần gửi/nhận qua `/app/chat.send` và
`/user/queue/messages` bằng công cụ dòng lệnh sẵn có). Nên tự tay thử gửi tin nhắn giữa 2 tài khoản
trên trình duyệt thật để xác nhận trước khi coi Chat/Notification real-time là hoàn tất 100%.
