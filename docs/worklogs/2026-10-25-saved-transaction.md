# Bỏ lưu: transaction cho derived JPA delete

Ngày: 2026-10-25. Nhánh: `codex/ui-backend-integration`.

## Vấn đề và thay đổi

Lượt kiểm tra API native của phiên tích hợp phát hiện `DELETE /api/saved?targetType=POST&targetId=...` trả 500 với thông báo `No EntityManager with actual transaction available for current thread - cannot reliably process remove call`.

Luồng gọi là `SavedItemController.unsave` → `SavedItemService.unsave` → `SavedItemRepository.deleteByTargetTypeAndTargetIdAndUserId`. Phương thức xóa suy diễn của repository cần transaction, nhưng service chưa mở transaction. Thêm duy nhất import và `@Transactional` tại phương thức `unsave`; giữ nguyên xác thực và phạm vi xóa theo người dùng hiện tại.

Thêm kiểm thử `unsave_opensTransactionBeforeDeletingCurrentUsersSavedItem` vào `SavedItemServiceTest`. Kiểm thử chạy service thật qua Spring `ProxyFactory` / `TransactionInterceptor` với nguồn thuộc tính transaction từ annotation. Transaction manager tối thiểu dùng cơ chế transaction synchronization thật của Spring; tại ranh giới repository, kiểm tra transaction đang hoạt động. Đồng thời xác minh target và user ID, và transaction đã kết thúc sau lời gọi. Không thêm database, H2 hay dependency.

## Kiểm chứng

PowerShell, chạy từ gốc repository:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.10'
$env:MAVEN_OPTS = '-javaagent:C:/Users/ADMIN/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.19/byte-buddy-agent-1.14.19.jar'
$mavenExecutable = 'C:/Users/ADMIN/.m2/wrapper/dists/apache-maven-3.9.14/ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d/bin/mvn.cmd'
& $mavenExecutable -Pnative-runtime -pl services/reaction-service -am '-Dtest=SavedItemServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DforkCount=0' test
& $mavenExecutable -Pnative-runtime -pl services/reaction-service -am '-DforkCount=0' test
```

1. RED trước sửa production: 9 tests, 1 failure, 0 errors. Đúng kiểm thử mới thất bại tại `derived JPA delete requires an active transaction`: expected true, actual false.
2. GREEN sau thêm annotation: 9 tests, 0 failures, 0 errors, exit 0 / BUILD SUCCESS.
3. Toàn bộ suite trong reactor reaction + common: common-lib 14/14; reaction-service 20/20 (ReactionServiceTest 11, SavedItemServiceTest 9). Tổng 34/34, exit 0 / BUILD SUCCESS.

Các vướng mắc môi trường đã xử lý: `.runtime/tools/java` là JRE nên lần thử đầu không có compiler; chuyển sang JDK 21 có sẵn. Mockito tự attach agent gặp lỗi Windows named pipe và treo khi chạy trong sandbox; dùng ByteBuddy agent cài sẵn qua `MAVEN_OPTS` cùng `-DforkCount=0` giải quyết được. Không sửa cấu hình repo. Maven vẫn in dòng `Access is denied.` lúc khởi động và cảnh báo forkCount/class sharing; các lượt kiểm chứng hoàn tất với exit 0 như trên.

## Bàn giao

Chỉ sửa `SavedItemService.java`, `SavedItemServiceTest.java` và worklog này. Không stage/commit, không package/repackage JAR đang chạy, không sửa frontend, credential, database hay cloud. Profile native-runtime ghi class/test output vào `.runtime/build` của module; JAR ứng dụng đang chạy vẫn do phiên chính quản lý.

Bước tiếp theo: phiên tích hợp rebuild/restart reaction-service, rồi kiểm tra lại save → unsave → getMine trên API native để xác nhận trạng thái bỏ lưu thực tế. Kết quả ở đây xác nhận transaction advice và toàn bộ suite của module, chưa thay thế lượt kiểm tra HTTP sau restart.

Quy định cộng tác tiếp nối: tiếp tục tiếng Việt với `i-have-adhd`, `ponytail` full; chủ động tiến tới kết quả, ghi số liệu trung thực, nêu điểm cần xử lý cùng bước giải quyết cụ thể và vừa phạm vi. Không biến giới hạn thành điều kiện chặn hay thêm thí nghiệm không cần thiết.
