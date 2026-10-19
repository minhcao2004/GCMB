/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Builds isolated dependencies for the adjacent manual service tests.
 */

package com.gcmb.backend.manual.booking.service;

import com.gcmb.backend.booking.dto.BookingDtos.PriceQuote;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.BookingRepository.AllocationRow;
import com.gcmb.backend.booking.repository.BookingRepository.BookingRow;
import com.gcmb.backend.booking.repository.BookingRepository.BranchScope;
import com.gcmb.backend.booking.repository.BookingRepository.ChargeRow;
import com.gcmb.backend.booking.repository.BookingRepository.ComboRow;
import com.gcmb.backend.booking.repository.BookingRepository.RoomRow;
import com.gcmb.backend.booking.repository.BookingRepository.RoomTypeRow;
import com.gcmb.backend.booking.service.BookingPricingService;
import com.gcmb.backend.booking.service.BookingService;
import com.gcmb.backend.manual.support.ManualFixture;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.mockito.invocation.InvocationOnMock;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public class BookingFixture extends ManualFixture {
    public final BookingRepository repo;
    public final BookingPricingService pricing;
    public final BookingService service;

    public BookingFixture(Object command) {
        super(command);
        errorType=BookingFailure.class;
        f.put("branch",new BranchScope(B,"Branch A",TZ));
        f.put("booking",new BookingRow(K,B,"BK001","An","0901234567",4,"PHONE",time("10:00:00"),time("12:00:00"),time("08:00:00"),null,"IN_HOUSE",null,null,40,U,"",T));
        f.put("allocation",new AllocationRow(AL,K,R,RT,"P01","Room A","Queen","AVAILABLE",time("08:00:00"),time("12:00:00"),"OCCUPIED","",T));
        f.put("room",new RoomRow(R,"P01","Room A",4,"AVAILABLE",RT,"Queen"));
        f.put("target",new RoomRow(R2,"P02","Room B",4,"AVAILABLE",RT2,"King"));
        f.put("roomType",new RoomTypeRow(RT,"QUEEN","Queen","CINEMA",4));
        f.put("combo",new ComboRow(C,"COMBO_A",1,RT,"Gói A",120,money("200000"),money("50000"),money("120000"),time("00:00:00").minus(Duration.ofDays(19)),null));
        f.put("rule",rule(PR,null,"ALL",100,"120000",60,0,1440));
        f.put("rules",List.of(f.get("rule")));
        f.put("quote",new PriceQuote("HOURLY",money("240000"),BigDecimal.ZERO,List.of(line("240000"))));
        f.put("charges",List.of(new ChargeRow(PR,AL,"ROOM","Room",money("2"),money("120000"),money("240000"),"ACTIVE",PR,null,"{\"from\":\"2026-10-20T01:00:00Z\",\"to\":\"2026-10-20T03:00:00Z\",\"pricingMode\":\"HOURLY\",\"billingStepMinutes\":60}",T)));
        f.put("balance",BigDecimal.ZERO);
        f.put("depositId",D);
        f.put("activeTotal",money("240000"));
        f.put("lock",K);
        repo=dependency(BookingRepository.class,this::repository);
        pricing=dependency(BookingPricingService.class,inv-> {
            if(inv.getMethod().getName().equals("quote"))return f.get("quote");
            if(inv.getMethod().getName().equals("meteredRate"))return f.get("rule");
            return RETURNS_DEFAULTS.answer(inv);
        });
        service=new BookingService(repo,pricing,JSON);
    }

    public BookingFixture confirmed() {
        fixture("booking","status","CONFIRMED");
        fixture("allocation","state","RESERVED");
        fixture("allocation","startsAt",time("10:00:00"));
        f.put("charges",List.of(new ChargeRow(PR,AL,"ROOM","Room",money("2"),money("120000"),money("240000"),"ACTIVE",PR,null,"{\"from\":\"2026-10-20T03:00:00Z\",\"to\":\"2026-10-20T05:00:00Z\",\"pricingMode\":\"HOURLY\",\"billingStepMinutes\":60}",T)));
        return this;
    }

    public BookingFixture cancellation() {
        confirmed();
        fixture("booking","plannedStart",T.plusSeconds(2400));
        return this;
    }

    public BookingFixture noShow() {
        confirmed();
        fixture("booking","plannedStart",T);
        return this;
    }

    public BookingFixture roomChange() {
        fixture("booking","plannedEnd",time("10:00:00"));
        fixture("allocation","endsAt",time("10:00:00"));
        return this;
    }

    public BookingFixture editOptions() {
        confirmed();
        f.put("start",time("10:00:00"));
        f.put("end",time("12:00:00"));
        return this;
    }

    public BookingFixture extension() {
        f.put("quote",new PriceQuote("HOURLY",money("120000"),BigDecimal.ZERO,List.of(line("120000"))));
        return this;
    }

    private Object repository(InvocationOnMock i)throws Throwable {
        String n=i.getMethod().getName();
        return configured(n,()->switch(n) {
            case "branchScope"->f.get("branch");
            case "booking"->f.get("booking");
            case "currentAllocation","currentAllocationForUpdate"->f.get("allocation");
            case "lockBookingId"->f.get("lock");
            case "roomType"->f.get("roomType");
            case "roomTypes"->List.of(f.get("roomType"));
            case "rooms","availableRooms","availableRoomsForEdit"->List.of(f.get("room"));
            case "lockAvailableRoom","availableRoom"->Objects.equals(i.getArgument(0),R)?f.get("room"):f.get("target");
            case "combo"->f.get("combo");
            case "combos"->f.getOrDefault("combos",List.of(f.get("combo")));
            case "pricingRules"->f.get("rules");
            case "activeChargesForAllocation"->f.get("charges");
            case "activeChargeTotal"->f.get("activeTotal");
            case "depositBalance","depositReceivedAmount","depositForfeitedAmount"->f.get("balance");
            case "depositCancellationOutcome","depositPaymentMethod"->null;
            case "lockDeposit"->f.get("depositId");
            case "meter"->f.get("meter");
            case "allocations","charges","events","findBookings"->List.of();
            case "countBookings"->0L;
            case "insertBooking"->K;
            case "insertAllocation","insertChangedAllocation"->id(66);
            case "createDepositRefundRequest"->Q;
            case "hasRoomOverlap","roomHasOccupant"->false;
            case "nextRoomBookingStart"->null;
            default-> {
                Class<?> rt=i.getMethod().getReturnType();
                if(rt==void.class)yield null;
                if(rt==int.class)yield 1;
                throw new IllegalStateException("Unarranged BookingRepository."+n);
            }
        });
    }
}
