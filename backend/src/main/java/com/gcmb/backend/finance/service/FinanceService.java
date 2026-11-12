package com.gcmb.backend.finance.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService.StoredFile;
import java.io.IOException;
import java.math.BigDecimal;
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
        return db.query("SELECT id,name FROM gcmb.branch ORDER BY name", (r,n)->row(r));
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
        return db.query("SELECT c.id,c.code,c.name,c.description,c.cost_type,c.active,c.updated_at,"+
            "(SELECT count(*) FROM gcmb.finance_expense_request r WHERE r.category_id=c.id) AS request_count FROM gcmb.finance_expense_category c "+
            (activeOnly?"WHERE c.active=true ":"")+"ORDER BY c.active DESC,c.name",(r,n)->row(r));
    }

    public List<Map<String,Object>> accountantCategories(Identity who) {
        requireRole(who,"ACCOUNTANT");
        return categories(false);
    }

    public Map<String,Object> saveCategory(Identity who, UUID id, String name, String description, String costType) {
        requireRole(who,"ACCOUNTANT");
        String cleanName=clean(name,120);
        if(cleanName.isBlank()) bad("Vui lòng nhập tên loại chi phí.");
        if(!"OPEX".equals(costType)&&!"CAPEX".equals(costType)) bad("Vui lòng chọn loại chi phí OPEX hoặc CAPEX.");
        if(id==null) {
            UUID created=UUID.randomUUID();
            db.update("INSERT INTO gcmb.finance_expense_category(id,code,name,description,cost_type,created_by) VALUES(?,?,?,?,?,?)",
                created,code("CAT"),cleanName,clean(description,500),costType,who.id());
            id=created;
        } else {
            int count=db.update("UPDATE gcmb.finance_expense_category SET name=?,description=?,cost_type=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",cleanName,clean(description,500),costType,id);
            if(count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy loại chi phí.");
        }
        return db.queryForMap("SELECT c.id,c.code,c.name,c.description,c.cost_type,c.active,c.updated_at,(SELECT count(*) FROM gcmb.finance_expense_request r WHERE r.category_id=c.id) AS request_count FROM gcmb.finance_expense_category c WHERE c.id=?",id);
    }

    public Map<String,Object> setCategoryActive(Identity who, UUID id, boolean active) {
        requireRole(who,"ACCOUNTANT");
        int count=db.update("UPDATE gcmb.finance_expense_category SET active=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND active<>?",active,id,active);
        if(count==0 && db.queryForList("SELECT id FROM gcmb.finance_expense_category WHERE id=?",UUID.class,id).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hạng mục chi phí.");
        return db.queryForMap("SELECT c.id,c.code,c.name,c.description,c.cost_type,c.active,c.updated_at,(SELECT count(*) FROM gcmb.finance_expense_request r WHERE r.category_id=c.id) AS request_count FROM gcmb.finance_expense_category c WHERE c.id=?",id);
    }

    public void deleteCategory(Identity who, UUID id) {
        requireRole(who,"ACCOUNTANT");
        if(db.queryForList("SELECT id FROM gcmb.finance_expense_category WHERE id=? FOR UPDATE",UUID.class,id).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hạng mục chi phí.");
        if(db.queryForObject("SELECT count(*) FROM gcmb.finance_expense_request WHERE category_id=?",Long.class,id)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Không thể xóa hạng mục đã được dùng trong đề nghị chi.");
        db.update("DELETE FROM gcmb.finance_expense_category WHERE id=?",id);
    }

    @Transactional
    public Map<String,Object> createRequest(Identity who, String kind, UUID categoryId, BigDecimal amount,
            LocalDate expenseOn, String description, String beneficiaryName, String bankAccount, String bankName) {
        UUID branch=branch(who);
        if(!"PAYMENT".equals(kind)&&!"REIMBURSEMENT".equals(kind)) bad("Loại đề nghị không hợp lệ.");
        if("REIMBURSEMENT".equals(kind)&&!"BRANCH_MANAGER".equals(who.role())) forbidden("Chỉ quản lý cơ sở được tạo đề nghị hoàn ứng.");
        if(amount==null||amount.signum()<=0) bad("Số tiền phải lớn hơn 0.");
        if(categoryId==null) bad("Vui lòng chọn loại chi phí đang hoạt động.");
        String categoryType=db.query("SELECT cost_type FROM gcmb.finance_expense_category WHERE id=? AND active=true FOR SHARE",(r,n)->r.getString("cost_type"),categoryId).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng chọn loại chi phí đang hoạt động."));
        if(expenseOn==null) bad("Vui lòng nhập ngày chi.");
        periods.requireOpen(expenseOn, "tạo đề nghị chi hoặc hoàn ứng");
        if(clean(description,1000).isBlank()) bad("Vui lòng nhập nội dung chi.");
        if(clean(beneficiaryName,160).isBlank()||clean(bankAccount,80).isBlank()||clean(bankName,120).isBlank()) bad("Vui lòng nhập người nhận, số tài khoản và ngân hàng.");
        String status=amount.compareTo(HEAD_OFFICE_APPROVAL_LIMIT)>0?"WAITING_HEAD_OFFICE":"WAITING_ACCOUNTANT";
        UUID id=UUID.randomUUID();
        db.update("""
            INSERT INTO gcmb.finance_expense_request(id,code,kind,branch_id,category_id,category_type,amount,expense_on,description,
              beneficiary_name,bank_account,bank_name,requested_by,status)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,code("EXP"),kind,branch,categoryId,categoryType,amount,expenseOn,clean(description,1000),clean(beneficiaryName,160),
            clean(bankAccount,80),clean(bankName,120),who.id(),status);
        db.update("INSERT INTO gcmb.finance_request_history(request_id,actor_id,action,note) VALUES(?,?,?,?)",id,who.id(),"SUBMITTED",
            status.equals("WAITING_HEAD_OFFICE")?"Gửi quản lý cấp cao duyệt do khoản chi trên 3.000.000đ":"Gửi kế toán duyệt");
        return request(id);
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
    public Map<String,Object> pay(Identity who, UUID id, String reference, MultipartFile proof) {
        requireRole(who,"ACCOUNTANT");
        Map<String,Object> item=request(id);
        periods.requireOpen(day(item.get("expense_on")), "ghi nhận thanh toán đề nghị chi hoặc hoàn ứng");
        if(!"APPROVED_TO_PAY".equals(item.get("status"))) conflict("Chỉ được thanh toán đề nghị đã duyệt.");
        if(proof==null||proof.isEmpty()) bad("Vui lòng tải lên chứng từ chuyển khoản thực tế.");
        AccountantFund.lock(db);
        BigDecimal available=balance(); BigDecimal amount=(BigDecimal)item.get("amount");
        if(available.compareTo(amount)<0) conflict("Số dư quỹ kế toán không đủ để thanh toán. Số dư hiện tại: "+available.toPlainString()+"đ.");
        int changed=db.update("UPDATE gcmb.finance_expense_request SET status='PAID',paid_by=?,paid_at=CURRENT_TIMESTAMP,paid_reference=? WHERE id=? AND status='APPROVED_TO_PAY'",
            who.id(),clean(reference,200),id);
        if(changed==0) conflict("Đề nghị vừa được xử lý ở phiên khác. Vui lòng tải lại.");
        attach("EXPENSE_REQUEST",id,"PAYMENT_PROOF",proof,who.id());
        db.update("INSERT INTO gcmb.finance_request_history(request_id,actor_id,action,note) VALUES(?,?,?,?)",id,who.id(),"PAID",clean(reference,1000));
        return request(id);
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

    private Map<String,Object> fundTransfer(UUID id) { return db.queryForMap("SELECT id,code,amount,sent_on,transfer_reference,sent_at,status,received_at,sent_by,received_by FROM gcmb.finance_fund_transfer WHERE id=?",id); }
    private Map<String,Object> request(UUID id) {
        Map<String,Object> item=db.queryForList("""
            SELECT r.id,r.code,r.kind,r.branch_id,b.name AS branch_name,r.category_id,c.name AS category_name,r.category_type,
              r.amount,r.expense_on,r.description,r.beneficiary_name,r.bank_account,r.bank_name,r.status,
              r.requested_by,req.username AS requested_by_name,r.requested_at,r.accountant_reviewed_by,
              r.accountant_reviewed_at,r.head_office_reviewed_by,r.head_office_reviewed_at,r.paid_by,
              r.paid_at,r.paid_reference
            FROM gcmb.finance_expense_request r JOIN gcmb.branch b ON b.id=r.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=r.category_id JOIN gcmb.user_account req ON req.id=r.requested_by WHERE r.id=?
            """,id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đề nghị chi."));
        item.put("attachments",attachments(id)); item.put("history",history(id)); return item;
    }
    private List<Map<String,Object>> requests(UUID branchId) {
        List<Map<String,Object>> rows=db.queryForList("""
            SELECT r.id,r.code,r.kind,r.branch_id,b.name AS branch_name,r.category_id,c.name AS category_name,r.category_type,
              r.amount,r.expense_on,r.description,r.beneficiary_name,r.bank_account,r.bank_name,r.status,
              r.requested_by,req.username AS requested_by_name,r.requested_at,r.paid_at,r.paid_reference
            FROM gcmb.finance_expense_request r JOIN gcmb.branch b ON b.id=r.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=r.category_id JOIN gcmb.user_account req ON req.id=r.requested_by
            WHERE (?::uuid IS NULL OR r.branch_id=?) ORDER BY r.requested_at DESC
            """,branchId,branchId);
        rows.forEach(r->{UUID id=(UUID)r.get("id");r.put("attachments",attachments(id));r.put("history",history(id));}); return rows;
    }
    private BigDecimal balance() {
        return AccountantFund.balance(db);
    }
    private String attach(String type, UUID owner, String kind, MultipartFile file, UUID actor) {
        // Finance evidence is stored on the backend's configured local attachment volume.
        // The shared file reader still supports legacy gdrive: keys for existing records.
        StoredFile stored=files.store(file);
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
