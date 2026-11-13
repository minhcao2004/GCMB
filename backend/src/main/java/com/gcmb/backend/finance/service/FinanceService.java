package com.gcmb.backend.finance.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService.StoredFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Quỹ vận hành chung và sổ chi phí theo từng cơ sở. */
@Service
@Transactional
public class FinanceService {
    private static final BigDecimal HEAD_OFFICE_APPROVAL_LIMIT = new BigDecimal("3000000");
    // Chi trực tiếp mặc định không chờ Quản lý cấp cao; đổi ngưỡng này nếu nghiệp vụ yêu cầu duyệt.
    private static final BigDecimal DIRECT_EXPENSE_HEAD_OFFICE_APPROVAL_LIMIT = BigDecimal.valueOf(Long.MAX_VALUE);
    private final JdbcTemplate db;
    private final EquipmentAssetFileStorageService files;
    private final FinancialPeriodService periods;

    public FinanceService(JdbcTemplate db, EquipmentAssetFileStorageService files, FinancialPeriodService periods) {
        this.db = db;
        this.files = files;
        this.periods = periods;
    }

    public Map<String,Object> summary(Identity who) {
        requireRole(who, "ACCOUNTANT");
        return Map.of("balance", balance(), "fundTransfers", fundTransfers(), "categories", categories(false),
            "requests", requests(null), "branches", branches());
    }

    public Map<String,Object> headOfficeSummary(Identity who) {
        requireRole(who, "HEAD_OFFICE_MANAGER");
        return Map.of("fundTransfers", fundTransfers(), "requests", requests(null),
            "categories", categories(false), "branches", branches());
    }

    public Map<String,Object> branchSummary(Identity who) {
        UUID branch = branch(who);
        return Map.of("categories", categories(true), "requests", requests(branch));
    }

    private List<Map<String,Object>> branches() {
        return db.query("SELECT id,name,status FROM gcmb.branch ORDER BY name", (r,n)->row(r));
    }

    public List<Map<String,Object>> fundTransfers() {
        return db.query("""
            SELECT f.id,f.code,f.amount,f.sent_on,f.transfer_reference,f.sent_at,f.status,
                   f.received_at,f.sent_by,a.username AS sent_by_name,ra.username AS received_by_name,
                   (SELECT x.id FROM gcmb.finance_attachment x WHERE x.owner_type='FUND_TRANSFER' AND x.owner_id=f.id ORDER BY x.created_at LIMIT 1) AS attachment_id,
                   (SELECT x.original_name FROM gcmb.finance_attachment x WHERE x.owner_type='FUND_TRANSFER' AND x.owner_id=f.id ORDER BY x.created_at LIMIT 1) AS attachment_name,
                   (SELECT x.mime_type FROM gcmb.finance_attachment x WHERE x.owner_type='FUND_TRANSFER' AND x.owner_id=f.id ORDER BY x.created_at LIMIT 1) AS attachment_mime,
                   EXISTS(SELECT 1 FROM gcmb.finance_attachment x WHERE x.owner_type='FUND_TRANSFER' AND x.owner_id=f.id) AS has_proof
            FROM gcmb.finance_fund_transfer f JOIN gcmb.user_account a ON a.id=f.sent_by
            LEFT JOIN gcmb.user_account ra ON ra.id=f.received_by ORDER BY f.sent_at DESC
            """, (r,n)->row(r));
    }

    @Transactional
    public Map<String,Object> createFundTransfer(Identity who, BigDecimal amount, LocalDate sentOn,
            String reference) {
        requireRole(who,"HEAD_OFFICE_MANAGER");
        if (amount==null||amount.signum()<=0) bad("Số tiền chuyển phải lớn hơn 0.");
        if (sentOn==null) bad("Vui lòng chọn ngày gửi tiền.");
        periods.requireOpen(sentOn, "tạo giao dịch cấp quỹ");
        UUID id=UUID.randomUUID(); String code=code("FUND");
        db.update("INSERT INTO gcmb.finance_fund_transfer(id,code,amount,sent_on,transfer_reference,sent_by,status) VALUES(?,?,?,?,?,?,'WAITING_CONFIRMATION')",
            id,code,amount,sentOn,clean(reference,200),who.id());
        return fundTransfer(id);
    }

    @Transactional
    public Map<String,Object> addFundTransferProof(Identity who, UUID id, MultipartFile proof) {
        requireRole(who,"HEAD_OFFICE_MANAGER");
        Map<String,Object> transfer=db.query("SELECT sent_by,status,sent_on FROM gcmb.finance_fund_transfer WHERE id=?",(r,n)->row(r),id)
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy khoản chuyển quỹ."));
        periods.requireOpen(day(transfer.get("sent_on")), "bổ sung chứng từ giao dịch cấp quỹ");
        if(!who.id().equals(transfer.get("sent_by"))) forbidden("Chỉ quản lý cấp cao đã tạo khoản chuyển mới được bổ sung chứng từ.");
        if(!"WAITING_CONFIRMATION".equals(transfer.get("status"))) conflict("Chỉ bổ sung chứng từ khi kế toán chưa xác nhận khoản chuyển.");
        if(!db.queryForList("SELECT id FROM gcmb.finance_attachment WHERE owner_type='FUND_TRANSFER' AND owner_id=?",UUID.class,id).isEmpty()) conflict("Khoản chuyển đã có chứng từ.");
        attach("FUND_TRANSFER",id,"TRANSFER_PROOF",proof,who.id());
        return fundTransfers().stream().filter(x->id.equals(x.get("id"))).findFirst().orElseThrow();
    }

