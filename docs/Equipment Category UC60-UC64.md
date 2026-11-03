# Quản lý nhóm thiết bị UC60–UC64

## Phạm vi

Màn hình nhóm thiết bị triển khai năm use case trong Report 3: UC-60 tạo nhóm, UC-61 xem/tìm kiếm/lọc, UC-62 cập nhật thông tin, UC-63 đổi trạng thái hoạt động và UC-64 xóa nhóm chưa được sử dụng. Quản lý cơ sở làm việc trong cơ sở được gán; quản lý cấp cao chọn cơ sở cần quản lý. Các nhóm thuộc từng cơ sở và mã nhóm duy nhất trong phạm vi cơ sở. Mỗi nhóm lưu mã, tên và mô tả; số thiết bị đang tham chiếu được hiển thị trong danh sách.

## Quy tắc nghiệp vụ

- BR-111: mã và tên là bắt buộc, không được chỉ chứa khoảng trắng; độ dài tuân theo giới hạn dữ liệu 60/200 ký tự.
- UC-63: chỉ đổi trạng thái hoạt động/ngừng hoạt động; không thay đổi hay xóa thiết bị đang tham chiếu.
- BR-118/BR-191: xóa nhóm chỉ được phép khi chưa có thiết bị tham chiếu; các bản ghi tài sản và lịch sử nghiệp vụ luôn được giữ nguyên.
- Mọi thao tác ghi được lưu vào audit log.

## API

- Quản lý cơ sở: `/api/v1/branch-management/equipment-categories`
- Quản lý cấp cao: `/api/v1/head-office/equipment-categories?branchId={id}`
- `GET` hỗ trợ `search` và `active`; `POST` tạo; `PUT /{id}` sửa; `PUT /{id}/status` đổi trạng thái; `DELETE /{id}` xóa khi chưa có tham chiếu.

Schema `gcmb.asset_category` và khóa ngoại từ `gcmb.asset.category_id` đã có trong migration V1. Migration V21 bổ sung trường mô tả cho nhóm thiết bị mà không sửa các migration đã áp dụng.
