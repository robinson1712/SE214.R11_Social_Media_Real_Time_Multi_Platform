# Runtime Windows không Docker hiện có — chưa phải website cloud

Tài liệu này mô tả **script Windows/PowerShell trong working tree hiện tại** để thử profile `cloud-free`. Nó vẫn chạy 19 JAR, Kafka và Flutter Web trên **máy đang mở**; tắt laptop thì website ngừng hoạt động. Nó **không** triển khai OCI, React, HTTPS IP, CI/CD tự động hoặc hoàn tất [PLAN.md](PLAN.md). Backend local đã chạy package/test ngày 30/09: 518 pass, 6 PostgreSQL skip, 0 lỗi; full clean build bị chặn bởi class output không xóa được trên Windows. Xem [audit mới](infra/no-docker/AUDIT_REPORT_2026-09-30.md). Các bước khởi động/cloud dưới đây chưa được xác minh lại; [VERIFICATION_REPORT.md](infra/no-docker/VERIFICATION_REPORT.md) chỉ ghi kết quả ngày 16/09/2026, với full cloud acceptance pending.

> Không dùng bộ script này làm đường mở Internet cho người dùng thật. JWT/default security và bucket public còn các blocker trong [PLAN.md](PLAN.md#4-bảo-mật-trước-khi-công-khai-internet). `ADMIN_BOOTSTRAP_TOKEN` để trống theo mặc định; chỉ nạp bí mật ngẫu nhiên tối thiểu 32 byte trong lần bootstrap đầu trên môi trường đóng. Chỉ dùng dữ liệu demo cách ly nếu tiếp tục thử Windows local.

## Luồng chạy sau refactor (02/10/2026)

File cấu hình được đọc là `.runtime/env.ps1`. Launcher ghép các thiết lập mặc định mới vào bộ nhớ khi file cũ thiếu chúng; credential đã nhập không bị ghi đè. Trong URI Atlas, `<db_password>` vẫn là placeholder: thay bằng database-user password đã URL-encode, không dùng password đăng nhập tài khoản Atlas hoặc tự dùng password Supabase.

```powershell
.\infra\no-docker\scripts\Build.ps1
.\infra\no-docker\scripts\Doctor.ps1
.\infra\no-docker\scripts\Test-CloudConnections.ps1
.\infra\no-docker\scripts\Start.ps1
```

`Build.ps1` yêu cầu Maven/JDK và Flutter SDK (PATH hoặc `.runtime/tools/flutter/bin/flutter.bat`). Backend build/test ở profile `native-runtime`, dùng output mới `<module>/.runtime/build` để tránh class cũ hoặc file `target` bị khóa trên Windows; chỉ sau build đạt mới copy đủ 19 JAR vào `apps/`. Launcher ưu tiên các JAR này. Frontend dùng lockfile đã đồng bộ với Flutter 3.47.5; CI cũng khóa phiên bản đó. `Build.ps1 -SkipFrontend -Offline` kiểm thử/build backend từ Maven cache.

`Test-CloudConnections.ps1` yêu cầu JDK với `javac` trên PATH. Nó dùng driver từ các JAR ứng dụng và chạy kiểm tra chỉ đọc: SQL login + schema USAGE/CREATE của từng service, Mongo ping/list collections, Valkey AUTH/PING với TLS hostname validation, S3 HeadBucket. Đầu ra không in URI, password hay SDK exception message. `-SkipMissing` giúp kiểm tra phần đã nhập nhưng vẫn trả exit code 1 khi còn credential thiếu. Các kết quả này không chứng minh quyền cách ly, media privacy, CRUD nghiệp vụ hoặc Grafana ingest. Start mặc định chạy Doctor và kiểm tra kết nối trước khi mở service.

Cổng Gateway có thể đặt bằng `GATEWAY_PORT`; Caddy và Alloy lấy cùng cổng. Máy hiện có một ứng dụng Java khác ở 8080, nên runtime đã đặt `GATEWAY_PORT = "18080"`. Trình duyệt vẫn mở `http://localhost:3001`; native client cần override `API_BASE_URL` nếu dùng cổng khác mặc định. Chi tiết kiểm chứng và các blocker còn lại được ghi trong [RUNTIME_REPORT_2026-10-02.md](infra/no-docker/RUNTIME_REPORT_2026-10-02.md).

Launcher giới hạn Hikari của mỗi service PostgreSQL ở `maximum-pool-size=2`, `minimum-idle=0` qua biến môi trường, kể cả khi cấu hình chung từ Config Server không được tải. `Start.ps1` mặc định khởi động service nghiệp vụ từng đợt 2 service và chờ health của đợt trước; có thể dùng `-BusinessStartupBatchSize 1` để giảm tải khởi động. Giới hạn này tránh tình trạng pool mặc định của nhiều service làm hết slot PostgreSQL (`53300`), nhưng vẫn cần đủ RAM và ngân sách kết nối của project.

### Thành phần Windows

| Thành phần | Vị trí hiện tại | Khác với đích cloud |
|---|---|---|
| 19 Spring Boot JAR, Kafka 3.7.0 KRaft, Caddy, Alloy | Native trên Windows local | Đích là Linux ARM64/systemd trên OCI A1, chưa có |
| PostgreSQL 10 schema, MongoDB 4 DB, Supabase Storage | Supabase/Atlas/cloud được cấu hình qua profile | Cloud conformance, quyền schema, media privacy chưa đạt |
| Redis-compatible | Aiven Valkey Free qua TLS | Đích thử Valkey native trên VM; profile hiện chưa đổi |
| Frontend | Flutter Web trên `localhost:3001` | Đích React + TypeScript trên Pages hoặc Caddy sau parity |
| Quan sát | Grafana Cloud và Alloy static scrape | Chưa chứng minh đủ target, Kafka trace hay quota |

## Chỗ điền thông tin cloud

Chạy `infra/no-docker/scripts/Setup.ps1` một lần từ PowerShell ở thư mục repo. File cần sửa là **`.runtime/env.ps1` ở thư mục gốc repo** (không phải `infra/no-docker/env.example.ps1`). Nếu đã chạy Setup, mở trực tiếp file đó bằng VS Code hoặc editor văn bản. `.runtime/` đã bị Git ignore để credentials không vào commit.

Thay placeholder/giá trị ví dụ bằng thông tin lấy từ dashboard của tài khoản cloud:

| Nhà cung cấp | Biến cần nhập trong `.runtime/env.ps1` |
|---|---|
| Tên môi trường | `ENVIRONMENT_ID` |
| Supabase Database | `SUPABASE_JDBC_BASE`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD` |
| MongoDB Atlas | `MONGODB_STORY_URI`, `MONGODB_REELS_URI`, `MONGODB_CHAT_URI`, `MONGODB_NOTIFICATION_URI` |
| Aiven Valkey | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`; giữ `REDIS_USERNAME` và `REDIS_SSL_ENABLED` theo cấu hình TLS của project |
| Supabase Storage S3 | `STORAGE_S3_ENDPOINT`, `STORAGE_PUBLIC_ENDPOINT`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_REGION`; bucket mặc định là `social-media` |
| Grafana Cloud, khi `GRAFANA_ENABLED = "true"` | `GRAFANA_PROMETHEUS_URL`, `GRAFANA_PROMETHEUS_USER`, `GRAFANA_LOKI_URL`, `GRAFANA_LOKI_USER`, `GRAFANA_OTLP_URL`, `GRAFANA_OTLP_USER`, `GRAFANA_API_TOKEN` |

`JWT_SECRET` do Setup tạo cho ứng dụng, không lấy từ nhà cung cấp. Để `ADMIN_BOOTSTRAP_TOKEN` trống khi chưa tạo admin đầu tiên; đây không phải cloud API key. Không gửi credentials trong chat và không commit file runtime.

### Supabase DoAn: trường nào lấy ở đâu

Kiểm tra riêng Supabase, không yêu cầu credential MongoDB/Valkey/Storage:

```powershell
.\infra\no-docker\scripts\Test-CloudConnections.ps1 -SupabaseOnly
```

Nếu gặp `SQLSTATE=28P01`, đối chiếu cả host, username và database password trong **Connect → Session pooler** của đúng project. Nếu quên password, đặt lại tại **Database → Settings** rồi cập nhật riêng `SUPABASE_DB_PASSWORD` trong `.runtime/env.ps1`. Dùng chuỗi gốc, không URL-encode, vì JDBC nhận password qua biến riêng; chuỗi PowerShell nên dùng nháy đơn và nhân đôi ký tự nháy đơn bên trong nếu có. Nếu vừa đổi password, pooler có thể còn cache password cũ; thử kết nối lại theo [hướng dẫn Supabase](https://supabase.com/docs/guides/troubleshooting/supavisor-error-password-authentication-failed-after-password-rotation), tránh reset nhiều lần liên tiếp.

Chỉ khi login thành công mới xử lý lỗi schema/quyền: chạy `infra/no-docker/config/supabase/init-schemas.sql` trong SQL Editor của cùng project nếu các schema chưa được tạo. Kết quả đạt là cả 10 service đều `PASS login/schema USAGE/CREATE`; đây là kiểm tra chỉ đọc, không tạo hoặc xóa dữ liệu.

Có thể áp dụng cùng file SQL từ máy có JDK, dùng driver PostgreSQL trong JAR đã build và credential của `.runtime/env.ps1`:

```powershell
.\infra\no-docker\scripts\Initialize-Supabase.ps1
.\infra\no-docker\scripts\Test-CloudConnections.ps1 -SupabaseOnly
```

Lệnh khởi tạo tạo 10 schema/role và đặt quyền theo script SQL trong một transaction; không xóa bảng hay dữ liệu, không đổi database password. Khác với lệnh kiểm tra, lệnh khởi tạo có sửa cấu trúc/quyền database. Chạy khi thiết lập môi trường ứng dụng.

Mở [project DoAn](https://supabase.com/dashboard/project/oksedozcqzjxnpujlqeh), sau đó vào [Connect → Session pooler](https://supabase.com/dashboard/project/oksedozcqzjxnpujlqeh?showConnect=true&method=session). Trong hộp **Session pooler**, chép connection string hoặc các giá trị host, port, database, username theo [hướng dẫn kết nối chính thức](https://supabase.com/docs/guides/database/connecting-to-postgres). Project ref của DoAn là `oksedozcqzjxnpujlqeh`; region là `ap-northeast-1`.

| Tên trong `.runtime/env.ps1` | Tên tương ứng trên Supabase | Giá trị cho DoAn / cách điền |
|---|---|---|
| `SUPABASE_JDBC_BASE` | **Session pooler → host, port, database** | `jdbc:postgresql://<host-vừa-chép>:5432/postgres?sslmode=require` |
| `SUPABASE_DB_USER` | **Session pooler → user / username** | `postgres.oksedozcqzjxnpujlqeh` |
| `SUPABASE_DB_PASSWORD` | **Database password** | Mật khẩu Postgres của project, đã tạo lúc tạo project. Đây không phải mật khẩu tài khoản Supabase và cũng không phải `anon`/publishable key hay `service_role` key. |

Nếu quên database password, đặt lại trong **Project → Database → Settings** theo [hướng dẫn xử lý lỗi mật khẩu của Supabase](https://supabase.com/docs/guides/troubleshooting/fatal-password-authentication-failed). Đặt lại password có thể làm các kết nối hiện có phải cập nhật theo; không reset nếu password hiện tại vẫn dùng được. Giữ password riêng trong `.runtime/env.ps1`, không dán vào chat hay URL JDBC.

Đừng điền hostname mẫu kiểu `aws-0-REGION.pooler.supabase.com` bằng cách thay tên vùng. Chỉ dùng đúng host mà hộp **Connect → Session pooler** hiển thị cho project này; Supabase có thể có nhiều pooler cluster trong một region. Trong app, user và password là các biến riêng, còn JDBC URL dùng host/port/database cộng `sslmode=require`.

Các cặp `SUPABASE_<SERVICE>_USER/PASSWORD` là **tùy chọn**, không phải credential có sẵn trong Supabase Dashboard. Nếu để trống, script dùng `SUPABASE_DB_USER/PASSWORD` cho tất cả service. Nếu muốn tách role DB, trước tiên chạy `infra/no-docker/config/supabase/init-schemas.sql` trong SQL Editor; script tạo các role `NOLOGIN`. Sau đó đặt `LOGIN` và password ngẫu nhiên riêng cho từng role, rồi điền username theo dạng `<role>.<project-ref>` khi đi qua shared pooler:

| Prefix trong env | Role Postgres do script tạo | Username Session pooler cho DoAn |
|---|---|---|
| `AUTH` | `sma_auth_service` | `sma_auth_service.oksedozcqzjxnpujlqeh` |
| `USER` | `sma_user_service` | `sma_user_service.oksedozcqzjxnpujlqeh` |
| `MEDIA` | `sma_media_service` | `sma_media_service.oksedozcqzjxnpujlqeh` |
| `POST` | `sma_post_service` | `sma_post_service.oksedozcqzjxnpujlqeh` |
| `COMMENT` | `sma_comment_service` | `sma_comment_service.oksedozcqzjxnpujlqeh` |
| `REACTION` | `sma_reaction_service` | `sma_reaction_service.oksedozcqzjxnpujlqeh` |
| `GROUP` | `sma_group_service` | `sma_group_service.oksedozcqzjxnpujlqeh` |
| `FANPAGE` | `sma_fanpage_service` | `sma_fanpage_service.oksedozcqzjxnpujlqeh` |
| `DATING` | `sma_dating_service` | `sma_dating_service.oksedozcqzjxnpujlqeh` |
| `MODERATION` | `sma_moderation_service` | `sma_moderation_service.oksedozcqzjxnpujlqeh` |

Ví dụ, cặp `SUPABASE_AUTH_USER`/`SUPABASE_AUTH_PASSWORD` phải khớp với role `sma_auth_service` và password bạn tự đặt cho role đó. Tạo role riêng và điền biến riêng **chưa tự chứng minh** quyền đã được giới hạn đúng; cần chạy acceptance kiểm tra truy cập schema cho phép/bị từ chối trước khi dùng môi trường public. Mật khẩu Postgres dùng chung phù hợp cho thử nghiệm cách ly, không phải cấu hình production.

Chạy `infra/no-docker/scripts/Doctor.ps1 -SkipNetwork` để kiểm tra file, placeholder, runtime tools và JAR. Khi các trường bắt buộc đã hết placeholder, chạy Doctor không có `-SkipNetwork` để kiểm tra thêm TCP tới Supabase DB, Valkey và Storage. Doctor không xác thực credential với từng nhà cung cấp, không kiểm tra Mongo/Grafana, không nghiệm thu quyền dữ liệu, TLS hostname, billing hoặc media privacy. Audit đầu ngày 30/09 báo **23/25 trường runtime cloud thiếu/placeholder**; sau khi kết nối project DoAn và điền metadata không bí mật, Doctor hiện báo **19/25 còn thiếu/placeholder**, và Flutter Web build chưa có. Điền credential là bước cấu hình cần thiết nhưng **chưa đủ để hoàn thành cloud target**; các gate ở `PLAN.md` và `infra/no-docker/ACCEPTANCE_MATRIX.md` vẫn phải được chạy và lưu bằng chứng.

`media-service` dùng MinIO client trong profile mặc định và AWS S3 adapter trong `cloud-free`. Adapter có endpoint Supabase `/storage/v1/s3`; URL public là `/storage/v1/object/public/...`. Test SDK/config trong report không chứng minh thao tác live, Range hay privacy. Bucket public-by-link không thực thi quyền của post/chat; chỉ dùng cho demo local cách ly hoặc media thật sự công khai. Bảo vệ media riêng tư là gate **bắt buộc** của cloud trong [PLAN.md](PLAN.md#4-bảo-mật-trước-khi-công-khai-internet) và [ME-02](infra/no-docker/ACCEPTANCE_MATRIX.md), không có ngoại lệ nghiệm thu bằng public-by-link.

## Thử nghiệm Windows local, chỉ khi có môi trường demo riêng

1. Tạo Supabase Free project, chạy `infra/no-docker/config/supabase/init-schemas.sql` trong SQL Editor; chuẩn bị Atlas Free cluster, Aiven Valkey Free và Grafana Cloud Free. Không dùng Supabase Auth cho ứng dụng. **Chú ý:** SQL hiện tạo `NOLOGIN` roles nhưng mặc định tất cả service dùng `postgres`; schema chưa cách ly quyền. Không dùng credential này cho bản public. Với DB, `sslmode=require` trong hướng dẫn cũ chỉ mã hóa; đích cần xác minh chứng chỉ/hostname và role riêng. [Supabase kết nối](https://supabase.com/docs/guides/database/connecting-to-postgres).
2. Chạy `infra/no-docker/scripts/Setup.ps1` từ PowerShell ở root, điền `.runtime/env.ps1` do setup tạo. Setup bổ sung `ADMIN_BOOTSTRAP_TOKEN` vào file runtime cũ và bỏ biến `ADMIN_EMAILS` lỗi thời, giữ nguyên các giá trị cấu hình khác. File chứa secret phải ở ngoài commit. Để đăng ký user thông thường, để `ADMIN_BOOTSTRAP_TOKEN` trống. Khi cần admin đầu tiên, dùng secret ngẫu nhiên ít nhất 32 byte, gọi trực tiếp auth-service qua `127.0.0.1:8081` (hoặc SSH tunnel) tới `/internal/auth/bootstrap-admin`, rồi xóa secret khỏi môi trường và kiểm tra lại role trong DB. Route này không được API Gateway proxy và từ chối lần bootstrap tiếp theo khi đã có ADMIN. Không dựa vào email để cấp quyền. Mongo cần bốn DB `story_db`, `reels_db`, `chat_db`, `notification_db`. Tạo public bucket `social-media` **chỉ cho dữ liệu demo không riêng tư**.
3. Nếu build thủ công trên máy phát triển, dùng Maven và Flutter hiện tại. Máy chạy thử có thể dùng bundle Windows đã build từ commit phù hợp. `tools.lock.psd1` tải Windows AMD64; không copy vào Linux ARM64.

```powershell
.\infra\no-docker\scripts\Setup.ps1
mvn -B clean package -DskipTests
Push-Location frontend
flutter pub get
flutter build web --release
Pop-Location
.\infra\no-docker\scripts\Doctor.ps1
.\infra\no-docker\scripts\Start.ps1
.\infra\no-docker\scripts\Status.ps1
```

Các địa chỉ **local**: Flutter `http://localhost:3001`, Gateway `http://localhost:8080`, Eureka `http://localhost:8761`, Swagger qua Gateway `http://localhost:8080/swagger-ui.html`. Trình duyệt dùng cùng origin Caddy để gọi REST/WebSocket; Flutter native vẫn dùng Gateway `8080`. Log ở `.runtime/logs`. Dừng bằng `infra/no-docker/scripts/Stop.ps1`; Kafka data trong `.runtime/data/kafka` được giữ qua restart. Không xem health HTTP đơn lẻ là readiness của toàn hệ.

```powershell
.\infra\no-docker\scripts\Stop.ps1
.\infra\no-docker\scripts\Build-RuntimeBundle.ps1
.\infra\no-docker\scripts\Measure-Disk.ps1
```

`Build-RuntimeBundle.ps1` tạo bundle Windows và kiểm tra ngưỡng khoảng 10 GB của **bundle cũ**. Số đo này không bao gồm ngân sách dài hạn của VM, OS, Kafka retention, backup và nhiều release. Không áp dụng ngưỡng này làm gate cloud; xem ngân sách mới trong [PLAN.md](PLAN.md#31-ngân-sách-tài-nguyên-phải-đo-trên-arm).

## Tình trạng kiểm thử và điều kiện chuyển hướng

- Workflow `.github/workflows/ci-cd.yml` cài PostgreSQL native trên hosted runner, bật `GroupRepositoryIntegrationTest` và `AdminBootstrapPostgresTest` bằng `RUN_NATIVE_POSTGRES_TESTS=true`, rồi chạy Maven; không còn Testcontainers trong group-service. Khi chạy Maven cục bộ không đặt biến này, integration test được skip để các bài unit không cần PostgreSQL. Full reactor backend local đã chạy đạt (518 pass, 6 PostgreSQL skip); workflow CI và bundle frontend chưa chạy, nên trạng thái CI vẫn **chưa xác minh**.
- Thử thật Supabase/Atlas/Storage/Valkey/Grafana và toàn bộ nghiệp vụ theo [ACCEPTANCE_MATRIX.md](infra/no-docker/ACCEPTANCE_MATRIX.md). Những mục này đang pending, không suy ra pass từ test cấu hình. Đặc biệt cần kiểm tra quyền post/feed/search, ADMIN bootstrap, media privacy và Range, STOMP/SockJS và tương thích MongoDB 8. Backend đã có một số kiểm tra quyền và guard STOMP với targeted unit coverage; chúng chưa thay thế acceptance qua Gateway/cloud.
- Chỉ chuyển sang hướng dẫn cloud sau khi các pha/gate trong [PLAN.md](PLAN.md#8-thứ-tự-thực-hiện-và-cổng-gono-go) có bằng chứng. Bản Windows hiện tại có ích để đối chiếu nhưng không phải giải pháp laptop tắt vẫn phục vụ.
