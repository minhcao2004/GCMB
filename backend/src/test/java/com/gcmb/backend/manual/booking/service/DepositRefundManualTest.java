/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Manual unit observations for the Accountant's deposit refund workflow.
 */
package com.gcmb.backend.manual.booking.service;

import com.gcmb.backend.booking.dto.DepositDtos.*;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.DepositRepository.RefundRequestRow;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.support.ManualScenarios;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.dao.DataIntegrityViolationException;
import static com.gcmb.backend.manual.support.ManualTestData.*;

public final class DepositRefundManualTest extends ManualScenarios<DepositFixture> {
    private static final String PRE="UC-44; actor ACCOUNTANT/U, scope B/Asia/Ho_Chi_Minh; service thật, BookingRepository/DepositRepository mock. Booking K CANCELLED, deposit D; initial/locked request Q REQUESTED amount100000, balance200000, completeRefundRequest=true. Request reference=TXREF. Không DB/HTTP/ngân hàng; reset fixture trước mỗi V.";
    private DepositRefundManualTest(){super("Booking","DepositRefundManualTest");}
    public static List<ManualTestSuite> suites(){var t=new DepositRefundManualTest();t.register();return t.buildSuites();}
    private static RefundRequestRow row(String status,String amount){return new RefundRequestRow(Q,K,D,money(amount),"Hoàn cọc",status);}
    private static Example<DepositFixture> ok(String input,String expected,Consumer<DepositFixture> setup,Consumer<DepositFixture> inspect){return new Example<>(input,expected,setup,0,null,inspect);}
    private static Example<DepositFixture> rejected(String input,int status,String field,Consumer<DepositFixture> setup){return new Example<>(input,"BF("+status+","+field+"); không gọi ghi nhận hoàn",setup,status,field,e->{e.none(e.deposits,"completeRefundRequest");e.noWrites();});}
    private void group(int n,List<Example<DepositFixture>> variants){
        examples(key("DEP",n),"Deposit Management",n<28?"DepositManagementService.refundRequests":"DepositManagementService.completeRefund",PRE,
            ()->new DepositFixture(new CompleteRefundRequest("TXREF")).accounting(),
            e->n<28?e.depositService.refundRequests(e.actor):n==41?repeat(e):e.depositService.completeRefund(e.actor,(UUID)e.f.getOrDefault("requestId",Q),(CompleteRefundRequest)e.cmd),variants);
    }
    private static void completed(DepositFixture e){
        var result=(RefundCompletionResult)e.actual;var locked=(RefundRequestRow)e.f.get("lockedRefund");
        equal(result.id(),locked.id(),"Completed request");equal(result.bookingId(),locked.bookingId(),"Completed booking");equal(result.amount(),locked.amount(),"Completed amount");equal(result.status(),"PAID","Completed status");
        e.count(e.deposits,"completeRefundRequest",1);var call=e.inv(e.deposits,"completeRefundRequest").get(0);
        equal(call.getArgument(0),B,"Refund scope");equal(call.getArgument(1),locked,"Locked request passed to writer");equal(call.getArgument(2),U,"Accountant actor");equal(call.getArgument(3),((CompleteRefundRequest)e.cmd).externalReference().trim(),"Reference after trim");
        e.count(e.deposits,"refundRequest",2);
        var reads=e.inv(e.deposits,"refundRequest");equal(reads.get(0).getArgument(2),false,"Initial unlocked read");equal(reads.get(1).getArgument(2),true,"Locked read");
        check(e.inv(e.repo,"lockDeposit").get(0).getSequenceNumber()<reads.get(1).getSequenceNumber(),"Lock deposit before reading locked request");
        check(reads.get(1).getSequenceNumber()<e.inv(e.repo,"depositBalance").get(0).getSequenceNumber(),"Read balance after locked request");
        e.none(e.repo,"receiveDeposit","createDepositRefundRequest","event");
    }
    private static Object repeat(DepositFixture e){
        var first=e.depositService.completeRefund(e.actor,Q,(CompleteRefundRequest)e.cmd);
        e.f.put("initialRefund",row("PAID","100000"));
        try{e.depositService.completeRefund(e.actor,Q,(CompleteRefundRequest)e.cmd);return Map.of("first",first,"second","unexpected success");}
        catch(BookingFailure failure){return Map.of("first",first,"secondStatus",failure.status(),"secondError",failure.getMessage());}
    }
    private void register(){
        var list=new ArrayList<Example<DepositFixture>>();
        for(boolean empty:new boolean[]{true,false})list.add(ok("refundRequests="+(empty?"[]":"[F_request]"),"Queue branchName=Branch A, đúng list fixture; repository nhận B; không ghi",e->e.f.put("refundItems",empty?List.of():List.of(new RefundRequestItem(Q,K,"BK001","An","0901234567",money("100000"),"Hoàn cọc","An","Bank A","012345","REQUESTED",T,null,null))),e->{
            var queue=(RefundRequestQueue)e.actual;equal(queue.branchName(),"Branch A","Queue branch name");equal(queue.items(),e.f.get("refundItems"),"Queue items");equal(Arrays.asList(e.inv(e.deposits,"refundRequests").get(0).getArguments()),List.of(B),"Queue scope");e.noWrites();}));
        group(26,list);
        group(27,List.of(new Example<>("branchScope=null","BF(403,form); không gọi refundRequests",e->e.f.put("branch",null),403,"form",e->{e.none(e.deposits,"refundRequests");e.noWrites();})));
        list=new ArrayList<>();
        for(String status:List.of("REQUESTED","APPROVED","PROCESSING"))list.add(ok("initial/locked status="+status,"PAID; đúng DTO, locked row/branch/actor/reference; writer gọi1 lần",e->{e.f.put("initialRefund",row(status,"100000"));e.f.put("lockedRefund",row(status,"100000"));},DepositRefundManualTest::completed));
        group(28,list);
        list=new ArrayList<>();list.add(rejected("requestId=null",400,"requestId",e->e.f.put("requestId",null)));list.add(rejected("request=null",400,"externalReference",e->e.cmd=null));
        for(String ref:Arrays.asList(null,"","   ","R","RR","R".repeat(199),"  "+"R".repeat(200)+"  ","R".repeat(201))){
            boolean invalid=ref==null||ref.isBlank()||ref.trim().length()>200;
            Consumer<DepositFixture> setup=e->e.cmd=new CompleteRefundRequest(ref);
            String label=ref==null?"null":ref.length()>20?(ref.startsWith(" ")?"\"  \" + R×200 + \"  \"":"R×"+ref.length()):"\""+ref+"\"";
            list.add(invalid?rejected("reference="+label,400,"externalReference",setup):ok("reference="+label,"PAID; reference trim đúng và không vượt200 ký tự",setup,DepositRefundManualTest::completed));
        }
        group(29,list);
        group(30,List.of(rejected("request hợp lệ, branchScope=null",403,"form",e->e.f.put("branch",null))));
        group(31,List.of(rejected("initial request không tồn tại trong scope B",404,"requestId",e->e.f.put("initialRefund",null))));
        list=new ArrayList<>();for(String status:List.of("PAID","REJECTED","FAILED"))list.add(rejected("initial.status="+status,409,"requestId",e->e.f.put("initialRefund",row(status,"100000"))));group(32,list);
        group(33,List.of(rejected("lockBookingId=null",404,"bookingId",e->e.f.put("lock",null)),rejected("booking=null",404,"bookingId",e->e.f.put("booking",null))));
        list=new ArrayList<>();for(String status:List.of("DRAFT","CONFIRMED","IN_HOUSE","COMPLETED","NO_SHOW"))list.add(rejected("booking.status="+status,409,"requestId",e->e.fixture("booking","status",status)));group(34,list);
        group(35,List.of(rejected("lockDeposit=null",409,"requestId",e->e.f.put("depositId",null)),rejected("lockDeposit khác initial.depositId",409,"requestId",e->e.f.put("depositId",id(98)))));
        group(36,List.of(rejected("initial có nhưng locked request=null",404,"requestId",e->e.f.put("lockedRefund",null))));
        list=new ArrayList<>();for(String status:List.of("PAID","REJECTED","FAILED"))list.add(rejected("initial REQUESTED; locked.status="+status,409,"requestId",e->e.f.put("lockedRefund",row(status,"100000"))));group(37,list);
        list=new ArrayList<>();for(String balance:List.of("-1","0","99999","100000","100001")){
            boolean invalid=money(balance).compareTo(money("100000"))<0;Consumer<DepositFixture> setup=e->e.f.put("balance",money(balance));
            list.add(invalid?rejected("amount100000,balance="+balance,409,"requestId",setup):ok("amount100000,balance="+balance,"PAID, không vượt số dư",setup,DepositRefundManualTest::completed));
        }
        list.add(ok("locked amount100000.00,balance100000","PAID; so sánh số tiền không phụ thuộc scale",e->{e.f.put("lockedRefund",row("REQUESTED","100000.00"));e.f.put("balance",money("100000"));},DepositRefundManualTest::completed));
        list.add(rejected("initial amount100000,locked100001,balance100000",409,"requestId",e->{e.f.put("lockedRefund",row("REQUESTED","100001"));e.f.put("balance",money("100000"));}));
        group(38,list);
        group(39,List.of(new Example<>("completeRefundRequest=false","BF(409,requestId), không trả PAID",e->e.answers.put("deposit.completeRefundRequest",false),409,"requestId",e->e.count(e.deposits,"completeRefundRequest",1))));
        group(40,List.of(new Example<>("repository đọc initial ném lỗi","Giữ nguyên exception, writer không được gọi",e->e.answers.put("deposit.refundRequest",new DataIntegrityViolationException("read failure")),-3,null,e->{e.none(e.deposits,"completeRefundRequest");equal(e.failure,e.answers.get("deposit.refundRequest"),"Original read failure");}),
            new Example<>("repository ghi nhận hoàn ném lỗi","Giữ nguyên exception; không trả thành công, không suy ra rollback",e->e.answers.put("deposit.completeRefundRequest",new DataIntegrityViolationException("write failure")),-3,null,e->{e.count(e.deposits,"completeRefundRequest",1);equal(e.failure,e.answers.get("deposit.completeRefundRequest"),"Original write failure");})));
        group(41,List.of(ok("Lần1 REQUESTED thành công; lần2 fixture initial trảPAID","Lần1 DTO PAID; lần2 409; chỉ một lời gọi ghi nhận hoàn",e->{},e->{Map<?,?> result=(Map<?,?>)e.actual;equal(((RefundCompletionResult)result.get("first")).status(),"PAID","First completion");equal(result.get("secondStatus"),409,"Repeat rejected");e.count(e.deposits,"completeRefundRequest",1);})));
    }
}
