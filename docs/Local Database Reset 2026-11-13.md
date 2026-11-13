# Làm sạch database local ngày 13/11/2026

## Phạm vi đã được xác nhận

Theo yêu cầu trực tiếp của người dùng, database PostgreSQL `gcmb` tại `localhost:5432` đã được thay toàn bộ bằng seed nền hiện hành trong source. Tài liệu tham chiếu là `GCMB_Thanh_test_data.md`; đây là danh mục seed/test, không phải bản dump để import trực tiếp.

Trước khi thay: 25 cơ sở, 124 phòng, 15 tài khoản, 40 booking. Sau khi thay: 18 cơ sở, 108 phòng đều `AVAILABLE`, 6 tài khoản và 6 hồ sơ nhân viên seed. Có 16 combo, 2 quy tắc giá, 3 loại phòng, 4 vai trò, 4 khung ca và 8 đơn vị tồn kho. Có 19 tài khoản quỹ nền; không có giao dịch hoặc số dư đầu kỳ được tự tạo.

Tất cả bảng trong schema `gcmb` đã được đếm lại: ngoài các bảng seed nền và phân công, các bảng còn lại đều trống, bao gồm booking, thanh toán, cọc, hóa đơn, F&B, tồn kho phát sinh, tài sản, lịch ca phát sinh, lương và giao dịch tài chính.

## Tài khoản và đăng nhập

Sáu username còn lại: `letan.hoabang`, `letan.nguyenluongbang`, `qlcs.hoabang`, `qlcs.nguyenluongbang`, `qlcc`, `ketoan`.

Giữ nguyên password hash trước reset của đúng sáu tài khoản này; kiểm tra so sánh hash đã đạt. Không lưu mật khẩu hoặc hash trong tài liệu. Vai trò, cơ sở và phân công được nạp lại theo migration, không giữ những chỉnh sửa phân quyền local trước đây. Các tài khoản khác và mọi phiên đăng nhập cũ đã bị loại bỏ. Người dùng cần đăng nhập lại; nếu backend đang dừng thì khởi động theo README.

## Cách thực hiện và kiểm tra

- Tạo bản sao lưu đầy đủ bằng `pg_dump -Fc` trước khi thay dữ liệu; kiểm tra danh mục archive bằng `pg_restore --list` thành công.
- Tạm giữ schema và lịch sử Flyway cũ trong cùng database trong thời gian nạp mới, chỉ xóa bản tạm sau khi mọi kiểm tra thành công. Bản dump vẫn được giữ lại.
- Flyway chạy mới toàn bộ 58 migration từ source, sau đó `validate()` thành công; phiên bản cuối là V58. Không sửa file migration hoặc checksum của lịch sử cũ.
- Kiểm tra tự động số lượng cơ sở/phòng/tài khoản, các bảng booking/thanh toán/cọc/phiên trống và mật khẩu được giữ nguyên. Truy vấn độc lập sau reset xác nhận lại trạng thái phòng, lịch sử Flyway và số bản ghi của mọi bảng.
- Không chạy các fixture UC34–44 lịch sử, không nhập mock frontend hoặc thêm ba nhân viên tùy chọn. Không chạy toàn bộ test ứng dụng hay kiểm tra đăng nhập qua giao diện trong tác vụ này.

## Bản sao lưu và giới hạn khôi phục

File local: `backend/secrets/database-backups/gcmb-before-reset-20261113.dump` (783.294 byte).

SHA-256: `9BCF110DA4EB50C3624AFEE53AE9F11B05E58C95B86E2D3C20A30EB9B929378B`.

Thư mục `backend/secrets/` đã được Git bỏ qua; không commit hoặc gửi bản dump vì có dữ liệu tài khoản và nghiệp vụ cũ. Bản dump chứa database trước reset, gồm schema, dữ liệu và metadata Flyway cũ; không chứa file upload nằm ngoài PostgreSQL. File upload hiện hữu không bị xóa trong tác vụ này và có thể không còn bản ghi tham chiếu trong database mới.

Khôi phục cần dừng backend và các client ghi dữ liệu, rồi thay database/schema bằng bản dump qua công cụ PostgreSQL. Không import chồng lên seed mới bằng chế độ mặc định. Chỉ kiểm tra danh mục archive trong tác vụ này, chưa diễn tập restore bản dump sang database riêng.

## Ngoài tài liệu hiện có

Đây là thao tác một lần trên database local, không phải migration mới hoặc thay đổi API. Khác với seed V15 nguyên bản, mật khẩu hiện tại của sáu tài khoản seed được giữ lại để tiếp tục sử dụng. Dữ liệu phát sinh cũ chỉ còn trong bản sao lưu; dữ liệu demo nghiệp vụ mới cần được tạo qua ứng dụng. File theo dõi: tài liệu này và liên kết trong `README.md`; source ứng dụng và migration không thay đổi.
