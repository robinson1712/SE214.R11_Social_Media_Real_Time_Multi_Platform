# Kiểm tra bàn giao repo nhóm — 10/10/2026

**Kết luận: CHƯA ĐỦ ĐIỀU KIỆN MERGE vào main.** Bản tích hợp riêng đã build và test thành công trong phạm vi bên dưới; credential lịch sử, advisory dependency và quyền đọc media còn cần xử lý. Không push, tạo PR, merge hoặc deploy trong phiên kiểm tra này.

## 1. Git và bản tích hợp

- Repo nhóm: `robinson1712/SE214.R11_Social_Media_Real_Time_Multi_Platform`; base `a181e73ffc29aecdda15567c6c36628ec60a25b6`.
- Repo test: `Jandz01/DoAn1`; source đã commit `dfc557d`.
- `a181e73` là commit thường với thông điệp `reverse commit`, parent `bf8052e`. `git diff c24ebc6 origin/main` rỗng: nội dung repo nhóm đã trở về đúng base cũ.
- Hai nhánh có 2 commit riêng bên nhóm và 9 commit riêng bên cá nhân. Bản tích hợp được lấy từ snapshot source đã commit, loại toàn bộ `.runtime/`, đặt trên `codex/team-handoff-audit` bắt đầu từ commit reverse của nhóm. Không nhập 9 commit cá nhân vào ancestry vì chúng có lịch sử credential.
- Không khôi phục Android mobile/tài liệu của `bf8052e` đã bị nhóm reverse. Không sửa các file đang làm dở ở checkout gốc: `infra/azure/VM_CONTROL_VI.md`, `infra/azure/SETUP_TEAM_VI.md`.

## 2. Kết quả thực chạy

| Kiểm tra | Kết quả | Giới hạn |
|---|---|---|
| Maven reactor | 21/21 entries SUCCESS; 19 executable service JAR | Maven 3.9.15; compile bằng JDK 21, target Java 17 |
| Surefire trên Java 17.0.20.1 | 527 cases; 521 pass; 6 skip; 0 failure/error; 46 suites | Byte Buddy agent có sẵn được nạp bằng `-javaagent` vì JRE 17 local thiếu attach provider cho Mockito |
| Python discovery/package/rollback | 7/7 PASS | Mock systemd, không phải deploy VM thực |
| Flutter dependency lock | `pub get --offline --enforce-lockfile` PASS | Flutter 3.47.5 / Dart 3.13.4 |
| Flutter analyze | 0 issues | Bản đầu có 23 issues; đã sửa trên nhánh audit |
| Flutter HTTP regression | 5/5 PASS | HTTP server loopback cách ly, token giả; không gọi cloud |
| Flutter Web release | PASS | JavaScript web build; wasm dry-run có cảnh báo package chưa hỗ trợ |
| Browser smoke | Login render; form trống hiện lỗi; điều hướng register; register trống hiện lỗi; console error = 0 | Chạy release build local, không tạo tài khoản hoặc xác minh đăng nhập backend |
| Credential guard | PASS; so 10 giá trị credential hiện tại, 0 match trong source tích hợp | Guard hẹp; không thay thế entropy scanner hoặc scan toàn bộ lịch sử |
| OSV dependency scan | 440 gói/phiên bản; 53 gói/phiên bản có advisory; 182 advisory duy nhất | Theo đúng phiên bản resolve; chưa chứng minh điều kiện khai thác của từng advisory |

Thống kê test đầy đủ và 6 case skip: [test-summary-2026-10-10.json](test-summary-2026-10-10.json).
Sáu case PostgreSQL gồm 5 query/visibility group và 1 race bootstrap admin. Chưa có native PostgreSQL cách ly ở máy local; không dùng database cloud đang chạy để thử create/drop. CI có bước cài PostgreSQL native và `RUN_NATIVE_POSTGRES_TESTS=true`, nhưng CI của nhánh mới chưa được chạy.

![Login validation local](evidence/login-validation.png)
![Register validation local](evidence/register-validation.png)

## 3. Lỗi đã sửa và kiểm chứng

1. **Banned account refresh:** `AuthService.refresh` trước đây vẫn phát access token cho account BANNED nếu refresh token còn hạn. Đã thêm check tương ứng login và test xác nhận không gọi token generator.
2. **401 retry vô hạn:** frontend retry dùng lại interceptor nhưng thiếu cờ đã refresh, dẫn tới refresh/retry tiếp nếu request vẫn 401. Đã giới hạn 1 refresh cho mỗi request, clear session khi retry vẫn 401 và thêm test HTTP. Các test còn xác nhận refresh thành công, single-flight 3 request, auth endpoint không nhận Bearer/không refresh, 429 dừng sau 3 lần retry.
3. **Flutter analyzer:** khai báo SDK dependency `flutter_web_plugins`, sửa deprecated form API, const, guard `BuildContext.mounted`, loại tham số private chưa dùng. Không thêm thư viện ứng dụng mới.
4. **CI credential:** workflow tạo env tạm từ Secret `BACKEND_ENV_PS1` rồi xóa trong cleanup. Thêm credential-file guard và job frontend analyze/test/build cho PR. Pages/runtime artifact chờ cả backend và frontend gate.

