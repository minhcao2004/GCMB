# UC39–UC40: Đổi phòng và gia hạn thời gian sử dụng

API gốc: `/api/v1/reception`. Các thao tác dùng booking đang ở trạng thái `IN_HOUSE`; backend tự lấy phạm vi chi nhánh từ tài khoản lễ tân.

## UC39 — Đổi phòng khi khách đang sử dụng

Lễ tân có thể đổi phòng khi phòng hiện tại gặp sự cố hoặc khi khách yêu cầu. Phòng đích phải đang hoạt động, đủ sức chứa và còn trống từ thời điểm đổi đến giờ trả dự kiến. Backend đóng allocation cũ tại thời điểm đổi, ghi `actual_end`, sau đó mở allocation mới tại chính thời điểm đó. Hộp thoại chỉ yêu cầu phòng mới và ghi chú không bắt buộc; giao diện gửi `cause=CUSTOMER` để áp dụng giá của hạng phòng mới cho phần còn lại.

Phí được tách theo thời lượng từng chặng. Phần đã sử dụng giữ giá của phòng/combo cũ. Nếu khách yêu cầu đổi, phần thời gian còn lại dùng combo cùng tên và thời lượng của hạng phòng mới nếu combo đó được cấu hình; nếu không có combo tương ứng, hệ thống báo giá theo giờ cho phần còn lại. Nếu đổi vì sự cố của cơ sở, phần còn lại giữ nguyên giá đã chốt, không tăng phí chỉ vì phải thay phòng.

Ví dụ theo menu: combo 4 giờ King giá 326.000đ, combo tương ứng Queen giá 296.000đ. Sau 2 giờ, khách yêu cầu đổi sang Queen: 2 giờ King = 163.000đ, 2 giờ Queen = 148.000đ, tổng 311.000đ. Nếu phải chuyển vì lỗi của cơ sở, tổng vẫn là 326.000đ.

| Method | Endpoint | Mục đích |
| --- | --- | --- |
| `GET` | `/bookings/{bookingId}/stay-options` | Phòng còn trống, combo gia hạn và thông tin chặng phòng hiện tại. |
| `GET` | `/bookings/{bookingId}/room-change-quote?targetRoomId=…&cause=CUSTOMER` | Tính trước số tiền đã dùng, phần còn lại và khoản điều chỉnh. |
| `POST` | `/bookings/{bookingId}/change-room` | Ghi nhận đổi phòng. |

Request đổi phòng:

```json
{
  "targetRoomId": "00000000-0000-0000-0000-000000000000",
  "cause": "CUSTOMER",
  "reason": "Khách muốn đổi từ King sang Queen"
}
```

`cause` nhận `CUSTOMER` hoặc `BRANCH_FAULT`; `reason` là ghi chú tùy chọn. Giao diện lễ tân luôn gửi `CUSTOMER`; API vẫn hỗ trợ cả hai giá trị cho các client khác. Response có `previousAmount`, `updatedAmount`, `adjustmentAmount` và booking chi tiết sau cập nhật.

Trong hộp thoại lễ tân, mã booking tự xuống dòng khi cần và phòng hiện tại được làm nổi bật. Phần đổi phòng chỉ hiển thị trường **Ghi chú**; ghi chú có thể để trống. Request vẫn giữ trường `cause` để tương thích hợp đồng API, nhưng giao diện gửi `CUSTOMER` mặc định và không cho đổi chính sách giá bằng lựa chọn nguyên nhân.

## UC40 — Gia hạn thời gian sử dụng

Khi sắp hết hoặc đã đến giờ kết thúc, lễ tân có thể mua thêm combo đang áp dụng cho hạng phòng hiện tại hoặc bật **đồng hồ giờ lẻ**. Combo dùng đúng giá combo đã cấu hình và cập nhật giờ trả dự kiến. Combo ngày chỉ dùng khi phần gia hạn bắt đầu lúc 07:00; combo đêm chỉ dùng khi bắt đầu lúc 23:00.

Đồng hồ giờ lẻ được lưu trên máy chủ, chạy qua thao tác đóng hộp thoại hoặc tải lại trang. Nếu bật trước giờ trả dự kiến, đồng hồ tính tiền từ giờ trả; nếu bật sau, tính từ thời điểm bấm bắt đầu. Giá giờ lẻ được chốt từ price rule tại thời điểm bắt đầu tính. Lễ tân bấm **Dừng tính giờ** khi khách kết thúc phần gia hạn; backend lấy giờ hệ thống, làm tròn *thời gian thực dùng* lên phút nguyên, tính giá theo tỷ lệ số phút/60 và làm tròn số tiền đến đồng nguyên. Bước làm tròn giờ của price rule không áp dụng cho đồng hồ này: ví dụ 30 phút bằng nửa giá một giờ. Số tiền chạy trên frontend chỉ là tạm tính; số tiền chốt được lưu thành `booking_charge`.

