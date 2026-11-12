/** Author: HuyDV. Bổ sung theo yêu cầu: ca trước giao tiền, ca sau nhận, không phát sinh thu/chi.
 * Dùng nguồn tiền UC-98/BR-95 và phạm vi chỉ xem BR-177; chưa có mã BR riêng cho bàn giao ca. */
package com.gcmb.backend.finance.service;

import static com.gcmb.backend.finance.service.FinanceAccess.*;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class ShiftCashHandoverService {
  private static final int[] HOURS = {0,7,12,18,24};
  private final JdbcTemplate db;
  private final FinanceAccess access;
  private final CashClosingService cash;
  private final ObjectMapper json;

  public ShiftCashHandoverService(JdbcTemplate db, FinanceAccess access, CashClosingService cash, ObjectMapper json) {
    this.db=db;
    this.access=access;
    this.cash=cash;
    this.json=json;
  }

  public record Receive(LocalDate date, int shift, BigDecimal actual, String note, boolean confirmed, String revision) {}
  public record Resolution(String note) {}

  private UUID branch(Identity actor, boolean write) {
    if (actor!=null && "BRANCH_MANAGER".equals(actor.role())) return access.branch(actor,write);
    access.role(actor,"RECEPTIONIST");
    if (actor.branchId()==null || !Boolean.TRUE.equals(db.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM gcmb.user_account WHERE id=? AND branch_id=? AND status='ACTIVE')
        """,Boolean.class,actor.id(),actor.branchId())))
      throw new AuthFailure(403,"form","Bạn không có quyền truy cập két của cơ sở này.");
    String status=db.queryForObject("SELECT status FROM gcmb.branch WHERE id=?"+(write?" FOR SHARE":""),
      String.class,actor.branchId());
    if (write && !"ACTIVE".equals(status)) throw new AuthFailure(403,"form",
      "Cơ sở đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi."); // BR-177/MSG43.
    return actor.branchId();
  }

  private ZoneId zone(UUID branch) {
    return ZoneId.of(db.queryForObject("SELECT timezone FROM gcmb.branch WHERE id=?",String.class,branch));
  }

  private LocalDate date(LocalDate input, ZoneId zone) {
    LocalDate day=input==null?LocalDate.now(zone):input;
    if (day.getYear()<2000 || day.isAfter(LocalDate.now(zone))) throw bad("Ngày bàn giao không hợp lệ hoặc ở tương lai.");
    return day;
  }

  private Instant end(LocalDate date, int shift, ZoneId zone) {
    if (shift<1 || shift>4) throw bad("Vui lòng chọn ca từ 1 đến 4.");
    return date.atStartOfDay().plusHours(HOURS[shift]).atZone(zone).toInstant();
  }

  // HuyDV / UC-85/86: giao và nhận đều phải thuộc đúng ca, không dùng quyền đăng nhập thay phân công.
  private boolean assignedToIncoming(Identity actor, UUID branch, LocalDate day, int outgoing, ZoneId zone) {
    LocalDate receivingDay=outgoing==4?day.plusDays(1):day;
    int incoming=outgoing==4?1:outgoing+1;
    return assigned(actor,branch,receivingDay,incoming,zone);
  }

  private boolean assigned(Identity actor, UUID branch, LocalDate day, int shift, ZoneId zone) {
    if (!"RECEPTIONIST".equals(actor.role())) return false;
    Instant starts=day.atStartOfDay().plusHours(HOURS[shift-1]).atZone(zone).toInstant();
    Instant ends=end(day,shift,zone);
    return Boolean.TRUE.equals(db.queryForObject("""
      SELECT EXISTS(
        SELECT 1 FROM gcmb.shift_assignment a
        JOIN gcmb.scheduled_shift s ON s.id=a.shift_id
        JOIN gcmb.employee e ON e.id=a.employee_id
        WHERE e.user_id=? AND e.status='ACTIVE' AND e.position='RECEPTIONIST'
          AND a.status='CONFIRMED' AND s.status<>'CANCELLED'
          AND s.branch_id=? AND s.work_date=? AND s.starts_at=? AND s.ends_at=?
      )
      """,Boolean.class,actor.id(),branch,java.sql.Date.valueOf(day),
      Timestamp.from(starts),Timestamp.from(ends)));
  }

  private List<Map<String,Object>> records(UUID branch, LocalDate date, boolean pending, boolean dispatch) {
    String table=dispatch?"shift_cash_dispatch":"shift_cash_handover";
    String user=dispatch?"handed_by":"received_by";
    var result=db.queryForList("SELECT h.*,coalesce(e.full_name,u.username) AS actor_name, "
      +"coalesce(me.full_name,m.username) AS resolver FROM gcmb."+table+" h "
      +"JOIN gcmb.user_account u ON u.id=h."+user+" "
      +"LEFT JOIN gcmb.user_account m ON m.id=h.resolved_by "
      +"LEFT JOIN gcmb.employee e ON e.user_id=u.id "
      +"LEFT JOIN gcmb.employee me ON me.user_id=m.id WHERE h.branch_id=? AND "
      +(pending?" h.status='DISCREPANCY' ":" h.business_date=? ")+" ORDER BY h.business_date,h.shift_number",
      pending?new Object[]{branch}:new Object[]{branch,java.sql.Date.valueOf(date)});
    ZoneId zone=zone(branch);
    for (var record:result) {
      LocalDate day=((java.sql.Date)record.get("business_date")).toLocalDate();
      int outgoing=((Number)record.get("shift_number")).intValue();
      LocalDate receivingDay=dispatch?day:outgoing==4?day.plusDays(1):day;
      int incoming=dispatch?outgoing:outgoing==4?1:outgoing+1;
      Instant starts=receivingDay.atStartOfDay().plusHours(HOURS[incoming-1]).atZone(zone).toInstant();
      Timestamp at=(Timestamp)record.get(dispatch?"handed_at":"received_at");
      // Giữ bản ghi cũ; đối chiếu phân công tại thời điểm nhận, kể cả ca được thay người sau đó.
      boolean verified=Boolean.TRUE.equals(db.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM gcmb.shift_assignment a
          JOIN gcmb.scheduled_shift s ON s.id=a.shift_id JOIN gcmb.employee e ON e.id=a.employee_id
          WHERE e.user_id=? AND s.branch_id=? AND s.work_date=? AND s.starts_at=? AND s.ends_at=?
            AND a.created_at<=? AND (a.status='CONFIRMED' OR (a.status='CANCELLED' AND a.cancelled_at>?)))
        """,Boolean.class,record.get(user),branch,java.sql.Date.valueOf(receivingDay),
        Timestamp.from(starts),Timestamp.from(end(receivingDay,incoming,zone)),at,at));
      record.put("assignmentVerified",verified);
      record.put("stage",dispatch?"DISPATCH":"RECEIVE");
    }
    return result;
  }

  @Transactional(readOnly=true)
  public Map<String,Object> overview(Identity actor, LocalDate input) {
    UUID branch=branch(actor,false);
    ZoneId zone=zone(branch);
    LocalDate day=date(input,zone);
    var records=records(branch,day,false,false);
    var dispatches=records(branch,day,false,true);
    var shifts=new ArrayList<Map<String,Object>>();
    for (int shift=1;shift<=4;shift++) {
      Instant cutoff=end(day,shift,zone);
      boolean ended=!Instant.now().isBefore(cutoff);
      final int number=shift;
      var existing=records.stream().filter(r->((Number)r.get("shift_number")).intValue()==number).findFirst().orElse(null);
      var dispatch=dispatches.stream().filter(r->((Number)r.get("shift_number")).intValue()==number).findFirst().orElse(null);
      var row=new LinkedHashMap<String,Object>();
      row.put("shift",shift);
      row.put("hours",String.format("%02d:00–%02d:00",HOURS[shift-1],HOURS[shift]%24));
      row.put("ended",ended);
      row.put("receiveShift",shift==4?1:shift+1);
      row.put("receiveDate",(shift==4?day.plusDays(1):day).toString());
      row.put("canDispatch",ended && existing==null && dispatch==null && assigned(actor,branch,day,shift,zone));
      row.put("canReceive",ended && existing==null && dispatch!=null && assignedToIncoming(actor,branch,day,shift,zone));
      row.put("record",existing);
      row.put("dispatch",dispatch);
      if (dispatch!=null) {
        row.put("receiveExpected",dispatch.get("actual_amount"));
        row.put("receiveRevision",dispatch.get("id").toString());
      }
      if (ended && existing==null && dispatch==null) {
        var source=cash.sourceAt(branch,day,cutoff);
        row.put("expected",source.get("expected"));
        row.put("opening",source.get("opening"));
        row.put("cashIn",source.get("cashIn"));
        row.put("cashOut",source.get("cashOut"));
        row.put("revision",source.get("revision"));
      }
      shifts.add(row);
    }
    var pending=new ArrayList<>(records(branch,day,true,true));
    pending.addAll(records(branch,day,true,false));
    return Map.of("date",day.toString(),"shifts",shifts,"pending",pending);
  }

  public Map<String,Object> dispatch(Identity actor, Receive input) {
    return count(actor,input,true);
  }

  public Map<String,Object> receive(Identity actor, Receive input) {
    return count(actor,input,false);
  }

  private Map<String,Object> count(Identity actor, Receive input, boolean dispatch) {
    // Khóa tiền trước hồ sơ như AccountProfileService; giữ khóa phân ca đến khi ghi nhận xong.
    access.lock();
    db.execute("SELECT pg_advisory_xact_lock(1300261013)");
    UUID branch=branch(actor,true);
    access.role(actor,"RECEPTIONIST");
    ZoneId zone=zone(branch);
    LocalDate day=date(input.date(),zone);
    Instant cutoff=end(day,input.shift(),zone);
    if (Instant.now().isBefore(cutoff)) throw conflict("Ca chưa kết thúc. Chỉ giao hoặc nhận tiền khi đã chuyển ca.");
    if (!(dispatch?assigned(actor,branch,day,input.shift(),zone):assignedToIncoming(actor,branch,day,input.shift(),zone)))
      throw new AuthFailure(403,"form",
        dispatch?"Bạn không được phân công ca giao nên không thể bàn giao tiền mặt."
          :"Bạn không được phân công ca nhận tiếp theo nên không thể nhận bàn giao này."); // MSG24.
    if (!input.confirmed()) throw bad("Vui lòng xác nhận đã kiểm đếm tiền mặt thực tế.");
    if (Boolean.TRUE.equals(db.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM gcmb.shift_cash_handover WHERE branch_id=? AND business_date=? AND shift_number=?)
        """,Boolean.class,branch,java.sql.Date.valueOf(day),input.shift())))
      throw conflict("Ca này đã xác nhận bàn giao. Vui lòng tải lại để xem kết quả.");
    if (Boolean.TRUE.equals(db.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM gcmb.financial_period WHERE month=? AND status='CLOSED')
        """,Boolean.class,java.sql.Date.valueOf(day.withDayOfMonth(1)))))
      throw conflict("Kỳ tài chính đã chốt, không thể bổ sung bàn giao.");
    var dispatches=db.queryForList("SELECT * FROM gcmb.shift_cash_dispatch WHERE branch_id=? AND business_date=? AND shift_number=?",
      branch,java.sql.Date.valueOf(day),input.shift());
    if (dispatch && !dispatches.isEmpty()) throw conflict("Ca này đã gửi bàn giao. Vui lòng tải lại để xem kết quả.");
    if (!dispatch && dispatches.isEmpty()) throw conflict("Ca trước chưa gửi bàn giao tiền mặt. Vui lòng liên hệ nhân viên ca giao.");
    Map<String,Object> source;
    if (dispatch) source=cash.sourceAt(branch,day,cutoff);
    else {
      source=new LinkedHashMap<>(dispatches.get(0));
      source.put("expected",source.get("actual_amount"));
      source.put("revision",source.get("id").toString());
    }
    if (!Objects.equals(input.revision(),source.get("revision")))
      throw conflict("Số liệu bàn giao đã thay đổi. Vui lòng tải lại và kiểm đếm trước khi xác nhận.");
    BigDecimal expected=new BigDecimal(source.get("expected").toString());
    BigDecimal actual=money(input.actual(),"Tiền mặt thực đếm",false);
    boolean matched=actual.compareTo(expected)==0;
    String note=text(input.note(),"Lý do chênh lệch",!matched);
    UUID id=UUID.randomUUID();
    String table=dispatch?"shift_cash_dispatch":"shift_cash_handover";
    db.update("INSERT INTO gcmb."+table+"(id,branch_id,business_date,shift_number,expected_amount,"
      +"actual_amount,source_revision,source_snapshot,note,status,"+(dispatch?"handed_by":"received_by")+") "
      +"VALUES (?,?,?,?,?,?,?::uuid,?::jsonb,?,?,?)",
      id,branch,java.sql.Date.valueOf(day),input.shift(),expected,actual,input.revision(),
      json.writeValueAsString(source),note,matched?"MATCHED":"DISCREPANCY",actor.id());
    var result=db.queryForMap("SELECT * FROM gcmb."+table+" WHERE id=?",id);
    access.audit(actor,branch,id,dispatch?"SHIFT_CASH_DISPATCHED":"SHIFT_CASH_RECEIVED",null,result);
    return result;
  }

  public Map<String,Object> resolve(Identity actor, UUID id, Resolution input) {
    access.lock();
    UUID branch=access.branch(actor,true);
    String note=text(input.note(),"Kết quả xử lý chênh lệch",true);
    for (String table:List.of("shift_cash_dispatch","shift_cash_handover")) {
      int count=db.update("UPDATE gcmb."+table+" SET status='RESOLVED',resolution=?,resolved_by=?,resolved_at=clock_timestamp() "
        +"WHERE id=? AND branch_id=? AND status='DISCREPANCY'",note,actor.id(),id,branch);
      if (count==1) {
        var result=db.queryForMap("SELECT * FROM gcmb."+table+" WHERE id=?",id);
        access.audit(actor,branch,id,"SHIFT_CASH_RESOLVED",null,result);
        return result;
      }
    }
    throw conflict("Không tìm thấy bàn giao chênh lệch cần xử lý tại cơ sở này.");
  }
}
