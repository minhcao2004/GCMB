/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Chốt giá đã xác nhận, ghi bill/thanh toán và kết thúc sử dụng phòng.
 * Updated: 15/10/2026 - Áp dụng cọc đã thu, chỉ thanh toán phần còn lại.
 */
package com.gcmb.backend.booking.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.dto.CheckoutDtos.*;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import java.math.BigDecimal;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class CheckoutService {
    private final JdbcTemplate db;
    private final BookingRepository repository;
    private final BookingService bookings;
    private final SePaySettings sepay;
    private final ObjectMapper json;

    public CheckoutService(JdbcTemplate db, BookingRepository repository, BookingService bookings,
            SePaySettings sepay, ObjectMapper json) {
        this.db = db;
        this.repository = repository;
        this.bookings = bookings;
        this.sepay = sepay;
        this.json = json;
    }

    @Transactional
    public Map<String, Object> depositPayment(Identity actor, UUID bookingId, boolean create) {
        UUID branchId = branch(actor);
        lockBooking(bookingId, branchId);
        var booking = repository.booking(bookingId, branchId);
        BigDecimal received = repository.depositReceivedAmount(bookingId);
        BigDecimal amount = BookingPricingService.depositFor(repository.activeChargeTotal(bookingId));
        var result = new LinkedHashMap<String, Object>();
        result.put("bookingId", bookingId);
        result.put("bookingCode", booking.code());
        result.put("amount", amount);
        result.put("receivedAmount", received);
        if (received.signum() > 0) {
            result.put("status", "SETTLED");
            return result;
        }
        if (!"CONFIRMED".equals(booking.status())) throw fail(409, "Chỉ tạo QR cọc cho booking đang xác nhận và chưa sử dụng.");
        var requests = db.queryForList("""
            SELECT * FROM gcmb.payment_request WHERE booking_id=? AND purpose='DEPOSIT'
                AND status IN ('PENDING','EXPIRED') ORDER BY created_at DESC,id DESC LIMIT 1
            """, bookingId);
        var request = requests.isEmpty() ? null : requests.get(0);
        boolean reusable = request != null && "PENDING".equals(request.get("status"))
            && ((BigDecimal) request.get("expected_amount")).compareTo(amount) == 0
            && ((Timestamp) request.get("expires_at")).toInstant().isAfter(Instant.now());
        if (create && !reusable) {
            if (!sepay.ready()) throw fail(503, "Chưa cấu hình ngân hàng và khóa webhook SePay trong backend/.env.");
            if (amount.signum() <= 0) throw fail(400, "Giá booking phải lớn hơn 0 để tạo QR cọc.");
            db.update("UPDATE gcmb.payment_request SET status='CANCELLED' WHERE booking_id=? AND purpose='DEPOSIT' AND status IN ('PENDING','EXPIRED')", bookingId);
            UUID account = bankAccount();
            String reference = "GZ" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
            request = db.queryForMap("""
                INSERT INTO gcmb.payment_request(branch_id,booking_id,account_id,purpose,reference_code,expected_amount,expires_at,status)
                VALUES (?,?,?,'DEPOSIT',?,?,CURRENT_TIMESTAMP+interval '30 minutes','PENDING') RETURNING *
                """, branchId, bookingId, account, reference, amount);
            audit(actor.id(), branchId, bookingId, "PLAN_DEPOSIT_PAYMENT", Map.of("amount", amount));
        }
        if (request == null) {
            result.put("status", "NONE");
            return result;
        }
        var account = db.queryForMap("SELECT bank_name,bank_account_number,account_holder FROM gcmb.financial_account WHERE id=?", request.get("account_id"));
        boolean expired = !((Timestamp) request.get("expires_at")).toInstant().isAfter(Instant.now())
            || ((BigDecimal) request.get("expected_amount")).compareTo(amount) != 0;
        result.put("status", expired ? "EXPIRED" : request.get("status"));
        result.put("amount", request.get("expected_amount"));
        result.put("expiresAt", ((Timestamp) request.get("expires_at")).toInstant());
        result.put("reference", request.get("reference_code"));
        result.put("bank", account.get("bank_name"));
        result.put("account", account.get("bank_account_number"));
        result.put("holder", account.get("account_holder"));
        if (!expired) result.put("qrUrl", "https://qr.sepay.vn/img?acc=" + encode(result.get("account"))
            + "&bank=" + encode(result.get("bank")) + "&amount=" + result.get("amount") + "&des=" + encode(result.get("reference")));
        return result;
    }

    public Map<String, Object> preview(Identity actor, UUID bookingId) {
        UUID branch = branch(actor);
        var booking = repository.booking(bookingId, branch);
        if (booking == null) throw fail(404, "Không tìm thấy booking trong cơ sở của bạn.");
        var bills = db.queryForList("SELECT id FROM gcmb.bill WHERE booking_id=? AND branch_id=?", bookingId, branch);
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
        var allocation = repository.currentAllocation(bookingId);
        result.put("room", allocation == null ? "—" : allocation.roomCode() + " · " + allocation.roomName());
        var overtime = bookings.checkoutOvertime(actor, bookingId, checkoutAt);
        var lines = new java.util.ArrayList<>(sourceLines(bookingId));
        for (var line : overtime) lines.add(Map.of("description", "Quá giờ khi checkout · " + line.minutes() + " phút",
            "quantity", BigDecimal.valueOf(line.minutes()).divide(BigDecimal.valueOf(60), 3, java.math.RoundingMode.HALF_UP),
            "unitPrice", line.hourlyPrice(), "amount", line.amount(), "category", "ROOM"));
        result.put("overtime", overtime);
        result.put("overtimeAmount", overtime.stream().map(Overtime::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        result.put("lines", lines);
        result.put("amount", total(lines));
        result.put("pendingOrders", pendingOrders(bookingId));
        result.put("depositBalance", repository.depositBalance(bookingId));
        result.put("hasPendingRefund", hasPendingRefund(bookingId));
        result.put("sepayReady", sepay.ready());
        return result;
    }

    public List<Map<String, Object>> list(Identity actor) {
        return db.queryForList("""
            SELECT b.id,b.number,b.status,b.net_amount AS amount,k.contact_name AS customer
            FROM gcmb.bill b JOIN gcmb.booking k ON k.id=b.booking_id
            WHERE b.branch_id=? ORDER BY b.created_at DESC LIMIT 100
            """, branch(actor));
    }

    @Transactional
    public Map<String, Object> issue(Identity actor, UUID bookingId, Issue input) {
        UUID branch = branch(actor);
        lockBooking(bookingId, branch);
        var existing = db.queryForList("SELECT id FROM gcmb.bill WHERE booking_id=?", bookingId);
        if (!existing.isEmpty()) return bill(actor, (UUID) existing.get(0).get("id"));
        var booking = repository.booking(bookingId, branch);
        requireInHouse(booking.status());
        if (input == null || !input.inspected()) throw fail(400, "Xác nhận đã kiểm tra phòng trước khi lập bill.");
        if (input.inspectionNote() != null && input.inspectionNote().length() > 2000) throw fail(400, "Ghi chú kiểm tra tối đa 2000 ký tự.");
        UUID deposit = repository.lockDeposit(bookingId);
        if (hasPendingRefund(bookingId)) throw fail(409, "Cọc đang có yêu cầu hoàn xử lý. Cần giải quyết yêu cầu trước khi lập bill.");
        if (!pendingOrders(bookingId).isEmpty()) throw fail(409, "Còn order chưa phục vụ. Hoàn tất hoặc hủy order trước khi chốt bill.");
        bookings.settleCheckoutOvertime(actor, bookingId, input.overtimeConfirmed(), input.overtimeAmount());
        if (input.surcharges() != null) {
            if (input.surcharges().size() > 20) throw fail(400, "Tối đa 20 phụ phí.");
            for (var fee : input.surcharges()) {
                if (fee == null || fee.reason() == null || fee.reason().isBlank() || fee.reason().length() > 180) {
                    throw fail(400, "Mỗi phụ phí cần lý do tối đa 180 ký tự.");
                }
                money(fee.amount());
                if (fee.amount().signum() <= 0) throw fail(400, "Phụ phí phải lớn hơn 0.");
                db.update("""
                    INSERT INTO gcmb.booking_charge(booking_id,kind,description,quantity,unit_price,amount,pricing_snapshot,status)
                    VALUES (?,'SURCHARGE',?,1,?,?,jsonb_build_object('source','CHECKOUT','reason',CAST(? AS text)),'ACTIVE')
                    """, bookingId, fee.reason().trim(), fee.amount(), fee.amount(), fee.reason().trim());
            }
        }
        List<Map<String, Object>> lines = sourceLines(bookingId);
        if (lines.isEmpty()) throw fail(409, "Booking chưa có giá đã xác nhận.");
        BigDecimal amount = total(lines);
        BigDecimal heldDeposit = repository.depositBalance(bookingId);
        if (heldDeposit.signum() < 0 || heldDeposit.compareTo(amount) > 0) {
            throw fail(409, "Số dư cọc không hợp lệ hoặc vượt tổng bill. Cần kiểm tra cọc trước khi thanh toán.");
        }
        UUID id = UUID.randomUUID();
        String number = "HD" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        db.update("""
            INSERT INTO gcmb.bill(id,booking_id,branch_id,number,status,gross_amount,discount_amount,net_amount,
                issued_at,checkout_at,inspection_note,issued_by)
            VALUES (?,?,?,?,'ISSUED',?,0,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?)
            """, id, bookingId, branch, number, amount, amount,
            input.inspectionNote() == null ? "" : input.inspectionNote().trim(), actor.id());
        for (var line : lines) {
            db.update("""
                INSERT INTO gcmb.bill_line(bill_id,booking_charge_id,order_line_id,description,quantity,unit_price,amount)
                VALUES (?,?,?,?,?,?,?)
                """, id, line.get("chargeId"), line.get("orderId"), line.get("description"),
                line.get("quantity"), line.get("unitPrice"), line.get("amount"));
        }
        // Dùng bút toán APPLY; không sửa tiền cọc gốc và không tạo khoản thu lần hai.
        if (heldDeposit.signum() > 0) {
            db.update("""
                INSERT INTO gcmb.deposit_entry(deposit_id,kind,amount,bill_id,actor_id,reason,idempotency_key)
                VALUES (?,'APPLY',?,?,?,'Trừ cọc đã thu vào bill checkout',?)
                """, deposit, heldDeposit, id, actor.id(), "CHECKOUT-DEPOSIT-" + id);
        }
        audit(actor.id(), branch, bookingId, "ISSUE_BILL", Map.of("billId", id, "amount", amount, "depositAmount", heldDeposit));
        return bill(actor, id);
    }

    public Map<String, Object> bill(Identity actor, UUID id) {
        var rows = db.queryForList("""
            SELECT b.id,b.booking_id AS "bookingId",b.number,b.status,b.net_amount AS amount,
                b.checkout_at AS "checkoutAt",b.inspection_note AS "inspectionNote",
                b.planned_cash AS "plannedCash",b.planned_cash_tendered AS "plannedCashTendered",
                COALESCE((SELECT sum(e.amount) FROM gcmb.deposit_entry e WHERE e.bill_id=b.id AND e.kind='APPLY'),0) AS "depositApplied",
                k.code AS "bookingCode",k.contact_name AS customer,k.actual_checkin AS checkin,
                COALESCE((SELECT r.code||' · '||r.name FROM gcmb.room_allocation a JOIN gcmb.room r ON r.id=a.room_id
                    WHERE a.booking_id=k.id ORDER BY a.created_at DESC LIMIT 1),'—') AS room,
                COALESCE((SELECT sum(p.amount) FROM gcmb.payment p WHERE p.bill_id=b.id AND NOT EXISTS (SELECT 1 FROM gcmb.checkout_cash_reclassification c WHERE c.payment_id=p.id)),0) AS paid
            FROM gcmb.bill b JOIN gcmb.booking k ON k.id=b.booking_id WHERE b.id=? AND b.branch_id=?
            """, id, branch(actor));
        if (rows.isEmpty()) throw fail(404, "Không tìm thấy bill trong cơ sở của bạn.");
        var result = new LinkedHashMap<>(rows.get(0));
        result.put("lines", db.queryForList("""
            SELECT l.description,l.quantity,l.unit_price AS "unitPrice",l.amount,
                CASE WHEN l.order_line_id IS NOT NULL THEN 'FNB'
                    WHEN c.kind='SURCHARGE' THEN 'SURCHARGE' ELSE 'ROOM' END AS category
            FROM gcmb.bill_line l LEFT JOIN gcmb.booking_charge c ON c.id=l.booking_charge_id
            WHERE l.bill_id=? ORDER BY l.created_at,l.id
            """, id));
        result.put("remaining", decimal(result, "amount").subtract(decimal(result, "depositApplied")).subtract(decimal(result, "paid")));
        result.put("readyToComplete", decimal(result, "paid").add(decimal(result, "plannedCash")).add(decimal(result, "depositApplied")).compareTo(decimal(result, "amount")) == 0);
        result.put("sepayReady", sepay.ready());
        var requests = db.queryForList("""
            SELECT q.id,q.reference_code AS reference,q.expected_amount AS amount,q.status,q.expires_at AS "expiresAt",
                f.bank_name AS bank,f.bank_account_number AS account,f.account_holder AS holder
            FROM gcmb.payment_request q JOIN gcmb.financial_account f ON f.id=q.account_id
            WHERE q.bill_id=? AND q.status<>'CANCELLED' ORDER BY q.created_at DESC,q.id DESC LIMIT 1
            """, id);
        if (!requests.isEmpty()) {
            var request = new LinkedHashMap<>(requests.get(0));
            request.put("qrUrl", "https://qr.sepay.vn/img?acc=" + encode(request.get("account"))
                + "&bank=" + encode(request.get("bank")) + "&amount=" + request.get("amount")
                + "&des=" + encode(request.get("reference")));
            request.put("expired", ((Timestamp) request.get("expiresAt")).toInstant().isBefore(Instant.now()));
            result.put("request", request);
        }
        result.put("payments", db.queryForList("SELECT method,amount,cash_tendered AS \"cashTendered\",cash_change AS \"cashChange\" FROM gcmb.payment p WHERE bill_id=? AND NOT EXISTS (SELECT 1 FROM gcmb.checkout_cash_reclassification c WHERE c.payment_id=p.id) ORDER BY created_at", id));
        return result;
    }

    @Transactional
    public Map<String, Object> plan(Identity actor, UUID id, PaymentPlan input) {
        var bill = lockBill(actor, id);
        if ("SETTLED".equals(bill.get("status"))) return bill(actor, id);
        if (input == null || !List.of("CASH", "BANK_TRANSFER", "MIXED").contains(input.method())) throw fail(400, "Chọn phương thức thanh toán hợp lệ.");
        // Gửi lặp giữ nguyên phân bổ tạm; không ghi khoản thu khi chỉ tạo QR.
        if (decimal(bill, "plannedCash").signum() > 0 || !db.queryForList("SELECT id FROM gcmb.payment p WHERE bill_id=? AND NOT EXISTS (SELECT 1 FROM gcmb.checkout_cash_reclassification c WHERE c.payment_id=p.id) UNION ALL SELECT id FROM gcmb.payment_request WHERE bill_id=? AND status<>'CANCELLED'", id, id).isEmpty()) {
            return bill(actor, id);
        }
        BigDecimal amount = decimal(bill, "remaining");
        BigDecimal cash = "CASH".equals(input.method()) ? amount : "MIXED".equals(input.method()) ? input.cashAmount() : BigDecimal.ZERO;
        money(cash);
        if (cash.compareTo(amount) > 0 || ("MIXED".equals(input.method()) && (cash.signum() <= 0 || cash.compareTo(amount) >= 0))) throw fail(400, "Phần tiền mặt kết hợp phải lớn hơn 0 và nhỏ hơn tổng bill.");
        BigDecimal bank = amount.subtract(cash);
        if (bank.signum() > 0 && !sepay.ready()) throw fail(503, "Chưa cấu hình ngân hàng và khóa webhook SePay trong backend/.env.");
        UUID booking = (UUID) bill.get("bookingId");
        UUID branch = branch(actor);
        BigDecimal tendered = BigDecimal.ZERO;
        if (cash.signum() > 0) {
            money(input.cashTendered());
            if (input.cashTendered().compareTo(cash) < 0) throw fail(400, "Tiền khách đưa chưa đủ phần tiền mặt.");
            tendered = input.cashTendered();
        }
        db.update("UPDATE gcmb.bill SET planned_cash=?,planned_cash_tendered=? WHERE id=?", cash, tendered, id);
        if (bank.signum() > 0) {
            UUID account = bankAccount();
            String reference = "GZ" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
            db.update("""
                INSERT INTO gcmb.payment_request(branch_id,booking_id,bill_id,account_id,purpose,reference_code,expected_amount,expires_at,status)
                VALUES (?,?,?,?,'BILL',?,?,CURRENT_TIMESTAMP+interval '30 minutes','PENDING')
                """, branch, booking, id, account, reference, bank);
        }
        audit(actor.id(), branch, booking, "PLAN_BILL_PAYMENT", Map.of("billId", id, "cashAmount", cash, "bankAmount", bank));
        return bill(actor, id);
    }

    @Transactional
    public Map<String, Object> complete(Identity actor, UUID id) {
        var bill = lockBill(actor, id);
        if ("SETTLED".equals(bill.get("status"))) return bill(actor, id);
        BigDecimal received = decimal(bill, "paid");
        BigDecimal cash = decimal(bill, "plannedCash");
        if (received.add(cash).add(decimal(bill, "depositApplied")).compareTo(decimal(bill, "amount")) != 0) throw fail(409, "Bill chưa được thanh toán đủ; chờ SePay xác nhận hoặc nhập tiền mặt.");
        UUID bookingId = (UUID) bill.get("bookingId");
        var allocation = repository.currentAllocationForUpdate(bookingId);
        if (allocation == null || !"OCCUPIED".equals(allocation.state())) throw fail(409, "Không có phiên sử dụng đang hoạt động.");
        // Tiền mặt chỉ thành khoản thu chính thức cùng giao dịch hoàn tất bill.
        if (cash.signum() > 0) {
            BigDecimal tendered = decimal(bill, "plannedCashTendered");
            db.update("""
                INSERT INTO gcmb.payment(branch_id,booking_id,bill_id,purpose,method,amount,cash_tendered,cash_change,received_at,received_by,idempotency_key)
                VALUES (?,?,?,'BILL','CASH',?,?,?,CURRENT_TIMESTAMP,?,?)
                """, branch(actor), bookingId, id, cash, tendered, tendered.subtract(cash), actor.id(), "CHECKOUT-CASH-" + id + "-FINAL");
        }
        received = received.add(cash);
        db.update("UPDATE gcmb.bill SET status='SETTLED',completed_at=CURRENT_TIMESTAMP,planned_cash=0,planned_cash_tendered=0 WHERE id=?", id);
        Timestamp checkout = (Timestamp) bill.get("checkoutAt");
        db.update("UPDATE gcmb.room_allocation SET state='FINISHED',actual_end=? WHERE id=?", checkout, allocation.id());
        db.update("UPDATE gcmb.booking SET status='COMPLETED',actual_checkout=? WHERE id=?", checkout, bookingId);
        db.update("UPDATE gcmb.room SET operating_status='AVAILABLE',status_reason='Khách đã checkout',expected_ready_at=NULL WHERE id=?", allocation.roomId());
        db.update("""
            INSERT INTO gcmb.room_operating_status_history(room_id,branch_id,previous_status,new_status,reason,changed_by)
            VALUES (?, ?, ?, 'AVAILABLE','Khách đã checkout',?)
            """, allocation.roomId(), branch(actor), allocation.operatingStatus(), actor.id());
        repository.event(bookingId, actor.id(), "BOOKING_CHECKED_OUT", null, Map.of("billId", id.toString()));
        audit(actor.id(), branch(actor), bookingId, "CHECKOUT_BOOKING", Map.of("billId", id, "amount", received, "depositAmount", decimal(bill, "depositApplied")));
        return bill(actor, id);
    }

    @Transactional
    public Map<String, Object> renew(Identity actor, UUID id) {
        var bill = lockBill(actor, id);
        // Gia hạn cùng mã tham chiếu; không tạo mã thứ hai có thể nhận tiền trùng.
        int changed = db.update("""
            UPDATE gcmb.payment_request SET expires_at=CURRENT_TIMESTAMP+interval '30 minutes'
            WHERE bill_id=? AND status='PENDING' AND expires_at<CURRENT_TIMESTAMP
            """, id);
        if (changed > 0) {
            audit(actor.id(), branch(actor), (UUID) bill.get("bookingId"), "RENEW_BILL_QR", Map.of("billId", id));
        }
        return bill(actor, id);
    }

    /** Hủy QR và xóa phân bổ tạm để form trở về ban đầu; không đảo khoản thu thật. */
    @Transactional
    public Map<String, Object> changeMethod(Identity actor, UUID id) {
        // Khóa cùng thứ tự với webhook: giao dịch đã ghi nhận sẽ chặn đổi phương thức.
        var bill = lockBill(actor, id);
        if (!"ISSUED".equals(bill.get("status"))) {
            throw fail(409, "Chỉ đổi phương thức cho bill đang chờ thanh toán.");
        }
        if (!db.queryForList("SELECT id FROM gcmb.payment p WHERE bill_id=? AND NOT EXISTS (SELECT 1 FROM gcmb.checkout_cash_reclassification c WHERE c.payment_id=p.id)", id).isEmpty()) {
            throw fail(409, "Bill đã nhận thanh toán thực tế, không thể đặt lại phương thức thanh toán.");
        }
        int changed = db.update("""
            UPDATE gcmb.payment_request SET status='CANCELLED'
            WHERE bill_id=? AND status IN ('PENDING','EXPIRED')
            """, id);
        db.update("UPDATE gcmb.bill SET planned_cash=0,planned_cash_tendered=0 WHERE id=?", id);
        if (changed > 0 || decimal(bill, "plannedCash").signum() > 0) {
            audit(actor.id(), branch(actor), (UUID) bill.get("bookingId"),
                "CHANGE_BILL_PAYMENT_METHOD", Map.of("billId", id));
        }
        return bill(actor, id);
    }

    @Transactional
    public void webhook(String authorization, Webhook payload) {
        if (!sepay.authorized(authorization)) throw fail(401, "Chứng thực webhook không hợp lệ.");
        if (payload == null || payload.id() == null || payload.id() <= 0) throw fail(400, "Thiếu mã giao dịch SePay.");
        money(payload.transferAmount());
        if (payload.transferAmount().signum() <= 0) throw fail(400, "Số tiền giao dịch phải lớn hơn 0.");
        if (!sepay.account.equals(payload.accountNumber()) || !sepay.bank.equalsIgnoreCase(payload.gateway())) throw fail(400, "Giao dịch không thuộc tài khoản ngân hàng đã cấu hình.");
        if (!List.of("in", "out").contains(payload.transferType())) throw fail(400, "Loại giao dịch không hợp lệ.");
        UUID account = bankAccount();
        String content = payload.content() == null ? "" : payload.content();
        var found = Pattern.compile("(?i)(?<![A-Z0-9])GZ[A-F0-9]{20}(?![A-Z0-9])").matcher(content);
        String reference = payload.code() != null && payload.code().matches("(?i)GZ[A-F0-9]{20}") ? payload.code().toUpperCase() : found.find() ? found.group().toUpperCase() : "";
        var requests = db.queryForList("SELECT * FROM gcmb.payment_request WHERE reference_code=? AND purpose IN ('BILL','DEPOSIT')", reference);
        if (!requests.isEmpty()) {
            var request = requests.get(0);
            // Cùng thứ tự khóa với thu tiền mặt/hoàn tất để tránh race và deadlock.
            lockBooking((UUID) request.get("booking_id"), (UUID) request.get("branch_id"));
            if ("BILL".equals(request.get("purpose"))) db.queryForList("SELECT id FROM gcmb.bill WHERE id=? FOR UPDATE", request.get("bill_id"));
        }
        Instant occurred;
        try { occurred = LocalDateTime.parse(payload.transactionDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(); }
        catch (RuntimeException e) { throw fail(400, "Ngày giao dịch SePay không hợp lệ."); }
        UUID transaction = UUID.randomUUID();
        int inserted = db.update("""
            INSERT INTO gcmb.bank_transaction(id,account_id,provider,external_id,direction,amount,transfer_content,occurred_at,match_status,raw_payload)
            VALUES (?,?,'SEPAY',?,?,?,?,?,'UNMATCHED',CAST(? AS jsonb)) ON CONFLICT(provider,account_id,external_id) DO NOTHING
            """, transaction, account, payload.id().toString(), "in".equals(payload.transferType()) ? "IN" : "OUT",
            payload.transferAmount(), content, Timestamp.from(occurred), json.writeValueAsString(payload));
        if (inserted == 0) return;
        if (requests.isEmpty()) return;
        var request = db.queryForMap("SELECT * FROM gcmb.payment_request WHERE id=? FOR UPDATE", requests.get(0).get("id"));
        boolean matches = "in".equals(payload.transferType()) && "PENDING".equals(request.get("status"))
            && account.equals(request.get("account_id"))
            && payload.transferAmount().compareTo((BigDecimal) request.get("expected_amount")) == 0
            && !occurred.isBefore(((Timestamp) request.get("created_at")).toInstant().minusSeconds(2))
            && !occurred.isAfter(((Timestamp) request.get("expires_at")).toInstant());
        boolean deposit = "DEPOSIT".equals(request.get("purpose"));
        if (deposit) {
            var booking = repository.booking((UUID) request.get("booking_id"), (UUID) request.get("branch_id"));
            matches = matches && booking != null && "CONFIRMED".equals(booking.status())
                && repository.depositReceivedAmount(booking.id()).signum() == 0
                && BookingPricingService.depositFor(repository.activeChargeTotal(booking.id()))
                    .compareTo((BigDecimal) request.get("expected_amount")) == 0;
        }
        if (!matches) {
            db.update("UPDATE gcmb.bank_transaction SET match_status='EXCEPTION' WHERE id=?", transaction);
            return;
        }
        UUID paymentId = UUID.randomUUID();
        db.update("""
            INSERT INTO gcmb.payment(id,branch_id,booking_id,bill_id,purpose,method,amount,request_id,bank_transaction_id,received_at,idempotency_key)
            VALUES (?,?,?,?,?,'BANK_TRANSFER',?,?,?, ?,?)
            """, paymentId, request.get("branch_id"), request.get("booking_id"), request.get("bill_id"), request.get("purpose"), payload.transferAmount(), request.get("id"), transaction,
            Timestamp.from(occurred), "SEPAY-" + payload.id());
        if (deposit) {
            repository.createDepositAccount((UUID) request.get("booking_id"));
            db.update("""
                INSERT INTO gcmb.deposit_entry(deposit_id,kind,amount,payment_id,reason,idempotency_key)
                SELECT id,'RECEIVE',?,?,'SePay xác nhận tiền cọc',? FROM gcmb.deposit WHERE booking_id=?
                """, payload.transferAmount(), paymentId, "SEPAY-DEPOSIT-" + payload.id(), request.get("booking_id"));
        }
        audit(null, (UUID) request.get("branch_id"), (UUID) request.get("booking_id"),
            deposit ? "CONFIRM_SEPAY_DEPOSIT" : "CONFIRM_SEPAY_PAYMENT",
            deposit ? Map.of("amount", payload.transferAmount()) : Map.of("billId", request.get("bill_id"), "amount", payload.transferAmount()));
    }

    private boolean hasPendingRefund(UUID bookingId) {
        return !db.queryForList("""
            SELECT id FROM gcmb.refund_request WHERE booking_id=? AND kind='DEPOSIT'
                AND status IN ('REQUESTED','APPROVED','PROCESSING')
            """, bookingId).isEmpty();
    }

    private UUID bankAccount() {
        String code = "SEPAY-" + UUID.nameUUIDFromBytes((sepay.bank + ":" + sepay.account).getBytes(StandardCharsets.UTF_8)).toString();
        db.update("""
            INSERT INTO gcmb.financial_account(code,name,kind,bank_name,bank_account_number,account_holder,active)
            VALUES (?,'Ngân hàng nhận thanh toán SePay','BANK',?,?,?,true) ON CONFLICT(code) DO NOTHING
            """, code, sepay.bank, sepay.account, sepay.holder);
        return db.queryForObject("SELECT id FROM gcmb.financial_account WHERE code=? AND active=true", UUID.class, code);
    }

    private List<Map<String, Object>> sourceLines(UUID booking) {
        return db.queryForList("""
            SELECT c.id AS "chargeId",NULL::uuid AS "orderId",c.description,c.quantity,c.unit_price AS "unitPrice",c.amount,
                CASE WHEN c.kind='SURCHARGE' THEN 'SURCHARGE' ELSE 'ROOM' END AS category
            FROM gcmb.booking_charge c WHERE c.booking_id=? AND c.status='ACTIVE'
            UNION ALL
            SELECT NULL::uuid,l.id,l.name_snapshot,l.quantity,l.unit_price,round(l.quantity*l.unit_price),'FNB'
            FROM gcmb.order_line l JOIN gcmb.fb_order o ON o.id=l.order_id
            WHERE l.booking_id=? AND o.status='SERVED' AND l.unit_price>0
            """, booking, booking);
    }

    private List<Map<String, Object>> pendingOrders(UUID booking) {
        return db.queryForList("SELECT id,status FROM gcmb.fb_order WHERE booking_id=? AND status IN ('PENDING','ACCEPTED')", booking);
    }

    private Map<String, Object> lockBill(Identity actor, UUID id) {
        var found = bill(actor, id);
        lockBooking((UUID) found.get("bookingId"), branch(actor));
        db.queryForList("SELECT id FROM gcmb.bill WHERE id=? FOR UPDATE", id);
        return bill(actor, id);
    }

    private void lockBooking(UUID id, UUID branch) {
        if (db.queryForList("SELECT id FROM gcmb.booking WHERE id=? AND branch_id=? FOR UPDATE", UUID.class, id, branch).isEmpty()) throw fail(404, "Không tìm thấy booking.");
    }

    private UUID branch(Identity actor) {
        if (actor == null || !"RECEPTIONIST".equals(actor.role())) throw fail(403, "Chỉ lễ tân được thực hiện checkout.");
        var scope = repository.branchScope(actor.id());
        if (scope == null) throw fail(403, "Tài khoản chưa được phân công tại cơ sở đang hoạt động.");
        return scope.id();
    }

    private void audit(UUID actor, UUID branch, UUID booking, String action, Map<String, Object> data) {
        var snapshot = new LinkedHashMap<>(data);
        snapshot.put("actorRole", actor == null ? "SYSTEM" : "RECEPTIONIST");
        db.update("""
            INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,after_data)
            VALUES (?,?,?,'BOOKING',?,CAST(? AS jsonb))
            """, actor, branch, action, booking, json.writeValueAsString(snapshot));
    }

    private static BigDecimal total(List<Map<String, Object>> lines) {
        return lines.stream().map(line -> (BigDecimal) line.get("amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal decimal(Map<String, Object> row, String key) { return (BigDecimal) row.get(key); }
    private static String encode(Object value) { return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8); }
    private static void requireInHouse(String status) { if (!"IN_HOUSE".equals(status)) throw fail(409, "Chỉ checkout booking đã check-in."); }
    private static void money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 0 || value.compareTo(new BigDecimal("999999999999999999")) > 0) throw fail(400, "Số tiền phải là số nguyên VND không âm, tối đa 18 chữ số.");
    }
    private static BookingFailure fail(int status, String message) { return new BookingFailure(status, "form", message); }
}
