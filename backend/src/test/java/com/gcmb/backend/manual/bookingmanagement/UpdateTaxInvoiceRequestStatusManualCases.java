/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-127 Update Tax Invoice Request Status manual unit test cases.
 */
package com.gcmb.backend.manual.bookingmanagement;

import static com.gcmb.backend.manual.bookingmanagement.BookingManagementManualRunner.check;
import static com.gcmb.backend.manual.bookingmanagement.BookingManagementManualRunner.test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.booking.service.TaxInvoiceService;
import com.gcmb.backend.booking.service.TaxInvoiceService.Progress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

final class UpdateTaxInvoiceRequestStatusManualCases {
    private static final String UC = "UC-127 Update Tax Invoice Request Status";
    private static final UUID ACCOUNTANT_ID = UUID.fromString("12700000-0000-4000-8000-000000000001");
    private static final UUID RECEPTIONIST_ID = UUID.fromString("12700000-0000-4000-8000-000000000002");
    private static final UUID BRANCH_ID = UUID.fromString("12700000-0000-4000-8000-000000000010");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("12700000-0000-4000-8000-000000000011");
    private static final UUID BOOKING_ID = UUID.fromString("12700000-0000-4000-8000-000000000020");
    private static final String PRE = "Kế toán ketoan đã đăng nhập; booking BK-TAX-127 có yêu cầu hóa đơn thuế tại cơ sở đang hoạt động.";

    private UpdateTaxInvoiceRequestStatusManualCases() {}

