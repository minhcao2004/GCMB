package com.gcmb.backend.finance.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.finance.repository.RecurringExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RecurringExpenseService {
    private static final ZoneId ZONE = FinancialPeriodService.ZONE;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final FinanceAccess financeAccess;
    private final FinanceService financeService;

    public RecurringExpenseService(RecurringExpenseRepository recurringExpenseRepository, FinanceAccess financeAccess,
            FinanceService financeService) {
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.financeAccess = financeAccess;
        this.financeService = financeService;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> list(Identity actor, UUID branchId, String dueMonthText, String statusText) {
        financeAccess.accountant(actor);
        YearMonth dueMonth = parseMonth(dueMonthText);
        String status = normalizePeriodStatus(statusText);
        LocalDate dueStart = dueMonth.atDay(1);
        LocalDate dueEnd = dueMonth.plusMonths(1).atDay(1);
        List<Map<String,Object>> recurring = recurringExpenseRepository.findTemplates(branchId);
        LocalDate today = LocalDate.now(ZONE);
        recurring.forEach(row -> row.put("next_due_date", nextDueDate(row,today)));

        List<Map<String,Object>> periods = recurringExpenseRepository.findPeriods(branchId,dueStart,dueEnd,status);
        List<Map<String,Object>> notices = notices(actor.id());
        return Map.of("recurringExpenses",recurring,"periods",periods,"notifications",notices,
            "reminderCount",reminderCount(today),"dueMonth",dueMonth.toString());
    }

    @Transactional(readOnly = true)
    public int reminderCount(Identity actor) {
        financeAccess.accountant(actor);
        return reminderCount(LocalDate.now(ZONE));
    }

    public Map<String,Object> save(Identity actor, UUID id, Command input) {
        financeAccess.accountant(actor);
        if(input==null) bad("Dữ liệu khoản chi định kỳ không hợp lệ.");
        String beneficiary=required(input.beneficiaryName(),160,"Vui lòng nhập người nhận.");
        String account=required(input.bankAccount(),80,"Vui lòng nhập số tài khoản.");
        String bank=required(input.bankName(),120,"Vui lòng nhập ngân hàng.");
        if(input.branchId()==null||!recurringExpenseRepository.lockActiveBranch(input.branchId()))
            bad("Vui lòng chọn cơ sở đang hoạt động.");
        if(input.categoryId()==null||!recurringExpenseRepository.lockActiveCategory(input.categoryId()))
            bad("Vui lòng chọn hạng mục chi phí đang hoạt động."); // BR-209.
        if(input.dueDay()==null||input.dueDay()<1||input.dueDay()>31) bad("Ngày trả cố định phải từ 1 đến 31.");
        if(input.reminderDays()!=null&&(input.reminderDays()<0||input.reminderDays()>31)) bad("Số ngày nhắc trước hạn phải từ 0 đến 31.");
        String belongs=input.expenseBelongsTo()==null?"":input.expenseBelongsTo().trim().toUpperCase(Locale.ROOT);
        if(!List.of("SAME_MONTH","PREVIOUS_MONTH").contains(belongs)) bad("Vui lòng chọn tháng ghi nhận chi phí hợp lệ.");
        LocalDate startMonth=firstOfMonth(input.startMonth(),"Vui lòng chọn tháng bắt đầu.");
        LocalDate endMonth=input.endMonth()==null?null:firstOfMonth(input.endMonth(),"Tháng kết thúc không hợp lệ.");
        if(endMonth!=null&&endMonth.isBefore(startMonth)) bad("Tháng kết thúc không được trước tháng bắt đầu.");
        BigDecimal monthlyAmount=optionalWholeAmount(input.monthlyAmount(),"Số tiền mỗi tháng");
        BigDecimal expectedAmount=optionalWholeAmount(input.expectedAmount(),"Số tiền dự kiến");
        int reminderDays=input.reminderDays()==null?3:input.reminderDays();
        String identityKey=input.branchId()+":"+input.categoryId()+":"+beneficiary.toLowerCase(Locale.ROOT);
        lockIdentity(identityKey);
        long duplicates=recurringExpenseRepository.countActiveDuplicates(input.branchId(),input.categoryId(),beneficiary,id);
        if(duplicates>0) conflict("Đã có khoản chi định kỳ đang hoạt động cho cơ sở, hạng mục và người nhận này.");

        UUID savedId;
        if(id==null) {
            savedId=UUID.randomUUID();
            recurringExpenseRepository.insertTemplate(savedId,input.branchId(),input.categoryId(),beneficiary,account,bank,
                input.dueDay(),monthlyAmount,expectedAmount,belongs,startMonth,endMonth,reminderDays,actor.id());
        } else {
            String currentStatus=recurringExpenseRepository.lockTemplateStatus(id).orElseThrow(()->notFound("Không tìm thấy khoản chi định kỳ."));
            if(!"ACTIVE".equals(currentStatus)) conflict("Chỉ sửa được khoản chi định kỳ đang hoạt động.");
            recurringExpenseRepository.updateActiveTemplate(id,input.branchId(),input.categoryId(),beneficiary,account,bank,
                input.dueDay(),monthlyAmount,expectedAmount,belongs,startMonth,endMonth,reminderDays,actor.id());
            savedId=id;
            updatePendingSnapshots(savedId,input.branchId(),input.categoryId(),beneficiary,account,bank,input.dueDay(),
                monthlyAmount,expectedAmount,belongs,reminderDays);
        }
        generateForTemplate(savedId,YearMonth.from(LocalDate.now(ZONE)));
        return template(savedId);
    }

    public Map<String,Object> terminate(Identity actor, UUID id) {
        financeAccess.accountant(actor);
        int updated=recurringExpenseRepository.terminateTemplate(id,actor.id());
        if(updated==0) {
            if(!recurringExpenseRepository.templateExists(id))
                throw notFound("Không tìm thấy khoản chi định kỳ.");
            conflict("Khoản chi định kỳ này đã chấm dứt.");
        }
        // BR-118: chấm dứt mẫu, không xóa mẫu hoặc lịch sử các kỳ đã phát sinh.
        return template(id);
    }

    public Map<String,Object> skipPeriod(Identity actor, UUID id, String reason) {
        financeAccess.accountant(actor);
        String note=required(reason,1000,"Vui lòng nhập lý do bỏ qua kỳ chi.");
        String status=recurringExpenseRepository.lockPeriodStatus(id).orElseThrow(()->notFound("Không tìm thấy kỳ chi định kỳ."));
        if(!"PENDING".equals(status)) conflict("Chỉ bỏ qua được kỳ chi đang chờ thanh toán.");
        recurringExpenseRepository.skipPendingPeriod(id,note);
        return period(id);
    }

    public Map<String,Object> payPeriod(Identity actor, UUID id, BigDecimal amount, String reason,
            MultipartFile proof, MultipartFile invoice) {
        financeAccess.accountant(actor);
        Map<String,Object> row=recurringExpenseRepository.lockPeriodForPayment(id).orElseThrow(()->notFound("Không tìm thấy kỳ chi định kỳ."));
        if("PAID".equals(row.get("status"))) return period(id);
        if(!"PENDING".equals(row.get("status"))) conflict("Kỳ chi này đã được bỏ qua.");
        LocalDate dueDate=day(row.get("due_date"));
        UUID branchId=(UUID)row.get("branch_id");
        if(!recurringExpenseRepository.lockActiveBranch(branchId))
            conflict("Cơ sở của kỳ chi đang tạm ngừng hoạt động.");
        Map<String,Object> request=financeService.recordRecurringExpensePayment(actor,id,branchId,(UUID)row.get("category_id"),
            dueDate,day(row.get("period_month")),amount,(BigDecimal)row.get("default_amount"),
            (String)row.get("beneficiary_name"),(String)row.get("bank_account"),(String)row.get("bank_name"),reason,proof,invoice);
        int updated=recurringExpenseRepository.markPeriodPaid(id,amount,request.get("id"));
        if(updated==0) conflict("Kỳ chi vừa được xử lý ở phiên khác. Vui lòng tải lại.");
        Map<String,Object> result=period(id);
        result.put("request",request);
        return result;
    }

    @Transactional
    public void processDaily() {
        LocalDate today=LocalDate.now(ZONE);
        YearMonth dueMonth=YearMonth.from(today);
        List<Map<String,Object>> active=recurringExpenseRepository.findActiveTemplates();
        for(Map<String,Object> row:active) generateForTemplate(row,dueMonth);

        List<UUID> accountants=recurringExpenseRepository.findActiveAccountantIds();
        if(accountants.isEmpty()) return;
        List<Map<String,Object>> pending=recurringExpenseRepository.findPendingPeriodsToRemind(today);
        for(Map<String,Object> row:pending) createNotices(row,accountants,today);
    }

    private void generateForTemplate(UUID id,YearMonth dueMonth) {
        Map<String,Object> row=recurringExpenseRepository.lockActiveTemplate(id).orElse(null);
        if(row!=null) generateForTemplate(row,dueMonth);
    }

    private void generateForTemplate(Map<String,Object> row,YearMonth dueMonth) {
        generateCycle(row,dueMonth);
        YearMonth nextMonth=dueMonth.plusMonths(1);
        int reminderDays=((Number)row.get("reminder_days")).intValue();
        LocalDate nextDue=RecurringExpenseCalendar.dueDate(nextMonth,((Number)row.get("due_day")).intValue());
        // A first-of-month due date can need a reminder in the prior month.
        if(nextDue.minusDays(reminderDays).isBefore(nextMonth.atDay(1))) generateCycle(row,nextMonth);
    }

    private void generateCycle(Map<String,Object> row,YearMonth dueMonth) {
        String belongs=(String)row.get("expense_belongs_to");
        LocalDate periodMonth=RecurringExpenseCalendar.periodMonth(dueMonth,belongs);
        if(!RecurringExpenseCalendar.withinTerm(periodMonth,day(row.get("start_month")),row.get("end_month")==null?null:day(row.get("end_month")))) return;
        LocalDate dueDate=RecurringExpenseCalendar.dueDate(dueMonth,((Number)row.get("due_day")).intValue());
        recurringExpenseRepository.insertPeriodIfAbsent(row,periodMonth,dueDate);
    }

    private void updatePendingSnapshots(UUID id,UUID branchId,UUID categoryId,String beneficiary,String account,String bank,
            int dueDay,BigDecimal monthlyAmount,BigDecimal expectedAmount,String belongs,int reminderDays) {
        List<Map<String,Object>> pending=recurringExpenseRepository.lockPendingPeriods(id);
        for(Map<String,Object> row:pending) {
            LocalDate periodMonth=day(row.get("period_month"));
            YearMonth dueMonth=YearMonth.from(periodMonth).plusMonths("PREVIOUS_MONTH".equals(belongs)?1:0);
            LocalDate dueDate=RecurringExpenseCalendar.dueDate(dueMonth,dueDay);
            recurringExpenseRepository.updatePendingPeriodSnapshot(row.get("id"),dueDate,branchId,categoryId,beneficiary,account,bank,
                monthlyAmount,expectedAmount,reminderDays);
        }
    }

    private void createNotices(Map<String,Object> period,List<UUID> recipients,LocalDate today) {
        LocalDate dueDate=day(period.get("due_date"));
        String base="Khoản chi định kỳ "+period.get("category_name")+" tại "+period.get("branch_name");
        if(period.get("amount")==null) base+=" chưa có số tiền";
        if(!today.isBefore(dueDate)) {
            insertNotice(period.get("id"),recipients,"DUE",dueDate,base+" đến hạn ngày "+dueDate+".");
            if(today.isAfter(dueDate)) insertNotice(period.get("id"),recipients,"OVERDUE",today,base+" đã quá hạn từ ngày "+dueDate+".");
        } else {
            int reminderDays=((Number)period.get("reminder_days")).intValue();
            LocalDate reminderDate=dueDate.minusDays(reminderDays);
            if(reminderDays>0&&!today.isBefore(reminderDate))
                insertNotice(period.get("id"),recipients,"BEFORE_DUE",reminderDate,base+" sẽ đến hạn ngày "+dueDate+".");
        }
    }

    private void insertNotice(Object periodId,List<UUID> recipients,String type,LocalDate date,String message) {
        for(UUID recipient:recipients) recurringExpenseRepository.insertNoticeIfAbsent(periodId,recipient,type,date,message);
    }

    private List<Map<String,Object>> notices(UUID actorId) {
        return recurringExpenseRepository.findNotices(actorId);
    }

    private int reminderCount(LocalDate today) {
        return recurringExpenseRepository.countPeriodsToRemind(today);
    }

    private Map<String,Object> template(UUID id) {
        return recurringExpenseRepository.findTemplate(id);
    }

    private Map<String,Object> period(UUID id) {
        return recurringExpenseRepository.findPeriod(id);
    }

    private LocalDate nextDueDate(Map<String,Object> row,LocalDate today) {
        if(row.get("next_pending_due_date")!=null) return day(row.get("next_pending_due_date"));
        if(!"ACTIVE".equals(row.get("status"))) return null;
        LocalDate start=day(row.get("start_month"));
        LocalDate end=row.get("end_month")==null?null:day(row.get("end_month"));
        String belongs=(String)row.get("expense_belongs_to");
        YearMonth firstDueMonth=YearMonth.from(start).plusMonths("PREVIOUS_MONTH".equals(belongs)?1:0);
        YearMonth candidate=YearMonth.from(today);
        if(candidate.isBefore(firstDueMonth)) candidate=firstDueMonth;
        int dueDay=((Number)row.get("due_day")).intValue();
        for(int i=0;i<2;i++,candidate=candidate.plusMonths(1)) {
            LocalDate periodMonth=RecurringExpenseCalendar.periodMonth(candidate,belongs);
            if(!RecurringExpenseCalendar.withinTerm(periodMonth,start,end)) continue;
            LocalDate due=RecurringExpenseCalendar.dueDate(candidate,dueDay);
            if(!due.isBefore(today)) return due;
        }
        return null;
    }

    private void lockIdentity(String identityKey) {
        recurringExpenseRepository.lockIdentity(identityKey);
    }

    private static LocalDate day(Object value) {
        if(value==null) return null;
        if(value instanceof LocalDate date) return date;
        return ((java.sql.Date)value).toLocalDate();
    }

    private static LocalDate firstOfMonth(LocalDate value,String message) {
        if(value==null||value.getDayOfMonth()!=1) bad(message);
        return value;
    }

    private static BigDecimal optionalWholeAmount(BigDecimal value,String label) {
        if(value==null) return null;
        if(value.signum()<=0||value.stripTrailingZeros().scale()>0) bad(label+" phải là VND nguyên và lớn hơn 0.");
        return value;
    }

    private static String required(String value,int max,String message) {
        String clean=value==null?"":value.trim();
        if(clean.isBlank()) bad(message);
        return clean.length()>max?clean.substring(0,max):clean;
    }

    private static YearMonth parseMonth(String value) {
        if(value==null||value.isBlank()) return YearMonth.from(LocalDate.now(ZONE));
        try { return YearMonth.parse(value); }
        catch(RuntimeException invalid) { bad("Tháng lọc không hợp lệ."); return YearMonth.from(LocalDate.now(ZONE)); }
    }

    private static String normalizePeriodStatus(String value) {
        String status=value==null||value.isBlank()?"ALL":value.trim().toUpperCase(Locale.ROOT);
        if(!List.of("ALL","PENDING","PAID","SKIPPED").contains(status)) bad("Trạng thái kỳ chi không hợp lệ.");
        return status;
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,message);
    }

    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT,message); }

    public record Command(UUID branchId,UUID categoryId,String beneficiaryName,String bankAccount,String bankName,
            Integer dueDay,BigDecimal monthlyAmount,BigDecimal expectedAmount,String expenseBelongsTo,
            LocalDate startMonth,LocalDate endMonth,Integer reminderDays) {}
}
