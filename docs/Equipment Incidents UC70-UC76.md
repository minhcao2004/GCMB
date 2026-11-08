# Quản lý sự cố và điều chuyển thiết bị UC70–UC76

## Phạm vi triển khai

- UC70: Lễ tân hoặc quản lý cơ sở báo sự cố cho thiết bị đang hoạt động tại cơ sở được phân quyền; bắt buộc ghi mức độ nghiêm trọng (`LOW/MEDIUM/HIGH/CRITICAL`), mức độ khẩn cấp (`LOW/NORMAL/HIGH/URGENT`), phương án `REPAIR` hoặc `REPLACE`, ngày dự kiến hoàn tất và đơn vị sửa chữa/thay thế. Có thể ghi chi phí dự kiến; phương án thay thế phải mô tả thiết bị/hạng mục thay thế. Báo cáo lễ tân vào `WAITING_APPROVAL`; báo cáo do quản lý cơ sở tạo vào thẳng `IN_PROGRESS`.
- UC71: Quản lý cơ sở duyệt hoặc từ chối báo cáo lễ tân, bắt buộc lưu ghi chú quyết định. Từ chối chuyển sang `REJECTED`; duyệt chuyển sang `IN_PROGRESS` và cập nhật tình trạng thiết bị theo phương án đã chọn. Lễ tân không thể duyệt, từ chối, cập nhật tiến độ hay đóng sự cố.
- Khi sửa chữa/thay thế xong, quản lý cơ sở cập nhật đơn vị thực hiện, chi phí thực tế, ngày hoàn tất, tình trạng thiết bị, kết quả và chứng từ. Ngày bắt đầu sửa được lấy từ mốc tiến trình `IN_PROGRESS` đầu tiên (hoặc ngày tạo sự cố nếu chưa có mốc), để thỏa điều kiện ngày hoàn tất không trước ngày bắt đầu. Hệ thống chuyển sang `VERIFIED`; quản lý cơ sở xác nhận đóng riêng, lưu `closed_by` và `closed_at`. Nhật ký tiến trình lưu người ghi, thời điểm, trạng thái, ghi chú và ngày dự kiến hoàn tất.
- UC72: Tìm kiếm, lọc, xem tiến độ và chi tiết sự cố theo cơ sở.
- Quản lý cấp cao xem được báo cáo của tất cả cơ sở cùng thiết bị, cơ sở phát sinh, chi phí dự kiến/thực tế, đơn vị sửa/thay, trạng thái và tiến trình mới nhất; có thể lọc một cơ sở nếu cần.
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
- `PUT /api/v1/branch-management/equipment-incidents/{id}/review` — quản lý cơ sở duyệt/từ chối kèm ghi chú.
- `GET /api/v1/{branch-management|reception}/equipment-incidents/{id}/progress` — xem nhật ký tiến trình.
- `PUT /api/v1/reception/equipment-incidents/{id}/suggestion` — lễ tân lưu đề xuất phương án sửa.
- `PUT /api/v1/branch-management/equipment-incidents/{id}/progress` — quản lý cơ sở cập nhật trạng thái, ghi chú và ngày dự kiến hoàn tất.
- `PUT /api/v1/branch-management/equipment-incidents/{id}/close` — quản lý cơ sở đóng sự cố sau khi cập nhật hoàn tất.
- `PUT /api/v1/branch-management/equipment-incidents/{id}/assessment` — đánh giá và chọn phương án.
- `POST /api/v1/branch-management/equipment-incidents/{id}/repair-outcome` — ghi nhận kết quả sửa chữa, chi phí, ngày hoàn tất, hóa đơn và chứng từ chuyển khoản.
- `GET /api/v1/{branch-management|head-office|reception}/equipment-incidents/{id}/repair-documents/{invoice|transfer-proof}` — xem chứng từ theo quyền và phạm vi cơ sở.
- `POST /api/v1/head-office/equipment-transfers` — tạo yêu cầu điều chuyển.
- `GET /api/v1/{branch-management|head-office}/equipment-transfers` — xem phiếu theo cơ sở liên quan.
- `PUT /api/v1/branch-management/equipment-transfers/{id}/handover` — xác nhận bàn giao nguồn.
- `PUT /api/v1/branch-management/equipment-transfers/{id}/receipt` — xác nhận tiếp nhận và phòng đích.

## Ghi chú

Chứng cứ báo sự cố là tùy chọn; kết quả sửa chữa bắt buộc có chứng từ. File được kiểm tra nội dung và dung lượng theo BR-115/BR-116. Màn hình báo cáo nhận tối đa một file chứng cứ cho sự cố; kết quả sửa chữa nhận một hóa đơn và một chứng từ chuyển khoản (số lượng này nằm trong giới hạn tối đa năm ảnh của Lễ tân theo BR-196). Thanh toán chi phí sửa chữa vẫn thuộc quy trình kế toán hiện có, theo BR-47; tạo liên kết đề nghị thanh toán từ kết quả sửa chữa là bước tích hợp tiếp theo.

Migration V37 mở rộng tập trạng thái sự cố để hỗ trợ chờ duyệt và từ chối. V38 thêm đơn vị sửa chữa/thay thế, mô tả hạng mục thay thế và chi phí dự kiến. Cần áp dụng Flyway V39 trước khi triển khai backend mới.

## Khuyến nghị vận hành

- Dùng severity để đo hậu quả/khu vực ảnh hưởng; dùng priority để quyết định thứ tự phản hồi theo thời gian. Ví dụ thiết bị hỏng nhẹ nhưng phục vụ phòng sắp check-in có thể severity thấp, priority cao.
- Bắt buộc ngày dự kiến hoàn tất ngay khi đã có đánh giá; cập nhật ngày và ghi chú mỗi lần nhà cung cấp thay đổi lịch. Đừng xóa các mốc cũ khỏi nhật ký.
- Dùng mã `ASSET-{BRANCH}-{NNNN}` cho tài sản và mã nhóm `EQ-{LOẠI}`. Cấp số tăng dần theo cơ sở, không tái sử dụng mã đã xóa/đã nghỉ hưu.
- Lễ tân báo cáo, chọn phương án và hạn dự kiến; quản lý cơ sở duyệt/từ chối, cập nhật tiến trình, chi phí, nghiệm thu rồi đóng. Báo cáo có severity `CRITICAL` hoặc priority `URGENT` cần được ưu tiên xử lý ngay và kiểm tra tình trạng phòng liên quan.
