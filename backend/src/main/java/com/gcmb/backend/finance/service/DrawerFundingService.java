/** Author: HuyDV. Agreed extension: QLCS requests 500k..1m after emptying a drawer; accountant records payment. */
package com.gcmb.backend.finance.service;

import static com.gcmb.backend.finance.service.FinanceAccess.*;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class DrawerFundingService {
  private final JdbcTemplate db;
  private final FinanceAccess access;
  private final CashClosingService cash;
  private final EquipmentAssetFileStorageService storage;
  public DrawerFundingService(JdbcTemplate db,FinanceAccess access,CashClosingService cash,
      EquipmentAssetFileStorageService storage) {
    this.db=db;
    this.access=access;
    this.cash=cash;
    this.storage=storage;
  }

  public record Request(BigDecimal amount,String reason,UUID requestId) {}
  public record Payment(boolean confirmed,String reference,String note) {}

  public Map<String,Object> overview(Identity actor,UUID branchId) {
    var result=new LinkedHashMap<String,Object>();
    if("BRANCH_MANAGER".equals(actor.role())) {
      branchId=access.branch(actor,false);
      var preview=cash.preview(actor,null);
      result.put("drawerBalance",preview.get("currentBalance"));
      result.put("accountId",preview.get("accountId"));
      result.put("recipient",recipient(actor.id(),false));
      result.put("requesterId",actor.id());
    } else {
      access.accountant(actor);
      result.put("fundBalance",AccountantFund.balance(db));
      result.put("branches",db.queryForList("SELECT id,name FROM gcmb.branch ORDER BY name"));
    }
    var rows=db.queryForList("""
      SELECT f.*,b.name AS "branchName",u.username AS "requestedBy",p.username AS "processedBy"
      FROM gcmb.cash_drawer_funding f JOIN gcmb.branch b ON b.id=f.branch_id
      JOIN gcmb.user_account u ON u.id=f.requested_by LEFT JOIN gcmb.user_account p ON p.id=f.processed_by
      WHERE (CAST(? AS uuid) IS NULL OR f.branch_id=?) ORDER BY f.requested_at DESC,f.id
      """,branchId,branchId);
    result.put("requests",rows);
    rows.forEach(this::publicRow);
    return result;
  }

  // HuyDV: lấy tài khoản của QLCS gửi yêu cầu; không nhận người thụ hưởng từ trình duyệt.
  private Map<String,Object> recipient(UUID actor,boolean required) {
    var row=db.queryForMap("""
      SELECT COALESCE(e.full_name,u.username) AS recipient_name,e.bank_name AS recipient_bank_name,
        e.bank_account AS recipient_bank_account,e.bank_holder AS recipient_bank_holder
      FROM gcmb.user_account u LEFT JOIN gcmb.employee e ON e.user_id=u.id WHERE u.id=?
      """,actor);
    if(required && (row.values().stream().anyMatch(v->v==null || v.toString().isBlank())
        || !Objects.toString(row.get("recipient_bank_account"),"").matches("[A-Za-z0-9]{3,60}"))) {
      throw bad("QLCS cần cập nhật đầy đủ ngân hàng, số tài khoản và chủ tài khoản trong Thông tin tài khoản trước khi gửi yêu cầu cấp két.");
    }
    return row;
  }

  private Map<String,Object> publicRow(Map<String,Object> row) {
    row.put("hasEvidence",row.remove("evidence_object_key")!=null);
    return row;
  }

  public Map<String,Object> completeRecipient(Identity actor,UUID id) {
    access.lock();
    boolean accounting="ACCOUNTANT".equals(actor.role());
    UUID branch=null;
    if(accounting) access.accountant(actor);
    else branch=access.branch(actor,true);
    var old=pending(id);
    if(!accounting && (!branch.equals(old.get("branch_id")) || !actor.id().equals(old.get("requested_by"))))
      throw bad("Chỉ QLCS đã gửi yêu cầu tại cơ sở này được bổ sung thông tin nhận tiền.");
    // HuyDV: yêu cầu cũ lấy đúng người đã gửi, kể cả khi kế toán mở màn thanh toán.
    // Thông tin đã lưu được giữ nguyên; không tự đổi tài khoản theo lần sửa hồ sơ sau đó.
    if(old.get("recipient_name")!=null) return publicRow(old);
    branch=(UUID)old.get("branch_id");
    var receiver=recipient((UUID)old.get("requested_by"),true);
    db.update("""
      UPDATE gcmb.cash_drawer_funding SET recipient_name=?,recipient_bank_name=?,recipient_bank_account=?,recipient_bank_holder=?
      WHERE id=?
      """,receiver.get("recipient_name"),receiver.get("recipient_bank_name"),
      receiver.get("recipient_bank_account"),receiver.get("recipient_bank_holder"),id);
    access.audit(actor,branch,id,"COMPLETE_DRAWER_FUNDING_RECIPIENT",null,receiver);
    return publicRow(db.queryForMap("SELECT * FROM gcmb.cash_drawer_funding WHERE id=?",id));
  }

  public Map<String,Object> request(Identity actor,Request input) {
    access.lock();
    UUID branch=access.branch(actor,true);
    BigDecimal amount=money(input.amount(),"Tiền cấp két",true);
    if(amount.compareTo(new BigDecimal("500000"))<0 || amount.compareTo(new BigDecimal("1000000"))>0)
      throw bad("Mỗi lần cấp két phải từ 500.000đ đến 1.000.000đ.");
    String reason=text(input.reason(),"Nội dung yêu cầu cấp két",true);
    if(input.requestId()==null) throw bad("Thiếu mã xác nhận yêu cầu cấp két.");
    var duplicate=db.queryForList("SELECT * FROM gcmb.cash_drawer_funding WHERE idempotency_key=?",input.requestId());
    if(!duplicate.isEmpty()) {
      var old=duplicate.get(0);
      if(!Objects.equals(old.get("branch_id"),branch) || amount.compareTo((BigDecimal)old.get("amount"))!=0
        || !reason.equals(old.get("reason"))) throw conflict("Mã xác nhận đã được dùng cho yêu cầu khác.");
      return publicRow(old);
    }
    var preview=cash.preview(actor,null);
    UUID account=(UUID)preview.get("accountId");
    if(new BigDecimal(preview.get("currentBalance").toString()).signum()!=0)
      throw conflict("Cần nộp hết tiền trong két, đưa số dư về 0đ trước khi yêu cầu cấp lại.");
    if(Boolean.TRUE.equals(db.queryForObject(
      "SELECT EXISTS(SELECT 1 FROM gcmb.cash_drawer_funding WHERE account_id=? AND status='REQUESTED')",
      Boolean.class,account))) throw conflict("Két đã có yêu cầu cấp tiền đang chờ kế toán.");
    UUID id=UUID.randomUUID();
    var receiver=recipient(actor.id(),true);
    String code="KET-"+LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString().replace("-","")+"-"+id.toString().substring(0,8).toUpperCase(Locale.ROOT);
    db.update("""
      INSERT INTO gcmb.cash_drawer_funding(id,code,branch_id,account_id,amount,reason,requested_by,idempotency_key,
        recipient_name,recipient_bank_name,recipient_bank_account,recipient_bank_holder)
      VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
      """,id,code,branch,account,amount,reason,actor.id(),input.requestId(),receiver.get("recipient_name"),
      receiver.get("recipient_bank_name"),receiver.get("recipient_bank_account"),receiver.get("recipient_bank_holder"));
    access.audit(actor,branch,id,"REQUEST_DRAWER_FUNDING",null,Map.of("amount",amount,"reason",reason));
    return publicRow(db.queryForMap("SELECT * FROM gcmb.cash_drawer_funding WHERE id=?",id));
  }

  public Map<String,Object> pay(Identity actor,UUID id,Payment input) {
    return pay(actor,id,input,null);
  }

  public Map<String,Object> pay(Identity actor,UUID id,Payment input,MultipartFile proof) {
    access.accountant(actor);
    access.lock(); // Same lock as expense and payroll payments; one shared fund cannot be overspent.
    var old=pending(id);
    if(old.get("recipient_name")==null) throw conflict("Yêu cầu chưa có thông tin nhận tiền. QLCS đã gửi cần bổ sung từ Thông tin tài khoản trước khi thanh toán.");
    if(!input.confirmed()) throw bad("Vui lòng xác nhận đã thực tế thanh toán tiền cấp két cho cơ sở.");
    var account=db.queryForMap("""
      SELECT a.active,a.kind,a.branch_id,b.status,b.timezone FROM gcmb.financial_account a
      JOIN gcmb.branch b ON b.id=a.branch_id WHERE a.id=? FOR SHARE OF a,b
      """,old.get("account_id"));
    if(!Boolean.TRUE.equals(account.get("active")) || !"CASH_DRAWER".equals(account.get("kind"))
      || !"ACTIVE".equals(account.get("status")) || !Objects.equals(account.get("branch_id"),old.get("branch_id")))
      throw conflict("Cơ sở hoặc két tiền mặt đã ngừng hoạt động; chưa thể thanh toán yêu cầu.");
    LocalDate today=LocalDate.now(ZoneId.of((String)account.get("timezone")));
    if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.financial_period WHERE month=? AND status='CLOSED')",
      Boolean.class,today.withDayOfMonth(1)))) throw conflict("Kỳ tài chính hiện tại đã khóa.");
    BigDecimal amount=(BigDecimal)old.get("amount");
    if(AccountantFund.balance(db).compareTo(amount)<0) throw conflict("Quỹ kế toán không đủ tiền để cấp két.");
    String reference=text(input.reference(),"Mã chứng từ thanh toán",false);
    if(reference.length()>200) throw bad("Mã chứng từ tối đa 200 ký tự.");
    String note=text(input.note(),"Ghi chú thanh toán",false);
    if(proof!=null && proof.isEmpty()) throw bad("Tệp minh chứng thanh toán không được để rỗng.");
    boolean hasProof=proof!=null && !proof.isEmpty();
    if(reference.isBlank() && !hasProof) throw bad("Vui lòng nhập mã giao dịch hoặc tải minh chứng thanh toán.");
    EquipmentAssetFileStorageService.StoredFile stored=null;
    if(hasProof) {
      stored=storage.store(proof, "cap-ket");
      String key=stored.objectKey();
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCompletion(int status) {
          if(status!=STATUS_COMMITTED) storage.deleteQuietly(key);
        }
      });
    }
    db.update("""
      UPDATE gcmb.cash_drawer_funding SET status='PAID',processed_by=?,processed_at=clock_timestamp(),
        payment_reference=?,resolution_note=?,evidence_original_name=?,evidence_mime_type=?,evidence_object_key=?
        WHERE id=? AND status='REQUESTED'
      """,actor.id(),reference,note,stored==null?null:stored.originalName(),
      stored==null?null:stored.mimeType(),stored==null?null:stored.objectKey(),id);
    // The PAID row debits AccountantFund and credits drawer_funding_movement atomically.
    access.audit(actor,(UUID)old.get("branch_id"),id,"PAY_DRAWER_FUNDING",old,Map.of("amount",amount,"reference",reference));
    return publicRow(db.queryForMap("SELECT * FROM gcmb.cash_drawer_funding WHERE id=?",id));
  }

  public Map<String,Object> reject(Identity actor,UUID id,Payment input) {
    access.accountant(actor);
    access.lock();
    var old=pending(id);
    String note=text(input.note(),"Lý do từ chối",true);
    db.update("UPDATE gcmb.cash_drawer_funding SET status='REJECTED',processed_by=?,processed_at=clock_timestamp(),resolution_note=? WHERE id=?",
      actor.id(),note,id);
    access.audit(actor,(UUID)old.get("branch_id"),id,"REJECT_DRAWER_FUNDING",old,Map.of("note",note));
    return publicRow(db.queryForMap("SELECT * FROM gcmb.cash_drawer_funding WHERE id=?",id));
  }

  public Map<String,Object> evidence(Identity actor,UUID id) {
    UUID branch=null;
    if("BRANCH_MANAGER".equals(actor.role())) branch=access.branch(actor,false);
    else access.accountant(actor);
    var rows=db.queryForList("""
      SELECT evidence_original_name AS original_name,evidence_mime_type AS mime_type,evidence_object_key AS object_key
      FROM gcmb.cash_drawer_funding WHERE id=? AND (CAST(? AS uuid) IS NULL OR branch_id=?)
        AND evidence_object_key IS NOT NULL
      """,id,branch,branch);
    if(rows.isEmpty()) throw bad("Không tìm thấy minh chứng thanh toán tại cơ sở được phép xem.");
    var row=rows.get(0);
    try { row.put("bytes",storage.read((String)row.get("object_key"))); }
    catch(java.io.IOException failure) { throw conflict("Không đọc được minh chứng thanh toán. Vui lòng thử lại."); }
    return row;
  }

  private Map<String,Object> pending(UUID id) {
    var rows=db.queryForList("SELECT * FROM gcmb.cash_drawer_funding WHERE id=? FOR UPDATE",id);
    if(rows.isEmpty()) throw bad("Không tìm thấy yêu cầu cấp tiền két.");
    var row=rows.get(0);
    if(!"REQUESTED".equals(row.get("status"))) throw conflict("Yêu cầu đã thanh toán hoặc đã từ chối; không được xử lý lần nữa.");
    return row;
  }
}
