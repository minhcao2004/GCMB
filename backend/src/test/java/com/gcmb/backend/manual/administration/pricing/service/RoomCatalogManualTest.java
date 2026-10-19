/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Covers room type, price rule and combo catalogue service scenarios.
 */

package com.gcmb.backend.manual.administration.pricing.service;

import com.gcmb.backend.administration.pricing.dto.PriceCommand;
import com.gcmb.backend.administration.pricing.dto.PriceRuleCommand;
import com.gcmb.backend.administration.pricing.dto.RoomTypeCommand;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.support.ManualScenarios;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.dao.DataIntegrityViolationException;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class RoomCatalogManualTest extends ManualScenarios<RoomCatalogFixture> {
    private RoomCatalogManualTest() {
        super("Administration","RoomCatalogManualTest");
    }

    public static List<ManualTestSuite> suites() {
        var tests=new RoomCatalogManualTest();
        tests.describeCases();
        tests.register();
        return tests.buildSuites();
    }

    private void describeCases() {
        define("TC-RTY-001", "Room Type Management", "RoomCatalogService.createRoomType",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Tạo hạng phòng hợp lệ\n" +
            "HOM; code \" queen_01 \"; mode CINEMA hoặc MUSIC_BOX; capacity 1",
            "Trả fixture RoomTypeView; quan sát code uppercase, các giá trị đã trim và active=true trong lời gọi ghi.",
            2,
            () -> new RoomCatalogFixture(new RoomTypeCommand("QUEEN_01","Queen","CINEMA",1,null)),
            e -> e.service.createRoomType((RoomTypeCommand)e.cmd));
        define("TC-RTY-002", "Room Type Management", "RoomCatalogService.createRoomType",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Validation code\n" +
            "code: null, rỗng, chỉ khoảng trắng, ký tự !. Mỗi biến thể chạy riêng; tất cả trường còn lại hợp lệ.",
            "400 trước khi ghi DB",
            4,
            () -> new RoomCatalogFixture(new RoomTypeCommand("QUEEN_01","Queen","CINEMA",1,null)),
            e -> e.service.createRoomType((RoomTypeCommand)e.cmd));
        define("TC-RTY-003", "Room Type Management", "RoomCatalogService.createRoomType",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Validation name\n" +
            "name: null, rỗng hoặc chỉ khoảng trắng. Mỗi biến thể chạy riêng; tất cả trường còn lại hợp lệ.",
            "400 trước khi ghi DB",
            3,
            () -> new RoomCatalogFixture(new RoomTypeCommand("QUEEN_01","Queen","CINEMA",1,null)),
            e -> e.service.createRoomType((RoomTypeCommand)e.cmd));
        define("TC-RTY-004", "Room Type Management", "RoomCatalogService.createRoomType",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Validation capacity\n" +
            "capacity: null, 0 hoặc -1. Mỗi biến thể chạy riêng; tất cả trường còn lại hợp lệ.",
            "400 trước khi ghi DB",
            3,
            () -> new RoomCatalogFixture(new RoomTypeCommand("QUEEN_01","Queen","CINEMA",1,null)),
            e -> e.service.createRoomType((RoomTypeCommand)e.cmd));
        define("TC-RTY-005", "Room Type Management", "RoomCatalogService.createRoomType",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Validation serviceType\n" +
            "serviceType: null hoặc UNKNOWN. Mỗi biến thể chạy riêng; tất cả trường còn lại hợp lệ.",
            "400 trước khi ghi DB",
            2,
            () -> new RoomCatalogFixture(new RoomTypeCommand("QUEEN_01","Queen","CINEMA",1,null)),
            e -> e.service.createRoomType((RoomTypeCommand)e.cmd));
        define("TC-RTY-006", "Room Type Management", "RoomCatalogService.createRoomType",
            "RoomCatalogService thật, JdbcTemplate mock. Command: " +
            "code=QUEEN_01,name=Queen,serviceType=CINEMA,capacity=1,description=null. Duplicate=0; INSERT=1; read-back F " +
            "RoomTypeView. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: code=\"A\"\n" +
            "V02: code=\"A\"×61\n" +
            "V03: code=\"AB\"\n" +
            "V04: code=\"A\"×60\n" +
            "V05: name=\"N\"×200\n" +
            "V06: name=\"N\"×201",
            "V01,V02,V06: RSE400; không INSERT.\n" +
            "V03,V04: Trả F; INSERT code đúng input.\n" +
            "V05: Trả F; INSERT name dài200.",
            6,
            () -> new RoomCatalogFixture(new RoomTypeCommand("QUEEN_01","Queen","CINEMA",1,null)),
            e -> e.service.createRoomType((RoomTypeCommand)e.cmd));
        define("TC-RTY-007", "Room Type Management", "RoomCatalogService.createRoomType",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Từ chối mã hạng trùng hoặc dịch lỗi ghi\n" +
            "Mock duplicate count >0; biến thể khác precheck=0 nhưng INSERT ném DataIntegrityViolationException.",
            "409 trong từng biến thể; không có kết quả tạo thành công. Không chạy hai request đồng thời.",
            2,
            () -> new RoomCatalogFixture(new RoomTypeCommand("QUEEN_01","Queen","CINEMA",1,null)),
            e -> e.service.createRoomType((RoomTypeCommand)e.cmd));
        define("TC-RTY-008", "Room Type Management", "RoomCatalogService.roomTypes",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Xem danh sách hạng phòng\n" +
            "Có active/inactive; danh sách rỗng",
            "Trả danh sách fixture do JdbcTemplate mock cung cấp; danh sách rỗng hợp lệ; không gọi ghi. Không kiểm ORDER " +
            "BY.",
            2,
            () -> new RoomCatalogFixture(null),
            e -> e.service.roomTypes());
        define("TC-RTY-010", "Room Type Management", "RoomCatalogService.updateRoomType",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Sửa hạng phòng giữ mã\n" +
            "Mock hạng cũ có code QUEEN; command code queen; UPDATE=1; query đọc lại trả fixture.",
            "Trả fixture; quan sát UPDATE chỉ nhận trường cho phép; không ghi vào booking/charge.",
            1,
            () -> new RoomCatalogFixture(new RoomTypeCommand("QUEEN_01","Queen","CINEMA",1,null)),
            e -> e.service.updateRoomType(RT,(RoomTypeCommand)e.cmd));
        define("TC-RTY-011", "Room Type Management", "RoomCatalogService.updateRoomType",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Không cho đổi mã và sửa ID thiếu\n" +
            "Đổi code sang mã khác; hoặc ID không có",
            "Đổi mã trả 400; ID thiếu trả 404; không ghi thay đổi",
            2,
            () -> new RoomCatalogFixture(new RoomTypeCommand("QUEEN_01","Queen","CINEMA",1,null)),
            e -> e.service.updateRoomType(RT,(RoomTypeCommand)e.cmd));
        define("TC-RTY-012", "Room Type Management", "RoomCatalogService.updateRoomType",
            "RoomCatalogService thật, JdbcTemplate mock. ID=RT; command " +
            "code=QUEEN_01,name=Queen,serviceType=CINEMA,capacity=1,description=null; current.code=QUEEN_01; UPDATE=1; " +
            "read-back F_after. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
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
            "V12: capacity=null\n" +
            "V13: capacity=0\n" +
            "V14: capacity=-1\n" +
            "V15: serviceType=null\n" +
            "V16: serviceType=UNKNOWN",
            "V01,V02,V03,V04,V05,V06,V07,V08,V09,V10,V11,V12,V13,V14,V15,V16: RSE400; không UPDATE.",
            16,
            () -> new RoomCatalogFixture(new RoomTypeCommand("QUEEN_01","Queen","CINEMA",1,null)),
            e -> e.service.updateRoomType(RT,(RoomTypeCommand)e.cmd));
        define("TC-RTY-013", "Room Type Management", "RoomCatalogService.deactivateRoomType",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Ngừng sử dụng hạng phòng\n" +
            "Mock UPDATE active=false trả 1, sau đó query trả fixture inactive.",
            "Trả fixture inactive; quan sát UPDATE active=false đúng ID. Không kiểm việc bộ chọn booking thật đã loại hạng " +
            "này.",
            1,
            () -> new RoomCatalogFixture(null),
            e -> e.service.deactivateRoomType(RT));
        define("TC-RTY-014", "Room Type Management", "RoomCatalogService.deactivateRoomType",
            "RoomCatalogService thật, JdbcTemplate mock. ID=RT; UPDATE active=false trả1; read-back F inactive. Không kết " +
            "nối DB. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: UPDATE active=false trả0; không có row đọc lại.\n" +
            "V02: Hạng đã inactive; UPDATE active=false trả1; read-back F inactive.",
            "V01: RSE404; không read-back/DELETE.\n" +
            "V02: Trả F inactive; có UPDATE active=false; không DELETE.",
            2,
            () -> new RoomCatalogFixture(null),
            e -> e.service.deactivateRoomType(RT));
        define("TC-RTY-015", "Room Type Management", "RoomCatalogService.deleteRoomType",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Xóa hạng phòng chưa dùng\n" +
            "Mock row khóa có ID; used=false; DELETE thành công.",
            "Hoàn tất không exception; có lời gọi DELETE đúng ID sau kiểm tra used.",
            1,
            () -> new RoomCatalogFixture(null),
            e -> {
            e.service.deleteRoomType(RT);
            return null;
        });
        define("TC-RTY-016", "Room Type Management", "RoomCatalogService.deleteRoomType",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Chặn xóa hạng có phụ thuộc\n" +
            "Mock used=true; biến thể used=false nhưng DELETE ném DataIntegrityViolationException.",
            "409; used=true không gọi DELETE; lỗi dependency được dịch thành conflict. Không kiểm FK hoặc bảo toàn dữ liệu " +
            "DB.",
            2,
            () -> new RoomCatalogFixture(null),
            e -> {
            e.service.deleteRoomType(RT);
            return null;
        });
        define("TC-RTY-017", "Room Type Management", "RoomCatalogService.deleteRoomType",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Xóa hạng không tồn tại\n" +
            "Mock truy vấn row khóa trả danh sách rỗng.",
            "404; không kiểm used/không gọi DELETE. Loại phần tranh chấp đồng thời của ca cũ.",
            1,
            () -> new RoomCatalogFixture(null),
            e -> {
            e.service.deleteRoomType(RT);
            return null;
        });
        define("TC-RTY-018", "Room Type Management", "RoomCatalogService.createRoomType",
            "RoomCatalogService thật, JdbcTemplate mock. Command: " +
            "code=QUEEN_01,name=Queen,serviceType=CINEMA,capacity=1,description=null. Duplicate=0; INSERT=1; read-back F " +
            "RoomTypeView. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là " +
            "Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: command=null.",
            "V01: RSE400; không query duplicate/INSERT/read-back.",
            1,
            () -> new RoomCatalogFixture(new RoomTypeCommand("QUEEN_01","Queen","CINEMA",1,null)),
            e -> e.service.createRoomType((RoomTypeCommand)e.cmd));
        define("TC-RTY-019", "Room Type Management", "RoomCatalogService.updateRoomType",
            "RoomCatalogService thật, JdbcTemplate mock. ID=RT; command " +
            "code=QUEEN_01,name=Queen,serviceType=CINEMA,capacity=1,description=null; current.code=QUEEN_01; UPDATE=1; " +
            "read-back F_after. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map " +
            "là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Current RT/codeQUEEN_01 có; command hợp lệ; UPDATE=0.",
            "V01: RSE404; không đọc lại hạng để trả thành công.",
            1,
            () -> new RoomCatalogFixture(new RoomTypeCommand("QUEEN_01","Queen","CINEMA",1,null)),
            e -> e.service.updateRoomType(RT,(RoomTypeCommand)e.cmd));
        define("TC-RPR-001", "Room Price Management", "RoomCatalogService.createPriceRule",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Tạo quy tắc giá hợp lệ\n" +
            "HOM; mã/tên/hạng phòng; weekdays [1,2,3,4,5]; validFrom; cửa sổ 07:00–23:00; giá 120000",
            "Trả fixture PriceRuleView; PreparedStatement mock nhận đúng code, version, weekdays, thời gian và giá. Không " +
            "kết luận weekdays đã được áp dụng lúc báo giá.",
            1,
            () -> new RoomCatalogFixture(new PriceRuleCommand("PRICE_A","Giá A",RT,List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"))),
            e -> e.service.createPriceRule((PriceRuleCommand)e.cmd));
        define("TC-RPR-002", "Room Price Management", "RoomCatalogService.createPriceRule",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Trường bắt buộc của quy tắc giá\n" +
            "Lần lượt null request/code/name/roomTypeId/weekdays/validFrom/minuteFrom/minuteTo/hourlyPrice",
            "400; không INSERT",
            9,
            () -> new RoomCatalogFixture(new PriceRuleCommand("PRICE_A","Giá A",RT,List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"))),
            e -> e.service.createPriceRule((PriceRuleCommand)e.cmd));
        define("TC-RPR-003", "Room Price Management", "RoomCatalogService.createPriceRule",
            "RoomCatalogService thật; JdbcTemplate/Connection/PreparedStatement/SQL Array mock. Command " +
            "code=PRICE_A,name=Giá " +
            "A,roomTypeId=RT,weekdays=[1],validFrom=2026-10-20,validTo=null,minuteFrom=420,minuteTo=1380,hourlyPrice=120000." +
            " RoomType có; activeCount=0,nextVersion=1; read-back F. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở" +
            " V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: code=\"A\"\n" +
            "V02: code=\"A\"×61\n" +
            "V03: code=\"!A\"\n" +
            "V04: code=\"AB\"\n" +
            "V05: code=\"A\"×60\n" +
            "V06: name=\"\"\n" +
            "V07: name=\"   \"\n" +
            "V08: name=\"N\"×201\n" +
            "V09: name=\"  \"+\"N\"×200+\"  \"",
            "V01,V02,V03,V06,V07,V08: RSE400; không execute INSERT callback.\n" +
            "V04,V05: Trả F; INSERT bind code đúng input.\n" +
            "V09: Trả F; bind name trim dài200.",
            9,
            () -> new RoomCatalogFixture(new PriceRuleCommand("PRICE_A","Giá A",RT,List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"))),
            e -> e.service.createPriceRule((PriceRuleCommand)e.cmd));
        define("TC-RPR-004", "Room Price Management", "RoomCatalogService.createPriceRule",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Ngày trong tuần không hợp lệ\n" +
            "weekdays []; [0]; [8]; [null]; [1,7]",
            "Từ chối rỗng/null/ngoài 1–7; nhận thứ Hai và Chủ nhật",
            5,
            () -> new RoomCatalogFixture(new PriceRuleCommand("PRICE_A","Giá A",RT,List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"))),
            e -> e.service.createPriceRule((PriceRuleCommand)e.cmd));
        define("TC-RPR-005", "Room Price Management", "RoomCatalogService.createPriceRule",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Biên ngày hiệu lực\n" +
            "validTo null; bằng validFrom; trước; sau 1 ngày",
            "Cho phép không hạn hoặc validTo > validFrom; còn lại 400",
            4,
            () -> new RoomCatalogFixture(new PriceRuleCommand("PRICE_A","Giá A",RT,List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"))),
            e -> e.service.createPriceRule((PriceRuleCommand)e.cmd));
        define("TC-RPR-006", "Room Price Management", "RoomCatalogService.createPriceRule",
            "RoomCatalogService thật; JdbcTemplate/Connection/PreparedStatement/SQL Array mock. Command " +
            "code=PRICE_A,name=Giá " +
            "A,roomTypeId=RT,weekdays=[1],validFrom=2026-10-20,validTo=null,minuteFrom=420,minuteTo=1380,hourlyPrice=120000." +
            " RoomType có; activeCount=0,nextVersion=1; read-back F. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở" +
            " V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: (minuteFrom,minuteTo)=(-1,1)\n" +
            "V02: (minuteFrom,minuteTo)=(0,1)\n" +
            "V03: (minuteFrom,minuteTo)=(1439,1440)\n" +
            "V04: (minuteFrom,minuteTo)=(1440,1)\n" +
            "V05: (minuteFrom,minuteTo)=(0,0)\n" +
            "V06: (minuteFrom,minuteTo)=(0,1440)\n" +
            "V07: (minuteFrom,minuteTo)=(0,1441)\n" +
            "V08: (minuteFrom,minuteTo)=(1,1)\n" +
            "V09: (minuteFrom,minuteTo)=(1380,420)\n" +
            "V10: hourlyPrice=-1\n" +
            "V11: hourlyPrice=0",
            "V01,V04,V05,V07,V08,V10: RSE400; không INSERT.\n" +
            "V02: Trả F; bind0/1.\n" +
            "V03: Trả F; bind1439/1440.\n" +
            "V06: Trả F; bind0/1440.\n" +
            "V09: Trả F; validator hiện nhận from>to; bind1380/420.\n" +
            "V11: Trả F; bind giá0.",
            11,
            () -> new RoomCatalogFixture(new PriceRuleCommand("PRICE_A","Giá A",RT,List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"))),
            e -> e.service.createPriceRule((PriceRuleCommand)e.cmd));
        define("TC-RPR-007", "Room Price Management", "RoomCatalogService.createPriceRule",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Mã active trùng và tạo version mới\n" +
            "Mock active count >0; biến thể count=0 và max(version)+1=3, insert callback trên Connection mock.",
            "Count>0: 409, không execute INSERT. Count=0: PreparedStatement nhận version=3, code chuẩn hóa, trả fixture " +
            "rule.",
            2,
            () -> new RoomCatalogFixture(new PriceRuleCommand("PRICE_A","Giá A",RT,List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"))),
            e -> e.service.createPriceRule((PriceRuleCommand)e.cmd));
        define("TC-RPR-008", "Room Price Management", "RoomCatalogService.createPriceRule",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Hạng phòng không tồn tại\n" +
            "roomTypeId chưa có",
            "404; không tạo price_rule",
            1,
            () -> new RoomCatalogFixture(new PriceRuleCommand("PRICE_A","Giá A",RT,List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"))),
            e -> e.service.createPriceRule((PriceRuleCommand)e.cmd));
        define("TC-RPR-012", "Room Price Management", "RoomCatalogService.priceRules",
            "RoomCatalogService thật, JdbcTemplate mock; query trả fixture F=[rule PR] hoặc [] như từng V. roomTypeId=RT " +
            "hoặc null; activeOnly boolean. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: roomTypeId=null,activeOnly=false; query trả F=[PR].\n" +
            "V02: roomTypeId=null,activeOnly=true; query trả F=[PR].\n" +
            "V03: roomTypeId=RT,activeOnly=false; query trả F=[PR].\n" +
            "V04: roomTypeId=RT,activeOnly=true; query trả F=[PR].",
            "V01: Trả F; args=[]; SQL dựng không thêm active=true.\n" +
            "V02: Trả F; args=[]; SQL dựng có active=true.\n" +
            "V03: Trả F; args=[RT]; SQL dựng không thêm active=true.\n" +
            "V04: Trả F; args=[RT]; SQL dựng có active=true.",
            4,
            () -> new RoomCatalogFixture(null),
            e -> e.service.priceRules((UUID)e.f.getOrDefault("filterRT",RT),(boolean)e.f.getOrDefault("activeOnly",false)));
        define("TC-RPR-013", "Room Price Management", "RoomCatalogService.updatePriceRule",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Sửa quy tắc thành version mới\n" +
            "Mock rule v1 và next version=2; callback ghi dùng Connection/PreparedStatement mock; query sau trả fixture v2.",
            "Trả fixture v2; quan sát deactivate ID cũ trước ghi ID/version mới. Không chứng minh snapshot hoặc transaction" +
            " thật.",
            1,
            () -> new RoomCatalogFixture(new PriceRuleCommand("PRICE_A","Giá A",RT,List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"))),
            e -> e.service.updatePriceRule(PR,(PriceRuleCommand)e.cmd));
        define("TC-RPR-014", "Room Price Management", "RoomCatalogService.updatePriceRule",
            "RoomCatalogService thật; JdbcTemplate/Connection/PreparedStatement/SQL Array mock. ID=PR; command PRICE_A/Giá " +
            "A/RT,[1],validFrom=2026-10-20,validTo=null,from=420,to=1380,price=120000; old.code=PRICE_A; nextVersion=2; " +
            "read-back F_v2. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: command.code=PRICE_B; old.code=PRICE_A.\n" +
            "V02: Command hợp lệ; lookup priceRule(PR)=[].\n" +
            "V03: command=null\n" +
            "V04: code=null\n" +
            "V05: code=\"\"\n" +
            "V06: code=\"!\"\n" +
            "V07: code=\"A\"\n" +
            "V08: code=\"A\"×61\n" +
            "V09: name=null\n" +
            "V10: name=\"\"\n" +
            "V11: name=\"   \"\n" +
            "V12: name=\"N\"×201\n" +
            "V13: roomTypeId=null\n" +
            "V14: weekdays=null\n" +
            "V15: weekdays=[]\n" +
            "V16: weekdays=[null]\n" +
            "V17: weekdays=[0]\n" +
            "V18: weekdays=[8]\n" +
            "V19: validFrom=null\n" +
            "V20: validTo=2026-10-19\n" +
            "V21: validTo=2026-10-20\n" +
            "V22: minuteFrom=null\n" +
            "V23: minuteFrom=-1\n" +
            "V24: minuteFrom=1440\n" +
            "V25: minuteTo=null\n" +
            "V26: minuteTo=0\n" +
            "V27: minuteTo=1441\n" +
            "V28: minuteTo=420\n" +
            "V29: hourlyPrice=null\n" +
            "V30: hourlyPrice=-1",
            "V01: RSE400; không deactivate/INSERT.\n" +
            "V02: RSE404; không deactivate/INSERT.\n" +
            "V03,V04,V05,V06,V07,V08,V09,V10,V11,V12,V13,V14,V15,V16,V17,V18,V19,V20,V21,V22,V23,V24,V25,V26,V27,V28,V29,V30:" +
            " RSE400; không ghi version.",
            30,
            () -> new RoomCatalogFixture(new PriceRuleCommand("PRICE_A","Giá A",RT,List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"))),
            e -> e.service.updatePriceRule(PR,(PriceRuleCommand)e.cmd));
        define("TC-RPR-015", "Room Price Management", "RoomCatalogService.createPriceRule",
            "RoomCatalogService thật; JdbcTemplate/Connection/PreparedStatement/SQL Array mock. Command " +
            "code=PRICE_A,name=Giá " +
            "A,roomTypeId=RT,weekdays=[1],validFrom=2026-10-20,validTo=null,minuteFrom=420,minuteTo=1380,hourlyPrice=120000." +
            " RoomType có; activeCount=0,nextVersion=1; read-back F. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là" +
            " BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Command base hợp lệ; activeCount0; nextVersion query=null.",
            "V01: Trả F; PreparedStatement bind version=1; codePRICE_A; không dùng null cho version.",
            1,
            () -> new RoomCatalogFixture(new PriceRuleCommand("PRICE_A","Giá A",RT,List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"))),
            e -> e.service.createPriceRule((PriceRuleCommand)e.cmd));
        define("TC-RPR-017", "Room Price Management", "RoomCatalogService.createPriceRule",
            "RoomCatalogService thật; JdbcTemplate/Connection/PreparedStatement/SQL Array mock. Command " +
            "code=PRICE_A,name=Giá " +
            "A,roomTypeId=RT,weekdays=[1],validFrom=2026-10-20,validTo=null,minuteFrom=420,minuteTo=1380,hourlyPrice=120000." +
            " RoomType có; activeCount=0,nextVersion=1; read-back F. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là" +
            " BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Mock db.execute chạy ConnectionCallback trên Connection mock; createArrayOf trả ARR; prepareStatement trả" +
            " STMT; executeUpdate=1.\n" +
            "V02: Cùng callback fixture; STMT.executeUpdate ném DataIntegrityViolationException E; close/free không ném.",
            "V01: STMT.close rồi ARR.free đều gọi đúng một lần; bind weekdays [1],version1; trả F.\n" +
            "V02: STMT.close và ARR.free vẫn được gọi một lần; rethrow cùng E; không read-back thành công.",
            2,
            () -> new RoomCatalogFixture(new PriceRuleCommand("PRICE_A","Giá A",RT,List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"))),
            e -> e.service.createPriceRule((PriceRuleCommand)e.cmd));
        define("TC-RPR-016", "Room Price Management", "RoomCatalogService.updatePriceRule",
            "RoomCatalogService thật; JdbcTemplate/Connection/PreparedStatement/SQL Array mock. ID=PR; command PRICE_A/Giá " +
            "A/RT,[1],validFrom=2026-10-20,validTo=null,from=420,to=1380,price=120000; old.code=PRICE_A; nextVersion=2; " +
            "read-back F_v2. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là " +
            "Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Lookup old PR/codePRICE_A có; nextVersion query=null; read-back F_next version1.",
            "V01: UPDATE inactive PR trước INSERT; bind next version1 theo fallback; trả F_next; không xóa lịch sử.",
            1,
            () -> new RoomCatalogFixture(new PriceRuleCommand("PRICE_A","Giá A",RT,List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"))),
            e -> e.service.updatePriceRule(PR,(PriceRuleCommand)e.cmd));
        define("TC-CMB-001", "Combo Package Management", "RoomCatalogService.createPrice",
            "RoomCatalogService thật, JdbcTemplate mock. kind=COMBO,roomTypeId=RT. Command code=COMBO_A,name=Gói " +
            "A,description=Mô tả,durationMinutes=120,minuteFrom=420,minuteTo=1380,price=200000. RoomType CINEMA có; " +
            "activeCount=0,nextVersion=1; INSERT=1; read-back F. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Base COMBO_A,Gói A,description=Mô tả,120 phút,(420,1380),200000; count0,version1.",
            "V01: Trả F; INSERT code COMBO_A,version1,RT,CINEMA,name Gói " +
            "A,duration120,price200000,from420,to1380,description Mô tả.",
            1,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.createPrice((String)e.f.getOrDefault("kind","COMBO"),(UUID)e.f.getOrDefault("createRT",RT),(PriceCommand)e.cmd));
        define("TC-CMB-002", "Combo Package Management", "RoomCatalogService.createPrice",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "kind=COMBO; Validation combo code\n" +
            "code: null, rỗng, chỉ khoảng trắng, ký tự !. Mỗi biến thể chạy riêng; tất cả trường còn lại hợp lệ.",
            "400; không INSERT combo",
            4,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.createPrice((String)e.f.getOrDefault("kind","COMBO"),(UUID)e.f.getOrDefault("createRT",RT),(PriceCommand)e.cmd));
        define("TC-CMB-003", "Combo Package Management", "RoomCatalogService.createPrice",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "kind=COMBO; Validation combo name\n" +
            "name: null, rỗng hoặc chỉ khoảng trắng. Mỗi biến thể chạy riêng; tất cả trường còn lại hợp lệ.",
            "400; không INSERT combo",
            3,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.createPrice((String)e.f.getOrDefault("kind","COMBO"),(UUID)e.f.getOrDefault("createRT",RT),(PriceCommand)e.cmd));
        define("TC-CMB-004", "Combo Package Management", "RoomCatalogService.createPrice",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "kind=COMBO; Validation combo durationMinutes\n" +
            "durationMinutes: null, 0 hoặc -1. Mỗi biến thể chạy riêng; tất cả trường còn lại hợp lệ.",
            "400; không INSERT combo",
            3,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.createPrice((String)e.f.getOrDefault("kind","COMBO"),(UUID)e.f.getOrDefault("createRT",RT),(PriceCommand)e.cmd));
        define("TC-CMB-005", "Combo Package Management", "RoomCatalogService.createPrice",
            "RoomCatalogService thật, JdbcTemplate mock. kind=COMBO,roomTypeId=RT. Command code=COMBO_A,name=Gói " +
            "A,description=Mô tả,durationMinutes=120,minuteFrom=420,minuteTo=1380,price=200000. RoomType CINEMA có; " +
            "activeCount=0,nextVersion=1; INSERT=1; read-back F. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: minuteFrom=null; minuteTo=1.\n" +
            "V02: minuteFrom=-1; minuteTo=1.\n" +
            "V03: minuteFrom=0; minuteTo=1.\n" +
            "V04: minuteFrom=1439; minuteTo=1.\n" +
            "V05: minuteFrom=1440; minuteTo=1.\n" +
            "V06: minuteFrom=1; minuteTo=1.",
            "V01,V02,V05: RSE400; không INSERT.\n" +
            "V03: Trả F; INSERT from0/to1.\n" +
            "V04: Trả F; validator nhận1439/1.\n" +
            "V06: RSE400 do hai đầu bằng nhau; không INSERT.",
            6,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.createPrice((String)e.f.getOrDefault("kind","COMBO"),(UUID)e.f.getOrDefault("createRT",RT),(PriceCommand)e.cmd));
        define("TC-CMB-006", "Combo Package Management", "RoomCatalogService.createPrice",
            "RoomCatalogService thật, JdbcTemplate mock. kind=COMBO,roomTypeId=RT. Command code=COMBO_A,name=Gói " +
            "A,description=Mô tả,durationMinutes=120,minuteFrom=420,minuteTo=1380,price=200000. RoomType CINEMA có; " +
            "activeCount=0,nextVersion=1; INSERT=1; read-back F. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: minuteFrom=0; minuteTo=null.\n" +
            "V02: minuteFrom=0; minuteTo=0.\n" +
            "V03: minuteFrom=0; minuteTo=1.\n" +
            "V04: minuteFrom=0; minuteTo=1440.\n" +
            "V05: minuteFrom=0; minuteTo=1441.\n" +
            "V06: minuteFrom=1; minuteTo=1.",
            "V01,V02,V05: RSE400; không INSERT.\n" +
            "V03: Trả F; INSERT0/1.\n" +
            "V04: Trả F; INSERT0/1440.\n" +
            "V06: RSE400 do hai đầu bằng nhau; không INSERT.",
            6,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.createPrice((String)e.f.getOrDefault("kind","COMBO"),(UUID)e.f.getOrDefault("createRT",RT),(PriceCommand)e.cmd));
        define("TC-CMB-007", "Combo Package Management", "RoomCatalogService.createPrice",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "kind=COMBO; Validation combo price\n" +
            "price: null hoặc -1. Mỗi biến thể chạy riêng; tất cả trường còn lại hợp lệ.",
            "400; không INSERT combo",
            2,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.createPrice((String)e.f.getOrDefault("kind","COMBO"),(UUID)e.f.getOrDefault("createRT",RT),(PriceCommand)e.cmd));
        define("TC-CMB-008", "Combo Package Management", "RoomCatalogService.createPrice",
            "RoomCatalogService thật, JdbcTemplate mock. kind=COMBO,roomTypeId=RT. Command code=COMBO_A,name=Gói " +
            "A,description=Mô tả,durationMinutes=120,minuteFrom=420,minuteTo=1380,price=200000. RoomType CINEMA có; " +
            "activeCount=0,nextVersion=1; INSERT=1; read-back F. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: code=\"A\"\n" +
            "V02: code=\"A\"×61\n" +
            "V03: code=\"AB\"\n" +
            "V04: code=\"A\"×60\n" +
            "V05: durationMinutes=1\n" +
            "V06: price=0\n" +
            "V07: (minuteFrom,minuteTo)=(1439,1440)\n" +
            "V08: (minuteFrom,minuteTo)=(0,1)\n" +
            "V09: (minuteFrom,minuteTo)=(1,1)\n" +
            "V10: (minuteFrom,minuteTo)=(1380,420)",
            "V01,V02,V09: RSE400; không INSERT.\n" +
            "V03,V04: Trả F; INSERT code đúng input.\n" +
            "V05: Trả F; INSERT duration1.\n" +
            "V06: Trả F; INSERT price0.\n" +
            "V07: Trả F; INSERT1439/1440.\n" +
            "V08: Trả F; INSERT0/1.\n" +
            "V10: Trả F; validator hiện nhận1380/420.",
            10,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.createPrice((String)e.f.getOrDefault("kind","COMBO"),(UUID)e.f.getOrDefault("createRT",RT),(PriceCommand)e.cmd));
        define("TC-CMB-010", "Combo Package Management", "RoomCatalogService.createPrice",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "kind=COMBO; Hạng phòng không tồn tại\n" +
            "roomTypeId không có",
            "404; không tạo combo",
            1,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.createPrice((String)e.f.getOrDefault("kind","COMBO"),(UUID)e.f.getOrDefault("createRT",RT),(PriceCommand)e.cmd));
        define("TC-CMB-011", "Combo Package Management", "RoomCatalogService.prices",
            "RoomCatalogService thật, JdbcTemplate mock. kind=COMBO; query trả F=[combo C] hoặc [] như từng V; " +
            "roomTypeId=RT/null,activeOnly boolean. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: roomTypeId=null,activeOnly=false; query trả F=[C].\n" +
            "V02: roomTypeId=null,activeOnly=true; query trả F=[C].\n" +
            "V03: roomTypeId=RT,activeOnly=false; query trả F=[C].\n" +
            "V04: roomTypeId=RT,activeOnly=true; query trả F=[C].",
            "V01: Trả F; args=[]; SQL dựng không thêm active=true.\n" +
            "V02: Trả F; args=[]; SQL dựng có active=true.\n" +
            "V03: Trả F; args=[RT]; SQL dựng không thêm active=true.\n" +
            "V04: Trả F; args=[RT]; SQL dựng có active=true.",
            4,
            () -> new RoomCatalogFixture(null),
            e -> e.service.prices("COMBO",(UUID)e.f.getOrDefault("filterRT",RT),(boolean)e.f.getOrDefault("activeOnly",false)));
        define("TC-CMB-012", "Combo Package Management", "RoomCatalogService.updatePrice",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Sửa combo tạo version mới\n" +
            "Mock combo v1; các query count/version và read-back hợp lệ; giá/duration mới khác cũ.",
            "Quan sát lệnh inactive bản cũ, INSERT version mới giữ mã và dùng giá/duration mới; trả fixture v2; không sửa " +
            "booking_charge.",
            1,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.updatePrice((String)e.f.getOrDefault("kind","COMBO"),C,(PriceCommand)e.cmd));
        define("TC-CMB-013", "Combo Package Management", "RoomCatalogService.updatePrice",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Chuyển hạng phòng của version combo mới\n" +
            "Mock combo cũ ở hạng A; request roomTypeId B hoặc null; các lookup trả fixture hợp lệ.",
            "Quan sát INSERT version mới nhận B khi được chọn, hoặc giữ A khi null; không ghi lại charge đã chốt.",
            1,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.updatePrice((String)e.f.getOrDefault("kind","COMBO"),C,(PriceCommand)e.cmd));
        define("TC-CMB-017", "Combo Package Management", "RoomCatalogService.deletePrice",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Xóa combo chưa được dùng\n" +
            "Mock lookup combo có dữ liệu; DELETE trả 1.",
            "Hoàn tất không exception; đúng ID được truyền vào DELETE. Không chứng minh combo chưa dùng ở DB.",
            1,
            () -> new RoomCatalogFixture(null),
            e -> {
            e.service.deletePrice((String)e.f.getOrDefault("kind","COMBO"),C);
            return null;
        });
        define("TC-CMB-018", "Combo Package Management", "RoomCatalogService.deletePrice",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Chặn xóa combo có phụ thuộc\n" +
            "Mock lookup combo hợp lệ; DELETE ném DataIntegrityViolationException.",
            "409 có thông báo nghiệp vụ; không trả thành công. Không chạy FK/trigger.",
            1,
            () -> new RoomCatalogFixture(null),
            e -> {
            e.service.deletePrice((String)e.f.getOrDefault("kind","COMBO"),C);
            return null;
        });
        define("TC-CMB-019", "Combo Package Management", "RoomCatalogService.deletePrice",
            "RoomCatalogService thật; JdbcTemplate mock. Với callback ghi price rule, dùng Connection/PreparedStatement/SQL" +
            " Array mock; không kết nối DB.",
            "Xóa combo thiếu hoặc sai loại\n" +
            "ID không có; gọi deletePrice kind HOURLY",
            "Lookup combo không có: 404. Gọi service.deletePrice với kind HOURLY: 400 trước DELETE; không kiểm route HTTP.",
            2,
            () -> new RoomCatalogFixture(null),
            e -> {
            e.service.deletePrice((String)e.f.getOrDefault("kind","COMBO"),C);
            return null;
        });
        define("TC-CMB-020", "Combo Package Management", "RoomCatalogService.createPrice",
            "RoomCatalogService thật, JdbcTemplate mock. kind=COMBO,roomTypeId=RT. Command code=COMBO_A,name=Gói " +
            "A,description=Mô tả,durationMinutes=120,minuteFrom=420,minuteTo=1380,price=200000. RoomType CINEMA có; " +
            "activeCount=0,nextVersion=1; INSERT=1; read-back F. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là " +
            "BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Command COMBO_A; activeCount1.\n" +
            "V02: Command COMBO_A; activeCount0,nextVersion3.",
            "V01: RSE409; không query nextVersion/INSERT.\n" +
            "V02: Trả F; INSERT codeCOMBO_A,version3; các trường khác theo base.",
            2,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.createPrice((String)e.f.getOrDefault("kind","COMBO"),(UUID)e.f.getOrDefault("createRT",RT),(PriceCommand)e.cmd));
        define("TC-CMB-021", "Combo Package Management", "RoomCatalogService.createPrice",
            "RoomCatalogService thật, JdbcTemplate mock. kind=COMBO,roomTypeId=RT. Command code=COMBO_A,name=Gói " +
            "A,description=Mô tả,durationMinutes=120,minuteFrom=420,minuteTo=1380,price=200000. RoomType CINEMA có; " +
            "activeCount=0,nextVersion=1; INSERT=1; read-back F. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là " +
            "BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Command code=\" combo_a \"; Answer count trả1 nếu tham số codeCOMBO_A,ngược lại0; room RT có.",
            "V01: Expected kiểm soát mã chuẩn: query duplicate nhận COMBO_A; RSE409 trước INSERT.",
            1,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.createPrice((String)e.f.getOrDefault("kind","COMBO"),(UUID)e.f.getOrDefault("createRT",RT),(PriceCommand)e.cmd));
        define("TC-CMB-022", "Combo Package Management", "RoomCatalogService.createPrice",
            "RoomCatalogService thật, JdbcTemplate mock. kind=COMBO,roomTypeId=RT. Command code=COMBO_A,name=Gói " +
            "A,description=Mô tả,durationMinutes=120,minuteFrom=420,minuteTo=1380,price=200000. RoomType CINEMA có; " +
            "activeCount=0,nextVersion=1; INSERT=1; read-back F. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là " +
            "BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: kindCOMBO,command=null.",
            "V01: RSE400; không roomType lookup/query duplicate/INSERT.",
            1,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.createPrice((String)e.f.getOrDefault("kind","COMBO"),(UUID)e.f.getOrDefault("createRT",RT),(PriceCommand)e.cmd));
        define("TC-CMB-023", "Combo Package Management", "RoomCatalogService.updatePrice",
            "RoomCatalogService thật, JdbcTemplate mock. kindCOMBO,idC; command codeCOMBO_A,nameGói mới,descriptionMô " +
            "tả,duration120,from420,to1380,price200000,roomTypeIdRT. Lookup combo cũ C/codeCOMBO_A/RT có; lookup RT có; " +
            "activeCount0,nextVersion2; read-back F_combo_v2. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là " +
            "BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: command=null.\n" +
            "V02: Chỉ command.name=\"   \".\n" +
            "V03: Chỉ durationMinutes=0.\n" +
            "V04: Chỉ price=-1.",
            "V01,V02,V03,V04: RSE400; không deactivate/INSERT version.",
            4,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.updatePrice((String)e.f.getOrDefault("kind","COMBO"),C,(PriceCommand)e.cmd));
        define("TC-CMB-024", "Combo Package Management", "RoomCatalogService.updatePrice",
            "RoomCatalogService thật, JdbcTemplate mock. kindCOMBO,idC; command codeCOMBO_A,nameGói mới,descriptionMô " +
            "tả,duration120,from420,to1380,price200000,roomTypeIdRT. Lookup combo cũ C/codeCOMBO_A/RT có; lookup RT có; " +
            "activeCount0,nextVersion2; read-back F_combo_v2. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là " +
            "BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Old codeCOMBO_A; command.code=NEW_CODE hợp lệ.",
            "V01: Deactivate C trước create version; duplicate/version query và INSERT dùng COMBO_A,version2,khôngNEW_CODE;" +
            " trả F_combo_v2; không ghi booking_charge.",
            1,
            () -> new RoomCatalogFixture(new PriceCommand(RT,"COMBO_A","Gói A","Mô tả",120,420,1380,money("200000"))),
            e -> e.service.updatePrice((String)e.f.getOrDefault("kind","COMBO"),C,(PriceCommand)e.cmd));
        define("TC-CMB-025", "Combo Package Management", "RoomCatalogService.deletePrice",
            "RoomCatalogService thật, JdbcTemplate mock. kindCOMBO,idC; lookup combo C có; DELETE trả1 trừ V nêu khác. " +
            "Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; " +
            "snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Lookup combo C có; DELETE=0.",
            "V01: RSE404; không trả thành công; không khẳng định concurrent delete thật.",
            1,
            () -> new RoomCatalogFixture(null),
            e -> {
            e.service.deletePrice((String)e.f.getOrDefault("kind","COMBO"),C);
            return null;
        });
    }

    private void register() {
        catalog();
    }

    private void catalog() {
        Consumer<RoomCatalogFixture> rt=e-> {
            equal(e.actual,e.f.get("roomTypeView"),"Room type fixture");
            check(!e.sql("INSERT INTO gcmb.room_type").isEmpty()||!e.sql("UPDATE gcmb.room_type").isEmpty(),"Expected write");
        };
        for(String mode:List.of("CINEMA","MUSIC_BOX"))ok("RTY",1,e-> {
            e.command("code"," queen_01 ");
            e.command("serviceType",mode);
        },e-> {
            rt.accept(e);
            sqlHas(e,"INSERT INTO gcmb.room_type","QUEEN_01",mode);
        });
        bad("RTY",2,"code",null,"","   ","!");
        bad("RTY",3,"name",missing());
        bad("RTY",4,"capacity",null,0,-1);
        bad("RTY",5,"serviceType",null,"UNKNOWN");
        for(int i=0;i<6;i++) {
            int n=i;
            add(key("RTY",6),e->e.command(n<4?"code":"name",n<4?new String[] {
                "A","A".repeat(61),"AB","A".repeat(60)
            }
                [n]:"N".repeat(n==4?200:201)),i<2||i==5?400:0,null,e-> {
                if(e.failure==null)rt.accept(e);
                else e.noWrites();
            });
        }
        error("RTY",7,409,e->e.answers.put("sql:SELECT COUNT(*) FROM gcmb.room_type",1L));
        error("RTY",7,409,e->e.answers.put("sql:INSERT INTO gcmb.room_type",new DataIntegrityViolationException("duplicate")));
        for(boolean empty:new boolean[] {
            false,true
        })ok("RTY",8,e-> {
            if(empty)e.answers.put("sql:FROM gcmb.room_type ORDER BY code",List.of());
        },e-> {
            equal(((List<?>)e.actual).size(),empty?0:1,"Room types list");
            e.noWrites();
        });
        ok("RTY",10,e->e.command("code","queen_01"),rt);
        error("RTY",11,400,e->e.command("code","OTHER"));
        error("RTY",11,404,e->e.answers.put("sql:WHERE id=?",List.of()));
        bad("RTY",12,"$command",(Object)null);
        bad("RTY",12,"code",null,"","   ","!","A","A".repeat(61));
        bad("RTY",12,"name",null,"","   ","N".repeat(201));
        bad("RTY",12,"capacity",null,0,-1);
        bad("RTY",12,"serviceType",null,"UNKNOWN");
        ok("RTY",13,e->e.f.put("roomTypeView",change(e.f.get("roomTypeView"),"active",false)),rt);
        error("RTY",14,404,e->e.answers.put("sql:UPDATE gcmb.room_type SET active",0));
        ok("RTY",14,e->e.f.put("roomTypeView",change(e.f.get("roomTypeView"),"active",false)),rt);
        ok("RTY",15,e-> {
        },e->e.sqlCount("DELETE FROM gcmb.room_type",1));
        error("RTY",16,409,e->e.answers.put("sql:SELECT EXISTS(SELECT 1 FROM gcmb.room WHERE",true));
        add(key("RTY",16),e->e.answers.put("sql:DELETE FROM gcmb.room_type",new DataIntegrityViolationException("used")),409,null,e->e.sqlCount("DELETE FROM gcmb.room_type",1));
        error("RTY",17,404,e->e.answers.put("sql:SELECT id FROM gcmb.room_type WHERE id=? FOR UPDATE",List.of()));
        bad("RTY",18,"$command",(Object)null);
        error("RTY",19,404,e->e.answers.put("sql:UPDATE gcmb.room_type SET name",0));
        priceRules();
        combos();
    }

    private void priceRules() {
        Consumer<RoomCatalogFixture> wrote=e-> {
            equal(e.actual,e.f.get("priceRuleView"),"Rule readback");
            check(e.f.containsKey("statement"),"INSERT callback not executed");
            var command=(PriceRuleCommand)e.cmd;
            var ps=(PreparedStatement)e.f.get("statement");
            try {
                verify(ps).setString(2,command.code().trim().toUpperCase(Locale.ROOT));
                verify(ps).setObject(4,command.roomTypeId());
                verify(ps).setString(5,command.name().trim());
                verify(ps).setObject(6,command.validFrom());
                verify(ps).setObject(7,command.validTo());
                verify(ps).setInt(8,command.minuteFrom());
                verify(ps).setInt(9,command.minuteTo());
                verify(ps).setArray(10,(java.sql.Array)e.f.get("sqlArray"));
                verify(ps).setBigDecimal(11,command.hourlyPrice());
                verify(ps).setBigDecimal(12,command.hourlyPrice());
                verify((Connection)e.f.get("connection")).createArrayOf(eq("smallint"),org.mockito.AdditionalMatchers.aryEq(command.weekdays().stream().map(Integer::shortValue).toArray(Short[]::new)));
            }
            catch(Exception ex) {
                throw new AssertionError(ex);
            }
        };
        ok("RPR",1,e->e.command("weekdays",List.of(1,2,3,4,5)),e-> {
            wrote.accept(e);
            var ps=(PreparedStatement)e.f.get("statement");
            try {
                verify(ps).setString(2,"PRICE_A");
                verify(ps).setInt(3,1);
            }
            catch(Exception ex) {
                throw new AssertionError(ex);
            }
        });
        bad("RPR",2,"$command",(Object)null);
        for(String f:List.of("code","name","roomTypeId","weekdays","validFrom","minuteFrom","minuteTo","hourlyPrice"))bad("RPR",2,f,(Object)null);
        Object[][] changes= {
            {
                "code","A"
            }, {
                "code","A".repeat(61)
            }, {
                "code","!A"
            }, {
                "code","AB"
            }, {
                "code","A".repeat(60)
            }, {
                "name",""
            }, {
                "name","   "
            }, {
                "name","N".repeat(201)
            }, {
                "name","  "+"N".repeat(200)+"  "
            }
        };
        for(int i=0;i<changes.length;i++) {
            var v=changes[i];
            boolean pass=i==3||i==4||i==8;
            add(key("RPR",3),e->e.command((String)v[0],v[1]),pass?0:400,null,e-> {
                if(pass)wrote.accept(e);
                else e.noWrites();
            });
        }
        List<List<Integer>> days=Arrays.asList(List.of(),List.of(0),List.of(8),Arrays.asList((Integer)null),List.of(1,7));
        for(int i=0;i<days.size();i++) {
            var v=days.get(i);
            boolean good=i==4;
            add(key("RPR",4),e->e.command("weekdays",v),good?0:400,null,e-> {
                if(good)wrote.accept(e);
                else e.noWrites();
            });
        }
        for(LocalDate date:Arrays.asList(null,LocalDate.of(2026,10,20),LocalDate.of(2026,10,19),LocalDate.of(2026,10,21)))add(key("RPR",5),e->e.command("validTo",date),date==null||date.getDayOfMonth()==21?0:400,null,e-> {
            if(e.failure==null)wrote.accept(e);
            else e.noWrites();
        });
        int[][] bounds= {
            {
                -1,1
            }, {
                0,1
            }, {
                1439,1440
            }, {
                1440,1
            }, {
                0,0
            }, {
                0,1440
            }, {
                0,1441
            }, {
                1,1
            }, {
                1380,420
            }
        };
        for(int i=0;i<bounds.length;i++) {
            var v=bounds[i];
            boolean good=Set.of(1,2,5,8).contains(i);
            add(key("RPR",6),e-> {
                e.command("minuteFrom",v[0]);
                e.command("minuteTo",v[1]);
            },good?0:400,null,e-> {
                if(good)wrote.accept(e);
                else e.noWrites();
            });
        }
        bad("RPR",6,"hourlyPrice",money("-1"));
        ok("RPR",6,e->e.command("hourlyPrice",BigDecimal.ZERO),wrote);
        error("RPR",7,409,e->e.answers.put("sql:SELECT COUNT(*) FROM gcmb.price_rule",1L));
        ok("RPR",7,e->e.answers.put("sql:SELECT COALESCE(MAX(version),0)+1 FROM gcmb.price_rule",3L),e-> {
            wrote.accept(e);
            try {
                verify((PreparedStatement)e.f.get("statement")).setInt(3,3);
            }
            catch(Exception ex) {
                throw new AssertionError(ex);
            }
        });
        error("RPR",8,404,e->e.answers.put("sql:SELECT id,code,name,mode,capacity,description,active FROM gcmb.room_type WHERE id=?",List.of()));
        for(boolean hasRT:new boolean[] {
            false,true
        })for(boolean active:new boolean[] {
            false,true
        })ok("RPR",12,e-> {
            e.f.put("filterRT",hasRT?RT:null);
            e.f.put("activeOnly",active);
        },e-> {
            equal(e.actual,List.of(e.f.get("priceRuleView")),"Rules list");
            e.noWrites();
            var q=e.sql("FROM gcmb.price_rule p").get(0);
            String s=q.getArgument(0);
            equal(s.contains("p.active=true"),active,"Active SQL filter");
            if(hasRT)check(Arrays.asList(q.getArguments()).contains(RT),"RT query arg");
        });
        ok("RPR",13,e->e.answers.put("sql:SELECT COALESCE(MAX(version),0)+1 FROM gcmb.price_rule",2L),e-> {
            wrote.accept(e);
            e.sqlCount("UPDATE gcmb.price_rule SET active=false",1);
        });
        error("RPR",14,400,e->e.command("code","PRICE_B"));
        error("RPR",14,404,e->e.answers.put("sql:WHERE p.id=?",List.of()));
        bad("RPR",14,"$command",(Object)null);
        bad("RPR",14,"code",null,"","!","A","A".repeat(61));
        bad("RPR",14,"name",null,"","   ","N".repeat(201));
        bad("RPR",14,"roomTypeId",(Object)null);
        bad("RPR",14,"weekdays",null,List.of(),Arrays.asList((Integer)null),List.of(0),List.of(8));
        bad("RPR",14,"validFrom",(Object)null);
        bad("RPR",14,"validTo",LocalDate.of(2026,10,19),LocalDate.of(2026,10,20));
        bad("RPR",14,"minuteFrom",null,-1,1440);
        bad("RPR",14,"minuteTo",null,0,1441,420);
        bad("RPR",14,"hourlyPrice",null,money("-1"));
        for(int id:new int[] {
            15,16
        })ok("RPR",id,e->e.answers.put("sql:SELECT COALESCE(MAX(version),0)+1 FROM gcmb.price_rule",null),e-> {
            wrote.accept(e);
            try {
                verify((PreparedStatement)e.f.get("statement")).setInt(3,1);
            }
            catch(Exception ex) {
                throw new AssertionError(ex);
            }
        });
        ok("RPR",17,e-> {
        },e-> {
            wrote.accept(e);
            try {
                verify((PreparedStatement)e.f.get("statement")).close();
                verify((java.sql.Array)e.f.get("sqlArray")).free();
            }
            catch(Exception ex) {
                throw new AssertionError(ex);
            }
        });
        add(key("RPR",17),e->e.f.put("callbackFailure",new DataIntegrityViolationException("write failure")),-3,null,e-> {
            equal(e.failure,e.f.get("callbackFailure"),"Same callback failure");
            try {
                verify((PreparedStatement)e.f.get("statement")).close();
                verify((java.sql.Array)e.f.get("sqlArray")).free();
            }
            catch(Exception ex) {
                throw new AssertionError(ex);
            }
        });
    }

    private void combos() {
        Consumer<RoomCatalogFixture> wrote=e-> {
            equal(e.actual,e.f.get("priceView"),"Combo readback");
            e.sqlCount("INSERT INTO gcmb.combo(",1);
        };
        ok("CMB",1,e-> {
        },e-> {
            wrote.accept(e);
            sqlHas(e,"INSERT INTO gcmb.combo(","COMBO_A",1L,RT,"CINEMA","Gói A",120,money("200000"),420,1380,"Mô tả");
        });
        bad("CMB",2,"code",null,"","   ","!");
        bad("CMB",3,"name",missing());
        bad("CMB",4,"durationMinutes",null,0,-1);
        Integer[] froms= {
            null,-1,0,1439,1440,1
        };
        for(int i=0;i<froms.length;i++) {
            var v=froms[i];
            boolean pass=i==2||i==3;
            add(key("CMB",5),e-> {
                e.command("minuteFrom",v);
                e.command("minuteTo",1);
            },pass?0:400,null,e-> {
                if(pass)wrote.accept(e);
                else e.noWrites();
            });
        }
        Integer[] tos= {
            null,0,1,1440,1441,1
        };
        for(int i=0;i<tos.length;i++) {
            var v=tos[i];
            int j=i;
            boolean pass=i==2||i==3;
            add(key("CMB",6),e-> {
                e.command("minuteFrom",j==5?1:0);
                e.command("minuteTo",v);
            },pass?0:400,null,e-> {
                if(pass)wrote.accept(e);
                else e.noWrites();
            });
        }
        bad("CMB",7,"price",null,money("-1"));
        for(int i=0;i<10;i++) {
            int n=i;
            boolean pass=i!=0&&i!=1&&i!=8;
            add(key("CMB",8),e-> {
                switch(n) {
                    case 0->e.command("code","A");
                    case 1->e.command("code","A".repeat(61));
                    case 2->e.command("code","AB");
                    case 3->e.command("code","A".repeat(60));
                    case 4->e.command("durationMinutes",1);
                    case 5->e.command("price",BigDecimal.ZERO);
                    case 6-> {
                        e.command("minuteFrom",1439);
                        e.command("minuteTo",1440);
                    }
                    case 7-> {
                        e.command("minuteFrom",0);
                        e.command("minuteTo",1);
                    }
                    case 8-> {
                        e.command("minuteFrom",1);
                        e.command("minuteTo",1);
                    }
                    case 9-> {
                        e.command("minuteFrom",1380);
                        e.command("minuteTo",420);
                    }
                }
            },pass?0:400,null,e-> {
                if(pass)wrote.accept(e);
                else e.noWrites();
            });
        }
        error("CMB",10,404,e->e.answers.put("sql:SELECT id,code,name,mode,capacity,description,active FROM gcmb.room_type WHERE id=?",List.of()));
        for(boolean hasRT:new boolean[] {
            false,true
        })for(boolean active:new boolean[] {
            false,true
        })ok("CMB",11,e-> {
            e.f.put("filterRT",hasRT?RT:null);
            e.f.put("activeOnly",active);
        },e-> {
            equal(e.actual,List.of(e.f.get("priceView")),"Combo list");
            e.noWrites();
            String s=e.sql("catalogue WHERE").get(0).getArgument(0);
            equal(s.contains("AND active=true"),active,"Active filter");
        });
        ok("CMB",12,e->e.answers.put("sql:SELECT COALESCE(MAX(version),0)+1 FROM gcmb.combo",2L),e-> {
            wrote.accept(e);
            e.sqlCount("UPDATE gcmb.combo SET active=false",1);
            sqlHas(e,"INSERT INTO gcmb.combo(",2L);
            check(e.sql("UPDATE gcmb.combo SET active=false").get(0).getSequenceNumber()<e.sql("INSERT INTO gcmb.combo(").get(0).getSequenceNumber(),"Deactivate before new version");
        });
        ok("CMB",13,e-> {
            e.command("roomTypeId",RT2);
            e.answers.put("sql:SELECT COALESCE(MAX(version),0)+1 FROM gcmb.combo",2L);
        },e-> {
            wrote.accept(e);
            sqlHas(e,"INSERT INTO gcmb.combo(",RT2,2L);
        });
        ok("CMB",17,e-> {
        },e->e.sqlCount("DELETE FROM gcmb.combo",1));
        add(key("CMB",18),e->e.answers.put("sql:DELETE FROM gcmb.combo",new DataIntegrityViolationException("FK")),409,null,e->e.sqlCount("DELETE FROM gcmb.combo",1));
        error("CMB",19,404,e->e.answers.put("sql:entries WHERE kind",List.of()));
        error("CMB",19,400,e->e.f.put("kind","HOURLY"));
        error("CMB",20,409,e->e.answers.put("sql:SELECT COUNT(*) FROM gcmb.combo",1));
        ok("CMB",20,e->e.answers.put("sql:SELECT COALESCE(MAX(version),0)+1 FROM gcmb.combo",3L),e-> {
            wrote.accept(e);
            sqlHas(e,"INSERT INTO gcmb.combo(",3L);
        });
        ok("CMB",21,e->e.command("code"," combo_a "),e-> {
            wrote.accept(e);
            sqlHas(e,"SELECT COUNT(*) FROM gcmb.combo","COMBO_A");
            sqlHas(e,"SELECT COALESCE(MAX(version),0)+1 FROM gcmb.combo","COMBO_A");
        });
        bad("CMB",22,"$command",(Object)null);
        bad("CMB",23,"$command",(Object)null);
        bad("CMB",23,"name","   ");
        bad("CMB",23,"durationMinutes",0);
        bad("CMB",23,"price",money("-1"));
        ok("CMB",24,e->e.command("code","OTHER"),e-> {
            wrote.accept(e);
            sqlHas(e,"INSERT INTO gcmb.combo(","COMBO_A");
        });
        add(key("CMB",25),e->e.answers.put("sql:DELETE FROM gcmb.combo",0),404,null,e->e.sqlCount("DELETE FROM gcmb.combo",1));
    }
}
