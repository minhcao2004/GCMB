/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Defines approved manual unit cases for room changes (UC-39).
 */
package com.gcmb.backend.manual.roomstaymanagement;

import static com.gcmb.backend.manual.roomstaymanagement.RoomStayManagementManualRunner.executeCase;
import static com.gcmb.backend.manual.roomstaymanagement.RoomStayManagementManualRunner.test;

import java.util.List;

final class ChangeRoomManualCases {
    private ChangeRoomManualCases() {
    }

    static List<RoomStayManagementManualRunner.ManualTest> cases() {
        return List.of(
            test("RSM-015", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock. Q02 và K01 còn trống toàn bộ thời gian còn lại.",
                    "Xem lựa chọn đổi phòng.",
                    "Chỉ trả phòng khác Q01, đủ sức chứa và còn trống toàn bộ thời gian còn lại.", "Normal",
                    "Nguồn: UC-39_Change_Room.docx; BR-05, BR-08, BR-128, BR-166; Production: BookingService.stayOptions. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-015")),
            test("RSM-016", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock. K01 có đơn giá cao hơn Q01.",
                    "Xem báo giá đổi sang K01, cause CUSTOMER.",
                    "Chỉ điều chỉnh phần thời gian chưa sử dụng; adjustment dương; không ghi dữ liệu.", "Normal",
                    "Nguồn: UC-39_Change_Room.docx; BR-10, BR-215, BR-217, BR-232; Production: BookingService.roomChangeQuote. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-016")),
            test("RSM-017", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock. Phòng thay thế có giá thấp hơn Q01.",
                    "Xác nhận đổi phòng, cause CUSTOMER.",
                    "Kết thúc chặng Q01, tạo chặng mới, giảm phí phần chưa dùng và lưu lịch sử hai phòng.", "Normal",
                    "Nguồn: UC-39_Change_Room.docx; BR-10, BR-215, BR-217; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-017")),
            test("RSM-018", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock. K01 đắt hơn Q01.",
                    "Đổi sang K01, cause BRANCH_FAULT.",
                    "Không thu thêm chênh lệch; lưu hai allocation và lý do.", "Normal",
                    "Nguồn: UC-39_Change_Room.docx; BR-09, BR-43; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-018")),
            test("RSM-019", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock.",
                    "Chọn lại Q01 làm phòng thay thế.",
                    "Trả \"Vui lòng chọn phòng khác với phòng Q01 đang sử dụng.\"; không đổi phòng.", "Abnormal",
                    "Nguồn: UC-39_Change_Room.docx; BR-166; MSG61; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-019")),
            test("RSM-020", "UC-39 Change Room", "Production service",
                    "Lễ tân hợp lệ; booking CONFIRMED, chưa check-in; production service thật với mock.",
                    "Yêu cầu đổi phòng.",
                    "Trả \"Chỉ có thể đổi phòng hoặc gia hạn khi khách đã nhận phòng và chưa trả phòng.\"", "Abnormal",
                    "Nguồn: UC-39_Change_Room.docx; BR-156; MSG60; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-020")),
            test("RSM-021", "UC-39 Change Room", "Production service",
                    "Lễ tân hợp lệ; booking COMPLETED; production service thật với mock.",
                    "Yêu cầu đổi phòng.",
                    "Trả MSG60; không tạo allocation hoặc charge mới.", "Abnormal",
                    "Nguồn: UC-39_Change_Room.docx; BR-156; MSG60; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-021")),
            test("RSM-022", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock. Booking có 5 khách; Q02 sức chứa 4.",
                    "Chọn Q02.",
                    "Trả \"Phòng Q02 chỉ phù hợp tối đa 4 khách. Vui lòng giảm số khách hoặc chọn phòng có sức chứa lớn hơn.\"", "Abnormal",
                    "Nguồn: UC-39_Change_Room.docx; BR-128; MSG54; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-022")),
            test("RSM-023", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock. Không có phòng thay thế phù hợp.",
                    "Chọn phòng không tồn tại hoặc không hoạt động.",
                    "Từ chối; chặng Q01 và các khoản phí giữ nguyên.", "Abnormal",
                    "Nguồn: UC-39_Change_Room.docx; BR-05, BR-08; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-023")),
            test("RSM-024", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock. K01 có booking trùng một phần thời gian còn lại.",
                    "Chọn K01.",
                    "Trả MSG15 đã điền K01; không kết thúc allocation Q01.", "Abnormal",
                    "Nguồn: UC-39_Change_Room.docx; BR-08; MSG15; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-024")),
            test("RSM-025", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock.",
                    "Cause rỗng.",
                    "Trả \"Vui lòng bổ sung lý do đổi phòng.\"; không cập nhật dữ liệu.", "Abnormal",
                    "Nguồn: UC-39_Change_Room.docx; MSG02; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-025")),
            test("RSM-026", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock.",
                    "Cause OTHER.",
                    "Từ chối nguyên nhân không thuộc CUSTOMER/BRANCH_FAULT; không đổi phòng.", "Abnormal",
                    "Nguồn: UC-39_Change_Room.docx; BR-09, BR-10; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-026")),
            test("RSM-027", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock.",
                    "Reason đúng 1000 ký tự.",
                    "Chấp nhận và lưu nguyên reason 1000 ký tự trong allocation/event.", "Boundary",
                    "Nguồn: UC-39_Change_Room.docx; BR-183; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-027")),
            test("RSM-028", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock.",
                    "Reason 1001 ký tự.",
                    "Trả \"Lý do đổi phòng chỉ được chứa tối đa 1000 ký tự.\"; không cập nhật.", "Boundary",
                    "Nguồn: UC-39_Change_Room.docx; BR-183; MSG03; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-028")),
            test("RSM-029", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock.",
                    "Reason để trống.",
                    "Cho phép vì reason chi tiết là tùy chọn; lưu null, không lưu chuỗi khoảng trắng.", "Normal",
                    "Nguồn: UC-39_Change_Room.docx; BR-183; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-029")),
            test("RSM-030", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock. Không có allocation OCCUPIED.",
                    "Yêu cầu đổi phòng.",
                    "Từ chối; không tạo chặng phòng hoặc phí mới.", "Abnormal",
                    "Nguồn: UC-39_Change_Room.docx; BR-156; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-030")),
            test("RSM-031", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock. Allocation cũ bị phiên khác kết thúc trước lúc lưu; finish trả 0.",
                    "Xác nhận đổi phòng.",
                    "Trả MSG27; giao dịch không tạo chặng mới.", "Abnormal",
                    "Nguồn: UC-39_Change_Room.docx; BR-164; MSG27; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-031")),
            test("RSM-032", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock. Chặng mới không thể chuyển OCCUPIED; occupy trả 0.",
                    "Xác nhận đổi phòng.",
                    "Rollback toàn bộ; không để chặng cũ kết thúc mà chặng mới chưa hoạt động.", "Abnormal",
                    "Nguồn: UC-39_Change_Room.docx; BR-164; MSG27; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-032")),
            test("RSM-033", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock. Charge cũ bị phiên khác thay đổi; supersede trả 0.",
                    "Xác nhận đổi phòng.",
                    "Rollback allocation và charge; trả MSG27.", "Abnormal",
                    "Nguồn: UC-39_Change_Room.docx; BR-164; MSG27; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-033")),
            test("RSM-034", "UC-39 Change Room", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG; booking IN_HOUSE, allocation Q01 OCCUPIED, chưa chạy meter, plannedEnd còn ở tương lai; charge snapshot đã xác nhận; dữ liệu V3/V14; production service thật với mock. Đổi phòng đã hoàn tất.",
                    "Gửi lại cùng xác nhận.",
                    "Không tạo allocation, charge adjustment hoặc event trùng.", "Abnormal",
                    "Nguồn: UC-39_Change_Room.docx; BR-164; Production: BookingService.changeRoom. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-034"))
        );
    }
}

