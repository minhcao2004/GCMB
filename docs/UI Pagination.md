# Phân trang giao diện

## Quy tắc

- Danh sách có dòng dài, nhiều thông tin hoặc thao tác: 5 bản ghi/trang.
- Danh sách ngắn, danh mục đơn giản: 10 bản ghi/trang.
- Bộ lọc được áp dụng trước khi chia trang. Tổng số bản ghi và tổng tiền vẫn tính trên toàn bộ dữ liệu trong phạm vi lọc; xuất báo cáo không giới hạn ở trang đang xem.
- Lịch tuần, sơ đồ phòng, biểu đồ và các dòng đang nhập trong biểu mẫu không chia trang như danh sách tra cứu.
- Đổi trang chỉ đổi phần hiển thị, không ghi dữ liệu nghiệp vụ.

## Thành phần dùng chung

`frontend/src/shared/components/Pagination.js` cung cấp `usePagination`, `Pagination`, `PaginatedTable` và `PaginatedList`; giao diện nằm trong `frontend/src/shared/styles/pagination.css`.

Danh sách đã được chia trang từ API hoặc từ state của màn hình dùng `Pagination` để tránh chia dữ liệu hai lần. `PaginatedTable` và `PaginatedList` phù hợp với danh sách đã tải đầy đủ. Khi chuyển bộ lọc hoặc phạm vi dữ liệu, màn hình cần đặt lại trang hoặc truyền `resetKey` phù hợp. Dòng chi tiết đi cùng bản ghi cha không được tính thành một bản ghi độc lập.

## Phạm vi tích hợp

Các nhóm màn hình sử dụng thành phần dùng chung gồm tài khoản/nhật ký, đặt phòng và thu/hoàn cọc, cơ sở/phòng, báo cáo, quỹ/chi phí/bàn giao, kho/F&B/thiết bị và nhân sự/công/lương. Đây là phân trang hiển thị; không thay đổi hợp đồng API thành phân trang phía máy chủ.

- Kho hàng gộp quản lý danh mục và tồn kho trong một bảng, 5 mặt hàng/trang. Thêm/sửa và xem lịch sử đều quay lại màn Kho hàng.
- Giao dịch kế toán: 5 giao dịch/trang, giữ bộ lọc và định dạng bảng của nhánh main. Lịch sử điều chỉnh dài: 5 lần/trang.
- Chọn quà combo dùng chung cho đặt phòng và khách đang sử dụng phòng: 10 lựa chọn/trang, giữ kiểm tra tồn và số lượng đã chọn.
- Bảng chi phí: 5 yêu cầu/trang; chi tiết phân bổ: 10 tháng/trang.
- Booking Lễ tân: vùng dòng đặt phòng cuộn độc lập trên desktop. Thanh phân trang nằm dưới bảng và chỉ chiếm chiều cao nội dung của chính nó. Đã kiểm tra với 7 booking, hiển thị 5 dòng ở trang đầu và 2 dòng ở trang sau.

## Kiểm tra khi thay đổi

Kiểm tra trang đầu/cuối, bộ lọc, danh sách rỗng, thao tác trên trang sau, dữ liệu bị xóa làm giảm số trang, và tổng tiền/xuất báo cáo. Kiểm tra riêng các nhóm bản ghi có dòng chi tiết và bộ chọn dùng trong nhiều màn hình.

Kết quả kiểm tra sau merge ngày 23/11/2026 được ghi trong [biên bản merge](Merge%20Resolution%202026-11-23.md).
