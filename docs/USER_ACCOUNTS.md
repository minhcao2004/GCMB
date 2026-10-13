# Tạo và xem tài khoản nhân viên

Đăng nhập bằng vai trò `HEAD_OFFICE_MANAGER`, mở **Tài khoản & phân quyền** trên thanh bên của trang quản lý cấp cao.

- Danh sách tìm theo tên nhân viên, tên đăng nhập hoặc email; lọc vai trò, cơ sở, trạng thái và phân trang 10 dòng.
- **Tạo tài khoản** mở bảng bên phải. Chọn hồ sơ nhân viên đang hoạt động chưa có tài khoản, nhập tên đăng nhập, mật khẩu, email, vai trò và chọn cơ sở.
- Quản lý cấp cao và kế toán có phạm vi toàn chuỗi. Quản lý cơ sở và lễ tân chọn cơ sở đang hoạt động. Phạm vi được lưu tại `user_account.branch_id`; tài khoản cũ chưa có giá trị này vẫn hiển thị theo phân công nhân sự.
- Tạo thành công hiển thị thông tin đã lưu. **Xem tài khoản** lọc và đánh dấu tài khoản vừa tạo.

## API

Tất cả endpoint nằm dưới `/api/v1/head-office/accounts`, được bảo vệ bằng session và quyền quản lý cấp cao có sẵn.

- `GET /`: tham số `q`, `role`, `branchId`, `status`, `page` (từ 0), `size` (1–100); trả `items`, `total`, `page`, `size`.
- `GET /options`: nhân viên đang hoạt động chưa có tài khoản, vai trò và cơ sở đang hoạt động.
- `POST /`: `employeeId`, `username`, `password`, `email`, `role`, `branchId` (null với vai trò toàn chuỗi). Lấy CSRF từ `/api/v1/auth/csrf` trước khi gửi. Trả HTTP 201 với tài khoản đã tạo, không trả mật khẩu/hash.

Tên đăng nhập: 3–100 chữ ASCII, số hoặc `.`, `_`, `-`. Mật khẩu: bắt buộc nhập, tối đa 128 ký tự, không yêu cầu tối thiểu 8 ký tự hay thành phần ký tự. Email được chuẩn hóa; username/email kiểm tra trùng không phân biệt hoa thường bằng unique index hiện có. Khóa hồ sơ nhân viên để ngăn cấp tài khoản trùng. Tạo tài khoản, liên kết nhân viên, lưu cơ sở và ghi audit cùng giao dịch; mật khẩu băm bằng `AuthCrypto` hiện có.

Migration V3 thêm cột cơ sở và chèn ba cơ sở theo yêu cầu: Hồ Tây, Nguyễn Văn Lộc, Triều Khúc; địa chỉ tạm là “Chưa cập nhật”. Migration chạy khi backend khởi động, giữ nguyên V1/V2. Thay đổi quyền, thu hồi tài khoản và màn hình nhật ký đã được triển khai; xem [hướng dẫn phân quyền và nhật ký](ACCOUNT_ACCESS_AND_AUDIT.md).

## Kiểm thử

- Frontend: `npm.cmd test -- --watchAll=false --runInBand` và `npm.cmd run build` trong `frontend`.
- Backend: `mvnw.cmd test` trong `backend`; các kiểm thử tích hợp hiện có chỉ chạy khi cấu hình database thử nghiệm riêng bằng `AUTH_TEST_DB_URL`.
- UAT với database thử nghiệm: tạo tài khoản liên kết nhân viên, dùng mật khẩu ngắn; kiểm tra đăng nhập bằng tài khoản mới, bộ lọc, trùng email/tên đăng nhập, từ chối cơ sở không hoạt động và từ chối API với vai trò khác.

## Hồ sơ mẫu

Chưa triển khai màn hình Nhân sự. Script `backend/scripts/seed-demo-employees.sql` thêm 3 hồ sơ thử nghiệm: DEMO-NV-001 (Phạm Thu Hà), DEMO-NV-002 (Nguyễn Minh An), DEMO-NV-003 (Trần Gia Huy). Script đã được chạy vào database local; chạy lại không ghi đè hồ sơ hay liên kết tài khoản hiện có.