    @Transactional
    public Map<String,Object> confirmFundTransfer(Identity who, UUID id) {
        requireRole(who,"ACCOUNTANT");
        List<Map<String,Object>> transfers=db.query("SELECT sent_on FROM gcmb.finance_fund_transfer WHERE id=?",(r,n)->row(r),id);
        if(transfers.isEmpty()) conflict("Khoản chuyển không còn chờ xác nhận hoặc không tồn tại.");
        periods.requireOpen(day(transfers.get(0).get("sent_on")), "xác nhận giao dịch cấp quỹ");
        AccountantFund.lock(db);
        int changed=db.update("UPDATE gcmb.finance_fund_transfer SET status='RECEIVED',received_by=?,received_at=CURRENT_TIMESTAMP WHERE id=? AND status='WAITING_CONFIRMATION'",who.id(),id);
        if(changed==0) conflict("Khoản chuyển không còn chờ xác nhận hoặc không tồn tại.");
        return fundTransfer(id);
    }

    public List<Map<String,Object>> categories(boolean activeOnly) {
        return db.query("SELECT c.id,c.code,c.name,c.description,c.category_group,c.cost_type,c.cost_nature,"+
            "c.recognition_mode,c.default_allocation_months,c.invoice_required,c.reimbursement_allowed,c.active,c.updated_at,"+
            "(SELECT count(*) FROM gcmb.finance_expense_request r WHERE r.category_id=c.id) AS request_count,"+
            "(SELECT count(*) FROM gcmb.finance_recurring_expense re WHERE re.category_id=c.id) AS recurring_count,"+
            "COALESCE((SELECT sum(r.amount) FROM gcmb.finance_expense_request r WHERE r.category_id=c.id AND r.status='PAID' " +
            "AND r.paid_at>=CURRENT_TIMESTAMP-INTERVAL '12 months'),0) AS total_paid_12_months FROM gcmb.finance_expense_category c "+
            (activeOnly?"WHERE c.active=true ":"")+"ORDER BY c.active DESC,c.name",(r,n)->row(r));
    }

    public List<Map<String,Object>> accountantCategories(Identity who) {
        requireRole(who,"ACCOUNTANT");
        return categories(false);
    }

