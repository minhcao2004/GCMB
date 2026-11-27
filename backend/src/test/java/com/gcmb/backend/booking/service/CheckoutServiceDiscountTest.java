package com.gcmb.backend.booking.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.dto.CheckoutDtos.Issue;
import com.gcmb.backend.booking.dto.CheckoutDtos.Surcharge;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.BookingRepository.AllocationRow;
import com.gcmb.backend.booking.repository.BookingRepository.BookingRow;
import com.gcmb.backend.booking.repository.BookingRepository.BranchScope;
import com.gcmb.backend.booking.repository.CheckoutRepository;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import com.gcmb.backend.inventory.service.FoodOrderService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckoutServiceDiscountTest {
    private final UUID branchId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID depositId = UUID.randomUUID();
    private final Identity actor = new Identity(UUID.randomUUID(), "le-tan", "RECEPTIONIST", "/le-tan", branchId, "Cơ sở");
    private final CheckoutRepository checkoutRepository = mock(CheckoutRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CheckoutService service = new CheckoutService(checkoutRepository, bookingRepository,
        mock(BookingService.class), new SePaySettings("", "", "", ""), objectMapper, mock(FoodOrderService.class),
        mock(FinancialPeriodService.class));

    private Map<String, Object> savedBill;
    private BigDecimal appliedDeposit = ZERO;

    @BeforeEach
    void prepareBooking() {
        Instant checkin = Instant.now().minusSeconds(3600);
        var booking = new BookingRow(bookingId, branchId, "BK-1", "Khách", null, 2,
            "PHONE", checkin, checkin.plusSeconds(7200), checkin, null, "IN_HOUSE", null, null, 40, actor.id(), null, checkin);
        var lines = new ArrayList<>(List.of(line("Tiền phòng", 120500, "ROOM"), line("Đồ uống đã phục vụ", 30000, "FNB")));
        when(bookingRepository.branchScope(actor.id())).thenReturn(new BranchScope(branchId, "Cơ sở", ZoneId.of("Asia/Ho_Chi_Minh")));
        when(bookingRepository.booking(bookingId, branchId)).thenReturn(booking);
        when(bookingRepository.lockDeposit(bookingId)).thenReturn(depositId);
        when(checkoutRepository.lockBookingInBranch(bookingId, branchId)).thenReturn(true);
        when(checkoutRepository.findChargeAndServedOrderLines(bookingId)).thenReturn(lines);
        doAnswer(call -> {
            lines.add(line(call.getArgument(1, String.class), call.getArgument(2, BigDecimal.class).longValueExact(), "SURCHARGE"));
            return null;
        }).when(checkoutRepository).insertSurcharge(eq(bookingId), any(), any());
    }

    @Test
    void discountsAllChargesBeforeDepositAndRoundingAndKeepsIssuedBillOnRetry() {
        stubBillPersistence();
        var quote = previewWithDeposit(50000);
        var surcharges = List.of(new Surcharge("Phụ phí vệ sinh", BigDecimal.valueOf(20000)));
        var result = service.issue(actor, bookingId, issue(quote, "12.50", 99000, surcharges));

        assertEquals(BigDecimal.valueOf(170500), result.get("grossAmount"));
        assertEquals(BigDecimal.valueOf(21313), result.get("discountAmount"));
        assertEquals(BigDecimal.valueOf(149187), result.get("amount"));
        assertEquals(BigDecimal.valueOf(50000), result.get("depositApplied"));
        assertEquals(BigDecimal.valueOf(-187), result.get("roundingAdjustment"));
        assertEquals(BigDecimal.valueOf(99000), result.get("remaining"));

        var repeated = service.issue(actor, bookingId, issue(quote, "50", 99000, surcharges));
        assertEquals(result, repeated);
        verify(checkoutRepository).insertSurcharge(bookingId, "Phụ phí vệ sinh", BigDecimal.valueOf(20000));
        verify(checkoutRepository).insertDepositApplication(eq(depositId), eq(BigDecimal.valueOf(50000)),
            eq((UUID) result.get("id")), eq(actor.id()), any());
        var audit = ArgumentCaptor.forClass(String.class);
        verify(checkoutRepository).insertBookingAudit(eq(actor.id()), eq(branchId), eq("ISSUE_BILL"), eq(bookingId), audit.capture());
        BigDecimal auditedPercent = objectMapper.readTree(audit.getValue()).get("discountPercent").decimalValue();
        assertEquals(0, auditedPercent.compareTo(new BigDecimal("12.50")));
    }

    @Test
    void leavesOldRequestsWithNoPercentageUndiscounted() {
        stubBillPersistence();
        var quote = previewWithDeposit(0);
        var input = objectMapper.readValue("""
            {"inspected":true,"inspectionNote":"","surcharges":[],"overtimeConfirmed":false,"overtimeAmount":0,
             "unservedConfirmed":false,"quotedAt":"%s","quoteToken":"%s","payableAmount":151000}
            """.formatted(quote.get("checkoutAt"), quote.get("quoteToken")), Issue.class);
        assertNull(input.discountPercent());

        var result = service.issue(actor, bookingId, input);

        assertEquals(BigDecimal.valueOf(150500), result.get("amount"));
        assertEquals(ZERO, result.get("discountAmount"));
        assertEquals(BigDecimal.valueOf(151000), result.get("remaining"));
    }

    @Test
    void acceptsFullDiscountWithoutDepositAndMakesBillReadyWithoutCollection() {
        stubBillPersistence();
        var result = service.issue(actor, bookingId, issue(previewWithDeposit(0), "100", 0, List.of()));

        assertEquals(BigDecimal.valueOf(150500), result.get("discountAmount"));
        assertEquals(ZERO, result.get("remaining"));
        assertEquals(true, result.get("readyToComplete"));
    }

    @Test
    void rejectsOversizedGrossEvenWhenFullDiscountWouldLeaveNothingToPay() {
        var surcharges = List.of(new Surcharge("Phụ phí", new BigDecimal("999999999999999999")));
        var failure = assertIssueRejected(issue(previewWithDeposit(0), "100", 0, surcharges), 400);
        assertTrue(failure.getMessage().contains("18 chữ số"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "100.01", "1.001"})
    void rejectsOutOfRangeOrOverprecisePercentage(String rate) {
        assertIssueRejected(issue(previewWithDeposit(0), rate, 151000, List.of()), 400);
    }

    @Test
    void rejectsNegativeDepositBalance() {
        var failure = assertIssueRejected(issue(previewWithDeposit(-1000), "0", 152000, List.of()), 409);
        assertTrue(failure.getMessage().contains("Số dư cọc"));
    }

    @Test
    void appliesOnlyTheBillAmountAndCreatesOneRefundWhenCheckoutCompletes() {
        stubBillPersistence();
        stubCheckoutCompletion();
        var issued = service.issue(actor, bookingId, issue(previewWithDeposit(130000), "20", 0, List.of()));
        UUID billId = (UUID) issued.get("id");
        var refundable = BigDecimal.valueOf(9600);

        assertEquals(BigDecimal.valueOf(120400), issued.get("amount"));
        assertEquals(BigDecimal.valueOf(120400), issued.get("depositApplied"));
        assertEquals(ZERO, issued.get("remaining"));
        assertEquals(refundable, issued.get("depositRefundAmount"));
        verify(bookingRepository, never()).createDepositRefundRequest(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        doAnswer(call -> {
            savedBill.put("depositRefundAmount", refundable);
            savedBill.put("depositRefundStatus", "REQUESTED");
            return UUID.randomUUID();
        }).when(bookingRepository).createDepositRefundRequest(eq(branchId), eq(bookingId), eq(depositId), eq(refundable),
            eq("Hoàn cọc dư khi checkout"), eq("Khách"), eq(""), eq(""), eq(actor.id()), eq(billId));

        var settled = service.complete(actor, billId);
        assertEquals("SETTLED", settled.get("status"));
        assertEquals("REQUESTED", settled.get("depositRefundStatus"));
        assertEquals(refundable, settled.get("depositRefundAmount"));
        assertEquals(settled, service.complete(actor, billId));
        verify(bookingRepository).createDepositRefundRequest(branchId, bookingId, depositId, refundable,
            "Hoàn cọc dư khi checkout", "Khách", "", "", actor.id(), billId);
        verify(checkoutRepository).settleBill(billId);
    }

    @Test
    void doesNotCreateARefundWhenTheDepositEqualsTheBill() {
        stubBillPersistence();
        stubCheckoutCompletion();
        var issued = service.issue(actor, bookingId, issue(previewWithDeposit(150500), "0", 0, List.of()));

        assertEquals(ZERO, issued.get("remaining"));
        assertEquals(ZERO, issued.get("depositRefundAmount"));
        assertEquals("SETTLED", service.complete(actor, (UUID) issued.get("id")).get("status"));
        verify(bookingRepository, never()).createDepositRefundRequest(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsPayableAmountCalculatedWithoutTheEnteredDiscount() {
        var failure = assertIssueRejected(issue(previewWithDeposit(0), "10", 151000, List.of()), 409);
        assertTrue(failure.getMessage().contains("Báo giá đã thay đổi"));
    }

    private Map<String, Object> previewWithDeposit(long amount) {
        when(bookingRepository.depositBalance(bookingId)).thenAnswer(call -> BigDecimal.valueOf(amount).subtract(appliedDeposit));
        return service.preview(actor, bookingId);
    }

    private static Issue issue(Map<String, Object> quote, String rate, long payable, List<Surcharge> surcharges) {
        return new Issue(true, "", surcharges, false, ZERO, false, (Instant) quote.get("checkoutAt"),
            (String) quote.get("quoteToken"), BigDecimal.valueOf(payable), new BigDecimal(rate));
    }

    private BookingFailure assertIssueRejected(Issue input, int status) {
        var failure = assertThrows(BookingFailure.class, () -> service.issue(actor, bookingId, input));
        assertEquals(status, failure.status());
        verify(checkoutRepository, never()).insertBill(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(checkoutRepository, never()).insertDepositApplication(any(), any(), any(), any(), any());
        return failure;
    }

    private void stubBillPersistence() {
        doAnswer(call -> {
            savedBill = new LinkedHashMap<>(Map.of("id", call.getArgument(0, UUID.class), "grossAmount", call.getArgument(4, BigDecimal.class),
                "discountAmount", call.getArgument(5, BigDecimal.class), "amount", call.getArgument(6, BigDecimal.class),
                "roundingAdjustment", call.getArgument(7, BigDecimal.class), "checkoutAt", call.getArgument(8, Timestamp.class),
                "paid", ZERO, "plannedCash", ZERO, "bookingId", bookingId, "customer", "Khách"));
            savedBill.put("status", "ISSUED");
            return null;
        }).when(checkoutRepository).insertBill(any(), eq(bookingId), eq(branchId), any(), any(), any(), any(), any(), any(), any(), eq(actor.id()));
        doAnswer(call -> {
            appliedDeposit = call.getArgument(1, BigDecimal.class);
            return null;
        }).when(checkoutRepository).insertDepositApplication(any(), any(), any(), any(), any());
        when(checkoutRepository.findBillIdsByBooking(bookingId)).thenAnswer(call ->
            savedBill == null ? List.of() : List.of(Map.of("id", savedBill.get("id"))));
        when(checkoutRepository.findBill(any(), eq(branchId))).thenAnswer(call -> {
            var bill = new LinkedHashMap<>(savedBill);
            bill.put("depositApplied", appliedDeposit);
            return List.of(bill);
        });
    }

    private void stubCheckoutCompletion() {
        Instant checkin = Instant.now().minusSeconds(3600);
        when(bookingRepository.currentAllocationForUpdate(bookingId)).thenReturn(new AllocationRow(UUID.randomUUID(),
            bookingId, UUID.randomUUID(), UUID.randomUUID(), "P1", "Phòng 1", "Hạng 1", "AVAILABLE",
            checkin, checkin.plusSeconds(7200), "OCCUPIED", null, checkin));
        doAnswer(call -> {
            savedBill.put("status", "SETTLED");
            return null;
        }).when(checkoutRepository).settleBill(any());
    }

    private static Map<String, Object> line(String description, long amount, String category) {
        return Map.of("description", description, "category", category, "quantity", BigDecimal.ONE,
            "unitPrice", BigDecimal.valueOf(amount), "amount", BigDecimal.valueOf(amount));
    }
}
