/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Builds isolated dependencies for the adjacent manual service tests.
 */

package com.gcmb.backend.manual.booking.service;

import com.gcmb.backend.booking.service.BookingService;
import com.gcmb.backend.booking.service.CheckoutService;
import com.gcmb.backend.booking.service.SePaySettings;
import com.gcmb.backend.manual.support.ManualJdbc;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class CheckoutFixture extends BookingFixture {
    public final BookingService bookingMock;
    public SePaySettings sepay=new SePaySettings("BANK_A","012345","TEST HOLDER","TEST_KEY");

    public CheckoutFixture(Object command) {
        super(command);
        f.put("bill",map("id",L,"bookingId",K,"status","ISSUED","amount",money("220000"),"depositApplied",BigDecimal.ZERO,"paid",BigDecimal.ZERO,"plannedCash",BigDecimal.ZERO,"plannedCashTendered",BigDecimal.ZERO,"checkoutAt",Timestamp.from(T)));
        f.put("request",map("id",Q,"booking_id",K,"branch_id",B,"bill_id",L,"status","PENDING","account_id",A,"expected_amount",money("220000"),"created_at",Timestamp.from(time("10:00:00")),"expires_at",Timestamp.from(time("10:30:00"))));
        f.put("sourceLines",List.of(map("chargeId",PR,"orderId",null,"description","Room","quantity",money("2"),"unitPrice",money("120000"),"amount",money("240000"),"category","ROOM")));
        ManualJdbc.attach(this);
        bookingMock=dependency(BookingService.class);
    }

    public CheckoutService checkout() {
        return new CheckoutService(db,repo,bookingMock,sepay,JSON);
    }

    public void bill(String key,Object value) {
        ((Map<String,Object>)f.get("bill")).put(key,value);
    }

    public void request(String key,Object value) {
        ((Map<String,Object>)f.get("request")).put(key,value);
    }
}
