package com.gcmb.backend.finance.controller;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.finance.service.RecurringExpenseService;
import com.gcmb.backend.finance.service.RecurringExpenseService.Command;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/accounting/finance/recurring-expenses")
public class RecurringExpenseController {
    private final RecurringExpenseService recurring;
    private final MutationAudit audit;

    public RecurringExpenseController(RecurringExpenseService recurring,MutationAudit audit) {
        this.recurring=recurring;
        this.audit=audit;
    }

    @GetMapping
    public Map<String,Object> list(@AuthenticationPrincipal Identity actor,
            @RequestParam(required=false) UUID branchId,@RequestParam(required=false) String dueMonth,
            @RequestParam(required=false) String status) {
        return recurring.list(actor,branchId,dueMonth,status);
    }

    @GetMapping("/reminder-count")
    public Map<String,Integer> reminderCount(@AuthenticationPrincipal Identity actor) {
        return Map.of("count",recurring.reminderCount(actor));
    }

    @PostMapping
    public Map<String,Object> create(@AuthenticationPrincipal Identity actor,@RequestBody Command body) {
        return audit.record("CREATE_FINANCE_RECURRING_EXPENSE","FINANCE_RECURRING_EXPENSE",null,
            ()->recurring.save(actor,null,body),result->(UUID)result.get("id"));
    }

    @PutMapping("/{id}")
    public Map<String,Object> update(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,@RequestBody Command body) {
        return audit.record("UPDATE_FINANCE_RECURRING_EXPENSE","FINANCE_RECURRING_EXPENSE",id,
            ()->recurring.save(actor,id,body),result->(UUID)result.get("id"));
    }

    @PostMapping("/{id}/terminate")
    public Map<String,Object> terminate(@AuthenticationPrincipal Identity actor,@PathVariable UUID id) {
        return audit.record("TERMINATE_FINANCE_RECURRING_EXPENSE","FINANCE_RECURRING_EXPENSE",id,
            ()->recurring.terminate(actor,id),result->(UUID)result.get("id"));
    }

    @PostMapping("/periods/{id}/skip")
    public Map<String,Object> skipPeriod(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,@RequestBody SkipCommand body) {
        return audit.record("SKIP_FINANCE_RECURRING_EXPENSE_PERIOD","FINANCE_RECURRING_EXPENSE_PERIOD",id,
            ()->recurring.skipPeriod(actor,id,body==null?null:body.reason()),result->(UUID)result.get("id"));
    }

    @PostMapping(value="/periods/{id}/pay",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> payPeriod(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,
            @RequestPart("command") PayCommand body,@RequestPart("proof") MultipartFile proof,
            @RequestPart(value="invoice",required=false) MultipartFile invoice) {
        return audit.record("PAY_FINANCE_RECURRING_EXPENSE_PERIOD","FINANCE_RECURRING_EXPENSE_PERIOD",id,
            ()->recurring.payPeriod(actor,id,body.amount(),body.reason(),proof,invoice),result->(UUID)result.get("id"));
    }

    public record SkipCommand(String reason) {}
    public record PayCommand(BigDecimal amount,String reason) {}
}
