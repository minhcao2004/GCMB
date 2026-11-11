/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Defines UC-44 Refund Booking Deposit manual unit test cases.
 */
package com.gcmb.backend.manual.depositmanagement;

import static com.gcmb.backend.manual.depositmanagement.DepositManagementManualRunner.PRE44;
import static com.gcmb.backend.manual.depositmanagement.DepositManagementManualRunner.PRE44_RECEPTION;
import static com.gcmb.backend.manual.depositmanagement.DepositManagementManualRunner.PRE44_REQUEST;
import static com.gcmb.backend.manual.depositmanagement.DepositManagementManualRunner.test;

import java.util.ArrayList;
import java.util.List;

final class RefundBookingDepositManualCases {
    private RefundBookingDepositManualCases() {
    }

    static List<DepositManagementManualRunner.ManualTest> cases() {
        String uc = "UC-44 Refund Booking Deposit";
        List<DepositManagementManualRunner.ManualTest> c = new ArrayList<>();
        c.add(t("DPM-029", uc, "DepositManagementService.refundRequests/DepositRepository.refundRequestsForAccounting", PRE44 + " Có request ở nhiều cơ sở.", "Mở danh sách yêu cầu hoàn cọc.", "Hiển thị yêu cầu hoàn cọc của tất cả cơ sở; ưu tiên request đang chờ xử lý.", "Normal", "BR-17", "", "White-box"));
        c.add(t("DPM-030", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Booking CANCELLED đúng hạn, còn balance và chưa có request mở.", "Nhập đủ người nhận, ngân hàng, số tài khoản và lý do.", "Tạo một request REQUESTED bằng toàn bộ balance; liên kết booking, deposit và event.", "Normal", "BR-14, BR-111, BR-164, BR-186", "", "White-box"));
        c.add(t("DPM-031", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST, "Không chọn booking.", "Hiển thị “Vui lòng bổ sung booking cần yêu cầu hoàn cọc.”; không tạo request.", "Abnormal", "BR-111", "MSG02", "White-box"));
        c.add(t("DPM-032", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Booking tồn tại ở cơ sở khác.", "Gửi yêu cầu hoàn cọc.", "Hiển thị “Bạn không có quyền gửi yêu cầu hoàn cọc đối với booking.”; không lộ hoặc thay đổi booking ngoài phạm vi.", "Abnormal", "Không có BR riêng", "MSG24", "White-box"));
        c.add(t("DPM-033", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Booking chưa CANCELLED.", "Gửi yêu cầu hoàn cọc.", "Từ chối; không tạo request hoặc event.", "Abnormal", "BR-14", "", "White-box"));
        c.add(t("DPM-034", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Booking đã hủy nhưng không có deposit account.", "Gửi yêu cầu hoàn cọc.", "Từ chối; không tạo request.", "Abnormal", "BR-14", "", "White-box"));
        c.add(t("DPM-035", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Deposit balance bằng 0đ.", "Gửi yêu cầu hoàn cọc.", "Từ chối; không tạo request có amount bằng 0đ.", "Boundary", "BR-14", "", "White-box"));
        c.add(t("DPM-036", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Booking không đủ điều kiện hoặc đã có request mở.", "Gửi thêm yêu cầu hoàn cọc.", "Hiển thị “Thao tác này đã được ghi nhận. Không tạo thêm bản ghi; vui lòng kiểm tra kết quả hiện có.”; không tạo request trùng.", "Abnormal", "BR-14, BR-164", "MSG74", "White-box"));
        c.add(t("DPM-037", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Booking đủ điều kiện.", "Tên chủ tài khoản chỉ gồm khoảng trắng.", "Hiển thị “Vui lòng bổ sung tên chủ tài khoản.”; không tạo request.", "Abnormal", "BR-111, BR-186", "MSG02", "White-box"));
        c.add(t("DPM-038", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Booking đủ điều kiện.", "Ngân hàng chỉ gồm khoảng trắng.", "Hiển thị “Vui lòng bổ sung ngân hàng.”; không tạo request.", "Abnormal", "BR-111, BR-186", "MSG02", "White-box"));
        c.add(t("DPM-039", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Booking đủ điều kiện.", "Số tài khoản chỉ gồm khoảng trắng.", "Hiển thị “Vui lòng bổ sung số tài khoản.”; không tạo request.", "Abnormal", "BR-111, BR-186", "MSG02", "White-box"));
        c.add(t("DPM-040", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Booking đủ điều kiện.", "Lý do chỉ gồm khoảng trắng.", "Hiển thị “Vui lòng bổ sung lý do hoàn cọc.”; không tạo request.", "Abnormal", "BR-111", "MSG02", "White-box"));
        c.add(t("DPM-041", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Booking đủ điều kiện.", "Tên chủ tài khoản đúng 200 ký tự.", "Chấp nhận và lưu đủ 200 ký tự.", "Boundary", "BR-187", "", "White-box"));
        c.add(t("DPM-042", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Booking đủ điều kiện.", "Tên chủ tài khoản 201 ký tự.", "Hiển thị “Tên chủ tài khoản chỉ được chứa tối đa 200 ký tự.”; không tạo request.", "Boundary", "BR-187", "MSG03", "White-box"));
        c.add(t("DPM-043", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Booking đủ điều kiện.", "Tên ngân hàng đúng 200 ký tự.", "Chấp nhận và lưu đủ 200 ký tự.", "Boundary", "BR-187", "", "White-box"));
        c.add(t("DPM-044", uc, "DepositManagementService.createRefundRequest", PRE44_REQUEST + " Booking đủ điều kiện.", "Tên ngân hàng 201 ký tự.", "Hiển thị “Ngân hàng chỉ được chứa tối đa 200 ký tự.”; không tạo request.", "Boundary", "BR-187", "MSG03", "White-box"));
        c.add(t("DPM-045", uc, "DepositManagementService.completeRefund", PRE44, "Nhập reference hợp lệ và tải JPEG hợp lệ.", "Request chuyển PAID; lưu evidence, reference và processor; tạo đúng một REFUND entry; balance và Accountant Available Fund cùng giảm đúng amount.", "Normal", "BR-17, BR-83, BR-115, BR-116, BR-157, BR-164, BR-188", "", "White-box"));
        c.add(t("DPM-046", uc, "DepositManagementService.completeRefund", PRE44, "Nhập reference hợp lệ, không tải evidence.", "Chấp nhận theo quy tắc có một trong hai loại bằng chứng; request chuyển PAID.", "Normal", "BR-157", "", "White-box"));
        c.add(t("DPM-047", uc, "DepositManagementService.completeRefund", PRE44, "Tải evidence hợp lệ, để trống reference.", "Chấp nhận theo quy tắc có một trong hai loại bằng chứng; request chuyển PAID.", "Normal", "BR-157", "", "White-box"));
        c.add(t("DPM-048", uc, "DepositManagementService.completeRefund", PRE44, "Không tải evidence và không nhập reference.", "Hiển thị “Vui lòng tải lên minh chứng chuyển tiền hoặc nhập mã giao dịch trước khi xác nhận đã hoàn cọc.”; request, balance và quỹ không đổi.", "Abnormal", "BR-157", "MSG67", "White-box"));
        c.add(t("DPM-049", uc, "DepositManagementService.completeRefund", PRE44, "Reference đúng 200 ký tự.", "Chấp nhận và lưu đủ 200 ký tự.", "Boundary", "BR-188", "", "White-box"));
        c.add(t("DPM-050", uc, "DepositManagementService.completeRefund", PRE44, "Reference 201 ký tự.", "Hiển thị “Mã giao dịch hoàn chỉ được chứa tối đa 200 ký tự.”; không hoàn tất request.", "Boundary", "BR-188", "MSG03", "White-box"));
        c.add(t("DPM-051", uc, "RefundEvidenceStorageService.store", PRE44 + " Dịch vụ lưu trữ được cô lập bằng mock S3.", "Tệp PNG hợp lệ.", "Nhận diện image/png và tạo metadata lưu trữ đúng byte size.", "Normal", "BR-115", "", "White-box"));
        c.add(t("DPM-052", uc, "RefundEvidenceStorageService.store", PRE44 + " Dịch vụ lưu trữ được cô lập bằng mock S3.", "Tệp WebP hợp lệ.", "Nhận diện image/webp và tạo metadata lưu trữ đúng byte size.", "Normal", "BR-115", "", "White-box"));
        c.add(t("DPM-053", uc, "RefundEvidenceStorageService.store", PRE44 + " Dịch vụ lưu trữ được cô lập bằng mock S3.", "Tệp HEIC hợp lệ.", "Chấp nhận tệp HEIC và tạo metadata lưu trữ.", "Normal", "BR-115", "", "White-box"));
        c.add(t("DPM-054", uc, "RefundEvidenceStorageService.store", PRE44 + " Dịch vụ lưu trữ được cô lập bằng mock S3.", "Tệp HEIF hợp lệ.", "Chấp nhận tệp HEIF và tạo metadata lưu trữ.", "Normal", "BR-115", "", "White-box"));
        c.add(t("DPM-055", uc, "RefundEvidenceStorageService.store", PRE44 + " Dịch vụ lưu trữ được cô lập bằng mock S3.", "Tệp PDF hợp lệ.", "Nhận diện application/pdf và tạo metadata lưu trữ đúng byte size.", "Normal", "BR-115", "", "White-box"));
        c.add(t("DPM-056", uc, "RefundEvidenceStorageService.store", PRE44, "Tệp TXT.", "Hiển thị “Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.”; không lưu file hoặc request.", "Abnormal", "BR-115", "MSG78", "White-box"));
        c.add(t("DPM-057", uc, "RefundEvidenceStorageService.store", PRE44, "Tệp 0 byte.", "Hiển thị “Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.”; không lưu file.", "Boundary", "BR-116", "MSG79", "White-box"));
        c.add(t("DPM-058", uc, "RefundEvidenceStorageService.store", PRE44 + " Dịch vụ lưu trữ được cô lập bằng mock S3.", "Tệp hợp lệ đúng 20.000.000 byte.", "Chấp nhận tệp và tạo metadata lưu trữ đúng 20.000.000 byte.", "Boundary", "BR-116", "", "White-box"));
        c.add(t("DPM-059", uc, "RefundEvidenceStorageService.store", PRE44, "Tệp 20.000.001 byte.", "Hiển thị “Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.”; không tạo file.", "Boundary", "BR-116", "MSG79", "White-box"));
        c.add(t("DPM-060", uc, "RefundEvidenceStorageService.store", PRE44, "Tên và MIME là PNG nhưng content là JPEG.", "Hiển thị “Không thể đọc tệp hoặc định dạng tệp không khớp với nội dung. Vui lòng xuất lại tệp đúng định dạng và tải lên lại.”; không lưu file.", "Abnormal", "BR-115", "MSG80", "White-box"));
        c.add(t("DPM-061", uc, "RefundEvidenceStorageService.store", PRE44, "Đuôi tệp được hỗ trợ nhưng content bị hỏng.", "Hiển thị “Không thể đọc tệp hoặc định dạng tệp không khớp với nội dung. Vui lòng xuất lại tệp đúng định dạng và tải lên lại.”; không lưu file.", "Abnormal", "BR-115", "MSG80", "White-box"));
        c.add(t("DPM-062", uc, "DepositManagementService.completeRefund", PRE44, "Request ID là null.", "Từ chối yêu cầu hoàn cọc không hợp lệ; không lưu evidence, refund entry hoặc cập nhật request.", "Abnormal", "BR-111", "", "White-box"));
        c.add(t("DPM-063", uc, "DepositManagementService.completeRefund", PRE44 + " Request ID không tồn tại.", "Xác nhận hoàn cọc.", "Thông báo không tìm thấy yêu cầu hoàn cọc; không lưu evidence, refund entry hoặc thay đổi số dư.", "Abnormal", "BR-17", "", "White-box"));
        c.add(t("DPM-064", uc, "DepositManagementService.completeRefund", PRE44 + " Request đã PAID.", "Xác nhận lại.", "Hiển thị “Thao tác này đã được ghi nhận. Không tạo thêm bản ghi; vui lòng kiểm tra kết quả hiện có.”; không tạo evidence hoặc refund entry thứ hai.", "Abnormal", "BR-164", "MSG74", "White-box"));
        c.add(t("DPM-065", uc, "DepositManagementService.completeRefund", PRE44 + " Booking không còn CANCELLED.", "Xác nhận hoàn cọc.", "Không hoàn tất; request, balance và quỹ không đổi.", "Abnormal", "BR-14, BR-17", "", "White-box"));
        c.add(t("DPM-066", uc, "DepositManagementService.completeRefund", PRE44 + " Deposit ID hiện tại khác deposit ID trên request.", "Xác nhận hoàn cọc.", "Không hoàn tất; không tạo refund entry hoặc evidence cuối.", "Abnormal", "BR-164", "", "White-box"));
        c.add(t("DPM-067", uc, "DepositManagementService.completeRefund", PRE44 + " Request amount lớn hơn balance.", "Xác nhận hoàn cọc.", "Không hoàn tất; balance không âm và quỹ không đổi.", "Boundary", "BR-17, BR-83", "", "White-box"));
        c.add(t("DPM-068", uc, "DepositManagementService.completeRefund", PRE44 + " Request bị phiên khác xử lý trước lần đọc có khóa.", "Xác nhận đồng thời.", "Hiển thị “Thao tác này đã được ghi nhận. Không tạo thêm bản ghi; vui lòng kiểm tra kết quả hiện có.”; không tạo refund entry trùng; evidence tạm được dọn.", "Abnormal", "BR-164", "MSG74", "White-box"));
        c.add(t("DPM-069", uc, "DepositManagementService.refundEvidence", PRE44_RECEPTION + " Request PAID có evidence thuộc đúng cơ sở.", "Tải evidence.", "Trả đúng filename, MIME và bytes; không phát sinh mutation.", "Normal", "BR-115, BR-157", "", "White-box"));
        c.add(t("DPM-070", uc, "DepositManagementService.refundEvidence", PRE44_RECEPTION + " Request chưa PAID hoặc chưa có evidence.", "Tải evidence.", "Không trả evidence chưa hoàn tất; không thay đổi request.", "Abnormal", "BR-17, BR-157", "", "White-box"));
        c.add(t("DPM-071", uc, "DepositManagementService.refundEvidence", PRE44_RECEPTION + " Evidence thuộc cơ sở khác.", "Tải evidence.", "Hiển thị “Bạn không có quyền xem chứng từ hoàn cọc đối với yêu cầu hoàn cọc.”; không trả metadata hoặc bytes ngoài phạm vi.", "Abnormal", "Không có BR riêng", "MSG24", "White-box"));
        c.add(t("DPM-072", uc, "DepositRepository.completeRefundRequest", PRE44 + " Refund đủ điều kiện được hoàn 100.000đ và kỳ tài chính đang mở.", "Hoàn 100.000đ.", "Ghi refund request, deposit REFUND và financial REFUND trong cùng kết quả; Accountant Available Fund giảm đúng 100.000đ.", "Normal", "BR-83, BR-164", "", "White-box"));
        c.add(t("DPM-073", uc, "DepositManagementService.accountingRefundEvidence", PRE44 + " Request PAID của một cơ sở có evidence.", "Kế toán tải evidence.", "Trả đúng filename, MIME và bytes của evidence bất kể cơ sở tạo request; không phát sinh mutation.", "Normal", "BR-17, BR-115, BR-157", "", "White-box"));
        c.add(t("DPM-074", uc, "DepositManagementService.accountingRefundEvidence", PRE44_RECEPTION + " Request PAID có evidence.", "Lễ tân gọi chức năng tải evidence dành cho kế toán.", "Hiển thị “Bạn không có quyền xử lý yêu cầu hoàn cọc đối với yêu cầu hoàn cọc.”; không đọc bytes evidence.", "Abnormal", "BR-17", "MSG24", "White-box"));
        return List.copyOf(c);
    }

    private static DepositManagementManualRunner.ManualTest t(String id, String uc, String production,
            String pre, String input, String expected, String type, String rules, String messages, String method) {
        String note = "Nguồn: UC-44 - Refund_Booking_Deposit.docx; " + rules
                + (messages.isBlank() ? "" : "; " + messages)
                + "; Flyway V14; Production: " + production
                + ". Phương pháp kiểm thử: " + method + ".";
        return test(id, uc, production, pre, input, expected, type, note);
    }
}
