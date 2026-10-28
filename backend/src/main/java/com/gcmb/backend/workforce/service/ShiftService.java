/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-83–86, công bố ca tuần, tự đăng ký và quản lý thay người trong cùng ca.
 */
package com.gcmb.backend.workforce.service;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.auth.service.AuthValidation;
import com.gcmb.backend.workforce.dto.ShiftDtos.*;
import com.gcmb.backend.workforce.repository.ShiftRepository;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ShiftService {

    private final ShiftRepository repo;
    private final AccountRepository accounts;

    public ShiftService(ShiftRepository repo, AccountRepository accounts) {
        this.repo = repo;
        this.accounts = accounts;
    }

    Map<String, Object> scope(Identity actor, boolean write, boolean manager) {
        if (
            actor == null ||
            actor.branchId() == null ||
            !Set.of("BRANCH_MANAGER", "RECEPTIONIST").contains(actor.role()) ||
            (manager && !"BRANCH_MANAGER".equals(actor.role()))
        ) throw denied();
        if (write) repo.serialize(); // cùng thứ tự khóa với thay cơ sở / ngừng nhân viên.
        if (!repo.actor(actor.id(), actor.role(), actor.branchId())) throw denied();
        if (
            "BRANCH_MANAGER".equals(actor.role()) &&
            !accounts.manages(actor.id(), actor.branchId(), false)
        ) throw denied();
        var branch = repo.branch(actor.branchId(), write);
        if (branch == null) throw denied();
        // BR-177 / MSG43: vẫn cho xem, chặn mọi thay đổi tại cơ sở tạm ngừng.
        if (write && !"ACTIVE".equals(branch.get("status"))) throw new AuthFailure(
            403,
            "form",
            "Cơ sở " +
                branch.get("name") +
                " đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi."
        );
        return branch;
    }

    private LocalDate currentWeek(Map<String, Object> branch) {
        return LocalDate.now(ZoneId.of((String) branch.get("timezone"))).with(
            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
    }

    private LocalDate week(LocalDate date, Map<String, Object> branch) {
        if (date == null) return currentWeek(branch);
        if (
            date.getYear() < 1 || date.getYear() > 9998 || date.getDayOfWeek() != DayOfWeek.MONDAY
        ) throw bad("week", "Vui lòng chọn tuần bắt đầu từ thứ Hai.");
        return date;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> weekly(Identity actor, LocalDate date) {
        var branch = scope(actor, false, false);
        return weekly(actor, week(date, branch), branch);
    }

    private Map<String, Object> weekly(Identity actor, LocalDate week, Map<String, Object> branch) {
        boolean manager = "BRANCH_MANAGER".equals(actor.role());
        UUID employee = manager ? null : repo.linkedEmployee(actor.id());
        if (!manager && employee == null) throw denied();
        // BR-60: lễ tân chỉ nhận phân công của mình; các ô đăng ký chỉ lộ số chỗ, không lộ tên đồng nghiệp.
        var result = new LinkedHashMap<String, Object>();
        result.put("week", week.toString());
        result.put("currentWeek", currentWeek(branch).toString());
        result.put("nextWeek", currentWeek(branch).plusWeeks(1).toString());
        result.put("branchName", branch.get("name"));
        var templates = repo.templates();
        var shifts = repo.shifts(actor.branchId(), week);
        result.put("templates", templates);
        result.put("shifts", shifts);
        // UC-85 mở rộng theo yêu cầu: quản lý chỉnh tuần hiện tại và tuần tới; dùng giờ server.
        var editable = new ArrayList<String>();
        if (
            manager &&
            "ACTIVE".equals(branch.get("status")) &&
            (week.equals(currentWeek(branch)) || week.equals(currentWeek(branch).plusWeeks(1)))
        ) {
            var closed = repo.closedMonths(actor.branchId(), week);
            var zone = ZoneId.of((String) branch.get("timezone"));
            Instant now = Instant.now();
            for (int day = 0; day < 7; day++) {
                LocalDate date = week.plusDays(day);
                for (var template : templates) {
                    Instant end = date
                        .atStartOfDay(zone)
                        .plusMinutes(((Number) template.get("endMinute")).intValue())
                        .toInstant();
                    boolean attended = shifts
                        .stream()
                        .anyMatch(
                            s ->
                                date.toString().equals(s.get("date")) &&
                                template.get("id").equals(s.get("templateId")) &&
                                Boolean.TRUE.equals(s.get("hasAttendance"))
                        );
                    if (end.isAfter(now) && !closed.contains(date.withDayOfMonth(1)) && !attended) {
                        editable.add(date + "/" + template.get("id"));
                    }
                }
            }
        }
        if (!manager) shifts.forEach(shift -> shift.remove("hasAttendance")); // BR-60: chỉ quản lý cần trạng thái khóa công của cả ca.
        result.put("editableSlots", editable);
        result.put("assignments", repo.assignments(actor.branchId(), week, employee));
        result.put("history", manager ? repo.history(actor.branchId(), week) : List.of());
        result.put("employees", manager ? repo.employees(actor.branchId()) : List.of());
        result.put("readOnly", !"ACTIVE".equals(branch.get("status")));
        return result;
    }

    public Map<String, Object> publish(Identity actor, Availability input) {
        var branch = scope(actor, true, true);
        LocalDate current = currentWeek(branch);
        if (
            input == null ||
            input.slots() == null ||
            input.slots().isEmpty() ||
            input.slots().size() > 28
        ) throw bad("slots", "Vui lòng bổ sung các ca cần công bố.");
        var first = input.slots().get(0);
        if (first == null || first.date() == null) throw bad(
            "slots",
            "Vui lòng bổ sung ngày và ca làm việc hợp lệ."
        );
        LocalDate targetWeek = first.date().with(DayOfWeek.MONDAY);
        if (!targetWeek.equals(current) && !targetWeek.equals(current.plusWeeks(1))) {
            throw bad("slots", "Chỉ mở hoặc chỉnh ca trong tuần hiện tại và tuần tới.");
        }
        Map<UUID, Map<String, Object>> templates = new HashMap<>();
        for (var t : repo.templates()) templates.put((UUID) t.get("id"), t);
        var seen = new HashSet<String>();
        for (var slot : input.slots()) {
            // UC-85 mở rộng tuần hiện tại; BR-53/111/203, MSG02/44: ca cố định, sức chứa nguyên dương.
            if (
                slot == null ||
                slot.date() == null ||
                slot.templateId() == null ||
                !templates.containsKey(slot.templateId())
            ) throw bad("slots", "Vui lòng bổ sung ngày và ca làm việc hợp lệ.");
            if (
                slot.date().isBefore(targetWeek) || !slot.date().isBefore(targetWeek.plusWeeks(1))
            ) throw bad("slots", "Các ca trong một lần lưu phải thuộc cùng một tuần.");
            if (!seen.add(slot.date() + "/" + slot.templateId())) throw bad(
                "slots",
                "Một ngày/ca không được xuất hiện hai lần."
            );
            var old = repo.slot(actor.branchId(), slot.templateId(), slot.date());
            if (old == null && !slot.open()) continue;
            if (slot.capacity() == null || slot.capacity() < 1) throw bad(
                "capacity",
                "Số chỗ phải là số nguyên lớn hơn 0."
            );
            if (
                old == null
                    ? slot.version() != null
                    : slot.version() == null ||
                          slot.version().longValue() != ((Number) old.get("version")).longValue()
            ) throw stale();
            long registered = old == null ? 0 : repo.registered((UUID) old.get("id"));
            // BR-192 / MSG85: kiểm tra lại dưới khóa, không giảm dưới số người đã có.
            if (slot.capacity() < registered) throw new AuthFailure(
                409,
                "capacity",
                "Ca này đã có " +
                    registered +
                    " người đăng ký. Số chỗ không được nhỏ hơn " +
                    registered +
                    "."
            );
            if (repo.closedPeriod(actor.branchId(), slot.date())) throw locked();
            var t = templates.get(slot.templateId());
            int start = ((Number) t.get("startMinute")).intValue(),
                end = ((Number) t.get("endMinute")).intValue();
            if (
                !Set.of("0/420", "420/720", "720/1080", "1080/1440").contains(start + "/" + end)
            ) throw bad("slots", "Ca làm việc không đúng bốn khung giờ cố định.");
            var zone = ZoneId.of((String) branch.get("timezone"));
            Instant from = slot.date().atStartOfDay(zone).plusMinutes(start).toInstant(),
                to = slot.date().atStartOfDay(zone).plusMinutes(end).toInstant();
            if (!to.isAfter(Instant.now())) throw bad(
                "slots",
                "Ca đã kết thúc, không thể mở hoặc chỉnh ca."
            );
            if (old != null && repo.hasAttendance((UUID) old.get("id"))) throw locked();
            String status = slot.open() ? "OPEN" : "CLOSED";
            if (
                old != null &&
                slot.capacity().intValue() == ((Number) old.get("capacity")).intValue() &&
                status.equals(old.get("status"))
            ) continue;
            UUID id = repo.publish(
                actor.branchId(),
                slot.templateId(),
                slot.date(),
                from,
                to,
                slot.capacity(),
                status,
                actor.id(),
                old
            );
            repo.audit(
                actor.id(),
                actor.branchId(),
                id,
                "SET_WEEKLY_SHIFT_AVAILABILITY",
                Map.of(
                    "date",
                    slot.date().toString(),
                    "capacity",
                    slot.capacity(),
                    "status",
                    status,
                    "previousCapacity",
                    old == null ? 0 : old.get("capacity"),
                    "previousStatus",
                    old == null ? "UNPUBLISHED" : old.get("status")
                )
            );
        }
        return weekly(actor, targetWeek, branch);
    }

    public Map<String, Object> register(Identity actor, UUID shift) {
        if (actor == null || !"RECEPTIONIST".equals(actor.role())) throw denied();
        var branch = scope(actor, true, false);
        var row = repo.shift(shift, actor.branchId());
        // UC-83, BR-54, BR-57 (Business Rules.docx), BR-55 / MSG84.
        if (row == null || !"OPEN".equals(row.get("status"))) throw registration();
        LocalDate date = ((java.sql.Date) row.get("work_date")).toLocalDate();
        // UC-83 mở rộng theo yêu cầu: ca chưa bắt đầu trong tuần hiện tại hoặc tuần tới.
        LocalDate current = currentWeek(branch);
        if (date.isBefore(current) || !date.isBefore(current.plusWeeks(2))) throw registration();
        UUID employee = repo.linkedEmployee(actor.id());
        Instant start = time(row, "starts_at"),
            end = time(row, "ends_at");
        if (
            employee == null ||
            !repo.belongs(employee, actor.branchId(), start, end) ||
            !start.isAfter(Instant.now())
        ) throw registration();
        if (repo.already(employee, shift)) return weekly(
            actor,
            date.with(DayOfWeek.MONDAY),
            branch
        );
        if (repo.closedPeriod(actor.branchId(), date)) throw locked();
        long registered = repo.registered(shift);
        // BR-56 / MSG19: khóa ca trước khi kiểm tra chỗ; đăng ký đủ điều kiện tự xác nhận, không duyệt tay.
        if (registered >= ((Number) row.get("capacity")).longValue()) throw new AuthFailure(
            409,
            "form",
            "Ca " +
                label(start, branch) +
                " ngày " +
                date +
                " đã đủ người đăng ký. Vui lòng chọn ca khác."
        );
        if (repo.overlap(employee, shift, start, end)) throw bad(
            "form",
            "Nhân viên đã có ca trùng thời gian."
        );
        UUID assignment = repo.assign(shift, employee, actor.id(), "SELF", null);
        repo.audit(
            actor.id(),
            actor.branchId(),
            shift,
            "REGISTER_FOR_SHIFT",
            Map.of("assignmentId", assignment, "employeeId", employee)
        );
        return weekly(actor, date.with(DayOfWeek.MONDAY), branch);
    }

    public Map<String, Object> replace(Identity actor, UUID assignment, Replacement input) {
        var branch = scope(actor, true, true);
        if (input == null || input.employeeId() == null) throw bad(
            "employeeId",
            "Vui lòng bổ sung nhân viên làm thay."
        );
        String reason = AuthValidation.required(
            input.reason(),
            "reason",
            "lý do thay người",
            1000,
            true
        );
        var before = repo.assignment(assignment, actor.branchId());
        if (before == null || !"CONFIRMED".equals(before.get("status"))) throw stale();
        UUID shift = (UUID) before.get("shift_id");
        var row = repo.shift(shift, actor.branchId());
        if (
            input.version() == null ||
            input.version().longValue() != ((Number) row.get("version")).longValue()
        ) throw stale();
        // UC-86 / BR-58, BR-111: theo yêu cầu người dùng, A -> B trong cùng ca, không chuyển ngày/giờ.
        if (input.employeeId().equals(before.get("employee_id"))) throw bad(
            "employeeId",
            "Nhân viên làm thay phải khác người hiện tại."
        );
        LocalDate date = ((java.sql.Date) row.get("work_date")).toLocalDate();
        Instant start = time(row, "starts_at"),
            end = time(row, "ends_at"),
            now = Instant.now();
        if (!end.isAfter(now)) throw bad("form", "Ca đã kết thúc, không thể thay nhân viên.");
        if (repo.closedPeriod(actor.branchId(), date) || repo.attended(assignment)) throw locked();
        if ("CANCELLED".equals(row.get("status"))) throw bad("form", "Ca đã bị hủy.");
        if (
            !repo.belongs(
                input.employeeId(),
                actor.branchId(),
                start.isAfter(now) ? start : now,
                end
            )
        ) throw bad(
            "employeeId",
            "Vui lòng chọn lễ tân đang hoạt động và được phân công tại cơ sở của ca."
        );
        if (repo.overlap(input.employeeId(), shift, start, end)) throw bad(
            "employeeId",
            "Nhân viên làm thay đã có ca trùng thời gian."
        );
        if (repo.registered(shift) > ((Number) row.get("capacity")).longValue()) throw bad(
            "form",
            "Số người trong ca đang vượt sức chứa. Vui lòng kiểm tra lại."
        );
        repo.cancel(assignment, reason);
        UUID replacement = repo.assign(shift, input.employeeId(), actor.id(), "MANAGER", reason);
        repo.audit(
            actor.id(),
            actor.branchId(),
            shift,
            "REPLACE_SHIFT_EMPLOYEE",
            Map.of(
                "previousAssignmentId",
                assignment,
                "previousEmployeeId",
                before.get("employee_id"),
                "assignmentId",
                replacement,
                "employeeId",
                input.employeeId(),
                "reason",
                reason
            )
        );
        return weekly(actor, date.with(DayOfWeek.MONDAY), branch);
    }

    private static Instant time(Map<String, Object> row, String key) {
        return ((Timestamp) row.get(key)).toInstant();
    }

    private static String label(Instant start, Map<String, Object> branch) {
        return start
            .atZone(ZoneId.of((String) branch.get("timezone")))
            .toLocalTime()
            .toString();
    }

    private static AuthFailure bad(String field, String message) {
        return new AuthFailure(400, field, message);
    }

    private static AuthFailure denied() {
        return new AuthFailure(
            403,
            "form",
            "Bạn không có quyền thực hiện thao tác đối với lịch ca tại cơ sở này."
        );
    } // MSG24

    private static AuthFailure stale() {
        return new AuthFailure(
            409,
            "form",
            "Lịch ca đã thay đổi. Vui lòng tải lại và kiểm tra thông tin trước khi xác nhận."
        );
    }

    private static AuthFailure registration() {
        return new AuthFailure(
            403,
            "form",
            "Chỉ có thể đăng ký ca đang mở tại cơ sở được phân công. Vui lòng chọn ca phù hợp."
        );
    } // MSG84

    private static AuthFailure locked() {
        return new AuthFailure(
            409,
            "form",
            "Ca thuộc kỳ công đã chốt hoặc đã có chấm công. Không thể thay đổi lịch này."
        );
    } // BR-63 + mockup: không sửa lịch làm sai liên kết chấm công.
}
