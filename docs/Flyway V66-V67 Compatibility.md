# Tương thích hai lịch sử Flyway V66–V67

## Phạm vi

Hai thứ tự V66/V67 đã được áp dụng trên các database khác nhau trước khi source được hợp nhất. Không đổi số migration đã chạy, sửa checksum trong database, chạy `repair` hoặc tắt validation để chuyển từ thứ tự này sang thứ tự kia.

Migration dùng chung nằm trong `backend/src/main/resources/db/migration/`: V1–V65 và các phiên bản mới từ V68. Hai bộ V66/V67 nằm trong hai thư mục ngang hàng; Flyway chỉ được nạp một bộ cùng với thư mục dùng chung.

- Mặc định: `db/migration-equipment-first`, với V66 `equipment_asset_code_counters` và V67 `cash_closing_notifications`. Giữ nguyên nội dung của hai file trong source trước lần sửa này. Database mới tiếp tục dùng bộ mặc định.
- Tương thích: `db/migration-notifications-first`, với V66 `cash_closing_notifications` và V67 `equipment_asset_code_counters`. Hai file giữ nguyên nội dung từ commit trước `b0f9c87`, kể cả phần chú thích ảnh hưởng checksum.

Cấu hình mặc định nằm trong `application.yaml`, dùng chung cho dev, prod và profile kiểm thử. `validate-on-migrate` vẫn bật trong dev/prod; thiếu thư mục migration được cấu hình sẽ làm startup thất bại.

## Chọn đúng bộ cho database hiện có

Đọc lịch sử trước khi thay cấu hình:

```sql
SELECT version, description, script, checksum, success
FROM public.flyway_schema_history
WHERE version IN ('66', '67')
ORDER BY installed_rank;
```

Nếu lịch sử là V66 equipment / V67 notifications, giữ cấu hình mặc định. Nếu là V66 notifications / V67 equipment, thêm vào `backend/.env` khi chạy `npm start`, hoặc môi trường backend khi chạy trực tiếp/Docker:

```dotenv
SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/migration-notifications-first
```

Không nạp cả hai bộ cùng lúc, không chọn location `classpath:db` vì nó quét cả hai bộ. Giữ nguyên lựa chọn của database đó trong các lần triển khai tiếp theo. Nếu mới có V66, chọn bộ theo tên script V66 đã áp dụng. Lịch sử khác hai bộ trên cần được đối chiếu riêng, không tự sửa bảng lịch sử.

Sau khi đổi location, khởi động lại backend. Với Docker phải recreate container để nhận biến môi trường mới. Lần đầu cập nhật source này nên dùng `npm.cmd start` từ `frontend/`: launcher xóa riêng bản migration sinh ra ở `backend/target/classes/db/migration/` trước khi Maven sao chép resource, tránh giữ lại V66/V67 cũ ở thư mục dùng chung. Không chạy Maven `clean` nếu cụm PostgreSQL còn nằm dưới `backend/target/`.

## Database local đã đối chiếu ngày 26/11/2026

Database `localhost:5432/gcmb` có V66 notifications (checksum `639331970`) và V67 equipment (checksum `-1240487493`), cả hai đã thành công. `backend/.env` của máy này chọn `migration-notifications-first`. Không sửa lịch sử Flyway, schema hoặc dữ liệu nghiệp vụ để khắc phục lỗi checksum.

Đã tạo bản sao lưu dạng custom archive trong `backend/secrets/database-backups/` trước khi kiểm tra khởi động. Thư mục này bị Git bỏ qua; không gửi hoặc commit bản dump vì có dữ liệu tài khoản và nghiệp vụ.

Kiểm tra sau sửa: Flyway xác thực thành công 67 migration, báo schema V67 đã cập nhật và không cần chạy migration mới. Backend health trả `UP`; trang đăng nhập trả HTTP 200 với header HTML và proxy API qua frontend trả `UP` với header JSON. Dấu kiểm tra toàn bộ bảng lịch sử Flyway giữ nguyên; số booking 34.038 và hóa đơn 30.471 không đổi. Không chạy lại bộ unit test toàn dự án hoặc triển khai VPS trong lần sửa này.

## Quy tắc cho thay đổi tiếp theo

Thêm thay đổi schema bằng migration mới từ V68 trong thư mục dùng chung. Không chỉnh sửa nội dung hoặc đổi số bốn file tương thích V66/V67. Hai bộ chỉ phục vụ lịch sử đã tồn tại, không phải hai nhánh schema để phát triển độc lập.
