# Kế hoạch chuyển UI sang frontend-web

Ngày khảo sát: 12/10/2026. Nguồn: `D:\SocialMedia`. Đích: thư mục `frontend-web/` trong đồ án chính.

## Phạm vi và trạng thái

| Bước | Trạng thái |
| --- | --- |
| 0. Tạo thư mục Next.js chính thức và kế hoạch | Đã thực hiện |
| 1. Chuyển bản UI Next.js hiện tại chạy độc lập | Đã copy 428 file khớp SHA-256; build/lint/typecheck đạt, browser QA 16/17 nhóm đạt; còn lỗi persistence khi reload ngay |
| 2. Hoàn thiện UI theo chức năng đồ án | Đề xuất, chưa thực hiện |
| 3. Bàn giao đặc tả nối backend | Đề xuất, chưa thực hiện |
| 4. Nhóm tích hợp nối dữ liệu thật và triển khai | Việc của nhóm tích hợp |

Không chuyển code Flutter sang React. Source Next.js hiện tại là nền tảng UI chính; Flutter và backend được tham khảo để hiểu nghiệp vụ.

Theo yêu cầu copy toàn bộ Next.js UI, lần chuyển này giữ tài liệu QA tại root cùng vị trí tương đối như source, lưu README nguồn thành `README.source.md`, và copy cả kết quả/ảnh QA lịch sử của Next.js. Không refactor mã ứng dụng hoặc triển khai chức năng mới. Manifest lần copy nằm tại `docs/source-copy-manifest.json`.

## Kiểm tra bản copy ngày 12/10/2026

- `npm ci --no-audit --no-fund`: cài 447 package từ lockfile, không nâng dependency.
- Production build bằng `node node_modules/next/dist/bin/next build`: đạt, gồm bước TypeScript và sinh route.
- `npm run lint` và `npm run typecheck`: đạt. Lượt gọi npm ban đầu gặp lỗi khởi chạy trên Windows; các lượt lint/typecheck và start sau chạy bình thường, không đổi script/config để khắc phục.
- `npm run start -- --port 3002`: chạy được tại `http://127.0.0.1:3002`; trang login trả HTTP 200.
- Bộ login: đạt responsive tại 1440, 1024, 820, 390 và 320px, cùng validation, demo flow, focus, privacy và reduced motion. Log: `tests/migration_login_qa.log`.
- Lượt browser QA đầy đủ: **16/17 nhóm đạt**, **140/140 trường hợp route/viewport/theme đạt**, **4/4 kiểm tra sidebar đạt**. Báo cáo đã giữ riêng tại `tests/migration_full_results.json`; log: `tests/migration_qa_complete.log`. Lượt trước bị ngắt khi phiên dừng, không tính là lượt đầy đủ.
- Nhóm `persistence` chưa đạt: trong lượt đầy đủ, tin nhắn không xuất hiện sau gửi rồi reload ngay; lần chạy riêng tiếp theo lỗi ở kiểm tra following sau reload, lần cuối lại lỗi ở tin nhắn. Giữ báo cáo/log các lượt kiểm tra; chưa coi full suite là pass.
- Đối chiếu cùng nhóm persistence với bản nguồn chạy production tại cổng 3003: đạt (`tests/migration_source_persistence_results.json`). Server nguồn dùng build sẵn, không build lại nguồn. Khác biệt về build/môi trường và thời điểm hoàn tất ghi IndexedDB cần kiểm tra thêm; **chưa xác định nguyên nhân**. Không sửa source hoặc bộ test để làm lần copy này đạt QA.
- Kiểm tra SHA-256 sau chạy: **181 file ổn định** của source, assets, cấu hình, tài liệu và bộ test vẫn khớp manifest ở cả nguồn lẫn đích. Screenshot và `tests/next_results.json` được test sinh lại; manifest vẫn ghi nhận lần copy ban đầu.
- Git xác nhận chỉ thêm file trong `frontend-web/`, không sửa file đã có của đồ án. Chưa commit/push hoặc thay Docker Compose.

Đã hoàn thành yêu cầu copy, giữ nguyên UI hiện có. Cần xử lý/kiểm tra giới hạn persistence trước khi tuyên bố bản đích đạt toàn bộ QA. Backend vẫn chưa được nối.

## Những gì source Next.js đang có