    static List<BookingManagementManualRunner.ManualTest> cases() {
        return List.of(
            tc("BKM-073", "TaxInvoiceService.list", "Kế toán ketoan đã đăng nhập; có yêu cầu hóa đơn tại nhiều cơ sở.", "Mở danh sách yêu cầu hóa đơn.", "Trả yêu cầu của tất cả cơ sở; không ghi dữ liệu.", "Normal", "BR-125", "", "Black-box", UpdateTaxInvoiceRequestStatusManualCases::accountantList),
            tc("BKM-074", "TaxInvoiceService.detail", "Lễ tân letan.hoabang đăng nhập; booking/yêu cầu thuộc HN_HOA_BANG.", "Mở chi tiết BK-TAX-127.", "Trả booking, bill, yêu cầu và lịch sử thuộc cơ sở.", "Normal", "BR-125, BR-177", "", "Black-box", UpdateTaxInvoiceRequestStatusManualCases::receptionDetail),
            tc("BKM-075", "TaxInvoiceService.detail", "Lễ tân letan.hoabang đăng nhập; booking thuộc cơ sở khác.", "Mở chi tiết BK-TAX-127.", "Hiển thị “Bạn không có quyền thực hiện thao tác đối với yêu cầu hóa đơn thuế.”; không lộ dữ liệu.", "Abnormal", "BR-177", "MSG24", "White-box", UpdateTaxInvoiceRequestStatusManualCases::crossBranchDetail),
            tc("BKM-076", "TaxInvoiceService.progress", PRE + " Trạng thái REQUESTED, version=1.", "status=PREPARING; version=1.", "Cập nhật PREPARING, version=2 và tạo đúng một audit.", "Normal", "BR-125, BR-164", "", "White-box", () -> progress("REQUESTED", null, "PREPARING", null)),
            tc("BKM-077", "TaxInvoiceService.progress", PRE + " Trạng thái ISSUED, version=1.", "status=DELIVERED; deliveryResult=EMAIL; version=1.", "Cập nhật DELIVERED/EMAIL, tăng version và ghi audit.", "Normal", "BR-125", "", "White-box", () -> progress("ISSUED", null, "DELIVERED", "EMAIL")),
            tc("BKM-078", "TaxInvoiceService.progress", PRE + " Trạng thái ISSUED, version=1.", "status=DELIVERY_FAILED; deliveryResult=INVALID_CONTACT; version=1.", "Cập nhật DELIVERY_FAILED/INVALID_CONTACT, tăng version và ghi audit.", "Normal", "BR-125", "", "White-box", () -> progress("ISSUED", null, "DELIVERY_FAILED", "INVALID_CONTACT")),
            tc("BKM-079", "TaxInvoiceService.progress", "Lễ tân letan.hoabang đăng nhập; yêu cầu hóa đơn tồn tại.", "status=PREPARING; version=1.", "Hiển thị “Bạn không có quyền thực hiện thao tác đối với yêu cầu hóa đơn thuế.”; không cập nhật.", "Abnormal", "BR-125", "MSG24", "White-box", UpdateTaxInvoiceRequestStatusManualCases::receptionCannotUpdate),
            tc("BKM-080", "TaxInvoiceService.progress", "Kế toán đăng nhập; booking tồn tại nhưng chưa có yêu cầu hóa đơn.", "status=PREPARING; version=1.", "Hiển thị “Booking chưa có yêu cầu hóa đơn thuế.”; không cập nhật.", "Abnormal", "BR-125", "", "White-box", UpdateTaxInvoiceRequestStatusManualCases::missingRequest),
            tc("BKM-081", "TaxInvoiceService.progress", PRE + " Version hiện tại=1.", "status=PREPARING; version=0.", "Hiển thị “Yêu cầu đã thay đổi. Vui lòng tải lại và kiểm tra trước khi xác nhận.”; không cập nhật.", "Abnormal", "BR-164", "MSG74", "White-box", UpdateTaxInvoiceRequestStatusManualCases::staleVersion),
            tc("BKM-082", "TaxInvoiceService.progress", PRE + " Version hiện tại=1.", "status=UNKNOWN; version=1.", "Hiển thị “Vui lòng bổ sung trạng thái xử lý hợp lệ.”; không cập nhật.", "Abnormal", "BR-111", "MSG02", "White-box", UpdateTaxInvoiceRequestStatusManualCases::invalidStatus),
            tc("BKM-083", "TaxInvoiceService.progress", PRE + " Version hiện tại=1.", "status=DELIVERED; deliveryResult=null.", "Hiển thị “Vui lòng bổ sung kết quả giao hóa đơn.”; không cập nhật.", "Abnormal", "BR-111, BR-125", "MSG02", "White-box", UpdateTaxInvoiceRequestStatusManualCases::missingDelivery),
            tc("BKM-084", "TaxInvoiceService.progress", PRE + " Version hiện tại=1.", "status=DELIVERED; deliveryResult=INVALID_CONTACT.", "Hiển thị “Vui lòng chọn kết quả giao hóa đơn phù hợp với trạng thái xử lý.”; không cập nhật.", "Abnormal", "BR-125", "", "White-box", UpdateTaxInvoiceRequestStatusManualCases::mismatchedDelivery),
            tc("BKM-085", "TaxInvoiceService.progress", "Kế toán đăng nhập; booking thuộc cơ sở HN_HOA_BANG đang tạm ngừng.", "status=PREPARING; version=1.", "Hiển thị “Cơ sở HN_HOA_BANG đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.”; không cập nhật.", "Abnormal", "BR-177", "MSG43", "White-box", UpdateTaxInvoiceRequestStatusManualCases::suspendedBranch),
            tc("BKM-086", "TaxInvoiceService.progress", PRE + " Trạng thái PREPARING, version=1.", "status=PREPARING; deliveryResult rỗng; version=1.", "Vẫn tăng version lên 2 và tạo đúng một audit; không tạo bản ghi yêu cầu thứ hai.", "Abnormal", "BR-164", "MSG74", "White-box", UpdateTaxInvoiceRequestStatusManualCases::sameStatus),
            tc("BKM-087", "TaxInvoiceService.progress", PRE + " Booking đã có charge và payment.", "Cập nhật trạng thái yêu cầu hóa đơn.", "Chỉ thay đổi tax_invoice_request và audit; charge/payment giữ nguyên.", "Normal", "BR-125", "", "White-box", UpdateTaxInvoiceRequestStatusManualCases::financialDataUnchanged),
            tc("BKM-088", "TaxInvoiceService.progress/detail", PRE + " Trạng thái REQUESTED, version=1.", "Cập nhật PREPARING rồi ISSUED; mở chi tiết.", "Chi tiết trả ISSUED/version=3 và hai sự kiện lịch sử theo thứ tự thời gian.", "Normal", "BR-125, BR-164", "", "White-box", UpdateTaxInvoiceRequestStatusManualCases::history)
        );
    }

    private static BookingManagementManualRunner.ManualTest tc(String id, String source, String pre, String input,
            String expected, String type, String rules, String messages, String method,
            BookingManagementManualRunner.CheckedAction action) {
        String note = "Nguồn: UC-127 - Update_Tax_Invoice_Request_Status.docx; " + rules
                + (messages.isBlank() ? "" : "; " + messages)
                + "; Flyway V14, V37; Production: " + source + ". Phương pháp kiểm thử: " + method + ".";
        return test(id, UC, source, pre, input, expected, type, note, action);
    }

    private static Identity accountant() {
        return new Identity(ACCOUNTANT_ID, "ketoan", "ACCOUNTANT", "/accounting", null, null);
    }

    private static Identity receptionist() {
        return new Identity(RECEPTIONIST_ID, "letan.hoabang", "RECEPTIONIST", "/reception", BRANCH_ID, "HN_HOA_BANG");
    }

