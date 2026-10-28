/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-87..89, công thực tế, xử lý phản ánh và chốt công tháng đúng cơ sở.
 */
package com.gcmb.backend.workforce.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.auth.service.AuthValidation;
import com.gcmb.backend.workforce.dto.AttendanceDtos.*;
import com.gcmb.backend.workforce.repository.AttendanceRepository;
import com.gcmb.backend.workforce.repository.ShiftRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AttendanceService {

    private final AttendanceRepository repo;
    private final ShiftRepository shifts;
    private final ShiftService shiftService;

    public AttendanceService(
        AttendanceRepository repo,
        ShiftRepository shifts,
        ShiftService shiftService
    ) {
        this.repo = repo;
        this.shifts = shifts;
        this.shiftService = shiftService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> daily(Identity actor, LocalDate date) {
        var branch = shiftService.scope(actor, false, true); // BR-59, BR-61 / MSG24: quản lý cơ sở.
        ZoneId zone = ZoneId.of((String) branch.get("timezone"));
        LocalDate day = date == null ? LocalDate.now(zone) : date;
        validateDate(day);
        var result = base(branch);
        result.put("date", day.toString());
        result.put("today", LocalDate.now(zone).toString());
        result.put(
            "period",
            periodView(repo.period(actor.branchId(), day.withDayOfMonth(1), false))
        );
        result.put("rows", decorate(repo.rows(actor.branchId(), day, day.plusDays(1))));
        result.put("issues", repo.issues(actor.branchId(), day, day.plusDays(1)));
        result.put("templates", shifts.templates());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> monthly(Identity actor, String month) {
        var branch = shiftService.scope(actor, false, true);
        return summary(actor.branchId(), branch, month(month, branch));
    }

    public Map<String, Object> record(Identity actor, UUID assignment, RecordCommand input) {
        var branch = shiftService.scope(actor, true, true); // BR-177 / MSG43, khóa chung với lịch ca.
        var row = repo.row(actor.branchId(), assignment);
        if (row == null) throw new AuthFailure(
            404,
            "form",
            "Không tìm thấy phân ca tại cơ sở đang quản lý."
        );
        LocalDate date = LocalDate.parse((String) row.get("workDate"));
        var period = repo.ensurePeriod(actor.branchId(), date.withDayOfMonth(1));
        requireOpen(period, date);
        if (
            input == null ||
            !Set.of("WORKED", "ABSENT").contains(Objects.toString(input.status(), ""))
        ) {
            throw bad("status", "Vui lòng bổ sung tình trạng chấm công."); // BR-111/204 / MSG02.
        }
        if (
            input.version() == null || input.version().longValue() != number(row, "version")
        ) throw stale();
        ZoneId zone = ZoneId.of((String) branch.get("timezone"));
        Instant in = null;
        Instant out = null;
        int minutes = 0;
        // BR-193/194/195/204: dùng đầy đủ ngày giờ, không trừ nghỉ; vắng = 0 phút.
        if ("WORKED".equals(input.status())) {
            if (input.actualIn() == null || input.actualOut() == null) {
                throw bad("actualOut", "Vui lòng bổ sung ngày giờ vào và ra thực tế."); // MSG02.
            }
            in = localTime(input.actualIn(), zone, "actualIn");
            out = localTime(input.actualOut(), zone, "actualOut");
            if (!out.isAfter(in)) {
                throw bad("actualOut", "Giờ ra thực tế phải sau Giờ vào thực tế."); // MSG07.
            }
            long duration = Duration.between(in, out).toMinutes();
            if (duration > Integer.MAX_VALUE) throw bad(
                "actualOut",
                "Khoảng thời gian chấm công quá lớn."
            );
            minutes = (int) duration;
            if (out.isAfter(Instant.now())) throw bad(
                "actualOut",
                "Giờ ra thực tế không được ở tương lai."
            );
        }
        if (date.isAfter(LocalDate.now(zone))) throw bad(
            "form",
            "Chưa thể chấm công cho ngày làm việc trong tương lai."
        );
        String note = input.note() == null ? "" : input.note().trim();
        if (note.length() > 2000) throw bad("note", "Ghi chú không được dài quá 2000 ký tự.");
        var selected =
            input.resolvedIssueIds() == null
                ? Set.<UUID>of()
                : new HashSet<>(input.resolvedIssueIds());
        var openIssues = repo
            .issues(actor.branchId(), date, date.plusDays(1))
            .stream()
            .filter(i -> assignment.equals(i.get("assignmentId")) && "OPEN".equals(i.get("status")))
            .map(i -> (UUID) i.get("id"))
            .collect(java.util.stream.Collectors.toSet());
        if (!openIssues.containsAll(selected)) throw stale();
        String resolution = selected.isEmpty()
            ? ""
            : AuthValidation.required(
                  input.resolution(),
                  "resolution",
                  "kết quả xử lý phản ánh",
                  2000,
                  true
              );
        UUID id = repo.save(
            assignment,
            (UUID) period.get("id"),
            input.status(),
            in,
            out,
            minutes,
            note,
            actor.id()
        );
        for (UUID issue : selected) {
            if (!repo.resolve(issue, id, actor.id(), resolution)) throw stale();
        }
        repo.changed((UUID) period.get("id"));
        var after = new LinkedHashMap<String, Object>();
        after.put("assignmentId", assignment);
        after.put("status", input.status());
        after.put("actualIn", in == null ? null : in.toString());
        after.put("actualOut", out == null ? null : out.toString());
        after.put("workedMinutes", minutes);
        after.put("note", note);
        after.put("resolvedIssueIds", selected);
        after.put("resolution", resolution);
        repo.audit(actor.id(), actor.branchId(), id, "RECORD_DAILY_ATTENDANCE", row, after);
        return daily(actor, date);
    }

    public Map<String, Object> close(Identity actor, String value, CloseCommand input) {
        var branch = shiftService.scope(actor, true, true);
        YearMonth month = month(value, branch);
        var existing = repo.period(actor.branchId(), month.atDay(1), true);
        requireOpen(existing, month.atDay(1));
        var current = summary(actor.branchId(), branch, month);
        // BR-62 / MSG20: đối chiếu toàn bộ phân ca, bản ghi còn thiếu và phản ánh chưa xử lý.
        if (!Boolean.TRUE.equals(current.get("canClose"))) {
            throw new AuthFailure(
                409,
                "form",
                "Chưa thể chốt công tháng " +
                    label(month) +
                    ". Vui lòng bổ sung các bản ghi còn thiếu và kiểm tra những bản ghi chấm công chưa đúng."
            );
        }
        if (input == null || !input.confirmedCorrect()) {
            throw bad(
                "confirmedCorrect",
                "Vui lòng xác nhận không còn bản ghi chấm công chưa đúng."
            );
        }
        if (!Objects.equals(current.get("revision"), input.revision())) throw stale();
        var period = repo.ensurePeriod(actor.branchId(), month.atDay(1));
        // BR-63/64: khóa tháng trong cùng giao dịch với lần kiểm tra cuối, chỉ dữ liệu đã chốt dùng tính lương.
        repo.close((UUID) period.get("id"), actor.id());
        repo.audit(
            actor.id(),
            actor.branchId(),
            (UUID) period.get("id"),
            "CLOSE_MONTHLY_ATTENDANCE",
            period,
            Map.of(
                "month",
                month.toString(),
                "workedMinutes",
                current.get("workedMinutes"),
                "assignmentCount",
                current.get("assignmentCount"),
                "confirmedCorrect",
                true
            )
        );
        return summary(actor.branchId(), branch, month);
    }

    private Map<String, Object> summary(
        UUID branchId,
        Map<String, Object> branch,
        YearMonth month
    ) {
        var period = repo.period(branchId, month.atDay(1), false);
        var raw = repo.rows(branchId, month.atDay(1), month.plusMonths(1).atDay(1));
        var rows = decorate(raw);
        var issues = repo.issues(branchId, month.atDay(1), month.plusMonths(1).atDay(1));
        long missing = rows
            .stream()
            .filter(r -> r.get("attendanceId") == null)
            .count();
        long invalid = rows
            .stream()
            .filter(r -> r.get("attendanceId") != null && !Boolean.TRUE.equals(r.get("valid")))
            .count();
        long unresolved = issues
            .stream()
            .filter(i -> "OPEN".equals(i.get("status")))
            .count();
        long minutes = rows
            .stream()
            .filter(r -> Boolean.TRUE.equals(r.get("valid")))
            .mapToLong(r -> number(r, "workedMinutes"))
            .sum();
        var employees = new LinkedHashMap<UUID, Map<String, Object>>();
        for (var row : rows) {
            UUID id = (UUID) row.get("employeeId");
            var employee = employees.computeIfAbsent(id, key -> {
                var item = new LinkedHashMap<String, Object>();
                item.put("employeeId", id);
                item.put("code", row.get("code"));
                item.put("fullName", row.get("fullName"));
                for (String field : List.of(
                    "shiftCount",
                    "workedMinutes",
                    "absences",
                    "missing",
                    "invalid",
                    "openIssues",
                    "exceptions"
                ))
                    item.put(field, 0L);
                return item;
            });
            increment(employee, "shiftCount", 1);
            increment(employee, "openIssues", number(row, "openIssues"));
            if (row.get("attendanceId") == null) increment(employee, "missing", 1);
            else if (!Boolean.TRUE.equals(row.get("valid"))) increment(employee, "invalid", 1);
            else {
                increment(employee, "workedMinutes", number(row, "workedMinutes"));
                if ("ABSENT".equals(row.get("status"))) increment(employee, "absences", 1);
                if (!((List<?>) row.get("exceptions")).isEmpty()) increment(
                    employee,
                    "exceptions",
                    1
                );
            }
        }
        var result = base(branch);
        result.put("month", month.toString());
        result.put("period", periodView(period));
        result.put("employees", employees.values());
        result.put("rows", rows);
        result.put("issues", issues);
        result.put("workedMinutes", minutes);
        result.put("assignmentCount", rows.size());
        result.put("missing", missing);
        result.put("invalid", invalid);
        result.put("openIssues", unresolved);
        boolean ended =
            month
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(ZoneId.of((String) branch.get("timezone")))
                .toInstant()
                .isBefore(Instant.now()) &&
            rows.stream().noneMatch(r -> time(r, "endsAt").isAfter(Instant.now()));
        result.put("monthEnded", ended);
        result.put(
            "canClose",
            ended &&
                !rows.isEmpty() &&
                missing == 0 &&
                invalid == 0 &&
                unresolved == 0 &&
                (period == null || "OPEN".equals(period.get("status"))) &&
                "ACTIVE".equals(branch.get("status"))
        );
        result.put("revision", digest(raw.toString() + issues + Objects.toString(period, "")));
        return result;
    }

    private List<Map<String, Object>> decorate(List<Map<String, Object>> rows) {
        for (var row : rows) {
            boolean valid = valid(row);
            row.put("valid", valid);
            var exceptions = new ArrayList<String>();
            if (valid && "WORKED".equals(row.get("status"))) {
                long late = Duration.between(
                    time(row, "startsAt"),
                    time(row, "actualIn")
                ).toMinutes();
                long early = Duration.between(
                    time(row, "actualOut"),
                    time(row, "endsAt")
                ).toMinutes();
                long overtime = Duration.between(
                    time(row, "endsAt"),
                    time(row, "actualOut")
                ).toMinutes();
                if (late > 0) exceptions.add("Đi muộn " + late + " phút");
                if (early > 0) exceptions.add("Về sớm " + early + " phút");
                if (overtime > 0) exceptions.add("Làm thêm " + overtime + " phút");
            }
            row.put("exceptions", exceptions);
        }
        return rows;
    }

    private boolean valid(Map<String, Object> row) {
        if (row.get("attendanceId") == null) return false;
        if ("ABSENT".equals(row.get("status"))) {
            return (
                row.get("actualIn") == null &&
                row.get("actualOut") == null &&
                number(row, "workedMinutes") == 0
            );
        }
        if (
            !"WORKED".equals(row.get("status")) ||
            row.get("actualIn") == null ||
            row.get("actualOut") == null
        ) return false;
        long seconds = Duration.between(time(row, "actualIn"), time(row, "actualOut")).getSeconds();
        return (
            seconds > 0 &&
            seconds % 60 == 0 &&
            number(row, "breakMinutes") == 0 &&
            seconds / 60 == number(row, "workedMinutes") &&
            !time(row, "actualOut").isAfter(Instant.now())
        );
    }

    private Map<String, Object> base(Map<String, Object> branch) {
        var result = new LinkedHashMap<String, Object>();
        result.put("branchName", branch.get("name"));
        result.put("timezone", branch.get("timezone"));
        result.put("readOnly", !"ACTIVE".equals(branch.get("status")));
        return result;
    }

    private Map<String, Object> periodView(Map<String, Object> period) {
        return period == null ? Map.of("status", "OPEN", "version", 0) : period;
    }

    private YearMonth month(String value, Map<String, Object> branch) {
        try {
            var month =
                value == null
                    ? YearMonth.now(ZoneId.of((String) branch.get("timezone")))
                    : YearMonth.parse(value);
            validateDate(month.atDay(1));
            return month;
        } catch (DateTimeException e) {
            throw bad("month", "Tháng phải có định dạng YYYY-MM.");
        }
    }

    private void validateDate(LocalDate date) {
        if (date.getYear() < 1 || date.getYear() > 9998) throw bad("date", "Ngày không hợp lệ.");
    }

    private Instant localTime(LocalDateTime value, ZoneId zone, String field) {
        if (value.getSecond() != 0 || value.getNano() != 0) throw bad(
            field,
            "Vui lòng nhập ngày giờ chính xác đến phút."
        );
        validateDate(value.toLocalDate());
        var offsets = zone.getRules().getValidOffsets(value);
        if (offsets.size() != 1) throw bad(field, "Ngày giờ thực tế không hợp lệ tại cơ sở.");
        return value.toInstant(offsets.get(0));
    }

    private void requireOpen(Map<String, Object> period, LocalDate date) {
        if (period != null && !"OPEN".equals(period.get("status"))) {
            throw new AuthFailure(
                409,
                "form",
                "Công tháng " +
                    label(YearMonth.from(date)) +
                    " đã được chốt nên không thể sửa trực tiếp bản ghi chấm công này."
            ); // BR-63 / MSG86.
        }
    }

    private static void increment(Map<String, Object> row, String key, long value) {
        row.put(key, number(row, key) + value);
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static Instant time(Map<String, Object> row, String key) {
        return ((Timestamp) row.get(key)).toInstant();
    }

    private static String label(YearMonth month) {
        return "%02d/%04d".formatted(month.getMonthValue(), month.getYear());
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AuthFailure bad(String field, String message) {
        return new AuthFailure(400, field, message);
    }

    private static AuthFailure stale() {
        return new AuthFailure(
            409,
            "form",
            "Bảng công đã thay đổi. Vui lòng tải lại và kiểm tra trước khi xác nhận."
        );
    }
}
