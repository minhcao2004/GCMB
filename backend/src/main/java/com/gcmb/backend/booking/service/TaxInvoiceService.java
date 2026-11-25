/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: Lễ tân gửi yêu cầu; kế toán cập nhật tiến độ hóa đơn theo UC-127/BR-125 đã thống nhất lại.
 */
package com.gcmb.backend.booking.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.auth.service.AuthValidation;
import com.gcmb.backend.booking.repository.TaxInvoiceRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class TaxInvoiceService {

    private final TaxInvoiceRepository taxInvoiceRepository;
    private final ObjectMapper objectMapper;

    public TaxInvoiceService(TaxInvoiceRepository taxInvoiceRepository, ObjectMapper objectMapper) {
        this.taxInvoiceRepository = taxInvoiceRepository;
        this.objectMapper = objectMapper;
    }

    public record Request(
        String buyerName,
        String taxCode,
        String buyerAddress,
        String contactEmail,
        String note
    ) {}

    public record Progress(String status, String deliveryResult, Long version) {}

    private static final Set<String> STATUSES = Set.of(
        "REQUESTED",
        "PREPARING",
        "ISSUED",
        "DELIVERED",
        "DELIVERY_FAILED"
    );
    private static final Set<String> DELIVERED_RESULTS = Set.of(
        "DELIVERED",
        "EMAIL",
        "DIRECT",
        "MESSAGE"
    );
    private static final Set<String> FAILED_RESULTS = Set.of(
        "INVALID_CONTACT",
        "UNREACHABLE",
        "SEND_FAILED"
    );

    private void authorize(Identity actor, boolean receptionOnly) {
        if (
            actor == null ||
            !(receptionOnly
                ? "RECEPTIONIST".equals(actor.role())
                : Set.of("RECEPTIONIST", "ACCOUNTANT").contains(actor.role()))
        ) throw denied();
        if (!taxInvoiceRepository.isActiveAccountInScope(actor.id(), actor.role(), actor.branchId())) throw denied();
    }

    private Map<String, Object> booking(Identity actor, UUID bookingId, boolean writing) {
        authorize(actor, false);
        var rows = taxInvoiceRepository.findBookingWithBranch(bookingId, writing);
        if (rows.isEmpty()) throw new AuthFailure(404, "form", "Không tìm thấy booking.");
        var bookingRow = rows.get(0);
        if (
            "RECEPTIONIST".equals(actor.role()) &&
            !Objects.equals(actor.branchId(), bookingRow.get("branch_id"))
        ) throw denied();
        // BR-177 / MSG43: cơ sở tạm ngừng vẫn xem được yêu cầu và lịch sử.
        if (writing && !"ACTIVE".equals(bookingRow.get("status"))) throw new AuthFailure(
            403,
            "form",
            "Cơ sở " +
                bookingRow.get("name") +
                " đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi."
        );
        return bookingRow;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Identity actor) {
        authorize(actor, false);
        return taxInvoiceRepository.findRequestsInScope(actor.role(), actor.branchId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Identity actor, UUID bookingId) {
        var bookingRow = booking(actor, bookingId, false);
        var requests = taxInvoiceRepository.findRequest(bookingId);
        var result = new LinkedHashMap<String, Object>();
        result.put("bookingId", bookingId);
        result.put("bookingCode", bookingRow.get("code"));
        // UC-127: the booking already uniquely identifies its checkout bill.
        var bills = taxInvoiceRepository.findBillsForBooking(bookingId);
        result.put("bill", null);
        if (!bills.isEmpty()) {
            var bill = bills.get(0);
            bill.put("lines", taxInvoiceRepository.findBillLines(bill.get("id")));
            result.put("bill", bill);
        }
        result.put("request", requests.isEmpty() ? null : requests.get(0));
        result.put("history", taxInvoiceRepository.findHistory(bookingId));
        return result;
    }

    public Map<String, Object> create(Identity actor, UUID bookingId, Request input) {
        authorize(actor, true); // Lễ tân tiếp nhận yêu cầu của khách và gửi kế toán.
        var bookingRow = booking(actor, bookingId, true);
        if (input == null) throw new AuthFailure(
            400,
            "form",
            "Vui lòng bổ sung thông tin yêu cầu hóa đơn."
        );
        // BR-111/113; MSG02/03/05. Request snapshot is retained for external handling.
        String buyerName = AuthValidation.required(
            input.buyerName(),
            "buyerName",
            "tên người mua / đơn vị",
            200,
            true
        );
        String email = optional(input.contactEmail(), "contactEmail", "Email", 254);
        if (!email.isEmpty()) email = AuthValidation.email(email);
        if (taxInvoiceRepository.requestExists(bookingId)) throw new AuthFailure(
            409,
            "form",
            "Booking đã có yêu cầu hóa đơn. Vui lòng xem yêu cầu hiện có."
        );
        taxInvoiceRepository.insertRequest(
            bookingId,
            buyerName,
            optional(input.taxCode(), "taxCode", "Mã số thuế", 30),
            optional(input.buyerAddress(), "buyerAddress", "Địa chỉ", 2000),
            email,
            optional(input.note(), "note", "Ghi chú", 2000),
            actor.id()
        );
        audit(actor, bookingId, bookingRow, "CREATE_TAX_INVOICE_REQUEST");
        return detail(actor, bookingId); // Yêu cầu mới đồng thời xuất hiện trong hộp thông báo của kế toán.
    }

    public Map<String, Object> progress(Identity actor, UUID bookingId, Progress input) {
        // UC-127 / BR-125: theo phân công mới của người dùng, chỉ kế toán được cập nhật tiến độ.
        authorize(actor, false);
        if (!"ACCOUNTANT".equals(actor.role())) throw denied(); // MSG24, áp dụng cả khi gọi trực tiếp service.
        var bookingRow = booking(actor, bookingId, true);
        var rows = taxInvoiceRepository.lockRequestProgress(bookingId);
        if (rows.isEmpty()) throw new AuthFailure(
            404,
            "form",
            "Booking chưa có yêu cầu hóa đơn thuế."
        );
        var current = rows.get(0);
        if (
            input == null ||
            input.version() == null ||
            input.version().longValue() != ((Number) current.get("version")).longValue()
        ) throw new AuthFailure(
            409,
            "form",
            "Yêu cầu đã thay đổi. Vui lòng tải lại và kiểm tra trước khi xác nhận."
        );
        if (input.status() == null || !STATUSES.contains(input.status())) throw new AuthFailure(
            400,
            "status",
            "Vui lòng bổ sung trạng thái xử lý hợp lệ."
        );
        String deliveryResult = optional(
            input.deliveryResult(),
            "deliveryResult",
            "Kết quả giao hóa đơn",
            2000
        );
        if (Set.of("DELIVERED", "DELIVERY_FAILED").contains(input.status())) deliveryResult =
            AuthValidation.required(deliveryResult, "deliveryResult", "kết quả giao hóa đơn", 2000, true);
        // UC-127 theo yêu cầu đã chốt: dùng lựa chọn kết quả cố định, không nhập ghi chú xử lý.
        boolean allowed = "DELIVERED".equals(input.status())
            ? DELIVERED_RESULTS.contains(deliveryResult)
            : "DELIVERY_FAILED".equals(input.status())
              ? FAILED_RESULTS.contains(deliveryResult)
              : deliveryResult.isEmpty();
        // Giữ nguyên văn kết quả cũ khi trạng thái/kết quả không đổi, không biến dữ liệu lịch sử thành lỗi.
        boolean unchanged =
            Objects.equals(input.status(), current.get("status")) &&
            Objects.equals(deliveryResult, current.get("delivery_result"));
        if (!allowed && !unchanged) throw new AuthFailure(
            400,
            "deliveryResult",
            "Vui lòng chọn kết quả giao hóa đơn phù hợp với trạng thái xử lý."
        );
        taxInvoiceRepository.updateProgress(bookingId, input.status(), deliveryResult, actor.id());
        audit(actor, bookingId, bookingRow, "UPDATE_TAX_INVOICE_REQUEST");
        return detail(actor, bookingId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> unread(Identity actor) {
        authorize(actor, false);
        if (!"ACCOUNTANT".equals(actor.role())) throw denied();
        return Map.of("count", taxInvoiceRepository.countUnread(actor.id()));
    }

    public void read(Identity actor, UUID bookingId) {
        authorize(actor, false);
        if (!"ACCOUNTANT".equals(actor.role())) throw denied();
        if (!taxInvoiceRepository.requestExists(bookingId)) throw new AuthFailure(404, "form", "Không tìm thấy yêu cầu hóa đơn.");
        taxInvoiceRepository.markRead(bookingId, actor.id());
    }

    private void audit(Identity actor, UUID bookingId, Map<String, Object> bookingRow, String action) {
        var after = taxInvoiceRepository.getRequest(bookingId);
        taxInvoiceRepository.insertAudit(
            actor.id(),
            bookingRow.get("branch_id"),
            action,
            bookingId,
            objectMapper.writeValueAsString(after)
        );
    }

    private static String optional(String value, String field, String label, int max) {
        return value == null || value.isBlank()
            ? ""
            : AuthValidation.required(value, field, label, max, true);
    }

    private static AuthFailure denied() {
        return new AuthFailure(
            403,
            "form",
            "Bạn không có quyền thực hiện thao tác đối với yêu cầu hóa đơn thuế."
        );
    } // MSG24
}