- App Router, shell, dark/light theme, UI primitives, composer, post card/detail, comment/reply, reaction và media viewer.
- Feed, login demo, messages, Explore/discussion, friends, notifications, stories, profile và settings.
- Trạng thái dùng chung ở `src/components/social-provider.tsx`; dữ liệu mẫu ở `src/lib/data.ts` và provider; kiểu dữ liệu ở `src/lib/types.ts`.
- Dữ liệu demo, draft và Blob được lưu bằng IndexedDB trong `src/lib/social-storage.ts`; theme lưu bằng localStorage.
- CSS hiện còn phụ thuộc `src/app/legacy-layout.css`, `src/app/assets.css` và `public/assets/`. Các file có chữ “legacy” này vẫn phục vụ app Next.js.
- Bộ test Next.js: `tests/test_next.py`, `tests/test_login.py`; phụ thuộc module `browser_checks.py` ở root, Python `websockets` và Chrome.

Tài liệu nguồn ghi nhận các lần QA trước đây. Những kết quả đó chỉ là mốc tham khảo, không thay cho kiểm tra bản chuyển ở đường dẫn mới.

## Bước 1 — Chuyển một bản đầy đủ, giữ nguyên hành vi

Chuyển trọn phần Next.js cần để chạy, sau đó mới thay đổi từng tính năng. Không copy rời từng màn hình khi shell, provider, assets và stylesheet vẫn phụ thuộc nhau.

### Danh sách cần chuyển

| Nguồn tại D:\SocialMedia | Xử lý |
| --- | --- |
| `src/`, `public/` | Copy nguyên trạng; giữ cả CSS và ảnh tham chiếu đang được dùng |
| `package.json`, `package-lock.json` | Copy cùng nhau; chưa nâng dependency |
| `next.config.ts`, `tsconfig.json` | Giữ alias, route redirect và cấu hình hiện có |
| `eslint.config.mjs`, `postcss.config.mjs`, `components.json` | Giữ toolchain và cấu hình component |
| `AGENTS.md` | Giữ hướng dẫn đọc tài liệu Next.js theo phiên bản đã cài |
| `tests/test_next.py`, `tests/test_login.py`, `tests/README.md`, `browser_checks.py` | Chuyển đủ để test không phụ thuộc ngược vào source cũ |
| `UI_UX_PLAN.md`, `UI_REVIEW.md`, `QA_SOCIAL_PLATFORM.md` | Đã copy nguyên trạng tại root để giữ các đường dẫn tương đối; đây là tài liệu lịch sử |
| README nguồn | Đã lưu nguyên trạng thành `README.source.md`; README đích có hướng dẫn chạy tại thư mục mới |
| Kết quả/log trong `tests/`, ảnh `screenshots/next_*` | Đã copy để giữ bằng chứng lịch sử; test có thể tạo lại kết quả/ảnh, output phát sinh được Git ignore |

Không copy `node_modules/`, `.next/`, `__pycache__/`, `tsconfig.tsbuildinfo`, file môi trường hoặc toàn bộ log/screenshot cũ. `next-env.d.ts` được Next.js sinh lại. Giữ bộ HTML/JS cũ (`index.html`, `app.js`, `screens.js`, CSS/assets ở root và test vanilla) tại nguồn; bộ test Next.js chỉ dùng Browser transport từ `browser_checks.py`, không chạy app HTML đó.

### Thứ tự và tiêu chí hoàn thành

1. Ghi nhận danh sách/checksum file nguồn của lần chuyển. Tạm ngừng chỉnh source trong lúc copy và kiểm tra checksum để phát hiện bản chuyển không đầy đủ.
2. Copy theo danh sách trên; không refactor hay đổi thương hiệu trong cùng bước.
3. Tại đích, dùng Node.js đáp ứng `engines` của bản Next.js trong lockfile; chạy `npm ci` từ lockfile đã chuyển. Không copy dependency đã cài từ nguồn.
4. Chạy lint, typecheck và production build. Chạy app đích trên một cổng còn trống; có thể dùng `npm run start -- --port 3002` nếu 3002 khả dụng. Đây là cổng kiểm tra tạm, chưa phải cấu hình triển khai.
5. Đặt `BASE_URL` theo app đích và chạy đủ nhóm `tests/test_next.py`, sau đó `tests/test_login.py`. Các suite cần server chạy trước. Kiểm tra route trực tiếp, reload/Back, theme, 320px/mobile, draft, media và lưu dữ liệu.
6. Chụp lại các màn hình tiêu biểu trên desktop/mobile; so với bản nguồn cùng dữ liệu mẫu. Đối chiếu asset, layout, focus và các tương tác quan trọng.
7. Chỉ công nhận bản chuyển khi các kiểm tra đạt. Sau đó phát triển tại `frontend-web/`, giữ `D:\SocialMedia` làm bản tham chiếu; lưu mốc Git của bản chuyển trong repository đồ án.

