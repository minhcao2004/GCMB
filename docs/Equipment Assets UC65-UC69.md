# Quản lý tài sản thiết bị UC65–UC69

## Phạm vi

UC65–UC69 hỗ trợ tạo, xem/tìm kiếm/lọc, cập nhật, ngừng/kích hoạt sử dụng và xóa tài sản thiết bị. Hồ sơ gồm mã, tên, nhóm, phòng/vị trí, serial, ngày mua, giá mua, hạn bảo hành, tình trạng vận hành và ảnh/chứng từ tùy chọn. Danh sách hiển thị nhóm, vị trí, hạn bảo hành, tình trạng và trạng thái sử dụng; hồ sơ xem thêm sự cố mới nhất.

## Quy tắc nghiệp vụ

- BR-202: Mã, tên, nhóm và vị trí là bắt buộc; mã tài sản duy nhất toàn chuỗi.
- BR-110: Hồ sơ tài sản luôn gắn với cơ sở/vị trí hiện hành. Cập nhật hồ sơ không thể đổi cơ sở; chuyển cơ sở cần quy trình xác nhận UC74–UC76.
- BR-115/BR-116: Ảnh/chứng từ phải khớp nội dung với loại JPEG, PNG, WebP, HEIC, HEIF hoặc PDF, kích thước lớn hơn 0 và không vượt 20 MB.
- UC-68 dùng cờ hoạt động riêng với tình trạng vận hành (đang dùng, hỏng, sửa, đang chuyển, ngừng sử dụng).
- BR-118: Chỉ xóa thiết bị chưa có sự cố hoặc điều chuyển; mọi asset được dùng trong lịch sử sự cố/điều chuyển được giữ nguyên.
- Tài sản có liên kết chi phí mua, đang điều chuyển hoặc được tham chiếu trong lịch sử thay thế không thể xóa.

## API

- Quản lý cơ sở: `/api/v1/branch-management/equipment-assets`
- Quản lý cấp cao: `/api/v1/head-office/equipment-assets?branchId={id}`
- `GET` hỗ trợ từ khóa, phòng, tình trạng, trạng thái sử dụng; `POST` tạo; `PUT /{id}` sửa; `PUT /{id}/status` đổi trạng thái sử dụng; `DELETE /{id}` xóa khi không có lịch sử; `POST /{id}/image` tải ảnh; `GET /{id}/image` xem ảnh.
- Migration V22 thêm cờ hoạt động, hạn bảo hành và liên kết ảnh; V1 chứa sẵn bảng tài sản, sự cố, điều chuyển và file đính kèm.
