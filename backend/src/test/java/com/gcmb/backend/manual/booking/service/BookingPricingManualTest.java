/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Covers BookingPricing service scenarios using isolated dependencies.
 */

package com.gcmb.backend.manual.booking.service;

import com.gcmb.backend.booking.dto.BookingDtos.PriceQuote;
import com.gcmb.backend.booking.repository.BookingRepository.PricingRule;
import com.gcmb.backend.booking.service.BookingPricingService;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.support.ManualFixture;
import com.gcmb.backend.manual.support.ManualScenarios;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.gcmb.backend.manual.booking.service.BookingManualTest.*;
import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class BookingPricingManualTest extends ManualScenarios<BookingFixture> {
    private BookingPricingManualTest() {
        super("Booking","BookingPricingManualTest");
    }

    public static List<ManualTestSuite> suites() {
        var tests=new BookingPricingManualTest();
        tests.describeCases();
        tests.register();
        return tests.buildSuites();
    }

    private void describeCases() {
        define("TC-BKG-021", "Booking Management", "BookingPricingService.quote",
            "BookingPricingService thật, BookingRepository mock. branchId=B,roomTypeId=RT,timezone Asia/Ho_Chi_Minh; " +
            "start=10:00,end=12:00,mode=HOURLY,comboId=null. Rule PR: " +
            "global,ALL,priority100,validFrom=2026-10-01,validTo=null,from0,to1440,rate120000,step60; list=[PR]. Combo C: " +
            "RT,duration120,price200000,deposit50000,validFrom=2026-10-01T00:00+07:00,validTo=null,version1. Reset " +
            "mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: mode=null,comboId=null.\n" +
            "V02: mode=\"   \",comboId=null.\n" +
            "V03: mode=\" hourly \",comboId=null.\n" +
            "V04: mode=UNKNOWN,comboId=null.\n" +
            "V05: mode=HOURLY,comboId=C.\n" +
            "V06: mode=COMBO,comboId=null.\n" +
            "V07: mode=\" combo \",comboId=C.",
            "V01,V02,V03: Quote HOURLY,amount240000.\n" +
            "V04: BF(400,pricingMode).\n" +
            "V05,V06: BF(400,comboId).\n" +
            "V07: Quote COMBO,amount200000,deposit50000,line/snapshot từ C.",
            7,
            () -> new BookingFixture(null),
            e -> new BookingPricingService(e.repo).quote(RT,B,TZ,(Instant)e.f.getOrDefault("start",time("10:00:00")),(Instant)e.f.getOrDefault("end",time("12:00:00")),(String)e.f.getOrDefault("mode","HOURLY"),(UUID)e.f.get("comboId")));
        define("TC-BKG-022", "Booking Management", "BookingPricingService.quote",
            "BookingPricingService thật, BookingRepository mock. branchId=B,roomTypeId=RT,timezone Asia/Ho_Chi_Minh; " +
            "start=10:00,end=12:00,mode=HOURLY,comboId=null. Rule PR: " +
            "global,ALL,priority100,validFrom=2026-10-01,validTo=null,from0,to1440,rate120000,step60; list=[PR]. Combo C: " +
            "RT,duration120,price200000,deposit50000,validFrom=2026-10-01T00:00+07:00,validTo=null,version1. Tất cả V " +
            "mode=COMBO,comboId=C. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: mode=COMBO,comboId=C; repository.combo(C)=null.\n" +
            "V02: Combo C.roomTypeId=RT2; input RT.\n" +
            "V03: C.validFrom=10:00:01,start10:00,end12:00.\n" +
            "V04: C.validFrom=10:00:00,start10:00,end12:00.\n" +
            "V05: C.validTo=10:00:00,start10:00,end12:00.\n" +
            "V06: C.validTo=10:00:01,start10:00,end12:00.\n" +
            "V07: C valid; end=start+7199s.\n" +
            "V08: C valid; end=start+7201s.",
            "V01,V02: BF(400,comboId).\n" +
            "V03,V05: BF(409,comboId).\n" +
            "V04,V06: COMBO200000,deposit50000; snapshot C.\n" +
            "V07,V08: BF(400,plannedEnd).",
            8,
            () -> new BookingFixture(null),
            e -> new BookingPricingService(e.repo).quote(RT,B,TZ,(Instant)e.f.getOrDefault("start",time("10:00:00")),(Instant)e.f.getOrDefault("end",time("12:00:00")),(String)e.f.getOrDefault("mode","HOURLY"),(UUID)e.f.get("comboId")));
        define("TC-BKG-023", "Booking Management", "BookingPricingService.quote",
            "BookingPricingService thật; BookingRepository mock trả PricingRule/ComboRow fixture. Thời gian và timezone " +
            "được xác định trước.",
            "Tính giá giờ một đoạn\n" +
            "Rule 120000/h; billing step 60; thuê 2h",
            "ExpectedAmount 240000; snapshot có rule/from/to/billedMinutes",
            1,
            () -> new BookingFixture(null),
            e -> new BookingPricingService(e.repo).quote(RT,B,TZ,(Instant)e.f.getOrDefault("start",time("10:00:00")),(Instant)e.f.getOrDefault("end",time("12:00:00")),(String)e.f.getOrDefault("mode","HOURLY"),(UUID)e.f.get("comboId")));
        define("TC-BKG-024", "Booking Management", "BookingPricingService.quote",
            "BookingPricingService thật, BookingRepository mock. branchId=B,roomTypeId=RT,timezone Asia/Ho_Chi_Minh; " +
            "start=10:00,end=12:00,mode=HOURLY,comboId=null. Rule PR: " +
            "global,ALL,priority100,validFrom=2026-10-01,validTo=null,from0,to1440,rate120000,step60; list=[PR]. Combo C: " +
            "RT,duration120,price200000,deposit50000,validFrom=2026-10-01T00:00+07:00,validTo=null,version1. Mọi rule " +
            "global/ALL,priority100,validFrom2026-10-01,validTo=null,step60; list chỉ PR1/PR2; không rule base PR. Reset " +
            "mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: 2026-10-20 10:00–12:00; PR1 window600..660 rate100000; PR2 window660..720 rate200000.\n" +
            "V02: 2026-10-20 23:00 →2026-10-21 01:00; PR1 window1380..1440 rate100000; PR2 window0..60 rate200000.",
            "V01: 2 lines: PR1 60m/100000,PR2 60m/200000; total300000; snapshot đúng2 intervals.\n" +
            "V02: 2 lines: PR1 23:00–00:00/100000,PR2 00:00–01:00/200000; total300000.",
            2,
            () -> new BookingFixture(null),
            e -> new BookingPricingService(e.repo).quote(RT,B,TZ,(Instant)e.f.getOrDefault("start",time("10:00:00")),(Instant)e.f.getOrDefault("end",time("12:00:00")),(String)e.f.getOrDefault("mode","HOURLY"),(UUID)e.f.get("comboId")));
        define("TC-BKG-025", "Booking Management", "BookingPricingService.quote",
            "BookingPricingService thật; BookingRepository mock trả PricingRule/ComboRow fixture. Thời gian và timezone " +
            "được xác định trước.",
            "Làm tròn từng đoạn giá\n" +
            "Rule 120000/h step 15; đoạn 1s/59s/60s/15m/15m1s",
            "actualMinutes làm tròn lên; billedMinutes theo step; 15m giá 30000, 15m1s giá 60000",
            5,
            () -> new BookingFixture(null),
            e -> new BookingPricingService(e.repo).quote(RT,B,TZ,(Instant)e.f.getOrDefault("start",time("10:00:00")),(Instant)e.f.getOrDefault("end",time("12:00:00")),(String)e.f.getOrDefault("mode","HOURLY"),(UUID)e.f.get("comboId")));
        define("TC-BKG-026", "Booking Management", "BookingPricingService.quote",
            "BookingPricingService thật, BookingRepository mock. branchId=B,roomTypeId=RT,timezone Asia/Ho_Chi_Minh; " +
            "start=10:00,end=12:00,mode=HOURLY,comboId=null. Rule PR: " +
            "global,ALL,priority100,validFrom=2026-10-01,validTo=null,from0,to1440,rate120000,step60; list=[PR]. Combo C: " +
            "RT,duration120,price200000,deposit50000,validFrom=2026-10-01T00:00+07:00,validTo=null,version1. Rule có unique" +
            " ID; mọi window0..1440,step60,validFrom2026-10-01. Mỗi V chạy lại với thứ tự list đảo ngược. Reset " +
            "mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. RSE=ResponseStatusException; " +
            "BF=BookingFailure(status,field).",
            "V01: Applicable rules: branch B priority1 ALL rate100000 vs global priority999 ALL rate200000.\n" +
            "V02: Cùng global: priority100 ALL rate100000 vs priority200 ALL rate200000.\n" +
            "V03: Ngày2026-10-24 thứBảy; cùng global/priority100: SPECIFIC(date24) rate300000,WEEKEND200000,ALL100000.\n" +
            "V04: Ngày2026-10-24; cùng global/priority100: WEEKEND200000,ALL100000.",
            "V01: Chọn branch B; ruleId BR,amount200000/2h.\n" +
            "V02: Chọn priority200; ruleId P200,amount400000/2h.\n" +
            "V03: Chọn SPECIFIC; ruleId SP,amount600000/2h.\n" +
            "V04: Chọn WEEKEND; ruleId WE,amount400000/2h.",
            4,
            () -> new BookingFixture(null),
            e -> new BookingPricingService(e.repo).quote(RT,B,TZ,(Instant)e.f.getOrDefault("start",time("10:00:00")),(Instant)e.f.getOrDefault("end",time("12:00:00")),(String)e.f.getOrDefault("mode","HOURLY"),(UUID)e.f.get("comboId")));
        define("TC-BKG-027", "Booking Management", "BookingPricingService.quote",
            "BookingPricingService thật; BookingRepository mock trả PricingRule/ComboRow fixture. Thời gian và timezone " +
            "được xác định trước.",
            "Thiếu rule hoặc hở một đoạn\n" +
            "Không rule; hoặc rule chỉ phủ một phần khoảng đặt",
            "409; không âm thầm tính 0 cho thời gian thiếu giá",
            2,
            () -> new BookingFixture(null),
            e -> new BookingPricingService(e.repo).quote(RT,B,TZ,(Instant)e.f.getOrDefault("start",time("10:00:00")),(Instant)e.f.getOrDefault("end",time("12:00:00")),(String)e.f.getOrDefault("mode","HOURLY"),(UUID)e.f.get("comboId")));
        define("TC-BKG-028", "Booking Management", "BookingPricingService.quote",
            "BookingPricingService thật, BookingRepository mock. branchId=B,roomTypeId=RT,timezone Asia/Ho_Chi_Minh; " +
            "start=10:00,end=12:00,mode=HOURLY,comboId=null. Rule PR: " +
            "global,ALL,priority100,validFrom=2026-10-01,validTo=null,from0,to1440,rate120000,step60; list=[PR]. Combo C: " +
            "RT,duration120,price200000,deposit50000,validFrom=2026-10-01T00:00+07:00,validTo=null,version1. Chỉ rule " +
            "A,rate120000,step1,priority100,global; mặc định ALL,validFrom01/10,validTo=null,window0..1440. V01–V04 chỉ đổi" +
            " date validity; V05–V08 chỉ đổi window. Reset mock/fixture trước mỗi V; chỉ thay dữ liệu nêu ở V. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: PR A validFrom20/10,validTo22/10; start19/10 10:00,end11:00.\n" +
            "V02: PR A như V01; start20/10 10:00,end11:00.\n" +
            "V03: PR A như V01; start21/10 10:00,end11:00.\n" +
            "V04: PR A như V01; start22/10 10:00,end11:00.\n" +
            "V05: PR A window600..660; start09:59,end10:00 ngày20/10.\n" +
            "V06: PR A window600..660; start10:00,end10:01.\n" +
            "V07: PR A window600..660; start10:59,end11:00.\n" +
            "V08: PR A window600..660; start11:00,end11:01.\n" +
            "V09: A WEEKDAY; start20/10 thứBa10:00,end11:00.\n" +
            "V10: A WEEKEND; start24/10 thứBảy10:00,end11:00.\n" +
            "V11: A SPECIFIC specificDate20/10; start21/10 10:00,end11:00.",
            "V01: BF(409,plannedStart); không có applicable rule.\n" +
            "V02,V03,V09,V10: Chọn A; amount120000.\n" +
            "V04,V05,V08,V11: BF(409,plannedStart).\n" +
            "V06,V07: Chọn A; amount2000 với step1.",
            11,
            () -> new BookingFixture(null),
            e -> new BookingPricingService(e.repo).quote(RT,B,TZ,(Instant)e.f.getOrDefault("start",time("10:00:00")),(Instant)e.f.getOrDefault("end",time("12:00:00")),(String)e.f.getOrDefault("mode","HOURLY"),(UUID)e.f.get("comboId")));
        define("TC-BKG-029", "Booking Management", "BookingPricingService.quote",
            "BookingPricingService thật; BookingRepository mock. Gọi quote(RT,A,Asia/Ho_Chi_Minh,start,end,HOURLY,null). " +
            "Khoảng 24/12/2026 10:00–11:00 địa phương (03:00–04:00Z); step=60, các rule cùng scope A, priority=10, window " +
            "0..1440 và cùng hiệu lực. Rule IDs riêng: H=HOLIDAY/180000; S=SPECIFIC ngày24/12/200000; W=WEEKDAY/120000; " +
            "A0=ALL/100000. Lịch lễ do Head Office Manager cấu hình, mock dependency đọc lịch khi contract được bổ sung; " +
            "không tự đặt tên API chưa tồn tại.",
            "V01: lịch có 24/12/2026; rules=[H].\n" +
            "V02: lịch không có 24/12; rules=[H,W].\n" +
            "V03: lịch không có 24/12; rules=[H].\n" +
            "V04: lịch có 24/12; rules=[S,H,W,A0].\n" +
            "V05: lịch có 24/12; rules=[H,W,A0].\n" +
            "V06: lịch có 24/12; rules=[H,A0].",
            "V01: chọn H; 1 charge 60 phút, đơn giá và total=180000.\n" +
            "V02: H không áp dụng; chọn W, total=120000.\n" +
            "V03: BookingFailure(409,plannedStart); không có giá hợp lệ.\n" +
            "V04: chọn S, total=200000.\n" +
            "V05,V06: chọn H, total=180000.\n" +
            "Các ca thành công: snapshot ruleId đúng rule được chọn; depositRequired=0; không chỉ kiểm total. Không có lời " +
            "gọi ghi dữ liệu.",
            6,
            () -> new BookingFixture(null),
            e -> new BookingPricingService(e.repo).quote(RT,B,TZ,(Instant)e.f.getOrDefault("start",time("10:00:00")),(Instant)e.f.getOrDefault("end",time("12:00:00")),(String)e.f.getOrDefault("mode","HOURLY"),(UUID)e.f.get("comboId")));
        define("TC-STY-064", "Room Stay Management", "BookingPricingService.meteredRate",
            "BookingPricingService thật; BookingRepository mock. roomTypeId=RT,branchId=B,timezone Asia/Ho_Chi_Minh,at=T. " +
            "Mọi rule có UUID " +
            "riêng,validFrom2026-10-01,validTo=null,window0..1440,dayKindALL,priority100,billingStepMinutes60 trừ V nêu " +
            "khác. Không mock meteredRate đang kiểm. Mỗi V lặp với list rule đảo thứ tự. pricingRules phải " +
            "nhận(RT,B,localDate,localDate). Chỉ assert rule trả về; không có charge mutation. Reset mock trước mỗi V; chỉ " +
            "thay phần nêu ở V. Tiền là BigDecimal từ chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO " +
            "Instant chuyển từ giờ local. RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: T20/10 thứBa; branch B priority1 rate100000 vs global priority999 rate200000.\n" +
            "V02: Cùng global; priority100 rate100000 vs priority200 rate200000.\n" +
            "V03: at=24/10/2026 09:00 thứBảy; global priority100: SPECIFIC(date24)rate300000,WEEKEND200000,ALL100000.",
            "V01: Trả đúng PricingRule BR thuộc B,rate100000.\n" +
            "V02: Trả PricingRule P200,rate200000.\n" +
            "V03: Trả PricingRule SP,rate300000.",
            3,
            () -> new BookingFixture(null),
            e -> new BookingPricingService(e.repo).meteredRate(RT,B,TZ,(Instant)e.f.getOrDefault("at",time("10:00:00"))));
        define("TC-STY-065", "Room Stay Management", "BookingPricingService.meteredRate",
            "BookingPricingService thật; BookingRepository mock. roomTypeId=RT,branchId=B,timezone Asia/Ho_Chi_Minh,at=T. " +
            "Mọi rule có UUID " +
            "riêng,validFrom2026-10-01,validTo=null,window0..1440,dayKindALL,priority100,billingStepMinutes60 trừ V nêu " +
            "khác. Không mock meteredRate đang kiểm. Reset mock trước mỗi V; chỉ thay phần nêu ở V. Tiền là BigDecimal từ " +
            "chuỗi; timestamp map là Timestamp; snapshot from/to là chuỗi ISO Instant chuyển từ giờ local. " +
            "RSE=ResponseStatusException; BF=BookingFailure(status,field).",
            "V01: pricingRules=[] tại at=T.\n" +
            "V02: List chỉ rule window600..660; at09:00 (minute540).\n" +
            "V03: List chỉ rule window600..660; at11:00 (minute660).",
            "V01,V02,V03: BF(409,pricingMode); không ghi.",
            3,
            () -> new BookingFixture(null),
            e -> new BookingPricingService(e.repo).meteredRate(RT,B,TZ,(Instant)e.f.getOrDefault("at",time("10:00:00"))));
    }
    static void quote(BookingFixture e,String total,UUID rule) {
        var q=(PriceQuote)e.actual;
        equal(q.expectedAmount(),money(total),"Price total");
        if(rule!=null)equal(q.charges().get(0).priceRuleId(),rule,"Selected rule");
        e.noWrites();
    }

    private void register() {
        String[] modes= {
            null,"   "," hourly ","UNKNOWN","HOURLY","COMBO"," combo "
        };
        for(int i=0;i<modes.length;i++) {
            int n=i;
            add(b(21),e-> {
                e.f.put("mode",modes[n]);
                if(n==4||n==6)e.f.put("comboId",C);
            },i==3||i==4||i==5?400:0,i==3?"pricingMode":i==4||i==5?"comboId":null,e-> {
                e.noWrites();
                if(e.failure==null) {
                    quote(e,n==6?"200000":"240000",n==6?null:PR);
                    equal(((PriceQuote)e.actual).depositRequired(),money(n==6?"50000":"0"),"Deposit required");
                }
            });
        }
        for(int i=0;i<8;i++) {
            int n=i;
            int status=i<=1?400:i==2||i==4?409:i>=6?400:0;
            add(b(22),e-> {
                e.f.put("mode","COMBO");
                e.f.put("comboId",C);
                switch(n) {
                    case 0->e.f.put("combo",null);
                    case 1->e.fixture("combo","roomTypeId",RT2);
                    case 2->e.fixture("combo","validFrom",time("10:00:01"));
                    case 3->e.fixture("combo","validFrom",time("10:00:00"));
                    case 4->e.fixture("combo","validTo",time("10:00:00"));
                    case 5->e.fixture("combo","validTo",time("10:00:01"));
                    case 6->e.f.put("end",time("10:00:00").plusSeconds(7199));
                    case 7->e.f.put("end",time("10:00:00").plusSeconds(7201));
                }
            },status,status==0?null:n>=6?"plannedEnd":"comboId",e-> {
                e.noWrites();
                if(e.failure==null) {
                    quote(e,"200000",null);
                    equal(((PriceQuote)e.actual).charges().get(0).comboId(),C,"Combo ID");
                }
            });
        }
        valid(b(23),e-> {
        },e-> {
            quote(e,"240000",PR);
            equal(((PriceQuote)e.actual).charges().size(),1,"One segment");
        });
        for(boolean midnight:new boolean[] {
            false,true
        })valid(b(24),e-> {
            e.f.put("start",time(midnight?"23:00:00":"10:00:00"));
            e.f.put("end",midnight?time("01:00:00").plusSeconds(86400):time("12:00:00"));
            e.f.put("rules",List.of(rule(PR,null,"ALL",100,"100000",60,midnight?1380:600,midnight?1440:660),rule(id(22),null,"ALL",100,"200000",60,midnight?0:660,midnight?60:720)));
        },e-> {
            quote(e,"300000",PR);
            var q=(PriceQuote)e.actual;
            equal(q.charges().size(),2,"Two price segments");
            equal(q.charges().get(1).priceRuleId(),id(22),"Second segment rule");
            equal(q.charges().get(0).amount(),money("100000"),"First amount");
            equal(q.charges().get(1).amount(),money("200000"),"Second amount");
        });
        for(int sec:new int[] {
            1,59,60,900,901
        })valid(b(25),e-> {
            e.f.put("end",time("10:00:00").plusSeconds(sec));
            e.f.put("rules",List.of(rule(PR,null,"ALL",100,"120000",15,0,1440)));
        },e->quote(e,sec<=900?"30000":"60000",PR));
        for(int i=0;i<4;i++) {
            int n=i;
            valid(b(26),e-> {
                var low=rule(PR,n==0?B:null,"ALL",n==0?1:100,"100000",60,0,1440);
                var high=rule(id(22),null,n>=2?"WEEKEND":"ALL",n==0?999:n==1?200:100,"200000",60,0,1440);
                var specific=(PricingRule)change(rule(id(23),null,"SPECIFIC",100,"300000",60,0,1440),"specificDate",LocalDate.of(2026,10,24));
                e.f.put("rules",n==2?List.of(low,high,specific):List.of(low,high));
                if(n>=2) {
                    e.f.put("start",time("10:00:00").plusSeconds(4*86400));
                    e.f.put("end",time("12:00:00").plusSeconds(4*86400));
                }
            },e->quote(e,n==0?"200000":n==2?"600000":"400000",n==0?PR:n==2?id(23):id(22)));
        }
        bad(b(27),e->e.f.put("rules",List.of()),409,"plannedStart");
        bad(b(27),e->e.f.put("rules",List.of(rule(PR,null,"ALL",100,"120000",60,600,660))),409,"plannedStart");
        for(int i=0;i<11;i++) {
            int n=i;
            boolean good=Set.of(1,2,5,6,8,9).contains(i);
            add(b(28),e-> {
                PricingRule r=rule(PR,null,n==8?"WEEKDAY":n==9?"WEEKEND":n==10?"SPECIFIC":"ALL",100,"120000",n==5||n==6?1:60,n>=4&&n<=7?600:0,n>=4&&n<=7?660:1440);
                r=(PricingRule)change(r,"validFrom",LocalDate.of(2026,10,20));
                r=(PricingRule)change(r,"validTo",LocalDate.of(2026,10,22));
                if(n==9)r=(PricingRule)change(r,"validTo",null);
                if(n==10)r=(PricingRule)change(r,"specificDate",LocalDate.of(2026,10,20));
                e.f.put("rules",List.of(r));
                String start=n==4?"09:59:00":n==6?"10:59:00":n==7?"11:00:00":"10:00:00";
                Instant from=time(start).plusSeconds((n==0?-1:n==2||n==10?1:n==3?2:n==9?4:0)*86400L);
                e.f.put("start",from);
                e.f.put("end",from.plusSeconds(n>=4&&n<=7?60:3600));
            },good?0:409,good?null:"plannedStart",e-> {
                e.noWrites();
                if(good)quote(e,n==5||n==6?"2000":"120000",PR);
            });
        }
        // The approved holiday calendar does not exist in the current service contract.
        // Execute quote with the holiday fixture to expose the positive-path discrepancy;
        // do not invent a mocked calendar API or mark the absent calendar cases Passed.
        for(int i=0;i<6;i++) {
            int n=i;
            add(b(29),e-> {
                if(n>=0)throw new ManualFixture.SetupUnavailable("Expected confirmed; HOM holiday calendar dependency/contract is absent, cannot arrange holiday calendar membership.");
                Instant start=LocalDateTime.of(2026,12,24,10,0).atZone(TZ).toInstant();
                e.f.put("start",start);
                e.f.put("end",start.plusSeconds(3600));
                var h=rule(PR,B,"HOLIDAY",10,"180000",60,0,1440);
                var w=rule(id(20),B,"WEEKDAY",10,"120000",60,0,1440);
                var all=rule(id(21),B,"ALL",10,"100000",60,0,1440);
                var sp=(PricingRule)change(rule(id(22),B,"SPECIFIC",10,"200000",60,0,1440),"specificDate",LocalDate.of(2026,12,24));
                e.f.put("rules",n==0?List.of(h):n==3?List.of(sp,h,w,all):n==4?List.of(h,w,all):List.of(h,all));
            },0,null,e->quote(e,n==3?"200000":"180000",n==3?id(22):PR));
        }
        for(int i=0;i<3;i++) {
            int n=i;
            valid(s(64),e-> {
                var low=rule(PR,n==0?B:null,"ALL",n==0?1:100,"100000",60,0,1440);
                var high=rule(id(22),null,n==2?"WEEKEND":"ALL",n==0?999:n==1?200:100,"200000",60,0,1440);
                var sp=(PricingRule)change(rule(id(23),null,"SPECIFIC",100,"300000",60,0,1440),"specificDate",LocalDate.of(2026,10,24));
                e.f.put("rules",n==2?List.of(low,high,sp):List.of(low,high));
                if(n==2)e.f.put("at",time("09:00:00").plusSeconds(4*86400));
            },e-> {
                equal(((PricingRule)e.actual).id(),n==0?PR:n==2?id(23):id(22),"Meter rule selection");
                e.noWrites();
            });
        }
        for(int i=0;i<3;i++) {
            int n=i;
            bad(s(65),e-> {
                e.f.put("rules",n==0?List.of():List.of(rule(PR,null,"ALL",100,"120000",60,600,660)));
                e.f.put("at",time(n==2?"11:00:00":"09:00:00"));
            },409,"pricingMode");
        }
    }
}
