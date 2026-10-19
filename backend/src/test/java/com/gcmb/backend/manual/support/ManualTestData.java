/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Shared fixed values and explicit DTO copies for manual tests.
 */

package com.gcmb.backend.manual.support;

import com.gcmb.backend.administration.branch.dto.BranchDtos.BranchCommand;
import com.gcmb.backend.administration.branch.dto.BranchDtos.StatusCommand;
import com.gcmb.backend.administration.branch.dto.RoomCommand;
import com.gcmb.backend.administration.branch.dto.RoomStatusCommand;
import com.gcmb.backend.administration.pricing.dto.PriceCommand;
import com.gcmb.backend.administration.pricing.dto.PriceRuleCommand;
import com.gcmb.backend.administration.pricing.dto.RoomTypeCommand;
import com.gcmb.backend.administration.pricing.dto.RoomTypeView;
import com.gcmb.backend.booking.dto.BookingDtos.CancelBookingRequest;
import com.gcmb.backend.booking.dto.BookingDtos.ChargeLine;
import com.gcmb.backend.booking.dto.BookingDtos.CreateBookingRequest;
import com.gcmb.backend.booking.dto.BookingDtos.ExtendStayRequest;
import com.gcmb.backend.booking.dto.BookingDtos.RoomChangeRequest;
import com.gcmb.backend.booking.dto.BookingDtos.UpdateBookingRequest;
import com.gcmb.backend.booking.dto.CheckoutDtos.Issue;
import com.gcmb.backend.booking.dto.CheckoutDtos.PaymentPlan;
import com.gcmb.backend.booking.dto.CheckoutDtos.Surcharge;
import com.gcmb.backend.booking.dto.CheckoutDtos.Webhook;
import com.gcmb.backend.booking.dto.DepositDtos.CreateRefundRequest;
import com.gcmb.backend.booking.repository.BookingRepository.AllocationRow;
import com.gcmb.backend.booking.repository.BookingRepository.BookingRow;
import com.gcmb.backend.booking.repository.BookingRepository.ChargeRow;
import com.gcmb.backend.booking.repository.BookingRepository.ComboRow;
import com.gcmb.backend.booking.repository.BookingRepository.MeterRow;
import com.gcmb.backend.booking.repository.BookingRepository.PricingRule;
import com.gcmb.backend.booking.repository.BookingRepository.RoomRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.*;

public final class ManualTestData {
    private ManualTestData() {
    }
    public static final UUID B=id(1), U=id(2), K=id(3), R=id(4), RT=id(5), AL=id(6), C=id(7), Q=id(8), D=id(9), A=id(10), R2=id(11), RT2=id(12), PR=id(13), M=id(14), L=id(15);
    public static final Instant T=Instant.parse("2026-10-20T02:00:00Z");
    public static final ZoneId TZ=ZoneId.of("Asia/Ho_Chi_Minh");
    public static final ObjectMapper JSON=new ObjectMapper();

    public static UUID id(int n) {
        return new UUID(0,n);
    }

    public static BigDecimal money(String s) {
        return new BigDecimal(s);
    }

    public static Instant time(String s) {
        return LocalDateTime.parse("2026-10-20T"+s).atZone(TZ).toInstant();
    }

    public static void check(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }

    public static void equal(Object actual,Object expected,String message) {
        if(actual instanceof BigDecimal a && expected instanceof BigDecimal b)check(a.compareTo(b)==0,message+": expected="+b+", actual="+a);
        else check(Objects.equals(actual,expected),message+": expected="+expected+", actual="+actual);
    }

    public static Object field(Object record,String field) {
        try {
            return record instanceof Map<?,?> m?m.get(field):record.getClass().getMethod(field).invoke(record);
        }
        catch(Exception ex) {
            throw new AssertionError("Cannot inspect "+field,ex);
        }
    }

    public static PricingRule rule(UUID id,UUID branch,String day,int priority,String rate,int step,int from,int to) {
        return new PricingRule(
            id,
            "Rule "+id,
            branch,
            LocalDate.of(2026,10,1),
            null,
            day,
            null,
            from,
            to,
            money(rate),
            money(rate),
            step,
            priority);
    }

