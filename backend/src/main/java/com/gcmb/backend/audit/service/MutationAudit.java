/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Ghi nhật ký thao tác thay đổi dữ liệu, người thực hiện và dữ liệu trước/sau trong cùng giao dịch nghiệp vụ.
 */
package com.gcmb.backend.audit.service;

import com.gcmb.backend.audit.repository.MutationAuditRepository;
import com.gcmb.backend.auth.dto.Identity;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MutationAudit {
    private static final Map<String, String> TABLES = Map.ofEntries(
        Map.entry("ROOM", "room"),
        Map.entry("ROOM_TYPE", "room_type"),
        Map.entry("PRICE_RULE", "price_rule"),
        Map.entry("COMBO", "combo"),
        Map.entry("BOOKING", "booking"),
        Map.entry("BRANCH", "branch"),
        Map.entry("MENU_ITEM", "menu_item"),
        Map.entry("FB_ORDER", "fb_order"),
        Map.entry("INVENTORY_ITEM", "inventory_item"),
        Map.entry("ASSET_CATEGORY", "asset_category"),
        Map.entry("ASSET", "asset"),
        Map.entry("INCIDENT", "asset_incident"),
        Map.entry("TRANSFER", "asset_transfer"),
        Map.entry("FINANCIAL_TRANSACTION", "financial_transaction"),
        Map.entry("BANK_TRANSACTION", "bank_transaction"),
        Map.entry("FINANCIAL_PERIOD", "financial_period"),
        Map.entry("FINANCE_EXPENSE_CATEGORY", "finance_expense_category"),
        Map.entry("FINANCE_EXPENSE_REQUEST", "finance_expense_request"),
        Map.entry("FINANCE_RECURRING_EXPENSE", "finance_recurring_expense"),
        Map.entry("FINANCE_RECURRING_EXPENSE_PERIOD", "finance_recurring_expense_period"));

    private final MutationAuditRepository mutationAuditRepository;
    private final TransactionTemplate transactionTemplate;

    public MutationAudit(MutationAuditRepository mutationAuditRepository, PlatformTransactionManager manager) {
        this.mutationAuditRepository = mutationAuditRepository;
        this.transactionTemplate = new TransactionTemplate(manager);
    }

    // resultId xác định ID mới khi tạo đối tượng hoặc tạo phiên bản giá mới.
    public <T> T record(String action, String type, UUID id, Supplier<T> operation, Function<T, UUID> resultId) {
        return recordWithMetadata(action, type, id, operation, resultId, null);
    }

    // Audit-only metadata avoids changing the entity row or caller-supplied note.
    public <T> T recordWithMetadata(String action, String type, UUID id, Supplier<T> operation,
                                    Function<T, UUID> resultId, String additionalAfterDataJson) {
        if (!TABLES.containsKey(type)) throw new IllegalArgumentException("Unsupported audit entity");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Identity actor)) {
            throw new IllegalStateException("Business audit requires an authenticated actor");
        }
        return transactionTemplate.execute(status -> {
            String before = snapshot(type, id);
            T result = operation.get();
            UUID afterId = resultId == null ? id : resultId.apply(result);
            String after = snapshot(type, afterId);
            if (!java.util.Objects.equals(before, after)) {
                mutationAuditRepository.insertEvent(actor.id(), action, type, id == null ? afterId : id, before, after,
                    additionalAfterDataJson, actor.role());
            }
            return result;
        });
    }

    private String snapshot(String type, UUID id) {
        if (id == null) return null;
        return mutationAuditRepository.lockAndSnapshot(type, TABLES.get(type), id);
    }
}