Lưu trữ trình duyệt thuộc origin, bao gồm hostname và cổng. Chạy đích trên cổng khác sẽ có dữ liệu demo riêng; đây không phải mất dữ liệu của nguồn. Giữ nguyên storage key/schema ở bước 1 và không xóa database. Chuyển dữ liệu demo cá nhân sang origin khác, nếu được yêu cầu, là việc riêng; không tự chuyển chúng thành tài khoản/dữ liệu thật.

### Phục hồi

Nếu bản đích lỗi, tiếp tục dùng source `D:\SocialMedia`, đối chiếu checksum và sửa bản đích. Sau khi có mốc Git, dùng mốc đó để khôi phục thay đổi của từng bước. Không xóa source gốc, dữ liệu trình duyệt hay frontend Flutter trong giai đoạn chuyển.

## Bước 2 — Hoàn thiện full UI từng nhóm

Mỗi nhóm hoàn thành cả màn hình, dialog, trạng thái dữ liệu, responsive và tương tác trước khi sang nhóm kế tiếp. Dùng lại component hiện có; chỉ tách `screens.tsx` hoặc provider khi thay đổi thực tế cần đến, không refactor toàn ứng dụng trước.

| Thứ tự | Nhóm UI | Cách tiếp cận |
| --- | --- | --- |
| A | Shell, điều hướng, theme và tài khoản | Giữ phong cách UI hiện tại; thống nhất nhãn, logo/ngôn ngữ theo đồ án; bổ sung UI đăng ký và trạng thái phiên; ghi rõ login còn demo |
| B | Feed, post, comment, reaction, share, media | Kế thừa UI hiện có; bổ sung quyền riêng tư/tag/ghim theo nghiệp vụ; phân biệt chia sẻ vào chat và repost |
| C | Profile, bạn bè, follow, saved, notifications, search | Hoàn thiện nội dung, trạng thái quan hệ và các danh sách; saved hiện nằm trong profile, cần chốt có cần route riêng; search tổng hợp là phần cần bổ sung |
| D | Nhóm và Fanpage | Explore/discussion có thể tái dùng UI phù hợp, nhưng không tự coi topic là group post; bổ sung chi tiết nhóm/trang, thành viên và các vai trò |
| E | Stories và Reels | Giữ viewer/story hiện có; bổ sung UI tạo nội dung và feed Reels còn thiếu |
| F | Chat, Dating và Moderation | Hoàn thiện chat từ source hiện có; bổ sung trạng thái kết nối/gửi lỗi; dựng Dating và màn hình admin/report theo nghiệp vụ |
| G | Settings và nghiệm thu toàn bộ | Phân biệt tùy chọn chỉ trên thiết bị với dữ liệu tài khoản; rà soát toàn bộ route, accessibility và responsive |

Mỗi phần phải có dữ liệu mẫu cho các trạng thái cần thiết: đang tải, có dữ liệu, rỗng, lỗi và thử lại, đang gửi, gửi thất bại, thành công và thiếu quyền. Chỉ hiển thị trạng thái có ý nghĩa với màn hình; không tạo lựa chọn chưa được yêu cầu.

Mỗi thay đổi cần cập nhật kiểm tra hành vi liên quan. Khi cả nhóm hoàn thành, chạy lint/typecheck/build và hồi quy phù hợp; khi chốt toàn bộ UI, chạy lại full suite cùng kiểm tra thủ công các màn hình mới chưa được suite bao phủ.

## Những khác biệt phải ghi rõ trước khi nối API