## 4. Điểm chặn còn mở

| Mức | Phát hiện | Cần hoàn thành |
|---|---|---|
| P0 | Repo cá nhân đã commit `.runtime/env.ps1` chứa credential Supabase, MongoDB, Redis/Valkey, S3, JWT và Grafana. `.gitignore`/revert không xóa secret khỏi lịch sử. | Thu hồi/đổi credential bị ảnh hưởng; cập nhật runtime và GitHub Secret mới. Không dán secret vào PR/chat. Bản tích hợp không có env thật nhưng credential cũ vẫn phải được xử lý. |
| P1 | OSV: 14 Critical, 72 High, 78 Moderate, 18 Low. Nhiều match thuộc Tomcat, Netty, Spring, Kafka và thư viện nén. | Nâng BOM Boot/Cloud theo cặp tương thích, xử lý transitive còn lại; đối chiếu điều kiện affected của từng advisory; scan và full test lại. Không coi mọi match là exploit đã tái hiện. |
| P1 | `MediaController.get` gọi `MediaService.get(id)` không truyền/kiểm tra viewer; service trả URL/object metadata cho người biết ID. MinIO mặc định khởi tạo public bucket, kể cả media CHAT. Cloud profile để policy cho provider, chưa xác minh live policy. | Thiết kế quyền đọc theo nội dung/conversation và URL private/signed; kiểm thử owner, người được phép, stranger và revoked access. Chưa tự đổi thành owner-only vì sẽ làm hỏng các luồng chia sẻ hợp lệ. |
| Gate | 6 PostgreSQL integration cases và E2E cloud/nghiệp vụ chưa chạy. | Chạy CI native PostgreSQL; tạo môi trường test dữ liệu cách ly để kiểm thử CRUD, chat/STOMP, notification/Kafka, upload/quyền media, restart/rollback và tải. |

Trong parent `bf8052e`, `.env.example:29` có URI dạng `user:password@...` mẫu, khác env cá nhân; không coi placeholder này là credential thật. Đây không phải scan hoàn chỉnh mọi secret từng xuất hiện trong tất cả refs.

## 5. Bằng chứng và lệnh chạy lại

