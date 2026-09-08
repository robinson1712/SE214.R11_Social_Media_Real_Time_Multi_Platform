# Social Media Backend (Facebook-like) — Java Spring Boot Microservices

Backend microservice cho một mạng xã hội kiểu Facebook: post, avatar, story, reels, comment,
reaction, block, group, fanpage, dating, nhắn tin real-time. Kiến trúc hướng đối tượng, phục vụ
chung cho cả web và mobile qua REST API + WebSocket.

## Kiến trúc

```
Client (Web/Mobile)
        │
        ▼
  API Gateway (8080)  ── xác thực JWT, forward X-User-Id/X-User-Roles xuống downstream
        │
        ▼
  Eureka Service Discovery (8761)
        │
        ├── auth-service (8081)          Postgres: auth_db
        ├── user-service (8082)          Postgres: user_db
        ├── media-service (8083)         Postgres: media_db + MinIO
        ├── post-service (8084)          Postgres: post_db
        ├── comment-service (8085)       Postgres: comment_db
        ├── reaction-service (8086)      Postgres: reaction_db
        ├── story-service (8087)         MongoDB: story_db
        ├── reels-service (8088)         MongoDB: reels_db
        ├── group-service (8089)         Postgres: group_db
        ├── fanpage-service (8090)       Postgres: fanpage_db
        ├── dating-service (8091)        Postgres: dating_db
        ├── chat-service (8092)          MongoDB: chat_db + Redis (presence) — WebSocket /ws
        ├── notification-service (8093)  MongoDB: notification_db — WebSocket /ws
        ├── feed-service (8094)          Redis (sorted set fanout)
        ├── moderation-service (8095)    Postgres: moderation_db — user report + admin review queue
        └── search-service (8096)        Không có DB riêng — tổng hợp qua Feign (user/group/fanpage/post)

  frontend (3001) — Flutter Web (nginx serve), bố cục dọc kiểu Facebook, gọi thẳng API Gateway

  Config Server (8888) — cấp config tập trung (native, đọc từ ./config-repo)
  Kafka — giao tiếp bất đồng bộ giữa các service (post/comment/reaction/group/page/match/message events)

  Observability: Zipkin (9411, distributed tracing) — Loki+Promtail+Grafana (3000, log tập trung)
                — Prometheus (9090, metrics, tự phát hiện service qua Eureka) + dashboard Grafana có sẵn
```

Chi tiết đầy đủ: xem `docs/plan.md` hoặc lịch sử trò chuyện đã tạo ra hệ thống này.

## Công nghệ

- Java 17, Spring Boot 3.3.4, Spring Cloud 2023.0.3
- Spring Web MVC (business services) + Spring Cloud Gateway/WebFlux (api-gateway)
- Spring Data JPA (Postgres) / Spring Data MongoDB / Spring Data Redis
- Spring Kafka, Spring Cloud OpenFeign, Spring WebSocket (STOMP)
- JWT (jjwt) tự issue ở `auth-service`, xác thực tập trung ở `api-gateway`
- MinIO (S3-compatible) cho lưu trữ media
- Docker Compose cho toàn bộ hạ tầng + service

## Chạy toàn bộ hệ thống

### 1. Build tất cả module (bắt buộc trước khi build Docker image, vì Dockerfile chỉ copy jar đã build sẵn)

```bash
mvn -q -B clean package -DskipTests
```

### 2. Chạy bằng Docker Compose

```bash
docker compose up -d --build
```

Chờ khoảng 1-2 phút để Postgres/Mongo/Kafka/MinIO/Eureka khởi động xong trước khi các service
đăng ký thành công vào Eureka. Kiểm tra: http://localhost:8761 (Eureka dashboard).

Lệnh trên cũng build và chạy `frontend` (Flutter Web) cùng lúc — mở http://localhost:3001 sau khi
backend đã sẵn sàng (`api-gateway` cần đăng ký xong ở Eureka trước, nếu không các request đầu tiên từ
giao diện sẽ gặp `503 Service Unavailable`).

### 3. Test nhanh luồng end-to-end

