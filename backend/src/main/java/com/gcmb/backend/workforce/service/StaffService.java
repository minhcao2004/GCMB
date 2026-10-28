/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 26/10/2026
 * Updated: 27/10/2026
 * Description: Kiểm tra quyền, BR và nghiệp vụ hồ sơ nhân viên (UC-77–82).
 */

package com.gcmb.backend.workforce.service;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.auth.service.AuthValidation;
import com.gcmb.backend.workforce.dto.StaffDtos.*;
import com.gcmb.backend.workforce.repository.StaffRepository;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StaffService {

    private final StaffRepository staff;
    private final AccountRepository accounts;

    public StaffService(StaffRepository staff, AccountRepository accounts) {
        this.staff = staff;
        this.accounts = accounts;
    }

    public static final Set<String> POSITIONS = Set.of(
        "HEAD_OFFICE_MANAGER",
        "BRANCH_MANAGER",
        "RECEPTIONIST",
        "ACCOUNTANT",
        "OTHER"
    );

    // BR-52, BR-177; MSG24, MSG43: recheck server assignments and account status on every request.
    private UUID scope(Identity actor, boolean writing) {
        if (
            actor == null || !Set.of("HEAD_OFFICE_MANAGER", "BRANCH_MANAGER").contains(actor.role())
        ) throw denied();
        if (writing) staff.serialize();
        if (!staff.actorActive(actor.id(), actor.role())) throw denied();
        if ("HEAD_OFFICE_MANAGER".equals(actor.role())) return null;
        if (
            actor.branchId() == null || !accounts.manages(actor.id(), actor.branchId(), false)
        ) throw denied();
        if (writing && !accounts.manages(actor.id(), actor.branchId(), true)) throw new AuthFailure(
            403,
            "form",
            "Cơ sở " +
                actor.branchName() +
                " đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi."
        );
        return actor.branchId();
    }

    private Map<String, Object> record(UUID id, UUID scope, boolean lock) {
        if (lock && !staff.lock(id)) throw missing();
        var row = staff.find(id);
        if (row == null) throw missing();
        if (scope != null && !scope.equals(row.get("branchId"))) throw denied();
        return row;
    }

    private void version(Map<String, Object> row, Long version) {
        if (
            version == null || version != ((Number) row.get("version")).longValue()
        ) throw new AuthFailure(
            409,
            "form",
            "Hồ sơ đã thay đổi. Vui lòng tải lại và kiểm tra thông tin trước khi xác nhận."
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(
        Identity actor,
        String q,
        String position,
        String status,
        UUID branch,
        boolean unassigned,
        int page,
        int size
    ) {
        UUID scope = scope(actor, false);
        if (
            page < 0 ||
            size < 1 ||
            size > 100 ||
            q.length() > 200 ||
            (!position.isEmpty() && !POSITIONS.contains(position)) ||
            (!status.isEmpty() && !Set.of("ACTIVE", "INACTIVE", "RESIGNED").contains(status))
        ) throw new AuthFailure(400, "form", "Bộ lọc hoặc phân trang không hợp lệ.");
        if (
            scope != null && ((branch != null && !scope.equals(branch)) || unassigned)
        ) throw denied();
        return staff.list(
            q.trim(),
            position,
            status,
            scope == null ? branch : scope,
            unassigned,
            page,
            size
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> options(Identity actor) {
        UUID scope = scope(actor, false);
        return Map.of("branches", staff.branches(scope));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Identity actor, UUID id) {
        return detailRow(record(id, scope(actor, false), false));
    }

    private Map<String, Object> detailRow(Map<String, Object> row) {
        var result = new LinkedHashMap<>(row);
        result.put("history", staff.history((UUID) row.get("id")));
        return result;
    }

    public Map<String, Object> create(Identity actor, StaffCommand input) {
        UUID scope = scope(actor, true);
        StaffCommand c = validate(input, null);
        UUID id = UUID.randomUUID();
        staff.create(id, c);
        // UC-77/BR-52: BM creates inside the current assigned branch. UC-81/82 remain HOM-only.
        if (scope != null) staff.assignment(
            id,
            scope,
            actor.id(),
            "Phân công khi tạo hồ sơ tại cơ sở."
        );
        var after = staff.find(id);
        staff.audit(actor.id(), id, "CREATE_STAFF", null, after, null);
        return detailRow(after);
    }

    public Map<String, Object> update(Identity actor, UUID id, StaffCommand input) {
        var before = record(id, scope(actor, true), true);
        version(before, input == null ? null : input.version());
        StaffCommand c = validate(input, id);
        staff.update(id, c);
        var after = staff.find(id);
        staff.audit(actor.id(), id, "UPDATE_STAFF", before, after, null);
        return detailRow(after);
    }

    // UC-80, BR-171, BR-199: status, automatic account lock (DB trigger), tokens and audit share the transaction.
    public Map<String, Object> status(Identity actor, UUID id, StatusCommand input) {
        var before = record(id, scope(actor, true), true);
        if (input == null) throw new AuthFailure(400, "form", "Vui lòng bổ sung trạng thái hồ sơ.");
        version(before, input.version());
        if (!input.active() && actor.id().equals(before.get("userId"))) throw new AuthFailure(
            409,
            "form",
            "Không thể ngừng hồ sơ đang liên kết với tài khoản bạn đang đăng nhập."
        );
        if (
            !input.active() &&
            "BRANCH_MANAGER".equals(actor.role()) &&
            before.get("userId") != null &&
            !"RECEPTIONIST".equals(before.get("accountRole"))
        ) throw denied();
        String reason = optional(input.reason(), "reason", "Lý do", 1000);
        LocalDate resigned = input.active() ? null : input.resignedOn();
        dates(LocalDate.parse((String) before.get("hiredOn")), resigned);
        String status = input.active() ? "ACTIVE" : "INACTIVE";
        if (
            status.equals(before.get("status")) &&
            Objects.equals(resigned == null ? null : resigned.toString(), before.get("resignedOn"))
        ) return detailRow(before);
        staff.status(id, status, resigned);
        var after = staff.find(id);
        staff.audit(
            actor.id(),
            id,
            input.active() ? "REACTIVATE_STAFF" : "DEACTIVATE_STAFF",
            before,
            after,
            reason
        );
        return detailRow(after); // Reactivation does not silently restore revoked account access (UC-07).
    }

    public Map<String, Object> assign(
        Identity actor,
        UUID id,
        AssignmentCommand input,
        boolean transfer
    ) {
        if (scope(actor, true) != null) throw denied(); // UC-81/82: only Head Office Manager.
        var before = record(id, null, true);
        if (input == null || input.branchId() == null) throw new AuthFailure(
            400,
            "branchId",
            "Vui lòng bổ sung cơ sở làm việc."
        );
        version(before, input.version());
        if (!"ACTIVE".equals(before.get("status"))) throw new AuthFailure(
            409,
            "form",
            "Chỉ phân công cơ sở cho nhân viên đang làm việc."
        );
        UUID previous = (UUID) before.get("branchId");
        if (
            (transfer && previous == null) || (!transfer && previous != null)
        ) throw new AuthFailure(
            409,
            "form",
            transfer
                ? "Nhân viên chưa có cơ sở. Vui lòng gán cơ sở trước."
                : "Nhân viên đã có cơ sở. Vui lòng dùng chức năng điều chuyển."
        );
        if (input.branchId().equals(previous)) throw new AuthFailure(
            400,
            "branchId",
            "Cơ sở mới phải khác cơ sở hiện tại."
        );
        // MSG40: báo rõ cơ sở không còn hoạt động nếu trạng thái đổi sau khi mở form.
        if (!staff.activeBranch(input.branchId())) throw new AuthFailure(
            409,
            "branchId",
            "Cơ sở " +
                staff.branchName(input.branchId()) +
                " hiện không hoạt động. Vui lòng chọn cơ sở đang hoạt động để phân công."
        );
        if (staff.futureAssignment(id)) throw new AuthFailure(
            409,
            "form",
            "Nhân viên có lịch phân công chưa kết thúc. Vui lòng kiểm tra lịch sử cơ sở."
        );
        String reason = optional(input.reason(), "reason", "Lý do", 1000);
        staff.assignment(id, input.branchId(), actor.id(), reason);
        if (
            before.get("userId") != null && "RECEPTIONIST".equals(before.get("accountRole"))
        ) staff.moveReceptionAccess((UUID) before.get("userId"), input.branchId());
        var after = staff.find(id);
        staff.audit(
            actor.id(),
            id,
            transfer ? "TRANSFER_EMPLOYEE" : "SET_EMPLOYEE_BRANCH",
            before,
            after,
            reason
        );
        return detailRow(after);
    }

    // BR-111, BR-119, BR-199; MSG02, MSG03, MSG04, MSG05, MSG88. Limits follow the existing employee schema.
    private StaffCommand validate(StaffCommand c, UUID id) {
        if (c == null) throw new AuthFailure(400, "form", "Vui lòng bổ sung thông tin hồ sơ.");
        String code = AuthValidation.required(c.code(), "code", "mã nhân viên", 60, true);
        String name = AuthValidation.required(c.fullName(), "fullName", "họ tên", 200, true);
        String position = AuthValidation.required(c.position(), "position", "vị trí", 60, true);
        if (!POSITIONS.contains(position)) throw new AuthFailure(
            400,
            "position",
            "Vị trí nhân viên không hợp lệ."
        );
        String phone = AuthValidation.phone(c.phone());
        String email = optional(c.contactEmail(), "contactEmail", "Email liên hệ", 254);
        if (email != null) try {
            email = AuthValidation.email(email);
        } catch (AuthFailure e) {
            throw new AuthFailure(400, "contactEmail", e.getMessage());
        }
        // UC-77/79: chặn ngay tại hồ sơ, không đợi đến bước cấp tài khoản (mở rộng BR-134/MSG35).
        if (email != null && accounts.emailReserved(email, id, null)) throw new AuthFailure(
            409,
            "contactEmail",
            "Email này đã được sử dụng cho hồ sơ nhân viên hoặc tài khoản khác. Vui lòng nhập email khác."
        );
        if (phone != null && accounts.staffPhoneTaken(phone, id, null)) throw new AuthFailure(
            409,
            "phone",
            "Số điện thoại này đã được sử dụng cho hồ sơ nhân viên khác. Vui lòng nhập số khác."
        );
        String identity = optional(c.identityNumber(), "identityNumber", "Số định danh", 30);
        if (staff.duplicate("code", code, id)) throw new AuthFailure(
            409,
            "code",
            "Mã nhân viên này đã được sử dụng. Vui lòng nhập mã khác."
        );
        if (
            identity != null && staff.duplicate("identity_number", identity, id)
        ) throw new AuthFailure(
            409,
            "identityNumber",
            "Số định danh này đã được sử dụng cho hồ sơ khác."
        );
        dates(c.hiredOn(), c.resignedOn());
        return new StaffCommand(
            code,
            name,
            phone,
            position,
            email,
            identity,
            optional(c.address(), "address", "Địa chỉ", 2000),
            Optional.ofNullable(optional(c.notes(), "notes", "Ghi chú", 4000)).orElse(""),
            c.hiredOn(),
            c.resignedOn(),
            c.version()
        );
    }

    private static void dates(LocalDate hired, LocalDate resigned) {
        if (hired == null) throw new AuthFailure(400, "hiredOn", "Vui lòng bổ sung ngày vào làm.");
        if (
            hired.getYear() < 1 ||
            hired.getYear() > 9999 ||
            (resigned != null && (resigned.getYear() < 1 || resigned.getYear() > 9999))
        ) throw new AuthFailure(400, "hiredOn", "Ngày làm việc không hợp lệ.");
        if (resigned != null && resigned.isBefore(hired)) throw new AuthFailure(
            400,
            "resignedOn",
            "Ngày nghỉ việc phải bằng hoặc sau ngày vào làm."
        );
    }

    private static String optional(String s, String field, String label, int max) {
        if (s == null || s.isBlank()) return null;
        return AuthValidation.required(s, field, label, max, true);
    }

    private static AuthFailure denied() {
        return new AuthFailure(
            403,
            "form",
            "Bạn không có quyền thực hiện thao tác đối với hồ sơ nhân viên này."
        );
    }

    private static AuthFailure missing() {
        return new AuthFailure(404, "form", "Không tìm thấy hồ sơ nhân viên.");
    }
}
