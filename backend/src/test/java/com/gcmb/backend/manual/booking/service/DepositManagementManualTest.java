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
        return tests.buildSuites();
    }

    private void describeCases() {
        define("TC-DEP-005", "Deposit Management", "BookingService.create",
            "BookingService thật; BookingRepository và BookingPricingService mock. Stub branch, room, booking, allocation, " +
            "charge và read-back theo nhánh.",
            "Ghi nhận cọc thực thu khi tạo booking\n" +
            "Mock create booking hợp lệ, amount=100000, paymentReference hợp lệ; depositBalance mock=100000.",
            "Quan sát receiveDeposit nhận đúng amount/reference; BANK_TRANSFER -> MANUAL_TRANSFER; không gọi SePay. Không " +
            "chứng minh balance thật tăng.",
            1,
            () -> new DepositFixture(new CreateBookingRequest(R,time("10:00:00"),time("12:00:00"),"HOURLY",null,"An","0901234567","PHONE",null,1,BigDecimal.ZERO,null,null)),
            e -> e.service.create(e.actor,(CreateBookingRequest)e.cmd));
        define("TC-DEP-006", "Deposit Management", "BookingService.create",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " scope B; room R/RT/AVAILABLE/capacity4; overlap=false. Request: " +
            "roomId=R,start=10:00,end=12:00,channel=PHONE,guestCount=1,mode=HOURLY,comboId=null,contactName=An,contactPhone=0901234567,note=null,depositAmount=0,paymentMethod=null,paymentReference=null." +
            " Quote mock HOURLY/240000/1 charge; các write trả ID; read-back F_detail. now=T. Reset mock/fixture trước mỗi " +
            "V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: depositAmount=null.\n" +
            "V02: depositAmount=0.\n" +
            "V03: depositAmount=-1.\n" +
            "V04: depositAmount=0.5.\n" +
            "V05: depositAmount=1000000000000000000.\n" +
            "V06: amount=1,method=BANK_TRANSFER; paymentReference=TX123; mock balance=1.\n" +
            "V07: amount=999999999999999999,method=BANK_TRANSFER; paymentReference=TX123; mock balance=999999999999999999.\n" +
            "V08: amount=100000,method=CASH; paymentReference=TX123; mock balance=100000.\n" +
            "V09: amount=100000,method=CARD; paymentReference=TX123; mock balance=100000.\n" +
            "V10: amount=100000,method=QR; paymentReference=TX123; mock balance=100000.\n" +
            "V11: amount100000,method=null.\n" +
            "V12: amount100000,method=UNKNOWN.",
            "V01,V02: Không receiveDeposit; insert booking thành công.\n" +
            "V03,V04,V05: BF(400,depositAmount); không insertBooking.\n" +
            "V06: receiveDeposit amount1,methodMANUAL_TRANSFER,referenceTX123; trả F_detail.\n" +
            "V07: receiveDeposit amount999999999999999999,methodMANUAL_TRANSFER,referenceTX123; trả F_detail.\n" +
            "V08: receiveDeposit amount100000,methodCASH,referenceTX123; trả F_detail.\n" +
            "V09: receiveDeposit amount100000,methodCARD,referenceTX123; trả F_detail.\n" +
            "V10: receiveDeposit amount100000,methodQR,referenceTX123; trả F_detail.\n" +
            "V11,V12: BF(400,paymentMethod); không insertBooking.",
            12,
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
        define("TC-DEP-008", "Deposit Management", "DepositManagementService.list",
            "DepositManagementService thật; BookingRepository/DepositRepository mock. Identity U/RECEPTIONIST; scope " +
            "B/timezone Asia/Ho_Chi_Minh. Base dateFrom/dateTo/status/method/search=null,page0,size20; count0,summary " +
            "F_summary,list=[]; now=T. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
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
            "V10: search=\"S\"×101.\n" +
            "V11: status=UNKNOWN.\n" +
            "V12: method=UNKNOWN.\n" +
            "V13: status=null,method=null.\n" +
            "V14: status=\"   \",method=\"   \".\n" +
            "V15: status=\" held \",method=\" manual_transfer \".",
            "V01: BF(400,dateTo); không count/summary/list.\n" +
            "V02: Trả page total0/pages0; filter nhận đúng ngày.\n" +
            "V03: BF(400,page); không count/summary/list.\n" +
            "V04: Trả page total0/pages0.\n" +
            "V05,V08: BF(400,size); không count/summary/list.\n" +
            "V06: Trả page size1,total0/pages0.\n" +
            "V07: Trả page size100,total0/pages0.\n" +
            "V09: Trả page; query search trim dài100.\n" +
            "V10: BF(400,search); không count/summary/list.\n" +
            "V11: BF(400,status); không count/summary/list.\n" +
            "V12: BF(400,method); không count/summary/list.\n" +
            "V13,V14: Query filters status/method=null; trả page.\n" +
            "V15: Query HELD/MANUAL_TRANSFER; trả page.",
            15,
            () -> new DepositFixture(null),
            e -> e.depositService.list(e.actor,(LocalDate)e.f.get("from"),(LocalDate)e.f.get("to"),(String)e.f.get("statusFilter"),(String)e.f.get("methodFilter"),(String)e.f.get("search"),(int)e.f.getOrDefault("page",0),(int)e.f.getOrDefault("size",20)));
        define("TC-DEP-014", "Deposit Management", "DepositManagementService.createRefundRequest",
            "DepositManagementService thật; BookingRepository và DepositRepository mock; stub scope, booking, deposit, page" +
            " và request ID.",
            "Tạo yêu cầu hoàn hợp lệ\n" +
            "Mock CANCELLED, depositId, balance=200000, canRequestRefund=true, create request trả ID.",
            "Trả REQUESTED với amount=200000; gọi createDepositRefundRequest/event đúng dữ liệu; không có gọi thực hiện " +
            "chuyển tiền.",
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
        define("TC-DEP-016", "Deposit Management", "DepositManagementService.createRefundRequest",
            "DepositManagementService thật; BookingRepository/DepositRepository mock. Identity U/RECEPTIONIST; scope B; " +
            "lock K có,booking K CANCELLED; depositId=D,balance200000; canRequestRefund=true; request " +
            "bookingId=K,beneficiaryName=An,bankName=Ngân hàng A,bankAccount=012345,reason=Hoàn cọc; create request trả Q; " +
            "now=T. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: booking.status=CONFIRMED.\n" +
            "V02: booking.status=IN_HOUSE.\n" +
            "V03: booking.status=NO_SHOW.\n" +
            "V04: booking.status=CANCELLED; canRequestRefund=false.",
            "V01,V02,V03: BF(409,bookingId); không lockDeposit/canRequestRefund/create request/event.\n" +
            "V04: BF(409,bookingId); không create request/event.",
            4,
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
        define("TC-DEP-018", "Deposit Management", "DepositManagementService.createRefundRequest",
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
            "V01,V02,V03,V04: BF(400,beneficiaryName); không create request/event.\n" +
            "V05,V06,V07,V08: BF(400,bankName); không create request/event.\n" +
            "V09,V10,V11,V12: BF(400,bankAccount); không create request/event.\n" +
            "V13,V14,V15,V16: BF(400,reason); không create request/event.\n" +
            "V17: REQUESTED amount200000; createRefundRequest nhận beneficiaryName trim dài200; event đúngQ/K.\n" +
            "V18: REQUESTED amount200000; createRefundRequest nhận bankName trim dài200; event đúngQ/K.\n" +
            "V19: REQUESTED amount200000; createRefundRequest nhận bankAccount trim dài60; event đúngQ/K.\n" +
            "V20: REQUESTED amount200000; createRefundRequest nhận reason trim dài1000; event đúngQ/K.",
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
        define("TC-DEP-021", "Deposit Management", "BookingService.create",
            "BookingService thật; BookingRepository/BookingPricingService mock, ObjectMapper thật. Identity U/RECEPTIONIST;" +
            " scope B; room R/RT/AVAILABLE/capacity4; overlap=false. Request: " +
            "roomId=R,start=10:00,end=12:00,channel=PHONE,guestCount=1,mode=HOURLY,comboId=null,contactName=An,contactPhone=0901234567,note=null,depositAmount=0,paymentMethod=null,paymentReference=null." +
            " Quote mock HOURLY/240000/1 charge; các write trả ID; read-back F_detail. now=T. Reset mock trước mỗi V; chỉ " +
            "thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO " +
            "Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Amount100000,methodBANK_TRANSFER; paymentReference=null; balance100000.\n" +
            "V02: Như V01,paymentReference=\"   \".\n" +
            "V03: Như V01,paymentReference=\"  \"+\"R\"×120+\"  \".\n" +
            "V04: Như V01,paymentReference=\"R\"×121.",
            "V01: Trả F_detail; receiveDeposit reference=null,methodMANUAL_TRANSFER.\n" +
            "V02: Trả F_detail; receiveDeposit reference=null sau trim.\n" +
            "V03: Trả F_detail; receiveDeposit reference trim dài120,methodMANUAL_TRANSFER.\n" +
            "V04: BF(400,paymentReference) trước insertBooking/receiveDeposit.",
            4,
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
        var args=Arrays.asList(e.inv(e.repo,"receiveDeposit").get(0).getArguments());
        check(args.containsAll(List.of(B,K,U,money(amount),method)),"Deposit receipt arguments "+args);
    }
    static void refund(DepositFixture e) {
        var r=(RefundRequestResult)e.actual;
        equal(r.id(),Q,"Refund request ID");
        equal(r.bookingId(),K,"Refund booking");
        equal(r.status(),"REQUESTED","Refund state");
        equal(r.amount(),money("200000"),"Refund amount");
        e.count(e.repo,"createDepositRefundRequest",1);
        e.count(e.repo,"event",1);
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
            e.answers.put("deposit.count",total);
            e.answers.put("deposit.list",Collections.nCopies((int)Math.min(total,20),item()));
        },e-> {
            var p=(DepositPage)e.actual;
            equal(p.total(),total,"Deposit total");
            equal(p.totalPages(),total==0?0:total<=20?1:2,"Deposit pages");
            equal(p.items().size(),(int)Math.min(total,20),"Deposit items");
            equal(e.inv(e.deposits,"count").get(0).getArgument(0),B,"Deposit scope");
            e.noWrites();
        });
        filters(this,d(8),true);
        valid(d(14),e-> {
        },DepositManagementManualTest::refund);
        bad(d(15),e->e.cmd=null,400,"bookingId");
        bad(d(15),e->e.command("bookingId",null),400,"bookingId");
        for(String status:List.of("CONFIRMED","IN_HOUSE","NO_SHOW"))bad(d(16),e->e.fixture("booking","status",status),409,"bookingId");
        bad(d(16),e->e.answers.put("deposit.canRequestRefund",false),409,"bookingId");
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
        for(String ref:Arrays.asList(null,"   ","  "+"R".repeat(120)+"  ","R".repeat(121)))add(d(21),e->receipt(e,money("100000"),"BANK_TRANSFER",ref),ref!=null&&ref.length()==121?400:0,ref!=null&&ref.length()==121?"paymentReference":null,e-> {
            if(e.failure==null) {
                received(e,"100000","MANUAL_TRANSFER");
                equal(e.inv(e.repo,"receiveDeposit").get(0).getArgument(5),ref==null||ref.isBlank()?null:ref.trim(),"Receipt reference");
            }
            else e.noWrites();
        });
        for(boolean empty:new boolean[] {
            false,true
        })valid(d(22),e->e.answers.put("deposit.eligibleRefundBookings",empty?List.of():List.of(new RefundableBooking(K,"BK001","An","0901234567",T,money("200000")))),e-> {
            equal(((List<?>)e.actual).size(),empty?0:1,"Eligible count");
            equal(e.inv(e.deposits,"eligibleRefundBookings").get(0).getArgument(0),B,"Eligible scope");
            e.noWrites();
        });
        bad(d(23),e->e.f.put("branch",null),403,"form");
    }
}
