/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Mocks JDBC observations for branch, catalogue and checkout service tests.
 */

package com.gcmb.backend.manual.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class ManualJdbc {
    private ManualJdbc() {
    }

    public static void attach(ManualFixture fixture) {
        fixture.db=fixture.dependency(JdbcTemplate.class,i->jdbc(fixture,i));
    }

    private static Object jdbc(ManualFixture fixture,InvocationOnMock i)throws Throwable {
        var f=fixture.f;
        var answers=fixture.answers;
        String n=i.getMethod().getName();
        Object[] raw=i.getArguments();
        String sql=raw.length>0 && raw[0] instanceof String s?s:"";
        String flat=sql.replaceAll("\\s+"," ").trim();
        for(var v:answers.entrySet())if(v.getKey().startsWith("sql:")&&flat.contains(v.getKey().substring(4))) {
            if(v.getValue() instanceof Throwable ex)throw ex;
            return v.getValue();
        }
        if(n.equals("execute")&&raw[0] instanceof ConnectionCallback<?> cb) {
            try {
                return cb.doInConnection(connection(fixture));
            }
            catch(SQLException ex) {
                throw ex;
            }
        }
        if(n.equals("update"))return 1;
        if(n.equals("execute"))return null;
        if(n.equals("queryForMap")) {
            if(flat.contains("SELECT u.branch_id,u.status,r.code"))return f.getOrDefault("account",map("code","BRANCH_MANAGER","status","ACTIVE","branch_id",B));
            return f.get("lockedRequest")!=null?f.get("lockedRequest"):f.get("request");
        }
        if(n.equals("queryForObject")) {
            Class<?> type=(Class<?>)raw[1];
            if(type==UUID.class)return A;
            if(type==Boolean.class)return false;
            if(type==Integer.class)return 0;
            if(type==Long.class)return flat.contains("MAX(version)")?1L:0L;
            if(type==String.class)return "HEAD_OFFICE_MANAGER";
        }
        if(n.equals("queryForList")&&raw.length>1&&raw[1] instanceof Class<?>)return List.of(B);
        if(n.equals("queryForList")) {
            if(flat.contains("FROM gcmb.user_account u")&&flat.contains("WHERE u.id=?"))return List.of(f.getOrDefault("account",map("code","BRANCH_MANAGER","status","ACTIVE","branch_id",B)));
            if(flat.contains("FROM gcmb.bill b JOIN gcmb.booking"))return f.getOrDefault("billMissing",false).equals(true)?List.of():List.of(f.get("bill"));
            if(flat.contains("FROM gcmb.payment_request WHERE reference_code"))return f.getOrDefault("noRequest",false).equals(true)?List.of():List.of(f.get("request"));
            if(flat.startsWith("SELECT b.id AS")&&flat.contains("FROM gcmb.branch"))return f.getOrDefault("branchMissing",false).equals(true)?List.of():List.of(f.get("branchMap"));
            if(flat.contains("FROM gcmb.booking_charge c WHERE"))return f.get("sourceLines");
            return List.of();
        }
        if(n.equals("query")) {
            if(flat.startsWith("SELECT id,code,name,mode,capacity,description,active FROM gcmb.room_type"))return List.of(f.get("roomTypeView"));
            if(flat.contains("p.weekdays"))return List.of(f.get("priceRuleView"));
            if(flat.contains("entries WHERE kind")||flat.contains("catalogue WHERE"))return List.of(f.get("priceView"));
            if(flat.contains("FROM gcmb.room_type"))return List.of(f.get("roomTypeView"));
        }
        if(n.equals("toString"))return "Mock JdbcTemplate";
        throw new IllegalStateException("Unarranged SQL call: "+n+" "+flat);
    }

    private static Connection connection(ManualFixture fixture)throws SQLException {
        var f=fixture.f;
        Connection con=mock(Connection.class);
        PreparedStatement ps=mock(PreparedStatement.class);
        java.sql.Array ar=mock(java.sql.Array.class);
        when(con.createArrayOf(anyString(),any(Object[].class))).thenReturn(ar);
        when(con.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeUpdate()).thenReturn(1);
        if(f.containsKey("callbackFailure"))doThrow((Throwable)f.get("callbackFailure")).when(ps).executeUpdate();
        f.put("connection",con);
        f.put("statement",ps);
        f.put("sqlArray",ar);
        return con;
    }
}
