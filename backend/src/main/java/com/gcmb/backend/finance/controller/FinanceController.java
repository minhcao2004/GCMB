package com.gcmb.backend.finance.controller;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.finance.service.FinanceService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class FinanceController {
    private final FinanceService financeService;
    private final MutationAudit mutationAudit;
    public FinanceController(FinanceService financeService, MutationAudit mutationAudit) { this.financeService=financeService; this.mutationAudit=mutationAudit; }

    @GetMapping("/head-office/finance")
    public Map<String,Object> headOffice(@AuthenticationPrincipal Identity who) { return financeService.headOfficeSummary(who); }
    @PostMapping("/head-office/finance/fund-transfers")
    public Map<String,Object> sendFund(@AuthenticationPrincipal Identity who,@RequestBody FundTransfer body) {
        return financeService.createFundTransfer(who,body.amount(),body.sentOn(),body.reference());
    }
    @PostMapping(value="/head-office/finance/fund-transfers/{id}/proof",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> fundProof(@AuthenticationPrincipal Identity who,@PathVariable UUID id,
            @RequestPart("proof") MultipartFile proof) { return financeService.addFundTransferProof(who,id,proof); }
    @PostMapping("/head-office/finance/requests/{id}/review")
    public Map<String,Object> headOfficeReview(@AuthenticationPrincipal Identity who,@PathVariable UUID id,@RequestBody Review body) {
        return financeService.review(who,id,body.approve(),body.note());
    }

    @GetMapping("/accounting/finance")
    public Map<String,Object> accounting(@AuthenticationPrincipal Identity who) { return financeService.summary(who); }
    @PostMapping("/accounting/finance/fund-transfers/{id}/confirm")
    public Map<String,Object> confirmFund(@AuthenticationPrincipal Identity who,@PathVariable UUID id) { return financeService.confirmFundTransfer(who,id); }
    @GetMapping("/accounting/finance/categories")
    public Object categories(@AuthenticationPrincipal Identity who) { return financeService.accountantCategories(who); }
    @PostMapping("/accounting/finance/categories")
    public Map<String,Object> createCategory(@AuthenticationPrincipal Identity who,@RequestBody Category body) {
        return mutationAudit.record("CREATE_FINANCE_EXPENSE_CATEGORY","FINANCE_EXPENSE_CATEGORY",null,
            () -> financeService.saveCategory(who,null,body.name(),body.description(),body.categoryGroup(),body.costType(),body.costNature(),
                body.recognitionMode(),body.defaultAllocationMonths(),body.invoiceRequired(),body.reimbursementAllowed()), result -> (UUID) result.get("id"));
    }
    @PutMapping("/accounting/finance/categories/{id}")
    public Map<String,Object> updateCategory(@AuthenticationPrincipal Identity who,@PathVariable UUID id,@RequestBody Category body) {
        return mutationAudit.record("UPDATE_FINANCE_EXPENSE_CATEGORY","FINANCE_EXPENSE_CATEGORY",id,
            () -> financeService.saveCategory(who,id,body.name(),body.description(),body.categoryGroup(),body.costType(),body.costNature(),
                body.recognitionMode(),body.defaultAllocationMonths(),body.invoiceRequired(),body.reimbursementAllowed()), result -> (UUID) result.get("id"));
    }
    @PutMapping("/accounting/finance/categories/{id}/status")
    public Map<String,Object> categoryStatus(@AuthenticationPrincipal Identity who,@PathVariable UUID id,@RequestBody CategoryStatus body) {
        return mutationAudit.record(body.active()?"REACTIVATE_FINANCE_EXPENSE_CATEGORY":"DEACTIVATE_FINANCE_EXPENSE_CATEGORY",
            "FINANCE_EXPENSE_CATEGORY",id,() -> financeService.setCategoryActive(who,id,body.active()), result -> (UUID) result.get("id"));
    }
    @DeleteMapping("/accounting/finance/categories/{id}")
    public void deleteCategory(@AuthenticationPrincipal Identity who,@PathVariable UUID id) {
        mutationAudit.record("DELETE_FINANCE_EXPENSE_CATEGORY","FINANCE_EXPENSE_CATEGORY",id,() -> {
            financeService.deleteCategory(who,id);
            return id;
        }, null);
    }
    @GetMapping("/accounting/finance/requests")
    public Object accountantRequests(@AuthenticationPrincipal Identity who) { return financeService.listRequests(who); }
    @PostMapping("/accounting/finance/requests/{id}/review")
    public Map<String,Object> accountantReview(@AuthenticationPrincipal Identity who,@PathVariable UUID id,@RequestBody Review body) {
        return financeService.review(who,id,body.approve(),body.note());
    }
    @PostMapping(value="/accounting/finance/requests/{id}/pay",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> pay(@AuthenticationPrincipal Identity who,@PathVariable UUID id,
            @RequestParam(required=false) String reference,@RequestPart("proof") MultipartFile proof,
            @RequestParam String paymentMethod,@RequestParam(required=false) String bankAccount,
            @RequestParam(required=false) String bankName,
            @RequestParam(required=false) Integer allocationMonths,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate allocationStart) {
        return financeService.pay(who,id,reference,proof,paymentMethod,bankAccount,bankName,allocationMonths,allocationStart);
    }

    @PostMapping(value="/accounting/finance/requests/direct",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> createDirectExpense(@AuthenticationPrincipal Identity who,
            @RequestPart("command") DirectExpenseRequest body,@RequestPart("proof") MultipartFile proof,
            @RequestPart(value="invoice",required=false) MultipartFile invoice) {
        return mutationAudit.record("CREATE_ACCOUNTANT_DIRECT_EXPENSE","FINANCE_EXPENSE_REQUEST",null,
            () -> financeService.createDirectExpense(who,body.idempotencyKey(),body.branchId(),body.categoryId(),body.amount(),body.expenseOn(),
                body.description(),body.beneficiaryName(),body.bankAccount(),body.bankName(),body.paymentMethod(),body.reference(),body.allocationMonths(),
                body.allocationStart(),proof,invoice),result -> (UUID)result.get("id"));
    }

    @GetMapping("/branch-management/finance")
    public Map<String,Object> branch(@AuthenticationPrincipal Identity who) { return financeService.branchSummary(who); }
    @PostMapping("/branch-management/finance/requests")
    public Map<String,Object> createRequest(@AuthenticationPrincipal Identity who,@RequestBody ExpenseRequest body) {
        return financeService.createRequest(who,body.kind(),body.categoryId(),body.amount(),body.expenseOn(),body.description(),
            body.beneficiaryName(),body.receiptIds());
    }
    @PostMapping(value="/branch-management/finance/requests/{id}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> requestAttachment(@AuthenticationPrincipal Identity who,@PathVariable UUID id,
            @RequestParam String kind,@RequestPart("file") MultipartFile file) {
        return financeService.addRequestAttachment(who,id,kind,file);
    }
    @GetMapping("/branch-management/finance/requests")
    public Object branchRequests(@AuthenticationPrincipal Identity who) { return financeService.listRequests(who); }

    @GetMapping("/finance/attachments/{id}")
    public ResponseEntity<byte[]> attachment(@AuthenticationPrincipal Identity who,@PathVariable UUID id) {
        Map<String,Object> f=financeService.attachment(who,id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)f.get("mime_type")))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename((String)f.get("original_name"),StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff").body((byte[])f.get("bytes"));
    }

    public record Category(String name,String description,String categoryGroup,String costType,String costNature,
            String recognitionMode,Integer defaultAllocationMonths,boolean invoiceRequired,boolean reimbursementAllowed) {}
    public record CategoryStatus(boolean active) {}
    public record Review(boolean approve,String note) {}
    public record FundTransfer(BigDecimal amount,LocalDate sentOn,String reference) {}
    public record ExpenseRequest(String kind,UUID categoryId,BigDecimal amount,LocalDate expenseOn,
            String description,String beneficiaryName,List<UUID> receiptIds) {}
    public record DirectExpenseRequest(UUID idempotencyKey,UUID branchId,UUID categoryId,BigDecimal amount,LocalDate expenseOn,
            String beneficiaryName,String bankAccount,String bankName,String paymentMethod,String description,String reference,
            Integer allocationMonths,LocalDate allocationStart) {}
}
