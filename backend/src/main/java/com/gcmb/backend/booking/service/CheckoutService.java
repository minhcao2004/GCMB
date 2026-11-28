/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Chốt giá đã xác nhận, ghi bill/thanh toán và kết thúc sử dụng phòng.
 * Updated: 15/10/2026 - Áp dụng cọc đã thu, chỉ thanh toán phần còn lại.
 */
package com.gcmb.backend.booking.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.booking.dto.CheckoutDtos.*;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.CheckoutRepository;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import com.gcmb.backend.inventory.service.FoodOrderService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class CheckoutService {

    private final byte[] quoteKey = new java.security.SecureRandom().generateSeed(32);
    private final CheckoutRepository checkoutRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final SePaySettings sePaySettings;
    private final ObjectMapper objectMapper;
    private final FoodOrderService foodOrderService;
    private final FinancialPeriodService financialPeriodService;

    public CheckoutService(
        CheckoutRepository checkoutRepository,
        BookingRepository bookingRepository,
        BookingService bookingService,
        SePaySettings sePaySettings,
        ObjectMapper objectMapper,
        FoodOrderService foodOrderService,
        FinancialPeriodService financialPeriodService
    ) {
        this.checkoutRepository = checkoutRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.sePaySettings = sePaySettings;
        this.objectMapper = objectMapper;
        this.foodOrderService = foodOrderService;
        this.financialPeriodService = financialPeriodService;
    }

    private Map<String, Object> depositStatus(Identity actor, UUID bookingId) {
        UUID branchId = branch(actor, "thu tiền cọc");
        lockBooking(bookingId, branchId, "thu tiền cọc");
        var booking = bookingRepository.booking(bookingId, branchId);
        BigDecimal received = bookingRepository.depositReceivedAmount(bookingId);
        var result = new LinkedHashMap<String, Object>();
        result.put("bookingId", bookingId);
        result.put("bookingCode", booking.code());
        result.put("amount", BookingPricingService.depositFor(bookingRepository.activeChargeTotal(bookingId)));
        result.put("receivedAmount", received);
        result.put("status", received.signum() > 0 ? "SETTLED" : "AWAITING_CONFIRMATION");
        return result;
    }

    // HuyDV: lễ tân xác nhận cọc chuyển khoản ngoài hệ thống, giữ mức cọc 30%.
    @Transactional
    public Map<String, Object> confirmDeposit(Identity actor, UUID bookingId, String reference, BigDecimal reviewedAmount) {
        UUID branchId = branch(actor, "thu tiền cọc");
        lockBooking(bookingId, branchId, "thu tiền cọc");
        bookingRepository.requireActiveBranch(branchId);
        var booking = bookingRepository.booking(bookingId, branchId);
        if (bookingRepository.depositReceivedAmount(bookingId).signum() > 0) {
            return depositStatus(actor, bookingId);
        }
        if (!"CONFIRMED".equals(booking.status())) throw fail(409, "Chỉ xác nhận cọc cho booking chưa nhận phòng.");
        String content = reference == null ? "" : reference.trim();
        if (content.isEmpty() || content.length() > 120) throw fail(400, "Nhập mã giao dịch đã nhận cọc, tối đa 120 ký tự.");
        BigDecimal amount = BookingPricingService.depositFor(bookingRepository.activeChargeTotal(bookingId));
        if (amount.signum() <= 0 || reviewedAmount == null || amount.compareTo(reviewedAmount) != 0) {
            throw fail(409, "Số tiền cọc đã thay đổi. Vui lòng tải lại trước khi xác nhận.");
        }
        java.time.LocalDate businessDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        financialPeriodService.requireOpen(businessDate, "ghi nhận tiền cọc");
        var period = checkoutRepository.ensureAndLockFinancialPeriod(businessDate.withDayOfMonth(1));
        if (!"OPEN".equals(period.get("status"))) throw fail(409, "Kỳ tài chính đã đóng, chưa thể ghi nhận cọc.");
        // HuyDV: sổ chuyển khoản ngoài hệ thống, không yêu cầu cấu hình tài khoản ngân hàng thực tế.
        String accountCode = "EXTERNAL-DEPOSIT-" + branchId;
        UUID accountId = checkoutRepository.ensureExternalDepositAccount(accountCode, branchId);
        checkoutRepository.cancelOpenDepositRequests(bookingId);
        bookingRepository.receiveDeposit(branchId, bookingId, actor.id(), amount, "MANUAL_TRANSFER", content);
        UUID paymentId = checkoutRepository.findLatestDepositPaymentId(bookingId);
        UUID transactionId = UUID.randomUUID();
        checkoutRepository.insertFinancialTransaction(transactionId, period.get("id"), businessDate, paymentId, content,
            actor.id(), "MANUAL-DEPOSIT-" + paymentId);
        checkoutRepository.insertFinancialEntry(transactionId, accountId, branchId, amount);
        bookingRepository.event(bookingId, actor.id(), "CONFIRM_MANUAL_DEPOSIT", null, Map.of("amount", amount, "reference", content));
        return depositStatus(actor, bookingId);
    }

    public Map<String, Object> preview(Identity actor, UUID bookingId) {
        UUID branchId = branch(actor);
        var booking = bookingRepository.booking(bookingId, branchId);
        if (booking == null) throw fail(404, "Không tìm thấy booking trong cơ sở của bạn.");
        var bills = checkoutRepository.findBillIdsByBooking(bookingId, branchId);
        if (!bills.isEmpty()) return bill(actor, (UUID) bills.get(0).get("id"));
        requireInHouse(booking.status());
        var result = new LinkedHashMap<String, Object>();
        result.put("bookingId", bookingId);
        result.put("bookingCode", booking.code());
        result.put("customer", booking.contactName());
        result.put("checkin", booking.actualCheckin());
        result.put("plannedEnd", booking.plannedEnd());
        Instant checkoutAt = Instant.now();
        result.put("checkoutAt", checkoutAt);
        var allocation = bookingRepository.currentAllocation(bookingId);
        result.put(
            "room",
            allocation == null ? "—" : allocation.roomCode() + " · " + allocation.roomName()
        );
        var overtime = bookingService.checkoutOvertime(actor, bookingId, checkoutAt);
        var lines = checkoutLines(actor, bookingId, checkoutAt, overtime);
        result.put("overtime", overtime);
        result.put(
            "overtimeAmount",
            overtime.stream().map(Overtime::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
        );
        result.put("lines", lines);
        BigDecimal amount = total(lines);
        BigDecimal depositBalance = bookingRepository.depositBalance(bookingId);
        BigDecimal rawPayable = amount.subtract(depositBalance);
        BigDecimal roundingBase = rawPayable.max(BigDecimal.ZERO);
        BigDecimal payableAmount = roundToNearestThousand(roundingBase);
        result.put("amount", amount);
        result.put("roundingAdjustment", payableAmount.subtract(roundingBase));
        result.put("payableAmount", payableAmount);
        result.put("quoteToken", quoteToken(actor, bookingId, checkoutAt, amount));
        var unserved = pendingOrders(bookingId);
        result.put("pendingOrders", unserved);
        result.put("unservedItemCount", unservedCount(unserved));
        result.put("depositBalance", depositBalance);
        result.put("hasPendingRefund", hasPendingRefund(bookingId));
        result.put("sepayReady", sePaySettings.ready());
        return result;
    }

    public List<Map<String, Object>> list(Identity actor) {
        return checkoutRepository.findRecentBills(branch(actor));
    }

    // BR-01/13/20/21/22/23/24/141/142/158/160/164/182/185/218/232; UC-41 (BR-125 được theo dõi độc lập trong TaxInvoiceService).
    @Transactional
    public Map<String, Object> issue(Identity actor, UUID bookingId, Issue input) {
        UUID branchId = branch(actor);
        lockBooking(bookingId, branchId);
        var existing = checkoutRepository.findBillIdsByBooking(bookingId);
        if (!existing.isEmpty()) return bill(actor, (UUID) existing.get(0).get("id"));
        var booking = bookingRepository.booking(bookingId, branchId);
        requireInHouse(booking.status());
        if (input == null || !input.inspected()) throw fail(
            400,
            "Vui lòng xác nhận đã kiểm tra phòng trước khi lập bill thanh toán."
        );
        if (input.inspectionNote() != null && input.inspectionNote().length() > 2000) throw fail(
            400,
            "Ghi chú kiểm tra phòng chỉ được chứa tối đa 2000 ký tự."
        );
        BigDecimal discountPercent = validateDiscountPercent(input.discountPercent());
        UUID depositId = bookingRepository.lockDeposit(bookingId);
        if (hasPendingRefund(bookingId)) throw fail(
            409,
            "Cọc đang có yêu cầu hoàn xử lý. Cần giải quyết yêu cầu trước khi lập bill."
        );
        // BR-159; MSG71: cảnh báo trước khi lễ tân chọn bỏ qua các order chưa phục vụ.
        var unserved = pendingOrders(bookingId);
        if (!unserved.isEmpty() && !input.unservedConfirmed()) throw fail(
            409,
            "Booking còn " +
                unservedCount(unserved).toPlainString() +
                " món chưa phục vụ. Bạn vẫn có thể tiếp tục trả phòng. Khi chốt bill, các order này sẽ được hủy. Bạn có muốn tiếp tục?"
        );
        // BR-164/170/215: chốt theo báo giá server đã ký, tối đa 5 phút để lễ tân đọc và xác nhận.
        // Không so với tiền chạy từng giây lúc POST vì sẽ khiến mọi báo giá vừa xem đều bị lệch.
        Instant quotedAt = input.quotedAt();
        Instant now = Instant.now();
        if (
            quotedAt == null ||
            quotedAt.isAfter(now) ||
            quotedAt.isBefore(now.minusSeconds(300)) ||
            quotedAt.isBefore(booking.actualCheckin())
        ) throw fail(409, "Báo giá đã hết hiệu lực. Vui lòng cập nhật tiền phòng và xác nhận lại.");
        financialPeriodService.requireOpen(quotedAt.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate(), "lập bill");
        var overtime = bookingService.checkoutOvertime(actor, bookingId, quotedAt);
        BigDecimal overtimeAmount = overtime
            .stream()
            .map(Overtime::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (
            !overtime.isEmpty() &&
            (!input.overtimeConfirmed() ||
                input.overtimeAmount() == null ||
                overtimeAmount.compareTo(input.overtimeAmount()) != 0)
        ) throw fail(409, "Vui lòng xác nhận tiền quá giờ theo báo giá mới nhất.");
        String signedToken = quoteToken(
            actor,
            bookingId,
            quotedAt,
            total(checkoutLines(actor, bookingId, quotedAt, overtime))
        );
        if (
            input.quoteToken() == null ||
            !java.security.MessageDigest.isEqual(
                signedToken.getBytes(StandardCharsets.UTF_8),
                input.quoteToken().getBytes(StandardCharsets.UTF_8)
            )
        ) throw fail(409, "Báo giá đã thay đổi. Vui lòng cập nhật tiền phòng và xác nhận lại.");
        bookingService.settleCheckoutCharges(actor, bookingId, quotedAt, overtime);
        if (input.surcharges() != null) {
            if (input.surcharges().size() > 20) throw fail(400, "Tối đa 20 phụ phí.");
            for (var surcharge : input.surcharges()) {
                if (
                    surcharge == null ||
                    surcharge.reason() == null ||
                    surcharge.reason().isBlank()
                ) {
                    throw fail(400, "Vui lòng bổ sung lý do phụ phí.");
                }
                if (surcharge.reason().length() > 180) throw fail(
                    400, "Lý do phụ phí chỉ được chứa tối đa 180 ký tự."
                );
                if (surcharge.amount() != null && surcharge.amount().signum() <= 0) throw fail(
                    400, "Phụ phí phải lớn hơn 0 đ."
                );
                money(surcharge.amount(), "Phụ phí");
                checkoutRepository.insertSurcharge(bookingId, surcharge.reason().trim(), surcharge.amount());
            }
        }
        List<Map<String, Object>> lines = sourceLines(bookingId);
        if (lines.isEmpty()) throw fail(409, "Booking chưa có giá đã xác nhận.");
        BigDecimal grossAmount = total(lines);
        money(grossAmount);
        BigDecimal discountAmount = grossAmount.multiply(discountPercent).movePointLeft(2).setScale(0, RoundingMode.HALF_UP);
        BigDecimal amount = grossAmount.subtract(discountAmount);
        BigDecimal heldDeposit = bookingRepository.depositBalance(bookingId);
        if (heldDeposit.signum() < 0) throw fail(409, "Số dư cọc không hợp lệ. Cần kiểm tra cọc trước khi thanh toán.");
        BigDecimal depositApplied = heldDeposit.min(amount);
        // BR-24: chỉ điều chỉnh khoản phải thu sau khi trừ cọc; net_amount và bill_line giữ nguyên giá trị hóa đơn.
        BigDecimal rawPayable = amount.subtract(depositApplied);
        BigDecimal payableAmount = roundToNearestThousand(rawPayable);
        BigDecimal roundingAdjustment = payableAmount.subtract(rawPayable);
        money(payableAmount);
        if (input.payableAmount() == null || input.payableAmount().compareTo(payableAmount) != 0) {
            throw fail(409, "Báo giá đã thay đổi. Vui lòng cập nhật tiền phòng và xác nhận lại.");
        }
        // BR-159: hủy order bỏ qua và hoàn kho trước khi trigger khóa thay đổi khi bill được phát hành.
        int cancelledOrderCount = unserved.isEmpty() ? 0 : foodOrderService.cancelUnservedForCheckout(actor, bookingId);
        UUID id = UUID.randomUUID();
        String number =
            "HD" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        checkoutRepository.insertBill(
            billId,
            bookingId,
            branchId,
            billNumber,
            grossAmount,
            discountAmount,
            amount,
            roundingAdjustment,
            Timestamp.from(quotedAt),
            input.inspectionNote() == null ? "" : input.inspectionNote().trim(),
            actor.id()
        );
        for (var line : lines) {
            checkoutRepository.insertBillLine(
                billId,
                line.get("chargeId"),
                line.get("orderId"),
                line.get("description"),
                line.get("quantity"),
                line.get("unitPrice"),
                line.get("amount")
            );
        }
        // Dùng bút toán APPLY; không sửa tiền cọc gốc và không tạo khoản thu lần hai.
        if (depositApplied.signum() > 0) {
            checkoutRepository.insertDepositApplication(depositId, depositApplied, billId, actor.id(), "CHECKOUT-DEPOSIT-" + billId);
        }
        audit(
            actor.id(),
            branchId,
            bookingId,
            "ISSUE_BILL",
            Map.of(
                "billId",
                billId,
                "amount",
                amount,
                "grossAmount",
                grossAmount,
                "discountPercent",
                discountPercent,
                "discountAmount",
                discountAmount,
                "roundingAdjustment",
                roundingAdjustment,
                "payableAmount",
                payableAmount,
                "depositAmount",
                depositApplied,
                "unservedConfirmed",
                input.unservedConfirmed(),
                "unservedItemCount",
                unservedCount(unserved),
                "cancelledOrderCount",
                cancelledOrderCount
            )
        );
        return bill(actor, billId);
    }

    public Map<String, Object> bill(Identity actor, UUID billId) {
        var rows = checkoutRepository.findBill(billId, branch(actor));
        if (rows.isEmpty()) throw fail(404, "Không tìm thấy bill trong cơ sở của bạn.");
        var result = new LinkedHashMap<>(rows.get(0));
        if ("ISSUED".equals(result.get("status")) && decimal(result, "amount").compareTo(decimal(result, "depositApplied")) == 0) {
            var depositBalance = bookingRepository.depositBalance((UUID) result.get("bookingId"));
            result.put("depositRefundAmount", depositBalance.max(BigDecimal.ZERO));
        }
        result.put("lines", checkoutRepository.findBillLines(billId));
        result.put(
            "remaining",
            decimal(result, "amount")
                .add(decimal(result, "roundingAdjustment"))
                .subtract(decimal(result, "depositApplied"))
                .subtract(decimal(result, "paid"))
        );
        result.put(
            "readyToComplete",
            decimal(result, "paid")
                .add(decimal(result, "plannedCash"))
                .add(decimal(result, "depositApplied"))
                .compareTo(decimal(result, "amount").add(decimal(result, "roundingAdjustment"))) == 0
        );
        result.put("sepayReady", sePaySettings.ready());
        var requests = checkoutRepository.findLatestPaymentRequest(billId);
        if (!requests.isEmpty()) {
            var request = new LinkedHashMap<>(requests.get(0));
            request.put(
                "qrUrl",
                "https://qr.sepay.vn/img?acc=" +
                    encode(request.get("account")) +
                    "&bank=" +
                    encode(request.get("bank")) +
                    "&amount=" +
                    request.get("amount") +
                    "&des=" +
                    encode(request.get("reference"))
            );
            request.put(
                "expired",
                ((Timestamp) request.get("expiresAt")).toInstant().isBefore(Instant.now())
            );
            result.put("request", request);
        }
        result.put("payments", checkoutRepository.findBillPayments(billId));
        return result;
    }

    // BR-25/26/144/145/160/161/162/167: tiền mặt đủ phần cần thu, QR đúng phần chuyển khoản và hạn 30 phút.
    @Transactional
    public Map<String, Object> plan(Identity actor, UUID billId, PaymentPlan input) {
        var bill = lockBill(actor, billId);
        if ("SETTLED".equals(bill.get("status"))) return bill(actor, billId);
        financialPeriodService.requireOpen(businessDay((Timestamp) bill.get("checkoutAt")), "cập nhật thanh toán bill");
        if (
            input == null || !List.of("CASH", "BANK_TRANSFER", "MIXED").contains(input.method())
        ) throw fail(400, "Chọn phương thức thanh toán hợp lệ.");
        // Gửi lặp giữ nguyên phân bổ tạm; không ghi khoản thu khi chỉ tạo QR.
        if (decimal(bill, "plannedCash").signum() > 0 || checkoutRepository.hasPaymentOrOpenRequest(billId)) {
            return bill(actor, billId);
        }
        BigDecimal remainingAmount = decimal(bill, "remaining");
        BigDecimal cashAmount = "CASH".equals(input.method())
            ? remainingAmount
            : "MIXED".equals(input.method())
              ? input.cashAmount()
              : BigDecimal.ZERO;
        money(cashAmount);
        if (
            cashAmount.compareTo(remainingAmount) > 0 ||
            ("MIXED".equals(input.method()) && (cashAmount.signum() <= 0 || cashAmount.compareTo(remainingAmount) >= 0))
        ) throw fail(
            400,
            "Phần thanh toán bằng tiền mặt phải lớn hơn 0 đ và nhỏ hơn " +
                remainingAmount.toPlainString() + " đ. Nếu trả toàn bộ bằng tiền mặt, hãy chọn thanh toán tiền mặt."
        );
        BigDecimal bankAmount = remainingAmount.subtract(cashAmount);
        if (bankAmount.signum() > 0 && !sePaySettings.ready()) throw fail(
            503,
            "Chưa cấu hình ngân hàng và khóa webhook SePay trong backend/.env."
        );
        UUID bookingId = (UUID) bill.get("bookingId");
        UUID branchId = branch(actor);
        BigDecimal cashTendered = BigDecimal.ZERO;
        if (cashAmount.signum() > 0) {
            money(input.cashTendered(), "Tiền khách đưa");
            if (input.cashTendered().compareTo(cashAmount) < 0) throw fail(
                400,
                "Tiền khách đưa chưa đủ. Cần thu ít nhất " + cashAmount.toPlainString() + " đ."
            );
            cashTendered = input.cashTendered();
        }
        checkoutRepository.updatePlannedCash(billId, cashAmount, cashTendered);
        if (bankAmount.signum() > 0) {
            UUID accountId = bankAccount();
            String referenceCode =
                "GZ" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
            checkoutRepository.insertBillPaymentRequest(branchId, bookingId, billId, accountId, referenceCode, bankAmount);
        }
        audit(
            actor.id(),
            branchId,
            bookingId,
            "PLAN_BILL_PAYMENT",
            Map.of("billId", billId, "cashAmount", cashAmount, "bankAmount", bankAmount)
        );
        return bill(actor, billId);
    }

    @Transactional
    public Map<String, Object> complete(Identity actor, UUID billId) {
        var bill = lockBill(actor, billId);
        if ("SETTLED".equals(bill.get("status"))) return bill(actor, billId);
        financialPeriodService.requireOpen(businessDay((Timestamp) bill.get("checkoutAt")), "hoàn tất bill");
        BigDecimal received = decimal(bill, "paid");
        BigDecimal plannedCash = decimal(bill, "plannedCash");
        if (
            received
                .add(plannedCash)
                .add(decimal(bill, "depositApplied"))
                .compareTo(decimal(bill, "amount").add(decimal(bill, "roundingAdjustment"))) != 0
        ) throw fail(409, "Bill chưa được thanh toán đủ; chờ SePay xác nhận hoặc nhập tiền mặt.");
        UUID bookingId = (UUID) bill.get("bookingId");
        UUID branchId = branch(actor);
        var allocation = bookingRepository.currentAllocationForUpdate(bookingId);
        if (allocation == null || !"OCCUPIED".equals(allocation.state())) throw fail(
            409,
            "Không có phiên sử dụng đang hoạt động."
        );
        // Quà combo đã được khách chọn lúc check-in; chỉ xuất kho khi bill được thanh toán đủ.
        foodOrderService.deductComboGiftStock(actor, bookingId);
        // Tiền mặt chỉ thành khoản thu chính thức cùng giao dịch hoàn tất bill.
        if (plannedCash.signum() > 0) {
            BigDecimal cashTendered = decimal(bill, "plannedCashTendered");
            checkoutRepository.insertCashPayment(
                branchId,
                bookingId,
                billId,
                plannedCash,
                cashTendered,
                cashTendered.subtract(plannedCash),
                actor.id(),
                "CHECKOUT-CASH-" + billId + "-FINAL"
            );
        }
        received = received.add(plannedCash);
        checkoutRepository.settleBill(billId);
        Timestamp checkoutAt = (Timestamp) bill.get("checkoutAt");
        checkoutRepository.finishAllocation(allocation.id(), checkoutAt);
        checkoutRepository.completeBooking(bookingId, checkoutAt);
        checkoutRepository.releaseRoomAfterCheckout(allocation.roomId(), branchId, allocation.operatingStatus(), actor.id());
        BigDecimal refundable = BigDecimal.ZERO;
        if (decimal(bill, "depositRefundAmount").signum() > 0) {
            UUID depositId = bookingRepository.lockDeposit(bookingId);
            refundable = bookingRepository.depositBalance(bookingId);
            if (refundable.signum() > 0) {
                if (depositId == null) throw fail(409, "Không tìm thấy tài khoản tiền cọc để tạo yêu cầu hoàn.");
                bookingRepository.createDepositRefundRequest(branchId, bookingId, depositId, refundable,
                    "Hoàn cọc dư khi checkout", (String) bill.get("customer"), "", "", actor.id(), billId);
            }
        }
        bookingRepository.event(
            bookingId,
            actor.id(),
            "BOOKING_CHECKED_OUT",
            null,
            Map.of("billId", billId.toString())
        );
        audit(
            actor.id(),
            branchId,
            bookingId,
            "CHECKOUT_BOOKING",
            Map.of(
                "billId",
                billId,
                "amount",
                received,
                "roundingAdjustment",
                decimal(bill, "roundingAdjustment"),
                "depositAmount",
                decimal(bill, "depositApplied"),
                "depositRefundAmount",
                refundable
            )
        );
        return bill(actor, billId);
    }

    @Transactional
    public Map<String, Object> renew(Identity actor, UUID billId) {
        var bill = lockBill(actor, billId);
        financialPeriodService.requireOpen(businessDay((Timestamp) bill.get("checkoutAt")), "gia hạn mã thanh toán bill");
        // Gia hạn cùng mã tham chiếu; không tạo mã thứ hai có thể nhận tiền trùng.
        int renewedCount = checkoutRepository.renewExpiredBillRequest(billId);
        if (renewedCount > 0) {
            audit(
                actor.id(),
                branch(actor),
                (UUID) bill.get("bookingId"),
                "RENEW_BILL_QR",
                Map.of("billId", billId)
            );
        }
        return bill(actor, billId);
    }

    /** Hủy QR và xóa phân bổ tạm để form trở về ban đầu; không đảo khoản thu thật. */
    @Transactional
    public Map<String, Object> changeMethod(Identity actor, UUID billId) {
        // Khóa cùng thứ tự với webhook: giao dịch đã ghi nhận sẽ chặn đổi phương thức.
        var bill = lockBill(actor, billId);
        financialPeriodService.requireOpen(businessDay((Timestamp) bill.get("checkoutAt")), "đổi phương thức thanh toán bill");
        if (!"ISSUED".equals(bill.get("status"))) {
            throw fail(409, "Chỉ đổi phương thức cho bill đang chờ thanh toán.");
        }
        if (checkoutRepository.hasActualPayment(billId)) {
            throw fail(
                409,
                "Bill đã nhận thanh toán thực tế, không thể đặt lại phương thức thanh toán."
            );
        }
        int cancelledCount = checkoutRepository.cancelOpenBillRequests(billId);
        checkoutRepository.resetPlannedCash(billId);
        if (cancelledCount > 0 || decimal(bill, "plannedCash").signum() > 0) {
            audit(
                actor.id(),
                branch(actor),
                (UUID) bill.get("bookingId"),
                "CHANGE_BILL_PAYMENT_METHOD",
                Map.of("billId", billId)
            );
        }
        return bill(actor, billId);
    }

    @Transactional
    public void webhook(String authorization, Webhook payload) {
        if (!sePaySettings.authorized(authorization)) throw fail(401, "Chứng thực webhook không hợp lệ.");
        if (payload == null || payload.id() == null || payload.id() <= 0) throw fail(
            400,
            "Thiếu mã giao dịch SePay."
        );
        money(payload.transferAmount());
        if (payload.transferAmount().signum() <= 0) throw fail(400, "Số tiền giao dịch phải lớn hơn 0 đ.");
        if (
            !sePaySettings.account.equals(payload.accountNumber()) ||
            !sePaySettings.bank.equalsIgnoreCase(payload.gateway())
        ) throw fail(400, "Giao dịch không thuộc tài khoản ngân hàng đã cấu hình.");
        if (!List.of("in", "out").contains(payload.transferType())) throw fail(
            400,
            "Loại giao dịch không hợp lệ."
        );
        UUID accountId = bankAccount();
        String content = payload.content() == null ? "" : payload.content();
        var referenceMatcher = Pattern.compile("(?i)(?<![A-Z0-9])GZ[A-F0-9]{20}(?![A-Z0-9])").matcher(content);
        String referenceCode =
            payload.code() != null && payload.code().matches("(?i)GZ[A-F0-9]{20}")
                ? payload.code().toUpperCase()
                : referenceMatcher.find()
                  ? referenceMatcher.group().toUpperCase()
                  : "";
        var requests = checkoutRepository.findPaymentRequestsByReference(referenceCode);
        if (!requests.isEmpty()) {
            var matchedRequest = requests.get(0);
            // Cùng thứ tự khóa với thu tiền mặt/hoàn tất để tránh race và deadlock.
            lockBooking((UUID) matchedRequest.get("booking_id"), (UUID) matchedRequest.get("branch_id"));
            if ("BILL".equals(matchedRequest.get("purpose"))) checkoutRepository.lockBill(matchedRequest.get("bill_id"));
        }
        Instant occurredAt;
        try {
            occurredAt = LocalDateTime.parse(
                payload.transactionDate(),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            )
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .toInstant();
        } catch (RuntimeException exception) {
            throw fail(400, "Ngày giao dịch SePay không hợp lệ.");
        }
        String externalId = payload.id().toString();
        String direction = "in".equals(payload.transferType()) ? "IN" : "OUT";
        if (checkoutRepository.bankTransactionExists(accountId, externalId)) return;
        try {
            financialPeriodService.requireOpen(occurredAt.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate(), "ghi nhận giao dịch SePay");
        } catch (AuthFailure failure) {
            if (failure.status() != 409) throw failure;
            checkoutRepository.insertClosedPeriodBankTransaction(UUID.randomUUID(), accountId, externalId, direction,
                payload.transferAmount(), content, Timestamp.from(occurredAt), objectMapper.writeValueAsString(payload));
            return;
        }
        UUID transactionId = UUID.randomUUID();
        int insertedCount = checkoutRepository.insertUnmatchedBankTransaction(transactionId, accountId, externalId, direction,
            payload.transferAmount(), content, Timestamp.from(occurredAt), objectMapper.writeValueAsString(payload));
        if (insertedCount == 0) return;
        if (requests.isEmpty()) {
            checkoutRepository.markBankTransactionException(transactionId, "REFERENCE_NOT_FOUND");
            return;
        }
        var request = checkoutRepository.lockPaymentRequest(requests.get(0).get("id"));
        String mismatchReason = null;
        if (!"in".equals(payload.transferType())) mismatchReason = "DIRECTION_MISMATCH";
        else if (!"PENDING".equals(request.get("status"))) mismatchReason = "REQUEST_NOT_PENDING";
        else if (!accountId.equals(request.get("account_id"))) mismatchReason = "ACCOUNT_MISMATCH";
        else if (payload.transferAmount().compareTo((BigDecimal) request.get("expected_amount")) != 0)
            mismatchReason = "AMOUNT_MISMATCH";
        else if (occurredAt.isBefore(((Timestamp) request.get("created_at")).toInstant().minusSeconds(2)))
            mismatchReason = "PAYMENT_BEFORE_REQUEST";
        else if (occurredAt.isAfter(((Timestamp) request.get("expires_at")).toInstant()))
            mismatchReason = "PAYMENT_EXPIRED";
        boolean isDeposit = "DEPOSIT".equals(request.get("purpose"));
        // Cọc chuyển khoản vẫn phải do lễ tân xác nhận.
        if (mismatchReason == null && isDeposit) mismatchReason = "DEPOSIT_MANUAL_CONFIRMATION_REQUIRED";
        if (mismatchReason != null) {
            checkoutRepository.markBankTransactionException(transactionId, mismatchReason);
            return;
        }
        UUID paymentId = UUID.randomUUID();
        checkoutRepository.insertBankTransferPayment(
            paymentId,
            request.get("branch_id"),
            request.get("booking_id"),
            request.get("bill_id"),
            request.get("purpose"),
            payload.transferAmount(),
            request.get("id"),
            transactionId,
            Timestamp.from(occurredAt),
            "SEPAY-" + payload.id()
        );
        if (isDeposit) {
            bookingRepository.createDepositAccount((UUID) request.get("booking_id"));
            checkoutRepository.insertSePayDepositReceipt(payload.transferAmount(), paymentId,
                "SEPAY-DEPOSIT-" + payload.id(), request.get("booking_id"));
        }
        audit(
            null,
            (UUID) request.get("branch_id"),
            (UUID) request.get("booking_id"),
            isDeposit ? "CONFIRM_SEPAY_DEPOSIT" : "CONFIRM_SEPAY_PAYMENT",
            isDeposit
                ? Map.of("amount", payload.transferAmount())
                : Map.of("billId", request.get("bill_id"), "amount", payload.transferAmount())
        );
    }

    private boolean hasPendingRefund(UUID bookingId) {
        return checkoutRepository.hasPendingDepositRefund(bookingId);
    }

    private static java.time.LocalDate businessDay(Timestamp value) {
        return value.toInstant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
    }

    private UUID bankAccount() {
        String accountCode =
            "SEPAY-" +
            UUID.nameUUIDFromBytes(
                (sePaySettings.bank + ":" + sePaySettings.account).getBytes(StandardCharsets.UTF_8)
            ).toString();
        return checkoutRepository.ensureSePayAccount(accountCode, sePaySettings.bank, sePaySettings.account, sePaySettings.holder);
    }

    // BR-13/21/28/29/30/172/215/216/218: preview chỉ đọc; chỉ món đã phục vụ mới được tính vào bill.
    private List<Map<String, Object>> checkoutLines(
        Identity actor,
        UUID bookingId,
        Instant checkoutAt,
        List<Overtime> overtime
    ) {
        var hourlyCharges = bookingService.checkoutHourlyCharges(actor, bookingId, checkoutAt);
        var result = new java.util.ArrayList<Map<String, Object>>();
        for (var original : sourceLines(bookingId)) {
            if ("OVERTIME".equals(original.get("chargeKind"))) continue;
            var adjusted = hourlyCharges.get(original.get("chargeId"));
            if (adjusted != null && adjusted.quantity().signum() == 0) continue;
            var line = new LinkedHashMap<>(original);
            if (adjusted != null) {
                line.put("quantity", adjusted.quantity());
                line.put("amount", adjusted.amount());
            }
            result.add(line);
        }
        for (var line : overtime)
            result.add(
                Map.of(
                    "description",
                    "Quá giờ · " + line.seconds() + " giây",
                    "quantity",
                    BigDecimal.valueOf(line.seconds()).divide(
                        BigDecimal.valueOf(3600),
                        3,
                        java.math.RoundingMode.UP
                    ),
                    "unitPrice",
                    line.hourlyPrice(),
                    "amount",
                    line.amount(),
                    "category",
                    "ROOM"
                )
            );
        return result;
    }

    private String quoteToken(Identity actor, UUID bookingId, Instant quotedAt, BigDecimal amount) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(quoteKey, "HmacSHA256"));
            String value =
                actor.id() +
                ":" +
                bookingId +
                ":" +
                quotedAt +
                ":" +
                amount.stripTrailingZeros().toPlainString();
            return java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot sign checkout quote", exception);
        }
    }

    private List<Map<String, Object>> sourceLines(UUID bookingId) {
        return checkoutRepository.findChargeAndServedOrderLines(bookingId);
    }

    private List<Map<String, Object>> pendingOrders(UUID bookingId) {
        return checkoutRepository.findPendingOrders(bookingId);
    }

    private static BigDecimal unservedCount(List<Map<String, Object>> orders) {
        return orders
            .stream()
            .map(order -> new BigDecimal(String.valueOf(order.getOrDefault("itemCount", 0))))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .stripTrailingZeros();
    }

    private Map<String, Object> lockBill(Identity actor, UUID billId) {
        var found = bill(actor, billId);
        lockBooking((UUID) found.get("bookingId"), branch(actor));
        checkoutRepository.lockBill(billId);
        return bill(actor, billId);
    }

    private void lockBooking(UUID bookingId, UUID branchId) {
        lockBooking(bookingId, branchId, "tạo yêu cầu thanh toán");
    }

    private void lockBooking(UUID bookingId, UUID branchId, String action) {
        bookingRepository.requireActiveBranch(branchId); // BR-177
        if (!checkoutRepository.lockBookingInBranch(bookingId, branchId)) {
            if (bookingRepository.bookingExists(bookingId)) throw fail(403, "Bạn không có quyền " + action + " đối với booking.");
            throw fail(404, "Không tìm thấy booking.");
        }
    }

    private UUID branch(Identity actor) {
        return branch(actor, "checkout");
    }

    private UUID branch(Identity actor, String action) {
        if (actor == null || !"RECEPTIONIST".equals(actor.role()))
            throw fail(403, "Bạn không có quyền " + action + " đối với booking.");
        var scope = bookingRepository.branchScope(actor.id());
        if (scope == null) throw fail(403, "Bạn không có quyền " + action + " đối với booking.");
        return scope.id();
    }

    private void audit(
        UUID actorId,
        UUID branchId,
        UUID bookingId,
        String action,
        Map<String, Object> data
    ) {
        var snapshot = new LinkedHashMap<>(data);
        snapshot.put("actorRole", actorId == null ? "SYSTEM" : "RECEPTIONIST");
        checkoutRepository.insertBookingAudit(actorId, branchId, action, bookingId, objectMapper.writeValueAsString(snapshot));
    }

    private static BigDecimal total(List<Map<String, Object>> lines) {
        return lines
            .stream()
            .map(line -> (BigDecimal) line.get("amount"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal validateDiscountPercent(BigDecimal percent) {
        if (percent == null) return BigDecimal.ZERO;
        if (percent.signum() >= 0 && percent.compareTo(BigDecimal.valueOf(100)) <= 0) {
            BigDecimal normalized = percent.stripTrailingZeros();
            if (normalized.scale() <= 2) return normalized;
        }
        throw fail(400, "Giảm giá phải từ 0% đến 100%, tối đa hai chữ số thập phân.");
    }

    private static BigDecimal roundToNearestThousand(BigDecimal amount) {
        BigDecimal thousand = BigDecimal.valueOf(1000);
        BigDecimal remainder = amount.remainder(thousand);
        return amount.subtract(remainder).add(
            remainder.compareTo(BigDecimal.valueOf(500)) >= 0 ? thousand : BigDecimal.ZERO
        );
    }

    private static BigDecimal decimal(Map<String, Object> row, String key) {
        return (BigDecimal) row.get(key);
    }

    private static String encode(Object value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }

    private static void requireInHouse(String status) {
        if (!"IN_HOUSE".equals(status)) throw fail(409, "Chỉ checkout booking đã check-in.");
    }

    private static void money(BigDecimal value) {
        money(value, null);
    }

    private static void money(BigDecimal value, String field) {
        if (field != null && value != null && value.stripTrailingZeros().scale() > 0) throw fail(
            400, field + " phải là số tiền nguyên đồng, không có phần thập phân."
        );
        if (
            value == null ||
            value.signum() < 0 ||
            value.stripTrailingZeros().scale() > 0 ||
            value.compareTo(new BigDecimal("999999999999999999")) > 0
        ) throw fail(400, "Số tiền phải là số nguyên VND không âm, tối đa 18 chữ số.");
    }

    private static BookingFailure fail(int status, String message) {
        return new BookingFailure(status, "form", message);
    }
}
