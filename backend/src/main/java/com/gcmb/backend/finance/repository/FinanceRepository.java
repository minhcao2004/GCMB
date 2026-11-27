package com.gcmb.backend.finance.repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Quỹ vận hành, danh mục chi phí, đề nghị chi/hoàn ứng, phân bổ và chứng từ (UC-102..120, UC-130..135).
public class FinanceRepository {

    private static final String CATEGORY_VIEW = "SELECT c.id,c.code,c.name,c.description,c.category_group,c.cost_type,c.cost_nature,"
        + "c.recognition_mode,c.default_allocation_months,c.invoice_required,c.reimbursement_allowed,c.active,c.updated_at,"
        + "(SELECT count(*) FROM gcmb.finance_expense_request r WHERE r.category_id=c.id) AS request_count,"
        + "(SELECT count(*) FROM gcmb.finance_recurring_expense re WHERE re.category_id=c.id) AS recurring_count,"
        + "COALESCE((SELECT sum(r.amount) FROM gcmb.finance_expense_request r WHERE r.category_id=c.id AND r.status='PAID' "
        + "AND r.paid_at>=CURRENT_TIMESTAMP-INTERVAL '12 months'),0) AS total_paid_12_months FROM gcmb.finance_expense_category c ";

    private final JdbcTemplate jdbcTemplate;

    public FinanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---- Quỹ kế toán và giao dịch cấp quỹ ----

    public void lockAccountantFund() {
        AccountantFund.lock(jdbcTemplate);
    }

    public BigDecimal accountantFundBalance() {
        return AccountantFund.balance(jdbcTemplate);
    }

    public List<Map<String, Object>> findBranches() {
        return jdbcTemplate.query("SELECT id,name,status FROM gcmb.branch ORDER BY name", (resultSet, rowNumber) -> toMap(resultSet));
    }

    public boolean branchExists(UUID branchId) {
        return Boolean.TRUE.equals(jdbcTemplate.query("SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE id=?)",
            (resultSet, rowNumber) -> resultSet.getBoolean(1), branchId).stream().findFirst().orElse(false));
    }

    public List<Map<String, Object>> findFundTransfers() {
        return jdbcTemplate.query("""
            SELECT f.id,f.code,f.amount,f.sent_on,f.transfer_reference,f.sent_at,f.status,
                   f.received_at,f.sent_by,a.username AS sent_by_name,ra.username AS received_by_name,
                   (SELECT x.id FROM gcmb.finance_attachment x WHERE x.owner_type='FUND_TRANSFER' AND x.owner_id=f.id ORDER BY x.created_at LIMIT 1) AS attachment_id,
                   (SELECT x.original_name FROM gcmb.finance_attachment x WHERE x.owner_type='FUND_TRANSFER' AND x.owner_id=f.id ORDER BY x.created_at LIMIT 1) AS attachment_name,
                   (SELECT x.mime_type FROM gcmb.finance_attachment x WHERE x.owner_type='FUND_TRANSFER' AND x.owner_id=f.id ORDER BY x.created_at LIMIT 1) AS attachment_mime,
                   EXISTS(SELECT 1 FROM gcmb.finance_attachment x WHERE x.owner_type='FUND_TRANSFER' AND x.owner_id=f.id) AS has_proof
            FROM gcmb.finance_fund_transfer f JOIN gcmb.user_account a ON a.id=f.sent_by
            LEFT JOIN gcmb.user_account ra ON ra.id=f.received_by ORDER BY f.sent_at DESC
            """, (resultSet, rowNumber) -> toMap(resultSet));
    }

    public Map<String, Object> getFundTransfer(UUID transferId) {
        return jdbcTemplate.queryForMap("SELECT id,code,amount,sent_on,transfer_reference,sent_at,status,received_at,sent_by,received_by FROM gcmb.finance_fund_transfer WHERE id=?", transferId);
    }

    public Optional<Map<String, Object>> findFundTransferOwnership(UUID transferId) {
        return jdbcTemplate.query("SELECT sent_by,status,sent_on FROM gcmb.finance_fund_transfer WHERE id=?",
            (resultSet, rowNumber) -> toMap(resultSet), transferId).stream().findFirst();
    }

