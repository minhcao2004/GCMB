# Đồng bộ System Messages — 22/11/2026

Nguồn: phần **System Messages** trong [Report 3 SRS](https://docs.google.com/document/d/1BGGrchT4eJBWgK1hG9q192_7w0dbestb/edit).

## Phạm vi

Đồng bộ các thông báo của F&B, kho, nhóm thiết bị, tài sản, sự cố/sửa chữa và điều chuyển trong nhóm defect từ DEF-UT-446 trở xuống. Không cập nhật Google Docs hoặc sheet defect trong đợt này.

- MSG02/03: tách trường bắt buộc và giới hạn ký tự; chuỗi chỉ có khoảng trắng được coi là thiếu.
- MSG24: thông báo quyền nêu đúng thao tác và đối tượng; giữ nguyên các vai trò được phép.
- MSG38: lỗi mã trùng nêu mã và phạm vi duy nhất. Mặt hàng kho duy nhất theo cơ sở; nhóm thiết bị trong danh mục toàn chuỗi; mã tài sản duy nhất toàn chuỗi. Chỉ lỗi unique key dùng thông báo mã trùng, không gán lỗi khóa ngoại thành mã trùng.
- MSG43: lỗi ghi tại cơ sở tạm ngừng có tên cơ sở và hướng dẫn chỉ xem dữ liệu.
- MSG45/47: giá bán âm và giá bán có phần thập phân có thông báo riêng, khác thông báo thiếu giá bán.
- MSG77: tồn kho thay đổi yêu cầu kiểm tra lại trước khi xác nhận **điều chỉnh**.
- MSG82/83/104: giữ thông báo bàn giao chưa hoàn tất, sửa chữa chưa nghiệm thu và bảo toàn lịch sử khi xóa.
- Form F&B, tạo mặt hàng trong phiếu nhập, nhóm và tài sản hiển thị cùng mẫu thông báo bắt buộc với backend.

API giữ nguyên đường dẫn và cấu trúc phản hồi. Điều chuyển bổ sung truy vấn đọc cơ sở nguồn/đích để phân biệt lỗi quyền (403, MSG24) với trạng thái chưa hợp lệ (409); các điều kiện ghi trong SQL vẫn giữ nguyên.

## Các luồng giữ nguyên

- Theo yêu cầu trực tiếp: toàn bộ thông báo và nghiệp vụ thu/hoàn cọc giữ nguyên, bao gồm xác nhận chuyển khoản thủ công và cách làm tròn cọc.
- Tạo mặt hàng kho vẫn tự sinh mã; nhóm thiết bị chỉ quản lý cấp cao được ghi.
- Không mở thêm quyền báo sự cố/ghi kết quả sửa chữa cho quản lý cấp cao.
- Đánh giá sự cố vẫn cho phép `NO_ACTION`; công bố ca vẫn chỉ cho tuần hiện tại. DEF-UT-544 không thuộc thay đổi thông báo này.

Các test case còn dựa trên quy tắc cũ cần được rà soát riêng; chỉ cập nhật Fixed sau khi retest. Bộ kiểm tra `InventorySystemMessagesTest` xác minh thông báo, phạm vi quyền và các nhánh từ chối không ghi dữ liệu.
