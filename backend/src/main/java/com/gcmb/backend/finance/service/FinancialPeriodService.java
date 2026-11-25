package com.gcmb.backend.finance.service;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.finance.repository.FinancialPeriodRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinancialPeriodService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final List<String> ROLES = List.of("ACCOUNTANT");

    private final FinancialPeriodRepository financialPeriodRepository;
    private final MutationAudit mutationAudit;
    @Value("${gcmb.finance.period-close.allow-early-close:${GCMB_ALLOW_EARLY_PERIOD_CLOSE:false}}")
    private boolean allowEarlyClose;

    public FinancialPeriodService(FinancialPeriodRepository financialPeriodRepository, MutationAudit mutationAudit) {
        this.financialPeriodRepository = financialPeriodRepository;
        this.mutationAudit = mutationAudit;
    }

    public void requireOpen(LocalDate date, String operation) {
        if (date == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày giao dịch không hợp lệ.");
        LocalDate month = date.withDayOfMonth(1);
        financialPeriodRepository.ensurePeriod(month);
        String status = financialPeriodRepository.lockPeriodStatusForShare(month);
        if (!"OPEN".equals(status)) throw new AuthFailure(409, "form",
            "Kỳ tài chính " + LABEL.format(YearMonth.from(month)) + " đã khóa. Không thể " + operation + ".");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Identity actor) {
        accountant(actor);
        return financialPeriodRepository.findPeriodsWithActivity();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> readiness(Identity actor, LocalDate month) {
        accountant(actor);
        LocalDate start = firstOfMonth(month);
        return readiness(start, currentStatus(start));
    }

    @Transactional
    public Map<String, Object> close(Identity actor, LocalDate month, boolean confirmed, String note) {
        accountant(actor);
        if (!confirmed) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng xác nhận khóa kỳ tài chính.");
        String closeNote = note == null ? "" : note.trim();
        if (closeNote.length() > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ghi chú chỉ được chứa tối đa 1000 ký tự.");
        LocalDate start = firstOfMonth(month);
        boolean monthEnded = periodEnded(start);
        if (!monthEnded && !allowEarlyClose) throw new AuthFailure(409, "form", "Chỉ có thể khóa kỳ sau khi tháng đã kết thúc.");
        boolean earlyClose = allowEarlyClose && !monthEnded;
        financialPeriodRepository.ensurePeriod(start);
        UUID periodId = financialPeriodRepository.lockPeriodIdForUpdate(start);
        UUID closedId = mutationAudit.recordWithMetadata("CLOSE_FINANCIAL_PERIOD", "FINANCIAL_PERIOD", periodId, () -> {
            if ("CLOSED".equals(financialPeriodRepository.lockPeriodStatusById(periodId)))
                throw new AuthFailure(409, "form", "Kỳ tài chính này đã được khóa.");
            Map<String, Object> checks = readiness(start, "OPEN");
            @SuppressWarnings("unchecked") List<Map<String, Object>> items = (List<Map<String, Object>>) checks.get("checks");
            List<String> blockers = items.stream().filter(item -> !Boolean.TRUE.equals(item.get("passed")))
                .map(item -> item.get("label") + ": còn " + item.get("pendingCount"))
                .toList();
            if (!blockers.isEmpty()) throw new AuthFailure(409, "form", "Chưa thể khóa kỳ " + LABEL.format(YearMonth.from(start)) + ". " + String.join("; ", blockers) + ".");
            if (financialPeriodRepository.closePeriod(periodId, actor.id(), closeNote) != 1)
                throw new AuthFailure(409, "form", "Kỳ tài chính vừa được khóa ở phiên khác.");
            return periodId;
        }, value -> value, earlyClose ? "{\"earlyClose\":true}" : null);
        return financialPeriodRepository.getPeriodView(closedId);
    }

    private Map<String, Object> readiness(LocalDate start, String periodStatus) {
        LocalDate end = start.plusMonths(1);
        boolean monthEnded = periodEnded(start);
        long periodEndPending = monthEnded || allowEarlyClose ? 0 : 1;
        long unreconciledCount = financialPeriodRepository.countUnreconciledClosings(start, end);
        long missingActivityClosings = financialPeriodRepository.countCashActivityWithoutClosing(start, end);
        long cashPending = unreconciledCount + missingActivityClosings;
        long attendancePending = financialPeriodRepository.countBranchesWithOpenAttendance(start);
        long payrollPending = financialPeriodRepository.countUnconfirmedPayroll(start);
        long expensesPending = financialPeriodRepository.countPendingExpenseRequests(start, end);
        long refundsPending = financialPeriodRepository.countPendingRefunds(
            start.atStartOfDay(ZONE).toOffsetDateTime(), end.atStartOfDay(ZONE).toOffsetDateTime());
        long sepayPending = financialPeriodRepository.countUnresolvedSePayTransactionsBefore(end.atStartOfDay(ZONE).toOffsetDateTime());

        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(check("periodEnded",
            allowEarlyClose ? "Chế độ demo: cho phép chốt khi tháng chưa kết thúc" : "Kỳ đã kết thúc",
            periodEndPending,
            allowEarlyClose ? "Chế độ demo đang bật; các điều kiện khác vẫn bắt buộc." : "Chỉ khóa sau khi đã sang tháng tiếp theo."));
        // BR-99: demo chỉ bỏ qua điều kiện kết thúc tháng; đối soát tiền mặt vẫn bắt buộc.
        checks.add(check("cashReconciliation", "Đối soát tiền mặt", cashPending,
            "Tính các phiếu chưa đối soát và ngày, quỹ có phát sinh dòng tiền nhưng chưa có phiếu đối soát."));
        checks.add(check("attendance", "Chốt công tháng", attendancePending,
            "Mỗi cơ sở đang hoạt động cần có kỳ công đã chốt."));
        checks.add(check("payroll", "Chốt bảng lương", payrollPending,
            "Bảng lương của tháng phải được xác nhận."));
        checks.add(check("expenses", "Xử lý đề nghị chi và hoàn ứng", expensesPending,
            "Đếm các đề nghị thanh toán hoặc hoàn ứng còn chờ duyệt hoặc chờ chi theo ngày chi."));
        checks.add(check("refunds", "Xử lý hoàn cọc", refundsPending,
            "Đếm yêu cầu hoàn cọc tạo trong tháng còn mới gửi, đã duyệt hoặc đang xử lý."));
        checks.add(check("sepayExceptions", "Xử lý giao dịch ngân hàng chưa khớp", sepayPending,
            "Đếm các giao dịch ngân hàng qua SePay chưa xử lý, có ngày giao dịch trước khi kỳ kết thúc, kể cả giao dịch từ kỳ trước."));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", start.toString().substring(0, 7));
        result.put("status", periodStatus);
        result.put("earlyCloseEnabled", allowEarlyClose);
        result.put("checks", checks);
        result.put("canClose", "OPEN".equals(periodStatus) && checks.stream().allMatch(item -> Boolean.TRUE.equals(item.get("passed"))));
        result.put("cashCalendarLimitation", "Không có lịch ngày bắt buộc chốt quỹ; hệ thống kiểm tra các ngày/quỹ có phát sinh dòng tiền và các phiếu đã tạo.");
        return result;
    }

    private Map<String, Object> check(String key, String label, long pending, String detail) {
        return Map.of("key", key, "label", label, "pendingCount", pending, "passed", pending == 0, "detail", detail);
    }

    private String currentStatus(LocalDate start) {
        List<String> statuses = financialPeriodRepository.findPeriodStatus(start);
        return statuses.isEmpty() ? "OPEN" : statuses.get(0);
    }

    @Transactional
    public Map<String, Object> resolveSePayException(Identity actor, UUID transactionId, String note) {
        accountant(actor);
        if (transactionId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giao dịch ngoại lệ không hợp lệ.");
        String resolutionNote = note == null ? "" : note.trim();
        if (resolutionNote.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập ghi chú xử lý.");
        if (resolutionNote.length() > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ghi chú xử lý chỉ được chứa tối đa 1000 ký tự.");
        UUID resolvedId = mutationAudit.record("RESOLVE_SEPAY_EXCEPTION", "BANK_TRANSACTION", transactionId, () -> {
            List<Map<String, Object>> rows = financialPeriodRepository.lockSePayTransaction(transactionId);
            if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch SePay ngoại lệ.");
            Map<String, Object> transaction = rows.get(0);
            if (!List.of("EXCEPTION", "UNMATCHED").contains(transaction.get("match_status")))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ xử lý giao dịch ngoại lệ hoặc chưa khớp thanh toán.");
            if ("UNRESOLVED".equals(transaction.get("resolution_status"))) {
                financialPeriodRepository.resolveSePayTransaction(transactionId, actor.id(), resolutionNote);
            }
            return transactionId;
        }, value -> value);
        return financialPeriodRepository.getSePayTransactionView(resolvedId);
    }

    private static LocalDate firstOfMonth(LocalDate month) {
        if (month == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tháng tài chính không hợp lệ.");
        return month.withDayOfMonth(1);
    }

    private static boolean periodEnded(LocalDate start) {
        return start.isBefore(LocalDate.now(ZONE).withDayOfMonth(1));
    }

    private static void accountant(Identity actor) {
        if (actor == null || !ROLES.contains(actor.role())) throw new AuthFailure(403, "form", "Bạn không có quyền thực hiện đối với nghiệp vụ kế toán.");
    }
}
