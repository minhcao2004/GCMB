/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Defines UC-44 Refund Booking Deposit manual unit test cases.
 */
package com.gcmb.backend.manual.depositmanagement;

import static com.gcmb.backend.manual.depositmanagement.DepositManagementManualRunner.PRE44;
import static com.gcmb.backend.manual.depositmanagement.DepositManagementManualRunner.test;

import java.util.ArrayList;
import java.util.List;

final class RefundBookingDepositManualCases {
    private RefundBookingDepositManualCases() {
    }

    static List<DepositManagementManualRunner.ManualTest> cases() {
        String uc = "UC-44 Refund Booking Deposit";
        List<DepositManagementManualRunner.ManualTest> c = new ArrayList<>();
        c.add(t("DPM-029", uc, "DepositManagementService.refundRequests/DepositRepository.refundRequests", PRE44 + " Có request ở nhiều chi nhánh.", "Mở queue hoàn cọc.", "Chỉ trả request đúng chi nhánh; pending request xử lý được.", "Normal", "BR-17", "", "White-box"));
        c.add(t("DPM-030", uc, "DepositManagementService.createRefundRequest", PRE44 + " Booking CANCELLED đúng hạn, còn balance, chưa có request mở.", "Nhập đủ người nhận/bank/account/reason.", "Tạo một REQUESTED bằng toàn bộ balance; liên kết booking/deposit và event.", "Normal", "BR-14, BR-111, BR-164, BR-186", "", "White-box"));
        c.add(t("DPM-031", uc, "DepositManagementService.createRefundRequest", PRE44, "Không chọn booking.", "Không tạo request; trả MSG02 cho Booking.", "Abnormal", "BR-111", "MSG02", "White-box"));
        c.add(t("DPM-032", uc, "DepositManagementService.createRefundRequest", PRE44 + " Booking thuộc chi nhánh khác.", "Gửi yêu cầu hoàn.", "Từ chối MSG24; không lộ/đổi booking khác chi nhánh.", "Abnormal", "Không có BR riêng", "MSG24", "White-box"));
        c.add(t("DPM-033", uc, "DepositManagementService.createRefundRequest", PRE44 + " Booking chưa CANCELLED.", "Gửi yêu cầu hoàn.", "Từ chối; không tạo request/event.", "Abnormal", "BR-14", "", "White-box"));
        c.add(t("DPM-034", uc, "DepositManagementService.createRefundRequest", PRE44 + " Booking hủy nhưng không có deposit account.", "Gửi yêu cầu hoàn.", "Từ chối; không tạo request.", "Abnormal", "BR-14", "", "White-box"));
        c.add(t("DPM-035", uc, "DepositManagementService.createRefundRequest", PRE44 + " Deposit balance=0.", "Gửi yêu cầu hoàn.", "Từ chối; không tạo request amount=0.", "Boundary", "BR-14", "MSG46", "White-box"));
        c.add(t("DPM-036", uc, "DepositManagementService.createRefundRequest", PRE44 + " Không đủ điều kiện hoặc có request mở.", "Gửi thêm yêu cầu.", "Từ chối; không tạo request trùng.", "Abnormal", "BR-14, BR-164", "MSG74", "White-box"));
        c.add(t("DPM-037", uc, "DepositManagementService.createRefundRequest", PRE44 + " Booking đủ điều kiện.", "Tên chủ tài khoản blank.", "Trả đúng MSG02; không tạo request.", "Abnormal", "BR-111, BR-186", "MSG02", "White-box"));
        c.add(t("DPM-038", uc, "DepositManagementService.createRefundRequest", PRE44 + " Booking đủ điều kiện.", "Ngân hàng blank.", "Trả đúng MSG02; không tạo request.", "Abnormal", "BR-111, BR-186", "MSG02", "White-box"));
        c.add(t("DPM-039", uc, "DepositManagementService.createRefundRequest", PRE44 + " Booking đủ điều kiện.", "Số tài khoản blank.", "Trả đúng MSG02; không tạo request.", "Abnormal", "BR-111, BR-186", "MSG02", "White-box"));
        c.add(t("DPM-040", uc, "DepositManagementService.createRefundRequest", PRE44 + " Booking đủ điều kiện.", "Lý do blank.", "Trả đúng MSG02; không tạo request.", "Abnormal", "BR-111", "MSG02", "White-box"));
        c.add(t("DPM-041", uc, "DepositManagementService.createRefundRequest", PRE44, "Tên chủ tài khoản đúng 200 ký tự.", "Chấp nhận và lưu đủ 200 ký tự.", "Boundary", "BR-187", "", "White-box"));
        c.add(t("DPM-042", uc, "DepositManagementService.createRefundRequest", PRE44, "Tên chủ tài khoản 201 ký tự.", "Trả đúng MSG03 giới hạn 200; không tạo request.", "Boundary", "BR-187", "MSG03", "White-box"));
        c.add(t("DPM-043", uc, "DepositManagementService.createRefundRequest", PRE44, "Tên ngân hàng đúng 200 ký tự.", "Chấp nhận và lưu đủ 200 ký tự.", "Boundary", "BR-187", "", "White-box"));
        c.add(t("DPM-044", uc, "DepositManagementService.createRefundRequest", PRE44, "Tên ngân hàng 201 ký tự.", "Trả đúng MSG03 giới hạn 200; không tạo request.", "Boundary", "BR-187", "MSG03", "White-box"));
        c.add(t("DPM-045", uc, "DepositManagementService.completeRefund", PRE44 + " Request mở; booking CANCELLED; deposit/balance hợp lệ.", "Reference hợp lệ và JPEG hợp lệ.", "Request PAID; lưu evidence/reference/processor; tạo một REFUND entry; balance giảm đúng.", "Normal", "BR-17, BR-115, BR-116, BR-157, BR-164, BR-188", "", "White-box"));
        c.add(t("DPM-046", uc, "DepositManagementService.completeRefund", PRE44 + " Request đủ điều kiện.", "Có reference, không evidence.", "Chấp nhận theo BR-157; request PAID.", "Normal", "BR-157", "", "White-box"));
        c.add(t("DPM-047", uc, "DepositManagementService.completeRefund", PRE44 + " Request đủ điều kiện.", "Có evidence, reference blank.", "Chấp nhận theo BR-157; request PAID.", "Normal", "BR-157", "", "White-box"));
        c.add(t("DPM-048", uc, "DepositManagementService.completeRefund", PRE44, "Không evidence và không reference.", "Không hoàn tất; đúng MSG67; request/balance không đổi.", "Abnormal", "BR-157", "MSG67", "White-box"));
        c.add(t("DPM-049", uc, "DepositManagementService.completeRefund", PRE44, "Reference đúng 200 ký tự.", "Chấp nhận và lưu đủ reference.", "Boundary", "BR-188", "", "White-box"));
        c.add(t("DPM-050", uc, "DepositManagementService.completeRefund", PRE44, "Reference 201 ký tự.", "Trả đúng MSG03 giới hạn 200; không hoàn tất.", "Boundary", "BR-188", "MSG03", "White-box"));
        c.add(t("DPM-051", uc, "RefundEvidenceStorageService.store/read", PRE44 + " Storage tạm sạch.", "PNG hợp lệ.", "Nhận diện image/png; lưu và đọc lại được.", "Normal", "BR-115", "", "Black-box"));
        c.add(t("DPM-052", uc, "RefundEvidenceStorageService.store/read", PRE44 + " Storage tạm sạch.", "WebP hợp lệ.", "Nhận diện image/webp; lưu và đọc lại được.", "Normal", "BR-115", "", "Black-box"));
        c.add(t("DPM-053", uc, "RefundEvidenceStorageService.store/read", PRE44 + " Storage tạm sạch.", "HEIC hợp lệ.", "Chấp nhận và lưu HEIC.", "Normal", "BR-115", "", "Black-box"));
        c.add(t("DPM-054", uc, "RefundEvidenceStorageService.store/read", PRE44 + " Storage tạm sạch.", "HEIF hợp lệ.", "Chấp nhận và lưu HEIF.", "Normal", "BR-115", "", "Black-box"));
        c.add(t("DPM-055", uc, "RefundEvidenceStorageService.store/read", PRE44 + " Storage tạm sạch.", "PDF hợp lệ.", "Chấp nhận và lưu application/pdf.", "Normal", "BR-115", "", "Black-box"));
        c.add(t("DPM-056", uc, "RefundEvidenceStorageService.store", PRE44, "Tệp TXT.", "Từ chối đúng MSG78; không lưu file/request.", "Abnormal", "BR-115", "MSG78", "Black-box"));
        c.add(t("DPM-057", uc, "RefundEvidenceStorageService.store", PRE44, "Tệp 0 byte.", "Từ chối đúng MSG79; không lưu file.", "Boundary", "BR-116", "MSG79", "Black-box"));
        c.add(t("DPM-058", uc, "RefundEvidenceStorageService.store", PRE44, "Tệp hợp lệ đúng 20.000.000 byte.", "Chấp nhận lưu file.", "Boundary", "BR-116", "", "Black-box"));
        c.add(t("DPM-059", uc, "RefundEvidenceStorageService.store", PRE44, "Tệp 20.000.001 byte.", "Từ chối đúng MSG79; không tạo file.", "Boundary", "BR-116", "MSG79", "Black-box"));
        c.add(t("DPM-060", uc, "RefundEvidenceStorageService.store", PRE44, "Tên/MIME PNG nhưng content JPEG.", "Từ chối đúng MSG80.", "Abnormal", "BR-115", "MSG80", "Black-box"));
        c.add(t("DPM-061", uc, "RefundEvidenceStorageService.store", PRE44, "Đuôi hỗ trợ nhưng content hỏng.", "Từ chối đúng MSG80; không lưu file.", "Abnormal", "BR-115", "MSG80", "Black-box"));
        c.add(t("DPM-062", uc, "DepositManagementService.completeRefund", PRE44, "Request ID null.", "Từ chối; không lưu evidence/refund entry/update request.", "Abnormal", "BR-111", "MSG02", "White-box"));
        c.add(t("DPM-063", uc, "DepositManagementService.completeRefund", PRE44 + " Request không tồn tại/khác chi nhánh.", "Xác nhận hoàn.", "Từ chối MSG24; không lộ/đổi request ngoài scope.", "Abnormal", "BR-17", "MSG24", "White-box"));
        c.add(t("DPM-064", uc, "DepositManagementService.completeRefund", PRE44 + " Request PAID.", "Xác nhận lại.", "Đúng MSG74; không tạo evidence/refund entry thứ hai.", "Abnormal", "BR-164", "MSG74", "White-box"));
        c.add(t("DPM-065", uc, "DepositManagementService.completeRefund", PRE44 + " Booking không còn CANCELLED.", "Xác nhận hoàn.", "Không hoàn tất; request/balance/fund không đổi.", "Abnormal", "BR-14, BR-17", "MSG27", "White-box"));
        c.add(t("DPM-066", uc, "DepositManagementService.completeRefund", PRE44 + " Current deposit ID khác request.", "Xác nhận hoàn.", "Không hoàn tất; không refund entry/evidence cuối.", "Abnormal", "BR-164", "MSG27", "White-box"));
        c.add(t("DPM-067", uc, "DepositManagementService.completeRefund", PRE44 + " Request amount lớn hơn balance.", "Xác nhận hoàn.", "Không hoàn tất; balance không âm.", "Boundary", "BR-17", "", "White-box"));
        c.add(t("DPM-068", uc, "DepositManagementService.completeRefund", PRE44 + " Request bị phiên khác xử lý trước lần đọc có khóa.", "Xác nhận đồng thời.", "Đúng MSG74; không refund entry trùng; evidence tạm được dọn.", "Abnormal", "BR-164", "MSG74", "White-box"));
        c.add(t("DPM-069", uc, "DepositManagementService.refundEvidence", PRE44 + " Request PAID có evidence đúng scope.", "Tải evidence.", "Trả đúng filename/MIME/bytes; không mutation.", "Normal", "BR-115, BR-157", "", "White-box"));
        c.add(t("DPM-070", uc, "DepositManagementService.refundEvidence", PRE44 + " Request chưa PAID/chưa evidence.", "Tải evidence.", "Không trả evidence chưa hoàn tất; không đổi request.", "Abnormal", "BR-17, BR-157", "", "White-box"));
        c.add(t("DPM-071", uc, "DepositManagementService.refundEvidence", PRE44 + " Evidence thuộc chi nhánh khác.", "Tải evidence.", "Từ chối MSG24; không trả metadata/bytes ngoài scope.", "Abnormal", "Không có BR riêng", "MSG24", "White-box"));
        c.add(t("DPM-072", uc, "DepositRepository.completeRefundRequest", PRE44 + " Refund hoàn thành.", "Hoàn 100.000đ.", "Ghi financial REFUND để Accountant Available Fund giảm 100.000đ; balance/request cùng phản ánh.", "Normal", "BR-83, BR-164", "", "White-box"));
        return List.copyOf(c);
    }

    private static DepositManagementManualRunner.ManualTest t(String id, String uc, String production,
            String pre, String input, String expected, String type, String rules, String messages, String method) {
        String note = "Nguồn: UC-44_Refund_Booking_Deposit.docx; " + rules
                + (messages.isBlank() ? "" : "; " + messages)
                + "; Flyway V14; Production: " + production
                + ". Phương pháp kiểm thử: " + method + ".";
        return test(id, uc, production, pre, input, expected, type, note);
    }
}
