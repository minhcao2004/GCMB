package com.gcmb.backend.finance.controller;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.finance.service.FinanceService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
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
    private final FinanceService finance;
    private final MutationAudit audit;
    public FinanceController(FinanceService finance, MutationAudit audit) { this.finance=finance; this.audit=audit; }

    @GetMapping("/head-office/finance")
    public Map<String,Object> headOffice(@AuthenticationPrincipal Identity who) { return finance.headOfficeSummary(who); }
    @PostMapping("/head-office/finance/fund-transfers")
    public Map<String,Object> sendFund(@AuthenticationPrincipal Identity who,@RequestBody FundTransfer body) {
        return finance.createFundTransfer(who,body.amount(),body.sentOn(),body.reference());
    }
    @PostMapping(value="/head-office/finance/fund-transfers/{id}/proof",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> fundProof(@AuthenticationPrincipal Identity who,@PathVariable UUID id,
            @RequestPart("proof") MultipartFile proof) { return finance.addFundTransferProof(who,id,proof); }
    @PostMapping("/head-office/finance/requests/{id}/review")
    public Map<String,Object> headOfficeReview(@AuthenticationPrincipal Identity who,@PathVariable UUID id,@RequestBody Review body) {
        return finance.review(who,id,body.approve(),body.note());
    }

    @GetMapping("/accounting/finance")
    public Map<String,Object> accounting(@AuthenticationPrincipal Identity who) { return finance.summary(who); }
    @PostMapping("/accounting/finance/fund-transfers/{id}/confirm")
    public Map<String,Object> confirmFund(@AuthenticationPrincipal Identity who,@PathVariable UUID id) { return finance.confirmFundTransfer(who,id); }
    @GetMapping("/accounting/finance/categories")
    public Object categories(@AuthenticationPrincipal Identity who) { return finance.accountantCategories(who); }
    @PostMapping("/accounting/finance/categories")
    public Map<String,Object> createCategory(@AuthenticationPrincipal Identity who,@RequestBody Category body) {
        return audit.record("CREATE_FINANCE_EXPENSE_CATEGORY","FINANCE_EXPENSE_CATEGORY",null,
            () -> finance.saveCategory(who,null,body.name(),body.description(),body.costType()), result -> (UUID) result.get("id"));
    }
    @PutMapping("/accounting/finance/categories/{id}")
    public Map<String,Object> updateCategory(@AuthenticationPrincipal Identity who,@PathVariable UUID id,@RequestBody Category body) {
        return audit.record("UPDATE_FINANCE_EXPENSE_CATEGORY","FINANCE_EXPENSE_CATEGORY",id,
            () -> finance.saveCategory(who,id,body.name(),body.description(),body.costType()), result -> (UUID) result.get("id"));
    }
    @PutMapping("/accounting/finance/categories/{id}/status")
    public Map<String,Object> categoryStatus(@AuthenticationPrincipal Identity who,@PathVariable UUID id,@RequestBody CategoryStatus body) {
        return audit.record(body.active()?"REACTIVATE_FINANCE_EXPENSE_CATEGORY":"DEACTIVATE_FINANCE_EXPENSE_CATEGORY",
            "FINANCE_EXPENSE_CATEGORY",id,() -> finance.setCategoryActive(who,id,body.active()), result -> (UUID) result.get("id"));
    }
    @DeleteMapping("/accounting/finance/categories/{id}")
    public void deleteCategory(@AuthenticationPrincipal Identity who,@PathVariable UUID id) {
        audit.record("DELETE_FINANCE_EXPENSE_CATEGORY","FINANCE_EXPENSE_CATEGORY",id,() -> {
            finance.deleteCategory(who,id);
            return id;
        }, null);
    }
    @GetMapping("/accounting/finance/requests")
    public Object accountantRequests(@AuthenticationPrincipal Identity who) { return finance.listRequests(who); }
    @PostMapping("/accounting/finance/requests/{id}/review")
    public Map<String,Object> accountantReview(@AuthenticationPrincipal Identity who,@PathVariable UUID id,@RequestBody Review body) {
        return finance.review(who,id,body.approve(),body.note());
    }
    @PostMapping(value="/accounting/finance/requests/{id}/pay",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> pay(@AuthenticationPrincipal Identity who,@PathVariable UUID id,
            @RequestParam(required=false) String reference,@RequestPart("proof") MultipartFile proof) { return finance.pay(who,id,reference,proof); }

    @GetMapping("/branch-management/finance")
    public Map<String,Object> branch(@AuthenticationPrincipal Identity who) { return finance.branchSummary(who); }
    @PostMapping("/branch-management/finance/requests")
    public Map<String,Object> createRequest(@AuthenticationPrincipal Identity who,@RequestBody ExpenseRequest body) {
        return finance.createRequest(who,body.kind(),body.categoryId(),body.amount(),body.expenseOn(),body.description(),
            body.beneficiaryName(),body.bankAccount(),body.bankName());
    }
    @PostMapping(value="/branch-management/finance/requests/{id}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> requestAttachment(@AuthenticationPrincipal Identity who,@PathVariable UUID id,
            @RequestParam String kind,@RequestPart("file") MultipartFile file) {
        return finance.addRequestAttachment(who,id,kind,file);
    }
    @GetMapping("/branch-management/finance/requests")
    public Object branchRequests(@AuthenticationPrincipal Identity who) { return finance.listRequests(who); }

    @GetMapping("/finance/attachments/{id}")
    public ResponseEntity<byte[]> attachment(@AuthenticationPrincipal Identity who,@PathVariable UUID id) {
        Map<String,Object> f=finance.attachment(who,id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)f.get("mime_type")))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename((String)f.get("original_name"),StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff").body((byte[])f.get("bytes"));
    }

    public record Category(String name,String description,String costType) {}
    public record CategoryStatus(boolean active) {}
    public record Review(boolean approve,String note) {}
    public record FundTransfer(BigDecimal amount,LocalDate sentOn,String reference) {}
    public record ExpenseRequest(String kind,UUID categoryId,BigDecimal amount,LocalDate expenseOn,
            String description,String beneficiaryName,String bankAccount,String bankName) {}
}
