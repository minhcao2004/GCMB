/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Defines approved manual unit cases for guest check-in (UC-38).
 */
package com.gcmb.backend.manual.roomstaymanagement;

import static com.gcmb.backend.manual.roomstaymanagement.RoomStayManagementManualRunner.executeCase;
import static com.gcmb.backend.manual.roomstaymanagement.RoomStayManagementManualRunner.test;

import java.util.List;

final class CheckInGuestManualCases {
    private CheckInGuestManualCases() {
    }

    static List<RoomStayManagementManualRunner.ManualTest> cases() {
        return List.of(
            test("RSM-001", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập, được phân công cơ sở HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở, phòng Q01; dữ liệu từ Flyway V3/V14; BookingService thật với repository/pricing mock. Booking CONFIRMED; allocation RESERVED; Q01 AVAILABLE; thời điểm hiện tại nằm trong khung booking.",
                    "Xác nhận check-in.",
                    "Ghi actual check-in; booking thành IN_HOUSE; allocation thành OCCUPIED; ghi đúng một event check-in.", "Normal",
                    "Nguồn: UC-38_Check_In_Guest.docx; BR-01, BR-155, BR-164; V14; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-001")),
            test("RSM-002", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập, được phân công cơ sở HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở, phòng Q01; dữ liệu từ Flyway V3/V14; BookingService thật với repository/pricing mock. Booking 10:00–12:00; khách đến lúc 09:30; Q01 không có lịch trùng.",
                    "Xác nhận check-in sớm 30 phút.",
                    "Giữ plannedStart 10:00; actualCheckin 09:30; plannedEnd/allocationEnd thành 11:30 để giữ đủ 120 phút.", "Normal",
                    "Nguồn: UC-38_Check_In_Guest.docx; BR-155; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-002")),
            test("RSM-003", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập, được phân công cơ sở HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở, phòng Q01; dữ liệu từ Flyway V3/V14; BookingService thật với repository/pricing mock. Booking CONFIRMED có channel WALK_IN, allocation RESERVED và Q01 AVAILABLE.",
                    "Xác nhận khách walk-in nhận phòng.",
                    "Check-in thành công; postcondition giống booking đặt trước.", "Normal",
                    "Nguồn: UC-38_Check_In_Guest.docx; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-003")),
            test("RSM-004", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập, được phân công cơ sở HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở, phòng Q01; dữ liệu từ Flyway V3/V14; BookingService thật với repository/pricing mock. Booking đang giữ cọc 100000đ, CONFIRMED, allocation RESERVED.",
                    "Xác nhận check-in.",
                    "Check-in thành công; số dư cọc giữ nguyên; không tạo payment/deposit entry mới.", "Normal",
                    "Nguồn: UC-38_Check_In_Guest.docx; BR-164; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-004")),
            test("RSM-005", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập, được phân công cơ sở HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở, phòng Q01; dữ liệu từ Flyway V3/V14; BookingService thật với repository/pricing mock. Booking CANCELLED.",
                    "Xác nhận check-in.",
                    "Từ chối với \"Booking đã bị hủy nên không thể nhận phòng. Vui lòng kiểm tra lại booking của khách.\"; không đổi booking/phòng.", "Abnormal",
                    "Nguồn: UC-38_Check_In_Guest.docx; BR-155; MSG58; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-005")),
            test("RSM-006", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập, được phân công cơ sở HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở, phòng Q01; dữ liệu từ Flyway V3/V14; BookingService thật với repository/pricing mock. Booking CONFIRMED; allocation Q01 RESERVED nhưng Q01 UNDER_MAINTENANCE.",
                    "Xác nhận check-in.",
                    "Từ chối với \"Phòng Q01 chưa sẵn sàng để nhận khách. Vui lòng kiểm tra phòng hoặc sắp xếp phòng khác.\"; không ghi check-in.", "Abnormal",
                    "Nguồn: UC-38_Check_In_Guest.docx; BR-05, BR-155; MSG59; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-006")),
            test("RSM-007", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập, được phân công cơ sở HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở, phòng Q01; dữ liệu từ Flyway V3/V14; BookingService thật với repository/pricing mock. Booking CONFIRMED; allocation Q01 RESERVED nhưng Q01 INACTIVE.",
                    "Xác nhận check-in.",
                    "Từ chối với \"Phòng Q01 chưa sẵn sàng để nhận khách. Vui lòng kiểm tra phòng hoặc sắp xếp phòng khác.\"; booking và allocation giữ nguyên.", "Abnormal",
                    "Nguồn: UC-38_Check_In_Guest.docx; BR-155; MSG59; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-007")),
            test("RSM-008", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập, được phân công cơ sở HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở, phòng Q01; dữ liệu từ Flyway V3/V14; BookingService thật với repository/pricing mock. Booking CONFIRMED nhưng allocation không tồn tại hoặc không còn RESERVED.",
                    "Xác nhận check-in.",
                    "Từ chối với \"Chưa thể xác nhận kết quả nhận phòng. Vui lòng kiểm tra lại trạng thái của booking trước khi thực hiện lại.\"; không tạo allocation thay thế và không ghi actual check-in.", "Abnormal",
                    "Nguồn: UC-38_Check_In_Guest.docx; BR-155, BR-164; MSG27; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-008")),
            test("RSM-009", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập, được phân công cơ sở HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở, phòng Q01; dữ liệu từ Flyway V3/V14; BookingService thật với repository/pricing mock. Booking CONFIRMED; plannedEnd bằng hoặc trước thời điểm hiện tại.",
                    "Xác nhận check-in.",
                    "Từ chối vì đã hết khung giờ; booking vẫn CONFIRMED và phòng không bị chiếm.", "Boundary",
                    "Nguồn: UC-38_Check_In_Guest.docx; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-009")),
            test("RSM-010", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập, được phân công cơ sở HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở, phòng Q01; dữ liệu từ Flyway V3/V14; BookingService thật với repository/pricing mock. Check-in sớm làm khoảng sử dụng mới của Q01 trùng booking khác.",
                    "Xác nhận check-in.",
                    "Trả \"Phòng Q01 đã có booking trùng với thời gian sử dụng đã chọn. Vui lòng chọn phòng hoặc thời gian khác.\"; không cập nhật.", "Abnormal",
                    "Nguồn: UC-38_Check_In_Guest.docx; BR-155; MSG15; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-010")),
            test("RSM-011", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập, được phân công cơ sở HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở, phòng Q01; dữ liệu từ Flyway V3/V14; BookingService thật với repository/pricing mock. Booking đã check-in thành công và đang IN_HOUSE.",
                    "Gửi lại cùng xác nhận check-in.",
                    "Không tạo actual check-in, event hoặc allocation thứ hai; trả kết quả hiện có.", "Abnormal",
                    "Nguồn: UC-38_Check_In_Guest.docx; BR-164; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-011")),
            test("RSM-012", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập, được phân công cơ sở HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở, phòng Q01; dữ liệu từ Flyway V3/V14; BookingService thật với repository/pricing mock. Booking bị phiên khác cập nhật ngay trước lúc lưu; repository.checkInBooking trả 0.",
                    "Xác nhận check-in.",
                    "Không ghi kết quả một phần; trả \"Chưa thể xác nhận kết quả nhận phòng. Vui lòng kiểm tra lại trạng thái của booking trước khi thực hiện lại.\"", "Abnormal",
                    "Nguồn: UC-38_Check_In_Guest.docx; BR-164; MSG27; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-012")),
            test("RSM-013", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập, được phân công cơ sở HN_HOA_BANG đang hoạt động; booking thuộc cùng cơ sở, phòng Q01; dữ liệu từ Flyway V3/V14; BookingService thật với repository/pricing mock. Allocation bị phiên khác cập nhật ngay trước lúc chuyển OCCUPIED; update allocation trả 0.",
                    "Xác nhận check-in.",
                    "Rollback toàn bộ; trả \"Chưa thể xác nhận kết quả nhận phòng. Vui lòng kiểm tra lại trạng thái của booking trước khi thực hiện lại.\"; không để booking IN_HOUSE khi allocation chưa OCCUPIED.", "Abnormal",
                    "Nguồn: UC-38_Check_In_Guest.docx; BR-164; MSG27; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-013")),
            test("RSM-014", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập nhưng booking thuộc cơ sở khác; production service thật với repository mock.",
                    "Xác nhận check-in booking ngoài phạm vi.",
                    "Trả \"Bạn không có quyền truy cập đối với booking.\"; không tiết lộ hoặc cập nhật booking.", "Abnormal",
                    "Nguồn: UC-38_Check_In_Guest.docx; BR-01; MSG24; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-014")),
            test("RSM-094", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG đang ACTIVE; booking CONFIRMED có combo tặng 1 đồ uống và 1 đồ ăn; allocation Q01 RESERVED, phòng AVAILABLE; BookingService và FoodOrderService thật được gọi qua mock boundary.",
                    "Chọn đúng 1 đồ uống và 1 đồ ăn rồi xác nhận check-in.",
                    "Tạo đúng một combo-gift order với các món đã chọn, sau đó ghi check-in đúng một lần.", "Normal",
                    "Nguồn: UC-38_Check_In_Guest.docx; V36/V37; Production: BookingService.checkIn, FoodOrderService.createComboGiftOrder. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-094")),
            test("RSM-095", "UC-38 Check In Guest", "Production service",
                    "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG đang ACTIVE; booking CONFIRMED yêu cầu 2 đồ uống tặng kèm; allocation Q01 RESERVED, phòng AVAILABLE.",
                    "Chỉ chọn 1 đồ uống rồi xác nhận check-in.",
                    "Từ chối với \"Vui lòng chọn đúng 2 đồ uống tặng kèm combo.\"; không tạo gift order và không ghi check-in.", "Abnormal",
                    "Nguồn: UC-38_Check_In_Guest.docx; V36/V37; Production: BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-095")),
            test("RSM-096", "UC-38 Check In Guest", "Production service",
                    "Lễ tân còn assignment nhưng cơ sở HN_HOA_BANG đã SUSPENDED; booking CONFIRMED và allocation RESERVED tồn tại.",
                    "Xác nhận check-in tại cơ sở đã ngừng hoạt động.",
                    "Từ chối với \"Bạn không có quyền truy cập đối với booking.\"; không ghi check-in, gift order hoặc event.", "Abnormal",
                    "Nguồn: UC-38_Check_In_Guest.docx; BR-177; Production: BookingRepository.branchScope, BookingService.checkIn. Phương pháp kiểm thử: White-box.", () -> executeCase("RSM-096"))
        );
    }
}

