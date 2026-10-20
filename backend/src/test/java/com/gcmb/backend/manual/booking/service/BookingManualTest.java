/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Covers Booking service scenarios using isolated dependencies.
 */

package com.gcmb.backend.manual.booking.service;

import com.gcmb.backend.booking.dto.BookingDtos.Availability;
import com.gcmb.backend.booking.dto.BookingDtos.AvailableRoom;
import com.gcmb.backend.booking.dto.BookingDtos.BookingDetail;
import com.gcmb.backend.booking.dto.BookingDtos.BookingEditOptions;
import com.gcmb.backend.booking.dto.BookingDtos.BookingOptions;
import com.gcmb.backend.booking.dto.BookingDtos.BookingPage;
import com.gcmb.backend.booking.dto.BookingDtos.BookingSummary;
import com.gcmb.backend.booking.dto.BookingDtos.CancelBookingRequest;
import com.gcmb.backend.booking.dto.BookingDtos.CancellationResult;
import com.gcmb.backend.booking.dto.BookingDtos.ChargeLine;
import com.gcmb.backend.booking.dto.BookingDtos.CreateBookingRequest;
import com.gcmb.backend.booking.dto.BookingDtos.NoShowRequest;
import com.gcmb.backend.booking.dto.BookingDtos.NoShowResult;
import com.gcmb.backend.booking.dto.BookingDtos.PriceQuote;
import com.gcmb.backend.booking.dto.BookingDtos.RoomSummary;
import com.gcmb.backend.booking.dto.BookingDtos.UpdateBookingRequest;
import com.gcmb.backend.booking.repository.BookingRepository.ChargeRow;
import com.gcmb.backend.booking.repository.BookingRepository.MeterRow;
import com.gcmb.backend.booking.repository.BookingRepository.RoomRow;
import com.gcmb.backend.booking.service.BookingPricingService;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.support.ManualScenarios;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.dao.DataIntegrityViolationException;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class BookingManualTest extends ManualScenarios<BookingFixture> {
    private BookingManualTest() {
        super("Booking","BookingManualTest");
    }

    public static List<ManualTestSuite> suites() {
        var tests=new BookingManualTest();
        tests.describeCases();
        tests.register();
        return tests.buildSuites();
    }

    private void describeCases() {
        define("TC-BKG-055", "Booking Management", "BookingService.availability",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. branchScope=B,timezone " +
            "Asia/Ho_Chi_Minh; roomType(RT) có; start=10:00,end=12:00; mode=HOURLY,comboId=null. Quote F: " +
            "amount240000,deposit0. availableRooms trả [R,capacity4,AVAILABLE]. Cố định now=T. Reset mock/fixture trước mỗi" +
            " V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Base start10:00/end12:00,RT; quote240000; availableRooms=[R,capacity4].",
            "V01: Availability có B,RT,10:00,12:00,list[R],quote240000; pricing.quote nhận đúng scope/time; không ghi.",
            1,
            () -> new BookingFixture(null),
            e -> e.service.availability(e.actor,(UUID)e.f.getOrDefault("typeId",RT),(Instant)e.f.getOrDefault("start",time("10:00:00")),(Instant)e.f.getOrDefault("end",time("12:00:00")),(String)e.f.getOrDefault("mode","HOURLY"),(UUID)e.f.get("comboId")));
        define("TC-BKG-056", "Booking Management", "BookingService.availability",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. branchScope=B,timezone " +
            "Asia/Ho_Chi_Minh; roomType(RT) có; start=10:00,end=12:00; mode=HOURLY,comboId=null. Quote F: " +
            "amount240000,deposit0. availableRooms trả [R,capacity4,AVAILABLE]. Cố định now=T. Reset mock/fixture trước mỗi" +
            " V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: roomType(RT)=null; period10:00–12:00.\n" +
            "V02: start=T,end=T+2h; roomType có.\n" +
            "V03: start=T−1s,end=T+2h.\n" +
            "V04: start10:00,end10:00.\n" +
            "V05: start10:00,end09:59.",
            "V01: BF(404,roomTypeId); không quote/availableRooms/ghi.\n" +
            "V02,V03: BF(400,plannedStart); không quote/availableRooms/ghi.\n" +
            "V04,V05: BF(400,plannedEnd); không quote/availableRooms/ghi.",
            5,
            () -> new BookingFixture(null),
            e -> e.service.availability(e.actor,(UUID)e.f.getOrDefault("typeId",RT),(Instant)e.f.getOrDefault("start",time("10:00:00")),(Instant)e.f.getOrDefault("end",time("12:00:00")),(String)e.f.getOrDefault("mode","HOURLY"),(UUID)e.f.get("comboId")));
        define("TC-BKG-001", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Tạo booking đặt trước theo giờ\n" +
            "Mock branch, room, overlap=false, pricing.quote và các ID/read-back; request future HOURLY 2h, không thu cọc.",
            "Trả BookingDetail từ fixture; quan sát insertBooking, insertAllocation, createDepositAccount, insertCharge và " +
            "event; không gọi receiveDeposit.",
            1,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-002", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Tạo booking theo combo\n" +
            "Mock pricing.quote trả COMBO kèm charges/depositRequired; input không có tiền cọc thực thu.",
            "Quan sát charge và snapshot lấy từ quote; không gọi receiveDeposit chỉ vì depositRequired>0.",
            1,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-003", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " scope B; room R/RT/AVAILABLE/capacity4; overlap=false. Request: " +
            "roomId=R,start=10:00,end=12:00,channel=PHONE,guestCount=1,mode=HOURLY,comboId=null,contactName=An,contactPhone=0901234567,note=null,depositAmount=0,paymentMethod=null,paymentReference=null." +
            " Quote mock HOURLY/240000/1 charge; các write trả ID; read-back F_detail. now=T. Reset mock/fixture trước mỗi " +
            "V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: channel=WALK_IN; plannedStart=T,plannedEnd=T+1h; quote mock HOURLY120000.",
            "V01: insertBooking được gọi trước insertAllocation; trả F_detail; không " +
            "checkInBooking/occupyAllocation/shiftAndOccupyAllocation.",
            1,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-004", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " scope B; room R/RT/AVAILABLE/capacity4; overlap=false. Request: " +
            "roomId=R,start=10:00,end=12:00,channel=PHONE,guestCount=1,mode=HOURLY,comboId=null,contactName=An,contactPhone=0901234567,note=null,depositAmount=0,paymentMethod=null,paymentReference=null." +
            " Quote mock HOURLY/240000/1 charge; các write trả ID; read-back F_detail. now=T. Reset mock/fixture trước mỗi " +
            "V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: request=null.\n" +
            "V02: plannedStart=null.\n" +
            "V03: plannedEnd=null.\n" +
            "V04: plannedEnd=plannedStart=10:00.\n" +
            "V05: plannedStart10:00,plannedEnd09:59.",
            "V01: BF(400,form); không insertBooking/insertAllocation.\n" +
            "V02: BF(400,plannedStart); không insertBooking/insertAllocation.\n" +
            "V03,V04,V05: BF(400,plannedEnd); không insertBooking/insertAllocation.",
            5,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-005", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh. Kiểm soát Instant.now tại mốc T.",
            "Đặt trước ở quá khứ\n" +
            "PHONE/FACEBOOK/ZALO/OTHER; start trước hoặc bằng thời điểm xét",
            "400 plannedStart; không giữ phòng",
            2,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-006", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh. Kiểm soát Instant.now tại mốc T.",
            "Biên walk-in 30 giây\n" +
            "Đóng băng thời gian kiểm thử; start now-31s, now-30s, now",
            "Trước now-30s bị từ chối; tại ngưỡng và now được chấp nhận nếu còn điều kiện khác",
            4,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-007", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Kênh tiếp nhận\n" +
            "WALK_IN/FACEBOOK/ZALO/PHONE/OTHER có khoảng trắng/chữ thường; null/blank/UNKNOWN",
            "Trim và uppercase kênh hợp lệ; sai 400",
            9,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-008", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " scope B; room R/RT/AVAILABLE/capacity4; overlap=false. Request: " +
            "roomId=R,start=10:00,end=12:00,channel=PHONE,guestCount=1,mode=HOURLY,comboId=null,contactName=An,contactPhone=0901234567,note=null,depositAmount=0,paymentMethod=null,paymentReference=null." +
            " Quote mock HOURLY/240000/1 charge; các write trả ID; read-back F_detail. now=T. Reset mock/fixture trước mỗi " +
            "V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: plannedStart10:00; end=start+3599s.\n" +
            "V02: plannedStart10:00; end=start+3600s.\n" +
            "V03: plannedStart10:00; end=start+3601s.\n" +
            "V04: plannedStart10:00; end=start+86399s.\n" +
            "V05: plannedStart10:00; end=start+86400s.\n" +
            "V06: plannedStart10:00; end=start+86401s.",
            "V01,V03,V04,V06: BF(400,plannedEnd); không insertBooking/insertAllocation.\n" +
            "V02: Trả F_detail; insertBooking nhận duration3600s.\n" +
            "V05: Trả F_detail; insertBooking nhận duration86400s.",
            6,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-009", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Sức chứa phòng\n" +
            "guestCount null, 0, 1, capacity, capacity+1",
            "Null mặc định 1; nhận 1..capacity; ngoài khoảng 400",
            5,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-010", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " scope B; room R/RT/AVAILABLE/capacity4; overlap=false. Request: " +
            "roomId=R,start=10:00,end=12:00,channel=PHONE,guestCount=1,mode=HOURLY,comboId=null,contactName=An,contactPhone=0901234567,note=null,depositAmount=0,paymentMethod=null,paymentReference=null." +
            " Quote mock HOURLY/240000/1 charge; các write trả ID; read-back F_detail. now=T. contactPhone luôn0901234567. " +
            "Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: contactName=null\n" +
            "V02: contactName=\"\"\n" +
            "V03: contactName=\"   \"\n" +
            "V04: contactName=\"N\"×201\n" +
            "V05: contactName=\"  \"+\"N\"×200+\"  \"",
            "V01,V02,V03,V04: BF(400,contactName); không insertBooking.\n" +
            "V05: Trả F_detail; insertBooking contactName trim dài200.",
            5,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-011", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " scope B; room R/RT/AVAILABLE/capacity4; overlap=false. Request: " +
            "roomId=R,start=10:00,end=12:00,channel=PHONE,guestCount=1,mode=HOURLY,comboId=null,contactName=An,contactPhone=0901234567,note=null,depositAmount=0,paymentMethod=null,paymentReference=null." +
            " Quote mock HOURLY/240000/1 charge; các write trả ID; read-back F_detail. now=T. contactName luônAn. Reset " +
            "mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: contactPhone=null\n" +
            "V02: contactPhone=\"\"\n" +
            "V03: contactPhone=\"   \"\n" +
            "V04: contactPhone=\"0\"+\"1\"×5\n" +
            "V05: contactPhone=\"0\"+\"1\"×15\n" +
            "V06: contactPhone=\"1901234\"\n" +
            "V07: contactPhone=\"09012a4\"\n" +
            "V08: contactPhone=\"090 1234\"\n" +
            "V09: contactPhone=\"+0901234\"\n" +
            "V10: contactPhone=\"0\"+\"1\"×6\n" +
            "V11: contactPhone=\"  \"+\"0\"+\"1\"×14+\"  \"",
            "V01,V02,V03,V04,V05,V06,V07,V08,V09: BF(400,contactPhone); không insertBooking.\n" +
            "V10,V11: Trả F_detail; insertBooking phone trim đúng7/15 chữ số.",
            11,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-012",
            "Booking Management",
            "BookingService.create",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " scope B; room R/RT/AVAILABLE/capacity4; overlap=false. Request: " +
            "roomId=R,start=10:00,end=12:00,channel=PHONE,guestCount=1,mode=HOURLY,comboId=null,contactName=An,contactPhone=0901234567,note=null,depositAmount=0,paymentMethod=null,paymentReference=null." +
            " Quote mock HOURLY/240000/1 charge; các write trả ID; read-back F_detail. now=T. Reset mock/fixture trước mỗi " +
            "V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: note=null\n" +
            "V02: note=\"\"\n" +
            "V03: note=\"   \"\n" +
            "V04: note=\"  \"+\"N\"×2000+\"  \"\n" +
            "V05: note=\"N\"×2001\n" +
            "V06: paymentReference=null\n" +
            "V07: paymentReference=\"\"\n" +
            "V08: paymentReference=\"   \"\n" +
            "V09: paymentReference=\"R\"×120\n" +
            "V10: paymentReference=\"R\"×121",
            "V01,V02,V03: Trả F_detail; insertBooking note=null.\nV04: Trả F_detail; insertBooking note trim dài2000.\nV05: BF(400,note); không insertBooking.\nV06,V07,V08,V09: Trả F_detail; không receiveDeposit vì depositAmount=0.\nV10: Trả F_detail; depositAmount=0 nên bỏ qua paymentReference dài121; không receiveDeposit.",
            10,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-015", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Từ chối khi dependency báo phòng trùng lịch\n" +
            "Mock hasRoomOverlap=true sau khi trả room hợp lệ.",
            "409 roomId; không gọi insertBooking/insertAllocation. Không chứng minh các khoảng thời gian giao nhau thật.",
            1,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-019", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " scope B; room R/RT/AVAILABLE/capacity4; overlap=false. Request: " +
            "roomId=R,start=10:00,end=12:00,channel=PHONE,guestCount=1,mode=HOURLY,comboId=null,contactName=An,contactPhone=0901234567,note=null,depositAmount=0,paymentMethod=null,paymentReference=null." +
            " Quote mock HOURLY/240000/1 charge; các write trả ID; read-back F_detail. now=T. Reset mock/fixture trước mỗi " +
            "V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: insertAllocation ném DataIntegrityViolationException với rootCause.message=\"no_room_overlap\".\n" +
            "V02: insertAllocation ném DataIntegrityViolationException với rootCause.message=\"no_booking_parallel_rooms\".\n" +
            "V03: insertAllocation ném DataIntegrityViolationException với rootCause.message=\"unrelated_constraint\".\n" +
            "V04: Root cause message=null.",
            "V01,V02: BF(409,roomId); không insertCharge/event sau lỗi.\n" +
            "V03,V04: Rethrow đúng DataIntegrityViolationException instance; không insertCharge/event.",
            4,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-013", "Booking Management", "BookingService.options",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST." +
            " Chỉ gọi options(identity). Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: repository.branchScope(U)=null.",
            "V01: BF(403,form); không gọi roomTypes/combos; không có lời gọi ghi.",
            1,
            () -> new BookingFixture(null),
            e -> e.service.options(e.actor));
        define("TC-BKG-035", "Booking Management", "BookingService.detail",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Chi tiết và che dữ liệu khác cơ sở\n" +
            "Mock repository.booking(id,scope) có fixture hoặc null; các dependency detail trả fixture.",
            "Có booking: trả detail tổng hợp từ mock. Null: 404. Quan sát đúng scope được truyền; không chứng minh SQL che " +
            "branch.",
            2,
            () -> new BookingFixture(null),
            e -> e.service.detail(e.actor,K));
        define("TC-BKG-031", "Booking Management", "BookingService.list",
            "BookingService thật, BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B,timeZone " +
            "Asia/Ho_Chi_Minh. Base filters status=null,dateFrom/dateTo=null,search=null,hasDeposit=null,page0,size20; " +
            "countBookings=0,findBookings=[]. now=T. RequestedStatus=\" pending " +
            "\",dateFrom20/10/2026,dateTo21/10/2026,search=\" An \",hasDeposit=true. Reset mock/fixture trước mỗi V; chỉ thay " +
            "dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: countBookings=0; findBookings trả 0 fixture rows; size20,page0.\n" +
            "V02: countBookings=1; findBookings trả 1 fixture rows; size20,page0.\n" +
            "V03: countBookings=20; findBookings trả 20 fixture rows; size20,page0.\n" +
            "V04: countBookings=21; findBookings trả 20 fixture rows; size20,page0.",
            "V01: total=0,totalPages=0; trả đúng rows; count/find nhận " +
            "B,status=CONFIRMED,dateFrom20/10,dateTo21/10,timezone,search=\"An\",hasDeposit=true,page0,size20 tương ứng.\n" +
            "V02: total=1,totalPages=1; trả đúng rows; count/find nhận " +
            "B,status=CONFIRMED,dateFrom20/10,dateTo21/10,timezone,search=\"An\",hasDeposit=true,page0,size20 tương ứng.\n" +
            "V03: total=20,totalPages=1; trả đúng rows; count/find nhận " +
            "B,status=CONFIRMED,dateFrom20/10,dateTo21/10,timezone,search=\"An\",hasDeposit=true,page0,size20 tương ứng.\n" +
            "V04: total=21,totalPages=2; trả đúng rows; count/find nhận " +
            "B,status=CONFIRMED,dateFrom20/10,dateTo21/10,timezone,search=\"An\",hasDeposit=true,page0,size20 tương ứng.",
            4,
            () -> new BookingFixture(null),
            e -> e.service.list(e.actor,(String)e.f.get("statusFilter"),(LocalDate)e.f.get("from"),(LocalDate)e.f.get("to"),(String)e.f.get("search"),(Boolean)e.f.get("hasDeposit"),(int)e.f.getOrDefault("page",0),(int)e.f.getOrDefault("size",20)));
        define("TC-BKG-032", "Booking Management", "BookingService.list",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Alias và trạng thái sai\n" +
            "ALL/null/blank; PENDING; CHECKED_IN; lowercase; UNKNOWN",
            "ALL/null/blank -> không lọc; PENDING -> CONFIRMED; CHECKED_IN -> IN_HOUSE; sai 400",
            8,
            () -> new BookingFixture(null),
            e -> e.service.list(e.actor,(String)e.f.get("statusFilter"),(LocalDate)e.f.get("from"),(LocalDate)e.f.get("to"),(String)e.f.get("search"),(Boolean)e.f.get("hasDeposit"),(int)e.f.getOrDefault("page",0),(int)e.f.getOrDefault("size",20)));
        define("TC-BKG-033", "Booking Management", "BookingService.list",
            "BookingService thật, BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B,timeZone " +
            "Asia/Ho_Chi_Minh. Base filters status=null,dateFrom/dateTo=null,search=null,hasDeposit=null,page0,size20; " +
            "countBookings=0,findBookings=[]. now=T. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: dateFrom21/10,dateTo20/10.\n" +
            "V02: dateFrom=dateTo20/10.\n" +
            "V03: page=-1.\n" +
            "V04: page=0.\n" +
            "V05: size=0.\n" +
            "V06: size=1.\n" +
            "V07: size=100.\n" +
            "V08: size=101.\n" +
            "V09: search=\"  \"+\"S\"×100+\"  \".\n" +
            "V10: search=\"S\"×101.",
            "V01: BF(400,dateTo); không count/find.\n" +
            "V02: Trả page total0/pages0; chuyển đúng hai ngày.\n" +
            "V03: BF(400,page); không count/find.\n" +
            "V04: Trả page total0/pages0.\n" +
            "V05,V08: BF(400,size); không count/find.\n" +
            "V06: Trả page size1,total0/pages0.\n" +
            "V07: Trả page size100,total0/pages0.\n" +
            "V09: Trả page; query nhận search trim dài100.\n" +
            "V10: BF(400,search); không count/find.",
            10,
            () -> new BookingFixture(null),
            e -> e.service.list(e.actor,(String)e.f.get("statusFilter"),(LocalDate)e.f.get("from"),(LocalDate)e.f.get("to"),(String)e.f.get("search"),(Boolean)e.f.get("hasDeposit"),(int)e.f.getOrDefault("page",0),(int)e.f.getOrDefault("size",20)));
        define("TC-BKG-038", "Booking Management", "BookingService.editOptions",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; booking K " +
            "CONFIRMED,start10:00,end12:00,guestCount4; current AL RESERVED,R/RT; proposed start10:00,end12:00. Base HOURLY" +
            " charge240000,confirmedDurationMinutes120,confirmedAmount240000; availableRoomsForEdit=[R],combos=[]. now=T. " +
            "V01 dùng COMBO charge C/unitPrice200000,duration120,overtime120000; V02 dùng base HOURLY. Không tạo combo mới." +
            " Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Quote COMBO C; danh sách [R(4,RT),R2(3,RT),R3(4,RT2),R4(5,RT2)].\n" +
            "V02: Quote HOURLY snapshot; cùng danh sách như V01.",
            "V01: Giữ [R]; availableRoomsForEdit excludeBookingId=K; không ghi.\n" +
            "V02: Giữ [R,R3,R4]; availableRoomsForEdit excludeBookingId=K; không ghi.",
            2,
            () -> new BookingFixture(null).editOptions(),
            e -> e.service.editOptions(e.actor,K,(Instant)e.f.get("start"),(Instant)e.f.get("end"),(String)e.f.get("mode"),(UUID)e.f.get("comboId")));
        define("TC-BKG-041", "Booking Management", "BookingService.editOptions",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Giữ giá hourly đã chốt\n" +
            "Booking 2h=240000; rule hiện tại tăng; sửa thành 3h cùng mode",
            "Tính theo confirmed snapshot/base duration: 360000; không lấy giá rule mới",
            1,
            () -> new BookingFixture(null).editOptions(),
            e -> e.service.editOptions(e.actor,K,(Instant)e.f.get("start"),(Instant)e.f.get("end"),(String)e.f.get("mode"),(UUID)e.f.get("comboId")));
        define("TC-BKG-042", "Booking Management", "BookingService.editOptions",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Giữ giá combo và tính vượt giờ\n" +
            "Combo cũ 120m=200000, overtime 120000/h; sửa 150m; config sau đó tăng",
            "Giữ package 200000 + overtime 60000 = 260000 từ snapshot",
            1,
            () -> new BookingFixture(null).editOptions(),
            e -> e.service.editOptions(e.actor,K,(Instant)e.f.get("start"),(Instant)e.f.get("end"),(String)e.f.get("mode"),(UUID)e.f.get("comboId")));
        define("TC-BKG-043", "Booking Management", "BookingService.editOptions",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; booking K " +
            "CONFIRMED,start10:00,end12:00,guestCount4; current AL RESERVED,R/RT; proposed start10:00,end12:00. Base HOURLY" +
            " charge240000,confirmedDurationMinutes120,confirmedAmount240000; availableRoomsForEdit=[R],combos=[]. now=T. " +
            "Cả hai V input10:00–12:00. Kiểm pricing.quote args RT,B,timezone,start,end,mode,comboId đúng V; không " +
            "supersede/insertCharge/updateBooking. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Charge hiện tại COMBO C; request modeCOMBO,comboId=C2 khác C; repo.combo(C2) " +
            "RT/duration120/price300000/overtime120000; BP.quote mock C2=300000.\n" +
            "V02: Charge hiện tại COMBO C; request modeHOURLY,comboId=null; BP.quote mock HOURLY=240000.",
            "V01: editOptions.quote COMBO300000,C2/snapshot từ quote; không ghi.\n" +
            "V02: editOptions.quote HOURLY240000/snapshot dependency; không ghi.",
            2,
            () -> new BookingFixture(null).editOptions(),
            e -> e.service.editOptions(e.actor,K,(Instant)e.f.get("start"),(Instant)e.f.get("end"),(String)e.f.get("mode"),(UUID)e.f.get("comboId")));
        define("TC-BKG-044", "Booking Management", "BookingService.editOptions",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; booking K " +
            "CONFIRMED,start10:00,end12:00,guestCount4; current AL RESERVED,R/RT; proposed start10:00,end12:00. Base HOURLY" +
            " charge240000,confirmedDurationMinutes120,confirmedAmount240000; availableRoomsForEdit=[R],combos=[]. now=T. " +
            "Base proposed start10:00; COMBO C id giữ nguyên,không gọi pricing.quote mới; availableRoomsForEdit/combos " +
            "fixture hợp lệ. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Giữ COMBO C snapshot duration120,overtime120000,unitPrice200000; end=start+59s.\n" +
            "V02: Giữ COMBO C snapshot duration120,overtime120000,unitPrice200000; end=start+60s.\n" +
            "V03: Giữ COMBO C snapshot duration120,overtime120000,unitPrice200000; end=start+61s.\n" +
            "V04: Giữ COMBO C snapshot duration120,overtime120000,unitPrice200000; end=start+86400s.\n" +
            "V05: Giữ COMBO C snapshot duration120,overtime120000,unitPrice200000; end=start+86401s.\n" +
            "V06: modeHOURLY; activeChargesForAllocation=[].\n" +
            "V07: Giữ COMBO C; snapshot thiếu durationMinutes,unitPrice200000,overtime120000.\n" +
            "V08: Giữ COMBO C; snapshot duration120 nhưng thiếu overtimeHourlyPrice.",
            "V01,V03,V05: BF(400,plannedEnd).\n" +
            "V02: Quote COMBO200000; không overtime.\n" +
            "V04: Quote COMBO2840000 gồm base200000+overtime2640000.\n" +
            "V06: BF(409,form).\n" +
            "V07,V08: BF(409,comboId).",
            8,
            () -> new BookingFixture(null).editOptions(),
            e -> e.service.editOptions(e.actor,K,(Instant)e.f.get("start"),(Instant)e.f.get("end"),(String)e.f.get("mode"),(UUID)e.f.get("comboId")));
        define("TC-BKG-036", "Booking Management", "BookingService.update",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Sửa liên hệ giữ nguyên lịch/giá\n" +
            "Mock CONFIRMED tương lai và giá snapshot; request chỉ sửa liên hệ.",
            "Gọi updateBooking với liên hệ chuẩn hóa; không release/insert allocation, không supersede/insert charge khi " +
            "không đổi giá/lịch.",
            1,
            () -> new BookingFixture(new UpdateBookingRequest(null,null,null,null,null,"An mới",null,null)).confirmed(),
            e -> e.service.update(e.actor,K,(UpdateBookingRequest)e.cmd));
        define("TC-BKG-037", "Booking Management", "BookingService.update",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Sửa thời gian hoặc phòng\n" +
            "Mock CONFIRMED, target hợp lệ, hasRoomOverlap=false; request đổi room/time.",
            "Quan sát release allocation cũ, insert allocation mới, thay charge và event đúng thứ tự; trả fixture detail. " +
            "Không xác minh transaction.",
            1,
            () -> new BookingFixture(new UpdateBookingRequest(null,null,null,null,null,"An mới",null,null)).confirmed(),
            e -> e.service.update(e.actor,K,(UpdateBookingRequest)e.cmd));
        define("TC-BKG-039", "Booking Management", "BookingService.update",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; " +
            "booking K CONFIRMED,start10:00,end12:00; current AL RESERVED,R/RT. Request chỉ contactName=An mới, các trường " +
            "tùy chọn null. Snapshot hourly240000/120m; activeChargeTotal240000; read-back F_detail. now=T. Reset " +
            "mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: K.status=IN_HOUSE; plannedStart10:00; input base.\n" +
            "V02: K.status=COMPLETED; plannedStart10:00; input base.\n" +
            "V03: K.status=CANCELLED; plannedStart10:00; input base.\n" +
            "V04: K.status=NO_SHOW; plannedStart10:00; input base.\n" +
            "V05: K CONFIRMED,plannedStart=T; input base.\n" +
            "V06: K CONFIRMED,plannedStart=T−1s; input base.\n" +
            "V07: K CONFIRMED,start10:00,end12:00; input plannedStart=T−1s,plannedEnd=T+2h.",
            "V01,V02,V03,V04,V05,V06: BF(409,form); không updateBooking/release/insert allocation.\n" +
            "V07: BF(400,plannedStart); không updateBooking/release/insert allocation.",
            7,
            () -> new BookingFixture(new UpdateBookingRequest(null,null,null,null,null,"An mới",null,null)).confirmed(),
            e -> e.service.update(e.actor,K,(UpdateBookingRequest)e.cmd));
        define("TC-BKG-040", "Booking Management", "BookingService.update",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Phòng sửa sai điều kiện\n" +
            "Mock targetRoom null hoặc capacity nhỏ hoặc khác hạng với COMBO.",
            "409 khi target null; 400 cho capacity/khác hạng; không gọi updateBooking/insert allocation mới. Không kết luận" +
            " rollback.",
            3,
            () -> new BookingFixture(new UpdateBookingRequest(null,null,null,null,null,"An mới",null,null)).confirmed(),
            e -> e.service.update(e.actor,K,(UpdateBookingRequest)e.cmd));
        define("TC-BKG-046", "Booking Management", "BookingService.cancel",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Hủy không có cọc\n" +
            "Mock booking CONFIRMED trước giờ, allocation RESERVED, depositBalance=0, read-back fixture.",
            "Kết quả NO_DEPOSIT; gọi releaseAllocation/cancelBooking/event; không gọi " +
            "createDepositRefundRequest/forfeitDeposit.",
            1,
            () -> new BookingFixture(new CancelBookingRequest("Khách hủy","An","012345","Bank A")).cancellation(),
            e -> e.service.cancel(e.actor,K,(CancelBookingRequest)e.cmd));
        define("TC-BKG-047", "Booking Management", "BookingService.cancel",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh. Kiểm soát Instant.now tại mốc T.",
            "Hủy trước mốc hoàn 40 phút\n" +
            "Kiểm soát Instant.now tại T; mock plannedStart=T+40m1s hoặc T+40m, balance=200000 và bank info hợp lệ.",
            "REFUND_ELIGIBLE; gọi tạo request=200000, release/cancel/event; không gọi forfeitDeposit. Không kiểm ghi ledger" +
            " thực tế.",
            2,
            () -> new BookingFixture(new CancelBookingRequest("Khách hủy","An","012345","Bank A")).cancellation(),
            e -> e.service.cancel(e.actor,K,(CancelBookingRequest)e.cmd));
        define("TC-BKG-048", "Booking Management", "BookingService.cancel",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh. Kiểm soát Instant.now tại mốc T.",
            "Hủy dưới mốc 40 phút\n" +
            "Kiểm soát Instant.now tại T; mock plannedStart=T+39m59s, balance=200000.",
            "NON_REFUNDABLE; gọi forfeitDeposit=200000; không tạo refund request; gọi release/cancel.",
            1,
            () -> new BookingFixture(new CancelBookingRequest("Khách hủy","An","012345","Bank A")).cancellation(),
            e -> e.service.cancel(e.actor,K,(CancelBookingRequest)e.cmd));
        define("TC-BKG-049", "Booking Management", "BookingService.cancel",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "CONFIRMED,plannedStart=T+40m,cutoff40m; AL RESERVED. DepositId=D,balance200000; request reason=Khách " +
            "hủy,beneficiaryName=An,bankName=Ngân hàng A,bankAccount=012345; refund ID=Q; read-back F_detail. now=T. " +
            "Balance200000 và delta40m cho mọi V; chỉ field nêu ở V invalid; các field recipient khác giữ base. Reset " +
            "mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: reason=null\n" +
            "V02: reason=\"\"\n" +
            "V03: reason=\"   \"\n" +
            "V04: reason=\"A\"×1001\n" +
            "V05: reason=\"  \"+\"A\"×1000+\"  \"\n" +
            "V06: beneficiaryName=null\n" +
            "V07: beneficiaryName=\"\"\n" +
            "V08: beneficiaryName=\"   \"\n" +
            "V09: beneficiaryName=\"A\"×201\n" +
            "V10: beneficiaryName=\"  \"+\"A\"×200+\"  \"\n" +
            "V11: bankName=null\n" +
            "V12: bankName=\"\"\n" +
            "V13: bankName=\"   \"\n" +
            "V14: bankName=\"A\"×201\n" +
            "V15: bankName=\"  \"+\"A\"×200+\"  \"\n" +
            "V16: bankAccount=null\n" +
            "V17: bankAccount=\"\"\n" +
            "V18: bankAccount=\"   \"\n" +
            "V19: bankAccount=\"A\"×61\n" +
            "V20: bankAccount=\"  \"+\"A\"×60+\"  \"",
            "V01,V02,V03,V04: BF(400,reason); không refund/release/cancel/forfeit.\n" +
            "V05: REFUND_ELIGIBLE; createRefundRequest nhận reason trim dài1000,amount200000.\n" +
            "V06,V07,V08,V09: BF(400,beneficiaryName); không refund/release/cancel/forfeit.\n" +
            "V10: REFUND_ELIGIBLE; createRefundRequest nhận beneficiaryName trim dài200,amount200000.\n" +
            "V11,V12,V13,V14: BF(400,bankName); không refund/release/cancel/forfeit.\n" +
            "V15: REFUND_ELIGIBLE; createRefundRequest nhận bankName trim dài200,amount200000.\n" +
            "V16,V17,V18,V19: BF(400,bankAccount); không refund/release/cancel/forfeit.\n" +
            "V20: REFUND_ELIGIBLE; createRefundRequest nhận bankAccount trim dài60,amount200000.",
            20,
            () -> new BookingFixture(new CancelBookingRequest("Khách hủy","An","012345","Bank A")).cancellation(),
            e -> e.service.cancel(e.actor,K,(CancelBookingRequest)e.cmd));
        define("TC-BKG-050", "Booking Management", "BookingService.cancel",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "CONFIRMED,plannedStart=T+40m,cutoff40m; AL RESERVED. DepositId=D,balance200000; request reason=Khách " +
            "hủy,beneficiaryName=An,bankName=Ngân hàng A,bankAccount=012345; refund ID=Q; read-back F_detail. now=T. Reset " +
            "mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: K.status=IN_HOUSE; start=T+40m.\n" +
            "V02: K.status=COMPLETED; start=T+40m.\n" +
            "V03: K.status=CANCELLED; start=T+40m.\n" +
            "V04: K.status=NO_SHOW; start=T+40m.\n" +
            "V05: K CONFIRMED,plannedStart=T.\n" +
            "V06: K CONFIRMED,plannedStart=T−1s.\n" +
            "V07: K CONFIRMED,future; allocation=null.\n" +
            "V08: K CONFIRMED,future; allocation.state=OCCUPIED.",
            "V01,V02,V03,V04,V05,V06,V07,V08: BF(409,form); không release/cancel/forfeit/refund.",
            8,
            () -> new BookingFixture(new CancelBookingRequest("Khách hủy","An","012345","Bank A")).cancellation(),
            e -> e.service.cancel(e.actor,K,(CancelBookingRequest)e.cmd));
        define("TC-BKG-051", "Booking Management", "BookingService.markNoShow",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "CONFIRMED,plannedStart=T; AL RESERVED; depositId=D,balance0; markNoShow=1; request=null; read-back F_detail. " +
            "now=T. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: plannedStart=T+1s; balance=0; request=null.\n" +
            "V02: plannedStart=T+1s; balance=200000; request=null.\n" +
            "V03: plannedStart=T; balance=0; request=null.\n" +
            "V04: plannedStart=T; balance=200000; request=null.\n" +
            "V05: plannedStart=T−1s; balance=0; request=null.\n" +
            "V06: plannedStart=T−1s; balance=200000; request=null.",
            "V01,V02: BF(409,form); không markNoShow/release/forfeit.\n" +
            "V03: NO_DEPOSIT; markNoShow/release/event; reason=\"Khách không đến nhận phòng\"; không refund/forfeit.\n" +
            "V04,V06: NON_REFUNDABLE; forfeit200000/markNoShow/release/event; không refund.\n" +
            "V05: NO_DEPOSIT; markNoShow/release/event; không refund/forfeit.",
            6,
            () -> new BookingFixture(null).noShow(),
            e -> e.service.markNoShow(e.actor,K,(NoShowRequest)e.cmd));
        define("TC-BKG-063", "Booking Management", "BookingService.create",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " scope B; room R/RT/AVAILABLE/capacity4; overlap=false. Request: " +
            "roomId=R,start=10:00,end=12:00,channel=PHONE,guestCount=1,mode=HOURLY,comboId=null,contactName=An,contactPhone=0901234567,note=null,depositAmount=0,paymentMethod=null,paymentReference=null." +
            " Quote mock HOURLY/240000/1 charge; các write trả ID; read-back F_detail. now=T. Reset mock trước mỗi V; chỉ " +
            "thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO " +
            "Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Future start10:00,end10:30; mode=null.\n" +
            "V02: Cùng interval30m; mode=HOURLY.\n" +
            "V03: Cùng interval30m; mode=\"   \"; pricing.quote mock HOURLY60000.",
            "V01,V02: Expected BF(400,plannedEnd); không insertBooking.\n" +
            "V03: Expected BF(400,plannedEnd) như HOURLY mặc định; không insertBooking.",
            3,
            () -> new BookingFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-BKG-065", "Booking Management", "BookingService.options",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST." +
            " Chỉ gọi options(identity). Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Scope B; roomTypes=[RT{codeQUEEN,nameQueen,modeCINEMA,capacity4}]; " +
            "combos(RT)=[C{codeCOMBO_A,version1,nameGói " +
            "A,duration120,price200000,deposit50000,validFrom01/10,validTo=null}].\n" +
            "V02: Scope B; roomTypes=[].",
            "V01: BookingOptions branchB/timezone; roomTypes[RT] đúng code/name/mode/capacity; combo C đúng " +
            "version/duration/price/deposit/validity; không ghi.\n" +
            "V02: BookingOptions roomTypes=[]; không gọi combos/ghi.",
            2,
            () -> new BookingFixture(null),
            e -> e.service.options(e.actor));
        define("TC-BKG-064", "Booking Management", "BookingPricingService.quote",
            "BookingPricingService thật, BookingRepository mock. branchId=B,roomTypeId=RT,timezone Asia/Ho_Chi_Minh; " +
            "start=10:00,end=12:00,mode=HOURLY,comboId=null. Rule PR: " +
            "global,ALL,priority100,validFrom=2026-10-01,validTo=null,from0,to1440,rate120000,step60; list=[PR]. Combo C: " +
            "RT,duration120,price200000,deposit50000,validFrom=2026-10-01T00:00+07:00,validTo=null,version1. Reset mock " +
            "trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to " +
            "là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: start=null,end12:00.\n" +
            "V02: start10:00,end=null.\n" +
            "V03: start10:00,end10:00.\n" +
            "V04: start10:00,end09:59.",
            "V01,V02,V03,V04: BF(400,plannedEnd); không pricingRules/combo lookup.",
            4,
            () -> new BookingFixture(null),
            e -> new BookingPricingService(e.repo).quote(RT,B,TZ,(Instant)e.f.getOrDefault("start",time("10:00:00")),(Instant)e.f.getOrDefault("end",time("12:00:00")),(String)e.f.getOrDefault("mode","HOURLY"),(UUID)e.f.get("comboId")));
        define("TC-BKG-066", "Booking Management", "BookingService.rooms",
            "BookingService thật, BookingRepository/BookingPricingService mock,ObjectMapper thật; Identity U/RECEPTIONIST; " +
            "branchScopeB; repository.rooms(B) trả RoomRow fixtures hoặc[] theo V. Reset mock trước mỗi V; chỉ thay phần " +
            "nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển" +
            " từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: repository.rooms(B) trả " +
            "R{idR,codeP01,namePhòng01,capacity4,roomTypeIdRT,roomTypeNameQueen,operatingStatusAVAILABLE}.\n" +
            "V02: repository.rooms(B) trả[].",
            "V01: RoomSummary R có đúng các field fixture; repository.rooms nhận B; không ghi.\n" +
            "V02: Trả[]; không ghi.",
            2,
            () -> new BookingFixture(null),
            e -> e.service.rooms(e.actor));
        define("TC-BKG-057", "Booking Management", "BookingService.update",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; " +
            "booking K CONFIRMED,start10:00,end12:00; current AL RESERVED,R/RT. Request chỉ contactName=An mới, các trường " +
            "tùy chọn null. Snapshot hourly240000/120m; activeChargeTotal240000; read-back F_detail. now=T. Reset mock " +
            "trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to " +
            "là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: request=null.",
            "V01: BF(400,form); không branchScope/lockBookingId/updateBooking/releaseAllocation/event.",
            1,
            () -> new BookingFixture(new UpdateBookingRequest(null,null,null,null,null,"An mới",null,null)).confirmed(),
            e -> e.service.update(e.actor,K,(UpdateBookingRequest)e.cmd));
        define("TC-BKG-058", "Booking Management", "BookingService.update",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; " +
            "booking K CONFIRMED,start10:00,end12:00; current AL RESERVED,R/RT. Request chỉ contactName=An mới, các trường " +
            "tùy chọn null. Snapshot hourly240000/120m; activeChargeTotal240000; read-back F_detail. now=T. Reset mock " +
            "trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to " +
            "là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Booking CONFIRMED,future; currentAllocationForUpdate=null.",
            "V01: BF(409,roomId); không editQuote/lockAvailableRoom/releaseAllocation/updateBooking/event.",
            1,
            () -> new BookingFixture(new UpdateBookingRequest(null,null,null,null,null,"An mới",null,null)).confirmed(),
            e -> e.service.update(e.actor,K,(UpdateBookingRequest)e.cmd));
        define("TC-BKG-059", "Booking Management", "BookingService.update",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; " +
            "booking K CONFIRMED,start10:00,end12:00; current AL RESERVED,R/RT. Request chỉ contactName=An mới, các trường " +
            "tùy chọn null. Snapshot hourly240000/120m; activeChargeTotal240000; read-back F_detail. now=T. Reset mock " +
            "trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to " +
            "là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Request roomId=R2,start10:00,end12:00; target R2 đủ capacity,cùng RT; hasRoomOverlap=true.",
            "V01: Đã gọi releaseAllocation(AL,\"Thay đổi lịch đặt\") trước hasRoomOverlap(R2,10:00,12:00,K); BF(409,roomId); " +
            "không updateBooking/insertAllocation/supersede/insertCharge/event.",
            1,
            () -> new BookingFixture(new UpdateBookingRequest(null,null,null,null,null,"An mới",null,null)).confirmed(),
            e -> e.service.update(e.actor,K,(UpdateBookingRequest)e.cmd));
        define("TC-BKG-060", "Booking Management", "BookingService.cancel",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "CONFIRMED,plannedStart=T+40m,cutoff40m; AL RESERVED. DepositId=D,balance200000; request reason=Khách " +
            "hủy,beneficiaryName=An,bankName=Ngân hàng A,bankAccount=012345; refund ID=Q; read-back F_detail. now=T. Reset " +
            "mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot " +
            "from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: K.status=DRAFT,plannedStart=T+40m; balance0.",
            "V01: CancellationResult outcomeNO_DEPOSIT,depositAmount0; releaseAllocation/cancelBooking/event đúng K; không " +
            "createRefundRequest/forfeitDeposit; trả F_detail.",
            1,
            () -> new BookingFixture(new CancelBookingRequest("Khách hủy","An","012345","Bank A")).cancellation(),
            e -> e.service.cancel(e.actor,K,(CancelBookingRequest)e.cmd));
        define("TC-BKG-061", "Booking Management", "BookingService.cancel",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "CONFIRMED,plannedStart=T+40m,cutoff40m; AL RESERVED. DepositId=D,balance200000; request reason=Khách " +
            "hủy,beneficiaryName=An,bankName=Ngân hàng A,bankAccount=012345; refund ID=Q; read-back F_detail. now=T. Reset " +
            "mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot " +
            "from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: K CONFIRMED,start=T+40m,balance200000; lockDeposit=null; thông tin ngân hàng đủ.",
            "V01: BF(409,form); không createDepositRefundRequest/forfeitDeposit/releaseAllocation/cancelBooking/event.",
            1,
            () -> new BookingFixture(new CancelBookingRequest("Khách hủy","An","012345","Bank A")).cancellation(),
            e -> e.service.cancel(e.actor,K,(CancelBookingRequest)e.cmd));
        define("TC-BKG-062", "Booking Management", "BookingService.markNoShow",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Scope B; lock K có; K " +
            "CONFIRMED,plannedStart=T; AL RESERVED; depositId=D,balance0; markNoShow=1; request=null; read-back F_detail. " +
            "now=T. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp;" +
            " snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: plannedStart=T,balance0; markNoShow(K)=0.\n" +
            "V02: plannedStart=T,balance200000; depositIdD có; markNoShow(K)=0.",
            "V01: releaseAllocation đã gọi; BF(409,form); không BOOKING_NO_SHOW event/read-back thành công.\n" +
            "V02: forfeitDeposit200000 và releaseAllocation đã gọi; BF(409,form); không BOOKING_NO_SHOW event/read-back " +
            "thành công.",
            2,
            () -> new BookingFixture(null).noShow(),
            e -> e.service.markNoShow(e.actor,K,(NoShowRequest)e.cmd));
    }
    static String b(int n) {
        return String.format("TC-BKG-%03d",n);
    }
    static String s(int n) {
        return String.format("TC-STY-%03d",n);
    }
    static String d(int n) {
        return String.format("TC-DEP-%03d",n);
    }
    static void changed(BookingFixture e,String method) {
        e.count(e.repo,method,1);
        check(e.actual!=null,"Missing return");
    }
    static void created(BookingFixture e) {
        changed(e,"insertBooking");
        e.count(e.repo,"insertAllocation",1);
        e.count(e.repo,"createDepositAccount",1);
        e.count(e.repo,"event",1);
        check(e.actual instanceof BookingDetail,"Expected detail");
        e.none(e.repo,"checkInBooking","occupyAllocation","shiftAndOccupyAllocation");
    }

    private void commandInvalid(String id,String field,Object... values) {
        for(Object v:values)bad(id,e-> {
            if(field.equals("$command"))e.cmd=v;
            else e.command(field,v);
        },400,field.equals("$command")?"form":field);
    }
    static MeterRow meter(String status) {
        return new MeterRow(Q,AL,PR,T,T,time("10:00:00"),T.plusSeconds(86400),money("120000"),status,status.equals("STOPPED")?T:null,status.equals("STOPPED")?0:null,BigDecimal.ZERO);
    }

    private void register() {
        create();
        read();
        edit();
        cancel();
    }

    private void create() {
        valid(b(1),e-> {
        },BookingManualTest::created);
        valid(b(2),e-> {
            e.command("pricingMode","COMBO");
            e.command("comboId",C);
            e.f.put("quote",new PriceQuote("COMBO",money("200000"),money("50000"),List.of(new ChargeLine("COMBO","Gói A",BigDecimal.ONE,money("200000"),money("200000"),null,C,Map.of()))));
        },e-> {
            created(e);
            e.none(e.repo,"receiveDeposit");
        });
        valid(b(3),e-> {
            e.command("channel","WALK_IN");
            e.command("plannedStart",T);
            e.command("plannedEnd",T.plusSeconds(3600));
        },BookingManualTest::created);
        commandInvalid(b(4),"$command",(Object)null);
        commandInvalid(b(4),"plannedStart",(Object)null);
        commandInvalid(b(4),"plannedEnd",null,time("10:00:00"),time("09:59:00"));
        for(Instant start:List.of(T.minusSeconds(1),T))bad(b(5),e->e.command("plannedStart",start),400,"plannedStart");
        for(int sec:new int[] {
            -31,-30,-29,0
        })add(b(6),e-> {
            e.command("channel","WALK_IN");
            e.command("plannedStart",T.plusSeconds(sec));
            e.command("plannedEnd",T.plusSeconds(sec+3600));
        },sec==-31?400:0,sec==-31?"plannedStart":null,e-> {
            if(sec==-31)e.noWrites();
            else created(e);
        });
        for(String channel:Arrays.asList(null,"","   ","UNKNOWN"," walk_in ","FACEBOOK","ZALO","PHONE","OTHER"))add(b(7),e->e.command("channel",channel),channel==null||channel.isBlank()||channel.equals("UNKNOWN")?400:0,channel==null||channel.isBlank()||channel.equals("UNKNOWN")?"channel":null,e-> {
            if(e.failure==null) {
                created(e);
                equal(e.inv(e.repo,"insertBooking").get(0).getArgument(5),channel.trim().toUpperCase(Locale.ROOT),"Normalized channel");
            }
            else e.noWrites();
        });
        for(long seconds:new long[] {
            3599,3600,3601,86399,86400,86401
        })add(b(8),e->e.command("plannedEnd",time("10:00:00").plusSeconds(seconds)),seconds==3600||seconds==86400?0:400,seconds==3600||seconds==86400?null:"plannedEnd",e-> {
            if(e.failure==null)created(e);
            else e.noWrites();
        });
        for(Integer count:Arrays.asList(null,0,1,4,5))add(b(9),e->e.command("guestCount",count),count!=null&&(count<1||count>4)?400:0,count!=null&&(count<1||count>4)?"guestCount":null,e-> {
            if(e.failure==null) {
                created(e);
                equal(e.inv(e.repo,"insertBooking").get(0).getArgument(4),count==null?1:count,"Guest count");
            }
            else e.noWrites();
        });
        commandInvalid(b(10),"contactName",null,"","   ","N".repeat(201));
        valid(b(10),e->e.command("contactName","  "+"N".repeat(200)+"  "),e-> {
            created(e);
            equal(e.inv(e.repo,"insertBooking").get(0).getArgument(2),"N".repeat(200),"Trim name");
        });
        commandInvalid(b(11),"contactPhone",null,"","   ","0"+"1".repeat(5),"0"+"1".repeat(15),"1901234","09012a4","090 1234","+0901234");
        for(String phone:List.of("0"+"1".repeat(6),"  0"+"1".repeat(14)+"  "))valid(b(11),e->e.command("contactPhone",phone),e-> {
            created(e);
            equal(e.inv(e.repo,"insertBooking").get(0).getArgument(3),phone.trim(),"Trim phone");
        });
        for(String note:Arrays.asList(null,"","   ","  "+"N".repeat(2000)+"  "))valid(b(12),e->e.command("note",note),e-> {
            created(e);
            equal(e.inv(e.repo,"insertBooking").get(0).getArgument(10),note==null||note.isBlank()?null:note.trim(),"Normalized note");
        });
        commandInvalid(b(12),"note","N".repeat(2001));
        for(String ref:Arrays.asList(null,"","   ","R".repeat(120)))valid(b(12),e->e.command("paymentReference",ref),e-> {
            created(e);
            e.none(e.repo,"receiveDeposit");
        });
        valid(b(12),e->e.command("paymentReference","R".repeat(121)),e->{created(e);e.none(e.repo,"receiveDeposit");});
        bad(b(15),e->e.answers.put("hasRoomOverlap",true),409,"roomId");
        for(String msg:Arrays.asList("no_room_overlap","no_booking_parallel_rooms","unrelated_constraint",null)) {
            boolean translated=msg!=null&&msg.startsWith("no_");
            add(b(19),e-> {
                var failure=new DataIntegrityViolationException("allocation",new RuntimeException(msg));
                e.answers.put("insertAllocation",failure);
            },translated?409:-3,translated?"roomId":null,e-> {
                e.none(e.repo,"insertCharge","event");
                if(!translated)equal(e.failure,e.answers.get("insertAllocation"),"Original constraint exception");
            });
        }
        bad(b(13),e->e.f.put("branch",null),403,"form");
        valid(b(55),e-> {
        },e-> {
            var result=(Availability)e.actual;
            equal(result.branchId(),B,"Branch");
            equal(result.roomTypeId(),RT,"Type");
            equal(result.quote().expectedAmount(),money("240000"),"Availability quote");
            equal(result.rooms().get(0).id(),R,"Available room");
            e.noWrites();
        });
        bad(b(56),e->e.f.put("roomType",null),404,"roomTypeId");
        for(Instant start:List.of(T,T.minusSeconds(1)))bad(b(56),e->e.f.put("start",start),400,"plannedStart");
        for(Instant end:List.of(time("10:00:00"),time("09:59:00")))bad(b(56),e->e.f.put("end",end),400,"plannedEnd");
        for(String mode:Arrays.asList(null,"HOURLY","   "))bad(b(63),e-> {
            e.command("pricingMode",mode);
            e.command("plannedEnd",time("10:30:00"));
            e.f.put("quote",new PriceQuote("HOURLY",money("60000"),BigDecimal.ZERO,List.of(line("60000"))));
        },400,"plannedEnd");
        for(Consumer<BookingFixture> change:List.<Consumer<BookingFixture>>of(e->e.f.put("start",null),e->e.f.put("end",null),e->e.f.put("end",time("10:00:00")),e->e.f.put("end",time("09:59:59"))))bad(b(64),change,400,change==null?null:null);
        valid(b(65),e-> {
        },e-> {
            var o=(BookingOptions)e.actual;
            equal(o.branchId(),B,"Options branch");
            equal(o.roomTypes().get(0).id(),RT,"Options type");
            equal(o.roomTypes().get(0).combos().get(0).id(),C,"Options combo");
            e.noWrites();
        });
        valid(b(65),e->e.answers.put("roomTypes",List.of()),e-> {
            equal(((BookingOptions)e.actual).roomTypes(),List.of(),"Empty options");
            e.noWrites();
        });
        for(boolean empty:new boolean[] {
            false,true
        })valid(b(66),e-> {
            if(empty)e.answers.put("rooms",List.of());
        },e-> {
            equal(((List<?>)e.actual).size(),empty?0:1,"Room DTO size");
            if(!empty)equal(((RoomSummary)((List<?>)e.actual).get(0)).id(),R,"Room ID");
            equal(e.inv(e.repo,"rooms").get(0).getArgument(0),B,"Rooms branch");
            e.noWrites();
        });
    }

    private void read() {
        valid(b(35),e->e.answers.put("allocations",List.of(e.f.get("allocation"))),e-> {
            equal(((BookingDetail)e.actual).booking().id(),K,"Detail booking");
            equal(((BookingDetail)e.actual).allocations().get(0).id(),AL,"Allocation DTO");
            e.noWrites();
        });
        bad(b(35),e->e.f.put("booking",null),404,null);
        for(long count:new long[] {
            0,1,20,21
        })valid(b(31),e-> {
            e.f.put("statusFilter","CONFIRMED");
            e.f.put("from",LocalDate.of(2026,10,20));
            e.f.put("to",LocalDate.of(2026,10,21));
            e.f.put("search","  An  ");
            e.f.put("hasDeposit",true);
            e.answers.put("countBookings",count);
            e.answers.put("findBookings",Collections.nCopies((int)Math.min(20,count),summary()));
        },e-> {
            var p=(BookingPage)e.actual;
            equal(p.totalElements(),count,"Total");
            equal(p.totalPages(),count==0?0:count<=20?1:2,"Pages");
            equal(p.items().size(),(int)Math.min(20,count),"Items");
            var args=Arrays.asList(e.inv(e.repo,"countBookings").get(0).getArguments());
            check(args.containsAll(List.of(B,"CONFIRMED","An",true,TZ)),"Normalized query args");
            e.noWrites();
        });
        for(String status:Arrays.asList(null,"","   ","ALL","PENDING","CHECKED_IN"," confirmed ","UNKNOWN"))add(b(32),e->e.f.put("statusFilter",status),"UNKNOWN".equals(status)?400:0,"UNKNOWN".equals(status)?"status":null,e-> {
            if(e.failure==null) {
                Object expected=status==null||status.isBlank()||status.equals("ALL")?null:status.equals("PENDING")?"CONFIRMED":status.equals("CHECKED_IN")?"IN_HOUSE":"CONFIRMED";
                equal(e.inv(e.repo,"countBookings").get(0).getArgument(1),expected,"Status alias");
            }
            e.noWrites();
        });
        filters(this,b(33),false);
    }
    static BookingSummary summary() {
        return new BookingSummary(K,"BK001","An","0901234567","PHONE",time("10:00:00"),time("12:00:00"),null,"CONFIRMED",R,"P01","Room A","Queen",money("240000"),BigDecimal.ZERO,BigDecimal.ZERO,4,"AVAILABLE");
    }
    static <F extends BookingFixture> void filters(ManualScenarios<F> tests,String id,boolean deposit) {
        for(int n=0;n<(deposit?15:10);n++) {
            int i=n;
            String error=switch(i) {
                case 0->"dateTo";
                case 2->"page";
                case 4,7->"size";
                case 9->"search";
                case 10->"status";
                case 11->"method";
                default->null;
            };
            tests.add(id,e-> {
                switch(i) {
                    case 0-> {
                        e.f.put("from",LocalDate.of(2026,10,21));
                        e.f.put("to",LocalDate.of(2026,10,20));
                    }
                    case 1-> {
                        e.f.put("from",LocalDate.of(2026,10,20));
                        e.f.put("to",LocalDate.of(2026,10,20));
                    }
                    case 2->e.f.put("page",-1);
                    case 3->e.f.put("page",0);
                    case 4->e.f.put("size",0);
                    case 5->e.f.put("size",1);
                    case 6->e.f.put("size",100);
                    case 7->e.f.put("size",101);
                    case 8->e.f.put("search","  "+"S".repeat(100)+"  ");
                    case 9->e.f.put("search","S".repeat(101));
                    case 10->e.f.put("statusFilter","UNKNOWN");
                    case 11->e.f.put("methodFilter","UNKNOWN");
                    case 12-> {
                        e.f.put("statusFilter",null);
                        e.f.put("methodFilter",null);
                    }
                    case 13-> {
                        e.f.put("statusFilter","   ");
                        e.f.put("methodFilter","   ");
                    }
                    case 14-> {
                        e.f.put("statusFilter"," held ");
                        e.f.put("methodFilter"," manual_transfer ");
                    }
                }
            },error==null?0:400,error,e-> {
                e.noWrites();
                if(error==null) {
                    equal(field(e.actual,deposit?"total":"totalElements"),0L,"Empty total");
                    if(i==8) {
                        Object mock=deposit?((DepositFixture)e).deposits:e.repo;
                        String method=deposit?"count":"countBookings";
                        check(Arrays.asList(e.inv(mock,method).get(0).getArguments()).contains("S".repeat(100)),"Trimmed search");
                    }
                }
                else {
                    if(deposit)e.none(((DepositFixture)e).deposits,"count","list","summary");
                    else e.none(e.repo,"countBookings","findBookings");
                }
            });
        }
    }

    private void edit() {
        valid(b(36),e-> {
        },e-> {
            changed(e,"updateBooking");
            e.none(e.repo,"releaseAllocation","insertAllocation","insertCharge","supersedeCharge");
            equal(e.inv(e.repo,"updateBooking").get(0).getArgument(1),"An mới","Updated name");
        });
        valid(b(37),e->e.command("roomId",R2),e-> {
            changed(e,"updateBooking");
            e.count(e.repo,"releaseAllocation",1);
            e.count(e.repo,"insertAllocation",1);
            e.count(e.repo,"insertCharge",1);
        });
        for(String status:List.of("IN_HOUSE","COMPLETED","CANCELLED","NO_SHOW"))bad(b(39),e->e.fixture("booking","status",status),409,"form");
        for(Instant start:List.of(T,T.minusSeconds(1)))bad(b(39),e->e.fixture("booking","plannedStart",start),409,"form");
        bad(b(39),e-> {
            e.command("plannedStart",T.minusSeconds(1));
            e.command("plannedEnd",T.plusSeconds(7200));
        },400,"plannedStart");
        bad(b(40),e-> {
            e.command("roomId",R2);
            e.f.put("target",null);
        },409,"roomId");
        bad(b(40),e-> {
            e.command("roomId",R2);
            e.fixture("target","capacity",3);
        },400,"roomId");
        bad(b(40),e-> {
            e.command("roomId",R2);
            e.command("pricingMode","COMBO");
            e.command("comboId",C);
            e.f.put("charges",List.of(comboCharge(true,true)));
        },400,"roomId");
        bad(b(57),e->e.cmd=null,400,"form");
        bad(b(58),e->e.f.put("allocation",null),409,"roomId");
        add(b(59),e-> {
            e.command("roomId",R2);
            e.answers.put("hasRoomOverlap",true);
        },409,"roomId",e-> {
            e.count(e.repo,"releaseAllocation",1);
            e.none(e.repo,"updateBooking","insertAllocation","event");
        });
        editQuotes();
    }
    static ChargeRow comboCharge(boolean duration,boolean overtime) {
        var snap=new LinkedHashMap<String,Object>();
        snap.put("pricingMode","COMBO");
        if(duration)snap.put("durationMinutes",120);
        if(overtime)snap.put("overtimeHourlyPrice",120000);
        snap.put("name","Gói A");
        return new ChargeRow(PR,AL,"COMBO","Gói A",BigDecimal.ONE,money("200000"),money("200000"),"ACTIVE",null,C,JSON.writeValueAsString(snap),T);
    }
    static void comboBase(BookingFixture e) {
        e.f.put("charges",List.of(comboCharge(true,true)));
        e.f.put("mode","COMBO");
        e.f.put("comboId",C);
    }

    private void editQuotes() {
        List<RoomRow> all=List.of(new RoomRow(R,"P01","R",4,"AVAILABLE",RT,"Queen"),new RoomRow(R2,"P02","R2",3,"AVAILABLE",RT,"Queen"),new RoomRow(id(33),"P03","R3",4,"AVAILABLE",RT2,"King"),new RoomRow(id(34),"P04","R4",5,"AVAILABLE",RT2,"King"));
        for(boolean combo:new boolean[] {
            true,false
        })valid(b(38),e-> {
            if(combo)comboBase(e);
            e.answers.put("availableRoomsForEdit",all);
        },e-> {
            equal(((BookingEditOptions)e.actual).rooms().stream().map(AvailableRoom::id).toList(),combo?List.of(R):List.of(R,id(33),id(34)),"Capacity and type filter");
            equal(e.inv(e.repo,"availableRoomsForEdit").get(0).getArgument(3),K,"Exclude own booking");
            e.noWrites();
        });
        for(long minutes:new long[] {
            180
        })valid(b(41),e-> {
            e.f.put("end",time("10:00:00").plusSeconds(minutes*60));
        },e-> {
            equal(((BookingEditOptions)e.actual).quote().expectedAmount(),money(minutes==120?"240000":"360000"),"Confirmed hourly amount");
            e.none(e.pricing,"quote");
            e.noWrites();
        });
        for(long minutes:new long[] {
            150
        })valid(b(42),e-> {
            comboBase(e);
            e.f.put("end",time("10:00:00").plusSeconds(minutes*60));
        },e-> {
            equal(((BookingEditOptions)e.actual).quote().expectedAmount(),money("260000"),"Confirmed combo plus overtime");
            e.noWrites();
        });
        valid(b(43),e-> {
            comboBase(e);
            e.f.put("comboId",id(44));
            e.f.put("quote",new PriceQuote("COMBO",money("300000"),BigDecimal.ZERO,List.of(new ChargeLine("COMBO","Gói B",BigDecimal.ONE,money("300000"),money("300000"),null,id(44),Map.of()))));
        },e-> {
            equal(((BookingEditOptions)e.actual).quote().expectedAmount(),money("300000"),"New combo quote");
            e.count(e.pricing,"quote",1);
            e.noWrites();
        });
        valid(b(43),e-> {
            comboBase(e);
            e.f.put("mode","HOURLY");
            e.f.put("comboId",null);
        },e-> {
            equal(((BookingEditOptions)e.actual).quote().expectedAmount(),money("240000"),"Switched hourly quote");
            e.count(e.pricing,"quote",1);
            e.noWrites();
        });
        for(long seconds:new long[] {
            59,60,61,86400,86401
        })add(b(44),e-> {
            comboBase(e);
            e.f.put("end",time("10:00:00").plusSeconds(seconds));
        },seconds==60||seconds==86400?0:400,seconds==60||seconds==86400?null:"plannedEnd",e-> {
            e.noWrites();
            if(e.failure==null)equal(((BookingEditOptions)e.actual).quote().expectedAmount(),money(seconds==60?"200000":"2840000"),"Combo edit duration");
        });
        bad(b(44),e->e.f.put("charges",List.of()),409,"form");
        bad(b(44),e-> {
            comboBase(e);
            e.f.put("charges",List.of(comboCharge(false,true)));
        },409,"comboId");
        bad(b(44),e-> {
            comboBase(e);
            e.f.put("charges",List.of(comboCharge(true,false)));
        },409,"comboId");
    }

    private void cancel() {
        valid(b(46),e-> {
        },e-> {
            equal(((CancellationResult)e.actual).depositOutcome(),"NO_DEPOSIT","Outcome");
            e.count(e.repo,"cancelBooking",1);
            e.count(e.repo,"releaseAllocation",1);
            e.none(e.repo,"forfeitDeposit","createDepositRefundRequest");
        });
        for(int seconds:new int[] {
            2399,2400,2401
        }) {
            String id=seconds<2400?b(48):b(47);
            valid(id,e-> {
                e.fixture("booking","plannedStart",T.plusSeconds(seconds));
                e.f.put("balance",money("200000"));
            },e-> {
                equal(((CancellationResult)e.actual).depositOutcome(),seconds<2400?"NON_REFUNDABLE":"REFUND_ELIGIBLE","Cutoff outcome");
                e.count(e.repo,seconds<2400?"forfeitDeposit":"createDepositRefundRequest",1);
                e.count(e.repo,"cancelBooking",1);
            });
        }
        String[] fields= {
            "reason","beneficiaryName","bankName","bankAccount"
        };
        int[] max= {
            1000,200,200,60
        };
        for(int i=0;i<fields.length;i++) {
            String field=fields[i];
            int length=max[i];
            for(String value:Arrays.asList(null,"","   ","A".repeat(length+1),"  "+"A".repeat(length)+"  "))add(b(49),e-> {
                e.f.put("balance",money("200000"));
                e.command(field,value);
            },value!=null&&value.trim().length()==length?0:400,value!=null&&value.trim().length()==length?null:field,e-> {
                if(e.failure==null) {
                    equal(((CancellationResult)e.actual).depositOutcome(),"REFUND_ELIGIBLE","Valid max length");
                    check(Arrays.asList(e.inv(e.repo,"createDepositRefundRequest").get(0).getArguments()).contains("A".repeat(length)),"Trimmed refund field");
                }
                else e.noWrites();
            });
        }
        for(String status:List.of("IN_HOUSE","COMPLETED","CANCELLED","NO_SHOW"))bad(b(50),e->e.fixture("booking","status",status),409,"form");
        for(Instant start:List.of(T,T.minusSeconds(1)))bad(b(50),e->e.fixture("booking","plannedStart",start),409,"form");
        bad(b(50),e->e.f.put("allocation",null),409,"form");
        bad(b(50),e->e.fixture("allocation","state","OCCUPIED"),409,"form");
        for(int delta:new int[] {
            1,0,-1
        })for(boolean deposit:new boolean[] {
            false,true
        })add(b(51),e-> {
            e.fixture("booking","plannedStart",T.plusSeconds(delta));
            e.f.put("balance",money(deposit?"200000":"0"));
        },delta>0?409:0,delta>0?"form":null,e-> {
            if(delta>0)e.noWrites();
            else {
                equal(((NoShowResult)e.actual).depositOutcome(),deposit?"NON_REFUNDABLE":"NO_DEPOSIT","No-show outcome");
                e.count(e.repo,"forfeitDeposit",deposit?1:0);
                e.count(e.repo,"releaseAllocation",1);
                e.count(e.repo,"markNoShow",1);
                e.count(e.repo,"event",1);
            }
        });
        valid(b(60),e->e.fixture("booking","status","DRAFT"),e-> {
            equal(((CancellationResult)e.actual).depositOutcome(),"NO_DEPOSIT","Draft cancellation");
            e.count(e.repo,"cancelBooking",1);
        });
        bad(b(61),e-> {
            e.f.put("balance",money("200000"));
            e.f.put("depositId",null);
        },409,"form");
        for(boolean withDeposit:new boolean[] {
            false,true
        })add(b(62),e-> {
            e.answers.put("markNoShow",0);
            if(withDeposit)e.f.put("balance",money("200000"));
        },409,"form",e-> {
            e.count(e.repo,"releaseAllocation",1);
            e.count(e.repo,"forfeitDeposit",withDeposit?1:0);
            e.none(e.repo,"event");
        });
    }
}