    private static BookingManagementManualRunner.TestResult accountantList() {
        var f = fixture();
        f.state().listRows = List.of(f.state().requestRow(), new LinkedHashMap<>(f.state().requestRow()));
        f.state().listRows.get(1).put("branchId", OTHER_BRANCH_ID);
        var rows = f.service().list(accountant());
        return check(rows.size() == 2 && f.state().updates == 0, "rows=" + rows.size() + ", updates=" + f.state().updates);
    }

    private static BookingManagementManualRunner.TestResult receptionDetail() {
        var f = fixture();
        var detail = f.service().detail(receptionist(), BOOKING_ID);
        return check(detail.get("request") != null && Objects.equals(detail.get("bookingCode"), "BK-TAX-127"),
                "bookingCode=" + detail.get("bookingCode") + ", requestPresent=" + (detail.get("request") != null));
    }

    private static BookingManagementManualRunner.TestResult crossBranchDetail() {
        var f = fixture();
        f.state().bookingBranch = OTHER_BRANCH_ID;
        return expectFailure(() -> f.service().detail(receptionist(), BOOKING_ID), 403,
                "Bạn không có quyền thực hiện thao tác đối với yêu cầu hóa đơn thuế.");
    }

    private static BookingManagementManualRunner.TestResult progress(String before, String beforeDelivery,
            String after, String afterDelivery) {
        var f = fixture();
        f.state().status = before;
        f.state().delivery = beforeDelivery;
        var detail = f.service().progress(accountant(), BOOKING_ID, new Progress(after, afterDelivery, 1L));
        String expectedDelivery = afterDelivery == null ? "" : afterDelivery;
        return check(Objects.equals(f.state().status, after) && Objects.equals(f.state().delivery, expectedDelivery)
                        && f.state().version == 2 && f.state().auditCount == 1 && detail.get("request") != null,
                "status=" + f.state().status + ", delivery=" + f.state().delivery + ", version="
                        + f.state().version + ", audits=" + f.state().auditCount);
    }

    private static BookingManagementManualRunner.TestResult receptionCannotUpdate() {
        var f = fixture();
        return expectFailure(() -> f.service().progress(receptionist(), BOOKING_ID, new Progress("PREPARING", null, 1L)),
                403, "Bạn không có quyền thực hiện thao tác đối với yêu cầu hóa đơn thuế.");
    }

    private static BookingManagementManualRunner.TestResult missingRequest() {
        var f = fixture();
        f.state().requestExists = false;
        return expectFailure(() -> f.service().progress(accountant(), BOOKING_ID, new Progress("PREPARING", null, 1L)),
                404, "Booking chưa có yêu cầu hóa đơn thuế.");
    }

    private static BookingManagementManualRunner.TestResult staleVersion() {
        var f = fixture();
        return expectFailure(() -> f.service().progress(accountant(), BOOKING_ID, new Progress("PREPARING", null, 0L)),
                409, "Yêu cầu đã thay đổi. Vui lòng tải lại và kiểm tra trước khi xác nhận.");
    }

    private static BookingManagementManualRunner.TestResult invalidStatus() {
        var f = fixture();
        return expectFailure(() -> f.service().progress(accountant(), BOOKING_ID, new Progress("UNKNOWN", null, 1L)),
                400, "Vui lòng bổ sung trạng thái xử lý hợp lệ.");
    }

    private static BookingManagementManualRunner.TestResult missingDelivery() {
        var f = fixture();
        return expectFailure(() -> f.service().progress(accountant(), BOOKING_ID, new Progress("DELIVERED", null, 1L)),
                400, "Vui lòng bổ sung kết quả giao hóa đơn.");
    }

    private static BookingManagementManualRunner.TestResult mismatchedDelivery() {
        var f = fixture();
        return expectFailure(() -> f.service().progress(accountant(), BOOKING_ID, new Progress("DELIVERED", "INVALID_CONTACT", 1L)),
                400, "Vui lòng chọn kết quả giao hóa đơn phù hợp với trạng thái xử lý.");
    }

    private static BookingManagementManualRunner.TestResult suspendedBranch() {
        var f = fixture();
        f.state().branchStatus = "SUSPENDED";
        return expectFailure(() -> f.service().progress(accountant(), BOOKING_ID, new Progress("PREPARING", null, 1L)),
                403, "Cơ sở HN_HOA_BANG đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.");
    }

    private static BookingManagementManualRunner.TestResult sameStatus() {
        var f = fixture();
        f.state().status = "PREPARING";
        f.service().progress(accountant(), BOOKING_ID, new Progress("PREPARING", "", 1L));
        return check(f.state().version == 2 && f.state().auditCount == 1 && f.state().updates == 1,
                "version=" + f.state().version + ", audits=" + f.state().auditCount + ", updates=" + f.state().updates);
    }