```bash
# Đăng ký + đăng nhập
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"email":"a@test.com","password":"123456","fullName":"Nguyen Van A"}'

# Lấy accessToken từ response trên, dùng cho các request tiếp theo
TOKEN=<accessToken>

# Tạo post
curl -X POST http://localhost:8080/api/posts -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"content":"Hello world","privacy":"PUBLIC"}'

# Xem feed
curl http://localhost:8080/api/feed/me -H "Authorization: Bearer $TOKEN"

# Chia sẻ lại 1 bài viết
curl -X POST http://localhost:8080/api/posts/<postId>/share -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"content":"Xem cái này nè!","privacy":"PUBLIC"}'

# Tìm kiếm tổng hợp (user + group + fanpage + post)
curl "http://localhost:8080/api/search?q=Nguyen&limit=5" -H "Authorization: Bearer $TOKEN"
```

WebSocket (STOMP): chat tại `ws://localhost:8080/ws?token=$TOKEN` (SockJS) hoặc `ws://localhost:8080/ws/websocket?token=$TOKEN` (raw WebSocket, bỏ qua SockJS framing); notification tại `ws://localhost:8080/ws-notifications?token=$TOKEN` (SockJS) hoặc `.../ws-notifications/websocket?token=$TOKEN` (raw) — hai đường dẫn khác nhau vì gateway chỉ route được một service cho mỗi path.

Swagger UI: **1 điểm truy cập chung** qua gateway tại `http://localhost:8080/swagger-ui.html` — dropdown góc trên bên phải để chuyển qua lại giữa 15 business service (không cần nhớ port riêng từng service nữa). Mỗi service vẫn expose Swagger UI riêng trên port của nó nếu cần test trực tiếp không qua gateway, vd `http://localhost:8084/swagger-ui/index.html` (post-service).

Observability: Zipkin UI `http://localhost:9411` (tìm trace theo service/traceId — mọi request qua gateway đều có 100% được trace, kể cả chặng Kafka bất đồng bộ); Grafana `http://localhost:3000` (anonymous admin, không cần đăng nhập) → Explore → datasource Loki để xem log tập trung mọi container, filter theo `{service="auth-service"}` — mỗi dòng log đều có sẵn `[traceId-spanId]` để tra ngược đúng trace trong Zipkin; dashboard **Service Overview** (đã provision sẵn, mở ngay không cần tự tạo) hiển thị request rate/p99 latency/error rate/JVM heap/CPU theo từng service — nguồn Prometheus `http://localhost:9090`, tự phát hiện service cần scrape qua Eureka thay vì liệt kê tay.

Tracing sampling mặc định 100% (`TRACING_SAMPLING_PROBABILITY`, mặc định `1.0` nếu không set) — hợp lý
cho demo/dev vì mọi request đều thấy được trace ngay. Deploy production nên hạ xuống (vd `0.1` = 10%)
để giảm overhead + dung lượng lưu trữ Zipkin: set biến môi trường này trong `x-app-env` của
`docker-compose.yml` (hoặc theo từng service riêng nếu muốn khác nhau), không cần rebuild image.

### Chạy từng service riêng lẻ khi phát triển (không qua Docker)

Cần Postgres/MongoDB/Redis/Kafka/MinIO chạy sẵn (có thể `docker compose up postgres mongodb redis kafka minio -d`
rồi chạy service bằng `mvn spring-boot:run` trong từng thư mục `services/<name>`), theo thứ tự:
`eureka-server` → `config-server` → các business service → `api-gateway`.

## Đã hoàn thiện thêm sau lượt scaffold đầu

- **Feed ranking theo engagement** (`feed-service`): đọc feed không còn thuần sắp theo thời gian.
  `FeedQueryService` lấy một pool ứng viên có giới hạn từ Redis, chấm điểm mỗi post bằng công thức
  kiểu Hacker News (`recencyScore × (1 + log(1 + reaction + 2×comment))`), sắp theo điểm rồi mới
  phân trang trong bộ nhớ. Ghi (fanout) vẫn chỉ ghi timestamp thuần — ranking chỉ tính lúc đọc, nên
  đổi công thức không cần migrate dữ liệu.
- **Thuật toán matching cho dating** (`dating-service`): `DatingProfile` có thêm `gender`/`birthDate`
  riêng (khác với `genderPreference` là *đang tìm ai*). `GET /candidates` lọc tương thích giới tính
  hai chiều rồi chấm điểm 0-100 (tối đa 60 điểm cho độ tuổi phù hợp hai chiều, 40 điểm cho sở thích
  chung theo Jaccard similarity), trả về kèm điểm số qua `CandidateResponse` để minh bạch thuật toán.
