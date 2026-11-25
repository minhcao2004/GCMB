package com.gcmb.backend.dashboard;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.dashboard.repository.FinancialReportRepository;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService.StoredFile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
// Ghi bút toán điều chỉnh cho giao dịch thu/chi gốc: không sửa giao dịch cũ, ghi vào kỳ đang mở hiện tại.
public class FinancialTransactionAdjustmentService {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999999999");

    private final FinancialReportRepository financialReportRepository;
    private final MutationAudit mutationAudit;
    private final EquipmentAssetFileStorageService fileStorageService;

    public FinancialTransactionAdjustmentService(FinancialReportRepository financialReportRepository,
            MutationAudit mutationAudit, EquipmentAssetFileStorageService fileStorageService) {
        this.financialReportRepository = financialReportRepository;
        this.mutationAudit = mutationAudit;
        this.fileStorageService = fileStorageService;
    }

    public Map<String, Object> adjust(Identity actor, UUID transactionId, Object amountSigned, String reasonText,
            MultipartFile evidence) {
        if (actor == null || !"ACCOUNTANT".equals(actor.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện đối với nghiệp vụ kế toán.");
        String reason = reasonText == null ? "" : reasonText.trim();
        if (reason.isEmpty() || reason.length() > 500) throw bad("Vui lòng nhập lý do điều chỉnh (tối đa 500 ký tự).");
        BigDecimal amount = parseAmount(amountSigned);
        StoredFile[] stored = new StoredFile[1];
        try {
            UUID adjustmentId = mutationAudit.record("ADJUST_FINANCIAL_TRANSACTION", "FINANCIAL_TRANSACTION", null, () -> {
                Map<String, Object> entry = requireAdjustableEntry(transactionId, amount);
                LocalDate today = LocalDate.now(FinancialPeriodService.ZONE);
                List<UUID> openPeriods = financialReportRepository.lockOpenPeriod(today.withDayOfMonth(1));
                if (openPeriods.isEmpty()) throw conflict("Kỳ hiện tại đã khóa. Không thể ghi điều chỉnh vào kỳ đã chốt.");
                UUID newId = UUID.randomUUID();
                financialReportRepository.insertAdjustment(newId, openPeriods.get(0), today, transactionId, reason, actor.id());
                financialReportRepository.insertEntry(newId, entry.get("account_id"), entry.get("branch_id"), amount);
                if (evidence != null && !evidence.isEmpty()) {
                    stored[0] = fileStorageService.store(evidence, "thu-chi");
                    financialReportRepository.insertAdjustmentEvidence(stored[0], newId, actor.id());
                }
                return newId;
            }, value -> value);
            return financialReportRepository.findAdjustment(adjustmentId);
        } catch (RuntimeException failure) {
            // File đã lưu nhưng giao dịch không commit thì phải xóa để không còn chứng từ mồ côi.
            if (stored[0] != null) fileStorageService.deleteQuietly(stored[0].objectKey());
            throw failure;
        }
    }

    // Chỉ điều chỉnh giao dịch gốc có đúng một dòng sổ tiền; tổng sau điều chỉnh không được đổi chiều thu/chi.
    private Map<String, Object> requireAdjustableEntry(UUID transactionId, BigDecimal amount) {
        List<Map<String, Object>> locked = financialReportRepository.lockTransactionForAdjustment(transactionId);
        if (locked.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch cần điều chỉnh.");
        Map<String, Object> original = locked.get(0);
        if ("ADJUSTMENT".equals(original.get("kind")) || original.get("adjusts_transaction_id") != null)
            throw bad("Chỉ được điều chỉnh giao dịch gốc, không thể điều chỉnh một bút toán điều chỉnh.");
        List<Map<String, Object>> entries = financialReportRepository.findEntries(transactionId);
        if (List.of("TRANSFER", "OPENING").contains(original.get("kind")) || entries.size() != 1)
            throw bad("Chỉ có thể điều chỉnh giao dịch gốc có một dòng sổ tiền.");
        Map<String, Object> entry = entries.get(0);
        BigDecimal originalAmount = (BigDecimal) entry.get("amount_signed");
        BigDecimal adjustedAmount = originalAmount.add(financialReportRepository.sumAdjustments(transactionId)).add(amount);
        if ((originalAmount.signum() > 0 && adjustedAmount.signum() < 0)
                || (originalAmount.signum() < 0 && adjustedAmount.signum() > 0))
            throw conflict("Tổng điều chỉnh vượt giá trị giao dịch gốc; số tiền sau điều chỉnh không được đổi chiều thu/chi.");
        return entry;
    }

    private static BigDecimal parseAmount(Object amountSigned) {
        BigDecimal amount;
        try { amount = new BigDecimal(String.valueOf(amountSigned)); }
        catch (RuntimeException invalid) { throw bad("Số tiền điều chỉnh không hợp lệ."); }
        if (amount.signum() == 0 || amount.stripTrailingZeros().scale() > 0 || amount.abs().compareTo(MAX_AMOUNT) > 0)
            throw bad("Số tiền điều chỉnh phải là số nguyên VND khác 0.");
        return amount;
    }

    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
