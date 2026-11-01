# Kiểm thử kết nối frontend-web

Fixture mô phỏng hợp đồng Java qua HTTP và WebSocket/STOMP thật trên localhost. Kết quả này kiểm tra frontend gọi đúng API, xử lý phản hồi và lỗi; kiểm tra backend Java native là một bước riêng.

1. Tại `frontend-web`, build bằng PowerShell:

   ```powershell
   $env:GATEWAY_UPSTREAM = 'http://127.0.0.1:3399'
   $env:NEXT_PUBLIC_WS_BASE_URL = 'http://127.0.0.1:3399'
   npm run build
   ```

2. Chạy `python tests/run_connected.py`. Runner sở hữu fixture `3399`, Next production `3300`, Chrome headless dùng profile tạm; dừng các tiến trình của mình khi kết thúc. Các port đang có tiến trình khác sẽ bị từ chối, không dừng tiến trình đó.
3. Đọc `tests/connected_results.json`. Có thể chạy riêng `python tests/run_connected.py test_connected_posts` hoặc `test_connected_screens`. `CHROME_PATH`, `NEXT_PORT`, `BASE_URL`, `FIXTURE_PORT` là các cấu hình tùy chọn; nếu đổi fixture port phải build lại cùng upstream và WS URL tương ứng.

Tài khoản chỉ dành cho fixture: `son@example.test` / `Passw0rd!test` là `user-1` (Thiên Sơn); `friend@example.test` cùng mật khẩu là `user-2` (Bạn Kiểm Thử).

Control routes chỉ tồn tại trong fixture, không thêm vào ứng dụng:

- POST `/__test/reset`, body `{}`: xóa mutation, đóng socket cũ, khôi phục seed.
- POST `/__test/fail-next`, body `{path:"/api/posts",method:"POST",status:500,message:"Please retry"}`: lần gọi khớp tiếp theo thất bại và không đổi dữ liệu. STOMP dùng `method:"STOMP",path:"/app/chat.send"`.
- GET `/__test/state`: trả các array `profiles,posts,comments,reactions,saved,friendships,follows,groups,members,stories,reels,conversations,messages,notifications,reports,uploads` và `requests:[{method,path,query,body,status}]` để assert request thật. Đừng dùng `profiles[userId]`; tìm phần tử theo `id`.

Runner reset fixture và mở Chrome profile mới cho từng module. Mỗi module export `async run(browser)` và đăng nhập qua UI; không seed session hay state React. HTTP resource creation trả 201 như backend Java, mutation còn lại thường 200; kiểm tra thành công bằng khoảng `200 <= status < 300`.
