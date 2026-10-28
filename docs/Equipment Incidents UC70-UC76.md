# Quản lý sự cố và điều chuyển thiết bị UC70–UC76

## Phạm vi triển khai

- UC70: Lễ tân hoặc quản lý cơ sở báo sự cố cho thiết bị đang hoạt động tại cơ sở được phân quyền.
- UC71: Quản lý cơ sở ghi kết quả, đơn vị sửa, chi phí thực tế, ngày hoàn tất và tình trạng thiết bị sau sửa chữa.
- UC72: Tìm kiếm, lọc, xem tiến độ và chi tiết sự cố theo cơ sở.
- UC73: Quản lý cơ sở chọn sửa chữa, thay thế hoặc không cần xử lý; nhập ghi chú đánh giá. Sự cố ảnh hưởng khả dụng phòng ghi nhận trạng thái phòng bảo trì theo BR-43.
- UC74: Chỉ quản lý cấp cao tạo yêu cầu điều chuyển giữa hai cơ sở; cơ sở thiết bị chưa đổi tại thời điểm tạo.
- UC75: Quản lý cơ sở nguồn xác nhận bàn giao; tài sản chuyển sang trạng thái đang vận chuyển.
- UC76: Quản lý cơ sở đích xác nhận tiếp nhận và vị trí; chỉ khi xác nhận, cơ sở/vị trí thiết bị mới đổi và lịch sử điều chuyển được giữ lại.

## Quy tắc nghiệp vụ

Áp dụng BR-41/42/43/44/45/46/47/48/49/50/51, BR-110/111/115/116/165/177/196/200/205 trong Report-3. Phạm vi chi nhánh được kiểm tra ở backend; nhân viên cơ sở không thể báo cáo hoặc xác nhận ở cơ sở khác. Báo cáo lưu người tạo và thời điểm; sửa chữa được nghiệm thu trước khi thiết bị có thể trở lại trạng thái sử dụng bình thường. Việc sửa thiết bị không tự mở lại phòng.

## API

- `GET /api/v1/branch-management/equipment-incidents` — tìm kiếm/lọc sự cố.
- `GET /api/v1/branch-management/equipment-incidents/assets` — lựa chọn tài sản đang hoạt động.
- `POST /api/v1/branch-management/equipment-incidents` — báo sự cố.
- `PUT /api/v1/branch-management/equipment-incidents/{id}/assessment` — đánh giá và chọn phương án.
- `POST /api/v1/branch-management/equipment-incidents/{id}/repair-outcome` — ghi nhận kết quả sửa chữa, chi phí, ngày hoàn tất và trạng thái sau sửa.
- `POST /api/v1/head-office/equipment-transfers` — tạo yêu cầu điều chuyển.
- `GET /api/v1/{branch-management|head-office}/equipment-transfers` — xem phiếu theo cơ sở liên quan.
- `PUT /api/v1/branch-management/equipment-transfers/{id}/handover` — xác nhận bàn giao nguồn.
- `PUT /api/v1/branch-management/equipment-transfers/{id}/receipt` — xác nhận tiếp nhận và phòng đích.

## Ghi chú

Chứng cứ báo sự cố là tùy chọn; kết quả sửa chữa bắt buộc có chứng từ. File được kiểm tra nội dung và dung lượng theo BR-115/BR-116. Màn hình hiện nhận một file mỗi lần báo cáo (số lượng này nằm trong giới hạn tối đa năm ảnh của Lễ tân theo BR-196). Thanh toán chi phí sửa chữa vẫn thuộc quy trình kế toán hiện có, theo BR-47; tạo liên kết đề nghị thanh toán từ kết quả sửa chữa là bước tích hợp tiếp theo.
