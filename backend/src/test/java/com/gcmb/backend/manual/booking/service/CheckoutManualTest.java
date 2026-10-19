/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Covers Checkout service scenarios using isolated dependencies.
 */

package com.gcmb.backend.manual.booking.service;

import com.gcmb.backend.booking.dto.CheckoutDtos;
import com.gcmb.backend.booking.dto.CheckoutDtos.Issue;
import com.gcmb.backend.booking.dto.CheckoutDtos.PaymentPlan;
import com.gcmb.backend.booking.dto.CheckoutDtos.Surcharge;
import com.gcmb.backend.booking.dto.CheckoutDtos.Webhook;
import com.gcmb.backend.booking.service.SePaySettings;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.support.ManualScenarios;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class CheckoutManualTest extends ManualScenarios<CheckoutFixture> {
    private CheckoutManualTest() {
        super("Booking","CheckoutManualTest");
    }

    public static List<ManualTestSuite> suites() {
        var tests=new CheckoutManualTest();
        tests.describeCases();
        tests.register();
        return tests.buildSuites();
    }

    private void describeCases() {
        define("TC-STY-051", "Room Stay Management", "CheckoutService.complete",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings fixture, ObjectMapper " +
            "thật. Scope B; bill L " +
            "ISSUED,amount320000,depositApplied100000,paid200000,plannedCash20000,plannedCashTendered50000,checkoutAt=T; " +
            "locks có; AL OCCUPIED,R; read-back bill đủ trường; now=T. Map bill read-back đủ " +
            "bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; tiền không nêu=0, các " +
            "request/payment list đọc lại=[] trừ V chỉ định khác. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V." +
            " RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Base amount320000,deposit100000,paid200000,cash20000,tendered50000.\n" +
            "V02: amount320000,deposit100000,paid220000,cash0,tendered0.",
            "V01: INSERT payment CASH20000,cash_tendered50000,cash_change30000; UPDATE " +
            "billSETTLED,bookingCOMPLETED,allocationFINISHED,roomCLEANING; event checkout.\n" +
            "V02: Không INSERT payment CASH; vẫn UPDATE các trạng thái hoàn tất và event checkout.",
            2,
            () -> new CheckoutFixture(null),
            e -> e.checkout().complete(e.actor,L));
        define("TC-STY-052", "Room Stay Management", "CheckoutService.complete",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings fixture, ObjectMapper " +
            "thật. Scope B; bill L " +
            "ISSUED,amount320000,depositApplied100000,paid200000,plannedCash20000,plannedCashTendered50000,checkoutAt=T; " +
            "locks có; AL OCCUPIED,R; read-back bill đủ trường; now=T. Map bill read-back đủ " +
            "bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; tiền không nêu=0, các " +
            "request/payment list đọc lại=[] trừ V chỉ định khác. Ghi đè plannedCash=0,plannedCashTendered=0 cho mọi V; chỉ" +
            " chỉnh số tiền ở V. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: amount320000,deposit100000,paid219999,cash0.\n" +
            "V02: amount320000,deposit100000,paid220000,cash0; AL OCCUPIED.\n" +
            "V03: amount320000,deposit100000,paid220001,cash0.\n" +
            "V04: Tổng đủ320000; currentAllocationForUpdate=null.\n" +
            "V05: Tổng đủ320000; allocation.state=FINISHED.",
            "V01,V03,V04,V05: BF(409,form); không INSERT payment/UPDATE hoàn tất.\n" +
            "V02: Tổng đủ320000: UPDATE hoàn tất; không INSERT CASH.",
            5,
            () -> new CheckoutFixture(null),
            e -> e.checkout().complete(e.actor,L));
        define("TC-STY-031", "Room Stay Management", "CheckoutService.issue",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings fixture sẵn sàng, " +
            "ObjectMapper thật. Actor U/RECEPTIONIST,scope B; lock K có; existingBill=[]; K IN_HOUSE,plannedEnd10:00; " +
            "meter=null,pendingRefund=false,pendingOrders=[]; depositId=D,balance0; sourceLines=[ROOM240000]. Input " +
            "inspected=true,inspectionNote=null,surcharges=[]; các write thành công; read-back bill F_amount đúng " +
            "sourceLines; now=T. Map bill read-back đủ " +
            "bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; tiền không nêu=0, các " +
            "request/payment list đọc lại=[] trừ V chỉ định khác. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V." +
            " RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: input=null.\n" +
            "V02: inspected=false.\n" +
            "V03: inspected=true,inspectionNote=\"N\"×2000.\n" +
            "V04: inspected=true,inspectionNote=\"N\"×2001.",
            "V01,V02,V04: BF(400,form); không INSERT bill.\n" +
            "V03: Lập bill240000; INSERT bill nhận note dài2000; trả bill fixture tính remaining240000.",
            4,
            () -> new CheckoutFixture(new CheckoutDtos.Issue(true,null,List.of())),
            e -> e.checkout().issue(e.actor,K,(CheckoutDtos.Issue)e.cmd));
        define("TC-STY-032", "Room Stay Management", "CheckoutService.issue",
            "CheckoutService thật; JdbcTemplate, BookingRepository, BookingService mock; SePaySettings fixture/mock; stub " +
            "Map đọc bill và request.",
            "Chặn lập bill khi nghiệp vụ chưa xong\n" +
            "Mock pending refund hoặc pending order hoặc quá giờ không có meter.",
            "409 trước INSERT bill/APPLY; không kết luận DB bảo vệ các trạng thái này.",
            3,
            () -> new CheckoutFixture(new CheckoutDtos.Issue(true,null,List.of())),
            e -> e.checkout().issue(e.actor,K,(CheckoutDtos.Issue)e.cmd));
        define("TC-STY-033", "Room Stay Management", "CheckoutService.issue",
            "CheckoutService thật; JdbcTemplate, BookingRepository, BookingService mock; SePaySettings fixture/mock; stub " +
            "Map đọc bill và request.",
            "Tự chốt meter trước bill\n" +
            "Mock meter RUNNING; BookingService mock cho stopHourlyMeter; query sourceLines trả phí sau stop.",
            "Quan sát stopHourlyMeter gọi trước query phí/INSERT bill. Không gọi BookingService thật để chạy meter.",
            1,
            () -> new CheckoutFixture(new CheckoutDtos.Issue(true,null,List.of())),
            e -> e.checkout().issue(e.actor,K,(CheckoutDtos.Issue)e.cmd));
        define("TC-STY-034", "Room Stay Management", "CheckoutService.issue",
            "CheckoutService thật; JdbcTemplate, BookingRepository, BookingService mock; SePaySettings fixture/mock; stub " +
            "Map đọc bill và request.",
            "Tổng bill có F&B và phụ phí\n" +
            "Mock sourceLines trả room=240000,FNB=60000,surcharge=20000; không pending; deposit=0.",
            "Quan sát amount=320000 trong INSERT bill và từng bill_line đúng fixture. Không kiểm SQL lọc order SERVED hay " +
            "qty*unitPrice ở DB.",
            1,
            () -> new CheckoutFixture(new CheckoutDtos.Issue(true,null,List.of())),
            e -> e.checkout().issue(e.actor,K,(CheckoutDtos.Issue)e.cmd));
        define("TC-STY-035", "Room Stay Management", "CheckoutService.issue",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings fixture sẵn sàng, " +
            "ObjectMapper thật. Actor U/RECEPTIONIST,scope B; lock K có; existingBill=[]; K IN_HOUSE,plannedEnd10:00; " +
            "meter=null,pendingRefund=false,pendingOrders=[]; depositId=D,balance0; sourceLines=[ROOM240000]. Input " +
            "inspected=true,inspectionNote=null,surcharges=[]; các write thành công; read-back bill F_amount đúng " +
            "sourceLines; now=T. Map bill read-back đủ " +
            "bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; tiền không nêu=0, các " +
            "request/payment list đọc lại=[] trừ V chỉ định khác. Mỗi V về amount dùng đúng một fee reason=Phí; các guard " +
            "trước hợp lệ. V01/V06/V13 stub read-back amount đúng tổng nêu ở V. Reset mock/fixture trước mỗi V; chỉ thay dữ" +
            " liệu nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: surcharges=20 fees, mỗi reason=Phí,amount1; sourceLines sau ghi=[ROOM240000,20 dòng SURCHARGE1].\n" +
            "V02: surcharges=21 fees, mỗi reason=Phí,amount1.\n" +
            "V03: surcharges=[null].\n" +
            "V04: Một fee reason=null,amount1.\n" +
            "V05: Một fee reason=\"   \",amount1.\n" +
            "V06: Một fee reason=\"R\"×180,amount1; sourceLines=[ROOM240000,SURCHARGE1].\n" +
            "V07: Một fee reason=\"R\"×181,amount1.\n" +
            "V08: fee.amount=null\n" +
            "V09: fee.amount=0\n" +
            "V10: fee.amount=-1\n" +
            "V11: fee.amount=0.5\n" +
            "V12: fee.amount=1000000000000000000\n" +
            "V13: Một fee reason=Phí,amount999999999999999999; sourceLines mock=[SURCHARGE999999999999999999].",
            "V01: Lập bill240020; 20 INSERT booking_charge và21 bill_line.\n" +
            "V02,V03,V04,V05,V07,V08,V09,V10,V11,V12: BF(400,form); không INSERT booking_charge/bill.\n" +
            "V06: Lập bill240001; ghi reason dài180.\n" +
            "V13: Lập bill999999999999999999; INSERT amount đúng giá trị; không tự áp thêm giới hạn net.",
            13,
            () -> new CheckoutFixture(new CheckoutDtos.Issue(true,null,List.of())),
            e -> e.checkout().issue(e.actor,K,(CheckoutDtos.Issue)e.cmd));
        define("TC-STY-037", "Room Stay Management", "CheckoutService.issue",
            "CheckoutService thật; JdbcTemplate, BookingRepository, BookingService mock; SePaySettings fixture/mock; stub " +
            "Map đọc bill và request.",
            "Áp cọc không giảm doanh thu\n" +
            "Mock sourceLines total=320000, held=100000, không pending; các write không lỗi.",
            "Quan sát net vẫn 320000 và một lời gọi INSERT APPLY=100000; bill read-back fixture remaining=220000. Không " +
            "kiểm số dư ledger thật.",
            1,
            () -> new CheckoutFixture(new CheckoutDtos.Issue(true,null,List.of())),
            e -> e.checkout().issue(e.actor,K,(CheckoutDtos.Issue)e.cmd));
        define("TC-STY-038", "Room Stay Management", "CheckoutService.issue",
            "CheckoutService thật; JdbcTemplate, BookingRepository, BookingService mock; SePaySettings fixture/mock; stub " +
            "Map đọc bill và request.",
            "Kiểm tra quan hệ cọc và tổng bill\n" +
            "Mock held=net; biến thể held>net hoặc held<0.",
            "held=net quan sát APPLY bằng net; âm/vượt net trả 409 trước INSERT bill. Không gọi complete trong cùng ca.",
            5,
            () -> new CheckoutFixture(new CheckoutDtos.Issue(true,null,List.of())),
            e -> e.checkout().issue(e.actor,K,(CheckoutDtos.Issue)e.cmd));
        define("TC-STY-039", "Room Stay Management", "CheckoutService.issue",
            "CheckoutService thật; JdbcTemplate, BookingRepository, BookingService mock; SePaySettings fixture/mock; stub " +
            "Map đọc bill và request.",
            "Lập bill khi dependency trả bill đã có\n" +
            "Mock query existing bill trả một ID ngay khi issue bắt đầu.",
            "Trả bill fixture; không INSERT bill/line/APPLY/surcharge mới; không gửi đồng thời hai request.",
            1,
            () -> new CheckoutFixture(new CheckoutDtos.Issue(true,null,List.of())),
            e -> e.checkout().issue(e.actor,K,(CheckoutDtos.Issue)e.cmd));
        define("TC-STY-041", "Room Stay Management", "CheckoutService.plan",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings.ready=true, " +
            "ObjectMapper thật. Scope B; bill L " +
            "ISSUED,amount220000,depositApplied0,paid0,plannedCash0,plannedCashTendered0; locks có; existing " +
            "payments/requests=[]; financialAccount A có; read-back bill map đủ trường; now=T. Remaining do bill() tính " +
            "=220000. Map bill read-back đủ " +
            "bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; tiền không nêu=0, các " +
            "request/payment list đọc lại=[] trừ V chỉ định khác. Mọi V method=MIXED,remaining220000; khi chỉ đổi " +
            "cashAmount, dùng cashTendered20000 nếu V không ghi giá trị. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu " +
            "nêu ở V. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: MIXED,cashAmount=0,cashTendered=0.\n" +
            "V02: MIXED,cashAmount=1,cashTendered=1.\n" +
            "V03: MIXED,cashAmount=219999,cashTendered=219999.\n" +
            "V04: MIXED,cashAmount=220000,cashTendered=220000.\n" +
            "V05: MIXED,cashAmount=220001,cashTendered=220001.\n" +
            "V06: MIXED,cashAmount20000,cashTendered19999.\n" +
            "V07: MIXED,cashAmount20000,cashTendered20000.\n" +
            "V08: MIXED,cashAmount20000,cashTendered20001.\n" +
            "V09: cashAmount=null\n" +
            "V10: cashAmount=-1\n" +
            "V11: cashAmount=0.5",
            "V01,V04,V05,V06,V09,V10,V11: BF(400,form); không UPDATE plan/request.\n" +
            "V02: plannedCash1; payment_request219999; chưa INSERT payment.\n" +
            "V03: plannedCash219999; payment_request1; chưa INSERT payment.\n" +
            "V07: plannedCash20000,plannedTendered20000; request200000.\n" +
            "V08: plannedCash20000,plannedTendered20001; request200000.",
            11,
            () -> new CheckoutFixture(new CheckoutDtos.PaymentPlan("BANK_TRANSFER",null,null)),
            e -> e.checkout().plan(e.actor,L,(CheckoutDtos.PaymentPlan)e.cmd));
        define("TC-STY-042", "Room Stay Management", "CheckoutService.plan",
            "CheckoutService thật; JdbcTemplate, BookingRepository, BookingService mock; SePaySettings fixture/mock; stub " +
            "Map đọc bill và request.",
            "Phương án chuyển khoản và mixed\n" +
            "Mock lockBill remaining=220000; method BANK_TRANSFER hoặc MIXED cash=20000, tendered đủ; SePaySettings hợp lệ.",
            "Quan sát plannedCash và expected bank amount 220000/200000 trong INSERT payment_request; chưa gọi INSERT " +
            "payment.",
            2,
            () -> new CheckoutFixture(new CheckoutDtos.PaymentPlan("BANK_TRANSFER",null,null)),
            e -> e.checkout().plan(e.actor,L,(CheckoutDtos.PaymentPlan)e.cmd));
        define("TC-STY-063", "Room Stay Management", "CheckoutService.plan",
            "CheckoutService thật; JdbcTemplate, BookingRepository, BookingService mock; SePaySettings fixture/mock; stub " +
            "Map đọc bill và request.",
            "Không lập phương án chuyển khoản khi SePay chưa sẵn sàng\n" +
            "Mock lockBill còn 220000, chưa payment/draft; BANK_TRANSFER; SePaySettings.ready() mock=false.",
            "BookingFailure 503 trước lệnh lưu phương án/payment request; không kiểm status trả qua HTTP.",
            1,
            () -> new CheckoutFixture(new CheckoutDtos.PaymentPlan("BANK_TRANSFER",null,null)),
            e -> e.checkout().plan(e.actor,L,(CheckoutDtos.PaymentPlan)e.cmd));
        define("TC-STY-030", "Room Stay Management", "CheckoutService.preview",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings.ready=true, " +
            "ObjectMapper thật. Actor U/RECEPTIONIST,scope B; K IN_HOUSE,actualCheckin08:00,plannedEnd10:00; AL R/P01; " +
            "existingBill=[]; sourceLines=[ROOM240000,FNB60000]; balance100000; pendingOrders=[],hasPendingRefund=false; " +
            "now=T. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Base lines ROOM240000,FNB60000; pendingOrders=[],pendingRefund=false,balance100000.\n" +
            "V02: Cùng amounts; pendingOrders=[order O]; hasPendingRefund=true.",
            "V01: preview amount300000,depositBalance100000,lines đủ2 " +
            "dòng,pendingOrders=[],hasPendingRefund=false,sepayReady=true; không bill/payment mutation.\n" +
            "V02: preview amount300000,depositBalance100000,pendingOrders=[O],hasPendingRefund=true; không bill/payment " +
            "mutation.",
            2,
            () -> new CheckoutFixture(null),
            e -> e.checkout().preview(e.actor,K));
        define("TC-STY-044", "Room Stay Management", "CheckoutService.webhook",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock; ObjectMapper thật. SePaySettings " +
            "thật cấu hình fixture BANK_A/012345/TEST HOLDER/TEST_KEY; authorization=\"Apikey TEST_KEY\". Payload " +
            "id123,gateway=BANK_A,accountNumber=012345,transferType=in,amount220000,transactionDate=2026-10-20 " +
            "10:10:00,code=GZ0123456789ABCDEF0123,content=\"\". Financial account A; request Q " +
            "BILL/PENDING,account_id=A,expected_amount220000,created10:00,expires10:30; các lock có; bank_transaction " +
            "INSERT=1; now=T. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Base payload id123,ref GZ0123456789ABCDEF0123,in,220000,occurred20/10 10:10; request " +
            "PENDING,A,created10:00,expires10:30; INSERT bank_transaction=1.",
            "V01: INSERT payment purposeBILL,methodBANK_TRANSFER,amount220000,request_idQ,bank_transaction_id " +
            "mới,received_at10:10,idempotency_keySEPAY-123; audit CONFIRM_SEPAY_PAYMENT; không assert trigger SETTLED.",
            1,
            () -> new CheckoutFixture(new CheckoutDtos.Webhook(123L,"BANK_A","2026-10-20 10:10:00","012345","GZ0123456789ABCDEF0123","","in",money("220000"),null)),
            e -> {
            e.checkout().webhook((String)e.f.getOrDefault("auth","Apikey TEST_KEY"),(CheckoutDtos.Webhook)e.cmd);
            return null;
        });
        define("TC-STY-045", "Room Stay Management", "CheckoutService.webhook",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock; ObjectMapper thật. SePaySettings " +
            "thật cấu hình fixture BANK_A/012345/TEST HOLDER/TEST_KEY; authorization=\"Apikey TEST_KEY\". Payload " +
            "id123,gateway=BANK_A,accountNumber=012345,transferType=in,amount220000,transactionDate=2026-10-20 " +
            "10:10:00,code=GZ0123456789ABCDEF0123,content=\"\". Financial account A; request Q " +
            "BILL/PENDING,account_id=A,expected_amount220000,created10:00,expires10:30; các lock có; bank_transaction " +
            "INSERT=1; now=T. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: payload=null\n" +
            "V02: id=null\n" +
            "V03: id=0\n" +
            "V04: id=-1\n" +
            "V05: transferAmount=null\n" +
            "V06: transferAmount=0\n" +
            "V07: transferAmount=-1\n" +
            "V08: transferAmount=0.5\n" +
            "V09: transferAmount=1000000000000000000\n" +
            "V10: gateway=BANK_B\n" +
            "V11: accountNumber=999999\n" +
            "V12: transactionDate=\"bad-date\"\n" +
            "V13: transferType=\"other\"\n" +
            "V14: transferType=null",
            "V01,V02,V03,V04,V05,V06,V07,V08,V09,V10,V11,V12,V13,V14: BF(400,form); không INSERT payment.",
            14,
            () -> new CheckoutFixture(new CheckoutDtos.Webhook(123L,"BANK_A","2026-10-20 10:10:00","012345","GZ0123456789ABCDEF0123","","in",money("220000"),null)),
            e -> {
            e.checkout().webhook((String)e.f.getOrDefault("auth","Apikey TEST_KEY"),(CheckoutDtos.Webhook)e.cmd);
            return null;
        });
        define("TC-STY-046", "Room Stay Management", "CheckoutService.webhook",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock; ObjectMapper thật. SePaySettings " +
            "thật cấu hình fixture BANK_A/012345/TEST HOLDER/TEST_KEY; authorization=\"Apikey TEST_KEY\". Payload " +
            "id123,gateway=BANK_A,accountNumber=012345,transferType=in,amount220000,transactionDate=2026-10-20 " +
            "10:10:00,code=GZ0123456789ABCDEF0123,content=\"\". Financial account A; request Q " +
            "BILL/PENDING,account_id=A,expected_amount220000,created10:00,expires10:30; các lock có; bank_transaction " +
            "INSERT=1; now=T. Mỗi V giữ nguyên điều kiện match khác của base; INSERT bank_transaction=1. out là payload hợp" +
            " lệ nhưng không khớp khoản thu. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Reference lookup trả [].\n" +
            "V02: Request.status=SETTLED.\n" +
            "V03: Request.account_id=A2 khác financial account A.\n" +
            "V04: Payload.transferAmount=219999,expected220000.\n" +
            "V05: Payload.transferAmount=220001,expected220000.\n" +
            "V06: Payload.transferType=out.\n" +
            "V07: Occurred09:59:57 trước created−2s.\n" +
            "V08: Occurred10:30:01 sau expires.",
            "V01: Có INSERT bank_transaction UNMATCHED; không UPDATE EXCEPTION/INSERT payment.\n" +
            "V02,V03,V04,V05,V06,V07,V08: UPDATE bank_transaction EXCEPTION; không INSERT payment.",
            8,
            () -> new CheckoutFixture(new CheckoutDtos.Webhook(123L,"BANK_A","2026-10-20 10:10:00","012345","GZ0123456789ABCDEF0123","","in",money("220000"),null)),
            e -> {
            e.checkout().webhook((String)e.f.getOrDefault("auth","Apikey TEST_KEY"),(CheckoutDtos.Webhook)e.cmd);
            return null;
        });
        define("TC-STY-047", "Room Stay Management", "CheckoutService.webhook",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock; ObjectMapper thật. SePaySettings " +
            "thật cấu hình fixture BANK_A/012345/TEST HOLDER/TEST_KEY; authorization=\"Apikey TEST_KEY\". Payload " +
            "id123,gateway=BANK_A,accountNumber=012345,transferType=in,amount220000,transactionDate=2026-10-20 " +
            "10:10:00,code=GZ0123456789ABCDEF0123,content=\"\". Financial account A; request Q " +
            "BILL/PENDING,account_id=A,expected_amount220000,created10:00,expires10:30; các lock có; bank_transaction " +
            "INSERT=1; now=T. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Payload.transactionDate=2026-10-20 09:59:58.\n" +
            "V02: Payload.transactionDate=2026-10-20 09:59:57.\n" +
            "V03: Payload.transactionDate=2026-10-20 10:30:00.\n" +
            "V04: Payload.transactionDate=2026-10-20 10:30:01.",
            "V01: INSERT payment/audit; received_at09:59:58.\n" +
            "V02,V04: UPDATE EXCEPTION; không INSERT payment/audit xác nhận.\n" +
            "V03: INSERT payment/audit; received_at10:30:00.",
            4,
            () -> new CheckoutFixture(new CheckoutDtos.Webhook(123L,"BANK_A","2026-10-20 10:10:00","012345","GZ0123456789ABCDEF0123","","in",money("220000"),null)),
            e -> {
            e.checkout().webhook((String)e.f.getOrDefault("auth","Apikey TEST_KEY"),(CheckoutDtos.Webhook)e.cmd);
            return null;
        });
        define("TC-STY-048", "Room Stay Management", "CheckoutService.webhook",
            "CheckoutService thật; JdbcTemplate, BookingRepository, BookingService mock; SePaySettings fixture/mock; stub " +
            "Map đọc bill và request.",
            "Dừng khi dependency báo giao dịch đã ghi nhận\n" +
            "Mock bank_transaction INSERT trả 0 do externalId đã có.",
            "Webhook kết thúc sớm, không INSERT payment/audit; không kiểm unique constraint hoặc hai externalId tranh chấp.",
            1,
            () -> new CheckoutFixture(new CheckoutDtos.Webhook(123L,"BANK_A","2026-10-20 10:10:00","012345","GZ0123456789ABCDEF0123","","in",money("220000"),null)),
            e -> {
            e.checkout().webhook((String)e.f.getOrDefault("auth","Apikey TEST_KEY"),(CheckoutDtos.Webhook)e.cmd);
            return null;
        });
        define("TC-STY-049", "Room Stay Management", "CheckoutService.webhook",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock; ObjectMapper thật. SePaySettings " +
            "thật cấu hình fixture BANK_A/012345/TEST HOLDER/TEST_KEY; authorization=\"Apikey TEST_KEY\". Payload " +
            "id123,gateway=BANK_A,accountNumber=012345,transferType=in,amount220000,transactionDate=2026-10-20 " +
            "10:10:00,code=GZ0123456789ABCDEF0123,content=\"\". Financial account A; request Q " +
            "BILL/PENDING,account_id=A,expected_amount220000,created10:00,expires10:30; các lock có; bank_transaction " +
            "INSERT=1; now=T. Cho mọi reference lookup trả[]; chỉ quan sát query reference,bank_transaction UNMATCHED và " +
            "không payment. Các guard payload trước regex hợp lệ. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V." +
            " RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: code=\"gz0123456789abcdef0123\"; content=\"\".\n" +
            "V02: code=null; content=\"TT GZ0123456789ABCDEF0123 done\".\n" +
            "V03: code=null; content=\"GZ\"+\"A\"×19.\n" +
            "V04: code=null; content=\"GZ\"+\"A\"×21.\n" +
            "V05: code=null; content=\"GZ\"+\"A\"×19+\"Z\".\n" +
            "V06: code=null; content=\"XGZ0123456789ABCDEF0123\".\n" +
            "V07: code=null; content=\"GZ0123456789ABCDEF0123X\".\n" +
            "V08: code=null; content=\"(GZ0123456789ABCDEF0123)\".\n" +
            "V09: code=GZ0123456789ABCDEF0123; content=GZAAAAAAAAAAAAAAAAAAAA.\n" +
            "V10: code=\"bad\"; content=GZAAAAAAAAAAAAAAAAAAAA.",
            "V01,V02,V08: Lookup reference=GZ0123456789ABCDEF0123.\n" +
            "V03,V04,V05,V06,V07: Lookup reference=\"\"; không payment.\n" +
            "V09: Lookup dùng code GZ0123456789ABCDEF0123, không lấy content.\n" +
            "V10: Lookup reference=GZAAAAAAAAAAAAAAAAAAAA.",
            10,
            () -> new CheckoutFixture(new CheckoutDtos.Webhook(123L,"BANK_A","2026-10-20 10:10:00","012345","GZ0123456789ABCDEF0123","","in",money("220000"),null)),
            e -> {
            e.checkout().webhook((String)e.f.getOrDefault("auth","Apikey TEST_KEY"),(CheckoutDtos.Webhook)e.cmd);
            return null;
        });
        define("TC-STY-062", "Room Stay Management", "SePaySettings.authorized",
            "SePaySettings thật với chuỗi cấu hình fixture. Không chạy HTTP, server hoặc gọi ngân hàng.",
            "Kiểm tra chuỗi chứng thực SePay\n" +
            "SePaySettings thật với cấu hình đủ; header null/sai/đúng Apikey + key; biến thể cấu hình chưa đủ.",
            "authorized() chỉ true khi ready và header khớp chính xác; false cho các trường hợp còn lại. Không kiểm status " +
            "HTTP.",
            4,
            () -> new CheckoutFixture(null),
            e -> e.sepay.authorized((String)e.f.get("auth")));
        define("TC-STY-043", "Room Stay Management", "SePaySettings.ready",
            "SePaySettings thật với chuỗi cấu hình fixture. Không chạy HTTP, server hoặc gọi ngân hàng.",
            "Kiểm tra cấu hình SePay sẵn sàng\n" +
            "SePaySettings thật; lần lượt thiếu bank/account/holder/webhookKey và trường hợp đủ bốn giá trị.",
            "ready() false khi thiếu bất kỳ giá trị bắt buộc; true khi đủ. Không HTTP hoặc gateway.",
            5,
            () -> new CheckoutFixture(null),
            e -> e.sepay.ready());
        define("TC-STY-091", "Room Stay Management", "CheckoutService.bill",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings.ready=true, " +
            "ObjectMapper thật. Actor U/RECEPTIONIST,scope B. Bill L: " +
            "bookingIdK,statusISSUED,amount320000,depositApplied100000,paid200000,plannedCash20000,plannedCashTendered20000,checkoutAt=T." +
            " Bill-line/payment/request list=[],trừ V nêu khác; query bill trả map đủ các trường. now=T. Reset mock trước " +
            "mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là " +
            "chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: amount320000,deposit100000,paid199999,plannedCash20000.\n" +
            "V02: paid200000; các tiền khác như V01.\n" +
            "V03: paid200001; các tiền khác như V01.",
            "V01: remaining20001,readyToComplete=false; không ghi.\n" +
            "V02: remaining20000,readyToComplete=true; không ghi.\n" +
            "V03: remaining19999,readyToComplete=false; không ghi.",
            3,
            () -> new CheckoutFixture(null),
            e -> e.checkout().bill(e.actor,L));
        define("TC-STY-092", "Room Stay Management", "CheckoutService.bill",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings.ready=true, " +
            "ObjectMapper thật. Actor U/RECEPTIONIST,scope B. Bill L: " +
            "bookingIdK,statusISSUED,amount320000,depositApplied100000,paid200000,plannedCash20000,plannedCashTendered20000,checkoutAt=T." +
            " Bill-line/payment/request list=[],trừ V nêu khác; query bill trả map đủ các trường. now=T. Reset mock trước " +
            "mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là " +
            "chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Bill amount320000,deposit100000,paid0,cash0; request Q amount220000,ref " +
            "GZ0123456789ABCDEF0123,bankBANK_A,account012345,expires=T−1s.\n" +
            "V02: Như V01,expires=T.\n" +
            "V03: Như V01,expires=T+1s.",
            "V01: request.expired=true; qrUrl có acc=012345&bank=BANK_A&amount=220000&des=GZ0123456789ABCDEF0123; " +
            "remaining220000.\n" +
            "V02,V03: request.expired=false; cùng QR amount/ref; remaining220000.",
            3,
            () -> new CheckoutFixture(null),
            e -> e.checkout().bill(e.actor,L));
        define("TC-STY-093", "Room Stay Management", "CheckoutService.bill",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings.ready=true, " +
            "ObjectMapper thật. Actor U/RECEPTIONIST,scope B. Bill L: " +
            "bookingIdK,statusISSUED,amount320000,depositApplied100000,paid200000,plannedCash20000,plannedCashTendered20000,checkoutAt=T." +
            " Bill-line/payment/request list=[],trừ V nêu khác; query bill trả map đủ các trường. now=T. Reset mock trước " +
            "mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là " +
            "chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Query bill(L,B) trả[].",
            "V01: BF(404,form); không bill_line/request/payment queries tiếp theo,không ghi.",
            1,
            () -> new CheckoutFixture(null),
            e -> e.checkout().bill(e.actor,L));
        define("TC-STY-085", "Room Stay Management", "CheckoutService.changeMethod",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings fixture, ObjectMapper " +
            "thật. Actor U/RECEPTIONIST,scope B. Bill L " +
            "ISSUED,K,amount220000,paid0,depositApplied0,plannedCash0,plannedCashTendered0,checkoutAt=T. Locks có ID; query" +
            " payment dùng kiểm guard trả[]; query bill/lines/requests/payments có fixture riêng. Request UPDATE CANCELLED " +
            "trả1 trừ V nêu khác. now=T. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: ISSUED,no payment; UPDATE request CANCELLED trả1; plannedCash0.\n" +
            "V02: ISSUED,no payment; UPDATE request trả0; plannedCash20000,tendered50000.",
            "V01: UPDATE request CANCELLED đúng L trước reset plannedCash/tendered0; audit CHANGE_BILL_PAYMENT_METHOD; " +
            "không INSERT payment hay đảo khoản thu.\n" +
            "V02: Vẫn reset plannedCash/tendered0; audit CHANGE_BILL_PAYMENT_METHOD vì có cash draft; không INSERT payment.",
            2,
            () -> new CheckoutFixture(null),
            e -> e.checkout().changeMethod(e.actor,L));
        define("TC-STY-086", "Room Stay Management", "CheckoutService.changeMethod",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings fixture, ObjectMapper " +
            "thật. Actor U/RECEPTIONIST,scope B. Bill L " +
            "ISSUED,K,amount220000,paid0,depositApplied0,plannedCash0,plannedCashTendered0,checkoutAt=T. Locks có ID; query" +
            " payment dùng kiểm guard trả[]; query bill/lines/requests/payments có fixture riêng. Request UPDATE CANCELLED " +
            "trả1 trừ V nêu khác. now=T. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: statusSETTLED,paid220000,amount220000.",
            "V01: BF(409,form); không query guard payment/UPDATE request/reset draft/audit.",
            1,
            () -> new CheckoutFixture(null),
            e -> e.checkout().changeMethod(e.actor,L));
        define("TC-STY-087", "Room Stay Management", "CheckoutService.changeMethod",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings fixture, ObjectMapper " +
            "thật. Actor U/RECEPTIONIST,scope B. Bill L " +
            "ISSUED,K,amount220000,paid0,depositApplied0,plannedCash0,plannedCashTendered0,checkoutAt=T. Locks có ID; query" +
            " payment dùng kiểm guard trả[]; query bill/lines/requests/payments có fixture riêng. Request UPDATE CANCELLED " +
            "trả1 trừ V nêu khác. now=T. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: ISSUED,paid10000; query guard payment trả[P].",
            "V01: BF(409,form); không UPDATE request/reset draft/audit.",
            1,
            () -> new CheckoutFixture(null),
            e -> e.checkout().changeMethod(e.actor,L));
        define("TC-STY-088", "Room Stay Management", "CheckoutService.changeMethod",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings fixture, ObjectMapper " +
            "thật. Actor U/RECEPTIONIST,scope B. Bill L " +
            "ISSUED,K,amount220000,paid0,depositApplied0,plannedCash0,plannedCashTendered0,checkoutAt=T. Locks có ID; query" +
            " payment dùng kiểm guard trả[]; query bill/lines/requests/payments có fixture riêng. Request UPDATE CANCELLED " +
            "trả1 trừ V nêu khác. now=T. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: ISSUED,no payment,plannedCash0,tendered0; request UPDATE trả0.",
            "V01: Vẫn gọi UPDATE request và reset draft=0 theo source; không audit CHANGE_BILL_PAYMENT_METHOD,không INSERT " +
            "payment/request; trả bill fixture.",
            1,
            () -> new CheckoutFixture(null),
            e -> e.checkout().changeMethod(e.actor,L));
        define("TC-STY-082", "Room Stay Management", "CheckoutService.complete",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings fixture, ObjectMapper " +
            "thật. Scope B; bill L " +
            "ISSUED,amount320000,depositApplied100000,paid200000,plannedCash20000,plannedCashTendered50000,checkoutAt=T; " +
            "locks có; AL OCCUPIED,R; read-back bill đủ trường; now=T. Map bill read-back đủ " +
            "bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; trường tiền không nêu=0, các " +
            "list đọc bill=[],trừ V nêu khác. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Bill amount320000,depositApplied320000,paid0,plannedCash0,plannedCashTendered0; AL OCCUPIED.",
            "V01: Không INSERT payment CASH/request; UPDATE " +
            "billSETTLED,allocationFINISHED,bookingCOMPLETED,roomCLEANING/history; event/audit checkout; read-back " +
            "remaining0/ready=true.",
            1,
            () -> new CheckoutFixture(null),
            e -> e.checkout().complete(e.actor,L));
        define("TC-STY-083", "Room Stay Management", "CheckoutService.complete",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings fixture, ObjectMapper " +
            "thật. Scope B; bill L " +
            "ISSUED,amount320000,depositApplied100000,paid200000,plannedCash20000,plannedCashTendered50000,checkoutAt=T; " +
            "locks có; AL OCCUPIED,R; read-back bill đủ trường; now=T. Map bill read-back đủ " +
            "bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; trường tiền không nêu=0, các " +
            "list đọc bill=[],trừ V nêu khác. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Bill SETTLED,amount320000,deposit100000,paid220000,plannedCash0; current state là kết quả hoàn tất đã " +
            "được mock.",
            "V01: Trả bill remaining0/ready=true; không currentAllocationForUpdate/INSERT payment/UPDATE trạng " +
            "thái/history/event/audit checkout.",
            1,
            () -> new CheckoutFixture(null),
            e -> e.checkout().complete(e.actor,L));
        define("TC-STY-095", "Room Stay Management", "CheckoutService.issue",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings fixture sẵn sàng, " +
            "ObjectMapper thật. Actor U/RECEPTIONIST,scope B; lock K có; existingBill=[]; K IN_HOUSE,plannedEnd10:00; " +
            "meter=null,pendingRefund=false,pendingOrders=[]; depositId=D,balance0; sourceLines=[ROOM240000]. Input " +
            "inspected=true,inspectionNote=null,surcharges=[]; các write thành công; read-back bill F_amount đúng " +
            "sourceLines; now=T. Map bill read-back đủ " +
            "bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; trường tiền không nêu=0, các " +
            "list đọc bill=[],trừ V nêu khác. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Input inspectedtrue,surcharges=[]; sourceLines=[]; các blocker trước đều false,booking còn thời gian.",
            "V01: BF(409,form); không INSERT bill/bill_line/APPLY/audit ISSUE_BILL.",
            1,
            () -> new CheckoutFixture(new CheckoutDtos.Issue(true,null,List.of())),
            e -> e.checkout().issue(e.actor,K,(CheckoutDtos.Issue)e.cmd));
        define("TC-STY-077", "Room Stay Management", "CheckoutService.plan",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings.ready=true, " +
            "ObjectMapper thật. Scope B; bill L " +
            "ISSUED,amount220000,depositApplied0,paid0,plannedCash0,plannedCashTendered0; locks có; existing " +
            "payments/requests=[]; financialAccount A có; read-back bill map đủ trường; now=T. Remaining do bill() tính " +
            "=220000. Map bill read-back đủ " +
            "bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; trường tiền không nêu=0, các " +
            "list đọc bill=[],trừ V nêu khác. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: method=CASH,cashTendered220000; sepay.ready=false.\n" +
            "V02: method=CASH,cashTendered250000; sepay.ready=false.",
            "V01: UPDATE plannedCash220000,plannedCashTendered220000; audit PLAN_BILL_PAYMENT cash220000/bank0; không " +
            "payment_request/payment.\n" +
            "V02: UPDATE plannedCash220000,plannedCashTendered250000; audit cash220000/bank0; không " +
            "payment_request/payment.",
            2,
            () -> new CheckoutFixture(new CheckoutDtos.PaymentPlan("BANK_TRANSFER",null,null)),
            e -> e.checkout().plan(e.actor,L,(CheckoutDtos.PaymentPlan)e.cmd));
        define("TC-STY-078", "Room Stay Management", "CheckoutService.plan",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings.ready=true, " +
            "ObjectMapper thật. Scope B; bill L " +
            "ISSUED,amount220000,depositApplied0,paid0,plannedCash0,plannedCashTendered0; locks có; existing " +
            "payments/requests=[]; financialAccount A có; read-back bill map đủ trường; now=T. Remaining do bill() tính " +
            "=220000. Map bill read-back đủ " +
            "bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; trường tiền không nêu=0, các " +
            "list đọc bill=[],trừ V nêu khác. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Bill ISSUED,plannedCash20000,plannedCashTendered50000; input BANK_TRANSFER.",
            "V01: Trả bill tính từ fixture hiện tại; không UPDATE plannedCash,không INSERT request/payment/audit; không ghi" +
            " đè phương án.",
            1,
            () -> new CheckoutFixture(new CheckoutDtos.PaymentPlan("BANK_TRANSFER",null,null)),
            e -> e.checkout().plan(e.actor,L,(CheckoutDtos.PaymentPlan)e.cmd));
        define("TC-STY-079", "Room Stay Management", "CheckoutService.plan",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings.ready=true, " +
            "ObjectMapper thật. Scope B; bill L " +
            "ISSUED,amount220000,depositApplied0,paid0,plannedCash0,plannedCashTendered0; locks có; existing " +
            "payments/requests=[]; financialAccount A có; read-back bill map đủ trường; now=T. Remaining do bill() tính " +
            "=220000. Map bill read-back đủ " +
            "bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; trường tiền không nêu=0, các " +
            "list đọc bill=[],trừ V nêu khác. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: plannedCash0,paid10000; query guard payment/request trả [payment P]; input BANK_TRANSFER.",
            "V01: Trả bill fixture; không UPDATE plan/INSERT request/payment/audit.",
            1,
            () -> new CheckoutFixture(new CheckoutDtos.PaymentPlan("BANK_TRANSFER",null,null)),
            e -> e.checkout().plan(e.actor,L,(CheckoutDtos.PaymentPlan)e.cmd));
        define("TC-STY-080", "Room Stay Management", "CheckoutService.plan",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings.ready=true, " +
            "ObjectMapper thật. Scope B; bill L " +
            "ISSUED,amount220000,depositApplied0,paid0,plannedCash0,plannedCashTendered0; locks có; existing " +
            "payments/requests=[]; financialAccount A có; read-back bill map đủ trường; now=T. Remaining do bill() tính " +
            "=220000. Map bill read-back đủ " +
            "bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; trường tiền không nêu=0, các " +
            "list đọc bill=[],trừ V nêu khác. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: plannedCash0; query guard trả request Q PENDING; input BANK_TRANSFER.\n" +
            "V02: Query guard trả request Q EXPIRED; các trường khác như V01.",
            "V01,V02: Trả bill fixture; không UPDATE plan/INSERT request/payment/audit.",
            2,
            () -> new CheckoutFixture(new CheckoutDtos.PaymentPlan("BANK_TRANSFER",null,null)),
            e -> e.checkout().plan(e.actor,L,(CheckoutDtos.PaymentPlan)e.cmd));
        define("TC-STY-081", "Room Stay Management", "CheckoutService.plan",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings.ready=true, " +
            "ObjectMapper thật. Scope B; bill L " +
            "ISSUED,amount220000,depositApplied0,paid0,plannedCash0,plannedCashTendered0; locks có; existing " +
            "payments/requests=[]; financialAccount A có; read-back bill map đủ trường; now=T. Remaining do bill() tính " +
            "=220000. Map bill read-back đủ " +
            "bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; trường tiền không nêu=0, các " +
            "list đọc bill=[],trừ V nêu khác. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Bill statusSETTLED,amount220000,paid220000,deposit0,cash0; input=null.",
            "V01: Trả bill remaining0/readyToComplete=true; không validate input/UPDATE plan/INSERT request/payment/audit.",
            1,
            () -> new CheckoutFixture(new CheckoutDtos.PaymentPlan("BANK_TRANSFER",null,null)),
            e -> e.checkout().plan(e.actor,L,(CheckoutDtos.PaymentPlan)e.cmd));
        define("TC-STY-094", "Room Stay Management", "CheckoutService.preview",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings.ready=true, " +
            "ObjectMapper thật. Actor U/RECEPTIONIST,scope B; K IN_HOUSE,actualCheckin08:00,plannedEnd10:00; AL R/P01; " +
            "existingBill=[]; sourceLines=[ROOM240000,FNB60000]; balance100000; pendingOrders=[],hasPendingRefund=false; " +
            "now=T. Map bill read-back đủ bookingId,amount,depositApplied,paid,plannedCash,plannedCashTendered,checkoutAt; " +
            "trường tiền không nêu=0, các list đọc bill=[],trừ V nêu khác. Các query bill/lines/requests/payments được mock" +
            " đầy đủ; existing bill branch chạy trước requireInHouse. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền " +
            "là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local." +
            " RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: Booking K COMPLETED; existing bill query trả L; bill map " +
            "SETTLED,amount320000,deposit100000,paid220000,cash0.",
            "V01: Trả map bill L,remaining0,ready=true; không sourceLines/pendingOrders/currentAllocation của nhánh preview" +
            " mới; không INSERT bill/payment.",
            1,
            () -> new CheckoutFixture(null),
            e -> e.checkout().preview(e.actor,K));
        define("TC-STY-084", "Room Stay Management", "CheckoutService.renew",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock, SePaySettings fixture, ObjectMapper " +
            "thật. Actor U/RECEPTIONIST,scope B. Bill L " +
            "ISSUED,K,amount220000,paid0,depositApplied0,plannedCash0,plannedCashTendered0,checkoutAt=T. " +
            "lockBooking/lockBill có ID; bill() read-back đủ map/lines/requests/payments. Không chạy SQL thời gian thực; " +
            "UPDATE trả số dòng theo V. now=T. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; " +
            "timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: UPDATE expires_at cho request của L trả1; read-back Q reference GZ0123456789ABCDEF0123.\n" +
            "V02: UPDATE expires_at trả0; read-back vẫn Q/reference như V01.",
            "V01: Audit RENEW_BILL_QR một lần; trả bill có cùng Q/reference; không INSERT payment_request/payment.\n" +
            "V02: Không audit RENEW_BILL_QR; vẫn trả bill; không INSERT payment_request/payment.",
            2,
            () -> new CheckoutFixture(null),
            e -> e.checkout().renew(e.actor,L));
        define("TC-STY-089", "Room Stay Management", "CheckoutService.webhook",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock; ObjectMapper thật. SePaySettings " +
            "thật cấu hình fixture BANK_A/012345/TEST HOLDER/TEST_KEY; authorization=\"Apikey TEST_KEY\". Payload " +
            "id123,gateway=BANK_A,accountNumber=012345,transferType=in,amount220000,transactionDate=2026-10-20 " +
            "10:10:00,code=GZ0123456789ABCDEF0123,content=\"\". Financial account A; request Q " +
            "BILL/PENDING,account_id=A,expected_amount220000,created10:00,expires10:30; các lock có; bank_transaction " +
            "INSERT=1; now=T. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là " +
            "Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Authorization=\"Apikey WRONG\"; payload base hợp lệ.\n" +
            "V02: Authorization=null; payload=null.",
            "V01: BF(401,form); không money/account query,không bankAccount upsert/INSERT bank_transaction/payment/audit.\n" +
            "V02: BF(401,form),không400; không có lời gọi DB/repository.",
            2,
            () -> new CheckoutFixture(new CheckoutDtos.Webhook(123L,"BANK_A","2026-10-20 10:10:00","012345","GZ0123456789ABCDEF0123","","in",money("220000"),null)),
            e -> {
            e.checkout().webhook((String)e.f.getOrDefault("auth","Apikey TEST_KEY"),(CheckoutDtos.Webhook)e.cmd);
            return null;
        });
        define("TC-STY-090", "Room Stay Management", "CheckoutService.webhook",
            "CheckoutService thật; JdbcTemplate/BookingRepository/BookingService mock; ObjectMapper thật. SePaySettings " +
            "thật cấu hình fixture BANK_A/012345/TEST HOLDER/TEST_KEY; authorization=\"Apikey TEST_KEY\". Payload " +
            "id123,gateway=BANK_A,accountNumber=012345,transferType=in,amount220000,transactionDate=2026-10-20 " +
            "10:10:00,code=GZ0123456789ABCDEF0123,content=\"\". Financial account A; request Q " +
            "BILL/PENDING,account_id=A,expected_amount220000,created10:00,expires10:30; các lock có; bank_transaction " +
            "INSERT=1; now=T. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là " +
            "Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Lookup đầu trả Q PENDING; sau khóa queryForMap(Q) trả Q SETTLED; payload vẫn đúng " +
            "ref/account/amount/time; INSERT bank_transaction=1.",
            "V01: Gọi đọc lại Q sau khóa; UPDATE bank_transaction EXCEPTION,không INSERT payment/audit xác nhận; không dựa " +
            "PENDING ở lookup đầu.",
            1,
            () -> new CheckoutFixture(new CheckoutDtos.Webhook(123L,"BANK_A","2026-10-20 10:10:00","012345","GZ0123456789ABCDEF0123","","in",money("220000"),null)),
            e -> {
            e.checkout().webhook((String)e.f.getOrDefault("auth","Apikey TEST_KEY"),(CheckoutDtos.Webhook)e.cmd);
            return null;
        });
    }
    static String id(int n) {
        return String.format("TC-STY-%03d",n);
    }

    private void ok(int n,Consumer<CheckoutFixture> setup,Consumer<CheckoutFixture> verify) {
        valid(id(n),setup,verify);
    }

    private void bad(int n,int status,Consumer<CheckoutFixture> setup) {
        add(id(n),setup,status,"form",e-> {
            e.sqlCount("INSERT INTO gcmb.payment(",0);
        });
    }
    static void full(CheckoutFixture e) {
        e.bill("amount",money("320000"));
        e.bill("depositApplied",money("100000"));
        e.bill("paid",money("200000"));
        e.bill("plannedCash",money("20000"));
        e.bill("plannedCashTendered",money("50000"));
    }
    static void settled(CheckoutFixture e) {
        full(e);
        e.bill("status","SETTLED");
        e.bill("paid",money("220000"));
        e.bill("plannedCash",BigDecimal.ZERO);
    }
    static void completed(CheckoutFixture e,boolean cash) {
        e.sqlCount("INSERT INTO gcmb.payment(",cash?1:0);
        e.sqlCount("UPDATE gcmb.bill SET status='SETTLED'",1);
        e.sqlCount("UPDATE gcmb.room_allocation SET state='FINISHED'",1);
        e.sqlCount("UPDATE gcmb.booking SET status='COMPLETED'",1);
        e.sqlCount("UPDATE gcmb.room SET operating_status='CLEANING'",1);
        e.count(e.repo,"event",1);
    }
    static void noop(CheckoutFixture e) {
        e.noWrites();
        check(e.actual instanceof Map<?,?>,"Expected bill map");
    }
    static Map<String,Object> charge(String amount,String category) {
        return map("chargeId",PR,"orderId",null,"description",category,"quantity",BigDecimal.ONE,"unitPrice",money(amount),"amount",money(amount),"category",category);
    }
    static void issueBase(CheckoutFixture e) {
        e.fixture("booking","plannedEnd",time("10:00:00"));
        e.bill("amount",money("240000"));
    }
    static void issueAmount(CheckoutFixture e,String amount,List<Map<String,Object>> lines) {
        e.f.put("sourceLines",lines);
        e.bill("amount",money(amount));
    }
    static void issued(CheckoutFixture e,String amount,int n) {
        e.sqlCount("INSERT INTO gcmb.bill(",1);
        e.sqlCount("INSERT INTO gcmb.bill_line(",n);
        sqlHas(e,"INSERT INTO gcmb.bill(",money(amount));
        equal(((Map<?,?>)e.actual).get("remaining"),money(amount).subtract((BigDecimal)((Map<?,?>)e.actual).get("depositApplied")),"Remaining");
    }

    private void register() {
        complete();
        issue();
        plan();
        webhook();
        queries();
    }

    private void complete() {
        ok(51,CheckoutManualTest::full,e-> {
            completed(e,true);
            sqlHas(e,"INSERT INTO gcmb.payment(",money("20000"),money("50000"),money("30000"));
        });
        ok(51,e-> {
            full(e);
            e.bill("paid",money("220000"));
            e.bill("plannedCash",BigDecimal.ZERO);
        },e->completed(e,false));
        for(int i=0;i<5;i++) {
            int n=i;
            add(id(52),e-> {
                full(e);
                e.bill("paid",money(n==0?"219999":n==2?"220001":"220000"));
                e.bill("plannedCash",BigDecimal.ZERO);
                if(n==3)e.f.put("allocation",null);
                if(n==4)e.fixture("allocation","state","FINISHED");
            },i==1?0:409,i==1?null:"form",e-> {
                if(n==1)completed(e,false);
                else {
                    e.noWrites();
                    e.sqlCount("UPDATE gcmb.bill SET status='SETTLED'",0);
                }
            });
        }
        ok(82,e-> {
            e.bill("amount",money("320000"));
            e.bill("depositApplied",money("320000"));
        },e-> {
            completed(e,false);
            equal(((Map<?,?>)e.actual).get("remaining"),BigDecimal.ZERO,"All covered by deposit");
        });
        ok(83,CheckoutManualTest::settled,e-> {
            noop(e);
            e.none(e.repo,"currentAllocationForUpdate","event");
            equal(((Map<?,?>)e.actual).get("readyToComplete"),true,"Ready");
        });
    }

    private void issue() {
        bad(31,400,e-> {
            issueBase(e);
            e.cmd=null;
        });
        bad(31,400,e-> {
            issueBase(e);
            e.command("inspected",false);
        });
        ok(31,e-> {
            issueBase(e);
            e.command("inspectionNote","N".repeat(2000));
        },e-> {
            issued(e,"240000",1);
            sqlHas(e,"INSERT INTO gcmb.bill(","N".repeat(2000));
        });
        bad(31,400,e->e.command("inspectionNote","N".repeat(2001)));
        for(int i=0;i<3;i++) {
            int n=i;
            bad(32,409,e-> {
                issueBase(e);
                if(n==0)e.answers.put("sql:FROM gcmb.refund_request WHERE",List.of(Map.of("id",Q)));
                if(n==1)e.answers.put("sql:SELECT id,status FROM gcmb.fb_order",List.of(Map.of("id",Q)));
                if(n==2)e.fixture("booking","plannedEnd",T.minusSeconds(1));
            });
        }
        ok(33,e-> {
            issueBase(e);
            e.f.put("meter",BookingManualTest.meter("RUNNING"));
        },e-> {
            e.count(e.bookingMock,"stopHourlyMeter",1);
            issued(e,"240000",1);
        });
        ok(34,e-> {
            issueBase(e);
            e.cmd=new Issue(true,"Đã kiểm tra",List.of(new Surcharge("Phí",money("20000"))));
            issueAmount(e,"320000",List.of(charge("240000","ROOM"),charge("60000","FNB"),charge("20000","SURCHARGE")));
        },e-> {
            issued(e,"320000",3);
            e.sqlCount("INSERT INTO gcmb.booking_charge(",1);
        });
        for(int i=0;i<13;i++) {
            int n=i;
            boolean good=i==0||i==5||i==12;
            add(id(35),e-> {
                issueBase(e);
                List<Surcharge> fees;
                if(n<=1) {
                    fees=Collections.nCopies(n==0?20:21,new Surcharge("Phí",BigDecimal.ONE));
                    if(n==0) {
                        var ls=new ArrayList<Map<String,Object>>();
                        ls.add(charge("240000","ROOM"));
                        for(int j=0;j<20;j++)ls.add(charge("1","SURCHARGE"));
                        issueAmount(e,"240020",ls);
                    }
                }
                else if(n==2)fees=Arrays.asList((Surcharge)null);
                else {
                    String reason=n==3?null:n==4?"   ":n==5?"R".repeat(180):n==6?"R".repeat(181):"Phí";
                    BigDecimal amount=n==7?null:n==8?BigDecimal.ZERO:n==9?money("-1"):n==10?money("0.5"):n==11?money("1000000000000000000"):n==12?money("999999999999999999"):BigDecimal.ONE;
                    fees=List.of(new Surcharge(reason,amount));
                    if(n==5)issueAmount(e,"240001",List.of(charge("240000","ROOM"),charge("1","SURCHARGE")));
                    if(n==12)issueAmount(e,"999999999999999999",List.of(charge("999999999999999999","SURCHARGE")));
                }
                e.cmd=new Issue(true,null,fees);
            },good?0:400,good?null:"form",e-> {
                if(good) {
                    issued(e,n==0?"240020":n==5?"240001":"999999999999999999",n==0?21:n==5?2:1);
                    e.sqlCount("INSERT INTO gcmb.booking_charge(",n==0?20:1);
                }
                else {
                    e.sqlCount("INSERT INTO gcmb.booking_charge(",0);
                    e.sqlCount("INSERT INTO gcmb.bill(",0);
                }
            });
        }
        ok(37,e-> {
            issueBase(e);
            e.f.put("balance",money("100000"));
            e.bill("depositApplied",money("100000"));
        },e-> {
            e.sqlCount("INSERT INTO gcmb.deposit_entry(",1);
            sqlHas(e,"INSERT INTO gcmb.bill(",money("240000"));
            sqlHas(e,"INSERT INTO gcmb.deposit_entry(",money("100000"));
            e.sqlCount("INSERT INTO gcmb.payment(",0);
        });
        for(int i=0;i<5;i++) {
            int n=i;
            String amount=new String[] {
                "-1","0","239999","240000","240001"
            }
            [i];
            add(id(38),e-> {
                issueBase(e);
                e.f.put("balance",money(amount));
                e.bill("depositApplied",money(amount));
            },i==0||i==4?409:0,i==0||i==4?"form":null,e-> {
                if(n==0||n==4)e.sqlCount("INSERT INTO gcmb.bill(",0);
                else {
                    e.sqlCount("INSERT INTO gcmb.bill(",1);
                    e.sqlCount("INSERT INTO gcmb.deposit_entry(",n==1?0:1);
                }
            });
        }
        ok(39,e->e.answers.put("sql:SELECT id FROM gcmb.bill WHERE booking_id=?",List.of(Map.of("id",L))),CheckoutManualTest::noop);
        bad(95,409,e-> {
            issueBase(e);
            e.f.put("sourceLines",List.of());
        });
    }

    private void plan() {
        BigDecimal[] amounts= {
            money("0"),money("1"),money("219999"),money("220000"),money("220001"),money("20000"),money("20000"),money("20000"),null,money("-1"),money("0.5")
        };
        for(int i=0;i<amounts.length;i++) {
            int n=i;
            boolean good=Set.of(1,2,6,7).contains(i);
            add(id(41),e-> {
                BigDecimal a=amounts[n];
                e.cmd=new PaymentPlan("MIXED",a,n==5?money("19999"):n==7?money("20001"):a);
            },good?0:400,good?null:"form",e-> {
                if(good) {
                    e.sqlCount("UPDATE gcmb.bill SET planned_cash=?",1);
                    e.sqlCount("INSERT INTO gcmb.payment_request(",1);
                    sqlHas(e,"INSERT INTO gcmb.payment_request(",money("220000").subtract(amounts[n]));
                }
                else e.noWrites();
                e.sqlCount("INSERT INTO gcmb.payment(",0);
            });
        }
        for(String method:List.of("BANK_TRANSFER","MIXED"))ok(42,e->e.cmd=new PaymentPlan(method,money("20000"),money("20000")),e-> {
            e.sqlCount("INSERT INTO gcmb.payment_request(",1);
            sqlHas(e,"INSERT INTO gcmb.payment_request(",money(method.equals("MIXED")?"200000":"220000"));
            e.sqlCount("INSERT INTO gcmb.payment(",0);
        });
        bad(63,503,e->e.sepay=new SePaySettings("","","",""));
        for(String tender:List.of("220000","250000"))ok(77,e-> {
            e.sepay=new SePaySettings("","","","");
            e.cmd=new PaymentPlan("CASH",null,money(tender));
        },e-> {
            sqlHas(e,"UPDATE gcmb.bill SET planned_cash=?",money("220000"),money(tender));
            e.sqlCount("INSERT INTO gcmb.payment_request(",0);
            e.sqlCount("INSERT INTO gcmb.payment(",0);
        });
        ok(78,e-> {
            e.bill("plannedCash",money("20000"));
            e.bill("plannedCashTendered",money("50000"));
        },CheckoutManualTest::noop);
        ok(79,e-> {
            e.bill("paid",money("10000"));
            e.answers.put("sql:UNION ALL SELECT id FROM gcmb.payment_request",List.of(Map.of("id",Q)));
        },CheckoutManualTest::noop);
        for(String state:List.of("PENDING","EXPIRED"))ok(80,e->e.answers.put("sql:UNION ALL SELECT id FROM gcmb.payment_request",List.of(Map.of("id",Q,"status",state))),CheckoutManualTest::noop);
        ok(81,e-> {
            e.bill("status","SETTLED");
            e.bill("paid",money("220000"));
            e.cmd=null;
        },CheckoutManualTest::noop);
        for(int changed:new int[] {
            1,0
        })ok(84,e-> {
            e.answers.put("sql:UPDATE gcmb.payment_request SET expires_at",changed);
            qr(e,T.plusSeconds(60));
        },e-> {
            e.sqlCount("INSERT INTO gcmb.audit_event(",changed);
            e.sqlCount("INSERT INTO gcmb.payment_request(",0);
            equal(((Map<?,?>)((Map<?,?>)e.actual).get("request")).get("reference"),"GZ0123456789ABCDEF0123","Same reference");
        });
        for(int n:new int[] {
            0,1
        })ok(85,e-> {
            e.answers.put("sql:UPDATE gcmb.payment_request SET status='CANCELLED'",n==0?1:0);
            if(n==1) {
                e.bill("plannedCash",money("20000"));
                e.bill("plannedCashTendered",money("50000"));
            }
        },e-> {
            e.sqlCount("UPDATE gcmb.payment_request SET status='CANCELLED'",1);
            e.sqlCount("UPDATE gcmb.bill SET planned_cash=0",1);
            e.sqlCount("INSERT INTO gcmb.audit_event(",1);
            e.sqlCount("INSERT INTO gcmb.payment(",0);
        });
        bad(86,409,CheckoutManualTest::settled);
        bad(87,409,e-> {
            e.bill("paid",money("10000"));
            e.answers.put("sql:SELECT id FROM gcmb.payment p WHERE bill_id=?",List.of(Map.of("id",Q)));
        });
        ok(88,e->e.answers.put("sql:UPDATE gcmb.payment_request SET status='CANCELLED'",0),e-> {
            e.sqlCount("UPDATE gcmb.bill SET planned_cash=0",1);
            e.sqlCount("INSERT INTO gcmb.audit_event(",0);
            e.sqlCount("INSERT INTO gcmb.payment(",0);
        });
    }

    private void webhook() {
        ok(44,e-> {
        },e-> {
            matched(e,true);
            sqlHas(e,"INSERT INTO gcmb.payment(",money("220000"),Q,Timestamp.from(time("10:10:00")),"SEPAY-123");
        });
        Object[][] invalid= {
            {
                "$command",null
            }, {
                "id",null
            }, {
                "id",0L
            }, {
                "id",-1L
            }, {
                "transferAmount",null
            }, {
                "transferAmount",BigDecimal.ZERO
            }, {
                "transferAmount",money("-1")
            }, {
                "transferAmount",money("0.5")
            }, {
                "transferAmount",money("1000000000000000000")
            }, {
                "gateway","BANK_B"
            }, {
                "accountNumber","999999"
            }, {
                "transactionDate","bad-date"
            }, {
                "transferType","other"
            }, {
                "transferType",null
            }
        };
        for(var v:invalid)bad(45,400,e-> {
            if(v[0].equals("$command"))e.cmd=null;
            else e.command((String)v[0],v[1]);
        });
        for(int i=0;i<8;i++) {
            int n=i;
            ok(46,e-> {
                switch(n) {
                    case 0->e.f.put("noRequest",true);
                    case 1->e.request("status","SETTLED");
                    case 2->e.request("account_id",id(90));
                    case 3->e.command("transferAmount",money("219999"));
                    case 4->e.command("transferAmount",money("220001"));
                    case 5->e.command("transferType","out");
                    case 6->e.command("transactionDate","2026-10-20 09:59:57");
                    case 7->e.command("transactionDate","2026-10-20 10:30:01");
                }
            },e-> {
                e.sqlCount("INSERT INTO gcmb.bank_transaction(",1);
                e.sqlCount("INSERT INTO gcmb.payment(",0);
                e.sqlCount("UPDATE gcmb.bank_transaction SET match_status='EXCEPTION'",n==0?0:1);
            });
        }
        String[] dates= {
            "09:59:58","09:59:57","10:30:00","10:30:01"
        };
        for(int i=0;i<dates.length;i++) {
            String date=dates[i];
            boolean match=i==0||i==2;
            ok(47,e->e.command("transactionDate","2026-10-20 "+date),e-> {
                matched(e,match);
                if(match)sqlHas(e,"INSERT INTO gcmb.payment(",Timestamp.from(time(date)));
            });
        }
        ok(48,e->e.answers.put("sql:INSERT INTO gcmb.bank_transaction(",0),e-> {
            e.sqlCount("INSERT INTO gcmb.payment(",0);
            e.sqlCount("INSERT INTO gcmb.audit_event(",0);
            e.sqlCount("UPDATE gcmb.bank_transaction",0);
        });
        String ref="GZ0123456789ABCDEF0123",other="GZ"+"A".repeat(20);
        String[][] refs= {
            {
                ref.toLowerCase(),"",ref
            }, {
                null,"TT "+ref+" done",ref
            }, {
                null,"GZ"+"A".repeat(19),""
            }, {
                null,"GZ"+"A".repeat(21),""
            }, {
                null,"GZ"+"A".repeat(19)+"Z",""
            }, {
                null,"X"+ref,""
            }, {
                null,ref+"X",""
            }, {
                null,"("+ref+")",ref
            }, {
                ref,other,ref
            }, {
                "bad",other,other
            }
        };
        for(var v:refs)ok(49,e-> {
            e.command("code",v[0]);
            e.command("content",v[1]);
            if(v[2].isEmpty())e.f.put("noRequest",true);
        },e-> {
            sqlHas(e,"SELECT * FROM gcmb.payment_request WHERE reference_code=?",v[2]);
            if(v[2].isEmpty())e.sqlCount("INSERT INTO gcmb.payment(",0);
        });
        for(String auth:Arrays.asList("Apikey WRONG",null))bad(89,401,e-> {
            e.f.put("auth",auth);
            if(auth==null)e.cmd=null;
        });
        ok(90,e-> {
            var r=new LinkedHashMap<>((Map<String,Object>)e.f.get("request"));
            r.put("status","SETTLED");
            e.f.put("lockedRequest",r);
        },e-> {
            matched(e,false);
            e.sqlCount("SELECT * FROM gcmb.payment_request WHERE id=? FOR UPDATE",1);
        });
        for(int i=0;i<5;i++) {
            int n=i;
            ok(43,e->e.sepay=new SePaySettings(n==0?"":"BANK_A",n==1?"":"012345",n==2?"":"TEST HOLDER",n==3?"":"TEST_KEY"),e->equal(e.actual,n==4,"SePay ready"));
        }
        for(String auth:Arrays.asList(null,"Apikey WRONG","Apikey TEST_KEY"))ok(62,e->e.f.put("auth",auth),e->equal(e.actual,"Apikey TEST_KEY".equals(auth),"Authorization exact match"));
        ok(62,e-> {
            e.sepay=new SePaySettings("","012345","H","TEST_KEY");
            e.f.put("auth","Apikey TEST_KEY");
        },e->equal(e.actual,false,"Incomplete configuration"));
    }
    static void matched(CheckoutFixture e,boolean match) {
        e.sqlCount("INSERT INTO gcmb.payment(",match?1:0);
        e.sqlCount("INSERT INTO gcmb.audit_event(",match?1:0);
        e.sqlCount("UPDATE gcmb.bank_transaction SET match_status='EXCEPTION'",match?0:1);
    }
    static void qr(CheckoutFixture e,Instant expiry) {
        e.answers.put("sql:FROM gcmb.payment_request q JOIN gcmb.financial_account",List.of(map("id",Q,"reference","GZ0123456789ABCDEF0123","amount",money("220000"),"status","PENDING","expiresAt",Timestamp.from(expiry),"bank","BANK_A","account","012345","holder","TEST HOLDER")));
    }

    private void queries() {
        for(boolean pending:new boolean[] {
            false,true
        })ok(30,e-> {
            e.f.put("sourceLines",List.of(charge("240000","ROOM"),charge("60000","FNB")));
            e.f.put("balance",money("100000"));
            if(pending) {
                e.answers.put("sql:SELECT id,status FROM gcmb.fb_order",List.of(Map.of("id",Q)));
                e.answers.put("sql:FROM gcmb.refund_request WHERE",List.of(Map.of("id",Q)));
            }
        },e-> {
            equal(((Map<?,?>)e.actual).get("amount"),money("300000"),"Preview total");
            equal(((Map<?,?>)e.actual).get("hasPendingRefund"),pending,"Pending refund flag");
            equal(((List<?>)((Map<?,?>)e.actual).get("lines")).size(),2,"Preview lines");
            e.noWrites();
        });
        for(int n=0;n<3;n++) {
            int v=n;
            ok(91,e-> {
                full(e);
                e.bill("paid",money(new String[] {
                    "199999","200000","200001"
                }
                    [v]));
            },e-> {
                var result=(Map<?,?>)e.actual;
                equal(result.get("remaining"),money(new String[] {
                    "20001","20000","19999"
                }
                    [v]),"Remaining");
                equal(result.get("readyToComplete"),v==1,"Ready only at equality");
                e.noWrites();
            });
        }
        for(int delta:new int[] {
            -1,0,1
        })ok(92,e-> {
            full(e);
            e.bill("paid",BigDecimal.ZERO);
            e.bill("plannedCash",BigDecimal.ZERO);
            qr(e,T.plusSeconds(delta));
        },e-> {
            var r=(Map<?,?>)((Map<?,?>)e.actual).get("request");
            equal(r.get("expired"),delta<0,"Expiry boundary");
            check(r.get("qrUrl").toString().contains("acc=012345&bank=BANK_A&amount=220000&des=GZ0123456789ABCDEF0123"),"QR values");
            equal(((Map<?,?>)e.actual).get("remaining"),money("220000"),"Remaining");
            e.noWrites();
        });
        bad(93,404,e->e.f.put("billMissing",true));
        ok(94,e-> {
            e.fixture("booking","status","COMPLETED");
            settled(e);
            e.answers.put("sql:SELECT id FROM gcmb.bill WHERE booking_id=? AND branch_id=?",List.of(Map.of("id",L)));
        },e-> {
            noop(e);
            e.none(e.repo,"currentAllocation");
            e.sqlCount("FROM gcmb.booking_charge c WHERE",0);
            equal(((Map<?,?>)e.actual).get("remaining"),BigDecimal.ZERO,"Existing bill");
        });
    }
}
