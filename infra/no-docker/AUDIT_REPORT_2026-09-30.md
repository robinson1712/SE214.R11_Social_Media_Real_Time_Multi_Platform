# Audit cloud no-Docker — 30/09/2026

## Phạm vi và kết luận

Audit source/config và kiểm thử backend local trên Windows; **không phải nghiệm thu cloud hoặc chứng nhận an toàn để public**. Checkout có nhiều thay đổi kế thừa chưa commit, base HEAD `c24ebc6a2dd545ba62847a7bf4aa4f0f84e0ffa9`. Không reset/restore/revert/commit, không Docker, không triển khai cloud. Có kiểm tra trạng thái cấu hình cục bộ qua Doctor; **không in, chép hoặc ghi giá trị secret vào báo cáo/chat**. Không tìm thấy AGENTS.md trong checkout hoặc tại D:\.

**Local package/test đạt:** 21/21 reactor entries SUCCESS lúc 09:51:07 +07:00 (2 phút 04 giây). Surefire XML có **524 cases: 518 pass, 6 skip, 0 failure, 0 error**, trong 45 suites. **Full clean build chưa đạt** vì Windows không xóa được một class output. CI Java 17, native PostgreSQL, browser/SockJS, cloud và ARM64 vẫn chưa được xác minh trong phiên này.

| Module | Cases | Skip |
|---|---:|---:|
| common-lib | 14 | 0 |
| api-gateway | 6 | 0 |
| auth-service | 31 | 1 |
| user-service | 82 | 0 |
| media-service | 24 | 0 |
| post-service | 54 | 0 |
| comment-service | 26 | 0 |
| reaction-service | 19 | 0 |
| story-service | 24 | 0 |
| reels-service | 30 | 0 |
| group-service | 52 | 5 |
| fanpage-service | 36 | 0 |
| dating-service | 16 | 0 |
| chat-service | 34 | 0 |
| notification-service | 33 | 0 |
| feed-service | 27 | 0 |
| moderation-service | 11 | 0 |
| search-service | 5 | 0 |

Root POM, Eureka và Config Server không có test suite. Bằng chứng XML nằm tại `common/common-lib/target/surefire-reports`, `infra/api-gateway/target/surefire-reports` và `services/*/target/surefire-reports`; tổng được cộng từ thuộc tính tests/failures/errors/skipped của mỗi TEST-*.xml, không đếm lại báo cáo TXT.

## Sửa trong phiên audit này

1. `StoryService.getStoriesByAuthor` trước đây trả story theo author mà không kiểm tra người xem; `markViewed` trả toàn bộ story và sửa viewer set mà không kiểm tra quan hệ hoặc hạn dùng. Nay cả hai yêu cầu owner hoặc bạn bè hiện tại; story hết hạn không thể markViewed. `commentAccess` dùng chung kiểm tra, xử lý friend response null theo hướng từ chối và không gọi friend service khi viewer là owner. Thêm 5 test hồi quy, cập nhật 3 test luồng hợp lệ. Đây là thay đổi quyền truy cập chủ ý, cần tiếp tục kiểm thử HTTP qua Gateway.
2. `AdminBootstrapPostgresTest` chỉ coi exception có SQLSTATE `40001` trong cause chain là transaction thua tranh chấp SERIALIZABLE. Lỗi runtime không liên quan được ném lại, không còn bị tính là race loser hợp lệ. Test biên dịch nhưng cần PostgreSQL native mới xác minh được race thật.

Các thay đổi kế thừa được đọc và kiểm thử lại gồm registration chỉ USER, bootstrap secret/after-commit event, Gateway token type/identity header, post/search/feed authorization, comment/reaction/save access, S3 adapter, WebSocket origin và STOMP guards. Không coi thay đổi kế thừa là do phiên audit này viết.

## Lệnh và bằng chứng local

