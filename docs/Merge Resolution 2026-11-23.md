# Gộp main và Huy — 23/11/2026

## Phạm vi

Xử lý 16 khối xung đột trong 9 file. Giữ naming convention trên main đã được nhóm chấp thuận, đồng thời tích hợp chức năng từ Huy. Chỉ sửa local; không commit, push, deploy, thay đổi `.env` hay ghi vào cơ sở dữ liệu thật trong lần xử lý này.

## Các quyết định gộp

- `backend/src/main/java/com/gcmb/backend/booking/service/CheckoutService.java`: giữ tên `jdbcTemplate`, `bookingRepository`, `bookingService`, `sePaySettings`, `objectMapper`, `foodOrderService`, `financialPeriodService`; bổ sung lý do giao dịch SePay không khớp và giữ xác nhận cọc thủ công. Giữ cơ chế chống xử lý thanh toán lặp.
- `backend/src/main/java/com/gcmb/backend/dashboard/AccountingWorkbookService.java`: giữ `jdbcTemplate`, `monthlyPerformanceService`; giữ đủ tham số ngày/cơ sở theo các dấu hỏi của SQL, và tính cả giao dịch EXCEPTION/UNMATCHED chưa xử lý.
- `backend/src/main/java/com/gcmb/backend/workforce/service/StaffService.java`: giữ `staffRepository` và các kiểm tra quyền/phạm vi; số định danh không bắt buộc, nhưng khi nhập phải đúng 12 chữ số và giữ số 0 đầu.
- `frontend/src/features/booking/pages/AccountantRefundPage.js`: giữ ToastStack và phân trang dùng chung.
- `frontend/src/features/booking/pages/BookingManagementPage.js`: giữ bộ chọn quà `ComboGiftPicker` đã tách trên main; không đưa lại modal cũ hoặc biến đã bị xóa. Giữ phân trang danh sách.
- `frontend/src/features/branch/components/BranchAssignmentPanel.js`: kết hợp danh sách phân trang với ToastStack.
- `frontend/src/features/dashboard/AccountantFinancePage.js`: giữ bộ lọc, bảng responsive, nhãn truy cập và dữ liệu giao dịch đã cắt theo trang của main; dùng bộ phân trang chung 5 giao dịch/trang, không hiển thị hai bộ phân trang cho cùng danh sách. Lịch sử điều chỉnh dùng 5 lần/trang.
- `frontend/src/features/finance/pages/FinancePage.js`: giữ lịch sử/chứng từ thu gọn, nhãn truy cập và cách ghi nhận phương thức chi thực tế trên main; kết hợp phân trang yêu cầu và lịch phân bổ.
- `frontend/src/features/inventory/pages/InventoryPage.js`: giữ giao diện Kho hàng gộp danh mục/tồn kho của Huy, đồng thời dùng ToastStack trên main; giữ thêm/sửa/xóa và xem lịch sử.
- File liên quan ngoài 9 xung đột: `frontend/src/features/booking/components/ComboGiftPicker.js` bổ sung phân trang 10 lựa chọn/trang vào bộ chọn dùng chung, giữ kiểm tra tồn kho và trạng thái đang xử lý.

Không đổi API, schema hoặc bổ sung migration trong phần giải quyết xung đột.

## Kết quả kiểm tra

- Java 17, Maven offline: backend compile thành công.
- Frontend production build thành công. Còn cảnh báo hook có sẵn ở `IncomingRoomOrderDialog.js` về dependency `order`.
- Bộ frontend hiện có: 1 suite, 5 test đạt, exit 0. Môi trường test vẫn in cảnh báo XHR/open handles có sẵn.
- Kiểm tra Java với repository/JdbcTemplate giả lập: giao dịch không có mã tham chiếu, cọc cần xác nhận tay, sai tiền/chiều giao dịch không tạo payment; thanh toán bill hợp lệ tạo một payment; sự kiện lặp không tạo thêm payment. Số tham số SQL báo cáo khớp số dấu hỏi. Số định danh trống/12 số hợp lệ; 11/13 số hoặc chữ bị từ chối.
- Kiểm tra React trong JSDOM với API giả lập: Kho hàng 5 dòng/trang; xóa từ trang sau; thêm/sửa/lịch sử; bộ lọc được giữ; bộ chọn quà 10 lựa chọn/trang, hết hàng và lượng đã chọn; trạng thái busy; bảng giao dịch giữ nhãn truy cập và chỉ một bộ phân trang hiển thị cho danh sách giao dịch.
- Không chạy kiểm thử đầu cuối bằng tài khoản và cơ sở dữ liệu thật. Không kiểm chứng transaction rollback bằng PostgreSQL thật; kiểm tra thanh toán nêu trên dùng mock.

## Unit test backend cần nhóm theo dõi

Chạy 8 suite dùng mock, tổng 216 test: 204 đạt, 7 assertion thất bại và 5 lỗi Mockito do stubbing không được sử dụng. Không chạy SpringBootTest kết nối database.

Đã chạy cùng bộ test trên bản HEAD của main trước merge (`dd8acb9`) trong thư mục nguồn tách riêng dưới `backend/target/merge-review-2026-11-23/baseline/`: 200 đạt, 11 assertion thất bại, 5 lỗi Mockito. Cả 12 test chưa đạt ở bản gộp đều chưa đạt ở main trước merge; không có tên test lỗi mới trong bộ này. Bốn test kiểm tra số điện thoại của AccountProfileService nay đạt.

Những test còn lỗi thuộc `AccountServiceTest`, `AuthServiceTest`, `AuditServiceTest`: có assertion thông báo/xác thực mật khẩu và expectation về dummy hash cần đối chiếu lại với quy tắc hiện hành; 5 lỗi Mockito là `UnnecessaryStubbingException`. Chưa sửa các test hoặc thay đổi nghiệp vụ đăng nhập để ép các assertion cũ đạt trong phạm vi giải quyết merge này. Nhóm cần rà soát riêng trước khi xem bộ unit test backend là xanh hoàn toàn.

Các kiểm tra bổ sung dùng JShell/Node trong bộ nhớ; không thêm file test theo dõi bằng Git. Bản sao xung đột ban đầu và log đối chiếu nằm dưới thư mục `backend/target/merge-review-2026-11-23/` bị Git bỏ qua.

## Bàn giao

Sau khi đánh dấu đã giải quyết, merge vẫn chờ người dùng review và tạo merge commit. Trước khi đưa lên môi trường dùng chung, nhóm nên chạy thử thanh toán/cọc, xuất báo cáo, phân quyền nhân sự và thao tác Kho hàng bằng dữ liệu test của môi trường đó; xử lý riêng các unit test cũ nêu trên.
