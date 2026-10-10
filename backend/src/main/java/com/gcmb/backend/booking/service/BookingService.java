package com.gcmb.backend.booking.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.booking.dto.BookingDtos;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.BookingRepository.AllocationRow;
import com.gcmb.backend.booking.repository.BookingRepository.BookingRow;
import com.gcmb.backend.booking.repository.BookingRepository.BranchScope;
import com.gcmb.backend.booking.repository.BookingRepository.ChargeRow;
import com.gcmb.backend.booking.repository.BookingRepository.ComboRow;
import com.gcmb.backend.booking.repository.BookingRepository.RoomRow;
import com.gcmb.backend.booking.repository.BookingRepository.RoomTypeRow;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private static final int REFUND_CUTOFF_MINUTES = 40;
    private static final Set<String> CHANNELS = Set.of("WALK_IN", "FACEBOOK", "ZALO", "PHONE", "OTHER");
    private static final Set<String> STATUSES = Set.of("DRAFT", "CONFIRMED", "IN_HOUSE", "COMPLETED", "CANCELLED", "NO_SHOW");

    private final BookingRepository repository;
    private final BookingPricingService pricing;
    private final ObjectMapper json;

    public BookingService(BookingRepository repository, BookingPricingService pricing, ObjectMapper json) {
        this.repository = repository; this.pricing = pricing; this.json = json;
    }

    @Transactional(readOnly = true)
    public BookingDtos.BookingOptions options(Identity identity) {
        BranchScope branch = branch(identity);
        List<BookingDtos.RoomTypeOption> roomTypes = repository.roomTypes().stream().map(type ->
            new BookingDtos.RoomTypeOption(type.id(), type.code(), type.name(), type.mode(), type.capacity(),
                repository.combos(type.id()).stream().map(BookingService::comboOption).toList())).toList();
        return new BookingDtos.BookingOptions(branch.id(), branch.name(), branch.timezone().getId(), roomTypes);
    }

    @Transactional(readOnly = true)
    public BookingDtos.Availability availability(Identity identity, UUID roomTypeId, Instant start, Instant end,
            String pricingMode, UUID comboId) {
        BranchScope branch = branch(identity);
        validatePeriod(start, end);
        if (!start.isAfter(Instant.now())) throw new BookingFailure(400, "plannedStart", "Thời gian đặt phòng phải ở tương lai");
        RoomTypeRow roomType = repository.roomType(roomTypeId);
        if (roomType == null) throw new BookingFailure(404, "roomTypeId", "Không tìm thấy loại phòng đang hoạt động");
        BookingDtos.PriceQuote quote = pricing.quote(roomType.id(), branch.id(), branch.timezone(), start, end, pricingMode, comboId);
        List<BookingDtos.AvailableRoom> rooms = repository.availableRooms(branch.id(), roomType.id(), start, end).stream()
            .map(room -> new BookingDtos.AvailableRoom(room.id(), room.code(), room.name(), room.capacity(), room.operatingStatus())).toList();
        return new BookingDtos.Availability(branch.id(), roomType.id(), start, end, rooms, quote);
    }

    @Transactional(readOnly = true)
    public BookingDtos.BookingPage list(Identity identity, String requestedStatus, LocalDate dateFrom, LocalDate dateTo,
            String search, Boolean hasDeposit, int page, int size) {
        BranchScope branch = branch(identity);
        String status = normalizeStatus(requestedStatus);
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom))
            throw new BookingFailure(400, "dateTo", "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu");
        if (page < 0) throw new BookingFailure(400, "page", "Số trang không hợp lệ");
        if (size < 1 || size > 100) throw new BookingFailure(400, "size", "Số dòng mỗi trang phải từ 1 đến 100");
        String normalizedSearch = search == null ? null : search.trim();
        if (normalizedSearch != null && normalizedSearch.length() > 100)
            throw new BookingFailure(400, "search", "Từ khóa tìm kiếm không được vượt quá 100 ký tự");
        long total = repository.countBookings(branch.id(), status, dateFrom, dateTo, branch.timezone(), normalizedSearch, hasDeposit);
        List<BookingDtos.BookingSummary> rows = repository.findBookings(branch.id(), status, dateFrom, dateTo,
            branch.timezone(), normalizedSearch, hasDeposit, page, size);
        int pages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new BookingDtos.BookingPage(rows, page, size, total, pages);
    }

    @Transactional(readOnly = true)
    public BookingDtos.BookingDetail detail(Identity identity, UUID bookingId) {
        BranchScope branch = branch(identity);
        BookingRow booking = repository.booking(bookingId, branch.id());
        if (booking == null) throw notFound();
        return detail(branch.id(), booking);
    }

    @Transactional
    public BookingDtos.BookingDetail create(Identity identity, BookingDtos.CreateBookingRequest request) {
        if (request == null) throw new BookingFailure(400, "form", "Dữ liệu booking không hợp lệ");
        BranchScope branch = branch(identity);
        UUID actorId = identity.id();
        validatePeriod(request.plannedStart(), request.plannedEnd());
        String channel = normalizeChannel(request.channel());
        Instant now = Instant.now();
        if ("WALK_IN".equals(channel)) {
            if (request.plannedStart().isBefore(now.minusSeconds(30)))
                throw new BookingFailure(400, "plannedStart", "Thời gian nhận phòng vãng lai không được ở quá khứ");
        } else if (!request.plannedStart().isAfter(now)) {
            throw new BookingFailure(400, "plannedStart", "Thời gian đặt trước phải ở tương lai");
        }

        RoomRow room = repository.lockAvailableRoom(request.roomId(), branch.id());
        if (room == null) throw new BookingFailure(409, "roomId", "Phòng không tồn tại tại chi nhánh hoặc hiện không hoạt động");
        if (repository.hasRoomOverlap(room.id(), request.plannedStart(), request.plannedEnd(), null))
            throw roomConflict();
        BookingDtos.PriceQuote quote = pricing.quote(room.roomTypeId(), branch.id(), branch.timezone(),
            request.plannedStart(), request.plannedEnd(), request.pricingMode(), request.comboId());
        String contactName = required(request.contactName(), "contactName", "Tên khách", 200);
        String contactPhone = nullable(request.contactPhone(), "contactPhone", 30);
        String note = nullable(request.note(), "note", 2000);
        String code = "BK-" + java.time.LocalDate.now(branch.timezone()).toString().replace("-", "") + "-" +
            UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        UUID bookingId = repository.insertBooking(branch.id(), code, contactName, contactPhone, channel,
            request.plannedStart(), request.plannedEnd(), REFUND_CUTOFF_MINUTES, actorId, note);
        UUID allocationId = insertAllocation(branch, bookingId, room.id(), request.plannedStart(), request.plannedEnd(), actorId);
        repository.createDepositAccount(bookingId);
        for (BookingDtos.ChargeLine charge : quote.charges()) repository.insertCharge(bookingId, allocationId, charge);
        repository.event(bookingId, actorId, "BOOKING_CREATED", null, detailsMap(
            "branchId", branch.id(), "roomId", room.id(), "startsAt", request.plannedStart(),
            "endsAt", request.plannedEnd(), "pricingMode", quote.pricingMode(),
            "expectedAmount", quote.expectedAmount(), "depositRequired", quote.depositRequired()));
        return detail(branch.id(), repository.booking(bookingId, branch.id()));
    }

    @Transactional
    public BookingDtos.BookingDetail update(Identity identity, UUID bookingId, BookingDtos.UpdateBookingRequest request) {
        if (request == null) throw new BookingFailure(400, "form", "Dữ liệu cập nhật không hợp lệ");
        BranchScope branch = branch(identity);
        UUID actorId = identity.id();
        lockBooking(bookingId, branch.id());
        BookingRow booking = repository.booking(bookingId, branch.id());
        if (!"CONFIRMED".equals(booking.status()))
            throw new BookingFailure(409, "form", "Chỉ có thể sửa booking đã xác nhận và chưa check-in");
        Instant now = Instant.now();
        if (!booking.plannedStart().isAfter(now)) throw new BookingFailure(409, "form", "Không thể sửa booking đã đến giờ sử dụng");
        AllocationRow current = repository.currentAllocationForUpdate(bookingId);
        if (current == null) throw new BookingFailure(409, "roomId", "Booking không còn giữ phòng để cập nhật");

        UUID roomId = request.roomId() == null ? current.roomId() : request.roomId();
        Instant start = request.plannedStart() == null ? booking.plannedStart() : request.plannedStart();
        Instant end = request.plannedEnd() == null ? booking.plannedEnd() : request.plannedEnd();
        validatePeriod(start, end);
        if (!start.isAfter(now)) throw new BookingFailure(400, "plannedStart", "Thời gian đặt phòng phải ở tương lai");
        boolean allocationChanged = !roomId.equals(current.roomId()) || !start.equals(current.startsAt()) || !end.equals(current.endsAt());

        RoomRow targetRoom = null;
        if (allocationChanged) {
            targetRoom = repository.lockAvailableRoom(roomId, branch.id());
            if (targetRoom == null) throw new BookingFailure(409, "roomId", "Phòng không tồn tại tại chi nhánh hoặc hiện không hoạt động");
            repository.releaseAllocation(current.id(), "Thay đổi lịch đặt");
            if (repository.hasRoomOverlap(roomId, start, end, bookingId)) throw roomConflict();
        }
        String contactName = request.contactName() == null ? booking.contactName() : required(request.contactName(), "contactName", "Tên khách", 200);
        String contactPhone = request.contactPhone() == null ? booking.contactPhone() : nullable(request.contactPhone(), "contactPhone", 30);
        String note = request.note() == null ? booking.note() : nullable(request.note(), "note", 2000);
        repository.updateBooking(bookingId, contactName, contactPhone, start, end, note);
        if (allocationChanged) insertAllocation(branch, bookingId, targetRoom.id(), start, end, actorId);
        repository.event(bookingId, actorId, "BOOKING_UPDATED", null, detailsMap(
            "previousRoomId", current.roomId(), "roomId", roomId,
            "previousStart", booking.plannedStart(), "startsAt", start,
            "previousEnd", booking.plannedEnd(), "endsAt", end,
            "expectedAmountPreserved", repository.activeChargeTotal(bookingId)));
        return detail(branch.id(), repository.booking(bookingId, branch.id()));
    }

    @Transactional
    public BookingDtos.CancellationResult cancel(Identity identity, UUID bookingId, BookingDtos.CancelBookingRequest request) {
        BranchScope branch = branch(identity);
        UUID actorId = identity.id();
        lockBooking(bookingId, branch.id());
        BookingRow booking = repository.booking(bookingId, branch.id());
        if (!"CONFIRMED".equals(booking.status()) && !"DRAFT".equals(booking.status()))
            throw new BookingFailure(409, "form", "Booking hiện tại không thể hủy");
        Instant now = Instant.now();
        if (!booking.plannedStart().isAfter(now))
            throw new BookingFailure(409, "form", "Booking đã đến giờ sử dụng; hãy xử lý theo luồng no-show hoặc check-in");
        AllocationRow allocation = repository.currentAllocationForUpdate(bookingId);
        if (allocation == null || !"RESERVED".equals(allocation.state()))
            throw new BookingFailure(409, "form", "Booking không còn lịch giữ phòng để hủy");
        String reason = request == null ? null : nullable(request.reason(), "reason", 1000);
        BigDecimal deposit = repository.depositBalance(bookingId);
        long secondsUntilStart = Duration.between(now, booking.plannedStart()).getSeconds();
        boolean eligible = secondsUntilStart >= booking.refundCutoffMinutes() * 60L;
        String outcome = deposit.signum() == 0 ? "NO_DEPOSIT" : eligible ? "REFUND_ELIGIBLE" : "NON_REFUNDABLE";
        if (deposit.signum() > 0 && !eligible)
            repository.forfeitDeposit(bookingId, actorId, deposit, "Hủy booking trong thời hạn không hoàn cọc", UUID.randomUUID().toString());
        repository.releaseAllocation(allocation.id(), "Booking đã hủy");
        repository.cancelBooking(bookingId, now, reason);
        repository.event(bookingId, actorId, "BOOKING_CANCELLED", reason, detailsMap(
            "cancelledAt", now, "depositOutcome", outcome, "depositAmount", deposit,
            "refundCutoffMinutes", booking.refundCutoffMinutes(), "releasedRoomId", allocation.roomId()));
        BookingDtos.BookingDetail detail = detail(branch.id(), repository.booking(bookingId, branch.id()));
        BookingDtos.DepositView depositView = new BookingDtos.DepositView(repository.depositBalance(bookingId), outcome, booking.refundCutoffMinutes());
        detail = new BookingDtos.BookingDetail(detail.booking(), detail.branchId(), detail.note(), detail.createdAt(),
            detail.allocations(), detail.charges(), depositView);
        return new BookingDtos.CancellationResult(detail, outcome, deposit, now);
    }

    private BookingDtos.BookingDetail detail(UUID branchId, BookingRow booking) {
        if (booking == null) throw notFound();
        List<AllocationRow> allocations = repository.allocations(booking.id());
        AllocationRow latest = allocations.isEmpty() ? null : allocations.get(allocations.size() - 1);
        BigDecimal depositBalance = repository.depositBalance(booking.id());
        BookingDtos.BookingSummary summary = new BookingDtos.BookingSummary(booking.id(), booking.code(), booking.contactName(),
            booking.contactPhone(), booking.channel(), booking.plannedStart(), booking.plannedEnd(), booking.status(),
            latest == null ? null : latest.roomId(), latest == null ? null : latest.roomCode(), latest == null ? null : latest.roomName(),
            repository.activeChargeTotal(booking.id()), depositBalance);
        List<BookingDtos.AllocationView> allocationViews = allocations.stream().map(a -> new BookingDtos.AllocationView(
            a.id(), a.roomId(), a.roomCode(), a.roomName(), a.startsAt(), a.endsAt(), a.state(), a.changeReason(), a.createdAt())).toList();
        List<BookingDtos.ChargeView> chargeViews = repository.charges(booking.id()).stream().map(this::chargeView).toList();
        return new BookingDtos.BookingDetail(summary, branchId, booking.note(), booking.createdAt(), allocationViews,
            chargeViews, new BookingDtos.DepositView(depositBalance, null, booking.refundCutoffMinutes()));
    }

    private BookingDtos.ChargeView chargeView(ChargeRow charge) {
        Map<String, Object> snapshot = Map.of();
        try {
            if (charge.snapshot() != null) snapshot = json.readValue(charge.snapshot(), new TypeReference<>() {});
        } catch (Exception ignored) {
            // The original JSON text remains in the database; a malformed value is not expected after the JSONB cast.
        }
        return new BookingDtos.ChargeView(charge.id(), charge.kind(), charge.description(), charge.quantity(),
            charge.unitPrice(), charge.amount(), charge.status(), charge.priceRuleId(), charge.comboId(), snapshot, charge.createdAt());
    }

    private BranchScope branch(Identity identity) {
        BranchScope scope = repository.branchScope(identity.id());
        if (scope == null) throw new BookingFailure(403, "form", "Tài khoản chưa được phân công tại chi nhánh đang hoạt động");
        return scope;
    }

    private void lockBooking(UUID bookingId, UUID branchId) {
        if (repository.lockBookingId(bookingId, branchId) == null) throw notFound();
    }

    private UUID insertAllocation(BranchScope branch, UUID bookingId, UUID roomId, Instant start, Instant end, UUID actorId) {
        try { return repository.insertAllocation(branch.id(), bookingId, roomId, start, end, actorId); }
        catch (DataIntegrityViolationException e) {
            String detail = e.getMostSpecificCause() == null ? "" : e.getMostSpecificCause().getMessage();
            if (detail != null && (detail.contains("no_room_overlap") || detail.contains("no_booking_parallel_rooms"))) throw roomConflict();
            throw e;
        }
    }

    private static BookingDtos.ComboOption comboOption(ComboRow combo) {
        return new BookingDtos.ComboOption(combo.id(), combo.code(), combo.version(), combo.name(), combo.durationMinutes(),
            combo.price(), combo.depositRequired(), combo.validFrom(), combo.validTo());
    }

    private static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) throw new BookingFailure(400, "channel", "Vui lòng chọn kênh nhận đặt phòng");
        String value = channel.trim().toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(value)) throw new BookingFailure(400, "channel", "Kênh nhận đặt phòng không hợp lệ");
        return value;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return null;
        String value = status.trim().toUpperCase(Locale.ROOT);
        if ("PENDING".equals(value)) value = "CONFIRMED";
        if ("CHECKED_IN".equals(value)) value = "IN_HOUSE";
        if (!STATUSES.contains(value)) throw new BookingFailure(400, "status", "Trạng thái booking không hợp lệ");
        return value;
    }

    private static String required(String value, String field, String label, int max) {
        if (value == null || value.trim().isEmpty()) throw new BookingFailure(400, field, "Vui lòng nhập " + label.toLowerCase(Locale.ROOT));
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new BookingFailure(400, field, label + " không được vượt quá " + max + " ký tự");
        return trimmed;
    }

    private static String nullable(String value, String field, int max) { return nullable(value, field, max, false); }
    private static String nullable(String value, String field, int max, boolean allowEmpty) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return allowEmpty ? "" : null;
        if (trimmed.length() > max) throw new BookingFailure(400, field, "Trường dữ liệu không được vượt quá " + max + " ký tự");
        return trimmed;
    }

    private static void validatePeriod(Instant start, Instant end) {
        if (start == null) throw new BookingFailure(400, "plannedStart", "Vui lòng nhập thời gian bắt đầu");
        if (end == null || !end.isAfter(start)) throw new BookingFailure(400, "plannedEnd", "Thời gian kết thúc phải sau thời gian bắt đầu");
    }

    private static BookingFailure roomConflict() {
        return new BookingFailure(409, "roomId", "Phòng đã có booking khác trong khoảng thời gian này");
    }

    private static BookingFailure notFound() { return new BookingFailure(404, "form", "Không tìm thấy booking trong phạm vi chi nhánh của bạn"); }

    private static Map<String, Object> detailsMap(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }
}
