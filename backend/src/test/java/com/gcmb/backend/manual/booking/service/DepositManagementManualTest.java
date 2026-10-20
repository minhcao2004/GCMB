/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Covers DepositManagement service scenarios using isolated dependencies.
 */

package com.gcmb.backend.manual.booking.service;

import com.gcmb.backend.booking.dto.BookingDtos.CreateBookingRequest;
import com.gcmb.backend.booking.dto.DepositDtos;
import com.gcmb.backend.booking.dto.DepositDtos.CreateRefundRequest;
import com.gcmb.backend.booking.dto.DepositDtos.DepositDetail;
import com.gcmb.backend.booking.dto.DepositDtos.DepositEvent;
import com.gcmb.backend.booking.dto.DepositDtos.DepositItem;
import com.gcmb.backend.booking.dto.DepositDtos.DepositPage;
import com.gcmb.backend.booking.dto.DepositDtos.RefundRequestResult;
import com.gcmb.backend.booking.dto.DepositDtos.RefundableBooking;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.support.ManualScenarios;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import static com.gcmb.backend.manual.booking.service.BookingManualTest.*;
import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class DepositManagementManualTest extends ManualScenarios<DepositFixture> {
    private DepositManagementManualTest() {
        super("Booking","DepositManagementManualTest");
    }

    public static List<ManualTestSuite> suites() {
        var tests=new DepositManagementManualTest();
        tests.describeCases();
        tests.register();
        tests.collectionMethods();
        return tests.buildSuites();
    }

    private void describeCases() {
        define("TC-DEP-005",
            "Deposit Management",
            "BookingService.create",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Ghi nhận cọc thực thu khi tạo booking\n" +
            "Mock create booking hợp lệ, amount=100000, paymentReference hợp lệ; depositBalance mock=100000.",
            "Quan sát receiveDeposit nhận đúng amount/reference; BANK_TRANSFER -> MANUAL_TRANSFER; không gọi SePay. Không chứng minh balance thật tăng.\nĐối chiếu từng vị trí đối số dependency, số tiền bằng compareTo và dữ liệu event; không chỉ kiểm tra có chứa giá trị hoặc số lần gọi.",
            1,
            () -> new DepositFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-DEP-006",
            "Deposit Management",
            "BookingService.create",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " scope B; room R/RT/AVAILABLE/capacity4; overlap=false. Request: " +
            "roomId=R,start=10:00,end=12:00,channel=PHONE,guestCount=1,mode=HOURLY,comboId=null,contactName=An,contactPhone=0901234567,note=null,depositAmount=0,paymentMethod=null,paymentReference=null." +
            " Quote mock HOURLY/240000/1 charge; các write trả ID; read-back F_detail. now=T. Reset mock/fixture trước mỗi " +
            "V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: depositAmount=null.\nV02: depositAmount=0.\nV03: depositAmount=-1.\nV04: depositAmount=0.5.\nV05: depositAmount=1000000000000000000.\nV06: amount=1,method=BANK_TRANSFER; paymentReference=TX123; mock balance=1.\nV07: amount=999999999999999999,method=BANK_TRANSFER; paymentReference=TX123; mock balance=999999999999999999.\nV08: amount=100000,method=CASH; paymentReference=TX123; mock balance=100000.\nV09: amount=100000,method=CARD; paymentReference=TX123; mock balance=100000.\nV10: amount=100000,method=QR; paymentReference=TX123; mock balance=100000.\nV11: amount100000,method=null.\nV12: amount100000,method=UNKNOWN.\nV13: amount=999999999999999998,BANK_TRANSFER,TX123.\nV14: amount=1.00,BANK_TRANSFER,TX123.\nV15: amount=null,method=UNKNOWN,reference dài121.\nV16: amount=0,method=UNKNOWN,reference dài121.\nV17: amount=0.00,method=UNKNOWN,reference dài121.",
            "V01,V02: Không receiveDeposit; insert booking thành công.\nV03,V04,V05: BF(400,depositAmount); không insertBooking.\nV06: receiveDeposit amount1,methodMANUAL_TRANSFER,referenceTX123; trả F_detail.\nV07: receiveDeposit amount999999999999999999,methodMANUAL_TRANSFER,referenceTX123; trả F_detail.\nV08: receiveDeposit amount100000,methodCASH,referenceTX123; trả F_detail.\nV09: receiveDeposit amount100000,methodCARD,referenceTX123; trả F_detail.\nV10: receiveDeposit amount100000,methodQR,referenceTX123; trả F_detail.\nV11,V12: BF(400,paymentMethod); không insertBooking.\nV13,V14: Trả F_detail, receiveDeposit đúng giá trị số tiền, mã TX123 và method MANUAL_TRANSFER; kiểm tra đúng vị trí đối số.\nV15–V17: Trả F_detail; không receiveDeposit; bỏ qua method/reference vì không thu cọc.",
            17,
            () -> new DepositFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-DEP-010", "Deposit Management", "DepositManagementService.detail",
            "DepositManagementService thật; BookingRepository và DepositRepository mock; stub scope, booking, deposit, page" +
            " và request ID.",
            "Chi tiết và lịch sử cọc\n" +
            "Mock deposits.item có fixture hoặc null; history trả fixture.",
            "Có item: trả detail/history fixture; null: 404 và không gọi history; quan sát đúng scope.",
            2,
            () -> new DepositFixture(null),
            e -> e.depositService.detail(e.actor,K));
        define("TC-DEP-007", "Deposit Management", "DepositManagementService.list",
            "DepositManagementService thật; BookingRepository/DepositRepository mock. Identity U/RECEPTIONIST; scope " +
            "B/timezone Asia/Ho_Chi_Minh. Base dateFrom/dateTo/status/method/search=null,page0,size20; count0,summary " +
            "F_summary,list=[]; now=T. requestedStatus=\" held \",requestedMethod=\" manual_transfer \",requestedSearch=\" An " +
            "\",dateFrom20/10,dateTo21/10; query nhận HELD,MANUAL_TRANSFER,An. Reset mock/fixture trước mỗi V; chỉ thay dữ " +
            "liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: count=0; list trả0 fixture rows; summary F_summary; page0,size20.\n" +
            "V02: count=1; list trả1 fixture rows; summary F_summary; page0,size20.\n" +
            "V03: count=20; list trả20 fixture rows; summary F_summary; page0,size20.\n" +
            "V04: count=21; list trả20 fixture rows; summary F_summary; page0,size20.",
            "V01: DepositPage total0,totalPages0,rows/summary đúng fixture; count/list nhận cùng scope B,timezone,filters " +
            "đã chuẩn hóa.\n" +
            "V02: DepositPage total1,totalPages1,rows/summary đúng fixture; count/list nhận cùng scope B,timezone,filters " +
            "đã chuẩn hóa.\n" +
            "V03: DepositPage total20,totalPages1,rows/summary đúng fixture; count/list nhận cùng scope B,timezone,filters " +
            "đã chuẩn hóa.\n" +
            "V04: DepositPage total21,totalPages2,rows/summary đúng fixture; count/list nhận cùng scope B,timezone,filters " +
            "đã chuẩn hóa.",
            4,
            () -> new DepositFixture(null),
            e -> e.depositService.list(e.actor,(LocalDate)e.f.get("from"),(LocalDate)e.f.get("to"),(String)e.f.get("statusFilter"),(String)e.f.get("methodFilter"),(String)e.f.get("search"),(int)e.f.getOrDefault("page",0),(int)e.f.getOrDefault("size",20)));
        define("TC-DEP-008",
            "Deposit Management",
            "DepositManagementService.list",
            "DepositManagementService thật; BookingRepository/DepositRepository mock. Identity U/RECEPTIONIST; scope " +
            "B/timezone Asia/Ho_Chi_Minh. Base dateFrom/dateTo/status/method/search=null,page0,size20; count0,summary " +
            "F_summary,list=[]; now=T. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: dateFrom21/10,dateTo20/10.\nV02: dateFrom=dateTo20/10.\nV03: page=-1.\nV04: page=0.\nV05: size=0.\nV06: size=1.\nV07: size=100.\nV08: size=101.\nV09: search=\"  \"+\"S\"×100+\"  \".\nV10: search=\"S\"×101.\nV11: status=UNKNOWN.\nV12: method=UNKNOWN.\nV13: status=null,method=null.\nV14: status=\"   \",method=\"   \".\nV15: status=\" held \",method=\" manual_transfer \".\nV16–V20: lần lượt status \" held \",\" pending_refund \",\" refunded \",\" forfeited \",\" applied \".\nV21–V26: lần lượt method \" bank_transfer \",\" manual_transfer \",\" cash \",\" card \",\" qr \",\" multiple \".",
            "V01: BF(400,dateTo); không count/summary/list.\nV02: Trả page total0/pages0; filter nhận đúng ngày.\nV03: BF(400,page); không count/summary/list.\nV04: Trả page total0/pages0.\nV05,V08: BF(400,size); không count/summary/list.\nV06: Trả page size1,total0/pages0.\nV07: Trả page size100,total0/pages0.\nV09: Trả page; query search trim dài100.\nV10: BF(400,search); không count/summary/list.\nV11: BF(400,status); không count/summary/list.\nV12: BF(400,method); không count/summary/list.\nV13,V14: Query filters status/method=null; trả page.\nV15: Query HELD/MANUAL_TRANSFER; trả page.\nV16–V26: Trả page; cả count và list nhận đúng status/method đã trim và uppercase; không ghi.",
            26,
            () -> new DepositFixture(null),
            e -> e.depositService.list(e.actor,(LocalDate)e.f.get("from"),(LocalDate)e.f.get("to"),(String)e.f.get("statusFilter"),(String)e.f.get("methodFilter"),(String)e.f.get("search"),(int)e.f.getOrDefault("page",0),(int)e.f.getOrDefault("size",20)));
        define("TC-DEP-014",
            "Deposit Management",
            "DepositManagementService.createRefundRequest",
            "DepositManagementService thật; BookingRepository và DepositRepository mock; stub scope, booking, deposit, page" +
            " và request ID.",
            "Tạo yêu cầu hoàn hợp lệ\n" +
            "Mock CANCELLED, depositId, balance=200000, canRequestRefund=true, create request trả ID.",
            "Trả REQUESTED với amount=200000; gọi createDepositRefundRequest/event đúng dữ liệu; không có gọi thực hiện chuyển tiền.\nĐối chiếu từng vị trí đối số dependency, số tiền bằng compareTo và dữ liệu event; không chỉ kiểm tra có chứa giá trị hoặc số lần gọi.",
            1,
            () -> new DepositFixture(new DepositDtos.CreateRefundRequest(K,"An","Bank A","012345","Hoàn cọc")).refundRequest(),
            e -> e.depositService.createRefundRequest(e.actor,(DepositDtos.CreateRefundRequest)e.cmd));
        define("TC-DEP-015", "Deposit Management", "DepositManagementService.createRefundRequest",
            "DepositManagementService thật; BookingRepository và DepositRepository mock; stub scope, booking, deposit, page" +
            " và request ID.",
            "Thiếu request hoặc bookingId\n" +
            "Request null; bookingId null",
            "400 bookingId trước khi ghi repository",
            2,
            () -> new DepositFixture(new DepositDtos.CreateRefundRequest(K,"An","Bank A","012345","Hoàn cọc")).refundRequest(),
            e -> e.depositService.createRefundRequest(e.actor,(DepositDtos.CreateRefundRequest)e.cmd));
        define("TC-DEP-016",
            "Deposit Management",
            "DepositManagementService.createRefundRequest",
            "DepositManagementService thật; BookingRepository/DepositRepository mock. Identity U/RECEPTIONIST; scope B; " +
            "lock K có,booking K CANCELLED; depositId=D,balance200000; canRequestRefund=true; request " +
            "bookingId=K,beneficiaryName=An,bankName=Ngân hàng A,bankAccount=012345,reason=Hoàn cọc; create request trả Q; " +
            "now=T. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: booking.status=CONFIRMED.\nV02: booking.status=IN_HOUSE.\nV03: booking.status=NO_SHOW.",
            "V01–V03: BF(409,bookingId); không lockDeposit/canRequestRefund/create request/event. Bỏ V04 trùng; giữ TC-DEP-019/V01.",
            3,
            () -> new DepositFixture(new DepositDtos.CreateRefundRequest(K,"An","Bank A","012345","Hoàn cọc")).refundRequest(),
            e -> e.depositService.createRefundRequest(e.actor,(DepositDtos.CreateRefundRequest)e.cmd));
        define("TC-DEP-017", "Deposit Management", "DepositManagementService.createRefundRequest",
            "DepositManagementService thật; BookingRepository và DepositRepository mock; stub scope, booking, deposit, page" +
            " và request ID.",
            "Booking khác cơ sở/thiếu tài khoản cọc/hết số dư\n" +
            "Mock lockBookingId/booking=null hoặc depositId=null/balance<=0 theo từng nhánh.",
            "404 khi không có booking trong scope; 409 khi thiếu/hết cọc; không gọi create request/event.",
            5,
            () -> new DepositFixture(new DepositDtos.CreateRefundRequest(K,"An","Bank A","012345","Hoàn cọc")).refundRequest(),
            e -> e.depositService.createRefundRequest(e.actor,(DepositDtos.CreateRefundRequest)e.cmd));
        define("TC-DEP-018",
            "Deposit Management",
            "DepositManagementService.createRefundRequest",
            "DepositManagementService thật; BookingRepository/DepositRepository mock. Identity U/RECEPTIONIST; scope B; " +
            "lock K có,booking K CANCELLED; depositId=D,balance200000; canRequestRefund=true; request " +
            "bookingId=K,beneficiaryName=An,bankName=Ngân hàng A,bankAccount=012345,reason=Hoàn cọc; create request trả Q; " +
            "now=T. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: beneficiaryName=null\n" +
            "V02: beneficiaryName=\"\"\n" +
            "V03: beneficiaryName=\"   \"\n" +
            "V04: beneficiaryName=\"A\"×201\n" +
            "V05: bankName=null\n" +
            "V06: bankName=\"\"\n" +
            "V07: bankName=\"   \"\n" +
            "V08: bankName=\"A\"×201\n" +
            "V09: bankAccount=null\n" +
            "V10: bankAccount=\"\"\n" +
            "V11: bankAccount=\"   \"\n" +
            "V12: bankAccount=\"A\"×61\n" +
            "V13: reason=null\n" +
            "V14: reason=\"\"\n" +
            "V15: reason=\"   \"\n" +
            "V16: reason=\"A\"×1001\n" +
            "V17: beneficiaryName=\"  \"+\"A\"×200+\"  \".\n" +
            "V18: bankName=\"  \"+\"A\"×200+\"  \".\n" +
            "V19: bankAccount=\"  \"+\"A\"×60+\"  \".\n" +
            "V20: reason=\"  \"+\"A\"×1000+\"  \".",
            "V01,V02,V03,V04: BF(400,beneficiaryName); không create request/event.\nV05,V06,V07,V08: BF(400,bankName); không create request/event.\nV09,V10,V11,V12: BF(400,bankAccount); không create request/event.\nV13,V14,V15,V16: BF(400,reason); không create request/event.\nV17: REQUESTED amount200000; createRefundRequest nhận beneficiaryName trim dài200; event đúngQ/K.\nV18: REQUESTED amount200000; createRefundRequest nhận bankName trim dài200; event đúngQ/K.\nV19: REQUESTED amount200000; createRefundRequest nhận bankAccount trim dài60; event đúngQ/K.\nV20: REQUESTED amount200000; createRefundRequest nhận reason trim dài1000; event đúngQ/K.\nĐối chiếu từng vị trí đối số dependency, số tiền bằng compareTo và dữ liệu event; không chỉ kiểm tra có chứa giá trị hoặc số lần gọi.",
            20,
            () -> new DepositFixture(new DepositDtos.CreateRefundRequest(K,"An","Bank A","012345","Hoàn cọc")).refundRequest(),
            e -> e.depositService.createRefundRequest(e.actor,(DepositDtos.CreateRefundRequest)e.cmd));
        define("TC-DEP-019", "Deposit Management", "DepositManagementService.createRefundRequest",
            "DepositManagementService thật; BookingRepository và DepositRepository mock; stub scope, booking, deposit, page" +
            " và request ID.",
            "Từ chối khi dependency báo không được yêu cầu hoàn\n" +
            "Mock booking CANCELLED có cọc nhưng deposits.canRequestRefund=false.",
            "409; không gọi tạo request. Không chứng minh chống hai request đồng thời/SQL chống trùng.",
            1,
            () -> new DepositFixture(new DepositDtos.CreateRefundRequest(K,"An","Bank A","012345","Hoàn cọc")).refundRequest(),
            e -> e.depositService.createRefundRequest(e.actor,(DepositDtos.CreateRefundRequest)e.cmd));
        define("TC-DEP-020", "Deposit Management", "BookingService.create",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " scope B; room R/RT/AVAILABLE/capacity4; overlap=false. Request: " +
            "roomId=R,start=10:00,end=12:00,channel=PHONE,guestCount=1,mode=HOURLY,comboId=null,contactName=An,contactPhone=0901234567,note=null,depositAmount=0,paymentMethod=null,paymentReference=null." +
            " Quote mock HOURLY/240000/1 charge; các write trả ID; read-back F_detail. now=T. Reset mock trước mỗi V; chỉ " +
            "thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO " +
            "Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: depositAmount100000,methodBANK_TRANSFER,referenceTX123; depositBalance=99999.\n" +
            "V02: Như V01,depositBalance=100001.",
            "V01: receiveDeposit100000/MANUAL_TRANSFER/TX123 đã gọi; IllegalStateException(\"Received booking deposit does " +
            "not match its ledger balance\"); không insertCharge/event BOOKING_CREATED.\n" +
            "V02: Cùng IllegalStateException; receiveDeposit đã gọi; không insertCharge/event BOOKING_CREATED.",
            2,
            () -> new DepositFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-DEP-021",
            "Deposit Management",
            "BookingService.create",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " scope B; room R/RT/AVAILABLE/capacity4; overlap=false. Request: " +
            "roomId=R,start=10:00,end=12:00,channel=PHONE,guestCount=1,mode=HOURLY,comboId=null,contactName=An,contactPhone=0901234567,note=null,depositAmount=0,paymentMethod=null,paymentReference=null." +
            " Quote mock HOURLY/240000/1 charge; các write trả ID; read-back F_detail. now=T. Reset mock trước mỗi V; chỉ " +
            "thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO " +
            "Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "Amount=100000,method=BANK_TRANSFER,balance=100000.\nV01: reference=null.\nV02: reference=\"   \".\nV03: reference=\"  \"+\"R\"×120+\"  \".\nV04: reference=\"R\"×121.\nV05: reference=\"\".\nV06: reference=\"R\".\nV07: reference=\"RR\".\nV08: reference=\"R\"×119.",
            "V01,V02,V04,V05: BF(400,paymentReference); không insertBooking/receiveDeposit/insertCharge/event.\nV03,V06,V07,V08: Trả F_detail; receiveDeposit reference đã trim, đúng độ dài120/1/2/119; method MANUAL_TRANSFER; kiểm tra đủ đối số và BOOKING_CREATED.",
            8,
            () -> new DepositFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-DEP-023", "Deposit Management", "DepositManagementService.list",
            "DepositManagementService thật; BookingRepository/DepositRepository mock. Identity U/RECEPTIONIST; scope " +
            "B/timezone Asia/Ho_Chi_Minh. Base dateFrom/dateTo/status/method/search=null,page0,size20; count0,summary " +
            "F_summary,list=[]; now=T. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: bookings.branchScope(U)=null; các filter base hợp lệ.",
            "V01: BF(403,form); không deposits.count/summary/list; không ghi.",
            1,
            () -> new DepositFixture(null),
            e -> e.depositService.list(e.actor,(LocalDate)e.f.get("from"),(LocalDate)e.f.get("to"),(String)e.f.get("statusFilter"),(String)e.f.get("methodFilter"),(String)e.f.get("search"),(int)e.f.getOrDefault("page",0),(int)e.f.getOrDefault("size",20)));
        define("TC-DEP-022", "Deposit Management", "DepositManagementService.eligibleRefundBookings",
            "DepositManagementService thật, BookingRepository/DepositRepository mock; Identity U/RECEPTIONIST,branchScopeB;" +
            " deposits.eligibleRefundBookings(B) trả fixture list theo V. Reset mock trước mỗi V; chỉ thay phần nêu ở V. " +
            "Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ " +
            "local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Scope B; deposits.eligibleRefundBookings(B) trả [F_eligible K].\n" +
            "V02: Scope B; eligibleRefundBookings(B) trả[].",
            "V01: Trả đúng list fixture; query nhận B; không create request/event.\n" +
            "V02: Trả[]; không create request/event.",
            2,
            () -> new DepositFixture(null),
            e -> e.depositService.eligibleRefundBookings(e.actor));
    }
    static DepositItem item() {
        return new DepositItem(K,"BK001","An","0901234567","P01","Room A",T,money("200000"),money("200000"),money("200000"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"MANUAL_TRANSFER","HELD",null,T,true);
    }
    static void receipt(DepositFixture e,BigDecimal amount,String method,String reference) {
        e.command("depositAmount",amount);
        e.command("paymentMethod",method);
        e.command("paymentReference",reference);
        e.f.put("balance",amount==null?BigDecimal.ZERO:amount);
    }
    static void received(DepositFixture e,String amount,String method) {
        created(e);
        e.count(e.repo,"receiveDeposit",1);
        var call=e.inv(e.repo,"receiveDeposit").get(0);
        equal(call.getArgument(0),B,"Receipt branch");equal(call.getArgument(1),K,"Receipt booking");
        equal(call.getArgument(2),U,"Receipt actor");equal(call.getArgument(3),money(amount),"Receipt amount");
        equal(call.getArgument(4),method,"Recorded method");
        var command=(CreateBookingRequest)e.cmd;
        String reference=command.paymentReference()==null||command.paymentReference().isBlank()?null:command.paymentReference().trim();
        equal(call.getArgument(5),reference,"Receipt reference");
        var event=e.inv(e.repo,"event").get(0);
        equal(event.getArgument(0),K,"Created event booking");equal(event.getArgument(1),U,"Created event actor");
        equal(event.getArgument(2),"BOOKING_CREATED","Created event type");
        Map<?,?> details=event.getArgument(4);equal(details.get("depositReceived"),money(amount),"Created deposit amount");equal(details.get("depositMethod"),method,"Created deposit method");
    }
    static void refund(DepositFixture e) {
        var r=(RefundRequestResult)e.actual;
        equal(r.id(),Q,"Refund request ID");
        equal(r.bookingId(),K,"Refund booking");
        equal(r.status(),"REQUESTED","Refund state");
        equal(r.amount(),money("200000"),"Refund amount");
        e.count(e.repo,"createDepositRefundRequest",1);
        e.count(e.repo,"event",1);
        var command=(DepositDtos.CreateRefundRequest)e.cmd;
        var call=e.inv(e.repo,"createDepositRefundRequest").get(0);
        Object[] expected={B,K,D,money("200000"),command.reason().trim(),command.beneficiaryName().trim(),command.bankName().trim(),command.bankAccount().trim(),U};
        for(int i=0;i<expected.length;i++)equal(call.getArgument(i),expected[i],"Refund argument "+i);
        var event=e.inv(e.repo,"event").get(0);
        equal(event.getArgument(0),K,"Refund event booking");equal(event.getArgument(1),U,"Refund event actor");
        equal(event.getArgument(2),"BOOKING_DEPOSIT_REFUND_REQUESTED","Refund event type");equal(event.getArgument(3),command.reason().trim(),"Refund event reason");
        Map<?,?> details=event.getArgument(4);equal(details.get("refundRequestId"),Q.toString(),"Refund event request");equal(details.get("depositAmount"),money("200000"),"Refund event amount");
    }

    private void register() {
        valid(d(5),e->receipt(e,money("100000"),"BANK_TRANSFER","TX123"),e->received(e,"100000","MANUAL_TRANSFER"));
        for(int i=0;i<12;i++) {
            int n=i;
            int status=i==2||i==3||i==4||i==10||i==11?400:0;
            add(d(6),e-> {
                BigDecimal amount=n==0?null:n==1?BigDecimal.ZERO:n==2?money("-1"):n==3?money("0.5"):n==4?money("1000000000000000000"):n==5?BigDecimal.ONE:n==6?money("999999999999999999"):money("100000");
                String method=n==7?"CASH":n==8?"CARD":n==9?"QR":n==10?null:n==11?"UNKNOWN":"BANK_TRANSFER";
                receipt(e,amount,method,"TX123");
            },status,status==0?null:i>=10?"paymentMethod":"depositAmount",e-> {
                if(e.failure!=null)e.noWrites();
                else if(n<=1) {
                    created(e);
                    e.none(e.repo,"receiveDeposit");
                }
                else received(e,n==5?"1":n==6?"999999999999999999":"100000",n==7?"CASH":n==8?"CARD":n==9?"QR":"MANUAL_TRANSFER");
            });
        }
        for(String amount:List.of("999999999999999998","1.00"))valid(d(6),e->receipt(e,money(amount),"BANK_TRANSFER","TX123"),e->received(e,amount,"MANUAL_TRANSFER"));
        for(BigDecimal amount:Arrays.asList(null,BigDecimal.ZERO,money("0.00")))valid(d(6),e->receipt(e,amount,"UNKNOWN","R".repeat(121)),e->{created(e);e.none(e.repo,"receiveDeposit");});
        valid(d(10),e-> {
            e.f.put("depositItem",item());
            e.answers.put("deposit.history",List.of(new DepositEvent(T,"RECEIVE","Thu cọc","TX123")));
        },e-> {
            equal(((DepositDetail)e.actual).deposit(),item(),"Deposit item");
            equal(((DepositDetail)e.actual).history().size(),1,"History");
            e.noWrites();
        });
        bad(d(10),e-> {
        },404,"bookingId");
        for(long total:new long[] {
            0,1,20,21
        })valid(d(7),e-> {
            e.f.put("from",LocalDate.of(2026,10,20));e.f.put("to",LocalDate.of(2026,10,21));
            e.f.put("statusFilter"," held ");e.f.put("methodFilter"," manual_transfer ");e.f.put("search"," An ");
            e.answers.put("deposit.count",total);
            e.answers.put("deposit.list",Collections.nCopies((int)Math.min(total,20),item()));
        },e-> {
            var p=(DepositPage)e.actual;
            equal(p.total(),total,"Deposit total");
            equal(p.totalPages(),total==0?0:total<=20?1:2,"Deposit pages");
            equal(p.items().size(),(int)Math.min(total,20),"Deposit items");
            equal(Arrays.asList(e.inv(e.deposits,"count").get(0).getArguments()),List.of(B,LocalDate.of(2026,10,20),LocalDate.of(2026,10,21),TZ,"HELD","MANUAL_TRANSFER","An"),"Count filters");
            equal(Arrays.asList(e.inv(e.deposits,"list").get(0).getArguments()),List.of(B,LocalDate.of(2026,10,20),LocalDate.of(2026,10,21),TZ,"HELD","MANUAL_TRANSFER","An",0,20),"List filters");
            equal(Arrays.asList(e.inv(e.deposits,"summary").get(0).getArguments()),List.of(B,LocalDate.of(2026,10,20),LocalDate.of(2026,10,21),TZ),"Summary scope/date/timezone");
            equal(p.summary(),new DepositDtos.DepositSummary(BigDecimal.ZERO,0,BigDecimal.ZERO,0,BigDecimal.ZERO,0,BigDecimal.ZERO,0),"Summary projection");
            e.noWrites();
        });
        filters(this,d(8),true);
        for(String status:List.of("HELD","PENDING_REFUND","REFUNDED","FORFEITED","APPLIED"))valid(d(8),e->e.f.put("statusFilter"," "+status.toLowerCase(Locale.ROOT)+" "),e->{
            equal(e.inv(e.deposits,"count").get(0).getArgument(4),status,"Normalized status/count");equal(e.inv(e.deposits,"list").get(0).getArgument(4),status,"Normalized status/list");e.noWrites();});
        for(String method:List.of("BANK_TRANSFER","MANUAL_TRANSFER","CASH","CARD","QR","MULTIPLE"))valid(d(8),e->e.f.put("methodFilter"," "+method.toLowerCase(Locale.ROOT)+" "),e->{
            equal(e.inv(e.deposits,"count").get(0).getArgument(5),method,"Normalized method/count");equal(e.inv(e.deposits,"list").get(0).getArgument(5),method,"Normalized method/list");e.noWrites();});
        valid(d(14),e-> {
        },DepositManagementManualTest::refund);
        bad(d(15),e->e.cmd=null,400,"bookingId");
        bad(d(15),e->e.command("bookingId",null),400,"bookingId");
        for(String status:List.of("CONFIRMED","IN_HOUSE","NO_SHOW"))bad(d(16),e->e.fixture("booking","status",status),409,"bookingId");
        bad(d(17),e->e.f.put("lock",null),404,"bookingId");
        bad(d(17),e->e.f.put("booking",null),404,"bookingId");
        bad(d(17),e->e.f.put("depositId",null),409,"bookingId");
        for(String amount:List.of("0","-1"))bad(d(17),e->e.f.put("balance",money(amount)),409,"bookingId");
        String[] fields= {
            "beneficiaryName","bankName","bankAccount","reason"
        };
        int[] lengths= {
            200,200,60,1000
        };
        for(int i=0;i<fields.length;i++) {
            String field=fields[i];
            int max=lengths[i];
            for(String v:Arrays.asList(null,"","   ","A".repeat(max+1)))bad(d(18),e->e.command(field,v),400,field);
        }
        for(int i=0;i<fields.length;i++) {
            String field=fields[i];
            int max=lengths[i];
            valid(d(18),e->e.command(field,"  "+"A".repeat(max)+"  "),e-> {
                refund(e);
                check(Arrays.asList(e.inv(e.repo,"createDepositRefundRequest").get(0).getArguments()).contains("A".repeat(max)),"Refund field trimming");
            });
        }
        bad(d(19),e->e.answers.put("deposit.canRequestRefund",false),409,"bookingId");
        for(String balance:List.of("99999","100001"))add(d(20),e-> {
            receipt(e,money("100000"),"BANK_TRANSFER","TX123");
            e.f.put("balance",money(balance));
        },-4,null,e-> {
            e.count(e.repo,"receiveDeposit",1);
            e.none(e.repo,"insertCharge","event");
            equal(e.failure.getMessage(),"Received booking deposit does not match its ledger balance","Ledger mismatch");
        });
        for(String ref:Arrays.asList(null,"   ","  "+"R".repeat(120)+"  ","R".repeat(121),"","R","RR","R".repeat(119))) {
            boolean invalid=ref==null||ref.isBlank()||ref.trim().length()>120;
            add(d(21),e->receipt(e,money("100000"),"BANK_TRANSFER",ref),invalid?400:0,invalid?"paymentReference":null,e->{
                if(invalid)e.noWrites();else received(e,"100000","MANUAL_TRANSFER");
            });
        }
        for(boolean empty:new boolean[] {
            false,true
        })valid(d(22),e->e.answers.put("deposit.eligibleRefundBookings",empty?List.of():List.of(new RefundableBooking(K,"BK001","An","0901234567",T,money("200000")))),e-> {
            equal(((List<?>)e.actual).size(),empty?0:1,"Eligible count");
            equal(e.inv(e.deposits,"eligibleRefundBookings").get(0).getArgument(0),B,"Eligible scope");
            e.noWrites();
        });
        bad(d(23),e->e.f.put("branch",null),403,"form");
    }

    private void collectionMethods() {
        var normalized=new java.util.ArrayList<Example<DepositFixture>>();
        for(String method:List.of(" bank_transfer "," cash "," card "," qr ","MANUAL_TRANSFER","UNKNOWN")) {
            boolean invalid=method.equals("MANUAL_TRANSFER")||method.equals("UNKNOWN");
            String stored=method.trim().toUpperCase(Locale.ROOT);if(stored.equals("BANK_TRANSFER"))stored="MANUAL_TRANSFER";
            String expectedMethod=stored;
            normalized.add(new Example<>("amount=100000; method="+method+"; reference=TX123",
                invalid?"BF(400,paymentMethod), không ghi":"Trả F_detail; method ghi nhận="+stored+", reference TX123; đúng toàn bộ đối số/event",
                e->receipt(e,money("100000"),method,"TX123"),invalid?400:0,invalid?"paymentMethod":null,
                e->{if(invalid)e.noWrites();else received(e,"100000",expectedMethod);}));
        }
        examples(d(24),"Deposit Management","BookingService.create","UC-42. BookingService thật; repository/pricing mock. Base booking hợp lệ, balance100000. Không gọi SePay.",
            ()->new DepositFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e->e.service.create(e.actor,(CreateBookingRequest)e.cmd),normalized);
        var references=new java.util.ArrayList<Example<DepositFixture>>();
        for(String method:List.of("CASH","CARD","QR"))for(String reference:Arrays.asList(null,"","   "," TX123 ","R".repeat(121))) {
            boolean invalid=reference!=null&&reference.trim().length()>120||!method.equals("CASH")&&(reference==null||reference.isBlank());
            String label=reference==null?"null":reference.length()>120?"R×121":"\""+reference+"\"";
            references.add(new Example<>("amount=100000; method="+method+"; reference="+label,
                invalid?"BF(400,paymentReference), không ghi":"Trả F_detail; thu đúng amount/method/reference sau trim, đúng event; không gọi ngân hàng",
                e->receipt(e,money("100000"),method,reference),invalid?400:0,invalid?"paymentReference":null,
                e->{if(invalid)e.noWrites();else received(e,"100000",method);}));
        }
        examples(d(25),"Deposit Management","BookingService.create","UC-42. Cọc dương; CASH cho phép reference trống. CARD/QR phải có mã; BANK_TRANSFER trống đã ở TC-DEP-021. Base booking hợp lệ, repository mock.",
            ()->new DepositFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e->e.service.create(e.actor,(CreateBookingRequest)e.cmd),references);
    }
}
