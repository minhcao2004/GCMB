# Quản lý tài sản thiết bị UC65–UC69

## Phạm vi

UC65–UC69 hỗ trợ tạo, xem/tìm kiếm/lọc, cập nhật, ngừng/kích hoạt sử dụng và xóa tài sản thiết bị. Hồ sơ gồm mã, tên, nhóm, phòng/vị trí, serial, ngày mua, giá mua, hạn bảo hành, tình trạng vận hành và ảnh/chứng từ tùy chọn. Danh sách hiển thị nhóm, vị trí, hạn bảo hành, tình trạng và trạng thái sử dụng; hồ sơ xem thêm sự cố mới nhất.

## Quy tắc nghiệp vụ

- BR-202: Tên, nhóm và vị trí là bắt buộc khi thêm. Mã bắt đầu chỉ cần nhập khi cơ sở chưa có dãy số. Hệ thống cấp mã chính thức duy nhất toàn chuỗi khi lưu hồ sơ.
- BR-110: Hồ sơ tài sản luôn gắn với cơ sở/vị trí hiện hành. Cập nhật hồ sơ không thể đổi cơ sở; chuyển cơ sở cần quy trình xác nhận UC74–UC76.
- BR-115/BR-116: Ảnh/chứng từ phải khớp nội dung với loại JPEG, PNG, WebP, HEIC, HEIF hoặc PDF, kích thước lớn hơn 0 và không vượt 20 MB.
- Ảnh và chứng từ mới được lưu trên thư mục local của backend (`GCMB_ATTACHMENT_STORAGE_DIR`, mặc định `./data/attachments`) và được phân phối qua API có xác thực; người dùng chỉ xem được tài sản trong phạm vi vai trò/cơ sở của mình.
- UC-68 dùng cờ hoạt động riêng với tình trạng vận hành (đang dùng, hỏng, sửa, đang chuyển, ngừng sử dụng).
- BR-118: Chỉ xóa thiết bị chưa có sự cố hoặc điều chuyển; mọi asset được dùng trong lịch sử sự cố/điều chuyển được giữ nguyên.
- Tài sản có liên kết chi phí mua, đang điều chuyển hoặc được tham chiếu trong lịch sử thay thế không thể xóa.

## Quy tắc cấp mã thiết bị

- Mã có dạng `ASSET-{MÃ_CƠ_SỞ}-{STT}`. Phần mã cơ sở viết hoa và giữ cả `_` hoặc `-`. Số thứ tự có ít nhất 4 chữ số và tiếp tục từ `9999` sang `10000`.
- Hệ thống chọn dãy số theo cơ sở mà quản lý cấp cao đang chọn hoặc cơ sở được phân quyền của quản lý cơ sở. Khi chưa có dãy, form Thêm thiết bị cho nhập `ASSET-HN_HOA_BANG-0025` hoặc số bắt đầu `25`. Mã chính thức là `ASSET-HN_HOA_BANG-0025`. Từ thiết bị tiếp theo, ô mã chỉ đọc và hiển thị số dự kiến `0026`, `0027` theo lượt lưu. Cơ sở có dãy từ dữ liệu cũ tiếp tục cấp số tự động.
- Backend cấp số trong cùng giao dịch với thao tác lưu thiết bị và ghi nhật ký. Hai yêu cầu lưu đồng thời tại cùng cơ sở nhận hai số khác nhau. Mở hoặc hủy form sẽ không lấy số. Số đã cấp được giữ trong bộ đếm ngay cả khi hồ sơ đã xóa.
- Mã đã cấp gắn với hồ sơ. Thiết bị giữ mã khi cập nhật hoặc điều chuyển. Sau khi đổi mã cơ sở, thiết bị mới dùng dãy của mã cơ sở mới. Nếu dùng lại một mã cơ sở đã tồn tại, dãy tiếp tục theo bộ đếm của mã đó.

## API

- Quản lý cơ sở: `/api/v1/branch-management/equipment-assets`
- Quản lý cấp cao: `/api/v1/head-office/equipment-assets?branchId={id}`
- `GET` hỗ trợ từ khóa, phòng, tình trạng, trạng thái sử dụng; `POST` tạo; `PUT /{id}` sửa; `PUT /{id}/status` đổi trạng thái sử dụng; `DELETE /{id}` xóa khi không có lịch sử; `POST /{id}/image` tải ảnh; `GET /{id}/image` xem ảnh.
- `GET /options` trả `codePrefix` và `nextCode` khi thêm. `nextCode` là `null` nếu cần nhập mã bắt đầu. `POST` nhận `code` dưới dạng mã đầy đủ theo cơ sở hoặc số bắt đầu dương cho dãy chưa khởi tạo. Dãy đang có tiếp tục cấp số tự động, kể cả khi client gửi `code`. Phản hồi luôn trả mã chính thức ở `code`. `PUT /{id}` cho phép bỏ qua `code` hoặc gửi lại chính mã hiện tại. Gửi mã khác trả HTTP 400.

## Migration

- Migration V1 chứa bảng tài sản, sự cố, điều chuyển và file đính kèm. V31 thêm cờ hoạt động, hạn bảo hành và liên kết ảnh, với DDL tương thích các database phát triển từng nhận các cột này ở V22.
- V66 mở rộng `asset.code` và tạo bộ đếm theo mã cơ sở. Bộ đếm lấy số lớn nhất từ các mã đúng quy tắc trong hồ sơ hiện tại và nhật ký tài sản, kể cả mã của thiết bị đã điều chuyển hoặc đã xóa có lưu nhật ký. Migration giữ nguyên giá trị mã đã cấp. Triển khai backend và frontend cùng nhau để form hiển thị đúng trạng thái tự cấp mã.
