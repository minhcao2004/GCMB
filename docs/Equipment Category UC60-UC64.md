# Quản lý nhóm thiết bị UC60–UC64

## Phạm vi

Màn hình nhóm thiết bị triển khai UC-60–UC-64. Chỉ quản lý cấp cao được xem, tạo, sửa, đổi trạng thái và xóa danh mục nhóm dùng chung toàn chuỗi. Quản lý cơ sở không quản lý danh mục nhóm; họ chỉ chọn nhóm đang hoạt động khi lập hồ sơ thiết bị tại cơ sở được phân quyền. Mỗi nhóm lưu mã, tên và mô tả; số thiết bị đang tham chiếu được hiển thị.

## Quy tắc nghiệp vụ

- BR-111: mã và tên là bắt buộc, không được chỉ chứa khoảng trắng; độ dài tuân theo giới hạn dữ liệu 60/200 ký tự. Mã nhóm viết hoa, chỉ gồm chữ/số và dấu gạch nối, ví dụ `EQ-AUDIO`.
- UC-63: chỉ đổi trạng thái hoạt động/ngừng hoạt động; không thay đổi hay xóa thiết bị đang tham chiếu.
- BR-118/BR-191: xóa nhóm chỉ được phép khi chưa có thiết bị tham chiếu; các bản ghi tài sản và lịch sử nghiệp vụ luôn được giữ nguyên.
- Mọi thao tác ghi được lưu vào audit log.

## API

- Quản lý cấp cao: `/api/v1/head-office/equipment-categories` — danh mục toàn chuỗi, không gửi `branchId`.
- Quản lý cơ sở chỉ đọc danh mục qua `GET /api/v1/branch-management/equipment-categories` để chọn nhóm cho thiết bị.
- `GET` hỗ trợ `search` và `active`; `POST` tạo; `PUT /{id}` sửa; `PUT /{id}/status` đổi trạng thái; `DELETE /{id}` xóa khi chưa có tham chiếu.

Schema `gcmb.asset_category` và khóa ngoại từ `gcmb.asset.category_id` đã có trong migration V1. V30 bổ sung mô tả. V36 cho phép `branch_id` NULL để biểu diễn nhóm toàn chuỗi và thêm unique index mã nhóm cho danh mục dùng chung. Nhóm cũ gắn với cơ sở vẫn được đọc để tương thích; nhóm mới tạo là danh mục toàn chuỗi. Backend phải chạy Flyway V36 trước khi triển khai giao diện/API mới.
