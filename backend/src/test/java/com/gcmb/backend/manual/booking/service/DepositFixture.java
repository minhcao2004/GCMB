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

    private Object depositRepository(InvocationOnMock i)throws Throwable {
        return configured("deposit."+i.getMethod().getName(),()->switch(i.getMethod().getName()) {
            case "count"->0L;
            case "list","history","eligibleRefundBookings"->List.of();
            case "canRequestRefund"->true;
            case "item"->f.get("depositItem");
            case "summary"->new DepositDtos.DepositSummary(BigDecimal.ZERO,0,BigDecimal.ZERO,0,BigDecimal.ZERO,0,BigDecimal.ZERO,0);
            default->null;
        });
    }
}
