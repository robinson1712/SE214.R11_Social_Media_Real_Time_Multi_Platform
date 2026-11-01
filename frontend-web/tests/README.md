# Kiểm tra UI đã nối backend

## Bộ hiện hành

1. `npm test` / `npm run test:unit`: Node24 `node:test`, HTTP fixture thực và WebSocket/STOMP; không cần test framework mới.
2. `npm run lint`, `npm run typecheck`, `npm run build`: ESLint, TypeScript và production Next Webpack.
3. `npm run test:connected`: Chrome headless với HTTP/STOMP fixture có trạng thái, ba module posts/screens/chat; hai profile cho chat, không seed session hoặc React internals.

Build riêng cho browser fixture trước khi chạy:

```powershell
$env:GATEWAY_UPSTREAM='http://127.0.0.1:3399'
$env:NEXT_PUBLIC_API_BASE_URL=''
$env:NEXT_PUBLIC_WS_BASE_URL=''
npm run build
npm run test:connected
```

Cần Chrome và Python `websockets`; có thể chỉ định `CHROME_PATH`. Cổng3300/3399 phải rảnh. Windows sandbox chặn loopback thì cần chạy bộ kiểm tra ngoài sandbox; không sửa client để bỏ kiểm tra mạng. Runner chỉ dừng process do nó tạo.

Chạy một nhóm: `python tests/run_connected.py test_connected_posts`, `test_connected_screens` hoặc `test_connected_chat`. Kết quả tổng hợp ở `connected_results.json`; khi lỗi, snapshot fixture và screenshot chỉ phục vụ chẩn đoán, không phải dữ liệu Java live. Snapshot lỗi bị Git ignore.

Sau fixture, build lại cho gateway thật (máy đã kiểm tra dùng18080) trước khi dùng production. CI dùng3399 để kiểm tra, không phát hành artifact đó.

## Mức xác minh

Fixture kiểm tra browser request/response, persisted mutations, errors/retry, file upload, STOMP echo/dedup/read/reconnect. Nó không thay thế database, storage hoặc Java service thật. Các lượt kiểm tra Java/cloud riêng và giới hạn Mongo TLS được ghi trong [báo cáo tổng hợp](../../docs/reports/2026-10-25-ui-backend-integration.md).

Unit sau review còn có `provider-sync.test.mjs`: chạy callback provider thật sau transpile trong bộ nhớ với hooks/API/socket được kiểm soát, xác nhận race REST/MESSAGE, reconnect tải bù, ID cũ không xác nhận SEND mới, story metadata và route notification. Đây là logic regression, không phải browser React lifecycle. `shared-post.test.mjs` kiểm tra adapter và render card thật, giữ liên kết bài gốc khi share không có lời dẫn.

## Tài liệu/test lịch sử

`test_next.py`, `test_login.py`, các JSON migration/refinement/UI-UX và báo cáo QA cũ thuộc bản UI demo trước tích hợp. Giữ để tham khảo bố cục/tương tác và nguồn chuyển đổi; không dùng các yêu cầu tài khoản mẫu/local-only đó làm nghiệm thu API hiện tại. `npm run test:legacy-demo` gọi bộ cũ có chủ đích.

Chi tiết fixture: [CONNECTED_FIXTURE.md](CONNECTED_FIXTURE.md). Nhật ký: [ui-backend-integration](../../docs/worklogs/2026-10-24-ui-backend-integration.md).
