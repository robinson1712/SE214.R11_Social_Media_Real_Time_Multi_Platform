# Social Media Backend (Facebook-like) — Java Spring Boot Microservices

> **Frontend:** Next.js/React nằm trong [`frontend-web`](frontend-web/README.md), production chạy trên Cloudflare Workers.
> **Backend:** Java Spring Boot microservices, đóng gói thành JAR và chạy native trên VM.
> GitHub Actions phục vụ CI/CD; YAML của Spring Boot là cấu hình runtime backend.
> Hướng dẫn chạy local: [NO_DOCKER_SETUP.md](NO_DOCKER_SETUP.md). Trạng thái cloud/nghiệm thu: [PLAN.md](PLAN.md) và [ACCEPTANCE_MATRIX.md](infra/no-docker/ACCEPTANCE_MATRIX.md).

Backend Java/Kafka chạy native trên máy backend; Next.js gọi HTTPS/WSS gateway origin được cấu hình lúc build. URL Worker production sẽ xuất hiện trong job deploy của GitHub Actions.

Backend microservice cho một mạng xã hội kiểu Facebook: post, avatar, story, reels, comment,
reaction, block, group, fanpage, dating và nhắn tin real-time. Web client gọi REST API + WebSocket.

## Kiến trúc

```
Next.js Web client
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

  frontend-web (3000) — Next.js, gọi API Gateway qua HTTPS/WebSocket proxy

  Config Server (8888) — cấp config tập trung (native, đọc từ ./config-repo)
  Kafka — giao tiếp bất đồng bộ giữa các service (post/comment/reaction/group/page/match/message events)

  Observability: Grafana Alloy (tùy chọn) gửi metrics/logs/traces tới Grafana Cloud
```

Kiến trúc cloud mục tiêu và điều kiện nghiệm thu: xem [PLAN.md](PLAN.md) và
[ACCEPTANCE_MATRIX.md](infra/no-docker/ACCEPTANCE_MATRIX.md).

## Công nghệ

- Java 17, Spring Boot 3.3.4, Spring Cloud 2023.0.3
- Spring Web MVC (business services) + Spring Cloud Gateway/WebFlux (api-gateway)
- Spring Data JPA (Postgres) / Spring Data MongoDB / Spring Data Redis
- Spring Kafka, Spring Cloud OpenFeign, Spring WebSocket (STOMP)
- JWT (jjwt) tự issue ở `auth-service`, xác thực tập trung ở `api-gateway`
- MinIO (S3-compatible) cho lưu trữ media
- Next.js, React, TypeScript cho giao diện web

## Chạy local

Yêu cầu: Java 17+, Maven, Node.js 24+. Cài dependency UI một lần, sau đó build backend và khởi động runtime:

```powershell
Push-Location frontend-web
npm ci
Pop-Location
.\infra\no-docker\scripts\Build.ps1
.\infra\no-docker\scripts\Doctor.ps1
.\infra\no-docker\scripts\Start.ps1
```

`Start.ps1` khởi động backend và Next.js. Mở `http://localhost:3000`; dừng bằng `infra/no-docker/scripts/Stop.ps1`.
Windows runtime bundle chỉ chứa backend; frontend production được deploy riêng lên Cloudflare Workers.

### Test nhanh API

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

Observability: Grafana Alloy gửi telemetry tới Grafana Cloud khi bật cấu hình; log ứng dụng local nằm trong `.runtime/logs`. Giao diện web local dùng cổng 3000.

Tracing sampling mặc định 100% (`TRACING_SAMPLING_PROBABILITY`, mặc định `1.0`). Production nên đặt mức thấp hơn nếu cần giảm overhead và dung lượng lưu trữ Zipkin.

### Chạy từng service riêng lẻ khi phát triển

Cần các datastore và Kafka đã sẵn sàng; chạy service bằng `mvn spring-boot:run` trong từng thư mục `services/<name>`, theo thứ tự:
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
- **Observability**: distributed tracing và log qua Grafana Alloy/Grafana Cloud khi cấu hình —
  xem mục Observability ở trên.
- **CI/CD hiện tại**: workflow cài PostgreSQL native trên hosted runner, chạy kiểm tra frontend/backend, phát hành backend và deploy Next.js Worker. Workflow Windows tạo bundle backend-only.
  Xem [.github/workflows](.github/workflows) để biết từng job.
