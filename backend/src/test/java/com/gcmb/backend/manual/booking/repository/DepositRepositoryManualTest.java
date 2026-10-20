/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Manual unit checks for deposit refund SQL parameters and row mapping.
 */
package com.gcmb.backend.manual.booking.repository;

import com.gcmb.backend.booking.dto.DepositDtos.*;
import com.gcmb.backend.booking.repository.DepositRepository.RefundRequestRow;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.support.ManualScenarios;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.dao.DataIntegrityViolationException;
import static com.gcmb.backend.manual.support.ManualTestData.*;

public final class DepositRepositoryManualTest extends ManualScenarios<DepositRepositoryFixture> {
    private static final String PRE="UC-44/Deposit Management. DepositRepository thật; JdbcTemplate và ResultSet mock. Row Q/K/D,amount100000,reason Hoàn cọc,status REQUESTED; UPDATE/INSERT mặc định1. Kiểm tra SQL/params và gọi RowMapper thật trên ResultSet double; không DB, không chứng minh rollback/race/sắp xếp PostgreSQL.";
    private DepositRepositoryManualTest(){super("Booking","DepositRepositoryManualTest");}
    public static List<ManualTestSuite> suites(){var t=new DepositRepositoryManualTest();t.register();return t.buildSuites();}
    private static RefundRequestRow request(){return new RefundRequestRow(Q,K,D,money("100000"),"Hoàn cọc","REQUESTED");}
    private static Example<DepositRepositoryFixture> ok(String input,String expected,Consumer<DepositRepositoryFixture> setup,Consumer<DepositRepositoryFixture> inspect){return new Example<>(input,expected,setup,0,null,inspect);}
    private void group(int n,List<Example<DepositRepositoryFixture>> examples){
        String method=n==42?"refundRequests":n==43?"refundRequest":n==48?"list":"completeRefundRequest";
        examples(key("DEP",n),"Deposit Management","DepositRepository."+method,PRE,DepositRepositoryFixture::new,
            e->switch(n){
                case 42->e.repository.refundRequests(B);
                case 43->e.repository.refundRequest(B,Q,(boolean)e.f.getOrDefault("lock",false));
                case 48->e.repository.list(B,LocalDate.of(2026,10,20),LocalDate.of(2026,10,21),TZ,"HELD","MANUAL_TRANSFER","An",(int)e.f.getOrDefault("page",0),25);
                default->e.repository.completeRefundRequest(B,request(),U,"TXREF");
            },examples);
    }
    private static List<Object> parameters(DepositRepositoryFixture e,String sql,int from){var args=Arrays.asList(e.sql(sql).get(0).getArguments());return args.subList(from,args.size());}
    private static void refundSql(DepositRepositoryFixture e){
        equal(parameters(e,"UPDATE gcmb.refund_request",1),List.of(U,"TXREF",Q,B),"Update parameters");
        String sql=e.sql("UPDATE gcmb.refund_request").get(0).getArgument(0);
        check(sql.contains("status='PAID'")&&sql.contains("kind='DEPOSIT'")&&sql.contains("'REQUESTED','APPROVED','PROCESSING'"),"Update state/kind guards");
    }
    private void register(){
        var list=new ArrayList<Example<DepositRepositoryFixture>>();
        for(int state=0;state<3;state++){
            int v=state;
            list.add(ok("rows="+(v==0?"[]":v==1?"REQUESTED paidAt/ref=null":"PAID paidAt=T/ref=TXREF"),"Trả đúng list và đầy đủ mapping; SQL branch B,kind DEPOSIT, ưu tiên trạng thái mở",e->{if(v==0)e.rows=List.of();else if(v==2){e.rows.get(0).put("status","PAID");e.rows.get(0).put("paid_at",java.sql.Timestamp.from(T));e.rows.get(0).put("external_reference","TXREF");}},e->{
                var actual=(List<?>)e.actual;equal(actual.size(),v==0?0:1,"Refund queue row count");
                if(v!=0)equal(actual.get(0),new RefundRequestItem(Q,K,"BK001","An","0901234567",money("100000"),"Hoàn cọc","An","Bank A","012345",v==1?"REQUESTED":"PAID",T,v==1?null:T,v==1?null:"TXREF"),"Refund queue mapping");
                equal(parameters(e,"FROM gcmb.refund_request r",2),List.of(B),"Queue scope param");
                String sql=e.sql("FROM gcmb.refund_request r").get(0).getArgument(0);check(sql.contains("r.kind='DEPOSIT'")&&sql.contains("'REQUESTED','APPROVED','PROCESSING'")&&sql.contains("r.created_at DESC,r.id DESC"),"Queue SQL scope/order");e.noWrites();
            }));
        }
        group(42,list);
        list=new ArrayList<>();for(boolean lock:new boolean[]{false,true})for(boolean empty:new boolean[]{false,true})list.add(ok("lock="+lock+",empty="+empty,"Không hàng trảnull; có hàng map đúng; FOR UPDATE chỉ khi lock=true",e->{e.f.put("lock",lock);if(empty)e.rows=List.of();},e->{
            equal(e.actual,empty?null:request(),"Refund request mapping");equal(parameters(e,"WHERE branch_id=? AND id=?",2),List.of(B,Q),"Request scope/id params");
            String sql=e.sql("WHERE branch_id=? AND id=?").get(0).getArgument(0);equal(sql.contains("FOR UPDATE"),lock,"Lock clause");check(sql.contains("kind='DEPOSIT'"),"Deposit kind guard");e.noWrites();
        }));group(43,list);
        group(44,List.of(ok("UPDATE=1,INSERT=1","true; đủ params, deterministic key, UPDATE trước INSERT",e->{},e->{
            equal(e.actual,true,"Recorded");refundSql(e);equal(parameters(e,"INSERT INTO gcmb.deposit_entry",1),List.of(D,money("100000"),Q,U,"Hoàn cọc","DEPOSIT-REFUND-"+Q),"Refund entry parameters");
            String sql=e.sql("INSERT INTO gcmb.deposit_entry").get(0).getArgument(0);check(sql.contains("'REFUND'"),"Refund ledger kind");e.sqlCount("UPDATE gcmb.refund_request",1);e.sqlCount("INSERT INTO gcmb.deposit_entry",1);
            check(e.sql("UPDATE gcmb.refund_request").get(0).getSequenceNumber()<e.sql("INSERT INTO gcmb.deposit_entry").get(0).getSequenceNumber(),"Update before insert");
        })));
        group(45,List.of(ok("UPDATE=0","false; không INSERT REFUND",e->e.answers.put("refund.update",0),e->{equal(e.actual,false,"Concurrent update declined");refundSql(e);e.sqlCount("INSERT INTO gcmb.deposit_entry",0);})));
        group(46,List.of(new Example<>("UPDATE=1,INSERT=0","IllegalStateException; không trảtrue, không suy ra rollback",e->e.answers.put("refund.insert",0),-4,null,e->{refundSql(e);e.sqlCount("INSERT INTO gcmb.deposit_entry",1);equal(e.failure.getMessage(),"Deposit refund entry was not recorded","Missing entry error");})));
        list=new ArrayList<>();for(String stage:List.of("refund.update","refund.insert"))list.add(new Example<>(stage+" ném DataIntegrityViolationException","Giữ nguyên exception; lỗi UPDATE không được gọi INSERT",e->e.answers.put(stage,new DataIntegrityViolationException(stage)), -3,null,e->{equal(e.failure,e.answers.get(stage),"Original JDBC failure");e.sqlCount("INSERT INTO gcmb.deposit_entry",stage.equals("refund.update")?0:1);}));group(47,list);
        list=new ArrayList<>();for(int page:new int[]{0,2})list.add(ok("page="+page+",size25,scopeB,date20–21/10,statusHELD,methodMANUAL_TRANSFER,searchAn","Mapping DepositItem đúng; priority/order SQL và mọi filter/limit/offset đúng",e->{e.f.put("page",page);e.rows.get(0).put("status","HELD");},e->{
            var actual=(List<?>)e.actual;equal(actual.size(),1,"Deposit list size");
            equal(actual.get(0),new DepositItem(K,"BK001","An","0901234567","P01","Room A",T,money("100000"),money("100000"),money("100000"),money("0"),money("0"),money("0"),"MANUAL_TRANSFER","HELD",null,T,true),"Deposit item mapping");
            String sql=e.sql("ORDER BY CASE v.status").get(0).getArgument(0);check(sql.contains("WHEN 'PENDING_REFUND' THEN 0 WHEN 'REFUNDED' THEN 1 WHEN 'HELD' THEN 2 WHEN 'FORFEITED' THEN 3 ELSE 4 END")&&sql.contains("v.activity_at DESC NULLS LAST,v.booking_code LIMIT ? OFFSET ?"),"Deposit priority SQL");
            equal(parameters(e,"ORDER BY CASE v.status",2),List.of(B,LocalDate.of(2026,10,20),TZ.getId(),LocalDate.of(2026,10,20),LocalDate.of(2026,10,21),TZ.getId(),LocalDate.of(2026,10,21),"HELD","HELD","MANUAL_TRANSFER","MANUAL_TRANSFER","An","An",25,(long)page*25),"Deposit list query args");e.noWrites();
        }));group(48,list);
    }
}
