# Dữ liệu demo để thử UC34–UC37

Fixture SQL: [`backend/scripts/seed_uc34_37_demo.sql`](../backend/scripts/seed_uc34_37_demo.sql).

## Nạp dữ liệu

1. Chạy ứng dụng backend ít nhất một lần để Flyway áp dụng migration V1 đến V4.
2. Mở database development `gcmb` trong pgAdmin, mở Query Tool, rồi chạy toàn bộ file fixture SQL. Có thể dùng PowerShell nếu đã cài `psql`:

   ```powershell
   psql -h localhost -p 5432 -U gcmb_dev -d gcmb -v ON_ERROR_STOP=1 -f backend/scripts/seed_uc34_37_demo.sql
   ```

3. Mở `http://localhost:3000/dang-nhap` và đăng nhập bằng:

   - Tên đăng nhập: `uc3437.demo`
   - Mật khẩu: `Demo123!`

Đây là tài khoản và dữ liệu giả chỉ dành cho database local/dev. Không chạy fixture trên production. Script dùng ID cố định để tránh nhân đôi dữ liệu khi chạy lại; các booking đã được thao tác và lịch sử thanh toán/cọc dạng append-only sẽ giữ nguyên trạng thái hiện tại.

## Dữ liệu có sẵn

| Mục | Dữ liệu |
| --- | --- |
| Chi nhánh | `DEMO-UC3437`, múi giờ `Asia/Ho_Chi_Minh` |
| Loại phòng | Music Box và Cinema, có giá theo giờ; Music Box có combo 2 giờ, cọc 100.000đ |
| Phòng | 5 phòng Music Box và 1 phòng Cinema hoạt động; thêm 1 phòng mỗi loại đang bảo trì |
| Lễ tân | Tài khoản demo có nhân viên và phân công còn hiệu lực tại chi nhánh demo |
| Check-in | `DEMO-UC3437-CHECKIN`, bắt đầu 5 phút trước khi nạp fixture, 4 khách, cọc 200.000đ; phòng đang ở trạng thái sẵn sàng |
| Booking cập nhật | `DEMO-UC3437-EDIT`, chưa bắt đầu, chưa cọc |
| Hủy được hoàn cọc | `DEMO-UC3437-REFUND`, cọc 100.000đ; hủy trước giờ nhận ít nhất 40 phút để tạo yêu cầu hoàn `REQUESTED` |
| Hủy sát giờ | `DEMO-UC3437-FORFEIT-30M`, cọc 100.000đ; giờ nhận đã được đặt trước 30 phút, hủy ngay để nhận `NON_REFUNDABLE` và số dư cọc bằng 0 |
| Hủy không cọc | `DEMO-UC3437-NODEPOSIT`, hủy trước giờ bắt đầu để nhận `NO_DEPOSIT` |

Booking check-in bắt đầu 5 phút trước khi chạy fixture để có thể check-in ngay. Booking `...-FORFEIT-30M` bắt đầu sau 30 phút; các booking còn lại bắt đầu sau khoảng 3, 6, 9 và 12 giờ. Phòng `DEMO-CIN-01` còn trống để tạo booking mới; phòng `DEMO-MB-05` được giữ cho luồng check-in. Hai phòng đang bảo trì không xuất hiện trong availability.

## Gợi ý luồng thử

- **UC34:** xem booking options; kiểm tra availability Music Box và Cinema; thử giá theo giờ và combo; tạo booking mới trên phòng còn trống.
- **UC35:** tìm `DEMO-UC3437` trong danh sách, thử lọc trạng thái/ngày/cọc, rồi mở chi tiết từng booking mẫu.
- **Check-in:** mở `DEMO-UC3437-CHECKIN`, kiểm tra số khách, tình trạng phòng và cọc, rồi xác nhận check-in để chuyển booking sang `IN_HOUSE`.
- **UC36:** sửa liên hệ, ghi chú hoặc đổi phòng/giờ của `DEMO-UC3437-EDIT`; kiểm tra lịch sử allocation, charge snapshot và số dư cọc được giữ nguyên.
- **UC37:** hủy booking `...-REFUND`, `...-FORFEIT-30M` và `...-NODEPOSIT` theo bảng trên. `...-FORFEIT-30M` mở sẵn màn giữ cọc 30 phút trước giờ nhận. Với `...-REFUND`, nhập chủ tài khoản, số tài khoản và ngân hàng; kế toán sẽ xử lý yêu cầu. Hủy/check-in là thao tác một lần; để thử lại từ đầu, nạp fixture vào database development mới.

API vẫn yêu cầu đăng nhập lễ tân và dùng cookie phiên. Với request `POST`/`PATCH` trực tiếp, lấy CSRF token từ `GET /api/v1/auth/csrf` và gửi lại ở header được response chỉ định.