- **Kiểm tra backend**: workflow bật
  `GroupRepositoryIntegrationTest` bằng `RUN_NATIVE_POSTGRES_TESTS=true`, chạy Maven, rồi build
  và upload bundle Windows sau khi CI đạt. Xem [PLAN.md](PLAN.md) để biết các gate còn mở.
- **Content moderation** (`moderation-service`, port 8095): profanity filter tự động (tiếng Anh +
  tiếng Việt, giữ nguyên dấu thanh) chặn `post-service`/`comment-service`/`reels-service`/
  `story-service`/`group-service`/`fanpage-service` lúc tạo nội dung — đủ cả 6 loại nội dung trong hệ
  thống; user report nội dung vi phạm qua `POST /api/moderation/reports`; admin (role `ADMIN`) duyệt
  hàng đợi qua `GET /api/moderation/reports`, xử lý `PUT /api/moderation/reports/{id}/resolve` với
  `REMOVE_CONTENT` sẽ publish sự kiện Kafka xoá nội dung xuyên service (`group-service`/
  `fanpage-service` xoá cascade cả bảng thành viên/follower/admin liên quan). Đăng ký công khai luôn
  tạo role `USER`; email không còn tự cấp role `ADMIN`. Admin đầu tiên có thể được tạo qua
  `POST http://127.0.0.1:8081/internal/auth/bootstrap-admin` bằng header
  `X-Admin-Bootstrap-Token`, chỉ từ máy auth-service hoặc SSH tunnel tới loopback. Đặt
  `ADMIN_BOOTSTRAP_TOKEN` thành bí mật ngẫu nhiên tối thiểu 32 byte cho lần bootstrap, rồi gỡ bí mật
  khỏi môi trường; endpoint từ chối nếu đã có ADMIN. Không gọi qua API Gateway. Đây mới là triển khai
  source, chưa được xác nhận runtime; xem [PLAN.md](PLAN.md) và [acceptance matrix](infra/no-docker/ACCEPTANCE_MATRIX.md).
- **Share/repost, tag người dùng, custom audience privacy** (`post-service`): `POST /api/posts/{id}/share`
  chia sẻ lại 1 bài (kèm lời bình, tăng `shareCount` bài gốc); tạo/sửa bài có thể kèm `taggedUserIds`
  (thông báo `type: TAG` cho người được tag); `privacy: CUSTOM` + `customAudienceUserIds` giới hạn
  người xem theo danh sách cụ thể. Một số đường list (`getPostsByAuthor`/`ByGroup`/`ByPage`)
  kiểm tra privacy; GET theo ID, batch và feed còn rủi ro cần sửa/test theo [PLAN.md](PLAN.md).
- **Tìm kiếm tổng hợp** (`search-service`, port 8096): `GET /api/search?q=&limit=` gộp kết quả từ
  user/group/fanpage/post-service qua Feign (mỗi client có circuit breaker + fallback rỗng riêng —
  1 service down không sập cả search). Không có database riêng.
- **Frontend Next.js** (`frontend-web/`): giao diện web hiện tại, gắn vào
  các API thật ở trên — đăng nhập, feed, đăng bài (kèm share/tag/custom-audience privacy), comment/
  reply, reaction, profile + kết bạn, tìm kiếm tổng hợp (kèm trang kết quả riêng), Group, Fanpage,
  Story, Reels, Dating, Chat real-time (STOMP/WebSocket), Notification real-time (STOMP/WebSocket),
  trang quản trị Moderation (gate theo role `ADMIN`). Các case cloud/nghiệp vụ còn lại nằm trong
  [ACCEPTANCE_MATRIX.md](infra/no-docker/ACCEPTANCE_MATRIX.md).

## Giới hạn còn lại

Một vài đơn giản hoá có chủ đích còn lại (ghi rõ trong code bằng comment): `reaction-service` nhận `targetOwnerId`
trực tiếp từ client thay vì tự resolve qua Feign (tránh phải có 3 Feign client cho POST/COMMENT/REEL);
`fanpage-service` không có khái niệm private page (đúng theo thiết kế — fanpage luôn công khai,
không cần sửa như group).
