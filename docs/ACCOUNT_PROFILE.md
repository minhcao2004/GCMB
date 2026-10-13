# Xem và cập nhật thông tin tài khoản cá nhân

Trong màn hình sau đăng nhập, chọn **Thông tin tài khoản** ở thanh trên cùng.
Chức năng dùng được với cả quản lý cấp cao, quản lý cơ sở, lễ tân và kế toán.

- Xem tên đăng nhập, mã nhân viên, họ tên, email, điện thoại, vai trò, cơ sở và trạng thái.
- Chọn **Chỉnh sửa thông tin** để sửa họ tên, điện thoại và email. **Hủy chỉnh sửa** bỏ dữ liệu chưa lưu.
- Tài khoản chưa liên kết nhân viên chỉ sửa được email; hệ thống không tự tạo hồ sơ nhân viên.
- Đổi email cần mật khẩu hiện tại vì email được dùng để khôi phục tài khoản. Mã khôi phục đã cấp trước đó bị thu hồi; phiên đăng nhập được giữ lại.
- Email phải đúng định dạng và không trùng tài khoản khác. Họ tên bắt buộc, tối đa 200 ký tự; điện thoại tùy chọn, 7–30 ký tự gồm số và dấu định dạng, ít nhất 7 chữ số.
- Tên đăng nhập, vai trò, cơ sở, trạng thái và mật khẩu không nằm trong biểu mẫu cập nhật này.

## API

`GET /api/v1/account/profile` trả thông tin của người đang đăng nhập.

`POST /api/v1/account/profile` nhận `fullName`, `phone`, `email`, `currentPassword`.
`currentPassword` chỉ bắt buộc khi email thay đổi. Tài khoản lấy từ phiên đăng nhập, không nhận ID từ trình duyệt. API yêu cầu đăng nhập và POST yêu cầu CSRF.

Thao tác cập nhật khóa bản ghi tài khoản và nhân viên, kiểm tra trạng thái ACTIVE, cập nhật dữ liệu và ghi `UPDATE_ACCOUNT_INFORMATION` trong cùng giao dịch. Nhật ký chỉ chứa thông tin cá nhân thay đổi, không chứa mật khẩu hoặc token. Quản lý tra cứu trong **Nhật ký hệ thống**.

Không thêm hoặc sửa migration. Email mới được xác nhận bằng mật khẩu hiện tại, chưa có bước xác minh quyền sở hữu email mới bằng OTP.

## Kiểm thử

- `AccountProfileServiceTest`: chuẩn hóa dữ liệu, email trùng, mật khẩu sai, trạng thái tài khoản, dữ liệu không đổi và hồ sơ chưa liên kết.
- `AccountProfileDialog.test.js`: xem/sửa/hủy, lỗi API, tải lại, xác nhận email và bảo toàn dữ liệu nhập khi lưu thất bại.
- `AccountAccessIntegrationTest`: API chỉ cập nhật người đang đăng nhập, CSRF, các vai trò, lưu SQL và audit, thu hồi mã khôi phục cũ nhưng giữ phiên đăng nhập.
