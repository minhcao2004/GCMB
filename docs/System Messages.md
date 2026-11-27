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
- Đánh giá sự cố vẫn cho phép `NO_ACTION`. DEF-UT-544 không thuộc thay đổi thông báo này.

Các test case còn dựa trên quy tắc cũ cần được rà soát riêng; chỉ cập nhật Fixed sau khi retest. Bộ kiểm tra `InventorySystemMessagesTest` xác minh thông báo, phạm vi quyền và các nhánh từ chối không ghi dữ liệu.

## Cập nhật ngày 27/11/2026

- BR-119 và MSG04 áp dụng cho hồ sơ tài khoản, nhân viên, cơ sở và số liên hệ của booking. Sau khi bỏ khoảng trắng ở hai đầu, số điện thoại gồm một hoặc nhiều chữ số và có thể có một dấu `+` ở đầu. Giới hạn lưu trữ là 30 ký tự, tính cả dấu `+`. Ví dụ hợp lệ gồm `1234567` và `+84901234567`.
- Khi phân công quản lý hoặc đổi trạng thái cơ sở, thông báo phân biệt thiếu tài khoản, tài khoản không đủ điều kiện, cơ sở ngừng hoạt động và lý do thiếu hoặc quá dài.
- Quy tắc giá nêu rõ lỗi ngày áp dụng, khoảng ngày hiệu lực và mã trùng. Combo thiếu thời lượng và thiết bị đầu tiên thiếu mã dùng thông báo trường bắt buộc.
- Lỗi đổi combo nêu tên combo khi xác định được. Thông báo từ chối hủy booking hoặc thu cọc nêu đúng thao tác. Gửi lại yêu cầu hoàn cọc đang chờ trả về MSG74.
- Khi điều chỉnh ca, số chỗ bằng 0 và số chỗ thấp hơn số người đã đăng ký có thông báo riêng. Khi sửa công đã chốt, thông báo nêu tháng và năm.
- MSG24 cho thao tác tạo, xem danh sách và sửa booking được kiểm tra tại dịch vụ. Các thao tác này yêu cầu tài khoản Lễ tân có phân công cơ sở.
- MSG67 và BR-157 yêu cầu kế toán nhập mã giao dịch hoặc tải tệp minh chứng khi ghi nhận đã hoàn cọc, áp dụng cho booking đã hủy và hoàn cọc dư từ checkout.
- MSG95 và BR-90 yêu cầu chứng từ thanh toán khi kế toán duyệt và ghi nhận chi trong cùng một lần xác nhận.

## Ngày hiệu lực và trùng quy tắc giá phòng

- Theo BR-146, ngày kết thúc có hiệu lực đến hết ngày đã chọn. Có thể lưu giá cho một ngày bằng cách chọn ngày bắt đầu và kết thúc giống nhau. PostgreSQL vẫn lưu biên kết thúc loại trừ, nên quy tắc đã lưu tiếp tục áp dụng cho các ngày trước đây. API quản lý giá trả ngày áp dụng cuối cùng.
- Theo BR-228, khi tạo hoặc sửa quy tắc giá toàn hệ thống, hệ thống kiểm tra quy tắc đang hoạt động của cùng hạng phòng. Chỉ báo trùng khi có ít nhất một ngày cùng áp dụng và hai khung giờ giao nhau. Hai khung giờ liền nhau có thể cùng tồn tại. Lỗi trả HTTP 409 và nêu tên mức giá bị trùng. Nếu sửa bị từ chối, phiên bản đang dùng vẫn hoạt động.
- Báo giá và tra đơn giá theo giờ dùng các ngày trong tuần đã cấu hình. Quy tắc giá riêng theo cơ sở tiếp tục được ưu tiên theo cơ chế hiện có.

## Đặt phòng theo giờ, combo và đổi phòng

- BR-153 và MSG62 áp dụng khung giờ bắt đầu được cấu hình cho combo theo múi giờ của cơ sở. Giờ bắt đầu nằm trong khung, kể cả khung qua nửa đêm. Giờ kết thúc có thể nằm ngoài khung. Màn gia hạn dùng danh sách combo phù hợp do backend trả về.
- Theo BR-151, đặt phòng tính giờ cho phép thời lượng dưới một giờ và vẫn giới hạn tối đa 24 giờ. Giao diện tạo, sửa và nhận khách vãng lai nhập số giờ tương ứng với số phút nguyên. Ví dụ, 0,5 giờ tương ứng 30 phút. Bước làm tròn tiền thuê lấy từ quy tắc giá.
- Khi đổi phòng, lễ tân chọn nguyên nhân và nhập lý do cụ thể. Nếu lý do thiếu hoặc chỉ có khoảng trắng, backend trả MSG02 trước khi ghi nhận chặng phòng hoặc phí đổi phòng.

## Gọi lặp lệnh xuất kho quà combo

- Nếu thao tác xuất kho được gọi lại cho quà combo đã xuất, hệ thống trả HTTP 409 với thông báo **“Quà combo đã được xuất kho. Vui lòng kiểm tra kết quả hiện có.”** trước khi ghi thêm dòng xuất. Yêu cầu hoàn tất checkout gửi lại cho bill đã tất toán vẫn trả về bill hiện có.

## Chỉnh ca tuần tới

- Lịch tuần tới tự mở các ca còn thiếu khi được xem lần đầu. Quản lý cơ sở có thể mở hoặc đóng đăng ký cho ca của tuần hiện tại và tuần tới. Khi xem tuần tới, dùng nút **Chỉnh tuần tới**.
- Mỗi ca giữ 3 chỗ. Tắt đăng ký giữ lại những người đã đăng ký. Điều kiện khóa ca đã kết thúc, có chấm công hoặc thuộc kỳ công đã chốt áp dụng cho cả hai tuần.
