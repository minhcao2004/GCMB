/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: JDBC and ResultSet doubles for manual deposit repository unit tests.
 */
package com.gcmb.backend.manual.booking.repository;

import com.gcmb.backend.booking.repository.DepositRepository;
import com.gcmb.backend.manual.support.ManualFixture;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.*;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class DepositRepositoryFixture extends ManualFixture {
    public final DepositRepository repository;
    public List<Map<String,Object>> rows=new ArrayList<>();
    public DepositRepositoryFixture(){
        super(null);db=dependency(JdbcTemplate.class,this::jdbc);repository=new DepositRepository(db);
        rows.add(map("id",Q,"booking_id",K,"deposit_id",D,"amount",money("100000"),"reason","Hoàn cọc","status","REQUESTED",
            "code","BK001","contact_name","An","contact_phone","0901234567","beneficiary_name","An","bank_name","Bank A","bank_account","012345",
            "created_at",Timestamp.from(T),"paid_at",null,"external_reference",null,
            "booking_code","BK001","customer_name","An","phone","0901234567","room_code","P01","room_name","Room A","check_in_at",Timestamp.from(T),
            "received_amount",money("100000"),"balance",money("100000"),"pending_amount",BigDecimalZero(),"refunded_amount",BigDecimalZero(),"forfeited_amount",BigDecimalZero(),
            "payment_method","MANUAL_TRANSFER","latest_request_status",null,"activity_at",Timestamp.from(T),"can_request_refund",true));
    }
    private static java.math.BigDecimal BigDecimalZero(){return java.math.BigDecimal.ZERO;}
    private Object jdbc(InvocationOnMock call)throws Throwable {
        String method=call.getMethod().getName();String sql=call.getArgument(0);
        if(method.equals("update"))return configured(sql.contains("UPDATE gcmb.refund_request")?"refund.update":"refund.insert",()->1);
        if(method.equals("query")){
            RowMapper<?> mapper=call.getArgument(1);var result=new ArrayList<>();
            for(int index=0;index<rows.size();index++){
                Map<String,Object> values=rows.get(index);
                ResultSet rs=mock(ResultSet.class,invocation->{
                    String getter=invocation.getMethod().getName();Object value=values.get(invocation.getArgument(0));
                    return switch(getter){case "getObject","getTimestamp","getBigDecimal"->value;case "getString"->value==null?null:String.valueOf(value);case "getBoolean"->Boolean.TRUE.equals(value);default->throw new IllegalStateException("Unarranged ResultSet."+getter);};
                });
                result.add(mapper.mapRow(rs,index));
            }
            return result;
        }
        throw new IllegalStateException("Unarranged JdbcTemplate."+method);
    }
}
