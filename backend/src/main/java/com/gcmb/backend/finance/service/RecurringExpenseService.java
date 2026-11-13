package com.gcmb.backend.finance.service;

import com.gcmb.backend.auth.dto.Identity;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RecurringExpenseService {
    private static final ZoneId ZONE = FinancialPeriodService.ZONE;
    private final JdbcTemplate db;
    private final FinanceAccess access;
    private final FinanceService finance;

    public RecurringExpenseService(JdbcTemplate db, FinanceAccess access, FinanceService finance) {
        this.db = db;
        this.access = access;
        this.finance = finance;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> list(Identity actor, UUID branchId, String dueMonthText, String statusText) {
        access.accountant(actor);
        YearMonth dueMonth = parseMonth(dueMonthText);
        String status = normalizePeriodStatus(statusText);
        LocalDate dueStart = dueMonth.atDay(1);
        LocalDate dueEnd = dueMonth.plusMonths(1).atDay(1);
        List<Map<String,Object>> recurring = db.queryForList("""
            SELECT r.id,r.branch_id,b.name AS branch_name,r.category_id,c.code AS category_code,c.name AS category_name,
              c.invoice_required,r.beneficiary_name,r.bank_account,r.bank_name,r.due_day,r.monthly_amount,r.expected_amount,
              r.expense_belongs_to,r.start_month,r.end_month,r.reminder_days,r.status,r.created_at,r.updated_at,r.terminated_at,
              (SELECT min(p.due_date) FROM gcmb.finance_recurring_expense_period p
                WHERE p.recurring_id=r.id AND p.status='PENDING') AS next_pending_due_date
            FROM gcmb.finance_recurring_expense r
            JOIN gcmb.branch b ON b.id=r.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=r.category_id
            WHERE (?::uuid IS NULL OR r.branch_id=?)
            ORDER BY CASE r.status WHEN 'ACTIVE' THEN 0 ELSE 1 END,r.due_day,c.name,b.name,r.beneficiary_name
            """,branchId,branchId);
        LocalDate today = LocalDate.now(ZONE);
        recurring.forEach(row -> row.put("next_due_date", nextDueDate(row,today)));

        List<Map<String,Object>> periods = db.queryForList("""
            SELECT p.id,p.recurring_id,p.period_month,p.due_date,p.branch_id,b.name AS branch_name,
              p.category_id,c.code AS category_code,c.name AS category_name,c.invoice_required,
              p.beneficiary_name,p.bank_account,p.bank_name,p.amount,p.default_amount,p.expected_amount,
              COALESCE(p.amount,p.expected_amount,(
                SELECT previous.amount FROM gcmb.finance_recurring_expense_period previous
                WHERE previous.recurring_id=p.recurring_id AND previous.status='PAID'
                  AND previous.period_month<p.period_month
                ORDER BY previous.period_month DESC LIMIT 1
              )) AS suggested_amount,
              p.reminder_days,p.status,p.request_id,rpay.code AS request_code,p.skip_reason,p.created_at,p.updated_at,
              r.monthly_amount AS current_default_amount,r.status AS recurring_status
            FROM gcmb.finance_recurring_expense_period p
            JOIN gcmb.finance_recurring_expense r ON r.id=p.recurring_id
            JOIN gcmb.branch b ON b.id=p.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=p.category_id
            LEFT JOIN gcmb.finance_expense_request rpay ON rpay.id=p.request_id
            WHERE (?::uuid IS NULL OR p.branch_id=?) AND p.due_date>=? AND p.due_date<?
              AND (?='ALL' OR p.status=?)
            ORDER BY CASE p.status WHEN 'PENDING' THEN 0 WHEN 'SKIPPED' THEN 1 ELSE 2 END,p.due_date,p.period_month,p.id
            """,branchId,branchId,dueStart,dueEnd,status,status);
        List<Map<String,Object>> notices = notices(actor.id());
        return Map.of("recurringExpenses",recurring,"periods",periods,"notifications",notices,
            "reminderCount",reminderCount(today),"dueMonth",dueMonth.toString());
    }

    @Transactional(readOnly = true)
    public int reminderCount(Identity actor) {
        access.accountant(actor);
        return reminderCount(LocalDate.now(ZONE));
    }

    public Map<String,Object> save(Identity actor, UUID id, Command input) {
        access.accountant(actor);
        if(input==null) bad("Dữ liệu khoản chi định kỳ không hợp lệ.");
        String beneficiary=required(input.beneficiaryName(),160,"Vui lòng nhập người nhận.");
        String account=required(input.bankAccount(),80,"Vui lòng nhập số tài khoản.");
        String bank=required(input.bankName(),120,"Vui lòng nhập ngân hàng.");
        if(input.branchId()==null||db.queryForList("SELECT id FROM gcmb.branch WHERE id=? AND status='ACTIVE' FOR SHARE",UUID.class,input.branchId()).isEmpty())
            bad("Vui lòng chọn cơ sở đang hoạt động.");
        if(input.categoryId()==null||db.queryForList("SELECT id FROM gcmb.finance_expense_category WHERE id=? AND active=true FOR SHARE",UUID.class,input.categoryId()).isEmpty())
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
        Long duplicates=db.queryForObject("""
            SELECT count(*) FROM gcmb.finance_recurring_expense
            WHERE status='ACTIVE' AND branch_id=? AND category_id=? AND lower(btrim(beneficiary_name))=lower(btrim(?))
              AND (?::uuid IS NULL OR id<>?)
            """,Long.class,input.branchId(),input.categoryId(),beneficiary,id,id);
        if(duplicates!=null&&duplicates>0) conflict("Đã có khoản chi định kỳ đang hoạt động cho cơ sở, hạng mục và người nhận này.");

        UUID savedId;
        if(id==null) {
            savedId=UUID.randomUUID();
            db.update("""
                INSERT INTO gcmb.finance_recurring_expense(id,branch_id,category_id,beneficiary_name,bank_account,bank_name,
                  due_day,monthly_amount,expected_amount,expense_belongs_to,start_month,end_month,reminder_days,status,created_by,updated_by)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?)
                """,savedId,input.branchId(),input.categoryId(),beneficiary,account,bank,input.dueDay(),monthlyAmount,
                expectedAmount,belongs,startMonth,endMonth,reminderDays,actor.id(),actor.id());
        } else {
            String currentStatus=db.query("SELECT status FROM gcmb.finance_recurring_expense WHERE id=? FOR UPDATE",
                (rs,n)->rs.getString(1),id).stream().findFirst().orElseThrow(()->notFound("Không tìm thấy khoản chi định kỳ."));
            if(!"ACTIVE".equals(currentStatus)) conflict("Chỉ sửa được khoản chi định kỳ đang hoạt động.");
            db.update("""
                UPDATE gcmb.finance_recurring_expense SET branch_id=?,category_id=?,beneficiary_name=?,bank_account=?,bank_name=?,
                  due_day=?,monthly_amount=?,expected_amount=?,expense_belongs_to=?,start_month=?,end_month=?,reminder_days=?,
                  updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='ACTIVE'
                """,input.branchId(),input.categoryId(),beneficiary,account,bank,input.dueDay(),monthlyAmount,expectedAmount,
                belongs,startMonth,endMonth,reminderDays,actor.id(),id);
            savedId=id;
            updatePendingSnapshots(savedId,input.branchId(),input.categoryId(),beneficiary,account,bank,input.dueDay(),
                monthlyAmount,expectedAmount,belongs,reminderDays);
        }
        generateForTemplate(savedId,YearMonth.from(LocalDate.now(ZONE)));
        return template(savedId);
    }

    public Map<String,Object> terminate(Identity actor, UUID id) {
        access.accountant(actor);
        int updated=db.update("""
            UPDATE gcmb.finance_recurring_expense SET status='TERMINATED',terminated_at=CURRENT_TIMESTAMP,
              updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='ACTIVE'
            """,actor.id(),id);
        if(updated==0) {
            if(db.queryForList("SELECT id FROM gcmb.finance_recurring_expense WHERE id=?",UUID.class,id).isEmpty())
                throw notFound("Không tìm thấy khoản chi định kỳ.");
            conflict("Khoản chi định kỳ này đã chấm dứt.");
        }
        // BR-118: chấm dứt mẫu, không xóa mẫu hoặc lịch sử các kỳ đã phát sinh.
        return template(id);
    }

    public Map<String,Object> skipPeriod(Identity actor, UUID id, String reason) {
        access.accountant(actor);
        String note=required(reason,1000,"Vui lòng nhập lý do bỏ qua kỳ chi.");
        String status=db.query("SELECT status FROM gcmb.finance_recurring_expense_period WHERE id=? FOR UPDATE",
            (rs,n)->rs.getString(1),id).stream().findFirst().orElseThrow(()->notFound("Không tìm thấy kỳ chi định kỳ."));
        if(!"PENDING".equals(status)) conflict("Chỉ bỏ qua được kỳ chi đang chờ thanh toán.");
        db.update("UPDATE gcmb.finance_recurring_expense_period SET status='SKIPPED',skip_reason=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'",
            note,id);
        return period(id);
    }

    public Map<String,Object> payPeriod(Identity actor, UUID id, BigDecimal amount, String reason,
            MultipartFile proof, MultipartFile invoice) {
        access.accountant(actor);
        Map<String,Object> row=db.queryForList("""
            SELECT p.*,r.status AS recurring_status,c.name AS category_name
            FROM gcmb.finance_recurring_expense_period p
            JOIN gcmb.finance_recurring_expense r ON r.id=p.recurring_id
            JOIN gcmb.finance_expense_category c ON c.id=p.category_id
            WHERE p.id=? FOR UPDATE OF p
            """,id).stream().findFirst().orElseThrow(()->notFound("Không tìm thấy kỳ chi định kỳ."));
        if("PAID".equals(row.get("status"))) return period(id);
        if(!"PENDING".equals(row.get("status"))) conflict("Kỳ chi này đã được bỏ qua.");
        LocalDate dueDate=day(row.get("due_date"));
        UUID branchId=(UUID)row.get("branch_id");
        if(db.queryForList("SELECT id FROM gcmb.branch WHERE id=? AND status='ACTIVE' FOR SHARE",UUID.class,branchId).isEmpty())
            conflict("Cơ sở của kỳ chi đang tạm ngừng hoạt động.");
        Map<String,Object> request=finance.recordRecurringExpensePayment(actor,id,branchId,(UUID)row.get("category_id"),
            dueDate,day(row.get("period_month")),amount,(BigDecimal)row.get("default_amount"),
            (String)row.get("beneficiary_name"),(String)row.get("bank_account"),(String)row.get("bank_name"),reason,proof,invoice);
        int updated=db.update("""
            UPDATE gcmb.finance_recurring_expense_period
            SET amount=?,status='PAID',request_id=?,updated_at=CURRENT_TIMESTAMP
            WHERE id=? AND status='PENDING'
            """,amount,request.get("id"),id);
        if(updated==0) conflict("Kỳ chi vừa được xử lý ở phiên khác. Vui lòng tải lại.");
        Map<String,Object> result=period(id);
        result.put("request",request);
        return result;
    }

    @Transactional
    public void processDaily() {
        LocalDate today=LocalDate.now(ZONE);
        YearMonth dueMonth=YearMonth.from(today);
        List<Map<String,Object>> active=db.queryForList("""
            SELECT id,branch_id,category_id,beneficiary_name,bank_account,bank_name,due_day,monthly_amount,expected_amount,
              expense_belongs_to,start_month,end_month,reminder_days
            FROM gcmb.finance_recurring_expense WHERE status='ACTIVE' ORDER BY id
            """);
        for(Map<String,Object> row:active) generateForTemplate(row,dueMonth);

        List<UUID> accountants=db.query("""
            SELECT u.id FROM gcmb.user_account u JOIN gcmb.role ro ON ro.id=u.role_id
            WHERE u.status='ACTIVE' AND ro.code='ACCOUNTANT' ORDER BY u.id
            """,(rs,n)->rs.getObject(1,UUID.class));
        if(accountants.isEmpty()) return;
        List<Map<String,Object>> pending=db.queryForList("""
            SELECT p.id,p.due_date,p.period_month,p.amount,p.reminder_days,b.name AS branch_name,c.name AS category_name
            FROM gcmb.finance_recurring_expense_period p
            JOIN gcmb.branch b ON b.id=p.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=p.category_id
            WHERE p.status='PENDING' AND p.due_date<=?::date+p.reminder_days
            ORDER BY p.due_date,p.id
            """,today);
        for(Map<String,Object> row:pending) createNotices(row,accountants,today);
    }

    private void generateForTemplate(UUID id,YearMonth dueMonth) {
        Map<String,Object> row=db.queryForList("""
            SELECT id,branch_id,category_id,beneficiary_name,bank_account,bank_name,due_day,monthly_amount,expected_amount,
              expense_belongs_to,start_month,end_month,reminder_days
            FROM gcmb.finance_recurring_expense WHERE id=? AND status='ACTIVE' FOR UPDATE
            """,id).stream().findFirst().orElse(null);
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
        db.update("""
            INSERT INTO gcmb.finance_recurring_expense_period(id,recurring_id,period_month,due_date,branch_id,category_id,
              beneficiary_name,bank_account,bank_name,amount,default_amount,expected_amount,reminder_days)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(recurring_id,period_month) DO NOTHING
            """,UUID.randomUUID(),row.get("id"),periodMonth,dueDate,row.get("branch_id"),row.get("category_id"),
            row.get("beneficiary_name"),row.get("bank_account"),row.get("bank_name"),row.get("monthly_amount"),
            row.get("monthly_amount"),row.get("expected_amount"),row.get("reminder_days"));
    }

    private void updatePendingSnapshots(UUID id,UUID branchId,UUID categoryId,String beneficiary,String account,String bank,
            int dueDay,BigDecimal monthlyAmount,BigDecimal expectedAmount,String belongs,int reminderDays) {
        List<Map<String,Object>> pending=db.queryForList("SELECT id,period_month FROM gcmb.finance_recurring_expense_period WHERE recurring_id=? AND status='PENDING' FOR UPDATE",id);
        for(Map<String,Object> row:pending) {
            LocalDate periodMonth=day(row.get("period_month"));
            YearMonth dueMonth=YearMonth.from(periodMonth).plusMonths("PREVIOUS_MONTH".equals(belongs)?1:0);
            LocalDate dueDate=RecurringExpenseCalendar.dueDate(dueMonth,dueDay);
            db.update("""
                UPDATE gcmb.finance_recurring_expense_period SET due_date=?,branch_id=?,category_id=?,beneficiary_name=?,
                  bank_account=?,bank_name=?,amount=?,default_amount=?,expected_amount=?,reminder_days=?,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='PENDING'
                """,dueDate,branchId,categoryId,beneficiary,account,bank,monthlyAmount,monthlyAmount,expectedAmount,reminderDays,row.get("id"));
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
        for(UUID recipient:recipients) db.update("""
            INSERT INTO gcmb.finance_recurring_expense_notice(period_id,recipient_id,reminder_type,reminder_date,message)
            VALUES(?,?,?,?,?) ON CONFLICT(period_id,recipient_id,reminder_type,reminder_date) DO NOTHING
            """,periodId,recipient,type,date,message);
    }

    private List<Map<String,Object>> notices(UUID actorId) {
        return db.queryForList("""
            SELECT n.id,n.period_id,n.reminder_type,n.reminder_date,n.message,n.created_at,
              p.period_month,p.due_date,p.status AS period_status,b.name AS branch_name,c.name AS category_name
            FROM gcmb.finance_recurring_expense_notice n
            JOIN gcmb.finance_recurring_expense_period p ON p.id=n.period_id
            JOIN gcmb.branch b ON b.id=p.branch_id JOIN gcmb.finance_expense_category c ON c.id=p.category_id
            WHERE n.recipient_id=? ORDER BY n.created_at DESC,n.id DESC LIMIT 30
            """,actorId);
    }

    private int reminderCount(LocalDate today) {
        return db.queryForObject("SELECT count(*) FROM gcmb.finance_recurring_expense_period WHERE status='PENDING' AND due_date<=?::date+reminder_days",
            Integer.class,today);
    }

    private Map<String,Object> template(UUID id) {
        return db.queryForMap("""
            SELECT r.*,b.name AS branch_name,c.code AS category_code,c.name AS category_name,c.invoice_required,
              (SELECT min(p.due_date) FROM gcmb.finance_recurring_expense_period p
                WHERE p.recurring_id=r.id AND p.status='PENDING') AS next_pending_due_date
            FROM gcmb.finance_recurring_expense r JOIN gcmb.branch b ON b.id=r.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=r.category_id WHERE r.id=?
            """,id);
    }

    private Map<String,Object> period(UUID id) {
        return db.queryForMap("""
            SELECT p.*,b.name AS branch_name,c.name AS category_name,c.invoice_required
            FROM gcmb.finance_recurring_expense_period p JOIN gcmb.branch b ON b.id=p.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=p.category_id WHERE p.id=?
            """,id);
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
        db.query("SELECT pg_advisory_xact_lock(1300261105,hashtext(?))",
            (ResultSet rs)->{rs.next();rs.getObject(1);return null;},identityKey);
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
