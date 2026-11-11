/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Defines UC-42 Collect Booking Deposit manual unit test cases.
 */
package com.gcmb.backend.manual.depositmanagement;

import static com.gcmb.backend.manual.depositmanagement.DepositManagementManualRunner.PRE42;
import static com.gcmb.backend.manual.depositmanagement.DepositManagementManualRunner.test;

import java.util.List;

final class CollectBookingDepositManualCases {
    private CollectBookingDepositManualCases() {
    }

    static List<DepositManagementManualRunner.ManualTest> cases() {
        String uc = "UC-42 Collect Booking Deposit";
        return List.of(
            test("DPM-001", uc, "BookingPricingService.depositFor", PRE42,
                    "Giá booking 100.000đ.", "Tiền cọc yêu cầu bằng đúng 30.000đ.", "Normal",
                    note("BR-178", "", "BookingPricingService.depositFor", "Black-box")),
            test("DPM-002", uc, "BookingPricingService.depositFor", PRE42,
                    "Giá booking 101đ.", "30% là 30,3đ và được làm tròn lên thành 31đ.", "Boundary",
                    note("BR-178", "", "BookingPricingService.depositFor", "Black-box")),
            test("DPM-003", uc, "CheckoutService.confirmDeposit", PRE42 + " Booking CONFIRMED, chưa nhận cọc; chưa có xác nhận giao dịch từ SePay.",
                    "Lễ tân nhập nội dung chuyển khoản và xác nhận đã kiểm tra đủ 30.000đ.",
                    "Tiền cọc vẫn chưa được ghi nhận thành công vì SePay chưa xác nhận reference và amount khớp; không tạo payment, deposit entry hoặc bút toán quỹ.", "Abnormal",
                    note("BR-25, BR-108", "", "CheckoutService.confirmDeposit", "White-box")),
            test("DPM-004", uc, "CheckoutService.confirmDeposit", PRE42 + " Booking đã có một khoản cọc 30.000đ được xác nhận hợp lệ.",
                    "Lễ tân xác nhận lại cùng khoản cọc.",
                    "Trả trạng thái SETTLED của khoản cọc hiện có; không tạo thêm payment, deposit entry hoặc bút toán quỹ.", "Abnormal",
                    note("BR-108, BR-164", "MSG74", "CheckoutService.confirmDeposit", "White-box")),
            test("DPM-005", uc, "CheckoutService.confirmDeposit", PRE42 + " Booking không ở trạng thái CONFIRMED và chưa nhận cọc.",
                    "Lễ tân xác nhận thu cọc.", "Từ chối xác nhận; không tạo payment, deposit entry hoặc bút toán quỹ.", "Abnormal",
                    note("BR-25", "", "CheckoutService.confirmDeposit", "White-box")),
            test("DPM-006", uc, "CheckoutService.confirmDeposit", PRE42 + " Booking CONFIRMED, chưa nhận cọc.",
                    "Nội dung chuyển khoản là null.", "Hiển thị “Vui lòng bổ sung nội dung chuyển khoản.”; không ghi nhận cọc.", "Abnormal",
                    note("BR-111", "MSG02", "CheckoutService.confirmDeposit", "White-box")),
            test("DPM-007", uc, "CheckoutService.confirmDeposit", PRE42 + " Booking CONFIRMED, chưa nhận cọc.",
                    "Nội dung chuyển khoản chỉ gồm khoảng trắng.", "Hiển thị “Vui lòng bổ sung nội dung chuyển khoản.”; không ghi nhận cọc.", "Abnormal",
                    note("BR-111", "MSG02", "CheckoutService.confirmDeposit", "White-box")),
            test("DPM-008", uc, "CheckoutService.confirmDeposit", PRE42 + " Giá booking 101đ, tiền cọc yêu cầu 31đ; chưa có xác nhận từ SePay.",
                    "Lễ tân nhập nội dung chuyển khoản và số tiền đã kiểm tra 31đ.",
                    "Tiền cọc vẫn chưa được ghi nhận thành công vì SePay chưa xác nhận reference và amount khớp.", "Boundary",
                    note("BR-25, BR-178", "", "CheckoutService.confirmDeposit", "White-box")),
            test("DPM-009", uc, "CheckoutService.confirmDeposit", PRE42 + " Booking CONFIRMED, tiền cọc yêu cầu 30.000đ.",
                    "Số tiền lễ tân đã kiểm tra là null.",
                    "Hiển thị “Chưa thể xác nhận kết quả thu tiền cọc. Vui lòng kiểm tra lại trạng thái của booking trước khi thực hiện lại.”; không ghi nhận cọc.", "Abnormal",
                    note("BR-25, BR-26", "MSG27", "CheckoutService.confirmDeposit", "White-box")),
            test("DPM-010", uc, "CheckoutService.confirmDeposit", PRE42 + " Booking CONFIRMED, tiền cọc yêu cầu 30.000đ.",
                    "Số tiền lễ tân đã kiểm tra là 29.999đ.",
                    "Hiển thị “Chưa thể xác nhận kết quả thu tiền cọc. Vui lòng kiểm tra lại trạng thái của booking trước khi thực hiện lại.”; không ghi nhận cọc.", "Boundary",
                    note("BR-25, BR-26, BR-178", "MSG27", "CheckoutService.confirmDeposit", "White-box")),
            test("DPM-011", uc, "CheckoutService.confirmDeposit", PRE42 + " Tổng giá booking bằng 0đ.",
                    "Lễ tân xác nhận thu cọc 0đ.", "Hiển thị “Số tiền cọc phải lớn hơn 0 đ.”; không ghi nhận cọc.", "Boundary",
                    note("BR-137", "MSG46", "CheckoutService.confirmDeposit", "White-box")),
            test("DPM-012", uc, "CheckoutService.confirmDeposit", "Lễ tân letan.hoabang đăng nhập; HN_HOA_BANG đang tạm ngừng; booking thuộc cơ sở và chưa nhận cọc.",
                    "Lễ tân xác nhận thu cọc.", "Từ chối mọi thay đổi; vẫn cho phép dữ liệu lịch sử của cơ sở được đọc; không ghi nhận cọc.", "Abnormal",
                    note("BR-177", "", "CheckoutService.confirmDeposit", "White-box")),
            test("DPM-013", uc, "CheckoutService.confirmDeposit", "Kế toán ketoan đã đăng nhập; booking CONFIRMED và chưa nhận cọc.",
                    "Kế toán thực hiện thu tiền cọc.", "Hiển thị “Bạn không có quyền thu tiền cọc đối với booking.”; không đọc hoặc ghi dữ liệu cọc.", "Abnormal",
                    note("Không có BR riêng", "MSG24", "CheckoutService.confirmDeposit", "White-box")),
            test("DPM-014", uc, "CheckoutService.confirmDeposit", "Lễ tân letan.hoabang đăng nhập tại HN_HOA_BANG đang hoạt động; booking tồn tại ở cơ sở khác.",
                    "Lễ tân xác nhận thu cọc cho booking ngoài phạm vi.", "Hiển thị “Bạn không có quyền thu tiền cọc đối với booking.”; không lộ hoặc thay đổi booking ngoài phạm vi.", "Abnormal",
                    note("Không có BR riêng", "MSG24", "CheckoutService.confirmDeposit", "White-box")),
            test("DPM-015", uc, "CheckoutService.webhook", PRE42 + " Request PENDING; reference, tài khoản, amount và thời gian khớp; booking CONFIRMED, chưa nhận cọc.",
                    "SePay xác nhận một giao dịch tiền vào hợp lệ.",
                    "Ghi nhận giao dịch thành công đúng một lần; tạo BANK_TRANSFER payment và RECEIVE deposit entry; liên kết khoản cọc với booking và không ghi vào service revenue.", "Normal",
                    note("BR-25, BR-108, BR-137", "", "CheckoutService.webhook", "White-box")),
            test("DPM-016", uc, "CheckoutService.webhook", PRE42 + " Có payment request đang chờ.",
                    "Webhook có authorization không hợp lệ.", "Từ chối webhook; không tạo bank transaction, payment hoặc deposit entry.", "Abnormal",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-017", uc, "CheckoutService.webhook", PRE42 + " Có payment request đang chờ.",
                    "Webhook thiếu transaction ID.", "Từ chối webhook; payment request và booking không đổi.", "Abnormal",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-018", uc, "CheckoutService.webhook", PRE42 + " Có payment request đang chờ.",
                    "Transaction amount bằng 0đ.", "Hiển thị “Số tiền giao dịch phải lớn hơn 0 đ.”; không ghi nhận cọc.", "Boundary",
                    note("BR-137", "MSG46", "CheckoutService.webhook", "White-box")),
            test("DPM-019", uc, "CheckoutService.webhook", PRE42 + " Có payment request đang chờ.",
                    "Webhook đến từ bank hoặc account khác cấu hình.", "Từ chối webhook; không tạo payment hoặc deposit entry.", "Abnormal",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-020", uc, "CheckoutService.webhook", PRE42 + " Có payment request đang chờ.",
                    "Loại giao dịch không hợp lệ.", "Từ chối webhook; payment request và số dư cọc không đổi.", "Abnormal",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-021", uc, "CheckoutService.webhook", PRE42 + " Không có payment request khớp reference.",
                    "SePay gửi webhook hợp lệ.", "Lưu giao dịch chưa khớp để đối soát; tiền cọc vẫn chưa được xác nhận.", "Abnormal",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-022", uc, "CheckoutService.webhook", PRE42 + " Reference khớp nhưng amount không khớp.",
                    "SePay gửi webhook.", "Đánh dấu giao dịch cần xử lý ngoại lệ; không tạo payment hoặc deposit entry.", "Abnormal",
                    note("BR-25, BR-26", "", "CheckoutService.webhook", "White-box")),
            test("DPM-023", uc, "CheckoutService.webhook", PRE42 + " Giao dịch xảy ra trước lúc payment request được tạo.",
                    "SePay gửi webhook.", "Không xác nhận cọc; đánh dấu giao dịch cần xử lý ngoại lệ và không tạo payment.", "Boundary",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-024", uc, "CheckoutService.webhook", PRE42 + " Giao dịch xảy ra sau khi payment request hết hạn.",
                    "SePay gửi webhook.", "Không xác nhận cọc; không tạo payment hoặc deposit entry.", "Boundary",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-025", uc, "CheckoutService.webhook", PRE42 + " Booking không còn ở trạng thái CONFIRMED.",
                    "SePay gửi webhook khớp request.", "Không xác nhận cọc và không tạo deposit entry.", "Abnormal",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-026", uc, "CheckoutService.webhook", PRE42 + " Booking đã có khoản cọc được xác nhận.",
                    "SePay gửi lại giao dịch cho khoản cọc.", "Không tạo khoản cọc thứ hai; đánh dấu giao dịch cần xử lý ngoại lệ.", "Abnormal",
                    note("BR-164", "MSG74", "CheckoutService.webhook", "White-box")),
            test("DPM-027", uc, "CheckoutService.webhook", PRE42 + " Giá quote hiện tại làm amount phải cọc khác amount trên request.",
                    "SePay gửi webhook cho request cũ.", "Không xác nhận amount cũ; không tạo payment hoặc deposit entry.", "Abnormal",
                    note("BR-25, BR-26, BR-178", "", "CheckoutService.webhook", "White-box")),
            test("DPM-028", uc, "CheckoutService.webhook", PRE42 + " Webhook hợp lệ với cùng external transaction ID đã được xử lý.",
                    "SePay gửi lại cùng webhook.", "Không tạo thêm bank transaction, payment, deposit entry hoặc audit.", "Abnormal",
                    note("BR-164", "MSG74", "CheckoutService.webhook", "White-box"))
        );
    }

    private static String note(String rules, String messages, String production, String method) {
        return "Nguồn: UC-42 - Collect_Booking_Deposit.docx; " + rules
                + (messages.isBlank() ? "" : "; " + messages)
                + "; Flyway V14; Production: " + production
                + ". Phương pháp kiểm thử: " + method + ".";
    }
}
