package com.gcmb.backend.finance.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.finance.repository.FinanceRepository;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService.StoredFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
    private final FinanceRepository financeRepository;
    private final EquipmentAssetFileStorageService equipmentAssetFileStorageService;
    private final FinancialPeriodService financialPeriodService;

    public FinanceService(FinanceRepository financeRepository, EquipmentAssetFileStorageService equipmentAssetFileStorageService, FinancialPeriodService financialPeriodService) {
        this.financeRepository = financeRepository;
        this.equipmentAssetFileStorageService = equipmentAssetFileStorageService;
        this.financialPeriodService = financialPeriodService;
    }

    public Map<String,Object> summary(Identity actor) {
        requireRole(actor, "ACCOUNTANT");
        return Map.of("balance", balance(), "fundTransfers", fundTransfers(), "categories", categories(false),
            "requests", requests(null), "branches", financeRepository.findBranches());
    }

    public Map<String,Object> headOfficeSummary(Identity actor) {
        requireRole(actor, "HEAD_OFFICE_MANAGER");
        return Map.of("fundTransfers", fundTransfers(), "requests", requests(null),
            "categories", categories(false), "branches", financeRepository.findBranches());
    }

    public Map<String,Object> branchSummary(Identity actor) {
        UUID branchId = branch(actor);
        return Map.of("categories", categories(true), "requests", requests(branchId),
            "stockReceipts", financeRepository.findUnclaimedStockReceipts(branchId));
    }

    public List<Map<String,Object>> fundTransfers() {
        return financeRepository.findFundTransfers();
    }

    @Transactional
    public Map<String,Object> createFundTransfer(Identity actor, BigDecimal amount, LocalDate sentOn,
            String reference) {
        requireRole(actor,"HEAD_OFFICE_MANAGER");
        if (amount==null||amount.signum()<=0) bad("Số tiền chuyển phải lớn hơn 0.");
        if (sentOn==null) bad("Vui lòng chọn ngày gửi tiền.");
        financialPeriodService.requireOpen(sentOn, "tạo giao dịch cấp quỹ");
        UUID transferId=UUID.randomUUID();
        financeRepository.insertFundTransfer(transferId,code("FUND"),amount,sentOn,clean(reference,200),actor.id());
        return financeRepository.getFundTransfer(transferId);
    }

    @Transactional
    public Map<String,Object> addFundTransferProof(Identity actor, UUID transferId, MultipartFile proof) {
        requireRole(actor,"HEAD_OFFICE_MANAGER");
        Map<String,Object> transfer=financeRepository.findFundTransferOwnership(transferId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy khoản chuyển quỹ."));
        financialPeriodService.requireOpen(toLocalDate(transfer.get("sent_on")), "bổ sung chứng từ giao dịch cấp quỹ");
        if(!actor.id().equals(transfer.get("sent_by"))) forbidden("Chỉ quản lý cấp cao đã tạo khoản chuyển mới được bổ sung chứng từ.");
        if(!"WAITING_CONFIRMATION".equals(transfer.get("status"))) conflict("Chỉ bổ sung chứng từ khi kế toán chưa xác nhận khoản chuyển.");
        if(financeRepository.fundTransferHasProof(transferId)) conflict("Khoản chuyển đã có chứng từ.");
        attach("FUND_TRANSFER",transferId,"TRANSFER_PROOF",proof,actor.id());
        return fundTransfers().stream().filter(candidate->transferId.equals(candidate.get("id"))).findFirst().orElseThrow();
    }

    @Transactional
    public Map<String,Object> confirmFundTransfer(Identity actor, UUID transferId) {
        requireRole(actor,"ACCOUNTANT");
        List<Map<String,Object>> transfers=financeRepository.findFundTransferSentOn(transferId);
        if(transfers.isEmpty()) conflict("Khoản chuyển không còn chờ xác nhận hoặc không tồn tại.");
        financialPeriodService.requireOpen(toLocalDate(transfers.get(0).get("sent_on")), "xác nhận giao dịch cấp quỹ");
        financeRepository.lockAccountantFund();
        int updatedCount=financeRepository.confirmFundTransfer(transferId,actor.id());
        if(updatedCount==0) conflict("Khoản chuyển không còn chờ xác nhận hoặc không tồn tại.");
        return financeRepository.getFundTransfer(transferId);
    }

    public List<Map<String,Object>> categories(boolean activeOnly) {
        return financeRepository.findCategories(activeOnly);
    }

    public List<Map<String,Object>> accountantCategories(Identity actor) {
        requireRole(actor,"ACCOUNTANT");
        return categories(false);
    }

    public Map<String,Object> saveCategory(Identity actor, UUID categoryId, String name, String description, String categoryGroup,
            String costType, String costNature, String recognitionMode, Integer defaultAllocationMonths,
            boolean invoiceRequired, boolean reimbursementAllowed) {
        requireRole(actor,"ACCOUNTANT");
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
        if(categoryId==null) {
            UUID createdId=UUID.randomUUID();
            financeRepository.insertCategory(createdId,code("CAT"),cleanName,clean(description,500),categoryGroup,costType,
                costNature,recognitionMode,defaultAllocationMonths,invoiceRequired,reimbursementAllowed,actor.id());
            categoryId=createdId;
        } else {
            int updatedCount=financeRepository.updateCategory(categoryId,cleanName,clean(description,500),categoryGroup,costType,costNature,
                recognitionMode,defaultAllocationMonths,invoiceRequired,reimbursementAllowed);
            if(updatedCount==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy loại chi phí.");
        }
        return financeRepository.getCategory(categoryId);
    }

    public Map<String,Object> setCategoryActive(Identity actor, UUID categoryId, boolean active) {
        requireRole(actor,"ACCOUNTANT");
        int updatedCount=financeRepository.setCategoryActive(categoryId,active);
        if(updatedCount==0 && !financeRepository.categoryExists(categoryId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hạng mục chi phí.");
        return financeRepository.getCategory(categoryId);
    }

    public void deleteCategory(Identity actor, UUID categoryId) {
        requireRole(actor,"ACCOUNTANT");
        if(!financeRepository.lockCategory(categoryId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hạng mục chi phí.");
        // BR-118: không xóa hạng mục đã được lịch sử nghiệp vụ tham chiếu.
        if(financeRepository.categoryIsReferenced(categoryId))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.");
        financeRepository.deleteCategory(categoryId);
    }

    @Transactional
    public Map<String,Object> createRequest(Identity actor, String kind, UUID categoryId, BigDecimal amount,
            LocalDate expenseOn, String description, String beneficiaryName, List<UUID> receiptIds) {
        UUID branchId=branch(actor);
        if(!"PAYMENT".equals(kind)&&!"REIMBURSEMENT".equals(kind)) bad("Loại đề nghị không hợp lệ.");
        if("REIMBURSEMENT".equals(kind)&&!"BRANCH_MANAGER".equals(actor.role())) forbidden("Chỉ quản lý cơ sở được tạo đề nghị hoàn ứng.");
        Map<UUID,BigDecimal> linkedReceipts=new LinkedHashMap<>();
        if(receiptIds!=null&&!receiptIds.isEmpty()) {
            if(!"REIMBURSEMENT".equals(kind)) bad("Chỉ chọn phiếu nhập cho đề nghị hoàn ứng tiền đã chi.");
            if(receiptIds.contains(null)) bad("Danh sách phiếu nhập không hợp lệ.");
            // BR-33, BR-94, BR-197: tính lại giá trị phiếu đã nhận, khóa phiếu và chặn hoàn ứng trùng; nhập kho không ghi thanh toán.
            for(UUID receiptId:receiptIds.stream().sorted().toList()) {
                if(linkedReceipts.containsKey(receiptId)) bad("Mỗi phiếu nhập chỉ được chọn một lần.");
                List<Map<String,Object>> receipts=financeRepository.lockStockReceiptWithAmount(receiptId,branchId);
                if(receipts.isEmpty()||!"POSTED".equals(receipts.get(0).get("status")))
                    bad("Phiếu nhập không thuộc cơ sở hoặc chưa được xác nhận.");
                List<String> claimStatuses=financeRepository.findActiveClaimStatuses(receiptId);
                if(!claimStatuses.isEmpty()) {
                    // BR-94 / MSG99: khoản đã hoàn ứng không được dùng lần nữa.
                    if("PAID".equals(claimStatuses.get(0))) conflict("Khoản chi này đã được hoàn ứng. Vui lòng kiểm tra khoản hoàn ứng đã ghi nhận.");
                    conflict("Phiếu nhập đã có trong đề nghị đang xử lý. Vui lòng tải lại danh sách.");
                }
                BigDecimal receiptAmount=(BigDecimal)receipts.get(0).get("amount");
                if(receiptAmount.signum()<=0) bad("Phiếu nhập không có giá trị cần hoàn ứng.");
                linkedReceipts.put(receiptId,receiptAmount);
            }
            BigDecimal calculatedAmount=linkedReceipts.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
            if(amount==null||amount.compareTo(calculatedAmount)!=0)
                conflict("Tổng tiền phiếu nhập đã thay đổi. Vui lòng tải lại và kiểm tra.");
            amount=calculatedAmount;
        }
        if(amount==null||amount.signum()<=0) bad("Số tiền phải lớn hơn 0.");
        if(amount.compareTo(new BigDecimal("999999999999.99"))>0) bad("Tổng tiền vượt giới hạn đề nghị chi.");
        if(categoryId==null) bad("Vui lòng chọn loại chi phí đang hoạt động.");
        Map<String,Object> category=activeCategory(categoryId);
        if("REIMBURSEMENT".equals(kind)&&!Boolean.TRUE.equals(category.get("reimbursement_allowed")))
            bad("Hạng mục này không cho phép hoàn ứng.");
        String categoryType=(String)category.get("cost_type");
        String recognitionMode=(String)category.get("recognition_mode");
        Integer allocationMonths="ALLOCATED".equals(recognitionMode)?((Number)category.get("default_allocation_months")).intValue():null;
        if("ALLOCATED".equals(recognitionMode)) requireWholeVnd(amount);
        if(expenseOn==null) bad("Vui lòng nhập ngày chi.");
        financialPeriodService.requireOpen(expenseOn, "tạo đề nghị chi hoặc hoàn ứng");
        if(clean(description,1000).isBlank()) bad("Vui lòng nhập nội dung chi.");
        if(clean(beneficiaryName,160).isBlank()) bad("Vui lòng nhập người nhận tiền.");
        // BR-91, BR-93: đề nghị thanh toán trên 3.000.000đ cần Quản lý cấp cao duyệt; hoàn ứng do Kế toán duyệt.
        String status="PAYMENT".equals(kind)&&amount.compareTo(HEAD_OFFICE_APPROVAL_LIMIT)>0
            ?"WAITING_HEAD_OFFICE":"WAITING_ACCOUNTANT";
        UUID requestId=UUID.randomUUID();
        financeRepository.insertBranchRequest(requestId,code("EXP"),kind,branchId,categoryId,categoryType,recognitionMode,allocationMonths,
            amount,expenseOn,clean(description,1000),clean(beneficiaryName,160),actor.id(),status);
        // BR-92, BR-94: requester lấy từ phiên đăng nhập; liên kết phiếu nhập giữ nguyên chứng cứ khoản chi và chặn dùng lại.
        for(var linked:linkedReceipts.entrySet())
            financeRepository.linkStockReceipt(requestId,linked.getKey(),linked.getValue());
        financeRepository.insertHistory(requestId,actor.id(),"SUBMITTED",
            status.equals("WAITING_HEAD_OFFICE")?"Gửi quản lý cấp cao duyệt do khoản chi trên 3.000.000đ":"Gửi kế toán duyệt");
        return request(requestId);
    }

    private Map<String,Object> activeCategory(UUID categoryId) {
        List<Map<String,Object>> categories=financeRepository.lockCategoryForUse(categoryId);
        if(categories.isEmpty()) bad("Vui lòng chọn loại chi phí đang hoạt động.");
        Map<String,Object> category=categories.get(0);
        if(!Boolean.TRUE.equals(category.get("active")))
            bad("Danh mục chi phí "+category.get("name")+" đã ngừng sử dụng. Vui lòng chọn danh mục đang hoạt động."); // MSG103 / BR-209.
        return category;
    }

    private static void requireWholeVnd(BigDecimal amount) {
        try { amount.longValueExact(); }
        catch(ArithmeticException invalidAmount) { bad("Số tiền phân bổ phải là VND nguyên, không có phần thập phân."); }
    }

    @Transactional
    public Map<String,Object> addRequestAttachment(Identity actor, UUID requestId, String kind, MultipartFile file) {
        UUID branchId=branch(actor);
        if(!List.of("INVOICE","TRANSFER_PROOF").contains(kind)) bad("Loại chứng từ không hợp lệ.");
        Map<String,Object> expenseRequest=request(requestId);
        financialPeriodService.requireOpen(toLocalDate(expenseRequest.get("expense_on")), "bổ sung chứng từ đề nghị chi");
        if(!branchId.equals(expenseRequest.get("branch_id"))) forbidden("Không thể thêm chứng từ cho yêu cầu của cơ sở khác.");
        if("PAID".equals(expenseRequest.get("status"))||"REJECTED".equals(expenseRequest.get("status"))) conflict("Không thể thêm chứng từ khi yêu cầu đã kết thúc.");
        if("TRANSFER_PROOF".equals(kind)&&!"PAYMENT".equals(expenseRequest.get("kind"))) bad("Chứng từ/báo giá chỉ áp dụng cho đề nghị thanh toán.");
        if(attachments(requestId).stream().anyMatch(attachment->kind.equals(attachment.get("kind")))) conflict("Chứng từ này đã được tải lên.");
        attach("EXPENSE_REQUEST",requestId,kind,file,actor.id());
        return request(requestId);
    }

    public List<Map<String,Object>> listRequests(Identity actor) {
        if("HEAD_OFFICE_MANAGER".equals(actor.role())) return requests(null);
        if("ACCOUNTANT".equals(actor.role())) return requests(null);
        return requests(branch(actor));
    }

    @Transactional
    public Map<String,Object> review(Identity actor, UUID requestId, boolean approve, String note) {
        if(!approve&&clean(note,1000).isBlank()) bad("Vui lòng nhập lý do từ chối.");
        Map<String,Object> expenseRequest=request(requestId);
        String status=(String)expenseRequest.get("status");
        String role=actor.role();
        financialPeriodService.requireOpen(toLocalDate(expenseRequest.get("expense_on")), "xét duyệt đề nghị chi hoặc hoàn ứng");
        String decision=approve?"APPROVED_TO_PAY":"REJECTED";
        if("HEAD_OFFICE_MANAGER".equals(role)) {
            if(!"WAITING_HEAD_OFFICE".equals(status)) conflict("Đề nghị này không chờ quản lý cấp cao xét duyệt.");
            financeRepository.reviewAsHeadOffice(requestId,decision,actor.id());
        } else if("ACCOUNTANT".equals(role)) {
            if(!"WAITING_ACCOUNTANT".equals(status)) conflict("Đề nghị này không chờ kế toán xét duyệt.");
            financeRepository.reviewAsAccountant(requestId,decision,actor.id());
        } else forbidden("Bạn không có quyền xét duyệt đề nghị chi.");
        if(!approve) financeRepository.releaseStockReceipts(requestId);
        financeRepository.insertHistory(requestId,actor.id(),approve?"APPROVED":"REJECTED",clean(note,1000));
        return request(requestId);
    }

    @Transactional
    public Map<String,Object> pay(Identity actor, UUID requestId, String reference, MultipartFile proof,
            String paymentMethod, String bankAccount, String bankName,
            Integer allocationMonths, LocalDate allocationStart) {
        requireRole(actor,"ACCOUNTANT");
        return recordApprovedPayment(actor,requestId,reference,proof,paymentMethod,bankAccount,bankName,allocationMonths,allocationStart,true);
    }

    @Transactional
    public Map<String,Object> approveAndPay(Identity actor, UUID requestId, String note, String reference, MultipartFile proof,
            String paymentMethod, String bankAccount, String bankName,
            Integer allocationMonths, LocalDate allocationStart) {
        requireRole(actor,"ACCOUNTANT");
        Map<String,Object> expenseRequest=request(requestId);
        financialPeriodService.requireOpen(toLocalDate(expenseRequest.get("expense_on")), "xét duyệt và thanh toán đề nghị chi hoặc hoàn ứng");
        // BR-90, BR-91: only a request in the accountant queue may be approved and paid with its evidence in one transaction.
        if(!"WAITING_ACCOUNTANT".equals(expenseRequest.get("status"))) conflict("Đề nghị này không chờ kế toán xét duyệt.");
        requirePaymentProof(proof);
        int approvedCount=financeRepository.approveWaitingAccountantRequest(requestId,actor.id());
        if(approvedCount==0) conflict("Đề nghị vừa được xử lý ở phiên khác. Vui lòng tải lại.");
        financeRepository.insertHistory(requestId,actor.id(),"APPROVED",clean(note,1000));
        return recordApprovedPayment(actor,requestId,reference,proof,paymentMethod,bankAccount,bankName,allocationMonths,allocationStart,true);
    }

    private static void requirePaymentProof(MultipartFile proof) {
        if (proof == null || proof.isEmpty())
            bad("Vui lòng đính kèm chứng từ hoặc minh chứng cho khoản đề nghị thanh toán."); // MSG95 / BR-90.
    }

    private Map<String,Object> recordApprovedPayment(Identity actor, UUID requestId, String reference, MultipartFile proof,
            String paidPaymentMethod, String bankAccount, String bankName,
            Integer requestedMonths, LocalDate requestedStart, boolean requireCategoryInvoice) {
        Map<String,Object> expenseRequest=request(requestId);
        financialPeriodService.requireOpen(toLocalDate(expenseRequest.get("expense_on")), "ghi nhận thanh toán đề nghị chi hoặc hoàn ứng");
        if(!"APPROVED_TO_PAY".equals(expenseRequest.get("status"))) conflict("Chỉ có thể ghi nhận đã thanh toán khi đề nghị đã được phê duyệt và việc thanh toán đã được xác nhận."); // MSG97 / BR-207.
        requirePaymentProof(proof);
        if(requireCategoryInvoice&&Boolean.TRUE.equals(expenseRequest.get("invoice_required"))&&attachments(requestId).stream().noneMatch(attachment->"INVOICE".equals(attachment.get("kind"))))
            bad("Hạng mục này bắt buộc có hóa đơn. Vui lòng tải hóa đơn trước khi thanh toán.");
        // BR-90, BR-207: chỉ ghi cách chi thực tế sau khi kế toán xác nhận thanh toán.
        String actualMethod=paymentMethod(paidPaymentMethod);
        String actualBankAccount="BANK".equals(actualMethod)?clean(bankAccount!=null?bankAccount:(String)expenseRequest.get("bank_account"),80):"";
        String actualBankName="BANK".equals(actualMethod)?clean(bankName!=null?bankName:(String)expenseRequest.get("bank_name"),120):"";
        if("BANK".equals(actualMethod)&&(actualBankAccount.isBlank()||actualBankName.isBlank()))
            bad("Vui lòng nhập số tài khoản và ngân hàng trước khi xác nhận chuyển khoản.");

        String recognitionMode=(String)expenseRequest.get("recognition_mode");
        Integer months=null;
        LocalDate firstMonth=null;
        List<ExpenseAllocationCalculator.Line> allocationLines=List.of();
        if("ALLOCATED".equals(recognitionMode)) {
            Object storedMonths=expenseRequest.get("allocation_months");
            months=requestedMonths!=null?requestedMonths:(storedMonths instanceof Number number?number.intValue():null);
            if(months==null) bad("Vui lòng chọn số tháng phân bổ từ 2 đến 60.");
            firstMonth=(requestedStart==null?toLocalDate(expenseRequest.get("expense_on")):requestedStart).withDayOfMonth(1);
            financialPeriodService.requireOpen(firstMonth,"ghi nhận chi phí phân bổ"); // BR-100: kỳ bắt đầu đã khóa thì không ghi nhận trực tiếp.
            try { allocationLines=ExpenseAllocationCalculator.calculate((BigDecimal)expenseRequest.get("amount"),months); }
            catch(IllegalArgumentException invalid) { bad(invalid.getMessage()); }
        } else if(requestedMonths!=null||requestedStart!=null) {
            bad("Hạng mục này ghi nhận ngay và không có lịch phân bổ.");
        }

        financeRepository.lockAccountantFund();
        BigDecimal availableBalance=balance();
        BigDecimal amount=(BigDecimal)expenseRequest.get("amount");
        // BR-206: chi trực tiếp và chi theo đề nghị đều phải nằm trong số dư quỹ kế toán.
        if(availableBalance.compareTo(amount)<0) conflict("Quỹ kế toán hiện còn "+availableBalance.toPlainString()+" đ, không đủ cho khoản "+amount.toPlainString()+" đ. Vui lòng kiểm tra lại nguồn tiền hoặc số tiền."); // MSG93.
        int updatedCount=financeRepository.markRequestPaid(requestId,actor.id(),clean(reference,200),actualMethod,actualBankAccount,actualBankName,months,firstMonth);
        if(updatedCount==0) conflict("Đề nghị vừa được xử lý ở phiên khác. Vui lòng tải lại.");
        if("ALLOCATED".equals(recognitionMode)) {
            for(ExpenseAllocationCalculator.Line line:allocationLines) {
                financeRepository.insertAllocation(requestId,expenseRequest.get("branch_id"),expenseRequest.get("category_id"),
                    firstMonth.plusMonths(line.sequence()-1L),line.sequence(),line.amount());
            }
        }
        if(proof!=null&&!proof.isEmpty()) attach("EXPENSE_REQUEST",requestId,"PAYMENT_PROOF",proof,actor.id());
        financeRepository.insertHistory(requestId,actor.id(),"PAID",clean(reference,1000));
        return request(requestId);
    }

    @Transactional
    public Map<String,Object> createDirectExpense(Identity actor, UUID idempotencyKey, UUID branchId, UUID categoryId,
            BigDecimal amount, LocalDate expenseOn, String description, String beneficiaryName, String bankAccount,
            String bankName, String paymentMethod, String reference, Integer allocationMonths, LocalDate allocationStart,
            MultipartFile proof, MultipartFile invoice) {
        requireRole(actor,"ACCOUNTANT");
        if(idempotencyKey==null) bad("Thiếu mã xác nhận khoản chi trực tiếp. Vui lòng gửi lại biểu mẫu.");
        // Serialize retries with the same key so the unique index resolves to the original result, not a race error.
        financeRepository.lockDirectExpenseKey(idempotencyKey);
        if(branchId==null||!financeRepository.branchExists(branchId))
            bad("Vui lòng chọn cơ sở hợp lệ.");
        if(categoryId==null) bad("Vui lòng chọn hạng mục chi phí đang hoạt động.");
        if(amount==null||amount.signum()<=0) bad("Số tiền phải lớn hơn 0.");
        if(expenseOn==null) bad("Vui lòng chọn ngày chi.");
        if(clean(description,1000).isBlank()) bad("Vui lòng nhập nội dung chi.");
        paymentMethod=paymentMethod(paymentMethod); // BR-90: chi trực tiếp khai báo nguồn tiền khi xác nhận chi.
        if(clean(beneficiaryName,160).isBlank()) bad("Vui lòng nhập người nhận tiền.");
        if("BANK".equals(paymentMethod)&&(clean(bankAccount,80).isBlank()||clean(bankName,120).isBlank()))
            bad("Vui lòng nhập số tài khoản và ngân hàng khi chuyển khoản.");
        bankAccount="BANK".equals(paymentMethod)?clean(bankAccount,80):"";
        bankName="BANK".equals(paymentMethod)?clean(bankName,120):"";
        if(proof==null||proof.isEmpty()) bad("Vui lòng đính kèm chứng từ hoặc minh chứng cho khoản đề nghị thanh toán."); // MSG95 / BR-90.

        List<Map<String,Object>> duplicates=financeRepository.findDirectExpenseByKey(idempotencyKey);
        if(!duplicates.isEmpty()) {
            Map<String,Object> existing=duplicates.get(0);
            boolean sameAllocation;
            if("ALLOCATED".equals(existing.get("recognition_mode"))) {
                LocalDate requestedStart=(allocationStart==null?expenseOn:allocationStart).withDayOfMonth(1);
                sameAllocation=(allocationMonths==null||java.util.Objects.equals(allocationMonths,existing.get("allocation_months")))&&
                    requestedStart.equals(toLocalDate(existing.get("allocation_start")));
            } else {
                sameAllocation=allocationMonths==null&&allocationStart==null;
            }
            boolean sameExpense=branchId.equals(existing.get("branch_id"))&&categoryId.equals(existing.get("category_id"))&&amount.compareTo((BigDecimal)existing.get("amount"))==0&&
                expenseOn.equals(toLocalDate(existing.get("expense_on")))&&clean(description,1000).equals(existing.get("description"))&&
                clean(beneficiaryName,160).equals(existing.get("beneficiary_name"))&&clean(bankAccount,80).equals(existing.get("bank_account"))&&
                clean(bankName,120).equals(existing.get("bank_name"))&&paymentMethod.equals(existing.get("payment_method"))&&clean(reference,200).equals(existing.get("paid_reference"))&&sameAllocation;
            if(!sameExpense) conflict("Mã xác nhận đã được dùng cho khoản chi khác.");
            return request((UUID)existing.get("id"));
        }

        Map<String,Object> category=activeCategory(categoryId);
        String recognitionMode=(String)category.get("recognition_mode");
        Integer months="ALLOCATED".equals(recognitionMode)?(allocationMonths!=null?allocationMonths:((Number)category.get("default_allocation_months")).intValue()):null;
        LocalDate firstMonth="ALLOCATED".equals(recognitionMode)?(allocationStart==null?expenseOn:allocationStart).withDayOfMonth(1):null;
        if("ALLOCATED".equals(recognitionMode)) {
            financialPeriodService.requireOpen(firstMonth,"ghi nhận chi phí phân bổ");
            try { ExpenseAllocationCalculator.calculate(amount,months); }
            catch(IllegalArgumentException invalid) { bad(invalid.getMessage()); }
        } else if(allocationMonths!=null||allocationStart!=null) {
            bad("Hạng mục này ghi nhận ngay và không có lịch phân bổ.");
        }
        financialPeriodService.requireOpen(expenseOn,"ghi nhận chi trực tiếp");
        if(Boolean.TRUE.equals(category.get("invoice_required"))&&(invoice==null||invoice.isEmpty()))
            bad("Hạng mục này bắt buộc có hóa đơn. Vui lòng tải hóa đơn trước khi ghi nhận chi.");

        boolean waitingForHeadOffice=amount.compareTo(DIRECT_EXPENSE_HEAD_OFFICE_APPROVAL_LIMIT)>0;
        String status=waitingForHeadOffice?"WAITING_HEAD_OFFICE":"APPROVED_TO_PAY";
        UUID requestId=UUID.randomUUID();
        financeRepository.insertDirectExpense(requestId,code("EXP"),branchId,categoryId,category.get("cost_type"),recognitionMode,months,
            idempotencyKey,amount,expenseOn,clean(description,1000),clean(beneficiaryName,160),clean(bankAccount,80),clean(bankName,120),
            paymentMethod,actor.id(),status,waitingForHeadOffice);
        financeRepository.insertHistory(requestId,actor.id(),"SUBMITTED","Kế toán ghi nhận chi trực tiếp.");
        if(waitingForHeadOffice) return request(requestId);

        financeRepository.insertHistory(requestId,actor.id(),"APPROVED","Kế toán tự duyệt chi trực tiếp theo ngưỡng hiện hành.");
        if(invoice!=null&&!invoice.isEmpty()) attach("EXPENSE_REQUEST",requestId,"INVOICE",invoice,actor.id());
        return recordApprovedPayment(actor,requestId,reference,proof,paymentMethod,bankAccount,bankName,months,firstMonth,true);
    }

    @Transactional
    public Map<String,Object> recordRecurringExpensePayment(Identity actor, UUID periodId, UUID branchId, UUID categoryId,
            LocalDate dueDate, LocalDate periodMonth, BigDecimal amount, BigDecimal defaultAmount,
            String beneficiaryName, String bankAccount, String bankName, String deviationReason,
            MultipartFile proof, MultipartFile invoice) {
        requireRole(actor,"ACCOUNTANT");
        if(amount==null||amount.signum()<=0||amount.stripTrailingZeros().scale()>0)
            bad("Số tiền chi định kỳ phải là VND nguyên và lớn hơn 0.");
        if(proof==null||proof.isEmpty()) bad("Vui lòng đính kèm chứng từ hoặc minh chứng cho khoản đề nghị thanh toán."); // MSG95 / BR-90.
        if(defaultAmount!=null&&amount.compareTo(defaultAmount)!=0&&clean(deviationReason,1000).isBlank())
            bad("Vui lòng nhập lý do khi số tiền chi khác số tiền mặc định.");

        // BR-100: cả kỳ ghi nhận chi phí và kỳ ngày trả phải còn mở.
        financialPeriodService.requireOpen(periodMonth,"ghi nhận chi phí định kỳ"); // MSG102 / BR-100.
        financialPeriodService.requireOpen(dueDate,"ghi nhận thanh toán khoản chi định kỳ");
        Map<String,Object> category=activeCategory(categoryId); // BR-209: kỳ mới không được dùng danh mục đã ngừng.
        UUID requestId=UUID.randomUUID();
        UUID idempotencyKey=UUID.nameUUIDFromBytes(("GCMB_RECURRING_EXPENSE_PERIOD:"+periodId).getBytes(StandardCharsets.UTF_8));
        String description="Chi định kỳ "+category.get("name")+" · kỳ "+periodMonth.toString().substring(0,7);
        String reason=clean(deviationReason,1000);
        if(!reason.isBlank()) description+=" · Lý do chênh lệch: "+reason;
        financeRepository.insertRecurringExpenseRequest(requestId,code("EXP"),branchId,categoryId,category.get("cost_type"),idempotencyKey,
            amount,dueDate,clean(description,1000),clean(beneficiaryName,160),clean(bankAccount,80),clean(bankName,120),actor.id());
        financeRepository.insertHistory(requestId,actor.id(),"SUBMITTED","Gửi ghi nhận chi định kỳ cho kỳ "+periodMonth.toString().substring(0,7)+".");
        financeRepository.insertHistory(requestId,actor.id(),"APPROVED","Kế toán tự duyệt khoản chi định kỳ trước khi xác nhận thanh toán."); // BR-207.
        if(invoice!=null&&!invoice.isEmpty()) attach("EXPENSE_REQUEST",requestId,"INVOICE",invoice,actor.id());
        // BR-206: dùng chung khóa quỹ, số dư và cập nhật PAID với luồng chi hiện có.
        // Chi định kỳ chỉ yêu cầu chứng từ chuyển khoản; không bắt buộc tải hóa đơn riêng.
        return recordApprovedPayment(actor,requestId,null,proof,"BANK",bankAccount,bankName,null,null,false);
    }

    public Map<String,Object> attachment(Identity actor, UUID attachmentId) {
        Map<String,Object> attachment=financeRepository.findAttachmentWithBranch(attachmentId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy chứng từ."));
        if("BRANCH_MANAGER".equals(actor.role())&&!actor.branchId().equals(attachment.get("branch_id"))) forbidden("Bạn không có quyền xem chứng từ của cơ sở khác.");
        if(!List.of("ACCOUNTANT","HEAD_OFFICE_MANAGER","BRANCH_MANAGER").contains(actor.role())) forbidden("Bạn không có quyền xem chứng từ.");
        try { attachment.put("bytes",equipmentAssetFileStorageService.read((String)attachment.get("object_key"))); return attachment; }
        catch(IOException exception){throw new ResponseStatusException(HttpStatus.NOT_FOUND,exception.getMessage()==null?"Tệp chứng từ không còn trên máy chủ.":exception.getMessage(),exception);}
    }

    public List<Map<String,Object>> history(UUID requestId) {
        return financeRepository.findHistory(requestId);
    }

    public List<Map<String,Object>> attachments(UUID requestId) {
        return financeRepository.findRequestAttachments(requestId);
    }

    public List<Map<String,Object>> allocations(UUID requestId) {
        return financeRepository.findAllocations(requestId);
    }

    private Map<String,Object> request(UUID requestId) {
        Map<String,Object> expenseRequest=financeRepository.findRequest(requestId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đề nghị chi."));
        appendDetails(expenseRequest,requestId);
        return expenseRequest;
    }

    // Dashboard QLCC chỉ cần đề nghị đang chờ duyệt, không tải toàn bộ lịch sử thu chi.
    public List<Map<String,Object>> headOfficePendingRequests(Identity actor) {
        requireRole(actor, "HEAD_OFFICE_MANAGER");
        Set<String> pending=Set.of("WAITING_HEAD_OFFICE","WAITING_APPROVAL","WAITING_MANAGER");
        return withDetails(financeRepository.findRequests(null).stream()
            .filter(expenseRequest->pending.contains(expenseRequest.get("status"))).toList());
    }

    private List<Map<String,Object>> requests(UUID branchId) {
        return withDetails(financeRepository.findRequests(branchId));
    }

    private List<Map<String,Object>> withDetails(List<Map<String,Object>> expenseRequests) {
        // Nạp chi tiết theo lô (4 câu cho cả danh sách) thay vì 4 câu cho mỗi đề nghị.
        List<UUID> ids=expenseRequests.stream().map(expenseRequest->(UUID)expenseRequest.get("id")).toList();
        var attachments=financeRepository.findRequestAttachmentsByRequests(ids);
        var history=financeRepository.findHistoryByRequests(ids);
        var allocations=financeRepository.findAllocationsByRequests(ids);
        var stockReceipts=financeRepository.findLinkedStockReceiptsByRequests(ids);
        for(Map<String,Object> expenseRequest:expenseRequests) {
            UUID id=(UUID)expenseRequest.get("id");
            expenseRequest.put("attachments",attachments.getOrDefault(id,List.of()));
            expenseRequest.put("history",history.getOrDefault(id,List.of()));
            expenseRequest.put("allocations",allocations.getOrDefault(id,List.of()));
            expenseRequest.put("stockReceipts",stockReceipts.getOrDefault(id,List.of()));
        }
        return expenseRequests;
    }

    private void appendDetails(Map<String,Object> expenseRequest,UUID requestId) {
        expenseRequest.put("attachments",attachments(requestId));
        expenseRequest.put("history",history(requestId));
        expenseRequest.put("allocations",allocations(requestId));
        expenseRequest.put("stockReceipts",financeRepository.findLinkedStockReceipts(requestId));
    }

    private BigDecimal balance() {
        return financeRepository.accountantFundBalance();
    }

    private String attach(String ownerType, UUID ownerId, String kind, MultipartFile file, UUID actorId) {
        // HuyDV / BR-115/116: dịch vụ chung lưu Cloudinary khi bật; giữ phân quyền và metadata chứng từ.
        StoredFile stored=equipmentAssetFileStorageService.store(file, "thu-chi");
        try {
            financeRepository.insertAttachment(stored.id(),ownerType,ownerId,kind,stored.objectKey(),stored.originalName(),
                stored.mimeType(),stored.byteSize(),stored.sha256(),actorId);
            return stored.objectKey();
        } catch (RuntimeException failure) {
            equipmentAssetFileStorageService.deleteQuietly(stored.objectKey());
            throw failure;
        }
    }

    private UUID branch(Identity actor) {
        if(actor==null||!"BRANCH_MANAGER".equals(actor.role())||actor.branchId()==null) forbidden("Chỉ quản lý cơ sở được tạo đề nghị trong cơ sở đang quản lý.");
        return actor.branchId();
    }

    private void requireRole(Identity actor,String role) { if(actor==null||!role.equals(actor.role())) forbidden("Bạn không có quyền thực hiện thao tác này."); }
    private static String code(String prefix) { return prefix+"-"+LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.ofPattern("yyMMdd"))+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(); }
    private static LocalDate toLocalDate(Object value) { return value instanceof LocalDate date ? date : ((java.sql.Date)value).toLocalDate(); }
    private static String paymentMethod(String method) {
        if(!"CASH".equals(method)&&!"BANK".equals(method)) bad("Vui lòng chọn tiền mặt hoặc tiền tài khoản.");
        return method;
    }
    private static String clean(String value,int maxLength) { String trimmed=value==null?"":value.trim(); return trimmed.length()>maxLength?trimmed.substring(0,maxLength):trimmed; }
    private static void bad(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static void forbidden(String message){throw new ResponseStatusException(HttpStatus.FORBIDDEN,message);}
    private static void conflict(String message){throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
