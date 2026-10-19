/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Covers branch room service scenarios.
 */

package com.gcmb.backend.manual.administration.branch.service;

import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.branch.dto.RoomCommand;
import com.gcmb.backend.administration.branch.dto.RoomStatusCommand;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.support.ManualScenarios;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.dao.DataIntegrityViolationException;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class BranchRoomManualTest extends ManualScenarios<BranchRoomFixture> {
    private BranchRoomManualTest() {
        super("Administration","BranchRoomManualTest");
    }

    public static List<ManualTestSuite> suites() {
        var tests=new BranchRoomManualTest();
        tests.describeCases();
        tests.register();
        return tests.buildSuites();
    }

    private void describeCases() {
        define("TC-ROM-001", "Room Management", "BranchRoomService.create",
            "BranchRoomService thật; BranchRoomRepository mock; Identity fixture có branchId=A nếu không kiểm nhánh thiếu " +
            "scope.",
            "Tạo phòng tại cơ sở quản lý\n" +
            "Identity branch A; RoomCommand hợp lệ; BranchRoomRepository.create mock trả RoomView fixture.",
            "Trả fixture; quan sát create(A, command) được gọi; không nhận branch tùy ý từ client.",
            1,
            () -> new BranchRoomFixture(new RoomCommand("P01","Room A",1,RT,1,null)),
            e -> e.service.create(e.actor,(RoomCommand)e.cmd));
        define("TC-ROM-002", "Room Management", "BranchRoomService.create",
            "BranchRoomService thật, BranchRoomRepository mock. Identity U/BRANCH_MANAGER/branchId=B. RoomCommand " +
            "code=P01,name=Phòng 01,roomTypeId=RT (UUID),floorNumber=1 (Integer),capacity=1 (Integer). " +
            "repo.create(B,command) trả F RoomView. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: code=null\n" +
            "V02: code=\"\"\n" +
            "V03: code=\"   \"",
            "V01,V02,V03: RSE400; không repo.create.",
            3,
            () -> new BranchRoomFixture(new RoomCommand("P01","Room A",1,RT,1,null)),
            e -> e.service.create(e.actor,(RoomCommand)e.cmd));
        define("TC-ROM-003", "Room Management", "BranchRoomService.create",
            "BranchRoomService thật, BranchRoomRepository mock. Identity U/BRANCH_MANAGER/branchId=B. RoomCommand " +
            "code=P01,name=Phòng 01,roomTypeId=RT (UUID),floorNumber=1 (Integer),capacity=1 (Integer). " +
            "repo.create(B,command) trả F RoomView. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: name=null\n" +
            "V02: name=\"\"\n" +
            "V03: name=\"   \"",
            "V01,V02,V03: RSE400; không repo.create.",
            3,
            () -> new BranchRoomFixture(new RoomCommand("P01","Room A",1,RT,1,null)),
            e -> e.service.create(e.actor,(RoomCommand)e.cmd));
        define("TC-ROM-004", "Room Management", "BranchRoomService.create",
            "BranchRoomService thật, BranchRoomRepository mock. Identity U/BRANCH_MANAGER/branchId=B. RoomCommand " +
            "code=P01,name=Phòng 01,roomTypeId=RT (UUID),floorNumber=1 (Integer),capacity=1 (Integer). " +
            "repo.create(B,command) trả F RoomView. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: roomTypeId=null (UUID).",
            "V01: RSE400; không repo.create.",
            1,
            () -> new BranchRoomFixture(new RoomCommand("P01","Room A",1,RT,1,null)),
            e -> e.service.create(e.actor,(RoomCommand)e.cmd));
        define("TC-ROM-005", "Room Management", "BranchRoomService.create",
            "BranchRoomService thật, BranchRoomRepository mock. Identity U/BRANCH_MANAGER/branchId=B. RoomCommand " +
            "code=P01,name=Phòng 01,roomTypeId=RT (UUID),floorNumber=1 (Integer),capacity=1 (Integer). " +
            "repo.create(B,command) trả F RoomView. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: floorNumber=null (Integer).",
            "V01: RSE400; không repo.create.",
            1,
            () -> new BranchRoomFixture(new RoomCommand("P01","Room A",1,RT,1,null)),
            e -> e.service.create(e.actor,(RoomCommand)e.cmd));
        define("TC-ROM-006", "Room Management", "BranchRoomService.create",
            "BranchRoomService thật, BranchRoomRepository mock. Identity U/BRANCH_MANAGER/branchId=B. RoomCommand " +
            "code=P01,name=Phòng 01,roomTypeId=RT (UUID),floorNumber=1 (Integer),capacity=1 (Integer). " +
            "repo.create(B,command) trả F RoomView. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: capacity=null\n" +
            "V02: capacity=0\n" +
            "V03: capacity=-1\n" +
            "V04: capacity=1",
            "V01,V02,V03: RSE400; không repo.create.\n" +
            "V04: Trả F; repo.create nhận capacity1.",
            4,
            () -> new BranchRoomFixture(new RoomCommand("P01","Room A",1,RT,1,null)),
            e -> e.service.create(e.actor,(RoomCommand)e.cmd));
        define("TC-ROM-008", "Room Management", "BranchRoomService.create",
            "BranchRoomService thật; BranchRoomRepository mock; Identity fixture có branchId=A nếu không kiểm nhánh thiếu " +
            "scope.",
            "Dịch lỗi tạo phòng từ repository\n" +
            "Mock repo.create ném DataIntegrityViolationException.",
            "ResponseStatusException 409 với lỗi mã/loại phòng; không coi đã tạo thành công.",
            1,
            () -> new BranchRoomFixture(new RoomCommand("P01","Room A",1,RT,1,null)),
            e -> e.service.create(e.actor,(RoomCommand)e.cmd));
        define("TC-ROM-007", "Room Management", "BranchRoomService.list",
            "BranchRoomService thật; BranchRoomRepository mock; Identity fixture có branchId=A nếu không kiểm nhánh thiếu " +
            "scope.",
            "Chưa được gán cơ sở\n" +
            "Identity null hoặc branchId null",
            "403 trước khi đọc/ghi repository",
            2,
            () -> new BranchRoomFixture(null),
            e -> e.service.list(e.actor));
        define("TC-ROM-010", "Room Management", "BranchRoomService.update",
            "BranchRoomService thật; BranchRoomRepository mock; Identity fixture có branchId=A nếu không kiểm nhánh thiếu " +
            "scope.",
            "Sửa phòng đúng phạm vi\n" +
            "Identity A; command hợp lệ; mock repo.update(A,id,command) trả fixture.",
            "Trả fixture; gọi repository với đúng branch/id/command; không kiểm lịch sử DB.",
            1,
            () -> new BranchRoomFixture(new RoomCommand("P01","Room A",1,RT,1,null)),
            e -> e.service.update(e.actor,R,(RoomCommand)e.cmd));
        define("TC-ROM-011", "Room Management", "BranchRoomService.update",
            "BranchRoomService thật; BranchRoomRepository mock; Identity fixture có branchId=A nếu không kiểm nhánh thiếu " +
            "scope.",
            "Repository không tìm thấy phòng để sửa\n" +
            "Mock repo.update(A,id,command) trả null.",
            "404; chỉ truyền scope A vào repository; không chứng minh repository tự lọc branch đúng.",
            1,
            () -> new BranchRoomFixture(new RoomCommand("P01","Room A",1,RT,1,null)),
            e -> e.service.update(e.actor,R,(RoomCommand)e.cmd));
        define("TC-ROM-012", "Room Management", "BranchRoomService.update",
            "BranchRoomService thật, BranchRoomRepository mock. Identity U/BRANCH_MANAGER/branchId=B; roomId=R. Command " +
            "P01/Phòng 01/RT (UUID)/floorNumber=1/capacity=1. repo.update(B,R,command) trả F RoomView. Reset mock/fixture " +
            "trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: command=null\n" +
            "V02: code=null\n" +
            "V03: code=\"\"\n" +
            "V04: code=\"   \"\n" +
            "V05: name=null\n" +
            "V06: name=\"\"\n" +
            "V07: name=\"   \"\n" +
            "V08: roomTypeId=null\n" +
            "V09: floorNumber=null\n" +
            "V10: capacity=null\n" +
            "V11: capacity=0\n" +
            "V12: capacity=-1\n" +
            "V13: Command base; repo.update ném DataIntegrityViolationException.",
            "V01,V02,V03,V04,V05,V06,V07,V08,V09,V10,V11,V12: RSE400; không repo.update.\n" +
            "V13: RSE409; không trả F thành công.",
            13,
            () -> new BranchRoomFixture(new RoomCommand("P01","Room A",1,RT,1,null)),
            e -> e.service.update(e.actor,R,(RoomCommand)e.cmd));
        define("TC-ROM-013", "Room Management", "BranchRoomService.status",
            "BranchRoomService thật; BranchRoomRepository mock; Identity fixture có branchId=A nếu không kiểm nhánh thiếu " +
            "scope.",
            "Ngừng hoạt động phòng\n" +
            "Mock repo.changeStatus trả RoomView INACTIVE; command status INACTIVE, reason hợp lệ.",
            "Trả fixture; changeStatus nhận đúng branchId, roomId, actorId, status/reason; không kiểm history/availability " +
            "thật.",
            1,
            () -> new BranchRoomFixture(new RoomStatusCommand("CLEANING","Vệ sinh",null)),
            e -> e.service.status(e.actor,R,(RoomStatusCommand)e.cmd));
        define("TC-ROM-015", "Room Management", "BranchRoomService.delete",
            "BranchRoomService thật; BranchRoomRepository mock; Identity fixture có branchId=A nếu không kiểm nhánh thiếu " +
            "scope.",
            "Xóa phòng khi repository báo thành công\n" +
            "Mock repo.delete trả true.",
            "Hoàn tất không exception; repo.delete nhận đúng branchId và roomId.",
            1,
            () -> new BranchRoomFixture(null),
            e -> {
            e.service.delete(e.actor,R);
            return null;
        });
        define("TC-ROM-016", "Room Management", "BranchRoomService.delete",
            "BranchRoomService thật; BranchRoomRepository mock; Identity fixture có branchId=A nếu không kiểm nhánh thiếu " +
            "scope.",
            "Repository từ chối xóa phòng\n" +
            "Mock repo.delete trả false.",
            "409 kèm hướng dẫn ngừng hoạt động; không coi đã xóa. Không kiểm SQL phát hiện allocation/asset.",
            1,
            () -> new BranchRoomFixture(null),
            e -> {
            e.service.delete(e.actor,R);
            return null;
        });
        define("TC-ROM-017", "Room Management", "BranchRoomService.delete",
            "BranchRoomService thật; BranchRoomRepository mock; Identity fixture có branchId=A nếu không kiểm nhánh thiếu " +
            "scope.",
            "Truyền scope vào thao tác xóa\n" +
            "Identity A; UUID bất kỳ; repo.delete(A,id) mock trả false.",
            "Quan sát scope A truyền xuống repo; exception 409 theo code. Không kiểm truy cập xuyên cơ sở qua HTTP.",
            1,
            () -> new BranchRoomFixture(null),
            e -> {
            e.service.delete(e.actor,R);
            return null;
        });
        define("TC-ROM-018", "Room Management", "BranchRoomService.list",
            "BranchRoomService thật; BranchRoomRepository mock; Identity fixture có branchId=A nếu không kiểm nhánh thiếu " +
            "scope.",
            "Liệt kê phòng với scope lấy từ Identity\n" +
            "Identity có branch A; mock repository.list(A) trả danh sách RoomView fixture.",
            "Trả danh sách fixture và gọi list đúng branch A; không kiểm role HTTP, trạng thái tổng hợp hoặc SQL.",
            2,
            () -> new BranchRoomFixture(null),
            e -> e.service.list(e.actor));
        define("TC-ROM-021", "Room Management", "BranchRoomService.status",
            "BranchRoomService thật, BranchRoomRepository mock. Identity U/BRANCH_MANAGER/branchId=B; roomId=R; " +
            "reason=\"Kiểm tra phòng\". changeStatus(B,R,U,command) trả F có status bằng command.status. Reset mock/fixture " +
            "trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: status=AVAILABLE; reason=\"Kiểm tra phòng\"; repo trả F_AVAILABLE.\n" +
            "V02: status=CLEANING; reason=\"Kiểm tra phòng\"; repo trả F_CLEANING.\n" +
            "V03: status=UNDER_MAINTENANCE; reason=\"Kiểm tra phòng\"; repo trả F_UNDER_MAINTENANCE.\n" +
            "V04: status=INACTIVE; reason=\"Kiểm tra phòng\"; repo trả F_INACTIVE.",
            "V01: Trả F_AVAILABLE; changeStatus nhận(B,R,U,status=AVAILABLE,reason); không gọi service khác.\n" +
            "V02: Trả F_CLEANING; changeStatus nhận(B,R,U,status=CLEANING,reason); không gọi service khác.\n" +
            "V03: Trả F_UNDER_MAINTENANCE; changeStatus nhận(B,R,U,status=UNDER_MAINTENANCE,reason); không gọi service " +
            "khác.\n" +
            "V04: Trả F_INACTIVE; changeStatus nhận(B,R,U,status=INACTIVE,reason); không gọi service khác.",
            4,
            () -> new BranchRoomFixture(new RoomStatusCommand("CLEANING","Vệ sinh",null)),
            e -> e.service.status(e.actor,R,(RoomStatusCommand)e.cmd));
        define("TC-ROM-022", "Room Management", "BranchRoomService.status",
            "BranchRoomService thật; BranchRoomRepository mock; Identity hợp lệ có id và branchId=A. Gọi status(who, " +
            "roomId, command), không gọi controller/DB. Reset mock trước mỗi V. Command chuẩn: status=CLEANING, reason=\"Vệ " +
            "sinh\".",
            "V01: command=null.\n" +
            "V02: command chuẩn nhưng status=null.\n" +
            "V03: status=UNKNOWN, lý do hợp lệ.\n" +
            "V04: status=CLEANING, reason=null.\n" +
            "V05: status=CLEANING, reason=\"   \".",
            "V01–V05: ResponseStatusException với statusCode=400; không trả RoomView; repo.changeStatus không được gọi.\n" +
            "V02: NullPointerException là sai expected; phải trả lỗi 400 có kiểm soát. Chỉ kết luận không gọi dependency " +
            "ghi trạng thái, không suy ra history/DB thực tế.",
            5,
            () -> new BranchRoomFixture(new RoomStatusCommand("CLEANING","Vệ sinh",null)),
            e -> e.service.status(e.actor,R,(RoomStatusCommand)e.cmd));
        define("TC-ROM-023", "Room Management", "BranchRoomService.status",
            "BranchRoomService thật; BranchRoomRepository mock; Identity fixture có branchId=A nếu không kiểm nhánh thiếu " +
            "scope.",
            "Repository không tìm thấy phòng để đổi trạng thái\n" +
            "Mock repo.changeStatus trả null.",
            "404; không trả RoomView thành công.",
            1,
            () -> new BranchRoomFixture(new RoomStatusCommand("CLEANING","Vệ sinh",null)),
            e -> e.service.status(e.actor,R,(RoomStatusCommand)e.cmd));
        define("TC-ROM-028", "Room Management", "BranchRoomService.create",
            "BranchRoomService thật, BranchRoomRepository mock. Identity U/BRANCH_MANAGER/branchId=B. RoomCommand " +
            "code=P01,name=Phòng 01,roomTypeId=RT (UUID),floorNumber=1 (Integer),capacity=1 (Integer). " +
            "repo.create(B,command) trả F RoomView. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ " +
            "chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Identity có branchB; command=null.",
            "V01: RSE400; không repo.create.",
            1,
            () -> new BranchRoomFixture(new RoomCommand("P01","Room A",1,RT,1,null)),
            e -> e.service.create(e.actor,(RoomCommand)e.cmd));
        define("TC-ROM-029", "Room Management", "BranchRoomService.types",
            "BranchRoomService thật, BranchRoomRepository mock; Identity U/BRANCH_MANAGER có branchIdB; repo.types trả " +
            "F_types hoặc[] theo V. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp " +
            "map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Identity có branchB; repo.types trả [F_type RT].\n" +
            "V02: Identity có branchB; repo.types trả[].\n" +
            "V03: Identity.branchId=null.",
            "V01: Trả list fixture; chỉ gọi repo.types,không ghi.\n" +
            "V02: Trả[]; không ghi.\n" +
            "V03: RSE403; không repo.types/ghi.",
            3,
            () -> new BranchRoomFixture(null),
            e -> e.service.types(e.actor));
    }

    private void register() {
        rooms();
    }

    private void rooms() {
        Consumer<BranchRoomFixture> read=e->equal(e.actual,e.f.get("roomView"),"Room fixture");
        ok("ROM",1,e-> {
        },e-> {
            read.accept(e);
            e.count(e.rooms,"create",1);
            equal(e.inv(e.rooms,"create").get(0).getArgument(0),B,"Scope");
        });
        bad("ROM",2,"code",missing());
        bad("ROM",3,"name",missing());
        bad("ROM",4,"roomTypeId",(Object)null);
        bad("ROM",5,"floorNumber",(Object)null);
        bad("ROM",6,"capacity",null,0,-1);
        ok("ROM",6,e->e.command("capacity",1),e-> {
            read.accept(e);
            e.count(e.rooms,"create",1);
        });
        add(key("ROM",8),e->e.answers.put("room.create",new DataIntegrityViolationException("duplicate")),409,null,e->e.count(e.rooms,"create",1));
        for(boolean noIdentity:new boolean[] {
            false,true
        })error("ROM",7,403,e->e.actor=noIdentity?null:new Identity(U,"staff","BRANCH_MANAGER","/",null,null));
        ok("ROM",10,e-> {
        },e-> {
            read.accept(e);
            e.count(e.rooms,"update",1);
            equal(e.inv(e.rooms,"update").get(0).getArgument(0),B,"Update scope");
        });
        add(key("ROM",11),e->e.answers.put("room.update",null),404,null,e->e.count(e.rooms,"update",1));
        bad("ROM",12,"$command",(Object)null);
        bad("ROM",12,"code",missing());
        bad("ROM",12,"name",missing());
        bad("ROM",12,"roomTypeId",(Object)null);
        bad("ROM",12,"floorNumber",(Object)null);
        bad("ROM",12,"capacity",null,0,-1);
        add(key("ROM",12),e->e.answers.put("room.update",new DataIntegrityViolationException("duplicate")),409,null,e->e.count(e.rooms,"update",1));
        ok("ROM",13,e->e.command("status","INACTIVE"),e-> {
            read.accept(e);
            equal(field(e.inv(e.rooms,"changeStatus").get(0).getArgument(3),"status"),"INACTIVE","Status argument");
        });
        ok("ROM",15,e-> {
        },e->e.count(e.rooms,"delete",1));
        add(key("ROM",16),e->e.answers.put("room.delete",false),409,null,e->e.count(e.rooms,"delete",1));
        ok("ROM",17,e-> {
        },e-> {
            e.count(e.rooms,"delete",1);
            equal(e.inv(e.rooms,"delete").get(0).getArgument(0),B,"Delete scope");
        });
        for(boolean empty:new boolean[] {
            false,true
        })ok("ROM",18,e-> {
            if(empty)e.answers.put("room.list",List.of());
        },e-> {
            equal(((List<?>)e.actual).size(),empty?0:1,"Room list");
            equal(e.inv(e.rooms,"list").get(0).getArgument(0),B,"List scope");
            e.noWrites();
        });
        for(String status:List.of("AVAILABLE","CLEANING","UNDER_MAINTENANCE","INACTIVE"))ok("ROM",21,e->e.command("status",status),e-> {
            read.accept(e);
            equal(field(e.inv(e.rooms,"changeStatus").get(0).getArgument(3),"status"),status,"Status passed unchanged");
        });
        bad("ROM",22,"$command",(Object)null);
        bad("ROM",22,"status",null,"UNKNOWN");
        bad("ROM",22,"reason",null,"   ");
        add(key("ROM",23),e->e.answers.put("room.changeStatus",null),404,null,e->e.count(e.rooms,"changeStatus",1));
        bad("ROM",28,"$command",(Object)null);
        for(boolean empty:new boolean[] {
            false,true
        })ok("ROM",29,e-> {
            if(empty)e.answers.put("room.types",List.of());
        },e-> {
            equal(((List<?>)e.actual).size(),empty?0:1,"Types");
            e.noWrites();
        });
        error("ROM",29,403,e->e.actor=new Identity(U,"staff","BRANCH_MANAGER","/",null,null));
    }
}
