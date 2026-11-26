# Trợ lý kinh doanh QLCC — báo cáo theo ngày và nhân sự

Cập nhật local ngày 26/11/2026. Trợ lý Gemini hỗ trợ báo cáo theo ngày, nhân sự và tra cứu thâm niên. Cần khởi động lại backend để nạp công cụ mới và tải lại trang để cập nhật khung chat. Các lượt chat thâm niên sử dụng thêm trường `displayAnswer` trong phản hồi của API chat hiện có.

## Cách sử dụng

QLCC có tài khoản đang hoạt động có thể hỏi trực tiếp, ví dụ:

- “Doanh thu hôm nay của Hoa Bằng bao nhiêu?”
- “Chi phí và lợi nhuận ngày 20/11/2026 của Nguyễn Lương Bằng 86-88?”
- “So sánh doanh thu từng ngày từ 20/11/2026 đến 25/11/2026.”
- Sau khi hỏi một cơ sở: “Còn hôm qua?”
- “Cơ sở Hoa Bằng hiện có bao nhiêu nhân viên?” hoặc “Toàn hệ thống hiện có bao nhiêu nhân viên?”
- “Cơ sở nào có nhiều nhân viên nhất?” hoặc “Cơ sở nào có nhiều lễ tân nhất?”
- “Nhân viên nào làm lâu năm nhất toàn chuỗi?” hoặc “Ở Hoa Bằng, ai có thâm niên cao nhất?”
- “Tuần này cơ sở nào còn nhiều chỗ trống trong ca nhất?”
- “Tháng này mỗi cơ sở có bao nhiêu lượt vắng, thiếu chấm công và bao nhiêu giờ công?”

Backend cung cấp ngày hiện tại và múi giờ `Asia/Ho_Chi_Minh` để Gemini tính “hôm nay/hôm qua”, không lấy ngày trong lịch sử trò chuyện làm ngày hiện tại. Ngày phát sinh giao dịch được xác định theo múi giờ của từng cơ sở như báo cáo tài chính hiện có. Nếu không chỉ định cơ sở, phạm vi là toàn chuỗi; Gemini phải phân biệt các tên gần giống nhau và tiếp tục đúng cơ sở của câu trước.

Ngày hiện tại là số tạm tính đến thời điểm truy vấn. Kỳ đối chiếu là **trọn khoảng ngày liền trước có cùng số ngày**, không phải cùng số giờ đã trôi qua; không dùng để kết luận tăng/giảm tương đương khi hôm nay chưa kết thúc.

## Công cụ tra cứu chỉ đọc

Thêm `read_daily_business_report` vào danh sách công cụ Gemini được phép gọi:

- `start_date`: ngày bắt đầu dạng `YYYY-MM-DD`, bắt buộc trong khai báo công cụ.
- `end_date`: ngày cuối, tính cả ngày này; bỏ trống để xem một ngày.
- `branch_id`: UUID cơ sở đã có trong danh mục; bỏ trống để xem toàn chuỗi. Bao gồm dữ liệu của cơ sở ngừng hoạt động khi truy vấn lịch sử.

Backend kiểm tra quyền QLCC đang hoạt động, ngày hợp lệ từ năm 2000 đến hôm nay, thứ tự ngày và cơ sở tồn tại. Mỗi lần gọi tối đa 31 ngày; Gemini có thể chia nhỏ khoảng dài hơn trong giới hạn số lần gọi công cụ của một lượt chat. Không có SQL tùy ý, quyền ghi dữ liệu hoặc quyền truy cập mới.

Kết quả gồm tổng kỳ, từng ngày (kể cả ngày không phát sinh), tổng từng cơ sở, nhóm chi phí, kỳ đối chiếu, ngày giờ server và dấu hiệu ngày chưa kết thúc. Các trường gồm doanh thu hóa đơn, cọc tịch thu, tiền phòng, F&B, phụ phí quá giờ, doanh thu khác, số hóa đơn, chi phí vận hành, lương/ứng lương, đầu tư và lợi nhuận. Dữ liệu gửi tới Gemini dùng nhãn tiếng Việt; kết quả công cụ không đưa thông tin cá nhân khách hàng, tài khoản ngân hàng hoặc từng dòng giao dịch.

## Tra cứu nhân sự, ca làm và tiền lương tổng hợp