    public static ChargeLine line(String amount) {
        return new ChargeLine(
            "ROOM",
            "Room",
            money("2"),
            money("120000"),
            money(amount),
            PR,
            null,
            Map.of("pricingMode","HOURLY"));
    }

    public static Map<String,Object> map(Object... pairs) {
        var m=new LinkedHashMap<String,Object>();
        for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);
        return m;
    }

    public static String key(String prefix,int n) {
        return "TC-"+prefix+"-"+String.format("%03d",n);
    }

    public static Object[] missing() {
        return new Object[] {
            null,"","   "
        };
    }

    public static void sqlHas(ManualFixture e,String fragment,Object... expected) {
        var calls=e.sql(fragment);
        check(!calls.isEmpty(),"Missing SQL "+fragment);
        var args=Arrays.asList(calls.get(0).getArguments());
        for(Object v:expected)check(args.contains(v),"Missing SQL arg "+v+" in "+args);
    }
/** Copy one field for boundary data while keeping constructor contracts checked by javac. */

    public static Object change(Object record,String field,Object value) {
        if(record instanceof BranchCommand r) {
            if(!Set.of("code","name","address","phone","notes").contains(field)) throw new IllegalArgumentException("Unknown BranchCommand field "+field);
            return new BranchCommand(
                field.equals("code")?(String)value:r.code(),
                field.equals("name")?(String)value:r.name(),
                field.equals("address")?(String)value:r.address(),
                field.equals("phone")?(String)value:r.phone(),
                field.equals("notes")?(String)value:r.notes());
        }
        if(record instanceof StatusCommand r) {
            if(!Set.of("status","reason").contains(field)) throw new IllegalArgumentException("Unknown StatusCommand field "+field);
            return new StatusCommand(field.equals("status")?(String)value:r.status(), field.equals("reason")?(String)value:r.reason());
        }
        if(record instanceof RoomCommand r) {
            if(!Set.of("code","name","floorNumber","roomTypeId","capacity","description").contains(field)) throw new IllegalArgumentException("Unknown RoomCommand field "+field);
            return new RoomCommand(
                field.equals("code")?(String)value:r.code(),
                field.equals("name")?(String)value:r.name(),
                field.equals("floorNumber")?(Integer)value:r.floorNumber(),
                field.equals("roomTypeId")?(UUID)value:r.roomTypeId(),
                field.equals("capacity")?(Integer)value:r.capacity(),
                field.equals("description")?(String)value:r.description());
        }
        if(record instanceof RoomStatusCommand r) {
            if(!Set.of("status","reason","expectedReadyAt").contains(field)) throw new IllegalArgumentException("Unknown RoomStatusCommand field "+field);
            return new RoomStatusCommand(field.equals("status")?(String)value:r.status(), field.equals("reason")?(String)value:r.reason(), field.equals("expectedReadyAt")?(OffsetDateTime)value:r.expectedReadyAt());
        }
        if(record instanceof RoomTypeCommand r) {
            if(!Set.of("code","name","serviceType","capacity","description").contains(field)) throw new IllegalArgumentException("Unknown RoomTypeCommand field "+field);
            return new RoomTypeCommand(
                field.equals("code")?(String)value:r.code(),
                field.equals("name")?(String)value:r.name(),
                field.equals("serviceType")?(String)value:r.serviceType(),
                field.equals("capacity")?(Integer)value:r.capacity(),
                field.equals("description")?(String)value:r.description());
        }
        if(record instanceof PriceRuleCommand r) {
            if(!Set.of("code","name","roomTypeId","weekdays","validFrom","validTo","minuteFrom","minuteTo","hourlyPrice").contains(field)) throw new IllegalArgumentException("Unknown PriceRuleCommand field "+field);
            return new PriceRuleCommand(
                field.equals("code")?(String)value:r.code(),
                field.equals("name")?(String)value:r.name(),
                field.equals("roomTypeId")?(UUID)value:r.roomTypeId(),
                field.equals("weekdays")?(List<Integer>)value:r.weekdays(),
                field.equals("validFrom")?(LocalDate)value:r.validFrom(),
                field.equals("validTo")?(LocalDate)value:r.validTo(),
                field.equals("minuteFrom")?(Integer)value:r.minuteFrom(),
                field.equals("minuteTo")?(Integer)value:r.minuteTo(),
                field.equals("hourlyPrice")?(BigDecimal)value:r.hourlyPrice());
        }
        if(record instanceof PriceCommand r) {
            if(!Set.of("roomTypeId","code","name","description","durationMinutes","minuteFrom","minuteTo","price").contains(field)) throw new IllegalArgumentException("Unknown PriceCommand field "+field);
            return new PriceCommand(
                field.equals("roomTypeId")?(UUID)value:r.roomTypeId(),
                field.equals("code")?(String)value:r.code(),
                field.equals("name")?(String)value:r.name(),
                field.equals("description")?(String)value:r.description(),
                field.equals("durationMinutes")?(Integer)value:r.durationMinutes(),
                field.equals("minuteFrom")?(Integer)value:r.minuteFrom(),
                field.equals("minuteTo")?(Integer)value:r.minuteTo(),
                field.equals("price")?(BigDecimal)value:r.price());
        }
        if(record instanceof CreateBookingRequest r) {
            if(!Set.of("roomId","plannedStart","plannedEnd","pricingMode","comboId","contactName","contactPhone","channel","note","guestCount","depositAmount","paymentMethod","paymentReference").contains(field)) throw new IllegalArgumentException("Unknown CreateBookingRequest field "+field);
            return new CreateBookingRequest(
                field.equals("roomId")?(UUID)value:r.roomId(),
                field.equals("plannedStart")?(Instant)value:r.plannedStart(),
                field.equals("plannedEnd")?(Instant)value:r.plannedEnd(),
                field.equals("pricingMode")?(String)value:r.pricingMode(),
                field.equals("comboId")?(UUID)value:r.comboId(),
                field.equals("contactName")?(String)value:r.contactName(),
                field.equals("contactPhone")?(String)value:r.contactPhone(),
                field.equals("channel")?(String)value:r.channel(),
                field.equals("note")?(String)value:r.note(),
                field.equals("guestCount")?(Integer)value:r.guestCount(),
                field.equals("depositAmount")?(BigDecimal)value:r.depositAmount(),
                field.equals("paymentMethod")?(String)value:r.paymentMethod(),
                field.equals("paymentReference")?(String)value:r.paymentReference());
        }
        if(record instanceof UpdateBookingRequest r) {
            if(!Set.of("roomId","plannedStart","plannedEnd","pricingMode","comboId","contactName","contactPhone","note").contains(field)) throw new IllegalArgumentException("Unknown UpdateBookingRequest field "+field);
            return new UpdateBookingRequest(
                field.equals("roomId")?(UUID)value:r.roomId(),
                field.equals("plannedStart")?(Instant)value:r.plannedStart(),
                field.equals("plannedEnd")?(Instant)value:r.plannedEnd(),
                field.equals("pricingMode")?(String)value:r.pricingMode(),
                field.equals("comboId")?(UUID)value:r.comboId(),
                field.equals("contactName")?(String)value:r.contactName(),
                field.equals("contactPhone")?(String)value:r.contactPhone(),
                field.equals("note")?(String)value:r.note());
        }
        if(record instanceof CancelBookingRequest r) {
            if(!Set.of("reason","beneficiaryName","bankAccount","bankName").contains(field)) throw new IllegalArgumentException("Unknown CancelBookingRequest field "+field);
            return new CancelBookingRequest(
                field.equals("reason")?(String)value:r.reason(),
                field.equals("beneficiaryName")?(String)value:r.beneficiaryName(),
                field.equals("bankAccount")?(String)value:r.bankAccount(),
                field.equals("bankName")?(String)value:r.bankName());
        }
        if(record instanceof RoomChangeRequest r) {
            if(!Set.of("targetRoomId","cause","reason").contains(field)) throw new IllegalArgumentException("Unknown RoomChangeRequest field "+field);
            return new RoomChangeRequest(field.equals("targetRoomId")?(UUID)value:r.targetRoomId(), field.equals("cause")?(String)value:r.cause(), field.equals("reason")?(String)value:r.reason());
        }
        if(record instanceof ExtendStayRequest r) {
            if(!Set.of("pricingMode","comboId","hours").contains(field)) throw new IllegalArgumentException("Unknown ExtendStayRequest field "+field);
            return new ExtendStayRequest(field.equals("pricingMode")?(String)value:r.pricingMode(), field.equals("comboId")?(UUID)value:r.comboId(), field.equals("hours")?(Integer)value:r.hours());
        }
        if(record instanceof Issue r) {
            if(!Set.of("inspected","inspectionNote","surcharges").contains(field)) throw new IllegalArgumentException("Unknown Issue field "+field);
            return new Issue(field.equals("inspected")?(Boolean)value:r.inspected(), field.equals("inspectionNote")?(String)value:r.inspectionNote(), field.equals("surcharges")?(List<Surcharge>)value:r.surcharges());
        }
        if(record instanceof PaymentPlan r) {
            if(!Set.of("method","cashAmount","cashTendered").contains(field)) throw new IllegalArgumentException("Unknown PaymentPlan field "+field);
            return new PaymentPlan(field.equals("method")?(String)value:r.method(), field.equals("cashAmount")?(BigDecimal)value:r.cashAmount(), field.equals("cashTendered")?(BigDecimal)value:r.cashTendered());
        }
        if(record instanceof Webhook r) {
            if(!Set.of("id","gateway","transactionDate","accountNumber","code","content","transferType","transferAmount","referenceCode").contains(field)) throw new IllegalArgumentException("Unknown Webhook field "+field);
            return new Webhook(
                field.equals("id")?(Long)value:r.id(),
                field.equals("gateway")?(String)value:r.gateway(),
                field.equals("transactionDate")?(String)value:r.transactionDate(),
                field.equals("accountNumber")?(String)value:r.accountNumber(),
                field.equals("code")?(String)value:r.code(),
                field.equals("content")?(String)value:r.content(),
                field.equals("transferType")?(String)value:r.transferType(),
                field.equals("transferAmount")?(BigDecimal)value:r.transferAmount(),
                field.equals("referenceCode")?(String)value:r.referenceCode());
        }
        if(record instanceof CreateRefundRequest r) {
            if(!Set.of("bookingId","beneficiaryName","bankName","bankAccount","reason").contains(field)) throw new IllegalArgumentException("Unknown CreateRefundRequest field "+field);
            return new CreateRefundRequest(
                field.equals("bookingId")?(UUID)value:r.bookingId(),
                field.equals("beneficiaryName")?(String)value:r.beneficiaryName(),
                field.equals("bankName")?(String)value:r.bankName(),
                field.equals("bankAccount")?(String)value:r.bankAccount(),
                field.equals("reason")?(String)value:r.reason());
        }
        if(record instanceof BookingRow r) {
            if(!Set.of("id","branchId","code","contactName","contactPhone","guestCount","channel","plannedStart","plannedEnd","actualCheckin","actualCheckout","status","cancelledAt","cancellationReason","refundCutoffMinutes","createdBy","note","createdAt").contains(field)) throw new IllegalArgumentException("Unknown BookingRow field "+field);
            return new BookingRow(
                field.equals("id")?(UUID)value:r.id(),
                field.equals("branchId")?(UUID)value:r.branchId(),
                field.equals("code")?(String)value:r.code(),
                field.equals("contactName")?(String)value:r.contactName(),
                field.equals("contactPhone")?(String)value:r.contactPhone(),
                field.equals("guestCount")?(Integer)value:r.guestCount(),
                field.equals("channel")?(String)value:r.channel(),
                field.equals("plannedStart")?(Instant)value:r.plannedStart(),
                field.equals("plannedEnd")?(Instant)value:r.plannedEnd(),
                field.equals("actualCheckin")?(Instant)value:r.actualCheckin(),
                field.equals("actualCheckout")?(Instant)value:r.actualCheckout(),
                field.equals("status")?(String)value:r.status(),
                field.equals("cancelledAt")?(Instant)value:r.cancelledAt(),
                field.equals("cancellationReason")?(String)value:r.cancellationReason(),
                field.equals("refundCutoffMinutes")?(Integer)value:r.refundCutoffMinutes(),
                field.equals("createdBy")?(UUID)value:r.createdBy(),
                field.equals("note")?(String)value:r.note(),
                field.equals("createdAt")?(Instant)value:r.createdAt());
        }
        if(record instanceof AllocationRow r) {
            if(!Set.of("id","bookingId","roomId","roomTypeId","roomCode","roomName","roomTypeName","operatingStatus","startsAt","endsAt","state","changeReason","createdAt").contains(field)) throw new IllegalArgumentException("Unknown AllocationRow field "+field);
            return new AllocationRow(
                field.equals("id")?(UUID)value:r.id(),
                field.equals("bookingId")?(UUID)value:r.bookingId(),
                field.equals("roomId")?(UUID)value:r.roomId(),
                field.equals("roomTypeId")?(UUID)value:r.roomTypeId(),
                field.equals("roomCode")?(String)value:r.roomCode(),
                field.equals("roomName")?(String)value:r.roomName(),
                field.equals("roomTypeName")?(String)value:r.roomTypeName(),
                field.equals("operatingStatus")?(String)value:r.operatingStatus(),
                field.equals("startsAt")?(Instant)value:r.startsAt(),
                field.equals("endsAt")?(Instant)value:r.endsAt(),
                field.equals("state")?(String)value:r.state(),
                field.equals("changeReason")?(String)value:r.changeReason(),
                field.equals("createdAt")?(Instant)value:r.createdAt());
        }
        if(record instanceof RoomRow r) {
            if(!Set.of("id","code","name","capacity","operatingStatus","roomTypeId","roomTypeName").contains(field)) throw new IllegalArgumentException("Unknown RoomRow field "+field);
            return new RoomRow(
                field.equals("id")?(UUID)value:r.id(),
                field.equals("code")?(String)value:r.code(),
                field.equals("name")?(String)value:r.name(),
                field.equals("capacity")?(Integer)value:r.capacity(),
                field.equals("operatingStatus")?(String)value:r.operatingStatus(),
                field.equals("roomTypeId")?(UUID)value:r.roomTypeId(),
                field.equals("roomTypeName")?(String)value:r.roomTypeName());
        }
        if(record instanceof ComboRow r) {
            if(!Set.of("id","code","version","roomTypeId","name","durationMinutes","price","depositRequired","overtimeHourlyPrice","validFrom","validTo").contains(field)) throw new IllegalArgumentException("Unknown ComboRow field "+field);
            return new ComboRow(
                field.equals("id")?(UUID)value:r.id(),
                field.equals("code")?(String)value:r.code(),
                field.equals("version")?(Integer)value:r.version(),
                field.equals("roomTypeId")?(UUID)value:r.roomTypeId(),
                field.equals("name")?(String)value:r.name(),
                field.equals("durationMinutes")?(Integer)value:r.durationMinutes(),
                field.equals("price")?(BigDecimal)value:r.price(),
                field.equals("depositRequired")?(BigDecimal)value:r.depositRequired(),
                field.equals("overtimeHourlyPrice")?(BigDecimal)value:r.overtimeHourlyPrice(),
                field.equals("validFrom")?(Instant)value:r.validFrom(),
                field.equals("validTo")?(Instant)value:r.validTo());
        }
        if(record instanceof PricingRule r) {
            if(!Set.of("id","name","branchId","validFrom","validTo","dayKind","specificDate","minuteFrom","minuteTo","hourlyPrice","overtimeHourlyPrice","billingStepMinutes","priority").contains(field)) throw new IllegalArgumentException("Unknown PricingRule field "+field);
            return new PricingRule(
                field.equals("id")?(UUID)value:r.id(),
                field.equals("name")?(String)value:r.name(),
                field.equals("branchId")?(UUID)value:r.branchId(),
                field.equals("validFrom")?(LocalDate)value:r.validFrom(),
                field.equals("validTo")?(LocalDate)value:r.validTo(),
                field.equals("dayKind")?(String)value:r.dayKind(),
                field.equals("specificDate")?(LocalDate)value:r.specificDate(),
                field.equals("minuteFrom")?(Integer)value:r.minuteFrom(),
                field.equals("minuteTo")?(Integer)value:r.minuteTo(),
                field.equals("hourlyPrice")?(BigDecimal)value:r.hourlyPrice(),
                field.equals("overtimeHourlyPrice")?(BigDecimal)value:r.overtimeHourlyPrice(),
                field.equals("billingStepMinutes")?(Integer)value:r.billingStepMinutes(),
                field.equals("priority")?(Integer)value:r.priority());
        }
        if(record instanceof ChargeRow r) {
            if(!Set.of("id","allocationId","kind","description","quantity","unitPrice","amount","status","priceRuleId","comboId","snapshot","createdAt").contains(field)) throw new IllegalArgumentException("Unknown ChargeRow field "+field);
            return new ChargeRow(
                field.equals("id")?(UUID)value:r.id(),
                field.equals("allocationId")?(UUID)value:r.allocationId(),
                field.equals("kind")?(String)value:r.kind(),
                field.equals("description")?(String)value:r.description(),
                field.equals("quantity")?(BigDecimal)value:r.quantity(),
                field.equals("unitPrice")?(BigDecimal)value:r.unitPrice(),
                field.equals("amount")?(BigDecimal)value:r.amount(),
                field.equals("status")?(String)value:r.status(),
                field.equals("priceRuleId")?(UUID)value:r.priceRuleId(),
                field.equals("comboId")?(UUID)value:r.comboId(),
                field.equals("snapshot")?(String)value:r.snapshot(),
                field.equals("createdAt")?(Instant)value:r.createdAt());
        }
        if(record instanceof MeterRow r) {
            if(!Set.of("id","allocationId","priceRuleId","startedAt","billedFrom","originalAllocationEnd","reservedUntil","hourlyPrice","status","stoppedAt","billedMinutes","amount").contains(field)) throw new IllegalArgumentException("Unknown MeterRow field "+field);
            return new MeterRow(
                field.equals("id")?(UUID)value:r.id(),
                field.equals("allocationId")?(UUID)value:r.allocationId(),
                field.equals("priceRuleId")?(UUID)value:r.priceRuleId(),
                field.equals("startedAt")?(Instant)value:r.startedAt(),
                field.equals("billedFrom")?(Instant)value:r.billedFrom(),
                field.equals("originalAllocationEnd")?(Instant)value:r.originalAllocationEnd(),
                field.equals("reservedUntil")?(Instant)value:r.reservedUntil(),
                field.equals("hourlyPrice")?(BigDecimal)value:r.hourlyPrice(),
                field.equals("status")?(String)value:r.status(),
                field.equals("stoppedAt")?(Instant)value:r.stoppedAt(),
                field.equals("billedMinutes")?(Integer)value:r.billedMinutes(),
                field.equals("amount")?(BigDecimal)value:r.amount());
        }
        if(record instanceof RoomTypeView r) {
            if(!Set.of("id","code","name","serviceType","capacity","description","active").contains(field)) throw new IllegalArgumentException("Unknown RoomTypeView field "+field);
            return new RoomTypeView(
                field.equals("id")?(UUID)value:r.id(),
                field.equals("code")?(String)value:r.code(),
                field.equals("name")?(String)value:r.name(),
                field.equals("serviceType")?(String)value:r.serviceType(),
                field.equals("capacity")?(Integer)value:r.capacity(),
                field.equals("description")?(String)value:r.description(),
                field.equals("active")?(Boolean)value:r.active());
        }
        throw new IllegalArgumentException("Unsupported test record: "+record);
    }
}