    private static BookingManagementManualRunner.TestResult financialDataUnchanged() {
        var f = fixture();
        f.service().progress(accountant(), BOOKING_ID, new Progress("PREPARING", null, 1L));
        boolean financialWrite = f.state().sql.stream().anyMatch(sql -> sql.matches("(?is).*UPDATE\\s+gcmb\\.(booking_charge|payment|bill).*"));
        return check(!financialWrite, "financial write SQL found=" + financialWrite);
    }

    private static BookingManagementManualRunner.TestResult history() {
        var f = fixture();
        f.service().progress(accountant(), BOOKING_ID, new Progress("PREPARING", null, 1L));
        f.service().progress(accountant(), BOOKING_ID, new Progress("ISSUED", null, 2L));
        var detail = f.service().detail(accountant(), BOOKING_ID);
        @SuppressWarnings("unchecked")
        var history = (List<Map<String, Object>>) detail.get("history");
        return check("ISSUED".equals(f.state().status) && f.state().version == 3 && history.size() == 2,
                "status=" + f.state().status + ", version=" + f.state().version + ", history=" + history.size());
    }

    private static BookingManagementManualRunner.TestResult expectFailure(
            BookingManagementManualRunner.ThrowingRunnable action, int status, String message) {
        try {
            action.run();
            return check(false, "No AuthFailure was thrown");
        } catch (AuthFailure failure) {
            return check(failure.status() == status && Objects.equals(message, failure.getMessage()),
                    "status=" + failure.status() + ", message=" + failure.getMessage());
        } catch (Throwable failure) {
            return check(false, failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static Fixture fixture() {
        State state = new State();
        JdbcTemplate db = mock(JdbcTemplate.class);
        doAnswer(state::answer).when(db).queryForObject(any(String.class), any(Class.class), any(Object[].class));
        doAnswer(state::answer).when(db).queryForList(any(String.class), any(Object[].class));
        doAnswer(state::answer).when(db).queryForMap(any(String.class), any(Object[].class));
        doAnswer(state::answer).when(db).update(any(String.class), any(Object[].class));
        return new Fixture(new TaxInvoiceService(db, new ObjectMapper()), state);
    }

    private record Fixture(TaxInvoiceService service, State state) {}

    private static final class State {
        UUID bookingBranch = BRANCH_ID;
        String branchStatus = "ACTIVE";
        String status = "REQUESTED";
        String delivery;
        long version = 1;
        boolean requestExists = true;
        int updates;
        int auditCount;
        List<Map<String, Object>> listRows = List.of();
        final List<String> sql = new ArrayList<>();
        final List<Map<String, Object>> history = new ArrayList<>();

        Object answer(InvocationOnMock invocation) {
            String method = invocation.getMethod().getName();
            Object[] args = invocation.getArguments();
            String statement = (String) args[0];
            sql.add(statement);
            if ("queryForObject".equals(method)) return Boolean.TRUE;
            if ("queryForMap".equals(method)) return requestRow();
            if ("update".equals(method)) return update(statement, args);
            if (statement.contains("FROM gcmb.booking b JOIN gcmb.branch")) return List.of(bookingRow());
            if (statement.contains("SELECT version,status,delivery_result")) {
                return requestExists ? List.of(versionRow()) : List.of();
            }
            if (statement.contains("WHERE (?='ACCOUNTANT'")) {
                return listRows.isEmpty() ? List.of(requestRow()) : listRows;
            }
            if (statement.contains("WHERE t.booking_id=?")) return requestExists ? List.of(requestRow()) : List.of();
            if (statement.contains("FROM gcmb.bill b")) return List.of();
            if (statement.contains("FROM gcmb.audit_event")) return List.copyOf(history);
            return List.of();
        }

        private int update(String statement, Object[] args) {
            if (statement.contains("UPDATE gcmb.tax_invoice_request")) {
                status = (String) args[1];
                delivery = (String) args[2];
                version++;
                updates++;
            } else if (statement.contains("INSERT INTO gcmb.audit_event")) {
                auditCount++;
                history.add(Map.of("action", "UPDATE_TAX_INVOICE_REQUEST", "data", "{}", "createdAt", Instant.now()));
            }
            return 1;
        }

        Map<String, Object> bookingRow() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", BOOKING_ID);
            row.put("code", "BK-TAX-127");
            row.put("branch_id", bookingBranch);
            row.put("name", "HN_HOA_BANG");
            row.put("status", branchStatus);
            return row;
        }

        Map<String, Object> versionRow() {
            Map<String, Object> row = new HashMap<>();
            row.put("version", version);
            row.put("status", status);
            row.put("delivery_result", delivery);
            return row;
        }

        Map<String, Object> requestRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bookingId", BOOKING_ID);
            row.put("bookingCode", "BK-TAX-127");
            row.put("branchId", bookingBranch);
            row.put("branchName", "HN_HOA_BANG");
            row.put("status", status);
            row.put("deliveryResult", delivery);
            row.put("version", version);
            return row;
        }
    }
}
