# Phân quyền, thu hồi truy cập và nhật ký

Ngày cập nhật: 13/10/2026 — HuyDV.

## Sử dụng

Đăng nhập bằng quản lý cấp cao, mở **Tài khoản & phân quyền**.

1. Bấm **Phân quyền** ở dòng tài khoản để mở bảng vai trò và phạm vi.
2. Chọn vai trò và cơ sở. Quản lý cấp cao/kế toán có phạm vi toàn chuỗi; quản lý cơ sở/lễ tân cần cơ sở đang hoạt động.
3. Bấm **Lưu phân quyền**. Khi quyền thực sự thay đổi, phiên đăng nhập và yêu cầu đặt lại mật khẩu cũ bị thu hồi; người dùng đăng nhập lại bằng mật khẩu hiện tại.
4. Trong bảng phân quyền, bấm **Thu hồi quyền truy cập**, nhập lý do rồi xác nhận. Tài khoản chuyển thành `REVOKED`; hồ sơ nhân viên và lịch sử được giữ nguyên.
5. Bấm **Xem nhật ký hệ thống** hoặc mục **Nhật ký hệ thống** trên thanh bên. Lọc theo ngày, người/mã đối tượng, hành động hoặc cơ sở. Bấm **Chi tiết** để xem lý do và các trường tài khoản trước/sau.

Không thể tự đổi quyền hoặc thu hồi tài khoản đang đăng nhập. Tài khoản đã thu hồi chỉ được xem; khôi phục tài khoản không nằm trong chức năng này. Với quản lý cơ sở, hộp thu hồi nhắc bố trí người thay thế; thay đổi phân công nhân sự là chức năng riêng.

Bảng quyền chỉ đọc lấy dữ liệu thật từ `role_permission` và `permission`. Nếu chưa cấu hình thì hiển thị thông báo tương ứng, không tự tạo quyền giả. Quyền truy cập các endpoint tiếp tục được kiểm tra qua Spring Security theo vai trò.

## API

Các API dưới `/api/v1/head-office/` yêu cầu phiên đăng nhập của `HEAD_OFFICE_MANAGER`.

- `GET accounts/permissions?role=...`: các quyền đã cấu hình của vai trò.
- `POST accounts/{id}/role`: JSON `role`, `branchId` (null cho vai trò toàn chuỗi).
- `POST accounts/{id}/deactivate`: JSON `reason`, bắt buộc, tối đa 1000 ký tự.
- `GET audit-logs`: `q`, `action`, `branchId`, `from`, `to`, `page`, `size`.
- `GET audit-logs/actions`: các hành động đã có trong nhật ký.
- `GET audit-logs/{id}`: chi tiết sự kiện và các trường tài khoản được phép hiển thị.

POST dùng CSRF lấy từ `/api/v1/auth/csrf`. Khoảng ngày nhật ký bao gồm toàn bộ ngày kết thúc theo `Asia/Ho_Chi_Minh`. Phân trang bắt đầu từ 0, mặc định 10, tối đa 100 dòng.

## Dữ liệu và giao dịch

Không có migration mới cho ba chức năng này. Cần schema V1–V3 hiện có.

- Đổi quyền, cập nhật trạng thái, thu hồi token và ghi audit nằm trong một giao dịch.
- Khóa giao dịch chung cho thao tác quản lý và kiểm tra lại quyền người thực hiện ngăn hai quản lý đồng thời thu hồi nhau.
- Audit dùng các hành động `SET_ACCOUNT_ROLE` và `DEACTIVATE_USER_ACCOUNT`, lưu username, role, tên phạm vi, trạng thái trước/sau và lý do thu hồi.
- Không lưu hoặc trả mật khẩu/token trong audit. API chi tiết chỉ chọn các trường cho phép, không trả nguyên JSON lịch sử.
- Sự kiện tạo tài khoản cũ có thể không có dữ liệu trước/sau; màn hình hiển thị `—` cho thông tin không được ghi lại.
- Nhật ký chỉ có API GET; bảng có trigger append-only từ V1.

## Kiểm thử

Đã chạy 30 kiểm thử frontend, 15 kiểm thử đơn vị backend, 3 kiểm thử tích hợp PostgreSQL riêng và build frontend. PostgreSQL kiểm thử được khởi tạo trong `backend/target`, dừng sau khi kiểm thử; không dùng database đang làm việc.

`AccountAccessIntegrationTest` chạy khi có `AUTH_TEST_DB_URL`, `AUTH_TEST_DB_USERNAME`, `AUTH_TEST_DB_PASSWORD`, với schema V1–V3 đã chuẩn bị. Mọi dữ liệu fixture được rollback. Không trỏ cấu hình thử nghiệm vào database sử dụng hằng ngày.
