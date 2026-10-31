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
                    "Giá booking 101đ.", "30% là 30,3đ, làm tròn lên thành 31đ.", "Boundary",
                    note("BR-178", "", "BookingPricingService.depositFor", "Black-box")),
            test("DPM-003", uc, "CheckoutService.depositPayment", PRE42 + " Booking CONFIRMED, chưa nhận cọc; SePay sẵn sàng; tổng charge dương.",
                    "Yêu cầu tạo thông tin chuyển khoản cọc.", "Tạo một request PENDING đúng 30%, reference duy nhất, hết hạn sau 30 phút, QR và audit một lần.", "Normal",
                    note("BR-26, BR-111, BR-137, BR-178", "", "CheckoutService.depositPayment", "White-box")),
            test("DPM-004", uc, "CheckoutService.depositPayment", PRE42 + " Booking đã có cọc nhận lớn hơn 0.",
                    "Kiểm tra trạng thái thu cọc.", "Trả SETTLED và amount đã nhận; không tạo request mới.", "Normal",
                    note("BR-108, BR-164", "", "CheckoutService.depositPayment", "White-box")),
            test("DPM-005", uc, "CheckoutService.depositPayment", PRE42 + " Booking không CONFIRMED, chưa nhận cọc.",
                    "Yêu cầu tạo QR cọc.", "Từ chối; không tạo request/payment/deposit entry/audit xác nhận.", "Abnormal",
                    note("BR-25", "", "CheckoutService.depositPayment", "White-box")),
            test("DPM-006", uc, "CheckoutService.depositPayment", PRE42 + " Có request PENDING chưa hết hạn và đúng amount.",
                    "Yêu cầu tạo QR lần nữa.", "Tái sử dụng reference; không hủy hoặc tạo request thứ hai.", "Normal",
                    note("BR-164", "", "CheckoutService.depositPayment", "White-box")),
            test("DPM-007", uc, "CheckoutService.depositPayment", PRE42 + " Request cũ còn hạn nhưng amount khác quote hiện tại.",
                    "Yêu cầu tạo lại QR.", "Hủy request cũ; tạo đúng một request mới theo amount hiện tại.", "Normal",
                    note("BR-26, BR-164, BR-178", "", "CheckoutService.depositPayment", "White-box")),
            test("DPM-008", uc, "CheckoutService.depositPayment", PRE42 + " Request cũ hết hạn.",
                    "Yêu cầu tạo lại QR.", "Hủy request cũ; tạo một request mới, không có hai request cùng hiệu lực.", "Normal",
                    note("BR-164", "", "CheckoutService.depositPayment", "White-box")),
            test("DPM-009", uc, "CheckoutService.depositPayment", PRE42 + " Chưa nhận cọc và chưa có request.",
                    "Chỉ kiểm tra trạng thái, không tạo QR.", "Trả NONE; không ghi dữ liệu.", "Normal",
                    note("Không có BR riêng", "", "CheckoutService.depositPayment", "White-box")),
            test("DPM-010", uc, "CheckoutService.depositPayment", PRE42 + " Request vừa chạm hoặc vượt expiry.",
                    "Kiểm tra trạng thái cọc.", "Trả EXPIRED, không trả QR còn hiệu lực và không ghi nhận cọc.", "Boundary",
                    note("BR-25", "", "CheckoutService.depositPayment", "White-box")),
            test("DPM-011", uc, "CheckoutService.depositPayment", PRE42 + " SePay chưa sẵn sàng.",
                    "Yêu cầu tạo QR.", "Không tạo request; trả đúng MSG25 cho SePay.", "Abnormal",
                    note("Không có BR riêng", "MSG25", "CheckoutService.depositPayment", "White-box")),
            test("DPM-012", uc, "CheckoutService.depositPayment", PRE42 + " Amount phải cọc bằng 0.",
                    "Yêu cầu tạo QR.", "Từ chối bằng MSG46; không tạo request.", "Boundary",
                    note("BR-137", "MSG46", "CheckoutService.depositPayment", "White-box")),
            test("DPM-013", uc, "CheckoutService.depositPayment", "Người dùng đăng nhập nhưng không phải RECEPTIONIST; production service thật.",
                    "Thực hiện thu tiền cọc.", "Từ chối bằng MSG24; không đọc/ghi booking hoặc payment.", "Abnormal",
                    note("Không có BR riêng", "MSG24", "CheckoutService.depositPayment", "White-box")),
            test("DPM-014", uc, "CheckoutService.depositPayment", "Lễ tân đăng nhập nhưng không có phân công tại chi nhánh hoạt động.",
                    "Thực hiện thu tiền cọc.", "Từ chối bằng MSG24; không tạo QR/request.", "Abnormal",
                    note("Không có BR riêng", "MSG24", "CheckoutService.depositPayment", "White-box")),
            test("DPM-015", uc, "CheckoutService.webhook", PRE42 + " Request PENDING; reference/account/amount/time khớp; booking CONFIRMED, chưa nhận cọc.",
                    "SePay xác nhận giao dịch tiền vào hợp lệ.", "Tạo bank transaction, BANK_TRANSFER payment, RECEIVE entry và audit; không ghi service revenue.", "Normal",
                    note("BR-25, BR-108, BR-137", "", "CheckoutService.webhook", "White-box")),
            test("DPM-016", uc, "CheckoutService.webhook", PRE42 + " Payment request đang chờ.",
                    "Webhook authorization không hợp lệ.", "Từ chối; không tạo bank transaction/payment/deposit entry.", "Abnormal",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-017", uc, "CheckoutService.webhook", PRE42 + " Payment request đang chờ.",
                    "Webhook thiếu transaction ID.", "Từ chối; request và booking không đổi.", "Abnormal",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-018", uc, "CheckoutService.webhook", PRE42 + " Payment request đang chờ.",
                    "Transaction amount=0đ.", "Từ chối bằng MSG46; không ghi nhận cọc.", "Boundary",
                    note("BR-137", "MSG46", "CheckoutService.webhook", "White-box")),
            test("DPM-019", uc, "CheckoutService.webhook", PRE42 + " Payment request đang chờ.",
                    "Webhook từ bank/account khác cấu hình.", "Từ chối; không ghi payment/deposit entry.", "Abnormal",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-020", uc, "CheckoutService.webhook", PRE42 + " Payment request đang chờ.",
                    "Loại giao dịch không hợp lệ.", "Từ chối; request và balance không đổi.", "Abnormal",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-021", uc, "CheckoutService.webhook", PRE42 + " Không có request khớp reference.",
                    "Xử lý webhook hợp lệ.", "Lưu transaction chưa khớp; booking chưa được xác nhận nhận cọc.", "Abnormal",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-022", uc, "CheckoutService.webhook", PRE42 + " Reference khớp nhưng amount sai.",
                    "Xử lý webhook.", "Transaction EXCEPTION; không tạo payment/deposit entry; phù hợp MSG27.", "Abnormal",
                    note("BR-25, BR-26", "MSG27", "CheckoutService.webhook", "White-box")),
            test("DPM-023", uc, "CheckoutService.webhook", PRE42 + " Transaction trước lúc request được tạo.",
                    "Xử lý webhook.", "Không xác nhận cọc; transaction EXCEPTION, không payment.", "Boundary",
                    note("BR-25", "MSG27", "CheckoutService.webhook", "White-box")),
            test("DPM-024", uc, "CheckoutService.webhook", PRE42 + " Transaction sau expiry.",
                    "Xử lý webhook.", "Không xác nhận cọc; không tạo payment/deposit entry.", "Boundary",
                    note("BR-25", "MSG27", "CheckoutService.webhook", "White-box")),
            test("DPM-025", uc, "CheckoutService.webhook", PRE42 + " Booking không còn CONFIRMED.",
                    "Xử lý webhook.", "Không xác nhận cọc và không tạo deposit entry.", "Abnormal",
                    note("BR-25", "", "CheckoutService.webhook", "White-box")),
            test("DPM-026", uc, "CheckoutService.webhook", PRE42 + " Booking đã có tiền cọc.",
                    "Xử lý webhook.", "Không tạo cọc thứ hai; transaction EXCEPTION.", "Abnormal",
                    note("BR-164", "", "CheckoutService.webhook", "White-box")),
            test("DPM-027", uc, "CheckoutService.webhook", PRE42 + " Quote đổi làm amount hiện tại khác request.",
                    "Xử lý webhook request cũ.", "Không xác nhận amount cũ; không tạo payment/deposit entry.", "Abnormal",
                    note("BR-26, BR-178", "", "CheckoutService.webhook", "White-box")),
            test("DPM-028", uc, "CheckoutService.webhook", PRE42 + " Webhook hợp lệ đã xử lý.",
                    "Gửi lại cùng external transaction ID.", "Không tạo thêm transaction/payment/deposit entry/audit.", "Abnormal",
                    note("BR-164", "", "CheckoutService.webhook", "White-box"))
        );
    }

    private static String note(String rules, String messages, String production, String method) {
        return "Nguồn: UC-42_Collect_Booking_Deposit.docx; " + rules
                + (messages.isBlank() ? "" : "; " + messages)
                + "; Flyway V14; Production: " + production
                + ". Phương pháp kiểm thử: " + method + ".";
    }
}