Khi bật đồng hồ, backend giữ allocation phòng đến booking kế tiếp hoặc tối đa 24 giờ, tùy mốc nào đến trước. Giao diện hiển thị hạn giữ phòng. Không thể đổi phòng hay mua combo khi đã có phiên giờ lẻ. Nếu dừng sau hạn giữ, backend vẫn chốt đủ số phút thực dùng; sự kiện dừng ghi `pastReservationLimit=true` để đối soát lịch phòng. Nút dừng đóng băng thời gian và tạo khoản phụ thu giờ lẻ một lần. Booking vẫn ở trạng thái `IN_HOUSE`, allocation vẫn `OCCUPIED`, giờ checkout thực tế và các khoản cọc không đổi. Luồng checkout, lập hóa đơn và thanh toán thuộc UC khác.

Thông tin chặng phòng hiện tại gồm combo đã tính phí (nếu có) và thời lượng allocation đang sử dụng. Hộp thoại gia hạn đặt các dữ kiện này cạnh giờ hẹn ban đầu, giờ check-in thực tế và giờ trả hiện tại; nếu API tùy chọn chưa trả dữ kiện, giao diện lấy lại từ chi tiết booking và allocation đang dùng. Nút chọn combo vẫn mở được; nếu không có combo áp dụng, hộp thoại nêu rõ lý do và cho chọn đồng hồ giờ lẻ.

| Method | Endpoint | Mục đích |
| --- | --- | --- |
| `POST` | `/bookings/{bookingId}/extension-quote` | Báo giá trước khi xác nhận. |
| `POST` | `/bookings/{bookingId}/extend` | Gia hạn và ghi khoản phí. |
| `POST` | `/bookings/{bookingId}/hourly-meter/start` | Bật đồng hồ, snapshot đơn giá và giữ phòng. |
| `POST` | `/bookings/{bookingId}/hourly-meter/stop` | Dừng đồng hồ và chốt khoản phụ thu vào booking. |

Request mua combo:

```json
{ "pricingMode": "COMBO", "comboId": "00000000-0000-0000-0000-000000000000", "hours": null }
```

`start` và `stop` không cần body. `BookingDetail` trả thêm `meter` (`status`, `billedFrom`, `hourlyPrice`, `billedMinutes`, `amount`, ...). API `extension-quote` và `extend` vẫn nhận `HOURLY` 1–12 giờ cho client cũ, nhưng giao diện lễ tân mới dùng đồng hồ.

## Giá menu cần cấu hình

Giá trong menu là giá tham khảo cấu hình ở mỗi chi nhánh; frontend không gửi giá chốt. Backend đọc `gcmb.price_rule` và `gcmb.combo`, sau đó trả báo giá cho giao diện.

| Hạng phòng | Theo giờ | Combo 2h 1 | Combo 2h 2 | Combo 2h 3 | 4h | 5h | 6h | Ngày 7–12h | Đêm 23–7h |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Queen | 96.000đ | 236.000đ | 276.000đ | 316.000đ | 296.000đ | 366.000đ | 416.000đ | 196.000đ | 296.000đ |
| King | 106.000đ | 246.000đ | 286.000đ | 336.000đ | 326.000đ | 396.000đ | 446.000đ | 246.000đ | 336.000đ |

Để giá trong UI khớp menu, dữ liệu active trong `price_rule`/`combo` của chi nhánh cần có đúng hạng phòng, thời lượng và giá ở bảng trên. Combo đối ứng khi đổi phòng được nhận diện theo tên gói sau khi bỏ hậu tố hạng phòng và cùng thời lượng; nếu không tìm thấy cặp, đổi theo yêu cầu khách sẽ dùng báo giá giờ cho phần còn lại.

Fixture local để nạp các hạng Queen/King và giá trên vào chi nhánh demo UC34–UC37: chạy `backend/scripts/seed_uc34_37_demo.sql` trước, rồi chạy `backend/scripts/seed_uc39_40_menu.sql`. Script thứ hai chỉ thêm dữ liệu demo mới, không thay giá trong chi nhánh thật.

Schema V1 có `room_allocation`, `booking_charge` và `booking_event`; migration V10 thêm `booking_hourly_meter` để lưu phiên đồng hồ bền vững cùng giá snapshot, thời điểm bắt đầu/dừng và số phút chốt.

## Phạm vi liên quan: UC37 no-show

No-show là booking đã đến giờ nhưng khách không xuất hiện và lễ tân không check-in. Đây là luồng xử lý booking vắng mặt, tách biệt với khách chủ động hủy trước giờ. UC37 hiện có luồng hủy booking và xử lý cọc; việc đánh dấu no-show, giải phóng phòng và xử lý cọc no-show vẫn là phần cần triển khai riêng.