| Hiện tại ở Next.js | Backend/nghiệp vụ cần đối chiếu | Hướng chuẩn bị bàn giao |
| --- | --- | --- |
| Profile/messages dùng tên người trong URL hoặc khóa dữ liệu; `Person` chưa có ID | Backend dùng ID người dùng và ID hội thoại | Nhóm tích hợp ánh xạ ID; UI cần hỗ trợ tên trùng nhau. Chưa đổi route/schema trong bản chuyển đầu |
| Post/comment/message là kiểu dữ liệu demo; comment nằm trong bài | Backend trả DTO, phân trang và tài nguyên riêng | Ghi field mapping, dữ liệu bắt buộc, ID cha/con, thời gian và pagination theo từng màn hình |
| Audience hiện là giá trị UI dạng chuỗi | Backend có PUBLIC/FRIENDS/CUSTOM/PRIVATE và danh sách người xem | Ánh xạ rõ nhãn UI sang enum và ID; backend thực thi quyền xem |
| Share gửi snapshot bài vào Messages | Backend có share/repost lên feed | Mô tả hai thao tác riêng; chỉ triển khai/nối những luồng nhóm xác nhận có hỗ trợ |
| Media là Blob/object URL cục bộ | Backend cần upload rồi trả URL/ID media | UI có trạng thái upload/thất bại/thử lại; nhóm tích hợp không gửi `blob:` URL tới backend |
| Explore dùng category/topic và membership demo | Group/Fanpage có entity, quyền và trạng thái thành viên riêng | Chốt mapping theo chức năng; không đổi tên dữ liệu để giả lập tương đương nghiệp vụ |
| Demo login, local message/report/notification | Auth JWT, REST, moderation và STOMP thật | Ghi rõ giới hạn demo; nhóm tích hợp thay handler và dữ liệu từng tính năng |
| SocialProvider và IndexedDB lưu cả dữ liệu xã hội | Backend là nguồn dữ liệu thật khi tích hợp | Chỉ giữ theme/draft cục bộ khi phù hợp; tránh để snapshot demo ghi đè dữ liệu API |

Không tạo sẵn tầng API lớn hoặc thêm thư viện chỉ để chuẩn bị. Khi hoàn thiện từng tính năng, ghi rõ điểm cấp dữ liệu và handler thao tác để nhóm tích hợp thay thế mà giữ component trình bày ổn định.

## Bước 3 — Bộ tài liệu bàn giao

Tạo và cập nhật tài liệu cùng lúc hoàn thiện từng nhóm, thay vì đợi đến cuối:

- Danh sách route/màn hình/dialog, vai trò sử dụng và liên kết tới component chính.
- Mỗi thao tác: dữ liệu đầu vào, kiểm tra UI, trạng thái chờ/lỗi/thành công, quyền cần có và kết quả mong đợi.
- Bảng field UI ↔ DTO backend, endpoint/phương thức, pagination, multipart upload và các trường hợp API còn thiếu hoặc chưa thống nhất.
- Với chat/thông báo: điểm kết nối, subscription, payload, reconnect và cách UI phản ánh trạng thái. Source backend hiện dùng `/ws`, `/ws-notifications`, `/user/queue/messages`, `/user/queue/notifications` và `/app/chat.send`; nhóm tích hợp phải kiểm tra lại contract khi thực hiện.
- Hướng dẫn chạy UI độc lập, dữ liệu demo, kiểm tra, ảnh tham chiếu và giới hạn còn lại.

Đầu ra UI được nghiệm thu bằng giao diện/tương tác trên dữ liệu demo. Xác thực thật, kiểm tra quyền trên server, lưu dữ liệu thật và real-time end-to-end do nhóm tích hợp kiểm tra riêng.

## Bước 4 — Nhóm tích hợp thực hiện

Nối theo chức năng: auth/profile → feed/post/media → comment/reaction/friend/group/page → chat/notification → các phần còn lại. Mỗi nhóm thay nguồn dữ liệu demo và handler bằng API thật, kiểm tra contract và luồng lỗi rồi mới triển khai tiếp. Không tự chuyển dữ liệu IndexedDB demo lên backend.

Docker Compose hiện vẫn phục vụ Flutter tại cổng 3001. Chỉ thay bằng Next.js sau khi có build và cách chạy Next.js đã kiểm tra, biến môi trường cho URL gateway, hướng dẫn và đường phục hồi. Khi đó cập nhật đồng bộ Docker/Compose, CI, README root và hướng dẫn khởi động; không dùng cấu hình serve Flutter bằng nginx nguyên trạng cho Next.js.
