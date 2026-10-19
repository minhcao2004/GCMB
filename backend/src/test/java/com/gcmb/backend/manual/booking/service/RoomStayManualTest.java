/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Covers RoomStay service scenarios using isolated dependencies.
 */

package com.gcmb.backend.manual.booking.service;

import com.gcmb.backend.booking.dto.BookingDtos.AvailableRoom;
import com.gcmb.backend.booking.dto.BookingDtos.BookingDetail;
import com.gcmb.backend.booking.dto.BookingDtos.ChargeLine;
import com.gcmb.backend.booking.dto.BookingDtos.ExtendStayRequest;
import com.gcmb.backend.booking.dto.BookingDtos.ExtensionQuote;
import com.gcmb.backend.booking.dto.BookingDtos.ExtensionResult;
import com.gcmb.backend.booking.dto.BookingDtos.PriceQuote;
import com.gcmb.backend.booking.dto.BookingDtos.RoomChangeQuote;
import com.gcmb.backend.booking.dto.BookingDtos.RoomChangeRequest;
import com.gcmb.backend.booking.dto.BookingDtos.RoomChangeResult;
import com.gcmb.backend.booking.dto.BookingDtos.StayOptions;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository.ChargeRow;
import com.gcmb.backend.booking.repository.BookingRepository.ComboRow;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.support.ManualScenarios;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import static com.gcmb.backend.manual.booking.service.BookingManualTest.*;
import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class RoomStayManualTest extends ManualScenarios<BookingFixture> {
    private RoomStayManualTest() {
        super("Booking","RoomStayManualTest");
    }

    public static List<ManualTestSuite> suites() {
        var tests=new RoomStayManualTest();
        tests.describeCases();
        tests.register();
        return tests.buildSuites();
    }

    private void describeCases() {
        define("TC-ROM-027", "Room Management", "BookingService.finishCleaning",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " branchScope=B; roomId=R; roomHasOccupant=false. Cố định thời gian T. Reset mock/fixture trước mỗi V; chỉ thay" +
            " dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: lockRoomStatus=UNDER_MAINTENANCE; occupant=false.\n" +
            "V02: lockRoomStatus=INACTIVE; occupant=false.\n" +
            "V03: lockRoomStatus=null; occupant=false.\n" +
            "V04: lockRoomStatus=CLEANING; occupant=true.\n" +
            "V05: lockRoomStatus=CLEANING; occupant=false.\n" +
            "V06: lockRoomStatus=AVAILABLE; occupant=false.",
            "V01,V02,V04: BF(409,form); không completeRoomCleaning.\n" +
            "V03: BF(404,form); không gọi roomHasOccupant/completeRoomCleaning.\n" +
            "V05: Hoàn tất; gọi completeRoomCleaning(B,R,U) một lần.\n" +
            "V06: Hoàn tất no-op; không completeRoomCleaning.",
            6,
            () -> new BookingFixture(null),
            e -> {
            e.service.finishCleaning(e.actor,R);
            return null;
        });
        define("TC-STY-001", "Room Stay Management", "BookingService.checkIn",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Check-in booking hợp lệ\n" +
            "Mock CONFIRMED, RESERVED, AVAILABLE, overlap=false; cập nhật booking/allocation đều trả 1.",
            "Gọi checkInBooking và occupyAllocation, ghi event; trả detail fixture. Không kiểm state DB.",
            1,
            () -> new BookingFixture(null).confirmed(),
            e -> e.service.checkIn(e.actor,K));
        define("TC-STY-002", "Room Stay Management", "BookingService.checkIn",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh. Kiểm soát Instant.now tại mốc T.",
            "Check-in sớm giữ đủ thời lượng\n" +
            "Clock T=09:00; booking 10:00–12:00; mock overlap=false và update=1.",
            "Quan sát effectiveEnd=11:00, allocation 09:00–11:00, actual=09:00; event giữ giờ hẹn gốc; không dùng sleep.",
            1,
            () -> new BookingFixture(null).confirmed(),
            e -> e.service.checkIn(e.actor,K));
        define("TC-STY-003", "Room Stay Management", "BookingService.checkIn",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh. Kiểm soát Instant.now tại mốc T.",
            "Check-in trễ trong khung giờ\n" +
            "Booking 10–12h; now 10:30h",
            "Quan sát checkInBooking nhận actual=10:30 và effectiveEnd=12:00, không dịch end thành 12:30; trả fixture " +
            "detail.",
            1,
            () -> new BookingFixture(null).confirmed(),
            e -> e.service.checkIn(e.actor,K));
        define("TC-STY-004", "Room Stay Management", "BookingService.checkIn",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "CONFIRMED,start10:00,end12:00; AL RESERVED,operatingStatus AVAILABLE; overlap=false; " +
            "checkInBooking/shiftAndOccupyAllocation/occupyAllocation trả1; read-back F_detail. now=T. Reset mock/fixture " +
            "trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: K.status=DRAFT; các trường khác base.\n" +
            "V02: K.status=IN_HOUSE; các trường khác base.\n" +
            "V03: K.status=COMPLETED; các trường khác base.\n" +
            "V04: K.status=CANCELLED; các trường khác base.\n" +
            "V05: K.status=NO_SHOW; các trường khác base.\n" +
            "V06: K CONFIRMED,plannedStart08:00; plannedEnd=T−1s.\n" +
            "V07: K CONFIRMED,plannedStart08:00; plannedEnd=T.\n" +
            "V08: K CONFIRMED,end12:00; allocation=null.\n" +
            "V09: K CONFIRMED,end12:00; allocation.state=OCCUPIED.\n" +
            "V10: K CONFIRMED,end12:00; allocation.state=FINISHED.\n" +
            "V11: K CONFIRMED,end12:00; allocation.state=RELEASED.",
            "V01,V02,V03,V04,V05,V06,V07: BF(409,form); không checkInBooking/occupy/shift.\n" +
            "V08,V09,V10,V11: BF(409,roomId); không checkInBooking/occupy/shift.",
            11,
            () -> new BookingFixture(null).confirmed(),
            e -> e.service.checkIn(e.actor,K));
        define("TC-STY-005", "Room Stay Management", "BookingService.checkIn",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Phòng chưa sẵn sàng hoặc lịch xung đột\n" +
            "Mock allocation maintenance/cleaning/inactive; biến thể AVAILABLE nhưng hasRoomOverlap=true.",
            "409; không gọi checkInBooking/occupyAllocation. Không chạy truy vấn lịch thật.",
            4,
            () -> new BookingFixture(null).confirmed(),
            e -> e.service.checkIn(e.actor,K));
        define("TC-STY-013", "Room Stay Management", "BookingService.changeRoom",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "IN_HOUSE,start08:00,end10:00; AL OCCUPIED,R/RT,startsAt08:00; meter=null; target R2/RT,capacity4,AVAILABLE; " +
            "guestCount4; overlap=false. Request targetRoomId=R2,cause=CUSTOMER,reason=Đổi phòng. now=T. Reset mock/fixture" +
            " trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: request=null.\n" +
            "V02: cause=null.\n" +
            "V03: cause=UNKNOWN.\n" +
            "V04: reason=\"R\"×1001.\n" +
            "V05: targetRoomId=R; target fixture cùng phòng hiện tại.\n" +
            "V06: target capacity3,guestCount4.\n" +
            "V07: current.startsAt=T; booking.plannedEnd10:00.\n" +
            "V08: booking.plannedEnd=T; current.startsAt08:00.",
            "V01: BF(400,form); không mutation.\n" +
            "V02,V03: BF(400,cause); không mutation.\n" +
            "V04: BF(400,reason); không mutation.\n" +
            "V05,V06: BF(400,targetRoomId); không mutation.\n" +
            "V07,V08: BF(409,form); không mutation.",
            8,
            () -> new BookingFixture(new RoomChangeRequest(R2,"CUSTOMER","Đổi phòng")).roomChange(),
            e -> e.service.changeRoom(e.actor,K,(RoomChangeRequest)e.cmd));
        define("TC-STY-015", "Room Stay Management", "BookingService.changeRoom",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Từ chối đổi phòng khi dependency báo target bận\n" +
            "Mock target hợp lệ nhưng hasRoomOverlap=true tại lúc gọi changeRoom.",
            "409 trước finishOccupiedAllocation/insertChangedAllocation; không tạo tranh chấp thật.",
            1,
            () -> new BookingFixture(new RoomChangeRequest(R2,"CUSTOMER","Đổi phòng")).roomChange(),
            e -> e.service.changeRoom(e.actor,K,(RoomChangeRequest)e.cmd));
        define("TC-STY-007", "Room Stay Management", "BookingService.roomChangeQuote",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; K " +
            "IN_HOUSE,plannedEnd10:00; AL OCCUPIED,R/RT,08:00–10:00; guestCount4; target R2/RT2,capacity4; " +
            "overlap=false,meter=null. Base ROOM charge240000/2h,snapshot from08:00,to10:00; activeChargeTotal240000; " +
            "now=T. Cause CUSTOMER; hourly quote remaining09:00–10:00 ở RT2 =180000/1 charge. Reset mock/fixture trước mỗi " +
            "V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Cause CUSTOMER; base ROOM240000/08:00–10:00; now09:00; target quote09:00–10:00=180000.",
            "V01: " +
            "currentAmount240000,usedSegmentAmount120000,nextSegmentAmount180000,projectedAmount300000,adjustmentAmount60000;" +
            " không mutation.",
            1,
            () -> new BookingFixture(null).roomChange(),
            e -> e.service.roomChangeQuote(e.actor,K,(UUID)e.f.getOrDefault("targetId",R2),(String)e.f.getOrDefault("cause","CUSTOMER")));
        define("TC-STY-008", "Room Stay Management", "BookingService.roomChangeQuote",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Báo giá đổi phòng do lỗi cơ sở giữ tổng phí\n" +
            "Mock IN_HOUSE, snapshot charge gốc, target đắt hơn; cause BRANCH_FAULT.",
            "Qua roomChangeQuote, projectedAmount không tăng chỉ vì giá target; used+remaining giữ đúng tổng gốc; không gọi" +
            " mutation.",
            1,
            () -> new BookingFixture(null).roomChange(),
            e -> e.service.roomChangeQuote(e.actor,K,(UUID)e.f.getOrDefault("targetId",R2),(String)e.f.getOrDefault("cause","CUSTOMER")));
        define("TC-STY-009", "Room Stay Management", "BookingService.roomChangeQuote",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; K " +
            "IN_HOUSE,plannedEnd10:00; AL OCCUPIED,R/RT,08:00–10:00; guestCount4; target R2/RT2,capacity4; " +
            "overlap=false,meter=null. Base ROOM charge240000/2h,snapshot from08:00,to10:00; activeChargeTotal240000; " +
            "now=T. Cause CUSTOMER; hourly quote remaining09:00–10:00 ở RT2 =180000/1 charge. Source charge.amount240000; " +
            "repo.combo(C) trả C; source snapshot pricingModeCOMBO,duration120,from08,to10. Mỗi V chỉ thay target combos; " +
            "không mutation. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Source COMBO C: tên Queen Gói A,duration120,unitPrice240000,snapshot08–10; target combo C2 tên King Gói " +
            "A,duration120,price360000.\n" +
            "V02: Source COMBO C như V01; target combos=[]; hourly quote09–10=180000.",
            "V01: used120000,next180000,projected300000,adjustment60000; không hourly pricing.quote.\n" +
            "V02: used120000,next180000,projected300000,adjustment60000; " +
            "pricing.quote(RT2,B,timezone,09:00,10:00,HOURLY,null).",
            2,
            () -> new BookingFixture(null).roomChange(),
            e -> e.service.roomChangeQuote(e.actor,K,(UUID)e.f.getOrDefault("targetId",R2),(String)e.f.getOrDefault("cause","CUSTOMER")));
        define("TC-STY-010", "Room Stay Management", "BookingService.roomChangeQuote",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; K " +
            "IN_HOUSE,plannedEnd10:00; AL OCCUPIED,R/RT,08:00–10:00; guestCount4; target R2/RT2,capacity4; " +
            "overlap=false,meter=null. Base ROOM charge240000/2h,snapshot from08:00,to10:00; activeChargeTotal240000; " +
            "now=T. Cause CUSTOMER; hourly quote remaining09:00–10:00 ở RT2 =180000/1 charge. Source COMBO " +
            "charge240000,snapshot08–10,duration120,name Queen Gói A; repo.combo(C) có; source charge.comboId=C. Reset " +
            "mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Source C Queen Gói A/duration120; target combos=[King Gói A/duration180,King Gói B/duration120]; hourly " +
            "quote09–10=180000.",
            "V01: Không có equivalent; gọi pricing.quote target RT2/09–10/HOURLY/null; " +
            "used120000,next180000,projected300000,adjustment60000; không mutation.",
            1,
            () -> new BookingFixture(null).roomChange(),
            e -> e.service.roomChangeQuote(e.actor,K,(UUID)e.f.getOrDefault("targetId",R2),(String)e.f.getOrDefault("cause","CUSTOMER")));
        define("TC-STY-011", "Room Stay Management", "BookingService.roomChangeQuote",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; K " +
            "IN_HOUSE,plannedEnd10:00; AL OCCUPIED,R/RT,08:00–10:00; guestCount4; target R2/RT2,capacity4; " +
            "overlap=false,meter=null. Base ROOM charge240000/2h,snapshot from08:00,to10:00; activeChargeTotal240000; " +
            "now=T. Cause CUSTOMER; hourly quote remaining09:00–10:00 ở RT2 =180000/1 charge. K.plannedEnd11:00; AL08–11; " +
            "target đắt hơn; now09:00; mọi source quantity/unitPrice đủ. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu " +
            "nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: BRANCH_FAULT; một ROOM charge100001,snapshot08:00–11:00,activeTotal100001; đổi lúc09:00.\n" +
            "V02: BRANCH_FAULT; hai ROOM charges100001 và100001 cùng snapshot08:00–11:00; activeTotal200002; đổi09:00.",
            "V01: used33334,next66667,projected100001,adjustment0; không pricing.quote/ghi.\n" +
            "V02: used66668,next133334,projected200002,adjustment0; không pricing.quote/ghi.",
            2,
            () -> new BookingFixture(null).roomChange(),
            e -> e.service.roomChangeQuote(e.actor,K,(UUID)e.f.getOrDefault("targetId",R2),(String)e.f.getOrDefault("cause","CUSTOMER")));
        define("TC-STY-012", "Room Stay Management", "BookingService.roomChangeQuote",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; K " +
            "IN_HOUSE,plannedEnd10:00; AL OCCUPIED,R/RT,08:00–10:00; guestCount4; target R2/RT2,capacity4; " +
            "overlap=false,meter=null. Base ROOM charge240000/2h,snapshot from08:00,to10:00; activeChargeTotal240000; " +
            "now=T. Cause CUSTOMER; hourly quote remaining09:00–10:00 ở RT2 =180000/1 charge. Ghi đè now=10:00; " +
            "K.plannedEnd12:00; AL.startsAt09:00; activeTotal360000; base không có extensionStartsAt; snapshot extension có" +
            " đủ hai mốc; không mutation. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: BRANCH_FAULT; base240000 snapshot10–12; AL thực09–12; extension120000 snapshot " +
            "extensionStartsAt11:00,extensionEndsAt12:00; đổi10:00.",
            "V01: Base dịch09–11:used120000,next120000; extension giữ11–12:used0,next120000; " +
            "usedTotal120000,nextTotal240000,projected360000,adjustment0.",
            1,
            () -> new BookingFixture(null).roomChange(),
            e -> e.service.roomChangeQuote(e.actor,K,(UUID)e.f.getOrDefault("targetId",R2),(String)e.f.getOrDefault("cause","CUSTOMER")));
        define("TC-STY-014", "Room Stay Management", "BookingService.roomChangeQuote",
            "BookingService thật; BookingRepository và BookingPricingService mock; Identity/BranchScope A hợp lệ. Gọi " +
            "roomChangeQuote(who,K,targetRoomId,CUSTOMER). Booking K thuộc A. Chỉ stub các dependency đến điểm dừng của " +
            "từng V; không cần charge/target room vì lỗi xảy ra trước bước đọc allocation.",
            "V01: K.status=CONFIRMED, chưa có meter.\n" +
            "V02: K.status=IN_HOUSE; repo.meter(K) trả RUNNING.\n" +
            "V03: K.status=COMPLETED sau checkout; dữ liệu đã lưu có meter STOPPED (không cần stub meter vì không được " +
            "đọc).\n" +
            "STOPPED chỉ phát sinh trong quy trình checkout; không thiết kế luồng bình thường dừng meter rồi tiếp tục đổi " +
            "phòng/gia hạn.",
            "V01,V03: BookingFailure(409,form) tại requireInHouseBooking; không gọi " +
            "meter/currentAllocation/availableRoom/pricing.\n" +
            "V02: BookingFailure(409,form) sau khi đọc meter; không gọi currentAllocation/availableRoom/pricing.\n" +
            "Tất cả: không ghi allocation, charge hoặc event. Không mở lại quyền đổi phòng/gia hạn sau STOPPED; các thao " +
            "tác này không thuộc quy trình checkout.",
            3,
            () -> new BookingFixture(null).roomChange(),
            e -> e.service.roomChangeQuote(e.actor,K,(UUID)e.f.getOrDefault("targetId",R2),(String)e.f.getOrDefault("cause","CUSTOMER")));
        define("TC-STY-056", "Room Stay Management", "BookingService.stayOptions",
            "BookingService thật; BookingRepository/BookingPricingService mock; ObjectMapper thật. now=20/10/2026 09:00 địa" +
            " phương, timezone Asia/Ho_Chi_Minh; K IN_HOUSE, guestCount=4, allocation OCCUPIED room R. Repository trả rooms" +
            " [R(cap4),R2(cap3),R3(cap4),R4(cap5)]; không có overlap. 3 combo C1/C2/C3 cùng RT, duration=120, cùng hiệu lực" +
            " 01/10–01/11/2026 bao phủ tất cả mốc thử; tên lần lượt ngày/đêm/generic. Charge hiện tại mô tả Gói A. Gọi " +
            "stayOptions; không gọi controller/DB.",
            "V01: K.plannedEnd=20/10 12:00; allocation20/10 08:00–12:00.\n" +
            "V02: K.plannedEnd=21/10 07:00; allocation20/10 08:00–21/10 07:00.\n" +
            "V03: K.plannedEnd=20/10 23:00; allocation20/10 08:00–23:00.",
            "V01–V03: rooms trả [R3,R4] theo thứ tự fixture; không trả phòng hiện tại R hoặc R2 thiếu sức chứa. Cả 3 combo " +
            "[C1,C2,C3] đều được trả vì cùng cấu hình áp dụng, không loại theo tên. currentComboName=Gói A.\n" +
            "V01: currentDurationMinutes=240; query rooms interval09:00–12:00, excludeK.\n" +
            "V02: duration=1380; interval20/10 09:00–21/10 07:00.\n" +
            "V03: duration=900; interval09:00–23:00.\n" +
            "Không ghi dữ liệu.",
            3,
            () -> new BookingFixture(null),
            e -> e.service.stayOptions(e.actor,K));
        define("TC-STY-018", "Room Stay Management", "BookingService.extendStay",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Gia hạn theo giờ hợp lệ\n" +
            "Mock IN_HOUSE/OCCUPIED, không meter, overlap=false; updates=1; hours=1 hoặc 12.",
            "Gọi tăng end booking/allocation; insertCharge kind OVERTIME với quote/snapshot; event đúng dữ liệu; trả " +
            "fixture.",
            2,
            () -> new BookingFixture(new ExtendStayRequest("HOURLY",null,1)).extension(),
            e -> e.service.extendStay(e.actor,K,(ExtendStayRequest)e.cmd));
        define("TC-STY-019", "Room Stay Management", "BookingService.extendStay",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "IN_HOUSE,plannedEnd12:00; AL OCCUPIED,R/RT,endsAt12:00; meter=null; overlap=false; " +
            "updateInHouseBookingEnd/updateOccupiedAllocationEnd=1; combo C RT/Gói A/duration120/price200000/validFrom " +
            "trước start; quote COMBO200000/1 line; read-back F_detail; now=T. Reset mock/fixture trước mỗi V; chỉ thay dữ " +
            "liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Request COMBO,comboId=C,hours=null; startsAt12:00,combo duration120,quote200000.",
            "V01: endsAt14:00; updateInHouseBookingEnd(K,12:00,14:00),updateOccupiedAllocationEnd(AL,12:00,14:00); " +
            "insertCharge kindCOMBO amount200000/snapshot UC40; event BOOKING_EXTENDED; trả result " +
            "starts12/end14/quote200000.",
            1,
            () -> new BookingFixture(new ExtendStayRequest("HOURLY",null,1)).extension(),
            e -> e.service.extendStay(e.actor,K,(ExtendStayRequest)e.cmd));
        define("TC-STY-023", "Room Stay Management", "BookingService.extendStay",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Gia hạn vướng booking kế tiếp\n" +
            "Mock hasRoomOverlap=true cho khoảng extension đã báo giá.",
            "409 hours; không update end hay insertCharge/event.",
            1,
            () -> new BookingFixture(new ExtendStayRequest("HOURLY",null,1)).extension(),
            e -> e.service.extendStay(e.actor,K,(ExtendStayRequest)e.cmd));
        define("TC-STY-020", "Room Stay Management", "BookingService.extensionQuote",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; K " +
            "IN_HOUSE,plannedEnd12:00; AL OCCUPIED,R/RT; meter=null. Request HOURLY,hours1,comboId=null; quote " +
            "mock120000/1h. Combo C RT/Gói A/duration120/price200000; quote COMBO mock200000/1 line. now=T. Reset " +
            "mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: HOURLY,comboId=null,hours=1.\n" +
            "V02: HOURLY,comboId=null,hours=12; quote mock1440000.\n" +
            "V03: HOURLY,hours=null.\n" +
            "V04: HOURLY,hours=0.\n" +
            "V05: HOURLY,hours=13.\n" +
            "V06: HOURLY,hours1,comboId=C.\n" +
            "V07: COMBO,comboId=null.\n" +
            "V08: mode=UNKNOWN.\n" +
            "V09: mode=null.\n" +
            "V10: mode=\"   \".\n" +
            "V11: COMBO,comboId=C,hours=null; generic Gói A120 phút.",
            "V01: Quote120000; starts12:00,ends13:00; không ghi.\n" +
            "V02: Quote1440000; starts12:00,ends00:00 ngày21/10; không ghi.\n" +
            "V03,V04,V05: BF(400,hours); không pricing.quote/ghi.\n" +
            "V06,V07: BF(400,comboId); không pricing.quote/ghi.\n" +
            "V08,V09,V10: BF(400,pricingMode); không pricing.quote/ghi.\n" +
            "V11: Quote200000; starts12:00,ends14:00; không ghi.",
            11,
            () -> new BookingFixture(new ExtendStayRequest("HOURLY",null,1)).extension(),
            e -> e.service.extensionQuote(e.actor,K,(ExtendStayRequest)e.cmd));
        define("TC-STY-021", "Room Stay Management", "BookingService.extensionQuote",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; K " +
            "IN_HOUSE,plannedEnd12:00; AL OCCUPIED,R/RT; meter=null. Request HOURLY,hours1,comboId=null; quote " +
            "mock120000/1h. Combo C RT/Gói A/duration120/price200000; quote COMBO mock200000/1 line. now=T. " +
            "AL.startsAt=06:00,AL.endsAt=K.plannedEnd tương ứng V; input duration luôn1h để cô lập so sánh endsAt với T. " +
            "Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: HOURLY,hours1; plannedEnd=T−1h−1s.\n" +
            "V02: HOURLY,hours1; plannedEnd=T−1h.\n" +
            "V03: HOURLY,hours1; plannedEnd=T−1h+1s; quote mock120000.",
            "V01,V02: BF(400,hours); không pricing.quote/ghi.\n" +
            "V03: Quote120000; endsAt=T+1s; không ghi.",
            3,
            () -> new BookingFixture(new ExtendStayRequest("HOURLY",null,1)).extension(),
            e -> e.service.extensionQuote(e.actor,K,(ExtendStayRequest)e.cmd));
        define("TC-STY-022", "Room Stay Management", "BookingService.extensionQuote",
            "BookingService thật; BookingRepository và BookingPricingService mock; ObjectMapper thật. K=IN_HOUSE, " +
            "meter=null; allocation OCCUPIED đúng loại RT. Branch timezone Asia/Ho_Chi_Minh. Request COMBO với comboId=C, " +
            "durationMinutes=120, price=300000. Fixture thời gian cấu hình: validFrom=24/12/2026 08:00, validTo=24/12/2026 " +
            "18:00 địa phương; đây là khoảng thời gian cụ thể, không tự suy ra lịch lặp hằng ngày. Gọi extensionQuote; " +
            "startsAt luôn là K.plannedEnd. Pricing mock trả Q (COMBO,300000,0) khi hợp lệ.",
            "V01: tên=\"Combo ngày\"; K.plannedEnd=24/12 10:00, trong cấu hình.\n" +
            "V02: giữ nguyên V01, chỉ đổi tên=\"Combo đêm\".\n" +
            "V03: giữ nguyên V01, chỉ đổi tên=\"Gói linh hoạt\".\n" +
            "V04: tên=\"Combo ngày\"; K.plannedEnd=24/12 07:00, ngoài cấu hình; pricing mock ném BookingFailure(409,comboId) " +
            "khi được gọi.\n" +
            "V05: tên=\"Combo đêm\"; K.plannedEnd=24/12 23:00, ngoài cấu hình; pricing mock ném cùng lỗi.",
            "V01–V03: trả startsAt=10:00, endsAt=12:00; quote=Q. Gọi pricing.quote(RT,A,timezone,10:00,12:00,COMBO,C) đúng " +
            "1 lần. Đổi riêng tên không làm đổi điều kiện áp dụng hay số tiền.\n" +
            "V04,V05: gọi pricing.quote với khoảng 07:00–09:00 hoặc 23:00–01:00 rồi truyền nguyên " +
            "BookingFailure(409,comboId) từ dependency; không áp đặt 07:00/23:00 theo tên.\n" +
            "Tất cả: không ghi allocation/charge/event. Giá và hiệu lực thực tế của pricing được kiểm ở ca unit pricing " +
            "riêng, không coi mock Q là đã kiểm thuật toán giá.",
            5,
            () -> new BookingFixture(new ExtendStayRequest("HOURLY",null,1)).extension(),
            e -> e.service.extensionQuote(e.actor,K,(ExtendStayRequest)e.cmd));
        define("TC-STY-026", "Room Stay Management", "BookingService.startHourlyMeter",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "IN_HOUSE,plannedEnd10:00; AL OCCUPIED,R/RT,endsAt10:00; meter=null,overlap=false; " +
            "updateOccupiedAllocationEnd=1; meteredRate mock PR/hourly120000; read-back F_detail. now=T. Reset mock/fixture" +
            " trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: K.end10:00,AL.end10:00; next=null.\n" +
            "V02: K.end10:00; next10:45.\n" +
            "V03: K.end10:00; next21/10 10:00.\n" +
            "V04: K.end10:00; next21/10 10:00:01.\n" +
            "V05: K.end08:00,AL.end08:00; next=null.",
            "V01,V03,V04: billedFrom10:00,reservedUntil21/10 10:00; startMeter rate120000.\n" +
            "V02: billedFrom10:00,reservedUntil10:45; startMeter rate120000.\n" +
            "V05: billedFrom09:00,reservedUntil21/10 09:00; startMeter rate120000.",
            5,
            () -> new BookingFixture(null),
            e -> e.service.startHourlyMeter(e.actor,K));
        define("TC-STY-027", "Room Stay Management", "BookingService.startHourlyMeter",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "IN_HOUSE,plannedEnd10:00; AL OCCUPIED,R/RT,endsAt10:00; meter=null,overlap=false; " +
            "updateOccupiedAllocationEnd=1; meteredRate mock PR/hourly120000; read-back F_detail. now=T. Reset mock/fixture" +
            " trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: meter=RUNNING.\n" +
            "V02: meter=STOPPED.\n" +
            "V03: meter=null; nextRoomBookingStart10:00 bằng billedFrom.\n" +
            "V04: meter=null; K.end=AL.end08:00; next08:59:59 trước billedFrom09:00.\n" +
            "V05: meter=null; next10:45; hasRoomOverlap=true.",
            "V01,V02: BF(409,form); không nextRoomBookingStart/startMeter/event.\n" +
            "V03,V04: BF(409,form); không hasRoomOverlap/startMeter/event.\n" +
            "V05: BF(409,form); không meteredRate/updateEnd/startMeter/event.",
            5,
            () -> new BookingFixture(null),
            e -> e.service.startHourlyMeter(e.actor,K));
        define("TC-STY-028", "Room Stay Management", "BookingService.stopHourlyMeter",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh. Kiểm soát Instant.now tại mốc T.",
            "Dừng meter theo phút thực\n" +
            "Rate 120000/h; elapsed 0ms/1ms/60000ms/60001ms kể từ billedFrom",
            "BilledMinutes 0/1/1/2; amount 0/2000/2000/4000; 0 phút không tạo charge",
            4,
            () -> new BookingFixture(null),
            e -> e.service.stopHourlyMeter(e.actor,K));
        define("TC-STY-029", "Room Stay Management", "BookingService.stopHourlyMeter",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Dừng meter lặp và không có meter\n" +
            "Mock meter STOPPED; biến thể không meter hoặc allocationId không khớp.",
            "STOPPED trả detail fixture và không gọi insertCharge/stopMeter lần nữa; thiếu/khác allocation: 409.",
            3,
            () -> new BookingFixture(null),
            e -> e.service.stopHourlyMeter(e.actor,K));
        define("TC-STY-068", "Room Stay Management", "BookingService.checkIn",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "CONFIRMED,start10:00,end12:00; AL RESERVED,operatingStatus AVAILABLE; overlap=false; " +
            "checkInBooking/shiftAndOccupyAllocation/occupyAllocation trả1; read-back F_detail. now=T. Reset mock trước mỗi" +
            " V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi " +
            "ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Early: K10:00–12:00,now09:00; checkInBooking(K,T,11:00)=0; shiftAndOccupyAllocation=1.\n" +
            "V02: K08:00–10:00,now09:00; checkInBooking(K,T,10:00)=0; occupyAllocation=1.",
            "V01: Cả checkInBooking và shiftAndOccupyAllocation đều được gọi; BF(409,form); không event/return thành công.\n" +
            "V02: Cả checkInBooking và occupyAllocation đều được gọi; BF(409,form); không event/return thành công.",
            2,
            () -> new BookingFixture(null).confirmed(),
            e -> e.service.checkIn(e.actor,K));
        define("TC-STY-069", "Room Stay Management", "BookingService.checkIn",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "CONFIRMED,start10:00,end12:00; AL RESERVED,operatingStatus AVAILABLE; overlap=false; " +
            "checkInBooking/shiftAndOccupyAllocation/occupyAllocation trả1; read-back F_detail. now=T. Reset mock trước mỗi" +
            " V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi " +
            "ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Early K10–12; checkInBooking=1,shiftAndOccupyAllocation=0.\n" +
            "V02: K08–10; checkInBooking=1,occupyAllocation=0.",
            "V01,V02: BF(409,form) sau hai lời gọi; không event/read-back thành công.",
            2,
            () -> new BookingFixture(null).confirmed(),
            e -> e.service.checkIn(e.actor,K));
        define("TC-STY-070", "Room Stay Management", "BookingService.checkIn",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "CONFIRMED,start10:00,end12:00; AL RESERVED,operatingStatus AVAILABLE; overlap=false; " +
            "checkInBooking/shiftAndOccupyAllocation/occupyAllocation trả1; read-back F_detail. now=T. Reset mock trước mỗi" +
            " V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi " +
            "ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Early K10–12; checkInBooking=1; shiftAndOccupyAllocation ném DataIntegrityViolationException có root " +
            "message no_room_overlap.\n" +
            "V02: Như V01 nhưng root message no_booking_parallel_rooms.\n" +
            "V03: Như V01 nhưng root message unrelated_constraint.",
            "V01,V02: BF(409,roomId); không event/read-back thành công.\n" +
            "V03: Rethrow cùng DataIntegrityViolationException instance; không event/read-back thành công.",
            3,
            () -> new BookingFixture(null).confirmed(),
            e -> e.service.checkIn(e.actor,K));
        define("TC-STY-066", "Room Stay Management", "BookingService.changeRoom",
            "BookingService thật; BookingRepository/BookingPricingService mock,ObjectMapper thật. Identity U/RECEPTIONIST; " +
            "branchScope B; lockBookingId(K,B) có; read-back detail dùng các projection fixture đầy đủ. " +
            "Booking/allocation/charge/request cụ thể được xác định bên dưới. K IN_HOUSE,plannedEnd10:00; AL " +
            "OCCUPIED,R/RT,08:00–10:00; target R2/RT2,capacity4; guestCount4,meter=null,overlap=false; source charge CH " +
            "ROOM,quantity2,unitPrice120000,amount240000,snapshot{pricingMode:HOURLY,from08:00,to10:00}; activeTotal240000." +
            " finishOccupiedAllocation=1,insertChangedAllocation=AL2,occupyAllocation=1,supersedeCharge(CH)=1; read-back " +
            "F_detail_new có fixture R2. now=T. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi;" +
            " timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Request targetR2,cause=BRANCH_FAULT,reason=Hỏng thiết bị.",
            "V01: Theo thứ tự: finish AL@T,insert AL2(R2,T,10:00),occupy AL2,supersede " +
            "CH,insertCharge(AL,used120000),insertCharge(AL2,next120000),event BOOKING_ROOM_CHANGED. Result " +
            "previous240000,updated240000,adjustment0,booking=F_detail_new; không pricing.quote.",
            1,
            () -> new BookingFixture(new RoomChangeRequest(R2,"CUSTOMER","Đổi phòng")).roomChange(),
            e -> e.service.changeRoom(e.actor,K,(RoomChangeRequest)e.cmd));
        define("TC-STY-067", "Room Stay Management", "BookingService.changeRoom",
            "BookingService thật; BookingRepository/BookingPricingService mock,ObjectMapper thật. Identity U/RECEPTIONIST; " +
            "branchScope B; lockBookingId(K,B) có; read-back detail dùng các projection fixture đầy đủ. " +
            "Booking/allocation/charge/request cụ thể được xác định bên dưới. K IN_HOUSE,plannedEnd10:00; AL " +
            "OCCUPIED,R/RT,08:00–10:00; target R2/RT2,capacity4; guestCount4,meter=null,overlap=false; source charge CH " +
            "ROOM,quantity2,unitPrice120000,amount240000,snapshot{pricingMode:HOURLY,from08:00,to10:00}; activeTotal240000." +
            " finishOccupiedAllocation=1,insertChangedAllocation=AL2,occupyAllocation=1,supersedeCharge(CH)=1; read-back " +
            "F_detail_new có fixture R2. now=T. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi;" +
            " timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Request targetR2,cause=CUSTOMER,reason=Khách nâng hạng; " +
            "pricing.quote(RT2,B,timezone,09:00,10:00,HOURLY,null) trả1 line ROOM180000.",
            "V01: finish AL,insert/occupy AL2,supersede CH,insert used120000 vào AL và next180000 vào AL2; event " +
            "causeCUSTOMER/used120000/next180000. Result previous240000,updated300000,adjustment60000,booking=F_detail_new.",
            1,
            () -> new BookingFixture(new RoomChangeRequest(R2,"CUSTOMER","Đổi phòng")).roomChange(),
            e -> e.service.changeRoom(e.actor,K,(RoomChangeRequest)e.cmd));
        define("TC-STY-071", "Room Stay Management", "BookingService.changeRoom",
            "BookingService thật; BookingRepository/BookingPricingService mock,ObjectMapper thật. Identity U/RECEPTIONIST; " +
            "branchScope B; lockBookingId(K,B) có; read-back detail dùng các projection fixture đầy đủ. " +
            "Booking/allocation/charge/request cụ thể được xác định bên dưới. K IN_HOUSE,plannedEnd10:00; AL " +
            "OCCUPIED,R/RT,08:00–10:00; target R2/RT2,capacity4; guestCount4,meter=null,overlap=false; source charge CH " +
            "ROOM,quantity2,unitPrice120000,amount240000,snapshot{pricingMode:HOURLY,from08:00,to10:00}; activeTotal240000." +
            " finishOccupiedAllocation=1,insertChangedAllocation=AL2,occupyAllocation=1,supersedeCharge(CH)=1; read-back " +
            "F_detail_new có fixture R2. now=T. Với các ca lỗi write-count, request BRANCH_FAULT và reason=Hỏng thiết bị; " +
            "không cần pricing.quote. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp" +
            " map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: finishOccupiedAllocation(AL,T,...)=0.",
            "V01: BF(409,form); không insertChangedAllocation/occupyAllocation/supersedeCharge/insertCharge/event.",
            1,
            () -> new BookingFixture(new RoomChangeRequest(R2,"CUSTOMER","Đổi phòng")).roomChange(),
            e -> e.service.changeRoom(e.actor,K,(RoomChangeRequest)e.cmd));
        define("TC-STY-072", "Room Stay Management", "BookingService.changeRoom",
            "BookingService thật; BookingRepository/BookingPricingService mock,ObjectMapper thật. Identity U/RECEPTIONIST; " +
            "branchScope B; lockBookingId(K,B) có; read-back detail dùng các projection fixture đầy đủ. " +
            "Booking/allocation/charge/request cụ thể được xác định bên dưới. K IN_HOUSE,plannedEnd10:00; AL " +
            "OCCUPIED,R/RT,08:00–10:00; target R2/RT2,capacity4; guestCount4,meter=null,overlap=false; source charge CH " +
            "ROOM,quantity2,unitPrice120000,amount240000,snapshot{pricingMode:HOURLY,from08:00,to10:00}; activeTotal240000." +
            " finishOccupiedAllocation=1,insertChangedAllocation=AL2,occupyAllocation=1,supersedeCharge(CH)=1; read-back " +
            "F_detail_new có fixture R2. now=T. Với các ca lỗi write-count, request BRANCH_FAULT và reason=Hỏng thiết bị; " +
            "không cần pricing.quote. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp" +
            " map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: finish=1,insertChangedAllocation trả AL2; occupyAllocation(AL2,T)=0.",
            "V01: finish và insert AL2 đã gọi; BF(409,form); không supersedeCharge/insertCharge/event.",
            1,
            () -> new BookingFixture(new RoomChangeRequest(R2,"CUSTOMER","Đổi phòng")).roomChange(),
            e -> e.service.changeRoom(e.actor,K,(RoomChangeRequest)e.cmd));
        define("TC-STY-073", "Room Stay Management", "BookingService.changeRoom",
            "BookingService thật; BookingRepository/BookingPricingService mock,ObjectMapper thật. Identity U/RECEPTIONIST; " +
            "branchScope B; lockBookingId(K,B) có; read-back detail dùng các projection fixture đầy đủ. " +
            "Booking/allocation/charge/request cụ thể được xác định bên dưới. K IN_HOUSE,plannedEnd10:00; AL " +
            "OCCUPIED,R/RT,08:00–10:00; target R2/RT2,capacity4; guestCount4,meter=null,overlap=false; source charge CH " +
            "ROOM,quantity2,unitPrice120000,amount240000,snapshot{pricingMode:HOURLY,from08:00,to10:00}; activeTotal240000." +
            " finishOccupiedAllocation=1,insertChangedAllocation=AL2,occupyAllocation=1,supersedeCharge(CH)=1; read-back " +
            "F_detail_new có fixture R2. now=T. Với các ca lỗi write-count, request BRANCH_FAULT và reason=Hỏng thiết bị; " +
            "không cần pricing.quote. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp" +
            " map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Một charge CH240000; finish/occupy=1; supersedeCharge(CH)=0.\n" +
            "V02: Hai charge CH1/CH2,mỗi amount120000,snapshot08–10; total240000; supersede CH1=1,CH2=0.",
            "V01: BF(409,form); không insertCharge/event; finish/insert/occupy đã gọi.\n" +
            "V02: Đã gọi supersede CH1 rồi CH2; BF(409,form); không insertCharge/event.",
            2,
            () -> new BookingFixture(new RoomChangeRequest(R2,"CUSTOMER","Đổi phòng")).roomChange(),
            e -> e.service.changeRoom(e.actor,K,(RoomChangeRequest)e.cmd));
        define("TC-STY-074", "Room Stay Management", "BookingService.extendStay",
            "BookingService thật; BookingRepository/BookingPricingService mock,ObjectMapper thật. Identity U/RECEPTIONIST; " +
            "scope B; lock K có; K IN_HOUSE,plannedEnd12:00; AL OCCUPIED,R/RT,endsAt12:00; meter=null,overlap=false. " +
            "Request HOURLY,hours1,comboIdnull; pricing.quote mock HOURLY120000,1 chargeROOM,interval12–13. " +
            "updateInHouseBookingEnd/updateOccupiedAllocationEnd=1 trừ V nêu khác; read-back F_detail; now=T. Reset mock " +
            "trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to " +
            "là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Request HOURLY,hours1,comboIdnull; quote mock120000,end13:00; updateInHouseBookingEnd=0.",
            "V01: BF(409,form); không updateOccupiedAllocationEnd do short-circuit OR; không insertCharge/event.",
            1,
            () -> new BookingFixture(new ExtendStayRequest("HOURLY",null,1)).extension(),
            e -> e.service.extendStay(e.actor,K,(ExtendStayRequest)e.cmd));
        define("TC-STY-075", "Room Stay Management", "BookingService.extendStay",
            "BookingService thật; BookingRepository/BookingPricingService mock,ObjectMapper thật. Identity U/RECEPTIONIST; " +
            "scope B; lock K có; K IN_HOUSE,plannedEnd12:00; AL OCCUPIED,R/RT,endsAt12:00; meter=null,overlap=false. " +
            "Request HOURLY,hours1,comboIdnull; pricing.quote mock HOURLY120000,1 chargeROOM,interval12–13. " +
            "updateInHouseBookingEnd/updateOccupiedAllocationEnd=1 trừ V nêu khác; read-back F_detail; now=T. Reset mock " +
            "trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to " +
            "là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Request HOURLY,hours1,comboIdnull; quote mock120000,end13:00; " +
            "updateInHouseBookingEnd=1,updateOccupiedAllocationEnd=0.",
            "V01: Cả hai update được gọi; BF(409,form); không insertCharge/event.",
            1,
            () -> new BookingFixture(new ExtendStayRequest("HOURLY",null,1)).extension(),
            e -> e.service.extendStay(e.actor,K,(ExtendStayRequest)e.cmd));
        define("TC-STY-076", "Room Stay Management", "BookingService.startHourlyMeter",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "IN_HOUSE,plannedEnd10:00; AL OCCUPIED,R/RT,endsAt10:00; meter=null,overlap=false; " +
            "updateOccupiedAllocationEnd=1; meteredRate mock PR/hourly120000; read-back F_detail. now=T. Reset mock trước " +
            "mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là " +
            "chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: nextRoomBookingStart10:45,overlapfalse; meteredRate PR120000; " +
            "updateOccupiedAllocationEnd(AL,10:00,10:45)=0.",
            "V01: meteredRate đã được gọi; BF(409,form); không startMeter/event/read-back thành công.",
            1,
            () -> new BookingFixture(null),
            e -> e.service.startHourlyMeter(e.actor,K));
    }

    private void register() {
        checkIn();
        changeRoom();
        extension();
        meters();
    }

    private void checkIn() {
        valid(s(1),e-> {
            e.fixture("booking","plannedStart",time("08:00:00"));
        },e-> {
            changed(e,"checkInBooking");
            e.count(e.repo,"occupyAllocation",1);
            e.count(e.repo,"event",1);
        });
        valid(s(2),e-> {
        },e-> {
            changed(e,"checkInBooking");
            equal(e.inv(e.repo,"checkInBooking").get(0).getArgument(2),time("11:00:00"),"Early checkin keeps 2h");
            e.count(e.repo,"shiftAndOccupyAllocation",1);
        });
        valid(s(3),e-> {
            e.fixture("booking","plannedStart",time("08:00:00"));
            e.fixture("booking","plannedEnd",time("10:00:00"));
        },e-> {
            changed(e,"checkInBooking");
            equal(e.inv(e.repo,"checkInBooking").get(0).getArgument(2),time("10:00:00"),"Late checkin keeps original end");
            e.count(e.repo,"occupyAllocation",1);
        });
        for(String state:List.of("DRAFT","IN_HOUSE","COMPLETED","CANCELLED","NO_SHOW"))bad(s(4),e->e.fixture("booking","status",state),409,"form");
        for(Instant end:List.of(T.minusSeconds(1),T))bad(s(4),e->e.fixture("booking","plannedEnd",end),409,"form");
        bad(s(4),e->e.f.put("allocation",null),409,"roomId");
        for(String state:List.of("OCCUPIED","FINISHED","RELEASED"))bad(s(4),e->e.fixture("allocation","state",state),409,"roomId");
        for(String status:List.of("UNDER_MAINTENANCE","CLEANING","INACTIVE"))bad(s(5),e->e.fixture("allocation","operatingStatus",status),409,"roomId");
        bad(s(5),e->e.answers.put("hasRoomOverlap",true),409,"roomId");
        for(int which:new int[] {
            68,69
        })for(boolean early:new boolean[] {
            true,false
        })add(s(which),e-> {
            if(!early) {
                e.fixture("booking","plannedStart",time("08:00:00"));
                e.fixture("booking","plannedEnd",time("10:00:00"));
            }
            e.answers.put(which==68?"checkInBooking":early?"shiftAndOccupyAllocation":"occupyAllocation",0);
        },409,"form",e-> {
            e.count(e.repo,"checkInBooking",1);
            e.count(e.repo,early?"shiftAndOccupyAllocation":"occupyAllocation",1);
            e.none(e.repo,"event");
        });
        for(String msg:List.of("no_room_overlap","no_booking_parallel_rooms","unrelated_constraint")) {
            boolean translated=!msg.equals("unrelated_constraint");
            add(s(70),e->e.answers.put("shiftAndOccupyAllocation",new DataIntegrityViolationException("shift",new RuntimeException(msg))),translated?409:-3,translated?"roomId":null,e-> {
                e.none(e.repo,"event");
                if(!translated)equal(e.failure,e.answers.get("shiftAndOccupyAllocation"),"Same constraint exception");
            });
        }
        String[] statuses= {
            "UNDER_MAINTENANCE","INACTIVE",null,"CLEANING","CLEANING","AVAILABLE"
        };
        for(int i=0;i<6;i++) {
            int n=i;
            add("TC-ROM-027",e-> {
                e.answers.put("lockRoomStatus",statuses[n]);
                e.answers.put("roomHasOccupant",n==3);
            },i>=4?0:i==2?404:409,i>=4?null:"form",e->e.count(e.repo,"completeRoomCleaning",n==4?1:0));
        }
    }
    static void remainingQuote(BookingFixture e) {
        e.f.put("quote",new PriceQuote("HOURLY",money("180000"),BigDecimal.ZERO,List.of(new ChargeLine("ROOM","Target",BigDecimal.ONE,money("180000"),money("180000"),PR,null,Map.of("from",T.toString(),"to",time("10:00:00").toString())))));
    }
    static ChargeRow charge(UUID id,String amount,Instant start,Instant end,boolean combo) {
        return new ChargeRow(id,AL,combo?"COMBO":"ROOM",combo?"Queen Gói A":"Room",combo?BigDecimal.ONE:money("2"),money(amount),money(amount),"ACTIVE",combo?null:PR,combo?C:null,JSON.writeValueAsString(map("from",start.toString(),"to",end.toString(),"pricingMode",combo?"COMBO":"HOURLY","name","Queen Gói A","durationMinutes",120)),T);
    }
    static void amounts(BookingFixture e,String used,String next,String total,String delta) {
        var q=(RoomChangeQuote)e.actual;
        equal(q.currentAmount(),e.f.get("activeTotal"),"Previous");
        equal(q.usedSegmentAmount(),money(used),"Used segment");
        equal(q.nextSegmentAmount(),money(next),"Next segment");
        equal(q.projectedAmount(),money(total),"Projected total");
        equal(q.adjustmentAmount(),money(delta),"Adjustment");
        e.noWrites();
    }

    private void changeRoom() {
        bad(s(13),e->e.cmd=null,400,"form");
        for(String cause:Arrays.asList(null,"UNKNOWN"))bad(s(13),e->e.command("cause",cause),400,"cause");
        bad(s(13),e->e.command("reason","R".repeat(1001)),400,"reason");
        bad(s(13),e->e.command("targetRoomId",R),400,"targetRoomId");
        bad(s(13),e->e.fixture("target","capacity",3),400,"targetRoomId");
        bad(s(13),e->e.fixture("allocation","startsAt",T),409,"form");
        bad(s(13),e->e.fixture("booking","plannedEnd",T),409,"form");
        bad(s(15),e->e.answers.put("hasRoomOverlap",true),409,"roomId");
        valid(s(7),RoomStayManualTest::remainingQuote,e->amounts(e,"120000","180000","300000","60000"));
        valid(s(8),e->e.f.put("cause","BRANCH_FAULT"),e-> {
            amounts(e,"120000","120000","240000","0");
            e.none(e.pricing,"quote");
        });
        for(boolean equivalent:new boolean[] {
            true,false
        })valid(s(9),e-> {
            remainingQuote(e);
            e.f.put("charges",List.of(charge(PR,"240000",time("08:00:00"),time("10:00:00"),true)));
            e.fixture("combo","name","Queen Gói A");
            var target=(ComboRow)change(change(change(e.f.get("combo"),"id",id(88)),"name","King Gói A"),"price",money("360000"));
            e.f.put("combos",equivalent?List.of(target):List.of());
        },e-> {
            amounts(e,"120000","180000","300000","60000");
            e.count(e.pricing,"quote",equivalent?0:1);
        });
        valid(s(10),e-> {
            remainingQuote(e);
            e.f.put("charges",List.of(charge(PR,"240000",time("08:00:00"),time("10:00:00"),true)));
            e.fixture("combo","name","Queen Gói A");
            e.f.put("combos",List.of(change(change(e.f.get("combo"),"name","King Gói A"),"durationMinutes",180),change(e.f.get("combo"),"name","King Gói B")));
        },e-> {
            amounts(e,"120000","180000","300000","60000");
            e.count(e.pricing,"quote",1);
        });
        for(int n:new int[] {
            1,2
        })valid(s(11),e-> {
            e.f.put("cause","BRANCH_FAULT");
            e.fixture("booking","plannedEnd",time("11:00:00"));
            var cs=new ArrayList<ChargeRow>();
            for(int i=0;i<n;i++)cs.add(charge(id(80+i),"100001",time("08:00:00"),time("11:00:00"),false));
            e.f.put("charges",cs);
            e.f.put("activeTotal",money(n==1?"100001":"200002"));
        },e-> {
            amounts(e,n==1?"33334":"66668",n==1?"66667":"133334",n==1?"100001":"200002","0");
            e.none(e.pricing,"quote");
        });
        valid(s(12),e-> {
            e.now=time("10:00:00");
            e.f.put("cause","BRANCH_FAULT");
            e.fixture("allocation","startsAt",time("09:00:00"));
            e.fixture("allocation","endsAt",time("12:00:00"));
            e.fixture("booking","plannedEnd",time("12:00:00"));
            var base=charge(PR,"240000",time("10:00:00"),time("12:00:00"),false);
            var ext=charge(id(88),"120000",time("11:00:00"),time("12:00:00"),false);
            ext=(ChargeRow)change(ext,"snapshot",JSON.writeValueAsString(map("extensionStartsAt",time("11:00:00").toString(),"extensionEndsAt",time("12:00:00").toString())));
            e.f.put("charges",List.of(base,ext));
            e.f.put("activeTotal",money("360000"));
        },e->amounts(e,"120000","240000","360000","0"));
        bad(s(14),e->e.fixture("booking","status","CONFIRMED"),409,"form");
        bad(s(14),e->e.f.put("meter",meter("RUNNING")),409,"form");
        bad(s(14),e->e.fixture("booking","status","COMPLETED"),409,"form");
        for(int id:new int[] {
            66,67
        })valid(s(id),e-> {
            remainingQuote(e);
            e.command("cause",id==66?"BRANCH_FAULT":"CUSTOMER");
            e.command("reason",id==66?"Hỏng thiết bị":"Khách nâng hạng");
        },e-> {
            var r=(RoomChangeResult)e.actual;
            equal(r.previousAmount(),money("240000"),"Previous");
            equal(r.updatedAmount(),money(id==66?"240000":"300000"),"Updated");
            equal(r.adjustmentAmount(),money(id==66?"0":"60000"),"Delta");
            e.count(e.repo,"finishOccupiedAllocation",1);
            e.count(e.repo,"insertChangedAllocation",1);
            e.count(e.repo,"occupyAllocation",1);
            e.count(e.repo,"supersedeCharge",1);
            e.count(e.repo,"insertCharge",2);
            e.count(e.repo,"event",1);
            var writes=e.inv(e.repo,"insertCharge");
            equal(((ChargeLine)writes.get(0).getArgument(2)).amount(),money("120000"),"Used charge");
            equal(((ChargeLine)writes.get(1).getArgument(2)).amount(),money(id==66?"120000":"180000"),"Next charge");
            equal(writes.get(0).getArgument(1),AL,"Old allocation");
            equal(writes.get(1).getArgument(1),com.gcmb.backend.manual.support.ManualTestData.id(66),"New allocation");
            check(e.inv(e.repo,"finishOccupiedAllocation").get(0).getSequenceNumber()<e.inv(e.repo,"insertChangedAllocation").get(0).getSequenceNumber(),"Finish before insert");
        });
        add(s(71),e-> {
            remainingQuote(e);
            e.answers.put("finishOccupiedAllocation",0);
        },409,"form",e->e.none(e.repo,"insertChangedAllocation","occupyAllocation","supersedeCharge","insertCharge","event"));
        add(s(72),e-> {
            remainingQuote(e);
            e.answers.put("occupyAllocation",0);
        },409,"form",e-> {
            e.count(e.repo,"insertChangedAllocation",1);
            e.none(e.repo,"supersedeCharge","insertCharge","event");
        });
        add(s(73),e-> {
            remainingQuote(e);
            e.answers.put("supersedeCharge",0);
        },409,"form",e-> {
            e.count(e.repo,"supersedeCharge",1);
            e.none(e.repo,"insertCharge","event");
        });
        add(s(73),e-> {
            remainingQuote(e);
            e.f.put("charges",List.of(charge(PR,"120000",time("08:00:00"),time("10:00:00"),false),charge(id(88),"120000",time("08:00:00"),time("10:00:00"),false)));
            org.mockito.Mockito.when(e.repo.supersedeCharge(PR)).thenReturn(1);
            org.mockito.Mockito.when(e.repo.supersedeCharge(id(88))).thenReturn(0);
        },409,"form",e-> {
            e.count(e.repo,"supersedeCharge",2);
            e.none(e.repo,"insertCharge","event");
        });
        options();
    }

    private void options() {
        for(int n=0;n<3;n++) {
            int v=n;
            valid(s(56),e-> {
                Instant end=v==0?time("12:00:00"):v==1?time("07:00:00").plusSeconds(86400):time("23:00:00");
                e.fixture("booking","plannedEnd",end);
                e.fixture("allocation","endsAt",end);
                e.answers.put("availableRoomsForEdit",List.of(e.f.get("room"),change(e.f.get("target"),"capacity",3),change(e.f.get("target"),"id",id(33)),change(change(e.f.get("target"),"id",id(34)),"capacity",5)));
                e.f.put("combos",List.of(change(e.f.get("combo"),"name","Gói ngày"),change(change(e.f.get("combo"),"id",id(88)),"name","Gói đêm"),change(e.f.get("combo"),"id",id(89))));
                e.f.put("charges",List.of(comboCharge(true,true)));
            },e-> {
                var r=(StayOptions)e.actual;
                equal(r.availableRooms().stream().map(AvailableRoom::id).toList(),List.of(id(33),id(34)),"Available rooms");
                equal(r.extensionCombos().size(),3,"Name-independent combo selection");
                equal(r.currentDurationMinutes(),v==0?240L:v==1?1380L:900L,"Duration");
                e.noWrites();
            });
        }
    }

    private void extension() {
        for(int hours:new int[] {
            1,12
        })valid(s(18),e-> {
            e.command("hours",hours);
            e.f.put("quote",new PriceQuote("HOURLY",money(hours==1?"120000":"1440000"),BigDecimal.ZERO,List.of(line(hours==1?"120000":"1440000"))));
        },e-> {
            var r=(ExtensionResult)e.actual;
            equal(r.endsAt(),time("12:00:00").plusSeconds(hours*3600L),"Extension end");
            e.count(e.repo,"updateInHouseBookingEnd",1);
            e.count(e.repo,"updateOccupiedAllocationEnd",1);
            equal(((ChargeLine)e.inv(e.repo,"insertCharge").get(0).getArgument(2)).kind(),"OVERTIME","Kind");
            e.count(e.repo,"event",1);
        });
        valid(s(19),e-> {
            e.cmd=new ExtendStayRequest("COMBO",C,null);
            e.f.put("quote",new PriceQuote("COMBO",money("200000"),BigDecimal.ZERO,List.of(new ChargeLine("COMBO","Gói A",BigDecimal.ONE,money("200000"),money("200000"),null,C,Map.of()))));
        },e-> {
            equal(((ExtensionResult)e.actual).endsAt(),time("14:00:00"),"Combo end");
            equal(((ChargeLine)e.inv(e.repo,"insertCharge").get(0).getArgument(2)).kind(),"COMBO","Combo kind");
            e.count(e.repo,"event",1);
        });
        for(int i=0;i<11;i++) {
            int n=i;
            String field=i>=2&&i<=4?"hours":i==5||i==6?"comboId":i>=7&&i<=9?"pricingMode":null;
            add(s(20),e-> {
                switch(n) {
                    case 0->e.cmd=new ExtendStayRequest("HOURLY",null,1);
                    case 1->e.cmd=new ExtendStayRequest("HOURLY",null,12);
                    case 2->e.cmd=new ExtendStayRequest("HOURLY",null,null);
                    case 3->e.cmd=new ExtendStayRequest("HOURLY",null,0);
                    case 4->e.cmd=new ExtendStayRequest("HOURLY",null,13);
                    case 5->e.cmd=new ExtendStayRequest("HOURLY",C,1);
                    case 6->e.cmd=new ExtendStayRequest("COMBO",null,null);
                    case 7->e.cmd=new ExtendStayRequest("UNKNOWN",null,null);
                    case 8->e.cmd=new ExtendStayRequest(null,null,null);
                    case 9->e.cmd=new ExtendStayRequest("   ",null,null);
                    case 10->e.cmd=new ExtendStayRequest("COMBO",C,null);
                }
                e.f.put("quote",new PriceQuote(n==10?"COMBO":"HOURLY",money(n==0?"120000":n==1?"1440000":"200000"),BigDecimal.ZERO,List.of(line("120000"))));
            },field==null?0:400,field,e-> {
                e.noWrites();
                if(field==null) {
                    var q=(ExtensionQuote)e.actual;
                    equal(q.endsAt(),time("12:00:00").plusSeconds(n==1?43200:n==10?7200:3600),"End quote");
                }
                else e.none(e.pricing,"quote");
            });
        }
        for(int delta:new int[] {
            -1,0,1
        })add(s(21),e->e.fixture("booking","plannedEnd",T.minusSeconds(3600).plusSeconds(delta)),delta>0?0:400,delta>0?null:"hours",e-> {
            if(delta>0)equal(((ExtensionQuote)e.actual).endsAt(),T.plusSeconds(1),"Now boundary");
            e.noWrites();
        });
        for(int n=0;n<5;n++) {
            int v=n;
            add(s(22),e-> {
                Instant start=LocalDateTime.of(2026,12,24,v==3?7:v==4?23:10,0).atZone(TZ).toInstant();
                e.fixture("booking","plannedEnd",start);
                e.cmd=new ExtendStayRequest("COMBO",C,null);
                e.fixture("combo","name",v==0||v==3?"Combo ngày":v==1||v==4?"Combo đêm":"Gói linh hoạt");
                e.fixture("combo","validFrom",LocalDateTime.of(2026,12,24,8,0).atZone(TZ).toInstant());
                e.fixture("combo","validTo",LocalDateTime.of(2026,12,24,18,0).atZone(TZ).toInstant());
                if(v>=3)org.mockito.Mockito.when(e.pricing.quote(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenThrow(new BookingFailure(409,"comboId","outside configuration"));
                else e.f.put("quote",new PriceQuote("COMBO",money("300000"),BigDecimal.ZERO,List.of(line("300000"))));
            },n>=3?409:0,n>=3?"comboId":null,e-> {
                e.count(e.pricing,"quote",1);
                e.noWrites();
                if(v<3)equal(((ExtensionQuote)e.actual).quote().expectedAmount(),money("300000"),"Name independent quote");
            });
        }
        bad(s(23),e->e.answers.put("hasRoomOverlap",true),409,"hours");
        add(s(74),e->e.answers.put("updateInHouseBookingEnd",0),409,"form",e->e.none(e.repo,"updateOccupiedAllocationEnd","insertCharge","event"));
        add(s(75),e->e.answers.put("updateOccupiedAllocationEnd",0),409,"form",e-> {
            e.count(e.repo,"updateInHouseBookingEnd",1);
            e.count(e.repo,"updateOccupiedAllocationEnd",1);
            e.none(e.repo,"insertCharge","event");
        });
    }

    private void meters() {
        for(int i=0;i<5;i++) {
            int n=i;
            valid(s(26),e-> {
                Instant end=time(n==4?"08:00:00":"10:00:00");
                e.fixture("booking","plannedEnd",end);
                e.fixture("allocation","endsAt",end);
                if(n==1)e.answers.put("nextRoomBookingStart",time("10:45:00"));
                if(n==2||n==3)e.answers.put("nextRoomBookingStart",time("10:00:00").plusSeconds(86400+(n==3?1:0)));
            },e-> {
                e.count(e.repo,"startMeter",1);
                var args=e.inv(e.repo,"startMeter").get(0).getArguments();
                equal(args[4],n==4?T:time("10:00:00"),"Billed from");
                equal(args[6],n==1?time("10:45:00"):(n==4?T:time("10:00:00")).plusSeconds(86400),"Reservation limit");
                equal(args[7],money("120000"),"Meter rate");
            });
        }
        for(int i=0;i<5;i++) {
            int n=i;
            add(s(27),e-> {
                e.fixture("booking","plannedEnd",time("10:00:00"));
                e.fixture("allocation","endsAt",time("10:00:00"));
                if(n<=1)e.f.put("meter",meter(n==0?"RUNNING":"STOPPED"));
                if(n==2)e.answers.put("nextRoomBookingStart",time("10:00:00"));
                if(n==3) {
                    e.fixture("booking","plannedEnd",time("08:00:00"));
                    e.fixture("allocation","endsAt",time("08:00:00"));
                    e.answers.put("nextRoomBookingStart",time("08:59:59"));
                }
                if(n==4) {
                    e.answers.put("nextRoomBookingStart",time("10:45:00"));
                    e.answers.put("hasRoomOverlap",true);
                }
            },409,"form",e-> {
                e.none(e.repo,"startMeter","event");
                e.none(e.pricing,"meteredRate");
            });
        }
        for(long elapsed:new long[] {
            0,1,60000,60001
        })valid(s(28),e->e.f.put("meter",change(meter("RUNNING"),"billedFrom",T.minusMillis(elapsed))),e-> {
            int minutes=elapsed==0?0:elapsed<=60000?1:2;
            e.count(e.repo,"stopMeter",1);
            var args=e.inv(e.repo,"stopMeter").get(0).getArguments();
            equal(args[2],minutes,"Meter minutes");
            equal(args[3],money(minutes==0?"0":minutes==1?"2000":"4000"),"Meter amount");
            e.count(e.repo,"insertCharge",minutes==0?0:1);
        });
        valid(s(29),e->e.f.put("meter",meter("STOPPED")),e-> {
            check(e.actual instanceof BookingDetail,"Stopped detail");
            e.none(e.repo,"insertCharge","stopMeter","event");
        });
        bad(s(29),e-> {
        },409,"form");
        bad(s(29),e->e.f.put("meter",change(meter("RUNNING"),"allocationId",id(88))),409,"form");
        add(s(76),e-> {
            e.fixture("booking","plannedEnd",time("10:00:00"));
            e.fixture("allocation","endsAt",time("10:00:00"));
            e.answers.put("nextRoomBookingStart",time("10:45:00"));
            e.answers.put("updateOccupiedAllocationEnd",0);
        },409,"form",e-> {
            e.count(e.pricing,"meteredRate",1);
            e.none(e.repo,"startMeter","event");
        });
    }
}
