/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Captures isolated manual test inputs, exceptions and dependency interactions.
 */

package com.gcmb.backend.manual.support;

import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.manual.ManualTestSupport;
import java.lang.reflect.UndeclaredThrowableException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.mockito.invocation.Invocation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public class ManualFixture {
    public final Map<String,Object> f=new HashMap<>();
    public final Map<String,Object> answers=new LinkedHashMap<>();
    private final List<Object> mocks=new ArrayList<>();
    public Identity actor=new Identity(U,"staff","RECEPTIONIST","/",B,"Branch A");
    public Instant now=T;
    public Object cmd,actual;
    public Throwable failure;
    protected JdbcTemplate db;
    protected Class<? extends Throwable> errorType=ResponseStatusException.class;

    public ManualFixture(Object command) {
        cmd=command;
    }

    protected <M> M dependency(Class<M> type,org.mockito.stubbing.Answer<?> answer) {
        M mock=mock(type,answer);
        mocks.add(mock);
        return mock;
    }

    protected <M> M dependency(Class<M> type) {
        M mock=mock(type);
        mocks.add(mock);
        return mock;
    }

    public void command(String field,Object value) {
        cmd=change(cmd,field,value);
    }

    public void fixture(String key,String field,Object value) {
        f.put(key,change(f.get(key),field,value));
    }

    protected Object configured(String name,Supplier<Object> fallback)throws Throwable {
        if(answers.containsKey(name)) {
            Object value=answers.get(name);
            if(value instanceof Throwable ex)throw ex;
            return value;
        }
        return fallback.get();
    }

    public String invocations() {
        return ManualTestSupport.renderInvocations(mocks.toArray());
    }
    public static final class SetupUnavailable extends RuntimeException {
        public SetupUnavailable(String reason) {
            super(reason);
        }
    }

    public void expect(int status,String expectedField) {
        if(status==0) {
            check(failure==null,"Expected return; actual exception="+failure);
            return;
        }
        if(status==-3) {
            check(failure instanceof DataIntegrityViolationException,"Expected original constraint exception, actual="+failure);
            return;
        }
        if(status==-4) {
            check(failure instanceof IllegalStateException,"Expected ledger mismatch exception, actual="+failure);
            return;
        }
        if(status==-2) {
            check(failure!=null && (failure instanceof SQLException || failure instanceof org.springframework.dao.DataAccessException || failure instanceof java.lang.reflect.UndeclaredThrowableException),"Expected SQL callback failure, actual="+failure);
            return;
        }
        check(failure!=null,"Expected error "+status+", method returned "+actual);
        check(errorType.isInstance(failure),"Expected "+errorType.getSimpleName()+", actual="+failure.getClass());
        int observed=failure instanceof BookingFailure x?x.status():failure instanceof ResponseStatusException x?x.getStatusCode().value():-1;
        equal(observed,status,"Exception status ("+failure+")");
        if(expectedField!=null) {
            check(failure instanceof BookingFailure,"Expected BookingFailure");
            var errors=ManualTestSupport.errorFields((BookingFailure)failure);
            check(errors.containsKey(expectedField),"Expected error field "+expectedField+", actual="+errors);
        }
    }

    public List<Invocation> inv(Object mock,String name) {
        return mockingDetails(mock).getInvocations().stream().filter(i->i.getMethod().getName().equals(name)).toList();
    }

    public void none(Object mock,String... methods) {
        for(String method:methods)equal(inv(mock,method).size(),0,"Unexpected "+method);
    }

    public void count(Object mock,String method,int n) {
        equal(inv(mock,method).size(),n,"Call count "+method);
    }

    public List<Invocation> sql(String fragment) {
        if(db==null)return List.of();
        return mockingDetails(db).getInvocations().stream().filter(i->i.getArguments().length>0&&i.getArgument(0) instanceof String s&&s.replaceAll("\\s+"," ").contains(fragment)).toList();
    }

    public void sqlCount(String fragment,int n) {
        equal(sql(fragment).size(),n,"SQL call count "+fragment);
    }

    public void noWrites() {
        for(Object mock:mocks)for(var i:mockingDetails(mock).getInvocations()) {
            String n=i.getMethod().getName();
            check(!n.matches("(update|insert.*|update.*|create.*|receive.*|forfeit.*|release.*|cancelBooking|markNoShow|event|changeStatus|delete|startMeter|stopMeter|finishOccupiedAllocation|occupy.*|supersede.*)"),"Unexpected write "+n);
        }
    }
}