`read_staff_headcount` lấy số liệu hiện tại từ mô-đun Nhân sự. Trợ lý dùng `branch_id` của đúng cơ sở trong danh mục. Bỏ `branch_id` trả cả tổng hệ thống, hồ sơ chưa có cơ sở chính và bảng từng cơ sở trong một lần gọi. Bảng mặc định sắp xếp giảm dần theo số đang làm việc và gồm cả cơ sở không có người. Khi xếp hạng mặc định xét cơ sở đang hoạt động và báo đồng hạng. Mỗi mục có số theo trạng thái hồ sơ và theo vị trí như lễ tân, QLCS, kế toán.

Trợ lý mặc định trả số hồ sơ đang hoạt động (`ACTIVE`). Tổng số hồ sơ là chỉ tiêu riêng và có thể bao gồm nhân viên đã ngừng làm. Bộ lọc cơ sở dùng phân công cơ sở chính đang có hiệu lực, giống màn Nhân sự. Vì vậy người đã chuyển cơ sở được tính ở nơi đang được phân công. Số liệu phản ánh thời điểm tra cứu và chưa hỗ trợ quân số ở các kỳ quá khứ.

`read_workforce_activity` nhận `start_date`, `end_date` tính cả ngày cuối và `branch_id` tùy chọn. Mỗi lần gọi tối đa 31 ngày. Công cụ trả số ca lên lịch, số ca còn chỗ theo chỉ tiêu cấu hình, lượt phân ca, đi làm/vắng, giờ công hợp lệ, công chưa ghi hoặc chưa hợp lệ, công đã chốt và phản ánh đang mở. Khi bỏ cơ sở, một kết quả gồm tổng hệ thống và tổng của từng cơ sở có lịch hoặc chấm công trong kỳ. Công gắn với cơ sở của ca lịch sử. Ca tương lai chỉ phản ánh lịch phân ca. Chỉ ca đã kết thúc mới được tính vào số thiếu chấm công. Một người có thể xuất hiện ở nhiều lượt ca.

Chi lương và ứng lương đã ghi sổ lấy từ công cụ báo cáo tài chính theo ngày hoặc tháng hiện có. Số tiền này gộp hai loại chi. Dữ liệu không gồm bảng lương cá nhân, thông tin ngân hàng hay số dư phải trả. Trợ lý dùng số tổng hợp, phục vụ QLCC tra cứu. Công cụ không gửi tên, mã, liên hệ hay ghi chú riêng của nhân viên cho Gemini.

Phần điều phối nằm trong `BusinessAssistantService` và `BusinessAssistantContext`. `StaffService` dùng `StaffRepository` cho số lượng hồ sơ và `AttendanceRepository` cho ca làm/chấm công. Các công cụ chỉ đọc và kiểm tra quyền QLCC đang hoạt động. Không có API HTTP hay migration mới.

Kiểm tra PostgreSQL tạm đối chiếu số đếm với bộ lọc Nhân sự, kiểm tra xếp hạng một lần gọi, đồng hạng, cơ sở trống, trạng thái và vị trí, hồ sơ chuyển cơ sở, ca tương lai, chấm công hợp lệ, phản ánh, dữ liệu đã lưu của ca hủy và quyền QLCC. Tình huống mẫu không gọi Gemini thật.

Đối chiếu chỉ đọc với database local ngày 26/11/2026 trên 18 cơ sở đang hoạt động. Cơ sở có nhiều nhân viên đang làm việc nhất là GenZ Cinema Nguyễn Lương Bằng 86-88 (16 người). Truy vấn ca làm và chấm công cũng chạy thành công trên schema local. 20 test báo cáo và lương đạt.

## Tra cứu nhân viên có thâm niên cao nhất

`read_staff_tenure` tra hồ sơ đang hoạt động theo ngày vào công ty sớm nhất và tính thâm niên đến ngày hiện tại theo giờ Việt Nam. Nếu có `branch_id`, chỉ xét người có cơ sở chính hiện tại tương ứng. Nếu không, xét toàn hệ thống, kể cả người chưa có cơ sở chính. Ngày chuyển cơ sở là một mốc riêng. Nếu có nhiều người cùng ngày vào làm, hiển thị tối đa 20 tên theo thứ tự tên và ghi tổng số người đồng hạng khi vượt giới hạn.

