# Quy tắc xác thực giá phòng và phòng

Tài liệu này ghi lại hợp đồng xác thực backend được áp dụng cho quản lý hạng phòng, giá, combo và phòng trong GCMB. Các thông báo lỗi được trả dưới dạng HTTP 400; lỗi trùng mã hoặc xóa bị chặn trả HTTP 409. Dữ liệu và audit log không được ghi khi xác thực thất bại.

## Hạng phòng

- Mã bắt buộc, từ 2 đến 60 ký tự; chỉ dùng chữ không dấu, số, `_` hoặc `-`.
- Tên bắt buộc, tối đa 200 ký tự sau khi bỏ khoảng trắng đầu/cuối.
- Sức chứa phải là số nguyên dương; loại dịch vụ phải là `CINEMA` hoặc `MUSIC_BOX`.

## Quy tắc giá

- Mã, tên, hạng áp dụng, ít nhất một ngày trong tuần, ngày bắt đầu, khung giờ và đơn giá được kiểm tra riêng để trả đúng thông báo theo trường.
- Ngày kết thúc được để trống hoặc bằng/sau ngày bắt đầu.
- Đơn giá không âm và phải là số tiền nguyên đồng.
- Hai mốc giờ bằng nhau biểu thị thời hạn 24 giờ và được lưu theo khoảng `[00:00, 24:00)`. Mốc kết thúc `00:00` sau một giờ bắt đầu muộn hơn được hiểu là `24:00` cùng ngày.

## Combo

- Mã/tên, hạng áp dụng, thời lượng, khung giờ và giá được kiểm tra riêng. Giá không âm và phải là số tiền nguyên đồng.
- Hai mốc giờ bằng nhau biểu thị 24 giờ và được chuẩn hóa thành `[00:00, 24:00)` trước khi lưu. Mốc kết thúc `00:00` sau một giờ bắt đầu muộn hơn được lưu là `24:00`.
- Khi sửa, request phải cung cấp hạng áp dụng; backend không tự thay hạng bị bỏ trống bằng hạng cũ.
- Mã trùng trong các combo đang hoạt động trả HTTP 409 với mã combo.
- Combo gồm giá phòng, điều khoản, số lượng đồ uống tặng và số lượng đồ ăn tặng (mỗi loại 0–20); không gắn tên mặt hàng cụ thể vì danh mục khác nhau giữa các cơ sở.
- Khi tạo booking, backend lưu snapshot định mức tặng theo phiên bản combo. Tại màn hình check-in, lễ tân chọn đủ số món ăn và đồ uống từ danh mục hàng `PACKAGED` của cơ sở; các món cần liên kết tồn kho và dùng `AUTO_ON_SALE`.
- Backend tạo order quà giá 0 khi check-in; các món quà không cộng thêm vào bill checkout. Khi bill được thanh toán đủ, kho trừ số lượng quà trong cùng giao dịch hoàn tất thanh toán. Nếu lựa chọn không hợp lệ, check-in bị rollback; nếu tồn không đủ lúc thanh toán, thanh toán chưa thể hoàn tất.
- Hủy booking hoặc đánh dấu no-show trước khi check-in không tạo order quà và không trừ kho.

## Phòng

- Tạo/sửa yêu cầu mã, tên, loại phòng, tầng và sức chứa. Tên tối đa 200 ký tự; sức chứa phải dương.
- Trạng thái vận hành và lý do thay đổi được xác thực riêng trước khi ghi lịch sử.
- Thiếu cơ sở gắn với thông báo quyền theo thao tác. Mã phòng trùng được báo kèm mã và tên cơ sở.
- Không xóa phòng còn lịch sử hoặc bản ghi tham chiếu; trả HTTP 409 và giữ dữ liệu liên quan.