Môi trường: Maven 3.9.14, JDK `C:\Program Files\Java\jdk-21.0.10`, compile target 17. Không tương đương chạy JDK 17 hoặc Linux ARM64. Maven executable:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
& 'C:\Users\ADMIN\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' -B -o package
```

- `-B -o clean package` trong sandbox: không resolve được BOM từ cache; chưa chạy test.
- Cùng lệnh ngoài sandbox: thiếu dependency plugin clean (`org/apache/maven/shared/utils/Os`).
- Các lần `clean package` trên Windows bị chặn khi xóa file build đang có access/lock (có lần tại `target/classes/com/socialapp/comment/client/StoryAccessClient.class`, lần gần nhất tại `common/common-lib/target/maven-status/maven-compiler-plugin/compile/default-compile/inputFiles.lst`). Không tự sửa ACL hoặc dừng Java process khác; nguyên nhân khóa file chưa được chứng minh. **Full clean build chưa đạt**.
- Chạy tiếp full reactor `-B -o package` ngoài sandbox để kiểm thử/build phần source hiện tại, không coi đây là clean-room build.
- Kết quả package: 19 JAR có Spring Boot JarLauncher, tổng 1.758.930.198 byte. XML của cả 45 suites có timestamp 09:49:06–09:51:06 +07:00, thuộc lần chạy cuối. `git diff --check` đạt; hash report lịch sử kiểm tra lại không đổi.
- Parse 9 file `.ps1` và 1 `.psd1` dưới `infra/no-docker` bằng PowerShell Parser: **10 file, 0 lỗi**; không thực thi setup/start/deploy.
- Dot-source riêng `Common.ps1` và `env.example.ps1`, gọi `Get-ServiceProcessEnvironment` cho 19 service: **PASS** mapping 10 schema SQL, 4 URI Mongo và bootstrap key chỉ trong generated map của auth. Không in giá trị secret. Kiểm tra này không chứng minh child process không thừa kế biến nhạy cảm đã có trong parent environment.
- `Setup.ps1 -SkipDownloads` chạy hai lần; lần đầu nâng cấp `.runtime/env.ps1` hiện có, lần hai không tạo diff thêm. Migration bỏ `ADMIN_EMAILS`, thêm `ADMIN_BOOTSTRAP_TOKEN` để trống và giữ lại các thiết lập provider khác. File runtime này được ignore bởi Git. Không dùng email chưa xác minh để tự cấp ADMIN.
- `Doctor.ps1 -SkipNetwork` thoát mã **1**: runtime env tồn tại; 19 JAR và Java/Kafka/Caddy/Alloy đều có; format Supabase JDBC đạt; disk đạt. **23/25 trường runtime cloud đang thiếu/placeholder**: `ENVIRONMENT_ID`; `SUPABASE_JDBC_BASE`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`; bốn `MONGODB_*_URI`; `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`; `STORAGE_S3_ENDPOINT`, `STORAGE_PUBLIC_ENDPOINT`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_REGION`; các `GRAFANA_PROMETHEUS_*`, `GRAFANA_LOKI_*`, `GRAFANA_OTLP_*` và `GRAFANA_API_TOKEN`. `ADMIN_BOOTSTRAP_TOKEN` là trường tùy chọn và hiện để trống. Flutter Web build còn thiếu tại `frontend/build/web`. Không đọc/đưa giá trị secret vào log audit.
- Sau audit, người dùng cung cấp project ref DoAn. Supabase connector xác nhận project `ACTIVE_HEALTHY`, region `ap-northeast-1`; truy vấn chỉ đọc `SELECT 1` trả về `1`. Đã điền bốn trường không bí mật vào file ignored `.runtime/env.ps1`: environment label `DoAn`, session-pooler username, S3 endpoint và region. **Không lấy hoặc lưu** database password hay S3 access/secret key. Không điền `STORAGE_PUBLIC_ENDPOINT` do media privacy vẫn là blocker. `Doctor.ps1 -SkipNetwork` hiện báo **19/25 trường cloud thiếu/placeholder** (DB connection string/password; bốn Mongo URI; ba Valkey fields; public media endpoint; hai S3 credentials; bảy Grafana fields) và Flutter Web build vẫn thiếu. Không chạy DDL hay sửa dữ liệu/schema Supabase.
- Kiểm tra **source** `services/*/src/main/resources/application-cloud-free.yml`: 16/16 có address/hostname loopback và `prefer-ip-address: false`. Lần quét đệ quy ban đầu lấy cả target cũ nên báo mismatch; đã giới hạn lại đúng source. Đây không phải test config precedence/runtime/firewall.
- `Get-Command psql,postgres,flutter` và service PostgreSQL không tìm thấy trong môi trường hiện tại. Không cài DB hay dùng DB cloud thật để chạy create-drop.

## Rủi ro còn mở, không được suy ra PASS từ unit tests

| Mức | Bằng chứng source và tác động | Điều kiện đóng |
|---|---|---|
| P1 | `MediaService` trả `publicEndpoint/bucket/objectKey`; bucket public không áp quyền post/story/chat. URL đã biết vẫn đọc được dù ACL nội dung đổi. | Thiết kế private read/Range proxy hoặc cơ chế thu hồi được chứng minh, test trực tiếp object sau đổi ACL. Không vá một phần bằng cách chỉ giấu URL. |
| P1 | `JwtTokenProvider` còn secret mặc định cố định, không issuer/audience validation; Gateway `resolveToken` nhận query token trên mọi path. | Fail startup production khi thiếu secret, giới hạn query-token route theo client contract, redaction và negative HTTP tests; key rotation/revocation. |
| P1 | SQL init tạo NOLOGIN roles nhưng launcher mặc định dùng chung postgres; sample TLS chỉ `sslmode=require`. | Credential LOGIN riêng, negative cross-schema test, verify-full/CA/hostname và tách migration privilege trên DB cách ly. |
| P2 | `ChatStompInboundGuard.releaseConnection` xóa send bucket khi connection cuối đóng; reconnect tạo burst đầy. | Giữ rate state có TTL/bounded storage độc lập connection, test reconnect và multi-instance. Không chỉ bỏ cleanup gây tăng bộ nhớ vô hạn. |
| P2 | `PostService.filterVisible` lọc sau phân trang nhưng giữ totalElements trước lọc; search chạy anonymous-safe nên không trả private-group post kể cả member. | Query/count theo quyền hoặc contract phân trang mới; test count leakage, underfilled pages và member search. |
| P2 | Auth after-commit tránh event từ rollback nhưng không phải durable outbox; crash/send failure sau commit có thể thiếu profile event. | Outbox/retry/idempotency, fault-injection Kafka/DB và kiểm tra exactly-once business outcome. |
| P2 | Compiler plugin chưa pin version ở root; local JDK21 dùng source/target17 thay vì release17, có cảnh báo system modules path. | Pin toolchain/plugin và chạy CI Java17; không coi bytecode target là bằng chứng tương thích API Java17. |

Ngoài ra chưa có live evidence cho expiry/revocation của WebSocket đã mở, SockJS fallback, proxy header trùng/case, block/unfriend sau fanout, HTTP status contract, saved-list stale references, DB/Kafka side effects và outage recovery. Không audit lại quota/giá nhà cung cấp trong phiên này; các giả định cloud trong PLAN vẫn cần xác minh tài khoản thật.

## Gate còn BLOCKED/PENDING

- Native PostgreSQL: 5 group repository cases và 1 bootstrap race case chỉ chạy khi `RUN_NATIVE_POSTGRES_TESTS=true`. CI có bước cài PostgreSQL native/tạo database nhưng workflow **chưa chạy**. Chỉ bật trên DB test dùng một lần; bootstrap dùng create-drop, không trỏ `AUTH_TEST_POSTGRES_URL` vào DB thật. Group test dùng group_db từ datasource hiện tại, cũng phải cách ly.
- Không chạy Flutter build/browser vì không có Flutter trên PATH; không build Windows runtime bundle hoặc khởi động 19 service dùng credential cloud.
- Cloud Supabase/Atlas/Storage/Valkey/Grafana, Linux ARM64 capacity, TLS renewal, backup/restore, laptop-off soak và React parity: **PENDING**, ngoài kiểm thử local này. Giữ toàn bộ acceptance matrix chưa nghiệm thu.
- `VERIFICATION_REPORT.md` lịch sử giữ nguyên; SHA256 tại audit: `DF2102DB6E60B12B8432B14CB8D93C25BBCC8355C88D6A63B0D46E6D714A7201`.