    public List<Map<String, Object>> findFundTransferSentOn(UUID transferId) {
        return jdbcTemplate.query("SELECT sent_on FROM gcmb.finance_fund_transfer WHERE id=?", (resultSet, rowNumber) -> toMap(resultSet), transferId);
    }

    public void insertFundTransfer(UUID transferId, String code, BigDecimal amount, LocalDate sentOn, String reference, UUID sentBy) {
        jdbcTemplate.update("INSERT INTO gcmb.finance_fund_transfer(id,code,amount,sent_on,transfer_reference,sent_by,status) VALUES(?,?,?,?,?,?,'WAITING_CONFIRMATION')",
            transferId, code, amount, sentOn, reference, sentBy);
    }

    public boolean fundTransferHasProof(UUID transferId) {
        return !jdbcTemplate.queryForList("SELECT id FROM gcmb.finance_attachment WHERE owner_type='FUND_TRANSFER' AND owner_id=?", UUID.class, transferId).isEmpty();
    }

    public int confirmFundTransfer(UUID transferId, UUID receivedBy) {
        return jdbcTemplate.update("UPDATE gcmb.finance_fund_transfer SET status='RECEIVED',received_by=?,received_at=CURRENT_TIMESTAMP WHERE id=? AND status='WAITING_CONFIRMATION'",
            receivedBy, transferId);
    }

    // ---- Danh mục chi phí ----

    public List<Map<String, Object>> findCategories(boolean activeOnly) {
        return jdbcTemplate.query(CATEGORY_VIEW + (activeOnly ? "WHERE c.active=true " : "") + "ORDER BY c.active DESC,c.name",
            (resultSet, rowNumber) -> toMap(resultSet));
    }

    public Map<String, Object> getCategory(UUID categoryId) {
        return jdbcTemplate.queryForMap(CATEGORY_VIEW + "WHERE c.id=?", categoryId);
    }

    public void insertCategory(UUID categoryId, String code, String name, String description, String categoryGroup, String costType,
                               String costNature, String recognitionMode, Integer defaultAllocationMonths, boolean invoiceRequired,
                               boolean reimbursementAllowed, UUID createdBy) {
        jdbcTemplate.update("INSERT INTO gcmb.finance_expense_category(id,code,name,description,category_group,cost_type,cost_nature,recognition_mode,default_allocation_months,invoice_required,reimbursement_allowed,created_by) "
            + "VALUES(?,?,?,?,?,?,?,?,?::integer,?,?,?)", categoryId, code, name, description, categoryGroup, costType,
            costNature, recognitionMode, defaultAllocationMonths, invoiceRequired, reimbursementAllowed, createdBy);
    }

