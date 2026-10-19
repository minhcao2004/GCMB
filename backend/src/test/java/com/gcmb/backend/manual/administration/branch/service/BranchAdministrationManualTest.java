/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Covers branch administration service scenarios.
 */

package com.gcmb.backend.manual.administration.branch.service;

import com.gcmb.backend.administration.branch.dto.BranchDtos.BranchCommand;
import com.gcmb.backend.administration.branch.dto.BranchDtos.StatusCommand;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.support.ManualScenarios;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.dao.DataIntegrityViolationException;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class BranchAdministrationManualTest extends ManualScenarios<BranchAdministrationFixture> {
    private BranchAdministrationManualTest() {
        super("Administration","BranchAdministrationManualTest");
    }

    public static List<ManualTestSuite> suites() {
        var tests=new BranchAdministrationManualTest();
        tests.describeCases();
        tests.register();
        return tests.buildSuites();
    }

    private void describeCases() {
        define("TC-BRA-001", "Branch Management", "BranchAdministrationService.create",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Tạo cơ sở hợp lệ\n" +
            "HOM; mã \" ht_01 \", tên/địa chỉ có khoảng trắng; phone hợp lệ",
            "Trả fixture branch sau tạo; quan sát INSERT nhận HT_01, tên/địa chỉ đã trim, ACTIVE và timezone mặc định. " +
            "Không kiểm dữ liệu DB.",
            1,
            () -> new BranchAdministrationFixture(new BranchCommand("HT_01","Branch A","Address","0901234567",null)),
            e -> e.service.create((BranchCommand)e.cmd));
        define("TC-BRA-002", "Branch Management", "BranchAdministrationService.create",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Thiếu code\n" +
            "Request còn lại hợp lệ; lần lượt null, rỗng, chỉ khoảng trắng cho code",
            "400; không INSERT cơ sở",
            3,
            () -> new BranchAdministrationFixture(new BranchCommand("HT_01","Branch A","Address","0901234567",null)),
            e -> e.service.create((BranchCommand)e.cmd));
        define("TC-BRA-003", "Branch Management", "BranchAdministrationService.create",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Thiếu name\n" +
            "Request còn lại hợp lệ; lần lượt null, rỗng, chỉ khoảng trắng cho name",
            "400; không INSERT cơ sở",
            3,
            () -> new BranchAdministrationFixture(new BranchCommand("HT_01","Branch A","Address","0901234567",null)),
            e -> e.service.create((BranchCommand)e.cmd));
        define("TC-BRA-004", "Branch Management", "BranchAdministrationService.create",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Thiếu address\n" +
            "Request còn lại hợp lệ; lần lượt null, rỗng, chỉ khoảng trắng cho address",
            "400; không INSERT cơ sở",
            3,
            () -> new BranchAdministrationFixture(new BranchCommand("HT_01","Branch A","Address","0901234567",null)),
            e -> e.service.create((BranchCommand)e.cmd));
        define("TC-BRA-005", "Branch Management", "BranchAdministrationService.create",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Biên mã cơ sở\n" +
            "Code dài 1/2/60/61; có ký tự !; chữ thường",
            "Chấp nhận 2–60 ký tự [A-Z0-9_-] sau chuẩn hóa; từ chối các dữ liệu còn lại",
            6,
            () -> new BranchAdministrationFixture(new BranchCommand("HT_01","Branch A","Address","0901234567",null)),
            e -> e.service.create((BranchCommand)e.cmd));
        define("TC-BRA-006", "Branch Management", "BranchAdministrationService.create",
            "BranchAdministrationService thật, JdbcTemplate mock. Base command: code=HT_01,name=Cơ sở A,address=Địa chỉ " +
            "A,phone=0901234567,notes=null. Duplicate=false; INSERT=1; get(id) trả F branch ACTIVE. Reset mock/fixture " +
            "trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: name=\"N\"×200\n" +
            "V02: name=\"N\"×201\n" +
            "V03: address=\"A\"×2000\n" +
            "V04: address=\"A\"×2001\n" +
            "V05: notes=\"G\"×4000\n" +
            "V06: notes=\"G\"×4001",
            "V01,V03,V05: Trả F; INSERT nhận đúng trường đã chuẩn hóa.\n" +
            "V02,V04,V06: RSE400; không INSERT.",
            6,
            () -> new BranchAdministrationFixture(new BranchCommand("HT_01","Branch A","Address","0901234567",null)),
            e -> e.service.create((BranchCommand)e.cmd));
        define("TC-BRA-007", "Branch Management", "BranchAdministrationService.create",
            "BranchAdministrationService thật, JdbcTemplate mock. Base command: code=HT_01,name=Cơ sở A,address=Địa chỉ " +
            "A,phone=0901234567,notes=null. Duplicate=false; INSERT=1; get(id) trả F branch ACTIVE. Reset mock/fixture " +
            "trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: phone=\"0\"+\"1\"×5\n" +
            "V02: phone=\"0\"+\"1\"×6\n" +
            "V03: phone=\"0\"+\"1\"×14\n" +
            "V04: phone=\"0\"+\"1\"×15\n" +
            "V05: phone=null\n" +
            "V06: phone=\"\"\n" +
            "V07: phone=\"   \"\n" +
            "V08: phone=\"1901234\"\n" +
            "V09: phone=\"09012a4\"\n" +
            "V10: phone=\"090 1234\"\n" +
            "V11: phone=\"+0901234\"",
            "V01,V04,V08,V09,V10,V11: RSE400; không INSERT.\n" +
            "V02,V03: Trả F; INSERT nhận đúng trường đã chuẩn hóa.\n" +
            "V05,V06,V07: Trả F; INSERT phone=\"\".",
            11,
            () -> new BranchAdministrationFixture(new BranchCommand("HT_01","Branch A","Address","0901234567",null)),
            e -> e.service.create((BranchCommand)e.cmd));
        define("TC-BRA-008", "Branch Management", "BranchAdministrationService.create",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Mã trùng không phân biệt hoa thường\n" +
            "JdbcTemplate mock trả duplicate=true sau khi nhận code đã uppercase.",
            "ResponseStatusException 409; quan sát code chuẩn hóa truyền vào precheck; không gọi INSERT.",
            1,
            () -> new BranchAdministrationFixture(new BranchCommand("HT_01","Branch A","Address","0901234567",null)),
            e -> e.service.create((BranchCommand)e.cmd));
        define("TC-BRA-011", "Branch Management", "BranchAdministrationService.get",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Không tìm thấy chi tiết cơ sở\n" +
            "JdbcTemplate mock trả danh sách rỗng khi get(UUID).",
            "ResponseStatusException 404; không gọi ghi.",
            1,
            () -> new BranchAdministrationFixture(null),
            e -> e.service.get(B));
        define("TC-BRA-010", "Branch Management", "BranchAdministrationService.list",
            "BranchAdministrationService thật, JdbcTemplate mock; chỉ gọi list(), không gọi get(). Reset mock/fixture trước" +
            " mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: queryForList trả [F_A,F_B] theo thứ tự A,B.\n" +
            "V02: queryForList trả [].",
            "V01: list() trả đúng [F_A,F_B]; không ghi.\n" +
            "V02: list() trả []; không ghi.",
            2,
            () -> new BranchAdministrationFixture(null),
            e -> e.service.list());
        define("TC-BRA-013", "Branch Management", "BranchAdministrationService.update",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Cập nhật thông tin cơ sở\n" +
            "Mock khóa tìm thấy branch; duplicate=false; UPDATE=1; query đọc lại trả fixture after.",
            "Trả fixture after; lệnh UPDATE nhận tên/địa chỉ/phone/notes đã trim và đúng branchId. Không kiểm lịch sử DB.",
            1,
            () -> new BranchAdministrationFixture(new BranchCommand("HT_01","Branch A","Address","0901234567",null)),
            e -> e.service.update(B,(BranchCommand)e.cmd));
        define("TC-BRA-014", "Branch Management", "BranchAdministrationService.update",
            "BranchAdministrationService thật, JdbcTemplate mock. Base command như create; ID=B; lock có B; " +
            "duplicate=false; UPDATE=1; get(B) trả F_after có trường đã chuẩn hóa. Reset mock/fixture trước mỗi V; chỉ thay" +
            " dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: command=null\n" +
            "V02: code=null\n" +
            "V03: code=\"\"\n" +
            "V04: code=\"   \"\n" +
            "V05: code=\"!\"\n" +
            "V06: code=\"A\"\n" +
            "V07: code=\"A\"×61\n" +
            "V08: name=null\n" +
            "V09: name=\"\"\n" +
            "V10: name=\"   \"\n" +
            "V11: name=\"N\"×201\n" +
            "V12: address=null\n" +
            "V13: address=\"\"\n" +
            "V14: address=\"   \"\n" +
            "V15: address=\"A\"×2001\n" +
            "V16: phone=\"1901234\"\n" +
            "V17: phone=\"09012a4\"\n" +
            "V18: notes=\"G\"×4001\n" +
            "V19: command hợp lệ; duplicate=true.\n" +
            "V20: command.code=HT_01 bằng mã hiện tại; duplicate=false.",
            "V01,V02,V03,V04,V05,V06,V07,V08,V09,V10,V11,V12,V13,V14,V15,V16,V17,V18: RSE400; không UPDATE.\n" +
            "V19: RSE409; không UPDATE.\n" +
            "V20: Trả F_after; UPDATE đúng B,code HT_01.",
            20,
            () -> new BranchAdministrationFixture(new BranchCommand("HT_01","Branch A","Address","0901234567",null)),
            e -> e.service.update(B,(BranchCommand)e.cmd));
        define("TC-BRA-015", "Branch Management", "BranchAdministrationService.update",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Cập nhật ID không tồn tại\n" +
            "Input hợp lệ; UUID chưa có",
            "404; không cập nhật dữ liệu cơ sở khác",
            1,
            () -> new BranchAdministrationFixture(new BranchCommand("HT_01","Branch A","Address","0901234567",null)),
            e -> e.service.update(B,(BranchCommand)e.cmd));
        define("TC-BRA-017", "Branch Management", "BranchAdministrationService.assign",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Gán quản lý cho cơ sở\n" +
            "Mock user đủ điều kiện, branch ACTIVE, phân công hiện tại khác user đích; query đọc lại trả fixture after.",
            "Trả fixture after; quan sát các lệnh ghi audit, xóa phân công cũ, thêm phân công mới và cập nhật " +
            "user.branch_id với đúng ID.",
            1,
            () -> new BranchAdministrationFixture(null),
            e -> e.service.assign(B,(UUID)e.f.getOrDefault("user",M),e.actor));
        define("TC-BRA-018", "Branch Management", "BranchAdministrationService.assign",
            "BranchAdministrationService thật, JdbcTemplate mock. Actor U/HOM; userId=M (UUID); users=[M]; lock B có; " +
            "branch ACTIVE,managerId khác M; read-back F_after. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: userId=null.\n" +
            "V02: userId=M; query users trả [].",
            "V01: RSE400; không query users, không ghi.\n" +
            "V02: RSE409; không khóa/đổi phân công B.",
            2,
            () -> new BranchAdministrationFixture(null),
            e -> e.service.assign(B,(UUID)e.f.getOrDefault("user",M),e.actor));
        define("TC-BRA-019", "Branch Management", "BranchAdministrationService.assign",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Cơ sở đích không nhận phân công\n" +
            "ID không tồn tại; hoặc status SUSPENDED/CLOSED",
            "404 hoặc 409 tương ứng; phân công cũ được giữ",
            3,
            () -> new BranchAdministrationFixture(null),
            e -> e.service.assign(B,(UUID)e.f.getOrDefault("user",M),e.actor));
        define("TC-BRA-021", "Branch Management", "BranchAdministrationService.assign",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Gửi lại cùng phân công\n" +
            "Mock branch đã có đúng managerId cần gán.",
            "Trả thông tin branch; chỉ đồng bộ user.branch_id; không gọi thêm INSERT assignment hoặc audit thay người.",
            1,
            () -> new BranchAdministrationFixture(null),
            e -> e.service.assign(B,(UUID)e.f.getOrDefault("user",M),e.actor));
        define("TC-BRA-022", "Branch Management", "BranchAdministrationService.syncAccountManager",
            "BranchAdministrationService thật, JdbcTemplate mock. Account {code:BRANCH_MANAGER,status:ACTIVE,branch_id:B}; " +
            "actorRole=HEAD_OFFICE_MANAGER. Nhánh assign có users=[M], branch ACTIVE,managerId khác M; read-back F_after. " +
            "Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Account BRANCH_MANAGER/ACTIVE/B.\n" +
            "V02: Chỉ account.code=RECEPTIONIST.\n" +
            "V03: Chỉ account.status=REVOKED.\n" +
            "V04: Chỉ account.branch_id=null.",
            "V01: Gọi nhánh assign: INSERT assignment(B,M,U), audit ASSIGN; không UNASSIGN.\n" +
            "V02,V03,V04: Ghi audit UNASSIGN, xóa assignment M; không INSERT assignment.",
            4,
            () -> new BranchAdministrationFixture(null),
            e -> {
            e.service.syncAccountManager(M,U);
            return null;
        });
        define("TC-BRA-023", "Branch Management", "BranchAdministrationService.deactivate",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Ngừng cơ sở không có nghiệp vụ dở dang\n" +
            "Mock branch ACTIVE, không blocker; input SUSPENDED hoặc CLOSED; query sau UPDATE trả fixture after.",
            "Trả fixture after; quan sát status/reason đã trim trong UPDATE. Không chứng minh DB đã đổi trạng thái hay giữ " +
            "lịch sử.",
            2,
            () -> new BranchAdministrationFixture(new StatusCommand("SUSPENDED","Tạm ngừng")),
            e -> e.service.deactivate(B,(StatusCommand)e.cmd));
        define("TC-BRA-024", "Branch Management", "BranchAdministrationService.deactivate",
            "BranchAdministrationService thật, JdbcTemplate mock. ID=B; lock có B; current.status=ACTIVE; " +
            "command.status=SUSPENDED,reason=\"Tạm ngừng\"; blockers=[]; UPDATE=1; read-back F_after. Reset mock/fixture " +
            "trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: command=null\n" +
            "V02: status=null\n" +
            "V03: status=ACTIVE\n" +
            "V04: status=UNKNOWN\n" +
            "V05: reason=null\n" +
            "V06: reason=\"\"\n" +
            "V07: reason=\"   \"\n" +
            "V08: reason=\"R\"×2001\n" +
            "V09: reason=\"R\"×2000",
            "V01,V02,V03,V04,V05,V06,V07,V08: RSE400; không khóa/UPDATE B.\n" +
            "V09: Trả F_after; UPDATE SUSPENDED,reason dài2000.",
            9,
            () -> new BranchAdministrationFixture(new StatusCommand("SUSPENDED","Tạm ngừng")),
            e -> e.service.deactivate(B,(StatusCommand)e.cmd));
        define("TC-BRA-025", "Branch Management", "BranchAdministrationService.deactivate",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Chặn khi còn booking\n" +
            "Mock truy vấn blockers trả ít nhất một BOOKING blocker.",
            "409; không gọi UPDATE trạng thái branch. Các trạng thái booking là dữ liệu fixture, không kiểm SQL lọc đúng.",
            1,
            () -> new BranchAdministrationFixture(new StatusCommand("SUSPENDED","Tạm ngừng")),
            e -> e.service.deactivate(B,(StatusCommand)e.cmd));
        define("TC-BRA-026", "Branch Management", "BranchAdministrationService.deactivate",
            "BranchAdministrationService thật; JdbcTemplate mock. Stub lookup/count/update và bản ghi đọc lại cho đúng " +
            "nhánh.",
            "Chặn khi còn phòng hoặc bill đang xử lý\n" +
            "Mock blockers trả ROOM_SESSION hoặc BILL trong hai biến thể độc lập.",
            "409; không gọi UPDATE trạng thái branch; không chạy truy vấn trên database.",
            2,
            () -> new BranchAdministrationFixture(new StatusCommand("SUSPENDED","Tạm ngừng")),
            e -> e.service.deactivate(B,(StatusCommand)e.cmd));
        define("TC-BRA-027", "Branch Management", "BranchAdministrationService.deactivate",
            "BranchAdministrationService thật, JdbcTemplate mock. ID=B; lock có B; current.status=ACTIVE; " +
            "command.status=SUSPENDED,reason=\"Tạm ngừng\"; blockers=[]; UPDATE=1; read-back F_after. Reset mock/fixture " +
            "trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: current ACTIVE; target SUSPENDED.\n" +
            "V02: current ACTIVE; target CLOSED.\n" +
            "V03: current SUSPENDED; target SUSPENDED.\n" +
            "V04: current SUSPENDED; target CLOSED.\n" +
            "V05: current CLOSED; target CLOSED.\n" +
            "V06: current CLOSED; target SUSPENDED.",
            "V01: UPDATE SUSPENDED; trả F_after.\n" +
            "V02,V04: UPDATE CLOSED; trả F_after.\n" +
            "V03,V05: Trả fixture hiện trạng; không UPDATE/blockers.\n" +
            "V06: RSE409; không UPDATE/blockers.",
            6,
            () -> new BranchAdministrationFixture(new StatusCommand("SUSPENDED","Tạm ngừng")),
            e -> e.service.deactivate(B,(StatusCommand)e.cmd));
        define("TC-BRA-028", "Branch Management", "BranchAdministrationService.create",
            "BranchAdministrationService thật, JdbcTemplate mock. Base command: code=HT_01,name=Cơ sở A,address=Địa chỉ " +
            "A,phone=0901234567,notes=null. Duplicate=false; INSERT=1; get(id) trả F branch ACTIVE. Reset mock trước mỗi V;" +
            " chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO" +
            " Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Duplicate=false; INSERT ném DataIntegrityViolationException E.",
            "V01: RSE409; không get(id) để trả thành công; không giả đã lưu cơ sở.",
            1,
            () -> new BranchAdministrationFixture(new BranchCommand("HT_01","Branch A","Address","0901234567",null)),
            e -> e.service.create((BranchCommand)e.cmd));
        define("TC-BRA-030", "Branch Management", "BranchAdministrationService.get",
            "BranchAdministrationService thật, JdbcTemplate mock; id=B; queryForList projection trả fixture F hoặc[] theo " +
            "V. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; " +
            "snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: queryForList theo idB trả [F_branch] có branchIdB,codeHT_01,nameCơ sở A.",
            "V01: get(B) trả đúng F_branch; query nhận B; không ghi.",
            1,
            () -> new BranchAdministrationFixture(null),
            e -> e.service.get(B));
        define("TC-BRA-033", "Branch Management", "BranchAdministrationService.history",
            "BranchAdministrationService thật, JdbcTemplate mock; branch id=B; get(B) trả F_branch; query history trả " +
            "projection F_history hoặc[] theo V. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ " +
            "chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: get(B) có; query history trả [F_event].\n" +
            "V02: get(B) có; history query trả[].\n" +
            "V03: Lookup branch cho get(B) trả[].",
            "V01: Trả [F_event]; query nhận B; không ghi.\n" +
            "V02: Trả[]; không ghi.\n" +
            "V03: RSE404; không query history/ghi.",
            3,
            () -> new BranchAdministrationFixture(null),
            e -> e.service.history(B));
        define("TC-BRA-032", "Branch Management", "BranchAdministrationService.staff",
            "BranchAdministrationService thật, JdbcTemplate mock; branch id=B; get(B) trả F_branch; query staff trả " +
            "projection F_staff hoặc[] theo V. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: get(B) có; query staff trả [F_staff].\n" +
            "V02: get(B) có; query staff trả[].\n" +
            "V03: Lookup branch cho get(B) trả[].",
            "V01: Trả [F_staff]; query staff nhận B; không ghi.\n" +
            "V02: Trả[]; không ghi.\n" +
            "V03: RSE404; không query staff/ghi.",
            3,
            () -> new BranchAdministrationFixture(null),
            e -> e.service.staff(B));
        define("TC-BRA-029", "Branch Management", "BranchAdministrationService.update",
            "BranchAdministrationService thật, JdbcTemplate mock. Base command như create; ID=B; lock có B; " +
            "duplicate=false; UPDATE=1; get(B) trả F_after có trường đã chuẩn hóa. Reset mock trước mỗi V; chỉ thay phần " +
            "nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển" +
            " từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Lock có B,duplicate=false; UPDATE ném DataIntegrityViolationException E.",
            "V01: RSE409; không read-back thành công; không khẳng định rollback.",
            1,
            () -> new BranchAdministrationFixture(new BranchCommand("HT_01","Branch A","Address","0901234567",null)),
            e -> e.service.update(B,(BranchCommand)e.cmd));
        define("TC-BRA-031", "Branch Management", "BranchAdministrationService.managers",
            "BranchAdministrationService thật, JdbcTemplate mock; không nhận tham số input; chỉ query projection fixture " +
            "managers. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là " +
            "Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Query trả [F_manager] có userIdM,branchIdB.\n" +
            "V02: Query trả[].",
            "V01: Trả đúng list fixture; không ghi.\n" +
            "V02: Trả[]; không ghi.",
            2,
            () -> new BranchAdministrationFixture(null),
            e -> e.service.managers());
    }

    private void register() {
        branch();
    }

    private void branch() {
        Consumer<BranchAdministrationFixture> created=e-> {
            equal(e.actual,e.f.get("branchMap"),"Branch readback");
            e.sqlCount("INSERT INTO gcmb.branch(",1);
        };
        ok("BRA",1,e-> {
            e.command("code"," ht_01 ");
            e.command("name"," Branch A ");
            e.command("address"," Address ");
        },e-> {
            created.accept(e);
            sqlHas(e,"INSERT INTO gcmb.branch(","HT_01","Branch A","Address");
        });
        bad("BRA",2,"code",missing());
        bad("BRA",3,"name",missing());
        bad("BRA",4,"address",missing());
        for(String code:new String[] {
            "A","AB","A".repeat(60),"A".repeat(61),"A!","ab"
        })add(key("BRA",5),e->e.command("code",code),code.length()<2||code.length()>60||code.contains("!")?400:0,null,e-> {
            if(e.failure==null) {
                created.accept(e);
                sqlHas(e,"INSERT INTO gcmb.branch(",code.toUpperCase());
            }
            else e.noWrites();
        });
        for(var v:List.of(new Object[] {
            "name",200
        },new Object[] {
            "name",201
        },new Object[] {
            "address",2000
        },new Object[] {
            "address",2001
        },new Object[] {
            "notes",4000
        },new Object[] {
            "notes",4001
        })) {
            int n=(int)v[1];
            String f=(String)v[0];
            add(key("BRA",6),e->e.command(f,(f.equals("name")?"N":f.equals("address")?"A":"G").repeat(n)),n==201||n==2001||n==4001?400:0,null,e-> {
                if(e.failure==null)created.accept(e);
                else e.noWrites();
            });
        }
        String[] phones= {
            "0"+"1".repeat(5),"0"+"1".repeat(6),"0"+"1".repeat(14),"0"+"1".repeat(15),null,"","   ","1901234","09012a4","090 1234","+0901234"
        };
        for(int i=0;i<phones.length;i++) {
            String v=phones[i];
            boolean good=i==1||i==2||i>=4&&i<=6;
            add(key("BRA",7),e->e.command("phone",v),good?0:400,null,e-> {
                if(good) {
                    created.accept(e);
                    sqlHas(e,"INSERT INTO gcmb.branch(",v==null?"":v.trim());
                }
                else e.noWrites();
            });
        }
        error("BRA",8,409,e->e.answers.put("sql:SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE upper(code)=?)",true));
        error("BRA",11,404,e->e.f.put("branchMissing",true));
        for(boolean empty:new boolean[] {
            false,true
        })ok("BRA",10,e-> {
            if(empty)e.f.put("branchMissing",true);
        },e-> {
            equal(((List<?>)e.actual).size(),empty?0:1,"Branch list size");
            e.noWrites();
        });
        ok("BRA",13,e-> {
            e.command("name"," Branch A ");
            e.command("address"," Address ");
        },e-> {
            e.sqlCount("UPDATE gcmb.branch SET code",1);
            equal(e.actual,e.f.get("branchMap"),"Readback");
            sqlHas(e,"UPDATE gcmb.branch SET code","HT_01","Branch A","Address",B);
        });
        bad("BRA",14,"$command",(Object)null);
        bad("BRA",14,"code",null,"","   ","!","A","A".repeat(61));
        bad("BRA",14,"name",null,"","   ","N".repeat(201));
        bad("BRA",14,"address",null,"","   ","A".repeat(2001));
        bad("BRA",14,"phone","1901234","09012a4");
        bad("BRA",14,"notes","G".repeat(4001));
        error("BRA",14,409,e->e.answers.put("sql:AND id<>?",true));
        ok("BRA",14,e-> {
        },e->e.sqlCount("UPDATE gcmb.branch SET code",1));
        error("BRA",15,404,e->e.answers.put("sql:SELECT id FROM gcmb.branch WHERE id=? FOR UPDATE",List.of()));
        ok("BRA",17,e-> {
        },e-> {
            e.sqlCount("INSERT INTO gcmb.branch_manager_assignment(",1);
            sqlHas(e,"INSERT INTO gcmb.branch_manager_assignment(",B,M,U);
            e.sqlCount("UPDATE gcmb.user_account SET branch_id=?",1);
            e.sqlCount("UPDATE gcmb.user_account SET branch_id=NULL",1);
        });
        error("BRA",18,400,e->e.f.put("user",null));
        error("BRA",18,409,e->e.answers.put("sql:FOR UPDATE OF u",List.of()));
        error("BRA",19,404,e->e.answers.put("sql:SELECT id FROM gcmb.branch WHERE id=? FOR UPDATE",List.of()));
        for(String status:List.of("SUSPENDED","CLOSED"))error("BRA",19,409,e->((Map<String,Object>)e.f.get("branchMap")).put("status",status));
        ok("BRA",21,e->((Map<String,Object>)e.f.get("branchMap")).put("managerId",M),e-> {
            e.sqlCount("INSERT INTO gcmb.branch_manager_assignment(",0);
            e.sqlCount("INSERT INTO gcmb.audit_event",0);
            e.sqlCount("UPDATE gcmb.user_account SET branch_id",1);
        });
        for(int v=0;v<4;v++) {
            int n=v;
            ok("BRA",22,e-> {
                e.f.put("account",map("code",n==1?"RECEPTIONIST":"BRANCH_MANAGER","status",n==2?"REVOKED":"ACTIVE","branch_id",n==3?null:B));
            },e-> {
                e.sqlCount("INSERT INTO gcmb.branch_manager_assignment(",n==0?1:0);
                if(n!=0)e.sqlCount("DELETE FROM gcmb.branch_manager_assignment WHERE user_id",1);
            });
        }
        for(String status:List.of("SUSPENDED","CLOSED"))ok("BRA",23,e->e.command("status",status),e-> {
            e.sqlCount("UPDATE gcmb.branch SET status",1);
            sqlHas(e,"UPDATE gcmb.branch SET status",status,"Tạm ngừng",B);
        });
        bad("BRA",24,"$command",(Object)null);
        bad("BRA",24,"status",null,"ACTIVE","UNKNOWN");
        bad("BRA",24,"reason",null,"","   ","R".repeat(2001));
        ok("BRA",24,e->e.command("reason","R".repeat(2000)),e->sqlHas(e,"UPDATE gcmb.branch SET status","R".repeat(2000)));
        error("BRA",25,409,e->e.answers.put("sql:AS kind,status FROM gcmb.booking",List.of(Map.of("kind","BOOKING"))));
        for(String kind:List.of("ROOM_SESSION","BILL"))error("BRA",26,409,e->e.answers.put("sql:AS kind,status FROM gcmb.booking",List.of(Map.of("kind",kind))));
        String[][] transitions= {
            {
                "ACTIVE","SUSPENDED"
            }, {
                "ACTIVE","CLOSED"
            }, {
                "SUSPENDED","SUSPENDED"
            }, {
                "SUSPENDED","CLOSED"
            }, {
                "CLOSED","CLOSED"
            }, {
                "CLOSED","SUSPENDED"
            }
        };
        for(int i=0;i<transitions.length;i++) {
            var t=transitions[i];
            int n=i;
            add(key("BRA",27),e-> {
                ((Map<String,Object>)e.f.get("branchMap")).put("status",t[0]);
                e.command("status",t[1]);
            },i==5?409:0,null,e->e.sqlCount("UPDATE gcmb.branch SET status",n==2||n>=4?0:1));
        }
        error("BRA",28,409,e->e.answers.put("sql:INSERT INTO gcmb.branch(",new DataIntegrityViolationException("duplicate")));
        error("BRA",29,409,e->e.answers.put("sql:UPDATE gcmb.branch SET code",new DataIntegrityViolationException("duplicate")));
        ok("BRA",30,e-> {
        },e-> {
            equal(e.actual,e.f.get("branchMap"),"Branch detail");
            e.noWrites();
        });
        for(boolean empty:new boolean[] {
            false,true
        })ok("BRA",31,e->e.answers.put("sql:FROM gcmb.user_account u",empty?List.of():List.of(Map.of("id",M))),e-> {
            equal(((List<?>)e.actual).size(),empty?0:1,"Managers size");
            e.noWrites();
        });
        for(int n:new int[] {
            32,33
        }) {
            String query=n==32?"SELECT DISTINCT e.id":"SELECT a.id,a.action";
            for(boolean empty:new boolean[] {
                false,true
            })ok("BRA",n,e->e.answers.put("sql:"+query,empty?List.of():List.of(Map.of("id",Q))),e-> {
                equal(((List<?>)e.actual).size(),empty?0:1,"Projection size");
                sqlHas(e,query,B);
                e.noWrites();
            });
            error("BRA",n,404,e->e.f.put("branchMissing",true));
        }
    }
}
