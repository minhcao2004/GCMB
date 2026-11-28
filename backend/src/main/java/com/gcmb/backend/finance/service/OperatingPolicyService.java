package com.gcmb.backend.finance.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.finance.repository.OperatingPolicyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.gcmb.backend.finance.service.FinanceAccess.bad;
import static com.gcmb.backend.finance.service.FinanceAccess.conflict;

@Service
@Transactional
public class OperatingPolicyService {
    public static final String NIGHT_SHIFT_PREMIUM = "NIGHT_SHIFT_PREMIUM";
    public static final String PAYMENT_APPROVAL_LIMIT = "PAYMENT_APPROVAL_LIMIT";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final OperatingPolicyRepository repository;
    private final FinanceAccess access;

    public OperatingPolicyService(OperatingPolicyRepository repository, FinanceAccess access) {
        this.repository = repository;
        this.access = access;
    }

    public BigDecimal amountAt(String key, LocalDate date) { return repository.amountAt(key, date); }

    public BigDecimal paymentLimitForNewRequest() {
        repository.lock(PAYMENT_APPROVAL_LIMIT);
        return repository.amountAt(PAYMENT_APPROVAL_LIMIT, LocalDate.now(ZONE));
    }

    public List<Map<String,Object>> history(Identity actor) {
        access.role(actor, "HEAD_OFFICE_MANAGER");
        return repository.history();
    }

    public List<Map<String,Object>> update(Identity actor, String key, BigDecimal amount,
                                            LocalDate effectiveFrom, String reason, UUID expectedLatestId) {
        access.role(actor, "HEAD_OFFICE_MANAGER");
        if (!Set.of(NIGHT_SHIFT_PREMIUM, PAYMENT_APPROVAL_LIMIT).contains(key)) throw bad("Chính sách không hợp lệ.");
        if (amount == null || amount.signum() < 0 || amount.scale() > 0
                || amount.compareTo(new BigDecimal("999999999999")) > 0)
            throw bad("Số tiền phải là số nguyên từ 0 đến 999.999.999.999đ.");
        LocalDate today = LocalDate.now(ZONE);
        if (effectiveFrom == null || effectiveFrom.isBefore(today)
                || (NIGHT_SHIFT_PREMIUM.equals(key) && !effectiveFrom.isAfter(today)))
            throw bad(NIGHT_SHIFT_PREMIUM.equals(key)
                ? "Phụ cấp ca đêm chỉ được áp dụng từ ngày mai trở đi."
                : "Ngày hiệu lực không được ở quá khứ.");
        if (reason == null || reason.isBlank() || reason.trim().length() > 500)
            throw bad("Vui lòng nhập lý do thay đổi, tối đa 500 ký tự.");
        repository.lock(key);
        if (expectedLatestId == null || !expectedLatestId.equals(repository.latestId(key)))
            throw conflict("Chính sách đã thay đổi. Vui lòng tải lại trước khi lưu.");
        repository.insert(key, amount, effectiveFrom, actor.id(), reason.trim());
        return repository.history();
    }
}