    public int updateCategory(UUID categoryId, String name, String description, String categoryGroup, String costType, String costNature,
                              String recognitionMode, Integer defaultAllocationMonths, boolean invoiceRequired, boolean reimbursementAllowed) {
        return jdbcTemplate.update("UPDATE gcmb.finance_expense_category SET name=?,description=?,category_group=?,cost_type=?,cost_nature=?,recognition_mode=?,"
            + "default_allocation_months=?::integer,invoice_required=?,reimbursement_allowed=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            name, description, categoryGroup, costType, costNature, recognitionMode, defaultAllocationMonths, invoiceRequired, reimbursementAllowed, categoryId);
    }

    public int setCategoryActive(UUID categoryId, boolean active) {
        return jdbcTemplate.update("UPDATE gcmb.finance_expense_category SET active=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND active<>?", active, categoryId, active);
    }

    public boolean categoryExists(UUID categoryId) {
        return !jdbcTemplate.queryForList("SELECT id FROM gcmb.finance_expense_category WHERE id=?", UUID.class, categoryId).isEmpty();
    }

    public boolean lockCategory(UUID categoryId) {
        return !jdbcTemplate.queryForList("SELECT id FROM gcmb.finance_expense_category WHERE id=? FOR UPDATE", UUID.class, categoryId).isEmpty();
    }

    public boolean categoryIsReferenced(UUID categoryId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM gcmb.finance_expense_request WHERE category_id=?", Long.class, categoryId) > 0
            || jdbcTemplate.queryForObject("SELECT count(*) FROM gcmb.finance_recurring_expense WHERE category_id=?", Long.class, categoryId) > 0;
    }

    public void deleteCategory(UUID categoryId) {
        jdbcTemplate.update("DELETE FROM gcmb.finance_expense_category WHERE id=?", categoryId);
    }

    public List<Map<String, Object>> lockCategoryForUse(UUID categoryId) {
        return jdbcTemplate.queryForList("SELECT id,name,active,cost_type,recognition_mode,default_allocation_months,invoice_required,reimbursement_allowed "
            + "FROM gcmb.finance_expense_category WHERE id=? FOR SHARE", categoryId);
    }

    // ---- Phiếu nhập kho dùng cho hoàn ứng (BR-33, BR-94, BR-197) ----

    public List<Map<String, Object>> findUnclaimedStockReceipts(UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT sr.id,sr.code,sr.supplier_name,sr.received_at,cost.amount
            FROM gcmb.stock_receipt sr
            JOIN LATERAL (
                SELECT COALESCE(SUM(round(rl.quantity * rl.unit_cost, 0)),0) AS amount
                FROM gcmb.stock_receipt_line rl WHERE rl.receipt_id=sr.id
            ) cost ON cost.amount>0
            WHERE sr.branch_id=? AND sr.status='POSTED'
              AND NOT EXISTS (
                  SELECT 1 FROM gcmb.finance_request_stock_receipt linked
                  WHERE linked.receipt_id=sr.id AND linked.active=true
              )
            ORDER BY sr.received_at DESC,sr.code DESC
            """, branchId);
    }

    public List<Map<String, Object>> lockStockReceiptWithAmount(UUID receiptId, UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT sr.id,sr.status,
              (SELECT COALESCE(SUM(round(rl.quantity * rl.unit_cost, 0)),0)
               FROM gcmb.stock_receipt_line rl WHERE rl.receipt_id=sr.id) AS amount
            FROM gcmb.stock_receipt sr WHERE sr.id=? AND sr.branch_id=? FOR UPDATE OF sr
            """, receiptId, branchId);
    }

    public List<String> findActiveClaimStatuses(UUID receiptId) {
        return jdbcTemplate.queryForList("""
            SELECT request.status FROM gcmb.finance_request_stock_receipt linked
            JOIN gcmb.finance_expense_request request ON request.id=linked.request_id
            WHERE linked.receipt_id=? AND linked.active=true
            """, String.class, receiptId);
    }

    public void linkStockReceipt(UUID requestId, UUID receiptId, BigDecimal amount) {
        jdbcTemplate.update("INSERT INTO gcmb.finance_request_stock_receipt(request_id,receipt_id,amount) VALUES(?,?,?)", requestId, receiptId, amount);
    }

    public void releaseStockReceipts(UUID requestId) {
        jdbcTemplate.update("UPDATE gcmb.finance_request_stock_receipt SET active=false WHERE request_id=?", requestId);
    }

    public List<Map<String, Object>> findLinkedStockReceipts(UUID requestId) {
        return jdbcTemplate.queryForList("""
            SELECT sr.code,sr.supplier_name,linked.amount
            FROM gcmb.finance_request_stock_receipt linked
            JOIN gcmb.stock_receipt sr ON sr.id=linked.receipt_id
            WHERE linked.request_id=? ORDER BY sr.received_at,sr.code
            """, requestId);
    }

    // ---- Đề nghị chi / hoàn ứng ----

