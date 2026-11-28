/** Author: HuyDV. Agreed extension: QLCS requests 500k..1m after emptying a drawer; accountant records payment. */
package com.gcmb.backend.finance.service;

import static com.gcmb.backend.finance.service.FinanceAccess.*;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.finance.repository.DrawerFundingRepository;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class DrawerFundingService {
  private final DrawerFundingRepository drawerFundingRepository;
  private final FinanceAccess financeAccess;
  private final CashClosingService cashClosingService;
  private final EquipmentAssetFileStorageService equipmentAssetFileStorageService;
  public DrawerFundingService(DrawerFundingRepository drawerFundingRepository,FinanceAccess financeAccess,CashClosingService cashClosingService,
      EquipmentAssetFileStorageService equipmentAssetFileStorageService) {
    this.drawerFundingRepository=drawerFundingRepository;
    this.financeAccess=financeAccess;
    this.cashClosingService=cashClosingService;
    this.equipmentAssetFileStorageService=equipmentAssetFileStorageService;
  }

  public record Request(BigDecimal amount,String reason,UUID requestId) {}
  public record Payment(boolean confirmed,String reference,String note) {}

  public Map<String,Object> overview(Identity actor,UUID branchId) {
    var result=new LinkedHashMap<String,Object>();
    if("BRANCH_MANAGER".equals(actor.role())) {
      branchId=financeAccess.branch(actor,false);
      var preview=cashClosingService.preview(actor,null);
      result.put("drawerBalance",preview.get("currentBalance"));
      result.put("accountId",preview.get("accountId"));
      result.put("recipient",recipient(actor.id(),false));
      result.put("requesterId",actor.id());
    } else {
      financeAccess.accountant(actor);
      result.put("fundBalance",drawerFundingRepository.accountantFundBalance());
      result.put("branches",drawerFundingRepository.findBranchOptions());
    }
    var requests=drawerFundingRepository.findRequests(branchId);
    result.put("requests",requests);
    requests.forEach(this::publicRow);
    return result;
  }

  // HuyDV: lấy tài khoản của QLCS gửi yêu cầu; không nhận người thụ hưởng từ trình duyệt.
  private Map<String,Object> recipient(UUID requesterId,boolean required) {
    var recipient=drawerFundingRepository.findRecipientOfUser(requesterId);
    if(required && (recipient.values().stream().anyMatch(value->value==null || value.toString().isBlank())
        || !Objects.toString(recipient.get("recipient_bank_account"),"").matches("[A-Za-z0-9]{3,60}"))) {
      throw bad("QLCS cần cập nhật đầy đủ ngân hàng, số tài khoản và chủ tài khoản trong Thông tin tài khoản trước khi gửi yêu cầu cấp két.");
    }
    return recipient;
  }

  private Map<String,Object> publicRow(Map<String,Object> row) {
    row.put("hasEvidence",row.remove("evidence_object_key")!=null);
    return row;
  }

  public Map<String,Object> completeRecipient(Identity actor,UUID fundingId) {
    financeAccess.lock();
    boolean accounting="ACCOUNTANT".equals(actor.role());
    UUID branchId=null;
    if(accounting) financeAccess.accountant(actor);
    else branchId=financeAccess.branch(actor,true);
    var current=pending(fundingId);
    if(!accounting && (!branchId.equals(current.get("branch_id")) || !actor.id().equals(current.get("requested_by"))))
      throw bad("Chỉ QLCS đã gửi yêu cầu tại cơ sở này được bổ sung thông tin nhận tiền.");
    // HuyDV: yêu cầu cũ lấy đúng người đã gửi, kể cả khi kế toán mở màn thanh toán.
    // Thông tin đã lưu được giữ nguyên; không tự đổi tài khoản theo lần sửa hồ sơ sau đó.
    if(current.get("recipient_name")!=null) return publicRow(current);
    branchId=(UUID)current.get("branch_id");
    var recipient=recipient((UUID)current.get("requested_by"),true);
    drawerFundingRepository.updateRecipient(fundingId,recipient.get("recipient_name"),recipient.get("recipient_bank_name"),
      recipient.get("recipient_bank_account"),recipient.get("recipient_bank_holder"));
    financeAccess.audit(actor,branchId,fundingId,"COMPLETE_DRAWER_FUNDING_RECIPIENT",null,recipient);
    return publicRow(drawerFundingRepository.getFunding(fundingId));
  }

  // BR-238, BR-239: chỉ xin cấp khi két bằng 0, từ 500.000 đến 1.000.000 VND và không có yêu cầu chờ.
  public Map<String,Object> request(Identity actor,Request input) {
    financeAccess.lock();
    UUID branchId=financeAccess.branch(actor,true);
    BigDecimal amount=money(input.amount(),"Tiền cấp két",true);
    if(amount.compareTo(new BigDecimal("500000"))<0 || amount.compareTo(new BigDecimal("1000000"))>0)
      throw bad("Mỗi lần cấp két phải từ 500.000đ đến 1.000.000đ.");
    String reason=text(input.reason(),"Nội dung yêu cầu cấp két",true);
    if(input.requestId()==null) throw bad("Thiếu mã xác nhận yêu cầu cấp két.");
    var duplicates=drawerFundingRepository.findByIdempotencyKey(input.requestId());
    if(!duplicates.isEmpty()) {
      var existing=duplicates.get(0);
      if(!Objects.equals(existing.get("branch_id"),branchId) || amount.compareTo((BigDecimal)existing.get("amount"))!=0
        || !reason.equals(existing.get("reason"))) throw conflict("Mã xác nhận đã được dùng cho yêu cầu khác.");
      return publicRow(existing);
    }
    var preview=cashClosingService.preview(actor,null);
    UUID accountId=(UUID)preview.get("accountId");
    if(new BigDecimal(preview.get("currentBalance").toString()).signum()!=0)
      throw conflict("Cần nộp hết tiền trong két, đưa số dư về 0đ trước khi yêu cầu cấp lại.");
    if(drawerFundingRepository.hasPendingRequest(accountId)) throw conflict("Két đã có yêu cầu cấp tiền đang chờ kế toán.");
    UUID fundingId=UUID.randomUUID();
    var recipient=recipient(actor.id(),true);
    String code="KET-"+LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString().replace("-","")+"-"+fundingId.toString().substring(0,8).toUpperCase(Locale.ROOT);
    drawerFundingRepository.insertRequest(fundingId,code,branchId,accountId,amount,reason,actor.id(),input.requestId(),
      recipient.get("recipient_name"),recipient.get("recipient_bank_name"),recipient.get("recipient_bank_account"),
      recipient.get("recipient_bank_holder"));
    financeAccess.audit(actor,branchId,fundingId,"REQUEST_DRAWER_FUNDING",null,Map.of("amount",amount,"reason",reason));
    return publicRow(drawerFundingRepository.getFunding(fundingId));
  }

  public Map<String,Object> pay(Identity actor,UUID fundingId,Payment input) {
    return pay(actor,fundingId,input,null);
  }

  // BR-84, BR-85, BR-240: kế toán cấp két bằng chuyển quỹ nội bộ, ghi giảm quỹ nguồn và tăng két cùng khoản tiền.
  public Map<String,Object> pay(Identity actor,UUID fundingId,Payment input,MultipartFile proof) {
    financeAccess.accountant(actor);
    financeAccess.lock(); // Same lock as expense and payroll payments; one shared fund cannot be overspent.
    var current=pending(fundingId);
    if(current.get("recipient_name")==null) throw conflict("Yêu cầu chưa có thông tin nhận tiền. QLCS đã gửi cần bổ sung từ Thông tin tài khoản trước khi thanh toán.");
    if(!input.confirmed()) throw bad("Vui lòng xác nhận đã thực tế thanh toán tiền cấp két cho cơ sở.");
    var account=drawerFundingRepository.lockDrawerAccount(current.get("account_id"));
    // BR-212: không cấp két cho cơ sở hoặc két đã ngừng hoạt động.
    if(!Boolean.TRUE.equals(account.get("active")) || !"CASH_DRAWER".equals(account.get("kind"))
      || !"ACTIVE".equals(account.get("status")) || !Objects.equals(account.get("branch_id"),current.get("branch_id")))
      throw conflict("Cơ sở hoặc két tiền mặt đã ngừng hoạt động; chưa thể thanh toán yêu cầu.");
    LocalDate today=LocalDate.now(ZoneId.of((String)account.get("timezone")));
    if(drawerFundingRepository.isPeriodClosed(today.withDayOfMonth(1))) throw conflict("Kỳ tài chính hiện tại đã khóa.");
    BigDecimal amount=(BigDecimal)current.get("amount");
    if(drawerFundingRepository.accountantFundBalance().compareTo(amount)<0) throw conflict("Quỹ kế toán không đủ tiền để cấp két.");
    String reference=text(input.reference(),"Mã chứng từ thanh toán",false);
    if(reference.length()>200) throw bad("Mã chứng từ tối đa 200 ký tự.");
    String note=text(input.note(),"Ghi chú thanh toán",false);
    if(proof!=null && proof.isEmpty()) throw bad("Tệp minh chứng thanh toán không được để rỗng.");
    boolean hasProof=proof!=null && !proof.isEmpty();
    if(reference.isBlank() && !hasProof) throw bad("Vui lòng nhập mã giao dịch hoặc tải minh chứng thanh toán.");
    EquipmentAssetFileStorageService.StoredFile storedProof=null;
    if(hasProof) {
      storedProof=equipmentAssetFileStorageService.store(proof, "cap-ket");
      String objectKey=storedProof.objectKey();
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCompletion(int status) {
          if(status!=STATUS_COMMITTED) equipmentAssetFileStorageService.deleteQuietly(objectKey);
        }
      });
    }
    drawerFundingRepository.markPaid(fundingId,actor.id(),reference,note,
      storedProof==null?null:storedProof.originalName(),
      storedProof==null?null:storedProof.mimeType(),
      storedProof==null?null:storedProof.objectKey());
    // The PAID row debits AccountantFund and credits drawer_funding_movement atomically.
    financeAccess.audit(actor,(UUID)current.get("branch_id"),fundingId,"PAY_DRAWER_FUNDING",current,Map.of("amount",amount,"reference",reference));
    return publicRow(drawerFundingRepository.getFunding(fundingId));
  }

  public Map<String,Object> reject(Identity actor,UUID fundingId,Payment input) {
    financeAccess.accountant(actor);
    financeAccess.lock();
    var current=pending(fundingId);
    String note=text(input.note(),"Lý do từ chối",true);
    drawerFundingRepository.markRejected(fundingId,actor.id(),note);
    financeAccess.audit(actor,(UUID)current.get("branch_id"),fundingId,"REJECT_DRAWER_FUNDING",current,Map.of("note",note));
    return publicRow(drawerFundingRepository.getFunding(fundingId));
  }

  public Map<String,Object> evidence(Identity actor,UUID fundingId) {
    UUID branchId=null;
    if("BRANCH_MANAGER".equals(actor.role())) branchId=financeAccess.branch(actor,false);
    else financeAccess.accountant(actor);
    var rows=drawerFundingRepository.findEvidence(fundingId,branchId);
    if(rows.isEmpty()) throw bad("Không tìm thấy minh chứng thanh toán tại cơ sở được phép xem.");
    var evidence=rows.get(0);
    try { evidence.put("bytes",equipmentAssetFileStorageService.read((String)evidence.get("object_key"))); }
    catch(java.io.IOException failure) { throw conflict("Không đọc được minh chứng thanh toán. Vui lòng thử lại."); }
    return evidence;
  }

  private Map<String,Object> pending(UUID fundingId) {
    var rows=drawerFundingRepository.lockFunding(fundingId);
    if(rows.isEmpty()) throw bad("Không tìm thấy yêu cầu cấp tiền két.");
    var funding=rows.get(0);
    if(!"REQUESTED".equals(funding.get("status"))) throw conflict("Yêu cầu đã thanh toán hoặc đã từ chối; không được xử lý lần nữa.");
    return funding;
  }
}