    public Map<String,Object> saveCategory(Identity who, UUID id, String name, String description, String categoryGroup,
            String costType, String costNature, String recognitionMode, Integer defaultAllocationMonths,
            boolean invoiceRequired, boolean reimbursementAllowed) {
        requireRole(who,"ACCOUNTANT");
        String cleanName=clean(name,120);
        if(cleanName.isBlank()) bad("Vui lòng nhập tên loại chi phí.");
        if(!"OPEX".equals(costType)&&!"CAPEX".equals(costType)) bad("Vui lòng chọn loại chi phí OPEX hoặc CAPEX.");
        if(!List.of("RENT","UTILITIES","INGREDIENTS","MAINTENANCE","MARKETING","EQUIPMENT","OTHER").contains(categoryGroup)) bad("Vui lòng chọn nhóm chi phí hợp lệ.");
        if(!List.of("FIXED","VARIABLE").contains(costNature)) bad("Vui lòng chọn tính chất chi phí hợp lệ.");
        if(!List.of("IMMEDIATE","ALLOCATED").contains(recognitionMode)) bad("Vui lòng chọn cách ghi nhận hợp lệ.");
        if("ALLOCATED".equals(recognitionMode) && (defaultAllocationMonths==null || defaultAllocationMonths<2 || defaultAllocationMonths>60))
            bad("Số tháng phân bổ mặc định phải từ 2 đến 60.");
        if("IMMEDIATE".equals(recognitionMode) && defaultAllocationMonths!=null)
            bad("Hạng mục ghi nhận ngay không được có số tháng phân bổ.");
        if(id==null) {
            UUID created=UUID.randomUUID();
            db.update("INSERT INTO gcmb.finance_expense_category(id,code,name,description,category_group,cost_type,cost_nature,recognition_mode,default_allocation_months,invoice_required,reimbursement_allowed,created_by) "+
                "VALUES(?,?,?,?,?,?,?,?,?::integer,?,?,?)",created,code("CAT"),cleanName,clean(description,500),categoryGroup,costType,
                costNature,recognitionMode,defaultAllocationMonths,invoiceRequired,reimbursementAllowed,who.id());
            id=created;
        } else {
            int count=db.update("UPDATE gcmb.finance_expense_category SET name=?,description=?,category_group=?,cost_type=?,cost_nature=?,recognition_mode=?,"+
                "default_allocation_months=?::integer,invoice_required=?,reimbursement_allowed=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                cleanName,clean(description,500),categoryGroup,costType,costNature,recognitionMode,defaultAllocationMonths,invoiceRequired,reimbursementAllowed,id);
            if(count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy loại chi phí.");
        }
        return category(id);
    }

    public Map<String,Object> setCategoryActive(Identity who, UUID id, boolean active) {
        requireRole(who,"ACCOUNTANT");
        int count=db.update("UPDATE gcmb.finance_expense_category SET active=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND active<>?",active,id,active);
        if(count==0 && db.queryForList("SELECT id FROM gcmb.finance_expense_category WHERE id=?",UUID.class,id).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hạng mục chi phí.");
        return category(id);
    }

    public void deleteCategory(Identity who, UUID id) {
        requireRole(who,"ACCOUNTANT");
        if(db.queryForList("SELECT id FROM gcmb.finance_expense_category WHERE id=? FOR UPDATE",UUID.class,id).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hạng mục chi phí.");
        // BR-118: không xóa hạng mục đã được lịch sử nghiệp vụ tham chiếu.
        if(db.queryForObject("SELECT count(*) FROM gcmb.finance_expense_request WHERE category_id=?",Long.class,id)>0 ||
                db.queryForObject("SELECT count(*) FROM gcmb.finance_recurring_expense WHERE category_id=?",Long.class,id)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.");
        db.update("DELETE FROM gcmb.finance_expense_category WHERE id=?",id);
    }

    private Map<String,Object> category(UUID id) {
        return db.queryForMap("SELECT c.id,c.code,c.name,c.description,c.category_group,c.cost_type,c.cost_nature,c.recognition_mode,"+
            "c.default_allocation_months,c.invoice_required,c.reimbursement_allowed,c.active,c.updated_at,"+
            "(SELECT count(*) FROM gcmb.finance_expense_request r WHERE r.category_id=c.id) AS request_count,"+
            "(SELECT count(*) FROM gcmb.finance_recurring_expense re WHERE re.category_id=c.id) AS recurring_count,"+
            "COALESCE((SELECT sum(r.amount) FROM gcmb.finance_expense_request r WHERE r.category_id=c.id AND r.status='PAID' " +
            "AND r.paid_at>=CURRENT_TIMESTAMP-INTERVAL '12 months'),0) AS total_paid_12_months FROM gcmb.finance_expense_category c WHERE c.id=?",id);
    }

    @Transactional
    public Map<String,Object> createRequest(Identity who, String kind, UUID categoryId, BigDecimal amount,
            LocalDate expenseOn, String description, String beneficiaryName, String bankAccount, String bankName) {
        UUID branch=branch(who);
        if(!"PAYMENT".equals(kind)&&!"REIMBURSEMENT".equals(kind)) bad("Loại đề nghị không hợp lệ.");
        if("REIMBURSEMENT".equals(kind)&&!"BRANCH_MANAGER".equals(who.role())) forbidden("Chỉ quản lý cơ sở được tạo đề nghị hoàn ứng.");
        if(amount==null||amount.signum()<=0) bad("Số tiền phải lớn hơn 0.");
        if(categoryId==null) bad("Vui lòng chọn loại chi phí đang hoạt động.");
        Map<String,Object> category=activeCategory(categoryId);
        if("REIMBURSEMENT".equals(kind)&&!Boolean.TRUE.equals(category.get("reimbursement_allowed")))
            bad("Hạng mục này không cho phép hoàn ứng.");
        String categoryType=(String)category.get("cost_type");
        String recognitionMode=(String)category.get("recognition_mode");
        Integer allocationMonths="ALLOCATED".equals(recognitionMode)?((Number)category.get("default_allocation_months")).intValue():null;
        if("ALLOCATED".equals(recognitionMode)) requireWholeVnd(amount);
        if(expenseOn==null) bad("Vui lòng nhập ngày chi.");
        periods.requireOpen(expenseOn, "tạo đề nghị chi hoặc hoàn ứng");
        if(clean(description,1000).isBlank()) bad("Vui lòng nhập nội dung chi.");
        if(clean(beneficiaryName,160).isBlank()||clean(bankAccount,80).isBlank()||clean(bankName,120).isBlank()) bad("Vui lòng nhập người nhận, số tài khoản và ngân hàng.");
        // BR-91: đề nghị của cơ sở trên 3.000.000đ cần Quản lý cấp cao duyệt.
        String status=amount.compareTo(HEAD_OFFICE_APPROVAL_LIMIT)>0?"WAITING_HEAD_OFFICE":"WAITING_ACCOUNTANT";
        UUID id=UUID.randomUUID();
        db.update("""
            INSERT INTO gcmb.finance_expense_request(id,code,kind,branch_id,category_id,category_type,recognition_mode,allocation_months,
              origin,amount,expense_on,description,beneficiary_name,bank_account,bank_name,requested_by,status)
            VALUES(?,?,?,?,?,?,?,?::integer,'BRANCH_MANAGER',?,?,?,?,?,?,?,?)
            """,id,code("EXP"),kind,branch,categoryId,categoryType,recognitionMode,allocationMonths,amount,expenseOn,clean(description,1000),clean(beneficiaryName,160),
            clean(bankAccount,80),clean(bankName,120),who.id(),status);
        db.update("INSERT INTO gcmb.finance_request_history(request_id,actor_id,action,note) VALUES(?,?,?,?)",id,who.id(),"SUBMITTED",
            status.equals("WAITING_HEAD_OFFICE")?"Gửi quản lý cấp cao duyệt do khoản chi trên 3.000.000đ":"Gửi kế toán duyệt");
        return request(id);
    }

    private Map<String,Object> activeCategory(UUID categoryId) {
        List<Map<String,Object>> rows=db.queryForList("SELECT id,name,active,cost_type,recognition_mode,default_allocation_months,invoice_required,reimbursement_allowed "+
            "FROM gcmb.finance_expense_category WHERE id=? FOR SHARE",categoryId);
        if(rows.isEmpty()) bad("Vui lòng chọn loại chi phí đang hoạt động.");
        Map<String,Object> category=rows.get(0);
        if(!Boolean.TRUE.equals(category.get("active")))
            bad("Danh mục chi phí "+category.get("name")+" đã ngừng sử dụng. Vui lòng chọn danh mục đang hoạt động."); // MSG103 / BR-209.
        return category;
    }

    private static void requireWholeVnd(BigDecimal amount) {
        try { amount.longValueExact(); }
        catch(ArithmeticException invalidAmount) { bad("Số tiền phân bổ phải là VND nguyên, không có phần thập phân."); }
    }

    @Transactional
    public Map<String,Object> addRequestAttachment(Identity who, UUID requestId, String kind, MultipartFile file) {
        UUID branch=branch(who);
        if(!List.of("INVOICE","TRANSFER_PROOF").contains(kind)) bad("Loại chứng từ không hợp lệ.");
        Map<String,Object> item=request(requestId);
        periods.requireOpen(day(item.get("expense_on")), "bổ sung chứng từ đề nghị chi");
        if(!branch.equals(item.get("branch_id"))) forbidden("Không thể thêm chứng từ cho yêu cầu của cơ sở khác.");
        if("PAID".equals(item.get("status"))||"REJECTED".equals(item.get("status"))) conflict("Không thể thêm chứng từ khi yêu cầu đã kết thúc.");
        if("TRANSFER_PROOF".equals(kind)&&!"PAYMENT".equals(item.get("kind"))) bad("Chứng từ chuyển khoản chỉ áp dụng cho đề nghị thanh toán.");
        if(attachments(requestId).stream().anyMatch(a->kind.equals(a.get("kind")))) conflict("Chứng từ này đã được tải lên.");
        attach("EXPENSE_REQUEST",requestId,kind,file,who.id());
        return request(requestId);
    }

    public List<Map<String,Object>> listRequests(Identity who) {
        if("HEAD_OFFICE_MANAGER".equals(who.role())) return requests(null);
        if("ACCOUNTANT".equals(who.role())) return requests(null);
        return requests(branch(who));
    }

    @Transactional
    public Map<String,Object> review(Identity who, UUID id, boolean approve, String note) {
        if(!approve&&clean(note,1000).isBlank()) bad("Vui lòng nhập lý do từ chối.");
        Map<String,Object> item=request(id); String status=(String)item.get("status"); String role=who.role();
        periods.requireOpen(day(item.get("expense_on")), "xét duyệt đề nghị chi hoặc hoàn ứng");
        if("HEAD_OFFICE_MANAGER".equals(role)) {
            if(!"WAITING_HEAD_OFFICE".equals(status)) conflict("Đề nghị này không chờ quản lý cấp cao xét duyệt.");
            db.update("UPDATE gcmb.finance_expense_request SET status=?,head_office_reviewed_by=?,head_office_reviewed_at=CURRENT_TIMESTAMP WHERE id=?",
                approve?"APPROVED_TO_PAY":"REJECTED",who.id(),id);
        } else if("ACCOUNTANT".equals(role)) {
            if(!"WAITING_ACCOUNTANT".equals(status)) conflict("Đề nghị này không chờ kế toán xét duyệt.");
            db.update("UPDATE gcmb.finance_expense_request SET status=?,accountant_reviewed_by=?,accountant_reviewed_at=CURRENT_TIMESTAMP WHERE id=?",
                approve?"APPROVED_TO_PAY":"REJECTED",who.id(),id);
        } else forbidden("Bạn không có quyền xét duyệt đề nghị chi.");
        String action=approve?"APPROVED":"REJECTED";
        db.update("INSERT INTO gcmb.finance_request_history(request_id,actor_id,action,note) VALUES(?,?,?,?)",id,who.id(),action,clean(note,1000));
        return request(id);
    }

    @Transactional
    public Map<String,Object> pay(Identity who, UUID id, String reference, MultipartFile proof,
            Integer allocationMonths, LocalDate allocationStart) {
        requireRole(who,"ACCOUNTANT");
        return recordApprovedPayment(who,id,reference,proof,allocationMonths,allocationStart);
    }

    private Map<String,Object> recordApprovedPayment(Identity who, UUID id, String reference, MultipartFile proof,
            Integer requestedMonths, LocalDate requestedStart) {
        Map<String,Object> item=request(id);
        periods.requireOpen(day(item.get("expense_on")), "ghi nhận thanh toán đề nghị chi hoặc hoàn ứng");
        if(!"APPROVED_TO_PAY".equals(item.get("status"))) conflict("Chỉ có thể ghi nhận đã thanh toán khi đề nghị đã được phê duyệt và việc thanh toán đã được xác nhận."); // MSG97 / BR-207.
        if(proof==null||proof.isEmpty()) bad("Vui lòng đính kèm chứng từ hoặc minh chứng cho khoản đề nghị thanh toán."); // MSG95 / BR-90.
        if(Boolean.TRUE.equals(item.get("invoice_required"))&&attachments(id).stream().noneMatch(a->"INVOICE".equals(a.get("kind"))))
            bad("Hạng mục này bắt buộc có hóa đơn. Vui lòng tải hóa đơn trước khi thanh toán.");

        String recognitionMode=(String)item.get("recognition_mode");
        Integer months=null;
        LocalDate firstMonth=null;
        List<ExpenseAllocationCalculator.Line> allocationLines=List.of();
        if("ALLOCATED".equals(recognitionMode)) {
            Object storedMonths=item.get("allocation_months");
            months=requestedMonths!=null?requestedMonths:(storedMonths instanceof Number n?n.intValue():null);
            if(months==null) bad("Vui lòng chọn số tháng phân bổ từ 2 đến 60.");
            firstMonth=(requestedStart==null?day(item.get("expense_on")):requestedStart).withDayOfMonth(1);
            periods.requireOpen(firstMonth,"ghi nhận chi phí phân bổ"); // BR-100: kỳ bắt đầu đã khóa thì không ghi nhận trực tiếp.
            try { allocationLines=ExpenseAllocationCalculator.calculate((BigDecimal)item.get("amount"),months); }
            catch(IllegalArgumentException invalid) { bad(invalid.getMessage()); }
        } else if(requestedMonths!=null||requestedStart!=null) {
            bad("Hạng mục này ghi nhận ngay và không có lịch phân bổ.");
        }

        AccountantFund.lock(db);
        BigDecimal available=balance(); BigDecimal amount=(BigDecimal)item.get("amount");
        // BR-206: chi trực tiếp và chi theo đề nghị đều phải nằm trong số dư quỹ kế toán.
        if(available.compareTo(amount)<0) conflict("Quỹ kế toán hiện còn "+available.toPlainString()+" đ, không đủ cho khoản "+amount.toPlainString()+" đ. Vui lòng kiểm tra lại nguồn tiền hoặc số tiền."); // MSG93.
        int changed=db.update("UPDATE gcmb.finance_expense_request SET status='PAID',paid_by=?,paid_at=CURRENT_TIMESTAMP,paid_reference=?,"+
            "allocation_months=?::integer,allocation_start=?::date WHERE id=? AND status='APPROVED_TO_PAY'",
            who.id(),clean(reference,200),months,firstMonth,id);
        if(changed==0) conflict("Đề nghị vừa được xử lý ở phiên khác. Vui lòng tải lại.");
        if("ALLOCATED".equals(recognitionMode)) {
            for(ExpenseAllocationCalculator.Line line:allocationLines) {
                db.update("INSERT INTO gcmb.finance_expense_allocation(request_id,branch_id,category_id,period_month,seq,amount) VALUES(?,?,?,?,?,?)",
                    id,item.get("branch_id"),item.get("category_id"),firstMonth.plusMonths(line.sequence()-1L),line.sequence(),line.amount());
            }
        }
        attach("EXPENSE_REQUEST",id,"PAYMENT_PROOF",proof,who.id());
        db.update("INSERT INTO gcmb.finance_request_history(request_id,actor_id,action,note) VALUES(?,?,?,?)",id,who.id(),"PAID",clean(reference,1000));
        return request(id);
    }

    @Transactional
    public Map<String,Object> createDirectExpense(Identity who, UUID idempotencyKey, UUID branchId, UUID categoryId,
            BigDecimal amount, LocalDate expenseOn, String description, String beneficiaryName, String bankAccount,
            String bankName, String reference, Integer allocationMonths, LocalDate allocationStart,
            MultipartFile proof, MultipartFile invoice) {
        requireRole(who,"ACCOUNTANT");
        if(idempotencyKey==null) bad("Thiếu mã xác nhận khoản chi trực tiếp. Vui lòng gửi lại biểu mẫu.");
        // Serialize retries with the same key so the unique index resolves to the original result, not a race error.
        db.query("SELECT pg_advisory_xact_lock(1300261104, hashtext(?))",
            (java.sql.ResultSet rs) -> { rs.next(); rs.getObject(1); return null; },idempotencyKey.toString());
        if(branchId==null||!Boolean.TRUE.equals(db.query("SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE id=?)",(r,n)->r.getBoolean(1),branchId).stream().findFirst().orElse(false)))
            bad("Vui lòng chọn cơ sở hợp lệ.");
        if(categoryId==null) bad("Vui lòng chọn hạng mục chi phí đang hoạt động.");
        if(amount==null||amount.signum()<=0) bad("Số tiền phải lớn hơn 0.");
        if(expenseOn==null) bad("Vui lòng chọn ngày chi.");
        if(clean(description,1000).isBlank()) bad("Vui lòng nhập nội dung chi.");
        if(clean(beneficiaryName,160).isBlank()||clean(bankAccount,80).isBlank()||clean(bankName,120).isBlank())
            bad("Vui lòng nhập người nhận, số tài khoản và ngân hàng.");
        if(proof==null||proof.isEmpty()) bad("Vui lòng đính kèm chứng từ hoặc minh chứng cho khoản đề nghị thanh toán."); // MSG95 / BR-90.

        List<Map<String,Object>> duplicate=db.queryForList("SELECT id,branch_id,category_id,recognition_mode,allocation_months,allocation_start,amount,expense_on,description,beneficiary_name,bank_account,bank_name,paid_reference "+
            "FROM gcmb.finance_expense_request WHERE origin='ACCOUNTANT_DIRECT' AND idempotency_key=?",idempotencyKey);
        if(!duplicate.isEmpty()) {
            Map<String,Object> old=duplicate.get(0);
            boolean sameAllocation;
            if("ALLOCATED".equals(old.get("recognition_mode"))) {
                LocalDate requestedStart=(allocationStart==null?expenseOn:allocationStart).withDayOfMonth(1);
                sameAllocation=(allocationMonths==null||java.util.Objects.equals(allocationMonths,old.get("allocation_months")))&&
                    requestedStart.equals(day(old.get("allocation_start")));
            } else {
                sameAllocation=allocationMonths==null&&allocationStart==null;
            }
            boolean same=branchId.equals(old.get("branch_id"))&&categoryId.equals(old.get("category_id"))&&amount.compareTo((BigDecimal)old.get("amount"))==0&&
                expenseOn.equals(day(old.get("expense_on")))&&clean(description,1000).equals(old.get("description"))&&
                clean(beneficiaryName,160).equals(old.get("beneficiary_name"))&&clean(bankAccount,80).equals(old.get("bank_account"))&&
                clean(bankName,120).equals(old.get("bank_name"))&&clean(reference,200).equals(old.get("paid_reference"))&&sameAllocation;
            if(!same) conflict("Mã xác nhận đã được dùng cho khoản chi khác.");
            return request((UUID)old.get("id"));
        }

        Map<String,Object> category=activeCategory(categoryId);
        String recognitionMode=(String)category.get("recognition_mode");
        Integer months="ALLOCATED".equals(recognitionMode)?(allocationMonths!=null?allocationMonths:((Number)category.get("default_allocation_months")).intValue()):null;
        LocalDate firstMonth="ALLOCATED".equals(recognitionMode)?(allocationStart==null?expenseOn:allocationStart).withDayOfMonth(1):null;
        if("ALLOCATED".equals(recognitionMode)) {
            periods.requireOpen(firstMonth,"ghi nhận chi phí phân bổ");
            try { ExpenseAllocationCalculator.calculate(amount,months); }
            catch(IllegalArgumentException invalid) { bad(invalid.getMessage()); }
        } else if(allocationMonths!=null||allocationStart!=null) {
            bad("Hạng mục này ghi nhận ngay và không có lịch phân bổ.");
        }
        periods.requireOpen(expenseOn,"ghi nhận chi trực tiếp");
        if(Boolean.TRUE.equals(category.get("invoice_required"))&&(invoice==null||invoice.isEmpty()))
            bad("Hạng mục này bắt buộc có hóa đơn. Vui lòng tải hóa đơn trước khi ghi nhận chi.");

        boolean waitingForHeadOffice=amount.compareTo(DIRECT_EXPENSE_HEAD_OFFICE_APPROVAL_LIMIT)>0;
        String status=waitingForHeadOffice?"WAITING_HEAD_OFFICE":"APPROVED_TO_PAY";
        UUID id=UUID.randomUUID();
        db.update("""
            INSERT INTO gcmb.finance_expense_request(id,code,kind,branch_id,category_id,category_type,recognition_mode,allocation_months,
              origin,idempotency_key,amount,expense_on,description,beneficiary_name,bank_account,bank_name,requested_by,status,
              accountant_reviewed_by,accountant_reviewed_at)
            VALUES(?,?,'PAYMENT',?,?,?,?,?::integer,'ACCOUNTANT_DIRECT',?,?,?,?,?,?,?,?,?,
              CASE WHEN ? THEN NULL ELSE ? END,CASE WHEN ? THEN NULL ELSE CURRENT_TIMESTAMP END)
            """,id,code("EXP"),branchId,categoryId,category.get("cost_type"),recognitionMode,months,idempotencyKey,amount,expenseOn,
            clean(description,1000),clean(beneficiaryName,160),clean(bankAccount,80),clean(bankName,120),who.id(),status,
            waitingForHeadOffice,who.id(),waitingForHeadOffice);
        db.update("INSERT INTO gcmb.finance_request_history(request_id,actor_id,action,note) VALUES(?,?,?,?)",id,who.id(),"SUBMITTED","Kế toán ghi nhận chi trực tiếp.");
        if(waitingForHeadOffice) return request(id);

        db.update("INSERT INTO gcmb.finance_request_history(request_id,actor_id,action,note) VALUES(?,?,?,?)",
            id,who.id(),"APPROVED","Kế toán tự duyệt chi trực tiếp theo ngưỡng hiện hành.");
        if(invoice!=null&&!invoice.isEmpty()) attach("EXPENSE_REQUEST",id,"INVOICE",invoice,who.id());
        return recordApprovedPayment(who,id,reference,proof,months,firstMonth);
    }

    @Transactional
    public Map<String,Object> recordRecurringExpensePayment(Identity who, UUID periodId, UUID branchId, UUID categoryId,
            LocalDate dueDate, LocalDate periodMonth, BigDecimal amount, BigDecimal defaultAmount,
            String beneficiaryName, String bankAccount, String bankName, String deviationReason,
            MultipartFile proof, MultipartFile invoice) {
        requireRole(who,"ACCOUNTANT");
        if(amount==null||amount.signum()<=0||amount.stripTrailingZeros().scale()>0)
            bad("Số tiền chi định kỳ phải là VND nguyên và lớn hơn 0.");
        if(proof==null||proof.isEmpty()) bad("Vui lòng đính kèm chứng từ hoặc minh chứng cho khoản đề nghị thanh toán."); // MSG95 / BR-90.
        if(defaultAmount!=null&&amount.compareTo(defaultAmount)!=0&&clean(deviationReason,1000).isBlank())
            bad("Vui lòng nhập lý do khi số tiền chi khác số tiền mặc định.");

        // BR-100: cả kỳ ghi nhận chi phí và kỳ ngày trả phải còn mở.
        periods.requireOpen(periodMonth,"ghi nhận chi phí định kỳ"); // MSG102 / BR-100.
        periods.requireOpen(dueDate,"ghi nhận thanh toán khoản chi định kỳ");
        Map<String,Object> category=activeCategory(categoryId); // BR-209: kỳ mới không được dùng danh mục đã ngừng.
        if(Boolean.TRUE.equals(category.get("invoice_required"))&&(invoice==null||invoice.isEmpty()))
            bad("Hạng mục này bắt buộc có hóa đơn. Vui lòng tải hóa đơn trước khi ghi nhận chi.");

        UUID id=UUID.randomUUID();
        UUID idempotencyKey=UUID.nameUUIDFromBytes(("GCMB_RECURRING_EXPENSE_PERIOD:"+periodId).getBytes(StandardCharsets.UTF_8));
        String description="Chi định kỳ "+category.get("name")+" · kỳ "+periodMonth.toString().substring(0,7);
        String reason=clean(deviationReason,1000);
        if(!reason.isBlank()) description+=" · Lý do chênh lệch: "+reason;
        db.update("""
            INSERT INTO gcmb.finance_expense_request(id,code,kind,branch_id,category_id,category_type,recognition_mode,
              origin,idempotency_key,amount,expense_on,description,beneficiary_name,bank_account,bank_name,requested_by,status,
              accountant_reviewed_by,accountant_reviewed_at)
            VALUES(?,?,'PAYMENT',?,?,?,'IMMEDIATE','ACCOUNTANT_DIRECT',?,?,?,?,?,?,?,?,'APPROVED_TO_PAY',?,CURRENT_TIMESTAMP)
            """,id,code("EXP"),branchId,categoryId,category.get("cost_type"),idempotencyKey,amount,dueDate,
            clean(description,1000),clean(beneficiaryName,160),clean(bankAccount,80),clean(bankName,120),who.id(),who.id());
        db.update("INSERT INTO gcmb.finance_request_history(request_id,actor_id,action,note) VALUES(?,?,?,?)",
            id,who.id(),"SUBMITTED","Gửi ghi nhận chi định kỳ cho kỳ "+periodMonth.toString().substring(0,7)+".");
        db.update("INSERT INTO gcmb.finance_request_history(request_id,actor_id,action,note) VALUES(?,?,?,?)",
            id,who.id(),"APPROVED","Kế toán tự duyệt khoản chi định kỳ trước khi xác nhận thanh toán."); // BR-207.
        if(invoice!=null&&!invoice.isEmpty()) attach("EXPENSE_REQUEST",id,"INVOICE",invoice,who.id());
        // BR-206: dùng chung khóa quỹ, số dư và cập nhật PAID với luồng chi hiện có.
        return recordApprovedPayment(who,id,null,proof,null,null);
    }

    public Map<String,Object> attachment(Identity who, UUID attachmentId) {
        Map<String,Object> a=db.query("""
            SELECT x.*,COALESCE(r.branch_id,t.branch_id) branch_id
            FROM gcmb.finance_attachment x
            LEFT JOIN gcmb.finance_expense_request r ON x.owner_type='EXPENSE_REQUEST' AND r.id=x.owner_id
            LEFT JOIN LATERAL (SELECT e.branch_id FROM gcmb.financial_entry e
              WHERE x.owner_type='FINANCIAL_TRANSACTION' AND e.transaction_id=x.owner_id ORDER BY e.id LIMIT 1) t ON true
            WHERE x.id=?
            """,
            (r,n)->row(r),attachmentId).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy chứng từ."));
        if("BRANCH_MANAGER".equals(who.role())&&!who.branchId().equals(a.get("branch_id"))) forbidden("Bạn không có quyền xem chứng từ của cơ sở khác.");
        if(!List.of("ACCOUNTANT","HEAD_OFFICE_MANAGER","BRANCH_MANAGER").contains(who.role())) forbidden("Bạn không có quyền xem chứng từ.");
        try { a.put("bytes",files.read((String)a.get("object_key"))); return a; }
        catch(IOException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage()==null?"Tệp chứng từ không còn trên máy chủ.":e.getMessage(),e);}
    }

    public List<Map<String,Object>> history(UUID requestId) {
        return db.query("SELECT h.id,h.action,h.note,h.created_at,a.username AS actor_name FROM gcmb.finance_request_history h JOIN gcmb.user_account a ON a.id=h.actor_id WHERE h.request_id=? ORDER BY h.created_at",(r,n)->row(r),requestId);
    }

    public List<Map<String,Object>> attachments(UUID requestId) {
        return db.query("SELECT id,kind,original_name,mime_type,byte_size FROM gcmb.finance_attachment WHERE owner_type='EXPENSE_REQUEST' AND owner_id=? ORDER BY created_at",(r,n)->row(r),requestId);
    }

    public List<Map<String,Object>> allocations(UUID requestId) {
        return db.query("SELECT id,period_month,seq,amount,status FROM gcmb.finance_expense_allocation WHERE request_id=? ORDER BY seq",
            (r,n)->row(r),requestId);
    }

    private Map<String,Object> fundTransfer(UUID id) { return db.queryForMap("SELECT id,code,amount,sent_on,transfer_reference,sent_at,status,received_at,sent_by,received_by FROM gcmb.finance_fund_transfer WHERE id=?",id); }
    private Map<String,Object> request(UUID id) {
        Map<String,Object> item=db.queryForList("""
            SELECT r.id,r.code,r.kind,r.branch_id,b.name AS branch_name,r.category_id,c.name AS category_name,r.category_type,
              r.recognition_mode,r.allocation_months,r.allocation_start,r.origin,c.invoice_required,
              r.amount,r.expense_on,r.description,r.beneficiary_name,r.bank_account,r.bank_name,r.status,
              r.requested_by,req.username AS requested_by_name,r.requested_at,r.accountant_reviewed_by,
              r.accountant_reviewed_at,r.head_office_reviewed_by,r.head_office_reviewed_at,r.paid_by,
              r.paid_at,r.paid_reference
            FROM gcmb.finance_expense_request r JOIN gcmb.branch b ON b.id=r.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=r.category_id JOIN gcmb.user_account req ON req.id=r.requested_by WHERE r.id=?
            """,id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đề nghị chi."));
        item.put("attachments",attachments(id)); item.put("history",history(id)); item.put("allocations",allocations(id)); return item;
    }
    private List<Map<String,Object>> requests(UUID branchId) {
        List<Map<String,Object>> rows=db.queryForList("""
            SELECT r.id,r.code,r.kind,r.branch_id,b.name AS branch_name,r.category_id,c.name AS category_name,r.category_type,
              r.recognition_mode,r.allocation_months,r.allocation_start,r.origin,c.invoice_required,
              r.amount,r.expense_on,r.description,r.beneficiary_name,r.bank_account,r.bank_name,r.status,
              r.requested_by,req.username AS requested_by_name,r.requested_at,r.paid_at,r.paid_reference
            FROM gcmb.finance_expense_request r JOIN gcmb.branch b ON b.id=r.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=r.category_id JOIN gcmb.user_account req ON req.id=r.requested_by
            WHERE (?::uuid IS NULL OR r.branch_id=?) ORDER BY r.requested_at DESC
            """,branchId,branchId);
        rows.forEach(r->{UUID id=(UUID)r.get("id");r.put("attachments",attachments(id));r.put("history",history(id));r.put("allocations",allocations(id));}); return rows;
    }
    private BigDecimal balance() {
        return AccountantFund.balance(db);
    }
    private String attach(String type, UUID owner, String kind, MultipartFile file, UUID actor) {
        // HuyDV / BR-115/116: dịch vụ chung lưu Cloudinary khi bật; giữ phân quyền và metadata chứng từ.
        StoredFile stored=files.store(file, "thu-chi");
        try {
            db.update("INSERT INTO gcmb.finance_attachment(id,owner_type,owner_id,kind,object_key,original_name,mime_type,byte_size,sha256,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                stored.id(),type,owner,kind,stored.objectKey(),stored.originalName(),stored.mimeType(),stored.byteSize(),stored.sha256(),actor);
            return stored.objectKey();
        } catch (RuntimeException failure) {
            files.deleteQuietly(stored.objectKey());
            throw failure;
        }
    }
    private UUID branch(Identity who) {
        if(who==null||!"BRANCH_MANAGER".equals(who.role())||who.branchId()==null) forbidden("Chỉ quản lý cơ sở được tạo đề nghị trong cơ sở đang quản lý.");
        return who.branchId();
    }
    private void requireRole(Identity who,String role) { if(who==null||!role.equals(who.role())) forbidden("Bạn không có quyền thực hiện thao tác này."); }
    private static String code(String prefix) { return prefix+"-"+LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.ofPattern("yyMMdd"))+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(); }
    private static LocalDate day(Object value) { return value instanceof LocalDate date ? date : ((java.sql.Date)value).toLocalDate(); }
    private static String clean(String s,int max) { String v=s==null?"":s.trim(); return v.length()>max?v.substring(0,max):v; }
    private static void bad(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private static void forbidden(String m){throw new ResponseStatusException(HttpStatus.FORBIDDEN,m);}
    private static void conflict(String m){throw new ResponseStatusException(HttpStatus.CONFLICT,m);}
    private static Map<String,Object> row(ResultSet r) throws SQLException {
        var meta=r.getMetaData(); java.util.Map<String,Object> m=new java.util.LinkedHashMap<>();
        for(int i=1;i<=meta.getColumnCount();i++) m.put(meta.getColumnLabel(i),r.getObject(i)); return m;
    }
}