Raw OSV response nằm local ở `.runtime/audit/team-handoff-2026-10-10/dependency-scan.json` trong checkout gốc; SHA-256 `ef9d4e5d3f03e11c1bf8216f5b12581f6d79d2792cb827795525246f79e3bf20`. [Danh sách phát hiện rút gọn](dependency-findings-2026-10-10.json) giữ ID, severity, alias và ranges/fixed mà OSV trả về.
Nguồn dữ liệu: [OSV querybatch API](https://google.github.io/osv.dev/post-v1-querybatch/). Chỉ tên/phiên bản package đã được gửi sau khi người dùng cho phép; không gửi source, tên repo hoặc credential.

```powershell
# Full JDK 17 / PostgreSQL native cách ly, hoặc chạy job CI đã chuẩn bị
mvn -B -Pnative-runtime verify
python -m unittest discover -s infra/azure/tests -v
python infra/audit/check_repository.py
cd frontend
flutter pub get --enforce-lockfile
flutter analyze
flutter test
flutter build web --release
```

Lệnh local dùng compiler JDK 21 và test JVM JRE 17: Maven `-Djvm=<java17.exe>` và `-DargLine=-javaagent:<byte-buddy-agent.jar>`; log cuối ở `.runtime/audit/backend-java17-agent.log` trong worktree audit. Maven `verify` được chạy trong worktree mới với output build riêng, không thay runtime JAR đang triển khai.

Không gọi đây là nghiệm thu E2E đầy đủ: chưa chạy database thật, backend cloud, browser nhiều người dùng, actual storage permissions, load/fault injection/restore. Không bật VM hoặc thay dữ liệu/credential cloud trong phiên này.

## 6. Inventory OSV đầy đủ theo gói/phiên bản

301 Maven runtime coordinates + 138 Pub hosted packages (gồm dev) + 1 PyYAML workflow. Không có match OSV cho Pub/PyYAML trong lần quét này. Không scan OS images, JDK, GitHub Action implementation hoặc hạ tầng provider. Bảng sau và JSON là kết quả advisory theo phiên bản; không phải phân tích reachability.

| Package | Version | Advisory IDs |
|---|---|---|
| `ch.qos.logback:logback-core` | `1.5.8` | [GHSA-25qh-j22f-pwp8](https://osv.dev/vulnerability/GHSA-25qh-j22f-pwp8), [GHSA-6v67-2wr5-gvf4](https://osv.dev/vulnerability/GHSA-6v67-2wr5-gvf4), [GHSA-jhq6-gfmj-v8fx](https://osv.dev/vulnerability/GHSA-jhq6-gfmj-v8fx), [GHSA-p47f-322f-whfh](https://osv.dev/vulnerability/GHSA-p47f-322f-whfh), [GHSA-pr98-23f8-jwxv](https://osv.dev/vulnerability/GHSA-pr98-23f8-jwxv), [GHSA-qqpg-mvqg-649v](https://osv.dev/vulnerability/GHSA-qqpg-mvqg-649v) |
| `com.fasterxml.jackson.core:jackson-core` | `2.17.2` | [GHSA-72hv-8253-57qq](https://osv.dev/vulnerability/GHSA-72hv-8253-57qq), [GHSA-7hhh-6rmp-j9qf](https://osv.dev/vulnerability/GHSA-7hhh-6rmp-j9qf), [GHSA-p6pp-m3f8-5c89](https://osv.dev/vulnerability/GHSA-p6pp-m3f8-5c89), [GHSA-r7wm-3cxj-wff9](https://osv.dev/vulnerability/GHSA-r7wm-3cxj-wff9) |
| `com.fasterxml.jackson.core:jackson-databind` | `2.17.2` | [GHSA-3pjw-73gf-8qr5](https://osv.dev/vulnerability/GHSA-3pjw-73gf-8qr5), [GHSA-5jmj-h7xm-6q6v](https://osv.dev/vulnerability/GHSA-5jmj-h7xm-6q6v), [GHSA-cxp5-3px4-pw24](https://osv.dev/vulnerability/GHSA-cxp5-3px4-pw24), [GHSA-gx83-3vf8-gh7j](https://osv.dev/vulnerability/GHSA-gx83-3vf8-gh7j), [GHSA-hgj6-7826-r7m5](https://osv.dev/vulnerability/GHSA-hgj6-7826-r7m5), [GHSA-j3rv-43j4-c7qm](https://osv.dev/vulnerability/GHSA-j3rv-43j4-c7qm), [GHSA-q4xh-88c3-wmh7](https://osv.dev/vulnerability/GHSA-q4xh-88c3-wmh7), [GHSA-rmj7-2vxq-3g9f](https://osv.dev/vulnerability/GHSA-rmj7-2vxq-3g9f), [GHSA-vvgp-rfg2-7rr6](https://osv.dev/vulnerability/GHSA-vvgp-rfg2-7rr6), [GHSA-wjgm-6hv5-3cvf](https://osv.dev/vulnerability/GHSA-wjgm-6hv5-3cvf), [GHSA-wv8q-qhhj-9h54](https://osv.dev/vulnerability/GHSA-wv8q-qhhj-9h54) |
| `com.fasterxml.woodstox:woodstox-core` | `6.2.1` | [GHSA-3f7h-mf4q-vrm4](https://osv.dev/vulnerability/GHSA-3f7h-mf4q-vrm4) |
| `com.thoughtworks.xstream:xstream` | `1.4.20` | [GHSA-hfq9-hggm-c56q](https://osv.dev/vulnerability/GHSA-hfq9-hggm-c56q) |
| `commons-configuration:commons-configuration` | `1.10` | [GHSA-pvp8-3xj6-8c6x](https://osv.dev/vulnerability/GHSA-pvp8-3xj6-8c6x) |
| `commons-fileupload:commons-fileupload` | `1.5` | [GHSA-vv7r-c36w-3prj](https://osv.dev/vulnerability/GHSA-vv7r-c36w-3prj) |
| `commons-io:commons-io` | `2.11.0` | [GHSA-78wr-2p64-hpwj](https://osv.dev/vulnerability/GHSA-78wr-2p64-hpwj) |
| `commons-lang:commons-lang` | `2.6` | [GHSA-j288-q9x7-2f5v](https://osv.dev/vulnerability/GHSA-j288-q9x7-2f5v) |
| `io.micrometer:micrometer-core` | `1.13.4` | [GHSA-g3pr-3p32-fp23](https://osv.dev/vulnerability/GHSA-g3pr-3p32-fp23) |
| `io.minio:minio` | `8.5.11` | [GHSA-h7rh-xfpj-hpcm](https://osv.dev/vulnerability/GHSA-h7rh-xfpj-hpcm) |
| `io.netty:netty-codec` | `4.1.113.Final` | [GHSA-3p8m-j85q-pgmj](https://osv.dev/vulnerability/GHSA-3p8m-j85q-pgmj), [GHSA-558v-64gr-wgg4](https://osv.dev/vulnerability/GHSA-558v-64gr-wgg4), [GHSA-mj4r-2hfc-f8p6](https://osv.dev/vulnerability/GHSA-mj4r-2hfc-f8p6) |
| `io.netty:netty-codec-dns` | `4.1.113.Final` | [GHSA-cm33-6792-r9fm](https://osv.dev/vulnerability/GHSA-cm33-6792-r9fm), [GHSA-mfg7-5gfp-c4w3](https://osv.dev/vulnerability/GHSA-mfg7-5gfp-c4w3) |
| `io.netty:netty-codec-http` | `4.1.113.Final` | [GHSA-38f8-5428-x5cv](https://osv.dev/vulnerability/GHSA-38f8-5428-x5cv), [GHSA-4mp9-239f-g9hg](https://osv.dev/vulnerability/GHSA-4mp9-239f-g9hg), [GHSA-57rv-r2g8-2cj3](https://osv.dev/vulnerability/GHSA-57rv-r2g8-2cj3), [GHSA-6cqp-g7gg-8hr5](https://osv.dev/vulnerability/GHSA-6cqp-g7gg-8hr5), [GHSA-6jqx-86gh-f27w](https://osv.dev/vulnerability/GHSA-6jqx-86gh-f27w), [GHSA-84h7-rjj3-6jx4](https://osv.dev/vulnerability/GHSA-84h7-rjj3-6jx4), [GHSA-8c42-7qj2-3j46](https://osv.dev/vulnerability/GHSA-8c42-7qj2-3j46), [GHSA-f6hv-jmp6-3vwv](https://osv.dev/vulnerability/GHSA-f6hv-jmp6-3vwv), [GHSA-fghv-69vj-qj49](https://osv.dev/vulnerability/GHSA-fghv-69vj-qj49), [GHSA-gcjf-9mgh-3p7g](https://osv.dev/vulnerability/GHSA-gcjf-9mgh-3p7g), [GHSA-hvcg-qmg6-jm4c](https://osv.dev/vulnerability/GHSA-hvcg-qmg6-jm4c), [GHSA-jppx-w49h-x2qq](https://osv.dev/vulnerability/GHSA-jppx-w49h-x2qq), [GHSA-m4cv-j2px-7723](https://osv.dev/vulnerability/GHSA-m4cv-j2px-7723), [GHSA-mvh2-crg5-v77c](https://osv.dev/vulnerability/GHSA-mvh2-crg5-v77c), [GHSA-pwqr-wmgm-9rr8](https://osv.dev/vulnerability/GHSA-pwqr-wmgm-9rr8), [GHSA-q4f6-jm68-57ww](https://osv.dev/vulnerability/GHSA-q4f6-jm68-57ww), [GHSA-v8h7-rr48-vmmv](https://osv.dev/vulnerability/GHSA-v8h7-rr48-vmmv), [GHSA-xxqh-mfjm-7mv9](https://osv.dev/vulnerability/GHSA-xxqh-mfjm-7mv9) |
| `io.netty:netty-codec-http2` | `4.1.113.Final` | [GHSA-563q-j3cm-6jxm](https://osv.dev/vulnerability/GHSA-563q-j3cm-6jxm), [GHSA-5x3r-wrvg-rp6q](https://osv.dev/vulnerability/GHSA-5x3r-wrvg-rp6q), [GHSA-93wv-jw9v-4972](https://osv.dev/vulnerability/GHSA-93wv-jw9v-4972), [GHSA-c2gf-v879-257j](https://osv.dev/vulnerability/GHSA-c2gf-v879-257j), [GHSA-c69g-56f8-xwqj](https://osv.dev/vulnerability/GHSA-c69g-56f8-xwqj), [GHSA-f6hv-jmp6-3vwv](https://osv.dev/vulnerability/GHSA-f6hv-jmp6-3vwv), [GHSA-prj3-ccx8-p6x4](https://osv.dev/vulnerability/GHSA-prj3-ccx8-p6x4), [GHSA-w9fj-cfpg-grvv](https://osv.dev/vulnerability/GHSA-w9fj-cfpg-grvv) |
| `io.netty:netty-common` | `4.1.113.Final` | [GHSA-389x-839f-4rhx](https://osv.dev/vulnerability/GHSA-389x-839f-4rhx), [GHSA-xq3w-v528-46rv](https://osv.dev/vulnerability/GHSA-xq3w-v528-46rv) |
| `io.netty:netty-handler` | `4.1.113.Final` | [GHSA-3qp7-7mw8-wx86](https://osv.dev/vulnerability/GHSA-3qp7-7mw8-wx86), [GHSA-4g8c-wm8x-jfhw](https://osv.dev/vulnerability/GHSA-4g8c-wm8x-jfhw), [GHSA-c4c3-7fpv-j4q5](https://osv.dev/vulnerability/GHSA-c4c3-7fpv-j4q5), [GHSA-c653-97m9-rcg9](https://osv.dev/vulnerability/GHSA-c653-97m9-rcg9), [GHSA-fccg-mwvh-qqg4](https://osv.dev/vulnerability/GHSA-fccg-mwvh-qqg4), [GHSA-x4gw-5cx5-pgmh](https://osv.dev/vulnerability/GHSA-x4gw-5cx5-pgmh) |
| `io.netty:netty-handler-proxy` | `4.1.113.Final` | [GHSA-45q3-82m4-75jr](https://osv.dev/vulnerability/GHSA-45q3-82m4-75jr) |
| `io.netty:netty-resolver-dns` | `4.1.113.Final` | [GHSA-5pvg-856g-cp85](https://osv.dev/vulnerability/GHSA-5pvg-856g-cp85), [GHSA-676x-f7gg-47vc](https://osv.dev/vulnerability/GHSA-676x-f7gg-47vc), [GHSA-xmv7-r254-6q78](https://osv.dev/vulnerability/GHSA-xmv7-r254-6q78) |
| `io.netty:netty-transport-native-epoll` | `4.1.113.Final` | [GHSA-w573-9ffj-6ff9](https://osv.dev/vulnerability/GHSA-w573-9ffj-6ff9) |
| `io.projectreactor.netty:reactor-netty-http` | `1.1.22` | [GHSA-4q2v-9p7v-3v22](https://osv.dev/vulnerability/GHSA-4q2v-9p7v-3v22) |
| `net.i2p.crypto:eddsa` | `0.3.0` | [GHSA-p53j-g8pw-4w5f](https://osv.dev/vulnerability/GHSA-p53j-g8pw-4w5f) |
| `org.apache.commons:commons-lang3` | `3.14.0` | [GHSA-j288-q9x7-2f5v](https://osv.dev/vulnerability/GHSA-j288-q9x7-2f5v) |
| `org.apache.httpcomponents.client5:httpclient5` | `5.3.1` | [GHSA-hjcp-jmpx-g3qm](https://osv.dev/vulnerability/GHSA-hjcp-jmpx-g3qm) |
| `org.apache.httpcomponents.core5:httpcore5` | `5.2.5` | [GHSA-hf6x-8p5f-cgmf](https://osv.dev/vulnerability/GHSA-hf6x-8p5f-cgmf) |
| `org.apache.httpcomponents.core5:httpcore5-h2` | `5.2.5` | [GHSA-v3jc-474w-2wm6](https://osv.dev/vulnerability/GHSA-v3jc-474w-2wm6) |
| `org.apache.httpcomponents:httpclient` | `4.5.3` | [GHSA-7r82-7xv7-xcpj](https://osv.dev/vulnerability/GHSA-7r82-7xv7-xcpj) |
| `org.apache.kafka:kafka-clients` | `3.7.1` | [GHSA-5qcv-4rpc-jp93](https://osv.dev/vulnerability/GHSA-5qcv-4rpc-jp93), [GHSA-vgq5-3255-v292](https://osv.dev/vulnerability/GHSA-vgq5-3255-v292), [GHSA-wf66-mphr-4c4r](https://osv.dev/vulnerability/GHSA-wf66-mphr-4c4r) |
| `org.apache.logging.log4j:log4j-api` | `2.23.1` | [GHSA-qv9r-c865-cp47](https://osv.dev/vulnerability/GHSA-qv9r-c865-cp47) |
| `org.apache.tomcat.embed:tomcat-embed-core` | `10.1.30` | [GHSA-23hv-mwm6-g8jf](https://osv.dev/vulnerability/GHSA-23hv-mwm6-g8jf), [GHSA-25xr-qj8w-c4vf](https://osv.dev/vulnerability/GHSA-25xr-qj8w-c4vf), [GHSA-27hp-xhwr-wr2m](https://osv.dev/vulnerability/GHSA-27hp-xhwr-wr2m), [GHSA-3p2h-wqq4-wf4h](https://osv.dev/vulnerability/GHSA-3p2h-wqq4-wf4h), [GHSA-42wg-hm62-jcwg](https://osv.dev/vulnerability/GHSA-42wg-hm62-jcwg), [GHSA-563x-q5rq-57qp](https://osv.dev/vulnerability/GHSA-563x-q5rq-57qp), [GHSA-5j33-cvvr-w245](https://osv.dev/vulnerability/GHSA-5j33-cvvr-w245), [GHSA-5m62-pw8w-7w9f](https://osv.dev/vulnerability/GHSA-5m62-pw8w-7w9f), [GHSA-5mp6-jrq3-r938](https://osv.dev/vulnerability/GHSA-5mp6-jrq3-r938), [GHSA-83qj-6fr2-vhqg](https://osv.dev/vulnerability/GHSA-83qj-6fr2-vhqg), [GHSA-9m3c-qcxr-9x87](https://osv.dev/vulnerability/GHSA-9m3c-qcxr-9x87), [GHSA-9m89-8frq-c98c](https://osv.dev/vulnerability/GHSA-9m89-8frq-c98c), [GHSA-9xv2-5v5q-p794](https://osv.dev/vulnerability/GHSA-9xv2-5v5q-p794), [GHSA-ff77-26x5-69cr](https://osv.dev/vulnerability/GHSA-ff77-26x5-69cr), [GHSA-fpj8-gq4v-p354](https://osv.dev/vulnerability/GHSA-fpj8-gq4v-p354), [GHSA-fv25-8xcx-gqjc](https://osv.dev/vulnerability/GHSA-fv25-8xcx-gqjc), [GHSA-gcx9-497g-6cp6](https://osv.dev/vulnerability/GHSA-gcx9-497g-6cp6), [GHSA-gqp3-2cvr-x8m3](https://osv.dev/vulnerability/GHSA-gqp3-2cvr-x8m3), [GHSA-gx5v-xp9w-j4cg](https://osv.dev/vulnerability/GHSA-gx5v-xp9w-j4cg), [GHSA-h2fw-rfh5-95r3](https://osv.dev/vulnerability/GHSA-h2fw-rfh5-95r3), [GHSA-h3gc-qfqq-6h8f](https://osv.dev/vulnerability/GHSA-h3gc-qfqq-6h8f), [GHSA-h3x4-894j-xpx5](https://osv.dev/vulnerability/GHSA-h3x4-894j-xpx5), [GHSA-h6fc-48rj-7qqh](https://osv.dev/vulnerability/GHSA-h6fc-48rj-7qqh), [GHSA-hgrr-935x-pq79](https://osv.dev/vulnerability/GHSA-hgrr-935x-pq79), [GHSA-mgp5-rv84-w37q](https://osv.dev/vulnerability/GHSA-mgp5-rv84-w37q), [GHSA-qvf5-hvjx-wm27](https://osv.dev/vulnerability/GHSA-qvf5-hvjx-wm27), [GHSA-r29c-68gh-xp6x](https://osv.dev/vulnerability/GHSA-r29c-68gh-xp6x), [GHSA-rv64-5gf8-9qq8](https://osv.dev/vulnerability/GHSA-rv64-5gf8-9qq8), [GHSA-vfww-5hm6-hx2j](https://osv.dev/vulnerability/GHSA-vfww-5hm6-hx2j), [GHSA-wc4r-xq3c-5cf3](https://osv.dev/vulnerability/GHSA-wc4r-xq3c-5cf3), [GHSA-wmwf-9ccg-fff5](https://osv.dev/vulnerability/GHSA-wmwf-9ccg-fff5), [GHSA-wr62-c79q-cv37](https://osv.dev/vulnerability/GHSA-wr62-c79q-cv37), [GHSA-x4m4-345f-5h5g](https://osv.dev/vulnerability/GHSA-x4m4-345f-5h5g) |
| `org.bouncycastle:bcprov-jdk18on` | `1.78` | [GHSA-574f-3g2m-x479](https://osv.dev/vulnerability/GHSA-574f-3g2m-x479), [GHSA-9pwp-9qqc-pr26](https://osv.dev/vulnerability/GHSA-9pwp-9qqc-pr26), [GHSA-c3fc-8qff-9hwx](https://osv.dev/vulnerability/GHSA-c3fc-8qff-9hwx), [GHSA-qp49-qgx5-5m26](https://osv.dev/vulnerability/GHSA-qp49-qgx5-5m26) |
| `org.eclipse.jgit:org.eclipse.jgit` | `6.6.1.202309021850-r` | [GHSA-vrpq-qp53-qv56](https://osv.dev/vulnerability/GHSA-vrpq-qp53-qv56) |
| `org.freemarker:freemarker` | `2.3.33` | [GHSA-27j2-h3m2-8237](https://osv.dev/vulnerability/GHSA-27j2-h3m2-8237) |
| `org.lz4:lz4-java` | `1.8.0` | [GHSA-343h-94h5-c4wr](https://osv.dev/vulnerability/GHSA-343h-94h5-c4wr), [GHSA-4v53-57pg-c464](https://osv.dev/vulnerability/GHSA-4v53-57pg-c464), [GHSA-6cx8-rjf8-pr8g](https://osv.dev/vulnerability/GHSA-6cx8-rjf8-pr8g), [GHSA-cmp6-m4wj-q63q](https://osv.dev/vulnerability/GHSA-cmp6-m4wj-q63q), [GHSA-gm45-99xc-r7wv](https://osv.dev/vulnerability/GHSA-gm45-99xc-r7wv), [GHSA-mcr4-qmvw-px4g](https://osv.dev/vulnerability/GHSA-mcr4-qmvw-px4g), [GHSA-vqf4-7m7x-wgfc](https://osv.dev/vulnerability/GHSA-vqf4-7m7x-wgfc), [GHSA-xx22-p4ch-683r](https://osv.dev/vulnerability/GHSA-xx22-p4ch-683r) |
| `org.postgresql:postgresql` | `42.7.4` | [GHSA-98qh-xjc8-98pq](https://osv.dev/vulnerability/GHSA-98qh-xjc8-98pq), [GHSA-hq9p-pm7w-8p54](https://osv.dev/vulnerability/GHSA-hq9p-pm7w-8p54), [GHSA-j92g-9f8w-j867](https://osv.dev/vulnerability/GHSA-j92g-9f8w-j867) |
| `org.springframework.boot:spring-boot` | `3.3.4` | [GHSA-rc42-6c7j-7h5r](https://osv.dev/vulnerability/GHSA-rc42-6c7j-7h5r), [GHSA-wwpq-f5c3-7hvx](https://osv.dev/vulnerability/GHSA-wwpq-f5c3-7hvx) |
| `org.springframework.boot:spring-boot-autoconfigure` | `3.3.4` | [GHSA-ggg2-9786-hwc8](https://osv.dev/vulnerability/GHSA-ggg2-9786-hwc8) |
| `org.springframework.boot:spring-boot-starter-actuator` | `3.3.4` | [GHSA-mgvc-8q2h-5pgc](https://osv.dev/vulnerability/GHSA-mgvc-8q2h-5pgc) |
| `org.springframework.cloud:spring-cloud-config-server` | `4.1.3` | [GHSA-2mh5-3cw6-hrrq](https://osv.dev/vulnerability/GHSA-2mh5-3cw6-hrrq), [GHSA-3qwq-q9vm-5j42](https://osv.dev/vulnerability/GHSA-3qwq-q9vm-5j42), [GHSA-6g23-24mc-hx6x](https://osv.dev/vulnerability/GHSA-6g23-24mc-hx6x), [GHSA-86wq-234q-r6wg](https://osv.dev/vulnerability/GHSA-86wq-234q-r6wg), [GHSA-j6hh-h3cf-c2hf](https://osv.dev/vulnerability/GHSA-j6hh-h3cf-c2hf) |
| `org.springframework.cloud:spring-cloud-gateway-server` | `4.1.5` | [GHSA-6j2q-c73v-97c5](https://osv.dev/vulnerability/GHSA-6j2q-c73v-97c5), [GHSA-fwxx-wv44-7qfg](https://osv.dev/vulnerability/GHSA-fwxx-wv44-7qfg) |
| `org.springframework.data:spring-data-commons` | `3.3.4` | [GHSA-5m4m-73w9-8433](https://osv.dev/vulnerability/GHSA-5m4m-73w9-8433), [GHSA-5vpf-xvv7-c8vh](https://osv.dev/vulnerability/GHSA-5vpf-xvv7-c8vh), [GHSA-9fw2-h3hf-293r](https://osv.dev/vulnerability/GHSA-9fw2-h3hf-293r) |
| `org.springframework.data:spring-data-keyvalue` | `3.3.4` | [GHSA-xg2j-3hj6-pc24](https://osv.dev/vulnerability/GHSA-xg2j-3hj6-pc24) |
| `org.springframework.data:spring-data-mongodb` | `4.3.4` | [GHSA-5whc-4q84-fj73](https://osv.dev/vulnerability/GHSA-5whc-4q84-fj73), [GHSA-hc43-m36c-8v33](https://osv.dev/vulnerability/GHSA-hc43-m36c-8v33) |
| `org.springframework.kafka:spring-kafka` | `3.2.4` | [GHSA-53w6-v7cv-fc9h](https://osv.dev/vulnerability/GHSA-53w6-v7cv-fc9h), [GHSA-xq69-5h5v-x9x4](https://osv.dev/vulnerability/GHSA-xq69-5h5v-x9x4), [GHSA-xvfq-4q6q-gxx7](https://osv.dev/vulnerability/GHSA-xvfq-4q6q-gxx7) |
| `org.springframework.retry:spring-retry` | `2.0.9` | [GHSA-2827-2mxx-j8pv](https://osv.dev/vulnerability/GHSA-2827-2mxx-j8pv) |
| `org.springframework.security:spring-security-crypto` | `6.3.3` | [GHSA-mg83-c7gq-rv5c](https://osv.dev/vulnerability/GHSA-mg83-c7gq-rv5c) |
| `org.springframework:spring-context` | `6.1.13` | [GHSA-4gc7-5j7h-4qph](https://osv.dev/vulnerability/GHSA-4gc7-5j7h-4qph), [GHSA-4wp7-92pw-q264](https://osv.dev/vulnerability/GHSA-4wp7-92pw-q264) |
| `org.springframework:spring-core` | `6.1.13` | [GHSA-659m-px2c-25wj](https://osv.dev/vulnerability/GHSA-659m-px2c-25wj), [GHSA-jmp9-x22r-554x](https://osv.dev/vulnerability/GHSA-jmp9-x22r-554x) |
| `org.springframework:spring-expression` | `6.1.13` | [GHSA-9f52-rjqv-25qv](https://osv.dev/vulnerability/GHSA-9f52-rjqv-25qv), [GHSA-r5w3-xv2f-j59q](https://osv.dev/vulnerability/GHSA-r5w3-xv2f-j59q), [GHSA-wxpp-56q6-5pcg](https://osv.dev/vulnerability/GHSA-wxpp-56q6-5pcg) |
| `org.springframework:spring-web` | `6.1.13` | [GHSA-4gc7-5j7h-4qph](https://osv.dev/vulnerability/GHSA-4gc7-5j7h-4qph), [GHSA-6r3c-xf4w-jxjm](https://osv.dev/vulnerability/GHSA-6r3c-xf4w-jxjm) |
| `org.springframework:spring-webflux` | `6.1.13` | [GHSA-4773-3jfm-qmx3](https://osv.dev/vulnerability/GHSA-4773-3jfm-qmx3), [GHSA-4hfh-6x8g-gwpp](https://osv.dev/vulnerability/GHSA-4hfh-6x8g-gwpp), [GHSA-5843-p793-ghmm](https://osv.dev/vulnerability/GHSA-5843-p793-ghmm), [GHSA-6hcq-hmm3-jj3c](https://osv.dev/vulnerability/GHSA-6hcq-hmm3-jj3c), [GHSA-6p4f-wcwh-5vvm](https://osv.dev/vulnerability/GHSA-6p4f-wcwh-5vvm), [GHSA-72pg-x5f8-j25j](https://osv.dev/vulnerability/GHSA-72pg-x5f8-j25j), [GHSA-83f7-v6px-pp3h](https://osv.dev/vulnerability/GHSA-83f7-v6px-pp3h), [GHSA-9qf2-26p9-2q2q](https://osv.dev/vulnerability/GHSA-9qf2-26p9-2q2q), [GHSA-cjpg-rgq5-fr37](https://osv.dev/vulnerability/GHSA-cjpg-rgq5-fr37), [GHSA-g5vr-rgqm-vf78](https://osv.dev/vulnerability/GHSA-g5vr-rgqm-vf78), [GHSA-h3qp-gqrc-q736](https://osv.dev/vulnerability/GHSA-h3qp-gqrc-q736), [GHSA-mq64-j8f9-9gcj](https://osv.dev/vulnerability/GHSA-mq64-j8f9-9gcj), [GHSA-wg35-8jpf-2xv3](https://osv.dev/vulnerability/GHSA-wg35-8jpf-2xv3), [GHSA-x23c-287f-qqv5](https://osv.dev/vulnerability/GHSA-x23c-287f-qqv5) |
| `org.springframework:spring-webmvc` | `6.1.13` | [GHSA-3chg-m5w7-qfv5](https://osv.dev/vulnerability/GHSA-3chg-m5w7-qfv5), [GHSA-4773-3jfm-qmx3](https://osv.dev/vulnerability/GHSA-4773-3jfm-qmx3), [GHSA-6hcq-hmm3-jj3c](https://osv.dev/vulnerability/GHSA-6hcq-hmm3-jj3c), [GHSA-6p4f-wcwh-5vvm](https://osv.dev/vulnerability/GHSA-6p4f-wcwh-5vvm), [GHSA-72pg-x5f8-j25j](https://osv.dev/vulnerability/GHSA-72pg-x5f8-j25j), [GHSA-957g-f97v-vppc](https://osv.dev/vulnerability/GHSA-957g-f97v-vppc), [GHSA-cjpg-rgq5-fr37](https://osv.dev/vulnerability/GHSA-cjpg-rgq5-fr37), [GHSA-g5vr-rgqm-vf78](https://osv.dev/vulnerability/GHSA-g5vr-rgqm-vf78), [GHSA-h3qp-gqrc-q736](https://osv.dev/vulnerability/GHSA-h3qp-gqrc-q736), [GHSA-mq64-j8f9-9gcj](https://osv.dev/vulnerability/GHSA-mq64-j8f9-9gcj), [GHSA-pc63-qcmh-9cmg](https://osv.dev/vulnerability/GHSA-pc63-qcmh-9cmg), [GHSA-r936-gwx5-v52f](https://osv.dev/vulnerability/GHSA-r936-gwx5-v52f), [GHSA-wg35-8jpf-2xv3](https://osv.dev/vulnerability/GHSA-wg35-8jpf-2xv3), [GHSA-x23c-287f-qqv5](https://osv.dev/vulnerability/GHSA-x23c-287f-qqv5) |
| `org.springframework:spring-websocket` | `6.1.13` | [GHSA-7fch-4f2f-jcgm](https://osv.dev/vulnerability/GHSA-7fch-4f2f-jcgm), [GHSA-q723-847q-5g8g](https://osv.dev/vulnerability/GHSA-q723-847q-5g8g) |
