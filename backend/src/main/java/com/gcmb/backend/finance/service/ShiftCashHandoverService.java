/** Author: HuyDV. Bổ sung theo yêu cầu: ca trước giao tiền, ca sau nhận, không phát sinh thu/chi.
 * Dùng nguồn tiền UC-98/BR-95, phạm vi chỉ xem BR-177 và quy tắc bàn giao BR-242. */
package com.gcmb.backend.finance.service;

import static com.gcmb.backend.finance.service.FinanceAccess.*;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.finance.repository.ShiftCashHandoverRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class ShiftCashHandoverService {
  private static final int[] SHIFT_BOUNDARY_HOURS = {0,7,12,18,24};
  private final ShiftCashHandoverRepository shiftCashHandoverRepository;
  private final FinanceAccess financeAccess;
  private final CashClosingService cashClosingService;
  private final ObjectMapper objectMapper;

  public ShiftCashHandoverService(ShiftCashHandoverRepository shiftCashHandoverRepository, FinanceAccess financeAccess,
                                  CashClosingService cashClosingService, ObjectMapper objectMapper) {
    this.shiftCashHandoverRepository=shiftCashHandoverRepository;
    this.financeAccess=financeAccess;
    this.cashClosingService=cashClosingService;
    this.objectMapper=objectMapper;
  }

  public record Receive(LocalDate date, int shift, BigDecimal actual, String note, boolean confirmed, String revision) {}
  public record Resolution(String note) {}

  private UUID branch(Identity actor, boolean write) {
    if (actor!=null && "BRANCH_MANAGER".equals(actor.role())) return financeAccess.branch(actor,write);
    financeAccess.role(actor,"RECEPTIONIST");
    if (actor.branchId()==null || !shiftCashHandoverRepository.isActiveUserOfBranch(actor.id(),actor.branchId()))
      throw new AuthFailure(403,"form","Bạn không có quyền truy cập két của cơ sở này.");
    String status=shiftCashHandoverRepository.findBranchStatus(actor.branchId(),write);
    if (write && !"ACTIVE".equals(status)) throw new AuthFailure(403,"form",
      "Cơ sở đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi."); // BR-177/MSG43.
    return actor.branchId();
  }

  private ZoneId zone(UUID branchId) {
    return ZoneId.of(shiftCashHandoverRepository.findBranchTimezone(branchId));
  }

  private LocalDate date(LocalDate input, ZoneId zone) {
    LocalDate businessDate=input==null?LocalDate.now(zone):input;
    if (businessDate.getYear()<2000 || businessDate.isAfter(LocalDate.now(zone))) throw bad("Ngày bàn giao không hợp lệ hoặc ở tương lai.");
    return businessDate;
  }

  private Instant end(LocalDate date, int shift, ZoneId zone) {
    if (shift<1 || shift>4) throw bad("Vui lòng chọn ca từ 1 đến 4.");
    return date.atStartOfDay().plusHours(SHIFT_BOUNDARY_HOURS[shift]).atZone(zone).toInstant();
  }

  // HuyDV / UC-85/86: giao và nhận đều phải thuộc đúng ca, không dùng quyền đăng nhập thay phân công.
  private boolean assignedToIncoming(Identity actor, UUID branchId, LocalDate businessDate, int outgoingShift, ZoneId zone) {
    LocalDate receivingDate=outgoingShift==4?businessDate.plusDays(1):businessDate;
    int incomingShift=outgoingShift==4?1:outgoingShift+1;
    return assigned(actor,branchId,receivingDate,incomingShift,zone);
  }

  private boolean assigned(Identity actor, UUID branchId, LocalDate workDate, int shift, ZoneId zone) {
    if (!"RECEPTIONIST".equals(actor.role())) return false;
    Instant startsAt=workDate.atStartOfDay().plusHours(SHIFT_BOUNDARY_HOURS[shift-1]).atZone(zone).toInstant();
    Instant endsAt=end(workDate,shift,zone);
    return shiftCashHandoverRepository.isConfirmedReceptionistOfShift(actor.id(),branchId,workDate,
      Timestamp.from(startsAt),Timestamp.from(endsAt));
  }

  private List<Map<String,Object>> records(UUID branchId, LocalDate businessDate, boolean pendingOnly, boolean dispatch) {
    String userColumn=dispatch?"handed_by":"received_by";
    var result=shiftCashHandoverRepository.findRecords(branchId,businessDate,pendingOnly,dispatch);
    ZoneId zone=zone(branchId);
    for (var record:result) {
      LocalDate recordDate=((java.sql.Date)record.get("business_date")).toLocalDate();
      int outgoingShift=((Number)record.get("shift_number")).intValue();
      LocalDate receivingDate=dispatch?recordDate:outgoingShift==4?recordDate.plusDays(1):recordDate;
      int incomingShift=dispatch?outgoingShift:outgoingShift==4?1:outgoingShift+1;
      Instant startsAt=receivingDate.atStartOfDay().plusHours(SHIFT_BOUNDARY_HOURS[incomingShift-1]).atZone(zone).toInstant();
      Timestamp recordedAt=(Timestamp)record.get(dispatch?"handed_at":"received_at");
      // Giữ bản ghi cũ; đối chiếu phân công tại thời điểm nhận, kể cả ca được thay người sau đó.
      boolean verified=shiftCashHandoverRepository.wasAssignedAt(record.get(userColumn),branchId,receivingDate,
        Timestamp.from(startsAt),Timestamp.from(end(receivingDate,incomingShift,zone)),recordedAt);
      record.put("assignmentVerified",verified);
      record.put("stage",dispatch?"DISPATCH":"RECEIVE");
    }
    return result;
  }

  @Transactional(readOnly=true)
  public Map<String,Object> overview(Identity actor, LocalDate input) {
    UUID branchId=branch(actor,false);
    ZoneId zone=zone(branchId);
    LocalDate businessDate=date(input,zone);
    var handovers=records(branchId,businessDate,false,false);
    var dispatches=records(branchId,businessDate,false,true);
    var shifts=new ArrayList<Map<String,Object>>();
    for (int shift=1;shift<=4;shift++) {
      Instant cutoff=end(businessDate,shift,zone);
      boolean ended=!Instant.now().isBefore(cutoff);
      final int shiftNumber=shift;
      var existing=handovers.stream().filter(record->((Number)record.get("shift_number")).intValue()==shiftNumber).findFirst().orElse(null);
      var dispatch=dispatches.stream().filter(record->((Number)record.get("shift_number")).intValue()==shiftNumber).findFirst().orElse(null);
      var row=new LinkedHashMap<String,Object>();
      row.put("shift",shift);
      row.put("hours",String.format("%02d:00–%02d:00",SHIFT_BOUNDARY_HOURS[shift-1],SHIFT_BOUNDARY_HOURS[shift]%24));
      row.put("ended",ended);
      row.put("receiveShift",shift==4?1:shift+1);
      row.put("receiveDate",(shift==4?businessDate.plusDays(1):businessDate).toString());
      row.put("canDispatch",ended && existing==null && dispatch==null && assigned(actor,branchId,businessDate,shift,zone));
      row.put("canReceive",ended && existing==null && dispatch!=null && assignedToIncoming(actor,branchId,businessDate,shift,zone));
      row.put("record",existing);
      row.put("dispatch",dispatch);
      if (dispatch!=null) {
        row.put("receiveExpected",dispatch.get("actual_amount"));
        row.put("receiveRevision",dispatch.get("id").toString());
      }
      if (ended && existing==null && dispatch==null) {
        var source=cashClosingService.sourceAt(branchId,businessDate,cutoff);
        row.put("expected",source.get("expected"));
        row.put("opening",source.get("opening"));
        row.put("cashIn",source.get("cashIn"));
        row.put("cashOut",source.get("cashOut"));
        row.put("revision",source.get("revision"));
      }
      shifts.add(row);
    }
    var pending=new ArrayList<>(records(branchId,businessDate,true,true));
    pending.addAll(records(branchId,businessDate,true,false));
    return Map.of("date",businessDate.toString(),"shifts",shifts,"pending",pending);
  }

  public Map<String,Object> dispatch(Identity actor, Receive input) {
    return recordCount(actor,input,true);
  }

  public Map<String,Object> receive(Identity actor, Receive input) {
    return recordCount(actor,input,false);
  }

  // BR-242: bàn giao chỉ lưu số đếm và chênh lệch; không tạo thêm dòng thu hoặc chi.
  private Map<String,Object> recordCount(Identity actor, Receive input, boolean dispatch) {
    // Khóa tiền trước hồ sơ như AccountProfileService; giữ khóa phân ca đến khi ghi nhận xong.
    financeAccess.lock();
    shiftCashHandoverRepository.lockShiftHandover();
    UUID branchId=branch(actor,true);
    financeAccess.role(actor,"RECEPTIONIST");
    ZoneId zone=zone(branchId);
    LocalDate businessDate=date(input.date(),zone);
    Instant cutoff=end(businessDate,input.shift(),zone);
    if (Instant.now().isBefore(cutoff)) throw conflict("Ca chưa kết thúc. Chỉ giao hoặc nhận tiền khi đã chuyển ca.");
    if (!(dispatch?assigned(actor,branchId,businessDate,input.shift(),zone):assignedToIncoming(actor,branchId,businessDate,input.shift(),zone)))
      throw new AuthFailure(403,"form",
        dispatch?"Bạn không được phân công ca giao nên không thể bàn giao tiền mặt."
          :"Bạn không được phân công ca nhận tiếp theo nên không thể nhận bàn giao này."); // MSG24.
    if (!input.confirmed()) throw bad("Vui lòng xác nhận đã kiểm đếm tiền mặt thực tế.");
    if (shiftCashHandoverRepository.handoverExists(branchId,businessDate,input.shift()))
      throw conflict("Ca này đã xác nhận bàn giao. Vui lòng tải lại để xem kết quả.");
    if (shiftCashHandoverRepository.isPeriodClosed(businessDate.withDayOfMonth(1)))
      throw conflict("Kỳ tài chính đã chốt, không thể bổ sung bàn giao.");
    var dispatches=shiftCashHandoverRepository.findDispatches(branchId,businessDate,input.shift());
    if (dispatch && !dispatches.isEmpty()) throw conflict("Ca này đã gửi bàn giao. Vui lòng tải lại để xem kết quả.");
    if (!dispatch && dispatches.isEmpty()) throw conflict("Ca trước chưa gửi bàn giao tiền mặt. Vui lòng liên hệ nhân viên ca giao.");
    Map<String,Object> source;
    if (dispatch) source=cashClosingService.sourceAt(branchId,businessDate,cutoff);
    else {
      source=new LinkedHashMap<>(dispatches.get(0));
      source.put("expected",source.get("actual_amount"));
      source.put("revision",source.get("id").toString());
    }
    if (!Objects.equals(input.revision(),source.get("revision")))
      throw conflict("Số liệu bàn giao đã thay đổi. Vui lòng tải lại và kiểm đếm trước khi xác nhận.");
    BigDecimal expectedAmount=new BigDecimal(source.get("expected").toString());
    BigDecimal actualAmount=money(input.actual(),"Tiền mặt thực đếm",false);
    boolean matched=actualAmount.compareTo(expectedAmount)==0;
    String note=text(input.note(),"Lý do chênh lệch",!matched);
    UUID recordId=UUID.randomUUID();
    shiftCashHandoverRepository.insertRecord(dispatch,recordId,branchId,businessDate,input.shift(),expectedAmount,actualAmount,
      input.revision(),objectMapper.writeValueAsString(source),note,matched?"MATCHED":"DISCREPANCY",actor.id());
    String table=dispatch?ShiftCashHandoverRepository.DISPATCH_TABLE:ShiftCashHandoverRepository.HANDOVER_TABLE;
    var result=shiftCashHandoverRepository.getRecord(table,recordId);
    financeAccess.audit(actor,branchId,recordId,dispatch?"SHIFT_CASH_DISPATCHED":"SHIFT_CASH_RECEIVED",null,result);
    return result;
  }

  public Map<String,Object> resolve(Identity actor, UUID recordId, Resolution input) {
    financeAccess.lock();
    UUID branchId=financeAccess.branch(actor,true);
    String resolution=text(input.note(),"Kết quả xử lý chênh lệch",true);
    for (String table:List.of(ShiftCashHandoverRepository.DISPATCH_TABLE,ShiftCashHandoverRepository.HANDOVER_TABLE)) {
      int updatedCount=shiftCashHandoverRepository.resolveDiscrepancy(table,recordId,branchId,resolution,actor.id());
      if (updatedCount==1) {
        var result=shiftCashHandoverRepository.getRecord(table,recordId);
        financeAccess.audit(actor,branchId,recordId,"SHIFT_CASH_RESOLVED",null,result);
        return result;
      }
    }
    throw conflict("Không tìm thấy bàn giao chênh lệch cần xử lý tại cơ sở này.");
  }
}