    public void insertBranchRequest(UUID requestId, String code, String kind, UUID branchId, UUID categoryId, String categoryType,
                                    String recognitionMode, Integer allocationMonths, BigDecimal amount, LocalDate expenseOn,
                                    String description, String beneficiaryName, UUID requestedBy, String status) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.finance_expense_request(id,code,kind,branch_id,category_id,category_type,recognition_mode,allocation_months,
              origin,amount,expense_on,description,beneficiary_name,bank_account,bank_name,payment_method,requested_by,status)
            VALUES(?,?,?,?,?,?,?,?::integer,'BRANCH_MANAGER',?,?,?,?,?,?,?,?,?)
            """, requestId, code, kind, branchId, categoryId, categoryType, recognitionMode, allocationMonths, amount, expenseOn,
            description, beneficiaryName, "", "", requestedBy, status);
    }

    public void insertDirectExpense(UUID requestId, String code, UUID branchId, UUID categoryId, Object costType, String recognitionMode,
                                    Integer allocationMonths, UUID idempotencyKey, BigDecimal amount, LocalDate expenseOn,
                                    String description, String beneficiaryName, String bankAccount, String bankName,
                                    String paymentMethod, UUID requestedBy, String status, boolean waitingForHeadOffice) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.finance_expense_request(id,code,kind,branch_id,category_id,category_type,recognition_mode,allocation_months,
              origin,idempotency_key,amount,expense_on,description,beneficiary_name,bank_account,bank_name,payment_method,requested_by,status,
              accountant_reviewed_by,accountant_reviewed_at)
            VALUES(?,?,'PAYMENT',?,?,?,?,?::integer,'ACCOUNTANT_DIRECT',?,?,?,?,?,?,?,?,?,?,
              CASE WHEN ? THEN NULL ELSE ? END,CASE WHEN ? THEN NULL ELSE CURRENT_TIMESTAMP END)
            """, requestId, code, branchId, categoryId, costType, recognitionMode, allocationMonths, idempotencyKey, amount, expenseOn,
            description, beneficiaryName, bankAccount, bankName, paymentMethod, requestedBy, status,
            waitingForHeadOffice, requestedBy, waitingForHeadOffice);
    }

    public void insertRecurringExpenseRequest(UUID requestId, String code, UUID branchId, UUID categoryId, Object costType,
                                              UUID idempotencyKey, BigDecimal amount, LocalDate expenseOn, String description,
                                              String beneficiaryName, String bankAccount, String bankName, UUID actorId) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.finance_expense_request(id,code,kind,branch_id,category_id,category_type,recognition_mode,
              origin,idempotency_key,amount,expense_on,description,beneficiary_name,bank_account,bank_name,requested_by,status,
              accountant_reviewed_by,accountant_reviewed_at)
            VALUES(?,?,'PAYMENT',?,?,?,'IMMEDIATE','ACCOUNTANT_DIRECT',?,?,?,?,?,?,?,?,'APPROVED_TO_PAY',?,CURRENT_TIMESTAMP)
            """, requestId, code, branchId, categoryId, costType, idempotencyKey, amount, expenseOn,
            description, beneficiaryName, bankAccount, bankName, actorId, actorId);
    }

    public void lockDirectExpenseKey(UUID idempotencyKey) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(1300261104, hashtext(?))",
            (ResultSet resultSet) -> { resultSet.next(); resultSet.getObject(1); return null; }, idempotencyKey.toString());
    }

    public List<Map<String, Object>> findDirectExpenseByKey(UUID idempotencyKey) {
        return jdbcTemplate.queryForList("SELECT id,branch_id,category_id,recognition_mode,allocation_months,allocation_start,amount,expense_on,description,beneficiary_name,bank_account,bank_name,payment_method,paid_reference "
            + "FROM gcmb.finance_expense_request WHERE origin='ACCOUNTANT_DIRECT' AND idempotency_key=?", idempotencyKey);
    }

    public void reviewAsHeadOffice(UUID requestId, String status, UUID reviewerId) {
        jdbcTemplate.update("UPDATE gcmb.finance_expense_request SET status=?,head_office_reviewed_by=?,head_office_reviewed_at=CURRENT_TIMESTAMP WHERE id=?",
            status, reviewerId, requestId);
    }

    public void reviewAsAccountant(UUID requestId, String status, UUID reviewerId) {
        jdbcTemplate.update("UPDATE gcmb.finance_expense_request SET status=?,accountant_reviewed_by=?,accountant_reviewed_at=CURRENT_TIMESTAMP WHERE id=?",
            status, reviewerId, requestId);
    }

    public int approveWaitingAccountantRequest(UUID requestId, UUID reviewerId) {
        return jdbcTemplate.update("UPDATE gcmb.finance_expense_request SET status='APPROVED_TO_PAY',accountant_reviewed_by=?,accountant_reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND status='WAITING_ACCOUNTANT'",
            reviewerId, requestId);
    }

    public int markRequestPaid(UUID requestId, UUID paidBy, String reference, String paymentMethod, String bankAccount, String bankName,
                               Integer allocationMonths, LocalDate allocationStart) {
        return jdbcTemplate.update("UPDATE gcmb.finance_expense_request SET status='PAID',paid_by=?,paid_at=CURRENT_TIMESTAMP,paid_reference=?,"
            + "paid_payment_method=?,bank_account=?,bank_name=?,allocation_months=?::integer,allocation_start=?::date WHERE id=? AND status='APPROVED_TO_PAY'",
            paidBy, reference, paymentMethod, bankAccount, bankName, allocationMonths, allocationStart, requestId);
    }

    public void insertAllocation(UUID requestId, Object branchId, Object categoryId, LocalDate periodMonth, int sequence, long amount) {
        jdbcTemplate.update("INSERT INTO gcmb.finance_expense_allocation(request_id,branch_id,category_id,period_month,seq,amount) VALUES(?,?,?,?,?,?)",
            requestId, branchId, categoryId, periodMonth, sequence, amount);
    }

    public void insertHistory(UUID requestId, UUID actorId, String action, String note) {
        jdbcTemplate.update("INSERT INTO gcmb.finance_request_history(request_id,actor_id,action,note) VALUES(?,?,?,?)", requestId, actorId, action, note);
    }

    public Optional<Map<String, Object>> findRequest(UUID requestId) {
        return jdbcTemplate.queryForList("""
            SELECT r.id,r.code,r.kind,r.branch_id,b.name AS branch_name,r.category_id,c.name AS category_name,r.category_type,
              r.recognition_mode,r.allocation_months,r.allocation_start,r.origin,c.invoice_required,
              r.amount,r.expense_on,r.description,r.beneficiary_name,r.bank_account,r.bank_name,r.payment_method,r.paid_payment_method,r.status,
              r.requested_by,req.username AS requested_by_name,r.requested_at,r.accountant_reviewed_by,
              r.accountant_reviewed_at,r.head_office_reviewed_by,r.head_office_reviewed_at,r.paid_by,
              r.paid_at,r.paid_reference
            FROM gcmb.finance_expense_request r JOIN gcmb.branch b ON b.id=r.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=r.category_id JOIN gcmb.user_account req ON req.id=r.requested_by WHERE r.id=?
            """, requestId).stream().findFirst();
    }

    public List<Map<String, Object>> findRequests(UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT r.id,r.code,r.kind,r.branch_id,b.name AS branch_name,r.category_id,c.name AS category_name,r.category_type,
              r.recognition_mode,r.allocation_months,r.allocation_start,r.origin,c.invoice_required,
              r.amount,r.expense_on,r.description,r.beneficiary_name,r.bank_account,r.bank_name,r.payment_method,r.paid_payment_method,r.status,
              r.requested_by,req.username AS requested_by_name,r.requested_at,r.paid_at,r.paid_reference
            FROM gcmb.finance_expense_request r JOIN gcmb.branch b ON b.id=r.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=r.category_id JOIN gcmb.user_account req ON req.id=r.requested_by
            WHERE (?::uuid IS NULL OR r.branch_id=?) ORDER BY r.requested_at DESC
            """, branchId, branchId);
    }

    public List<Map<String, Object>> findHistory(UUID requestId) {
        return jdbcTemplate.query("SELECT h.id,h.action,h.note,h.created_at,a.username AS actor_name FROM gcmb.finance_request_history h JOIN gcmb.user_account a ON a.id=h.actor_id WHERE h.request_id=? ORDER BY h.created_at",
            (resultSet, rowNumber) -> toMap(resultSet), requestId);
    }

    public List<Map<String, Object>> findRequestAttachments(UUID requestId) {
        return jdbcTemplate.query("SELECT id,kind,original_name,mime_type,byte_size FROM gcmb.finance_attachment WHERE owner_type='EXPENSE_REQUEST' AND owner_id=? ORDER BY created_at",
            (resultSet, rowNumber) -> toMap(resultSet), requestId);
    }

    public List<Map<String, Object>> findAllocations(UUID requestId) {
        return jdbcTemplate.query("SELECT id,period_month,seq,amount,status FROM gcmb.finance_expense_allocation WHERE request_id=? ORDER BY seq",
            (resultSet, rowNumber) -> toMap(resultSet), requestId);
    }

    // Nạp chi tiết của nhiều đề nghị bằng một câu mỗi loại, tránh N+1 khi liệt kê danh sách.
    public Map<UUID, List<Map<String, Object>>> findHistoryByRequests(List<UUID> requestIds) {
        return queryGrouped("SELECT h.request_id AS group_id,h.id,h.action,h.note,h.created_at,a.username AS actor_name FROM gcmb.finance_request_history h JOIN gcmb.user_account a ON a.id=h.actor_id WHERE h.request_id=ANY(?) ORDER BY h.request_id,h.created_at", requestIds);
    }

    public Map<UUID, List<Map<String, Object>>> findRequestAttachmentsByRequests(List<UUID> requestIds) {
        return queryGrouped("SELECT owner_id AS group_id,id,kind,original_name,mime_type,byte_size FROM gcmb.finance_attachment WHERE owner_type='EXPENSE_REQUEST' AND owner_id=ANY(?) ORDER BY owner_id,created_at", requestIds);
    }

    public Map<UUID, List<Map<String, Object>>> findAllocationsByRequests(List<UUID> requestIds) {
        return queryGrouped("SELECT request_id AS group_id,id,period_month,seq,amount,status FROM gcmb.finance_expense_allocation WHERE request_id=ANY(?) ORDER BY request_id,seq", requestIds);
    }

    public Map<UUID, List<Map<String, Object>>> findLinkedStockReceiptsByRequests(List<UUID> requestIds) {
        return queryGrouped("""
            SELECT linked.request_id AS group_id,sr.code,sr.supplier_name,linked.amount
            FROM gcmb.finance_request_stock_receipt linked
            JOIN gcmb.stock_receipt sr ON sr.id=linked.receipt_id
            WHERE linked.request_id=ANY(?) ORDER BY linked.request_id,sr.received_at,sr.code
            """, requestIds);
    }

    private Map<UUID, List<Map<String, Object>>> queryGrouped(String sql, List<UUID> ids) {
        Map<UUID, List<Map<String, Object>>> grouped = new HashMap<>();
        if (ids.isEmpty()) return grouped;
        jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setArray(1, connection.createArrayOf("uuid", ids.toArray()));
            return statement;
        }, (ResultSet resultSet) -> {
            Map<String, Object> row = toMap(resultSet);
            UUID groupId = (UUID) row.remove("group_id");
            grouped.computeIfAbsent(groupId, key -> new ArrayList<>()).add(row);
        });
        return grouped;
    }

    // ---- Chứng từ ----

    public Optional<Map<String, Object>> findAttachmentWithBranch(UUID attachmentId) {
        return jdbcTemplate.query("""
            SELECT x.*,COALESCE(r.branch_id,t.branch_id) branch_id
            FROM gcmb.finance_attachment x
            LEFT JOIN gcmb.finance_expense_request r ON x.owner_type='EXPENSE_REQUEST' AND r.id=x.owner_id
            LEFT JOIN LATERAL (SELECT e.branch_id FROM gcmb.financial_entry e
              WHERE x.owner_type='FINANCIAL_TRANSACTION' AND e.transaction_id=x.owner_id ORDER BY e.id LIMIT 1) t ON true
            WHERE x.id=?
            """, (resultSet, rowNumber) -> toMap(resultSet), attachmentId).stream().findFirst();
    }

    public void insertAttachment(UUID attachmentId, String ownerType, UUID ownerId, String kind, String objectKey, String originalName,
                                 String mimeType, long byteSize, String sha256, UUID createdBy) {
        jdbcTemplate.update("INSERT INTO gcmb.finance_attachment(id,owner_type,owner_id,kind,object_key,original_name,mime_type,byte_size,sha256,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
            attachmentId, ownerType, ownerId, kind, objectKey, originalName, mimeType, byteSize, sha256, createdBy);
    }

    private static Map<String, Object> toMap(ResultSet resultSet) throws SQLException {
        var metadata = resultSet.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int column = 1; column <= metadata.getColumnCount(); column++) row.put(metadata.getColumnLabel(column), resultSet.getObject(column));
        return row;
    }
}
