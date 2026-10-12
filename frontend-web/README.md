# Frontend Web chính thức — Next.js

Thư mục dành cho toàn bộ UI/UX web chính thức của đồ án Social Media.

- Công nghệ: Next.js App Router, React, TypeScript, Tailwind CSS, Radix UI và Motion; kế thừa phiên bản trong source hiện tại.
- Source đã copy từ: `D:\SocialMedia`; phát triển UI tiếp tại `frontend-web/`.
- `../frontend/` là bản demo Flutter; frontend web chính thức phát triển tại đây.
- Người phụ trách UI hoàn thiện màn hình, responsive, tương tác và dữ liệu demo. Các thành viên phụ trách tích hợp sẽ nối API, xác thực, upload và WebSocket của backend.

## Trạng thái ngày 12/10/2026

Đã copy toàn bộ phần Next.js UI, cấu hình, lockfile, assets, bộ test, tài liệu và ảnh kiểm thử liên quan: **428 file đã đối chiếu SHA-256 khớp nguồn**. Source `D:\SocialMedia` được giữ nguyên. Không thay đổi hành vi hoặc giao diện ứng dụng trong lần copy này.

Danh sách file/checksum: [docs/source-copy-manifest.json](./docs/source-copy-manifest.json). Các file kết quả test và screenshot có thể được sinh lại khi chạy kiểm tra; manifest ghi nhận thời điểm copy.

README nguyên bản được giữ tại [README.source.md](./README.source.md). Các báo cáo `UI_REVIEW.md`, `UI_UX_PLAN.md`, `QA_SOCIAL_PLATFORM.md` và kết quả QA đã copy là tài liệu lịch sử, không phải kết quả kiểm tra mới tại đích.

Đọc [MIGRATION.md](./MIGRATION.md) để xem danh sách chuyển, thứ tự thực hiện, tiêu chí kiểm tra và hướng bàn giao.

## Chạy ứng dụng

Cần Node.js 20.9 trở lên và npm. Chạy từ thư mục này:

```powershell
rtk proxy npm.cmd ci
rtk proxy npm.cmd run dev
```

Mở `http://127.0.0.1:3000`. Nếu cổng đang được dùng, thêm `-- --port 3002` vào lệnh dev. Để chạy production:

```powershell
rtk proxy npm.cmd run build
rtk proxy npm.cmd run start -- --port 3002
```

Mở `http://127.0.0.1:3002`. Server frontend này chạy độc lập với Docker Compose đang có.

## Kiểm tra

```powershell
rtk proxy npm.cmd run lint
rtk proxy npm.cmd run typecheck
rtk proxy npm.cmd run build
```

Kiểm tra trình duyệt cần Chrome, Python với gói `websockets` và server Next.js đang chạy. Chỉ định đúng cổng server qua `BASE_URL`:

```powershell
$env:BASE_URL = "http://127.0.0.1:3002"
rtk proxy python tests/test_next.py
rtk proxy python tests/test_login.py
```

Đọc [tests/README.md](./tests/README.md) để chạy từng nhóm. Kết quả kiểm tra tại đích được ghi trong [MIGRATION.md](./MIGRATION.md).

Kiểm tra ngày 12/10/2026: production build, lint, typecheck và bộ login đạt; browser suite đạt **16/17 nhóm**, **140/140 trường hợp giao diện**, **4/4 kiểm tra sidebar**. Nhóm persistence còn lỗi dữ liệu khi reload ngay sau thao tác; chưa xác định nguyên nhân và chưa sửa code trong lần copy. Xem [báo cáo lượt đầy đủ](./tests/migration_full_results.json) và phần kiểm tra trong MIGRATION.md.

## Cấu trúc

```text
frontend-web/
├── src/
│   ├── app/              # Route, layout và stylesheet hiện có
│   ├── components/       # Màn hình, tương tác và component UI dùng chung
│   └── lib/              # Kiểu dữ liệu UI, dữ liệu demo, storage và helper
├── public/assets/        # Asset đang được source sử dụng
├── tests/                # Bộ kiểm tra Next.js và login
├── browser_checks.py     # Browser transport mà bộ test hiện tại import
├── docs/                 # Manifest của lần copy; bổ sung đặc tả khi làm UI
├── screenshots/          # Ảnh Next.js đã copy và ảnh do test tạo (Git ignore)
├── package.json
├── package-lock.json
├── next.config.ts
├── tsconfig.json
├── components.json
├── eslint.config.mjs
├── postcss.config.mjs
├── AGENTS.md
├── README.source.md
├── README.md
└── MIGRATION.md
```

Không copy `node_modules/`, `.next/`, cache Python/TypeScript hoặc bản HTML/JavaScript cũ ở root. Dependency được cài lại từ lockfile; Next.js tự sinh declaration/build output. Các stylesheet có chữ “legacy” bên trong `src/app/` vẫn được copy đầy đủ vì Next.js đang dùng chúng.

UI hiện dùng dữ liệu demo và IndexedDB; login, tin nhắn, báo cáo và quyền riêng tư chưa nối backend thật. Dữ liệu trình duyệt thuộc hostname/cổng, không tự chuyển khi chạy ứng dụng trên origin khác.

## Nguyên tắc phát triển

1. Chuyển source đang chạy trước, giữ giao diện và hành vi hiện tại; nâng cấp từng phần sau khi kiểm tra bản chuyển.
2. Giữ cấu trúc và dependency đang có. Chưa cần thêm workspace manager, framework hoặc tầng abstraction mới.
3. UI phải xem và thao tác được bằng dữ liệu demo khi backend chưa chạy. Ghi rõ thao tác nào còn mô phỏng.
4. Dữ liệu và quyền hạn thực tế do backend quyết định. Không coi demo login, IndexedDB hoặc việc ẩn nút là xác thực/phân quyền thật.
5. Khi chuyển source xong, đây sẽ là nơi phát triển chính; `D:\SocialMedia` giữ làm bản tham chiếu và phục hồi, tránh sửa song song hai bản.
6. Việc thay frontend Flutter trong Docker/CI là bước tích hợp riêng, sau khi Next.js đã được kiểm tra và nhóm triển khai nhận bàn giao.