- **Group visibility đúng theo quyền thành viên** (`group-service`): `GET /api/groups` giờ trả cả
  group PRIVATE mà caller đã là thành viên APPROVED, không chỉ group PUBLIC.
- **Resilience4j circuit breaker** trên mọi Feign client (`comment-service`, `story-service`,
  `feed-service`): mỗi `@FeignClient` có `fallbackFactory` riêng, phân biệt lỗi nghiệp vụ (404 —
  vẫn báo lỗi đúng) với lỗi hạ tầng (service sập/timeout — trả `ServiceUnavailableException` 503
  hoặc giá trị rỗng tuỳ ngữ cảnh). Xem trạng thái circuit qua `actuator/circuitbreakers`.
- **Observability**: distributed tracing (Zipkin) + centralized logging (Loki/Promtail/Grafana) —
  xem mục Observability ở trên.
- **CI/CD**: GitHub Actions (`.github/workflows/ci-cd.yml`) build+test toàn reactor, chạy
  Testcontainers integration test, build Docker image cho mọi service, push GHCR khi merge `main`.
- **Content moderation** (`moderation-service`, port 8095): profanity filter tự động (tiếng Anh +
  tiếng Việt, giữ nguyên dấu thanh) chặn `post-service`/`comment-service`/`reels-service`/
  `story-service`/`group-service`/`fanpage-service` lúc tạo nội dung — đủ cả 6 loại nội dung trong hệ
  thống; user report nội dung vi phạm qua `POST /api/moderation/reports`; admin (role `ADMIN`) duyệt
  hàng đợi qua `GET /api/moderation/reports`, xử lý `PUT /api/moderation/reports/{id}/resolve` với
  `REMOVE_CONTENT` sẽ publish sự kiện Kafka xoá nội dung xuyên service (`group-service`/
  `fanpage-service` xoá cascade cả bảng thành viên/follower/admin liên quan). Set biến môi trường
  `ADMIN_EMAILS=admin@social.app,...` (comma-separated) trước khi build `auth-service` để các email
  đó tự động có role `ADMIN` lúc đăng ký.
- **Share/repost, tag người dùng, custom audience privacy** (`post-service`): `POST /api/posts/{id}/share`
  chia sẻ lại 1 bài (kèm lời bình, tăng `shareCount` bài gốc); tạo/sửa bài có thể kèm `taggedUserIds`
  (thông báo `type: TAG` cho người được tag); `privacy: CUSTOM` + `customAudienceUserIds` giới hạn
  người xem theo danh sách cụ thể. **Post-service giờ mới thực sự enforce privacy lúc đọc**
  (`getPostsByAuthor`/`ByGroup`/`ByPage`) — `FRIENDS` gọi Feign sang `user-service` kiểm tra bạn bè.
- **Tìm kiếm tổng hợp** (`search-service`, port 8096): `GET /api/search?q=&limit=` gộp kết quả từ
  user/group/fanpage/post-service qua Feign (mỗi client có circuit breaker + fallback rỗng riêng —
  1 service down không sập cả search). Không có database riêng.
- **Frontend Flutter Web** (`frontend/`, port 3001): giao diện bố cục dọc kiểu Facebook, gắn thẳng vào
  các API thật ở trên — đăng nhập, feed, đăng bài (kèm share/tag/custom-audience privacy), comment/
  reply, reaction, profile + kết bạn, tìm kiếm tổng hợp (kèm trang kết quả riêng), Group, Fanpage,
  Story, Reels, Dating, Chat real-time (STOMP/WebSocket), Notification real-time (STOMP/WebSocket),
  trang quản trị Moderation (gate theo role `ADMIN`). Đã phủ đủ chức năng của toàn bộ 16 business
  service. Chi tiết đầy đủ + giới hạn đã biết (chưa test bằng trình duyệt thật, chưa test round-trip
  WebSocket thật): xem mục "Giai đoạn 2a"/"Giai đoạn 2b" trong `TODO.md`.

## Giới hạn còn lại

Một vài đơn giản hoá có chủ đích còn lại (ghi rõ trong code bằng comment): `reaction-service` nhận `targetOwnerId`
trực tiếp từ client thay vì tự resolve qua Feign (tránh phải có 3 Feign client cho POST/COMMENT/REEL);
`fanpage-service` không có khái niệm private page (đúng theo thiết kế — fanpage luôn công khai,
không cần sửa như group).