QLCC đang hoạt động mới dùng được công cụ. `BusinessAssistantContext` hiển thị tên, cơ sở chính, ngày vào làm và thâm niên bằng dữ liệu từ `StaffService` và `StaffRepository`. `BusinessAssistantService` trả chi tiết trực tiếp cho giao diện trong `displayAnswer`. Gemini chỉ nhận phản hồi trung tính nếu cần tiếp tục xử lý công cụ khác. `BusinessAssistant.js` lưu `displayAnswer` theo lịch sử của tài khoản trên trình duyệt để mở lại. Các lượt tiếp theo chỉ gửi `answer` làm nội dung lịch sử trợ lý.

Đã kiểm tra trường hợp đồng hạng, hồ sơ nghỉ việc và chuyển cơ sở, quyền QLCC, mở lại cuộc chat và dữ liệu gửi theo câu hỏi tiếp theo bằng dữ liệu giả. Chưa gọi Gemini thật.

## Ý nghĩa số liệu

Tái sử dụng `MonthlyPerformanceService.overview(..., "prev", "day")` và `dailyRevenue(...)` cùng SQL repository hiện có:

- Doanh thu bằng giá trị ròng của hóa đơn đã tất toán cộng cọc tịch thu. Không cộng cọc đang giữ, cấp quỹ hoặc chuyển quỹ.
- Chi phí gồm chi phí vận hành được ghi nhận và các khoản lương/ứng lương theo báo cáo hiện có. Đầu tư được tách riêng, không trừ lại vào lợi nhuận.
- Chi phí phân bổ tháng nằm tại ngày ghi nhận của kỳ (thường đầu tháng). Đây không phải số tiền mặt thực chi đúng ngày đó; không tự chia đều chi phí tháng để tính một loại lợi nhuận ngày khác.
- Tiền phòng/F&B/phụ phí lấy từ dòng hóa đơn trước khi phân bổ giảm giá chung. Vì vậy doanh thu khác có thể âm do giảm giá; dùng tổng doanh thu ròng để đối chiếu tài chính.
- Số hóa đơn không phải số booking, số khách hoặc tỷ lệ lấp đầy. Công cụ này không cung cấp kênh bán theo ngày, danh sách giao dịch cá nhân hay ảnh chụp lịch sử phòng/sự cố. Không được suy diễn các chỉ tiêu đó từ số hóa đơn.
- Chỉ tính dữ liệu vận hành đã liên kết với báo cáo; không tự cộng các file nguồn chưa liên kết. Ngày không có phát sinh trả số 0, không đồng nghĩa hệ thống không lưu dữ liệu của kỳ đó.

## File thay đổi và kiểm tra

- `backend/src/main/java/com/gcmb/backend/dashboard/BusinessAssistantContext.java`: ngày giờ server, kiểm tra phạm vi ngày, tổng hợp báo cáo ngày từ service hiện có, giới hạn trường được trả về.
- `backend/src/main/java/com/gcmb/backend/dashboard/BusinessAssistantService.java`: khai báo/điều phối công cụ, nhãn tiếng Việt, hướng dẫn dùng đúng ngày và cách so sánh.
- `README.md` và tài liệu này: hướng dẫn bàn giao.

Đã kiểm tra:

- Backend compile thành công với Java 17.
- 15 test hiện có đạt: `MonthlyPerformanceServiceTest` (9), `BranchDashboardComparisonTest` (6).
- Self-check dùng PostgreSQL fixture riêng và repository/service thật: quyền truy cập; phạm vi cơ sở/toàn chuỗi; cơ sở ngừng hoạt động; ranh giới ngày, tháng, năm, năm nhuận và múi giờ; hóa đơn tất toán/nháp; giảm giá; cọc tịch thu/đang giữ; OPEX, lương, CAPEX, chuyển quỹ; ngày ghi nhận phân bổ; ngày trống, hóa đơn 0đ; giới hạn 31 ngày; khai báo/điều phối công cụ và nhãn dữ liệu. Fixture đã dừng; không thay đổi database dự án.

Self-check và log nằm trong thư mục bị Git bỏ qua `backend/target/assistant-daily-2026-11-26/`. Chưa gọi Gemini thật hoặc kiểm thử toàn luồng trên giao diện; sau khi khởi động lại backend có thể dùng các câu hỏi mẫu ở trên để kiểm tra câu trả lời thực tế. Mô hình vẫn phải gọi đúng công cụ để nhận số liệu; chưa có cơ chế định tuyến ngày độc lập với mô hình.
