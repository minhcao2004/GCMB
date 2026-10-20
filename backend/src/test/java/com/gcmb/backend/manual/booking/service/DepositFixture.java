/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Builds isolated dependencies for the adjacent manual service tests.
 */

package com.gcmb.backend.manual.booking.service;

import com.gcmb.backend.booking.dto.DepositDtos;
import com.gcmb.backend.booking.repository.DepositRepository;
import com.gcmb.backend.booking.service.DepositManagementService;
import com.gcmb.backend.booking.repository.DepositRepository.RefundRequestRow;
import com.gcmb.backend.administration.auth.dto.response.Identity;
import java.math.BigDecimal;
import java.util.List;
import org.mockito.invocation.InvocationOnMock;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class DepositFixture extends BookingFixture {
    public final DepositRepository deposits;
    public final DepositManagementService depositService;

    public DepositFixture(Object command) {
        super(command);
        deposits=dependency(DepositRepository.class,this::depositRepository);
        depositService=new DepositManagementService(repo,deposits);
    }

    public DepositFixture refundRequest() {
        fixture("booking","status","CANCELLED");
        f.put("balance",money("200000"));
        return this;
    }

    public DepositFixture accounting() {
        refundRequest();
        actor=new Identity(U,"accountant","ACCOUNTANT","/",B,"Branch A");
        var request=new RefundRequestRow(Q,K,D,money("100000"),"Hoàn cọc","REQUESTED");
        f.put("initialRefund",request);f.put("lockedRefund",request);f.put("refundItems",List.of());
        return this;
    }

    private Object depositRepository(InvocationOnMock i)throws Throwable {
        return configured("deposit."+i.getMethod().getName(),()->switch(i.getMethod().getName()) {
            case "count"->0L;
            case "list","history","eligibleRefundBookings"->List.of();
            case "canRequestRefund"->true;
            case "item"->f.get("depositItem");
            case "refundRequests"->f.get("refundItems");
            case "refundRequest"->f.get((boolean)i.getArgument(2)?"lockedRefund":"initialRefund");
            case "completeRefundRequest"->true;
            case "summary"->new DepositDtos.DepositSummary(BigDecimal.ZERO,0,BigDecimal.ZERO,0,BigDecimal.ZERO,0,BigDecimal.ZERO,0);
            default->null;
        });
    }
}
