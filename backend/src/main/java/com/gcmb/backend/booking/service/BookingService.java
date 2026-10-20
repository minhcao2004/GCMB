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
import com.gcmb.backend.booking.repository.BookingRepository.MeterRow;
import com.gcmb.backend.booking.repository.BookingRepository.PricingRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

    // Kết hợp truy vấn, tính giá và snapshot JSON trong các giao dịch booking.
    public BookingService(BookingRepository repository, BookingPricingService pricing, ObjectMapper json) {
        this.repository = repository; this.pricing = pricing; this.json = json;
    }

    @Transactional(readOnly = true)
    // Nạp loại phòng và combo đang hoạt động trong phạm vi chi nhánh của lễ tân.
    public BookingDtos.BookingOptions options(Identity identity) {
        BranchScope branch = branch(identity);
        List<BookingDtos.RoomTypeOption> roomTypes = repository.roomTypes().stream().map(type ->
            new BookingDtos.RoomTypeOption(type.id(), type.code(), type.name(), type.mode(), type.capacity(),
                repository.combos(type.id()).stream().map(BookingService::comboOption).toList())).toList();
        return new BookingDtos.BookingOptions(branch.id(), branch.name(), branch.timezone().getId(), roomTypes);
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.RoomSummary> rooms(Identity identity) {
        return repository.rooms(branch(identity).id()).stream()
            .map(room -> new BookingDtos.RoomSummary(room.id(), room.code(), room.name(), room.capacity(),
                room.roomTypeId(), room.roomTypeName(), room.operatingStatus())).toList();
    }

    @Transactional
    public void finishCleaning(Identity identity, UUID roomId) {
        UUID branchId = branch(identity).id();
        String status = repository.lockRoomStatus(branchId, roomId);
        if (status == null) throw new BookingFailure(404, "form", "Không tìm thấy phòng trong cơ sở của bạn");
        if (!"CLEANING".equals(status) && !"AVAILABLE".equals(status))
            throw new BookingFailure(409, "form", "Chỉ xác nhận dọn xong cho phòng đang dọn");
        if (repository.roomHasOccupant(branchId, roomId))
            throw new BookingFailure(409, "form", "Phòng vẫn có khách đang sử dụng; cần checkout trước");
        if ("CLEANING".equals(status)) repository.completeRoomCleaning(branchId, roomId, identity.id());
    }

    @Transactional(readOnly = true)
    // Kiểm tra khoảng đặt trong tương lai, trả phòng còn trống và báo giá từ cấu hình.
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
    // Liệt kê phòng có thể chọn khi sửa booking, bỏ qua chính lượt giữ phòng hiện tại.
    public BookingDtos.BookingEditOptions editOptions(Identity identity, UUID bookingId, Instant start, Instant end,
                                                      String pricingMode, UUID comboId) {
        BranchScope branch = branch(identity);
        validatePeriod(start, end);
        BookingRow booking = repository.booking(bookingId, branch.id());
        if (booking == null) throw notFound();
        if (!"CONFIRMED".equals(booking.status()) || !booking.plannedStart().isAfter(Instant.now()))
            throw new BookingFailure(409, "form", "Chỉ có thể sửa booking đã xác nhận và chưa đến giờ sử dụng");
        if (!start.isAfter(Instant.now()))
            throw new BookingFailure(400, "plannedStart", "Thời gian đặt phòng phải ở tương lai");
        AllocationRow current = repository.currentAllocation(bookingId);
        if (current == null) throw new BookingFailure(409, "roomId", "Booking không còn phòng được giữ");
        BookingDtos.PriceQuote quote = editQuote(branch, booking, current, start, end, pricingMode, comboId);
        boolean comboBooking = "COMBO".equals(quote.pricingMode());
        List<BookingDtos.AvailableRoom> rooms = repository.availableRoomsForEdit(branch.id(), start, end, bookingId).stream()
            .filter(room -> room.capacity() >= booking.guestCount()
                && (!comboBooking || room.roomTypeId().equals(current.roomTypeId())))
            .map(room -> new BookingDtos.AvailableRoom(room.id(), room.code(), room.name(), room.capacity(), room.operatingStatus())).toList();
        List<BookingDtos.ComboOption> combos = repository.combos(current.roomTypeId()).stream()
            .map(BookingService::comboOption).toList();
        return new BookingDtos.BookingEditOptions(rooms, combos, quote);
    }

    @Transactional(readOnly = true)
    // Lọc và phân trang booking theo trạng thái, ngày địa phương và tiền cọc của chi nhánh.
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
    // Chỉ trả chi tiết booking thuộc chi nhánh mà tài khoản được phân công.
    public BookingDtos.BookingDetail detail(Identity identity, UUID bookingId) {
        BranchScope branch = branch(identity);
        BookingRow booking = repository.booking(bookingId, branch.id());
        if (booking == null) throw notFound();
        return detail(branch.id(), booking);
    }

    @Transactional(readOnly = true)
    // Tập hợp phòng đổi được và combo gia hạn cho booking đang sử dụng.
    public BookingDtos.StayOptions stayOptions(Identity identity, UUID bookingId) {
        BranchScope branch = branch(identity);
        BookingRow booking = repository.booking(bookingId, branch.id());
        if (booking == null) throw notFound();
        if (!"IN_HOUSE".equals(booking.status()))
            throw new BookingFailure(409, "form", "Chỉ booking đang sử dụng phòng mới có thể đổi phòng hoặc gia hạn");
        AllocationRow allocation = repository.currentAllocation(bookingId);
        if (allocation == null || !"OCCUPIED".equals(allocation.state()))
            throw new BookingFailure(409, "roomId", "Không tìm thấy chặng phòng đang được sử dụng");
        Instant now = Instant.now();
        List<BookingDtos.AvailableRoom> available = !booking.plannedEnd().isAfter(now) ? List.of() : repository.availableRoomsForEdit(branch.id(), now, booking.plannedEnd(), bookingId)
            .stream().filter(room -> !room.id().equals(allocation.roomId()) && room.capacity() >= booking.guestCount())
            .map(room -> new BookingDtos.AvailableRoom(room.id(), room.code(), room.name(), room.capacity(), room.operatingStatus())).toList();
        List<BookingDtos.ComboOption> combos = repository.combos(allocation.roomTypeId()).stream()
            .filter(combo -> extensionComboAvailableAt(combo, booking.plannedEnd(), branch.timezone()))
            .map(BookingService::comboOption).toList();
        String currentComboName = repository.activeChargesForAllocation(allocation.id()).stream()
            .filter(charge -> charge.comboId() != null || "COMBO".equals(charge.kind())
                || "COMBO".equals(readSnapshot(charge.snapshot()).get("pricingMode")))
            .map(charge -> charge.description())
            .distinct()
            .collect(java.util.stream.Collectors.joining(" · "));
        long currentDurationMinutes = Math.max(0, Duration.between(allocation.startsAt(), allocation.endsAt()).toMinutes());
        return new BookingDtos.StayOptions(allocation.roomId(), allocation.roomCode(), allocation.roomTypeId(), allocation.roomTypeName(),
            booking.plannedEnd(), booking.plannedEnd(), currentComboName.isBlank() ? null : currentComboName,
            currentDurationMinutes, available, combos);
    }

    @Transactional(readOnly = true)
    // Báo trước phần phí đã dùng và phần còn lại khi đổi phòng, chưa ghi dữ liệu.
    public BookingDtos.RoomChangeQuote roomChangeQuote(Identity identity, UUID bookingId, UUID targetRoomId, String cause) {
        BranchScope branch = branch(identity);
        BookingRow booking = requireInHouseBooking(bookingId, branch.id());
        rejectRunningMeter(bookingId);
        AllocationRow current = repository.currentAllocation(bookingId);
        validateActiveAllocation(current);
        Instant now = Instant.now();
        String normalizedCause = normalizeChangeCause(cause);
        RoomRow target = repository.availableRoom(targetRoomId, branch.id());
        validateTargetRoom(target, booking);
        if (target.id().equals(current.roomId())) throw new BookingFailure(400, "targetRoomId", "Vui lòng chọn phòng khác phòng hiện tại");
        if (!now.isBefore(booking.plannedEnd()) || !now.isAfter(current.startsAt()))
            throw new BookingFailure(409, "form", "Chỉ đổi phòng trong thời gian booking đang được sử dụng");
        if (repository.hasRoomOverlap(target.id(), now, booking.plannedEnd(), bookingId)) throw roomConflict();
        RoomChangePlan plan = roomChangePlan(branch, booking, current, target, now, normalizedCause);
        BigDecimal previous = repository.activeChargeTotal(bookingId);
        BigDecimal updated = previous.subtract(plan.originalAmount()).add(plan.usedAmount()).add(plan.nextAmount());
        return new BookingDtos.RoomChangeQuote(previous, updated, updated.subtract(previous), plan.usedAmount(), plan.nextAmount());
    }

    @Transactional
    // Đổi chặng phòng trong một giao dịch và thay các dòng phí bằng phần đã dùng, phần tiếp theo.
    public BookingDtos.RoomChangeResult changeRoom(Identity identity, UUID bookingId, BookingDtos.RoomChangeRequest request) {
        if (request == null) throw new BookingFailure(400, "form", "Dữ liệu đổi phòng không hợp lệ");
        BranchScope branch = branch(identity);
        UUID actorId = identity.id();
        lockBooking(bookingId, branch.id());
        BookingRow booking = requireInHouseBooking(bookingId, branch.id());
        rejectRunningMeter(bookingId);
        AllocationRow current = repository.currentAllocationForUpdate(bookingId);
        validateActiveAllocation(current);
        Instant now = Instant.now();
        String cause = normalizeChangeCause(request.cause());
        String reason = nullable(request.reason(), "reason", 1000);
        RoomRow target = repository.lockAvailableRoom(request.targetRoomId(), branch.id());
        validateTargetRoom(target, booking);
        if (target.id().equals(current.roomId())) throw new BookingFailure(400, "targetRoomId", "Vui lòng chọn phòng khác phòng hiện tại");
        if (!now.isBefore(booking.plannedEnd()) || !now.isAfter(current.startsAt()))
            throw new BookingFailure(409, "form", "Chỉ đổi phòng trong thời gian booking đang được sử dụng");
        if (repository.hasRoomOverlap(target.id(), now, booking.plannedEnd(), bookingId)) throw roomConflict();

        RoomChangePlan plan = roomChangePlan(branch, booking, current, target, now, cause);
        BigDecimal previous = repository.activeChargeTotal(bookingId);
        if (repository.finishOccupiedAllocation(current.id(), now, cause, reason) != 1)
            throw new BookingFailure(409, "form", "Chặng phòng vừa được cập nhật ở một phiên khác; vui lòng tải lại booking");
        UUID nextAllocation = repository.insertChangedAllocation(branch.id(), bookingId, target.id(), now,
            booking.plannedEnd(), actorId, cause, reason);
        if (repository.occupyAllocation(nextAllocation, now) != 1)
            throw new BookingFailure(409, "form", "Không thể kích hoạt chặng phòng mới");
        for (UUID chargeId : plan.chargeIds()) {
            if (repository.supersedeCharge(chargeId) != 1)
                throw new BookingFailure(409, "form", "Các khoản phí vừa được cập nhật ở một phiên khác; vui lòng tải lại booking");
        }
        plan.usedCharges().forEach(line -> repository.insertCharge(bookingId, current.id(), line));
        plan.nextCharges().forEach(line -> repository.insertCharge(bookingId, nextAllocation, line));
        BigDecimal updated = previous.subtract(plan.originalAmount()).add(plan.usedAmount()).add(plan.nextAmount());
        repository.event(bookingId, actorId, "BOOKING_ROOM_CHANGED", reason, detailsMap(
            "previousRoomId", current.roomId(), "previousRoomCode", current.roomCode(),
            "roomId", target.id(), "roomCode", target.code(), "changeCause", cause,
            "changedAt", now, "plannedEnd", booking.plannedEnd(), "previousAmount", previous,
            "updatedAmount", updated, "adjustmentAmount", updated.subtract(previous),
            "usedSegmentAmount", plan.usedAmount(), "nextSegmentAmount", plan.nextAmount()));
        BookingDtos.BookingDetail updatedBooking = detail(branch.id(), repository.booking(bookingId, branch.id()));
        return new BookingDtos.RoomChangeResult(updatedBooking, previous, updated, updated.subtract(previous));
    }

    @Transactional(readOnly = true)
    // Báo giá gia hạn từ giờ trả hiện tại mà chưa giữ thêm phòng.
    public BookingDtos.ExtensionQuote extensionQuote(Identity identity, UUID bookingId, BookingDtos.ExtendStayRequest request) {
        if (request == null) throw new BookingFailure(400, "form", "Chọn combo hoặc số giờ gia hạn");
        BranchScope branch = branch(identity);
        BookingRow booking = requireInHouseBooking(bookingId, branch.id());
        rejectRunningMeter(bookingId);
        AllocationRow current = repository.currentAllocation(bookingId);
        validateActiveAllocation(current);
        return extensionQuote(branch, booking, current, request);
    }

    @Transactional
    // Kiểm tra xung đột rồi gia hạn lịch phòng, phí và lịch sử trong cùng giao dịch.
    public BookingDtos.ExtensionResult extendStay(Identity identity, UUID bookingId, BookingDtos.ExtendStayRequest request) {
        if (request == null) throw new BookingFailure(400, "form", "Chọn combo hoặc số giờ gia hạn");
        BranchScope branch = branch(identity);
        UUID actorId = identity.id();
        lockBooking(bookingId, branch.id());
        BookingRow booking = requireInHouseBooking(bookingId, branch.id());
        rejectRunningMeter(bookingId);
        AllocationRow current = repository.currentAllocationForUpdate(bookingId);
        validateActiveAllocation(current);
        BookingDtos.ExtensionQuote extension = extensionQuote(branch, booking, current, request);
        if (repository.hasRoomOverlap(current.roomId(), extension.startsAt(), extension.endsAt(), bookingId))
            throw new BookingFailure(409, "hours", "Phòng đã có booking khác trong khoảng thời gian gia hạn");
        if (repository.updateInHouseBookingEnd(bookingId, booking.plannedEnd(), extension.endsAt()) != 1
            || repository.updateOccupiedAllocationEnd(current.id(), current.endsAt(), extension.endsAt()) != 1)
            throw new BookingFailure(409, "form", "Booking vừa được gia hạn ở một phiên khác; vui lòng tải lại");

        for (BookingDtos.ChargeLine charge : extension.quote().charges()) {
            Map<String, Object> snapshot = new LinkedHashMap<>(charge.pricingSnapshot());
            snapshot.put("useCase", "UC40");
            snapshot.put("extensionStartsAt", extension.startsAt().toString());
            snapshot.put("extensionEndsAt", extension.endsAt().toString());
            String kind = "COMBO".equals(extension.quote().pricingMode()) ? "COMBO" : "OVERTIME";
            repository.insertCharge(bookingId, current.id(), new BookingDtos.ChargeLine(kind, charge.description(),
                charge.quantity(), charge.unitPrice(), charge.amount(), charge.priceRuleId(), charge.comboId(), snapshot));
        }
        repository.event(bookingId, actorId, "BOOKING_EXTENDED", null, detailsMap(
            "roomId", current.roomId(), "pricingMode", extension.quote().pricingMode(),
            "comboId", request.comboId(), "hours", request.hours(), "startsAt", extension.startsAt(),
            "endsAt", extension.endsAt(), "addedAmount", extension.quote().expectedAmount()));
        return new BookingDtos.ExtensionResult(detail(branch.id(), repository.booking(bookingId, branch.id())),
            extension.startsAt(), extension.endsAt(), extension.quote());
    }

    @Transactional
    // Chốt đơn giá giờ lẻ và giữ phòng đến booking kế tiếp hoặc tối đa 24 giờ.
    public BookingDtos.BookingDetail startHourlyMeter(Identity identity, UUID bookingId) {
        BranchScope branch = branch(identity);
        lockBooking(bookingId, branch.id());
        BookingRow booking = requireInHouseBooking(bookingId, branch.id());
        if (repository.meter(bookingId) != null)
            throw new BookingFailure(409, "form", "Booking đã có phiên tính giờ lẻ; vui lòng tải lại");
        AllocationRow allocation = repository.currentAllocationForUpdate(bookingId);
        validateActiveAllocation(allocation);
        Instant now = Instant.now();
        // Bật sớm vẫn giữ phòng, nhưng tiền giờ lẻ chỉ chạy từ giờ trả đã hẹn.
        Instant billedFrom = later(now, booking.plannedEnd());
        Instant reservedUntil = billedFrom.plus(Duration.ofHours(24));
        Instant nextBooking = repository.nextRoomBookingStart(allocation.roomId(), allocation.endsAt(), bookingId);
        if (nextBooking != null && nextBooking.isBefore(reservedUntil)) reservedUntil = nextBooking;
        if (!reservedUntil.isAfter(billedFrom))
            throw new BookingFailure(409, "form", "Phòng đã có booking kế tiếp; không còn thời gian trống để bật đồng hồ giờ lẻ");
        if (repository.hasRoomOverlap(allocation.roomId(), allocation.endsAt(), reservedUntil, bookingId))
            throw new BookingFailure(409, "form", "Phòng đã có booking khác trong khoảng tính giờ lẻ");
        PricingRule rule = pricing.meteredRate(allocation.roomTypeId(), branch.id(), branch.timezone(), billedFrom);
        if (repository.updateOccupiedAllocationEnd(allocation.id(), allocation.endsAt(), reservedUntil) != 1)
            throw new BookingFailure(409, "form", "Lịch phòng vừa thay đổi; vui lòng tải lại");
        repository.startMeter(bookingId, allocation.id(), rule.id(), now, billedFrom, allocation.endsAt(),
            reservedUntil, rule.hourlyPrice(), identity.id());
        repository.event(bookingId, identity.id(), "HOURLY_METER_STARTED", null, detailsMap(
            "startedAt", now, "billedFrom", billedFrom, "reservedUntil", reservedUntil,
            "hourlyPrice", rule.hourlyPrice(), "roomId", allocation.roomId()));
        return detail(branch.id(), repository.booking(bookingId, branch.id()));
    }

    @Transactional
    // Dừng đồng hồ, làm tròn phút thực dùng và chỉ ghi phụ thu một lần.
    public BookingDtos.BookingDetail stopHourlyMeter(Identity identity, UUID bookingId) {
        BranchScope branch = branch(identity);
        lockBooking(bookingId, branch.id());
        BookingRow booking = requireInHouseBooking(bookingId, branch.id());
        MeterRow meter = repository.meter(bookingId);
        if (meter == null || !Set.of("RUNNING", "STOPPED").contains(meter.status()))
            throw new BookingFailure(409, "form", "Booking không có phiên tính giờ lẻ để dừng");
        // Gửi lặp thao tác dừng chỉ trả trạng thái đã chốt, không tạo thêm dòng phí.
        if ("STOPPED".equals(meter.status())) return detail(branch.id(), booking);
        AllocationRow allocation = repository.currentAllocationForUpdate(bookingId);
        validateActiveAllocation(allocation);
        if (!allocation.id().equals(meter.allocationId()))
            throw new BookingFailure(409, "form", "Phòng hiện tại không khớp phiên tính giờ; vui lòng tải lại");
        Instant stoppedAt = Instant.now();
        long elapsedMillis = Math.max(0, Duration.between(meter.billedFrom(), stoppedAt).toMillis());
        // Làm tròn số phút thực dùng lên trước, rồi chia giá giờ theo tỷ lệ phút/60.
        int minutes = Math.toIntExact((elapsedMillis + 59_999) / 60_000);
        BigDecimal amount = meter.hourlyPrice().multiply(BigDecimal.valueOf(minutes))
            .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
        if (minutes > 0) {
            Map<String, Object> snapshot = detailsMap("useCase", "UC40_METER", "from", meter.billedFrom(),
                "to", stoppedAt, "billedMinutes", minutes, "hourlyPrice", meter.hourlyPrice(),
                "billing", "ACTUAL_MINUTES", "meterId", meter.id());
            repository.insertCharge(bookingId, allocation.id(), new BookingDtos.ChargeLine("OVERTIME",
                "Gia hạn giờ lẻ · " + minutes + " phút", BigDecimal.valueOf(minutes)
                    .divide(BigDecimal.valueOf(60), 3, RoundingMode.HALF_UP),
                meter.hourlyPrice(), amount, meter.priceRuleId(), null, snapshot));
        }
        repository.stopMeter(meter.id(), stoppedAt, minutes, amount, identity.id());
        repository.event(bookingId, identity.id(), "HOURLY_METER_STOPPED", null, detailsMap(
            "stoppedAt", stoppedAt, "billedMinutes", minutes, "amount", amount,
            "pastReservationLimit", stoppedAt.isAfter(meter.reservedUntil())));
        return detail(branch.id(), repository.booking(bookingId, branch.id()));
    }

    @Transactional
    // Tạo booking, chặng phòng, phí chốt và khoản thu cọc đã xác nhận trong một giao dịch.
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
        int guestCount = request.guestCount() == null ? 1 : request.guestCount();
        if (guestCount < 1 || guestCount > room.capacity())
            throw new BookingFailure(400, "guestCount", "Số khách phải từ 1 đến " + room.capacity() + " người theo sức chứa phòng");
        if (request.pricingMode() == null || "HOURLY".equalsIgnoreCase(request.pricingMode().trim()))
            validateHourlyRental(request.plannedStart(), request.plannedEnd());
        BookingDtos.PriceQuote quote = pricing.quote(room.roomTypeId(), branch.id(), branch.timezone(),
            request.plannedStart(), request.plannedEnd(), request.pricingMode(), request.comboId());
        String contactName = required(request.contactName(), "contactName", "Tên khách", 200);
        String contactPhone = contactPhone(request.contactPhone());
        String note = nullable(request.note(), "note", 2000);
        BigDecimal depositAmount = request.depositAmount() == null ? BigDecimal.ZERO : request.depositAmount();
        BigDecimal normalizedDepositAmount = depositAmount.stripTrailingZeros();
        if (depositAmount.signum() < 0 || normalizedDepositAmount.scale() > 0
                || normalizedDepositAmount.precision() - normalizedDepositAmount.scale() > 18)
            throw new BookingFailure(400, "depositAmount", "Số tiền cọc phải là số nguyên không âm, tối đa 18 chữ số");
        String paymentMethod = null;
        String paymentReference = null;
        if (depositAmount.signum() > 0) {
            paymentMethod = request.paymentMethod() == null ? "" : request.paymentMethod().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("BANK_TRANSFER", "CASH", "CARD", "QR").contains(paymentMethod))
                throw new BookingFailure(400, "paymentMethod", "Vui lòng chọn phương thức thu cọc hợp lệ");
            paymentReference = nullable(request.paymentReference(), "paymentReference", 120);
            if (!"CASH".equals(paymentMethod) && paymentReference == null)
                throw new BookingFailure(400, "paymentReference", "Nhập mã giao dịch / ghi chú khi thu cọc bằng phương thức này");
        }
        // Chuyển khoản do lễ tân xác nhận thủ công khác khoản được webhook ngân hàng đối soát.
        String recordedPaymentMethod = "BANK_TRANSFER".equals(paymentMethod) ? "MANUAL_TRANSFER" : paymentMethod;
        String code = "BK-" + java.time.LocalDate.now(branch.timezone()).toString().replace("-", "") + "-" +
            UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        UUID bookingId = repository.insertBooking(branch.id(), code, contactName, contactPhone, guestCount, channel,
            request.plannedStart(), request.plannedEnd(), REFUND_CUTOFF_MINUTES, actorId, note);
        UUID allocationId = insertAllocation(branch, bookingId, room.id(), request.plannedStart(), request.plannedEnd(), actorId);
        repository.createDepositAccount(bookingId);
        // Chỉ ghi sổ khi thực sự có tiền đã thu; yêu cầu cọc của combo không tự tạo khoản thu.
        if (depositAmount.signum() > 0) {
            repository.receiveDeposit(branch.id(), bookingId, actorId, depositAmount,
                recordedPaymentMethod, paymentReference);
            if (repository.depositBalance(bookingId).compareTo(depositAmount) != 0)
                throw new IllegalStateException("Received booking deposit does not match its ledger balance");
        }
        for (BookingDtos.ChargeLine charge : quote.charges()) repository.insertCharge(bookingId, allocationId, charge);
        repository.event(bookingId, actorId, "BOOKING_CREATED", null, detailsMap(
            "branchId", branch.id(), "roomId", room.id(), "startsAt", request.plannedStart(),
            "endsAt", request.plannedEnd(), "pricingMode", quote.pricingMode(),
            "expectedAmount", quote.expectedAmount(), "depositRequired", quote.depositRequired(), "guestCount", guestCount,
            "depositReceived", depositAmount, "depositMethod", recordedPaymentMethod));
        return detail(branch.id(), repository.booking(bookingId, branch.id()));
    }

    @Transactional
    // Ghi giờ nhận phòng thực tế; nhận sớm sẽ dịch giờ trả để giữ đủ thời lượng đã đặt.
    public BookingDtos.BookingDetail checkIn(Identity identity, UUID bookingId) {
        BranchScope branch = branch(identity);
        UUID actorId = identity.id();
        lockBooking(bookingId, branch.id());
        BookingRow booking = repository.booking(bookingId, branch.id());
        if (!"CONFIRMED".equals(booking.status()))
            throw new BookingFailure(409, "form", "Chỉ có thể check-in booking đang chờ nhận phòng");

        Instant now = Instant.now();
        if (!booking.plannedEnd().isAfter(now))
            throw new BookingFailure(409, "form", "Đã quá khung giờ booking; không thể check-in");
        AllocationRow allocation = repository.currentAllocationForUpdate(bookingId);
        if (allocation == null || !"RESERVED".equals(allocation.state()))
            throw new BookingFailure(409, "roomId", "Booking không còn phòng được giữ để check-in");
        if (!"AVAILABLE".equals(allocation.operatingStatus()))
            throw new BookingFailure(409, "roomId", "Phòng " + allocation.roomCode() + " chưa sẵn sàng nhận khách; hãy đổi phòng trước khi check-in");

        // Giữ plannedStart làm giờ hẹn gốc; chỉ dời allocation và plannedEnd khi khách đến sớm.
        boolean earlyCheckIn = now.isBefore(booking.plannedStart());
        Instant effectiveStart = earlyCheckIn ? now : booking.plannedStart();
        Instant effectiveEnd = earlyCheckIn
            ? now.plus(Duration.between(booking.plannedStart(), booking.plannedEnd()))
            : booking.plannedEnd();
        if (repository.hasRoomOverlap(allocation.roomId(), effectiveStart, effectiveEnd, bookingId))
            throw new BookingFailure(409, "roomId", "Phòng vẫn đang được sử dụng hoặc lịch giữ phòng đã thay đổi; vui lòng chọn phòng khác");

        int bookingUpdated = repository.checkInBooking(bookingId, now, effectiveEnd);
        int allocationUpdated = earlyCheckIn
            ? shiftAndOccupyAllocation(allocation.id(), now, effectiveStart, effectiveEnd)
            : repository.occupyAllocation(allocation.id(), now);
        if (bookingUpdated != 1 || allocationUpdated != 1)
            throw new BookingFailure(409, "form", "Booking vừa được xử lý ở một phiên khác. Vui lòng tải lại dữ liệu.");
        BigDecimal deposit = repository.depositBalance(bookingId);
        repository.event(bookingId, actorId, "BOOKING_CHECKED_IN", null, detailsMap(
            "actualCheckin", now, "roomId", allocation.roomId(), "depositBalance", deposit,
            "earlyCheckIn", earlyCheckIn, "plannedStartBeforeCheckIn", booking.plannedStart(),
            "plannedEndBeforeCheckIn", booking.plannedEnd(), "plannedStartAfterCheckIn", booking.plannedStart(),
            "plannedEndAfterCheckIn", effectiveEnd));
        return detail(branch.id(), repository.booking(bookingId, branch.id()));
    }

    // Báo giá sửa lịch từ giá combo/đơn giá đã chốt; chỉ chọn combo khác mới lấy giá cấu hình mới.
    private BookingDtos.PriceQuote editQuote(BranchScope branch, BookingRow booking, AllocationRow current,
                                             Instant start, Instant end, String requestedMode, UUID selectedComboId) {
        validateEditDuration(start, end);
        List<ChargeRow> charges = repository.activeChargesForAllocation(current.id());
        if (charges.isEmpty()) throw new BookingFailure(409, "form", "Không tìm thấy giá đã chốt của booking");
        ChargeRow packageCharge = charges.stream()
            .filter(charge -> charge.comboId() != null || "COMBO".equals(charge.kind())).findFirst().orElse(null);
        String mode = requestedMode == null || requestedMode.isBlank()
            ? packageCharge == null ? "HOURLY" : "COMBO" : requestedMode.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("HOURLY", "COMBO").contains(mode))
            throw new BookingFailure(400, "pricingMode", "Chọn tính giờ hoặc combo");
        if ("HOURLY".equals(mode)) {
            if (selectedComboId != null) throw new BookingFailure(400, "comboId", "Booking tính giờ không có combo để sửa");
            validateHourlyRental(start, end);
            if (packageCharge != null) {
                return pricing.quote(current.roomTypeId(), branch.id(), branch.timezone(), start, end, "HOURLY", null);
            }
            long originalMinutes = Duration.between(booking.plannedStart(), booking.plannedEnd()).toMinutes();
            long requestedMinutes = Duration.between(start, end).toMinutes();
            List<BookingDtos.ChargeLine> revised = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (ChargeRow charge : charges) {
                Map<String, Object> snapshot = new LinkedHashMap<>(readSnapshot(charge.snapshot()));
                int baseMinutes = snapshotInteger(snapshot, "confirmedDurationMinutes", (int) originalMinutes);
                BigDecimal baseAmount = snapshotDecimal(snapshot, "confirmedAmount", charge.amount());
                if (baseMinutes <= 0) throw new BookingFailure(409, "form", "Thời lượng giá gốc không hợp lệ");
                BigDecimal amount = proportional(baseAmount, requestedMinutes, baseMinutes);
                snapshot.put("confirmedDurationMinutes", baseMinutes);
                snapshot.put("confirmedAmount", baseAmount);
                snapshot.put("from", start.toString());
                snapshot.put("to", end.toString());
                revised.add(new BookingDtos.ChargeLine(charge.kind(), charge.description(),
                    charge.quantity(), charge.unitPrice(), amount, charge.priceRuleId(), null, snapshot));
                total = total.add(amount);
            }
            return new BookingDtos.PriceQuote("HOURLY", total, BigDecimal.ZERO, revised);
        }
        UUID comboId = selectedComboId == null && packageCharge != null ? packageCharge.comboId() : selectedComboId;
        if (comboId == null) throw new BookingFailure(409, "comboId", "Booking combo thiếu mã gói đã chốt");
        Map<String, Object> snapshot;
        BigDecimal packagePrice;
        BigDecimal overtimeRate;
        int packageMinutes;
        String name;
        if (packageCharge != null && comboId.equals(packageCharge.comboId())) {
            snapshot = new LinkedHashMap<>(readSnapshot(packageCharge.snapshot()));
            packageMinutes = snapshotInteger(snapshot, "durationMinutes", 0);
            packagePrice = packageCharge.unitPrice();
            overtimeRate = snapshotDecimal(snapshot, "overtimeHourlyPrice", null);
            name = packageCharge.description();
            if (packageMinutes <= 0 || packagePrice == null || overtimeRate == null)
                throw new BookingFailure(409, "comboId", "Giá combo đã chốt thiếu dữ liệu để tính lại");
        } else {
            ComboRow combo = repository.combo(comboId);
            if (combo == null || !current.roomTypeId().equals(combo.roomTypeId()))
                throw new BookingFailure(400, "comboId", "Combo không áp dụng cho hạng phòng của booking");
            packageMinutes = combo.durationMinutes();
            BookingDtos.PriceQuote base = pricing.quote(current.roomTypeId(), branch.id(), branch.timezone(),
                start, start.plusSeconds(packageMinutes * 60L), "COMBO", comboId);
            snapshot = new LinkedHashMap<>(base.charges().get(0).pricingSnapshot());
            packagePrice = base.expectedAmount();
            overtimeRate = combo.overtimeHourlyPrice();
            name = combo.name();
        }
        snapshot.put("from", start.toString());
        snapshot.put("to", end.toString());
        snapshot.put("durationMinutes", packageMinutes);
        snapshot.put("overtimeHourlyPrice", overtimeRate);
        List<BookingDtos.ChargeLine> revised = new ArrayList<>();
        revised.add(new BookingDtos.ChargeLine("COMBO", name, BigDecimal.ONE, packagePrice,
            packagePrice, null, comboId, snapshot));
        long extraMinutes = Math.max(0, Duration.between(start, end).toMinutes() - packageMinutes);
        BigDecimal total = packagePrice;
        if (extraMinutes > 0) {
            BigDecimal quantity = BigDecimal.valueOf(extraMinutes).divide(BigDecimal.valueOf(60), 3, RoundingMode.HALF_UP);
            BigDecimal amount = overtimeRate.multiply(BigDecimal.valueOf(extraMinutes))
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
            Map<String, Object> overtimeSnapshot = new LinkedHashMap<>();
            overtimeSnapshot.put("pricingMode", "COMBO_OVERTIME");
            overtimeSnapshot.put("from", start.plusSeconds(packageMinutes * 60L).toString());
            overtimeSnapshot.put("to", end.toString());
            overtimeSnapshot.put("overtimeHourlyPrice", overtimeRate);
            overtimeSnapshot.put("comboId", comboId.toString());
            revised.add(new BookingDtos.ChargeLine("OVERTIME", "Phụ phí vượt giờ · " + name,
                quantity, overtimeRate, amount, null, null, overtimeSnapshot));
            total = total.add(amount);
        }
        return new BookingDtos.PriceQuote("COMBO", total, BigDecimal.ZERO, revised);
    }

    @Transactional
    // Chỉ sửa booking chưa bắt đầu; đổi phòng hoặc giờ sẽ tạo chặng giữ phòng mới.
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
        BookingDtos.PriceQuote quote = editQuote(branch, booking, current, start, end, request.pricingMode(), request.comboId());
        List<ChargeRow> previousCharges = repository.activeChargesForAllocation(current.id());
        boolean chargesChanged = allocationChanged
            || ("COMBO".equals(quote.pricingMode()) != previousCharges.stream().anyMatch(charge -> charge.comboId() != null))
            || (request.comboId() != null
            && previousCharges.stream().noneMatch(charge -> request.comboId().equals(charge.comboId())));

        RoomRow targetRoom = null;
        // Giải phóng chặng cũ trong giao dịch để không tự tính nó là xung đột của lịch mới.
        if (allocationChanged) {
            targetRoom = repository.lockAvailableRoom(roomId, branch.id());
            if (targetRoom == null) throw new BookingFailure(409, "roomId", "Phòng không tồn tại tại chi nhánh hoặc hiện không hoạt động");
            if (booking.guestCount() > targetRoom.capacity())
                throw new BookingFailure(400, "roomId", "Phòng mới không đủ sức chứa số khách của booking");
            if ("COMBO".equals(quote.pricingMode()) && !targetRoom.roomTypeId().equals(current.roomTypeId()))
                throw new BookingFailure(400, "roomId", "Booking combo chỉ được đổi sang phòng cùng hạng");
            repository.releaseAllocation(current.id(), "Thay đổi lịch đặt");
            if (repository.hasRoomOverlap(roomId, start, end, bookingId)) throw roomConflict();
        }
        String contactName = request.contactName() == null ? booking.contactName() : required(request.contactName(), "contactName", "Tên khách", 200);
        String contactPhone = request.contactPhone() == null ? booking.contactPhone() : contactPhone(request.contactPhone());
        String note = request.note() == null ? booking.note() : nullable(request.note(), "note", 2000);
        repository.updateBooking(bookingId, contactName, contactPhone, start, end, note);
        UUID allocationId = allocationChanged ? insertAllocation(branch, bookingId, targetRoom.id(), start, end, actorId) : current.id();
        BigDecimal previousAmount = repository.activeChargeTotal(bookingId);
        if (chargesChanged) {
            previousCharges.forEach(charge -> repository.supersedeCharge(charge.id()));
            quote.charges().forEach(charge -> repository.insertCharge(bookingId, allocationId, charge));
        }
        repository.event(bookingId, actorId, "BOOKING_UPDATED", null, detailsMap(
            "previousRoomId", current.roomId(), "roomId", roomId,
            "previousStart", booking.plannedStart(), "startsAt", start,
            "previousEnd", booking.plannedEnd(), "endsAt", end,
            "previousAmount", previousAmount, "updatedAmount", repository.activeChargeTotal(bookingId),
            "comboId", request.comboId()));
        return detail(branch.id(), repository.booking(bookingId, branch.id()));
    }

    @Transactional
    // Hủy trước giờ nhận; tạo yêu cầu hoàn cọc hoặc ghi cọc bị giữ theo mốc cắt.
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
        if (request == null) throw new BookingFailure(400, "form", "Vui lòng nhập thông tin hủy booking");
        String reason = required(request.reason(), "reason", "Lý do hủy", 1000);
        UUID depositId = repository.lockDeposit(bookingId);
        BigDecimal deposit = repository.depositBalance(bookingId);
        long secondsUntilStart = Duration.between(now, booking.plannedStart()).getSeconds();
        boolean eligible = secondsUntilStart >= booking.refundCutoffMinutes() * 60L;
        String outcome = deposit.signum() == 0 ? "NO_DEPOSIT" : eligible ? "REFUND_ELIGIBLE" : "NON_REFUNDABLE";
        UUID refundRequestId = null;
        // Đủ điều kiện hoàn chỉ tạo yêu cầu chờ xử lý, chưa coi là tiền đã trả cho khách.
        if (deposit.signum() > 0 && eligible) {
            if (depositId == null) throw new BookingFailure(409, "form", "Không tìm thấy tài khoản tiền cọc để tạo yêu cầu hoàn");
            String beneficiaryName = required(request.beneficiaryName(), "beneficiaryName", "Chủ tài khoản", 200);
            String bankAccount = required(request.bankAccount(), "bankAccount", "Số tài khoản", 60);
            String bankName = required(request.bankName(), "bankName", "Ngân hàng", 200);
            refundRequestId = repository.createDepositRefundRequest(branch.id(), bookingId, depositId, deposit,
                reason, beneficiaryName, bankName, bankAccount, actorId);
        }
        // Trong mốc không hoàn, số dư cọc được đưa vào bút toán giữ cọc.
        if (deposit.signum() > 0 && !eligible)
            repository.forfeitDeposit(bookingId, actorId, deposit, "Hủy booking trong thời hạn không hoàn cọc", UUID.randomUUID().toString());
        repository.releaseAllocation(allocation.id(), "Booking đã hủy");
        repository.cancelBooking(bookingId, now, reason);
        repository.event(bookingId, actorId, "BOOKING_CANCELLED", reason, detailsMap(
            "cancelledAt", now, "depositOutcome", outcome, "depositAmount", deposit,
            "refundCutoffMinutes", booking.refundCutoffMinutes(), "releasedRoomId", allocation.roomId(),
            "refundRequestId", refundRequestId));
        BookingDtos.BookingDetail detail = detail(branch.id(), repository.booking(bookingId, branch.id()));
        return new BookingDtos.CancellationResult(detail, outcome, deposit, now, refundRequestId);
    }

    @Transactional
    // Xử lý khách vắng mặt sau giờ hẹn, giải phóng phòng và ghi cọc bị giữ.
    public BookingDtos.NoShowResult markNoShow(Identity identity, UUID bookingId, BookingDtos.NoShowRequest request) {
        BranchScope branch = branch(identity);
        UUID actorId = identity.id();
        lockBooking(bookingId, branch.id());
        BookingRow booking = repository.booking(bookingId, branch.id());
        if (booking == null) throw notFound();
        if (!"CONFIRMED".equals(booking.status()))
            throw new BookingFailure(409, "form", "Chỉ booking đã xác nhận và chưa check-in mới có thể đánh dấu no-show");
        Instant now = Instant.now();
        if (booking.plannedStart().isAfter(now))
            throw new BookingFailure(409, "form", "Chỉ có thể đánh dấu no-show khi đã đến giờ bắt đầu booking");
        AllocationRow allocation = repository.currentAllocationForUpdate(bookingId);
        if (allocation == null || !"RESERVED".equals(allocation.state()))
            throw new BookingFailure(409, "form", "Booking không còn phòng đang được giữ để giải phóng");

        String reason = request == null || request.reason() == null || request.reason().isBlank()
            ? "Khách không đến nhận phòng"
            : required(request.reason(), "reason", "Lý do no-show", 1000);
        UUID depositId = repository.lockDeposit(bookingId);
        BigDecimal deposit = repository.depositBalance(bookingId);
        if (deposit.signum() > 0 && depositId == null)
            throw new BookingFailure(409, "form", "Không tìm thấy tài khoản tiền cọc để ghi nhận no-show");
        if (deposit.signum() > 0)
            repository.forfeitDeposit(bookingId, actorId, deposit, "Tịch thu cọc do khách không đến (no-show)", UUID.randomUUID().toString());

        repository.releaseAllocation(allocation.id(), "Booking no-show");
        if (repository.markNoShow(bookingId) != 1)
            throw new BookingFailure(409, "form", "Booking đã được cập nhật bởi thao tác khác");
        String depositOutcome = deposit.signum() > 0 ? "NON_REFUNDABLE" : "NO_DEPOSIT";
        repository.event(bookingId, actorId, "BOOKING_NO_SHOW", reason, detailsMap(
            "noShowAt", now, "depositOutcome", depositOutcome, "depositAmount", deposit,
            "releasedRoomId", allocation.roomId()));
        BookingDtos.BookingDetail detail = detail(branch.id(), repository.booking(bookingId, branch.id()));
        return new BookingDtos.NoShowResult(detail, depositOutcome, deposit, now, allocation.roomId());
    }

    // Bắt buộc booking thuộc chi nhánh và đang ở trạng thái sử dụng phòng.
    private BookingRow requireInHouseBooking(UUID bookingId, UUID branchId) {
        BookingRow booking = repository.booking(bookingId, branchId);
        if (booking == null) throw notFound();
        if (!"IN_HOUSE".equals(booking.status()))
            throw new BookingFailure(409, "form", "Chỉ booking đang sử dụng phòng mới có thể đổi phòng hoặc gia hạn");
        return booking;
    }

    // Chặn đổi phòng hoặc mua thêm thời gian khi đồng hồ giờ lẻ đã bắt đầu.
    private void rejectRunningMeter(UUID bookingId) {
        MeterRow meter = repository.meter(bookingId);
        if (meter != null)
            throw new BookingFailure(409, "form", "Đã có phiên tính giờ lẻ; không thể đổi phòng hoặc mua combo sau khi bắt đầu tính giờ");
    }

    // Bắt buộc tồn tại chặng phòng đang được sử dụng.
    private static void validateActiveAllocation(AllocationRow allocation) {
        if (allocation == null || !"OCCUPIED".equals(allocation.state()))
            throw new BookingFailure(409, "roomId", "Không tìm thấy chặng phòng đang được sử dụng");
    }

    // Kiểm tra phòng đích còn hoạt động và đủ sức chứa khách.
    private static void validateTargetRoom(RoomRow room, BookingRow booking) {
        if (room == null) throw new BookingFailure(409, "targetRoomId", "Phòng không tồn tại tại chi nhánh hoặc hiện không hoạt động");
        if (booking.guestCount() > room.capacity())
            throw new BookingFailure(400, "targetRoomId", "Phòng mới không đủ sức chứa số khách của booking");
    }

    // Chỉ nhận nguyên nhân đổi phòng được hỗ trợ để áp dụng đúng chính sách giá.
    private static String normalizeChangeCause(String cause) {
        if (cause == null || cause.isBlank()) throw new BookingFailure(400, "cause", "Vui lòng chọn lý do đổi phòng");
        String value = cause.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("CUSTOMER", "BRANCH_FAULT").contains(value))
            throw new BookingFailure(400, "cause", "Lý do đổi phòng không hợp lệ");
        return value;
    }

    // Tính mốc kết thúc và báo giá cho combo hoặc 1–12 giờ gia hạn.
    private BookingDtos.ExtensionQuote extensionQuote(BranchScope branch, BookingRow booking, AllocationRow current,
                                                       BookingDtos.ExtendStayRequest request) {
        validateActiveAllocation(current);
        Instant startsAt = booking.plannedEnd();
        String mode = request.pricingMode() == null ? "" : request.pricingMode().trim().toUpperCase(Locale.ROOT);
        Instant endsAt;
        if ("COMBO".equals(mode)) {
            if (request.comboId() == null) throw new BookingFailure(400, "comboId", "Vui lòng chọn combo gia hạn");
            ComboRow combo = repository.combo(request.comboId());
            if (combo == null || !current.roomTypeId().equals(combo.roomTypeId()))
                throw new BookingFailure(400, "comboId", "Combo không áp dụng cho loại phòng hiện tại");
            if (!extensionComboAvailableAt(combo, startsAt, branch.timezone()))
                throw new BookingFailure(400, "comboId", "Combo ngày chỉ bắt đầu lúc 07:00 và combo đêm chỉ bắt đầu lúc 23:00");
            endsAt = startsAt.plusSeconds(combo.durationMinutes() * 60L);
        } else if ("HOURLY".equals(mode)) {
            if (request.comboId() != null) throw new BookingFailure(400, "comboId", "Không chọn combo khi gia hạn theo giờ");
            Integer hours = request.hours();
            if (hours == null || hours < 1 || hours > 12)
                throw new BookingFailure(400, "hours", "Số giờ gia hạn phải từ 1 đến 12 giờ");
            endsAt = startsAt.plus(Duration.ofHours(hours));
        } else {
            throw new BookingFailure(400, "pricingMode", "Chọn gia hạn theo combo hoặc theo giờ");
        }
        if (!endsAt.isAfter(Instant.now()))
            throw new BookingFailure(400, "hours", "Thời lượng gia hạn phải đủ để bao phủ đến thời điểm hiện tại");
        BookingDtos.PriceQuote quote = pricing.quote(current.roomTypeId(), branch.id(), branch.timezone(), startsAt, endsAt,
            mode, request.comboId());
        return new BookingDtos.ExtensionQuote(startsAt, endsAt, quote);
    }

    // Giới hạn giờ bắt đầu của combo ngày và combo đêm theo múi giờ chi nhánh.
    private static boolean extensionComboAvailableAt(ComboRow combo, Instant start, java.time.ZoneId timezone) {
        String name = combo.name() == null ? "" : combo.name().toLowerCase(Locale.ROOT);
        LocalTime localStart = start.atZone(timezone).toLocalTime();
        if (name.contains("ngày") || name.contains("ngay") || name.contains("day"))
            return localStart.equals(LocalTime.of(7, 0));
        if (name.contains("đêm") || name.contains("dem") || name.contains("night"))
            return localStart.equals(LocalTime.of(23, 0));
        return true;
    }

    // Tách phí theo từng chặng; sự cố cơ sở giữ giá cũ, yêu cầu khách dùng giá phòng mới.
    private RoomChangePlan roomChangePlan(BranchScope branch, BookingRow booking, AllocationRow current,
                                           RoomRow target, Instant changedAt, String cause) {
        List<ChargeRow> charges = repository.activeChargesForAllocation(current.id());
        if (charges.isEmpty()) throw new BookingFailure(409, "form", "Không tìm thấy khoản phí đang gắn với chặng phòng hiện tại");
        List<BookingDtos.ChargeLine> usedLines = new ArrayList<>();
        List<BookingDtos.ChargeLine> nextLines = new ArrayList<>();
        BigDecimal originalAmount = BigDecimal.ZERO;
        BigDecimal usedAmount = BigDecimal.ZERO;
        BigDecimal nextAmount = BigDecimal.ZERO;
        List<UUID> chargeIds = new ArrayList<>();
        // Snapshot phí gốc có thể ghi trước lần check-in sớm; dịch nó theo chặng phòng thực tế.
        Instant savedBaseStart = charges.stream()
            .filter(charge -> !readSnapshot(charge.snapshot()).containsKey("extensionStartsAt"))
            .map(charge -> readSnapshot(charge.snapshot()))
            .map(snapshot -> snapshotInstant(snapshot, null, "from", "startsAt"))
            .filter(value -> value != null).min(Instant::compareTo).orElse(null);
        Duration timelineShift = savedBaseStart == null ? Duration.ZERO : Duration.between(savedBaseStart, current.startsAt());

        for (ChargeRow charge : charges) {
            originalAmount = originalAmount.add(charge.amount());
            chargeIds.add(charge.id());
            Map<String, Object> sourceSnapshot = readSnapshot(charge.snapshot());
            Instant lineStart = snapshotInstant(sourceSnapshot, current.startsAt(), "extensionStartsAt", "from", "startsAt");
            Instant lineEnd = snapshotInstant(sourceSnapshot, current.endsAt(), "extensionEndsAt", "to", "endsAt");
            boolean hasSavedWindow = sourceSnapshot.containsKey("extensionStartsAt") || sourceSnapshot.containsKey("from")
                || sourceSnapshot.containsKey("startsAt") || sourceSnapshot.containsKey("extensionEndsAt")
                || sourceSnapshot.containsKey("to") || sourceSnapshot.containsKey("endsAt");
            if (hasSavedWindow && !sourceSnapshot.containsKey("extensionStartsAt")) {
                lineStart = lineStart.plus(timelineShift);
                lineEnd = lineEnd.plus(timelineShift);
            }
            boolean packageCharge = charge.comboId() != null || "COMBO".equals(sourceSnapshot.get("pricingMode"));
            if (packageCharge && !hasSavedWindow) {
                int savedDuration = snapshotInteger(sourceSnapshot, "durationMinutes", 0);
                if (savedDuration > 0) lineEnd = lineStart.plusSeconds(savedDuration * 60L);
            }
            Instant usedEnd = earlier(lineEnd, changedAt);
            if (usedEnd.isAfter(lineStart)) {
                long fullSeconds = Math.max(1, Duration.between(lineStart, lineEnd).getSeconds());
                long usedSeconds = Duration.between(lineStart, usedEnd).getSeconds();
                BigDecimal amount = proportional(charge.amount(), usedSeconds, fullSeconds);
                Map<String, Object> snapshot = splitSnapshot(sourceSnapshot, lineStart, usedEnd, cause, "USED", charge.id());
                usedLines.add(new BookingDtos.ChargeLine(charge.kind(), charge.description(),
                    splitQuantity(charge.quantity(), usedSeconds, fullSeconds), charge.unitPrice(), amount,
                    charge.priceRuleId(), charge.comboId(), snapshot));
                usedAmount = usedAmount.add(amount);
            }

            Instant nextStart = later(lineStart, changedAt);
            if (!lineEnd.isAfter(nextStart)) continue;
            long fullSeconds = Math.max(1, Duration.between(lineStart, lineEnd).getSeconds());
            long nextSeconds = Duration.between(nextStart, lineEnd).getSeconds();
            // Lấy phần dư từ tiền gốc để sai số làm tròn không làm thay đổi tổng phí đã chốt.
            BigDecimal sourceRemaining = charge.amount().subtract(proportional(charge.amount(), fullSeconds - nextSeconds, fullSeconds));
            // Lỗi cơ sở: giữ giá phần chưa dùng, dù phòng thay thế thuộc hạng khác.
            if ("BRANCH_FAULT".equals(cause)) {
                Map<String, Object> snapshot = splitSnapshot(sourceSnapshot, nextStart, lineEnd, cause, "NEXT", charge.id());
                nextLines.add(new BookingDtos.ChargeLine(charge.kind(), charge.description(),
                    splitQuantity(charge.quantity(), nextSeconds, fullSeconds), charge.unitPrice(), sourceRemaining,
                    charge.priceRuleId(), charge.comboId(), snapshot));
                nextAmount = nextAmount.add(sourceRemaining);
                continue;
            }

            // Khách yêu cầu: dùng combo tương đương ở phòng mới, nếu thiếu thì báo giá theo giờ.
            ComboRow sourceCombo = charge.comboId() == null ? null : repository.combo(charge.comboId());
            String sourceComboName = sourceCombo == null ? snapshotString(sourceSnapshot, "name") : sourceCombo.name();
            int sourceDuration = sourceCombo == null
                ? snapshotInteger(sourceSnapshot, "durationMinutes", 0) : sourceCombo.durationMinutes();
            ComboRow equivalent = packageCharge
                ? equivalentCombo(sourceComboName, sourceDuration, target.roomTypeId()) : null;
            if (equivalent != null) {
                int packageMinutes = snapshotInteger(sourceSnapshot, "packageDurationMinutes",
                    sourceDuration);
                BigDecimal amount = proportional(equivalent.price(), nextSeconds, Math.max(1, packageMinutes * 60L));
                Map<String, Object> snapshot = splitSnapshot(sourceSnapshot, nextStart, lineEnd, cause, "NEXT", charge.id());
                snapshot.put("pricingMode", "COMBO");
                snapshot.put("name", equivalent.name());
                snapshot.put("code", equivalent.code());
                snapshot.put("version", equivalent.version());
                snapshot.put("durationMinutes", equivalent.durationMinutes());
                snapshot.put("packageDurationMinutes", packageMinutes);
                if (charge.comboId() != null) snapshot.put("sourceComboId", charge.comboId().toString());
                nextLines.add(new BookingDtos.ChargeLine("COMBO", equivalent.name(),
                    BigDecimal.valueOf(nextSeconds).divide(BigDecimal.valueOf(3600), 3, RoundingMode.HALF_UP),
                    equivalent.price(), amount, null, equivalent.id(), snapshot));
                nextAmount = nextAmount.add(amount);
            } else {
                BookingDtos.PriceQuote hourly = pricing.quote(target.roomTypeId(), branch.id(), branch.timezone(),
                    nextStart, lineEnd, "HOURLY", null);
                for (BookingDtos.ChargeLine line : hourly.charges()) {
                    Map<String, Object> snapshot = new LinkedHashMap<>(line.pricingSnapshot());
                    snapshot.put("changeCause", cause);
                    snapshot.put("sourceChargeId", charge.id().toString());
                    snapshot.put("chargeSegment", "NEXT");
                    nextLines.add(new BookingDtos.ChargeLine(line.kind(), line.description(), line.quantity(),
                        line.unitPrice(), line.amount(), line.priceRuleId(), line.comboId(), snapshot));
                    nextAmount = nextAmount.add(line.amount());
                }
            }
        }
        return new RoomChangePlan(List.copyOf(chargeIds), List.copyOf(usedLines), List.copyOf(nextLines),
            originalAmount, usedAmount, nextAmount);
    }

    // Tìm combo tương ứng ở hạng phòng mới với cùng tên gói và thời lượng.
    private ComboRow equivalentCombo(String sourceComboName, int sourceDurationMinutes, UUID targetRoomTypeId) {
        if (sourceComboName == null || sourceComboName.isBlank() || sourceDurationMinutes <= 0) return null;
        String sourceName = normalizeComboName(sourceComboName);
        return repository.combos(targetRoomTypeId).stream()
            .filter(combo -> combo.durationMinutes() == sourceDurationMinutes)
            .filter(combo -> normalizeComboName(combo.name()).equals(sourceName))
            .findFirst().orElse(null);
    }

    // Bỏ tên hạng phòng và dấu phân cách để đối chiếu combo giữa các hạng.
    private static String normalizeComboName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("queen|king|room|phòng", "")
            .replaceAll("[^a-z0-9]", "");
    }

    // Đọc bản chụp giá cũ; dữ liệu không đọc được được xem như không có trường bổ sung.
    private Map<String, Object> readSnapshot(String source) {
        try { return source == null ? new LinkedHashMap<>() : json.readValue(source, new TypeReference<>() {}); }
        catch (Exception ignored) { return new LinkedHashMap<>(); }
    }

    // Lấy mốc thời gian từ các khóa snapshot cũ và mới theo thứ tự ưu tiên.
    private static Instant snapshotInstant(Map<String, Object> snapshot, Instant fallback, String... keys) {
        for (String key : keys) {
            Object value = snapshot.get(key);
            if (value instanceof String text) {
                try { return Instant.parse(text); } catch (Exception ignored) { /* thử khóa tương thích kế tiếp */ }
            }
        }
        return fallback;
    }

    // Đọc số nguyên từ snapshot, dùng giá trị dự phòng khi thiếu hoặc sai định dạng.
    private static int snapshotInteger(Map<String, Object> snapshot, String key, int fallback) {
        Object value = snapshot.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try { return Integer.parseInt(text); } catch (NumberFormatException ignored) { /* dùng giá trị dự phòng */ }
        }
        return fallback;
    }

    private static BigDecimal snapshotDecimal(Map<String, Object> snapshot, String key, BigDecimal fallback) {
        Object value = snapshot.get(key);
        if (value == null) return fallback;
        try { return new BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    // Đọc một trường snapshot dưới dạng chuỗi nếu có.
    private static String snapshotString(Map<String, Object> snapshot, String key) {
        Object value = snapshot.get(key);
        return value == null ? null : String.valueOf(value);
    }

    // Phân bổ tiền theo số giây và làm tròn đến đơn vị đồng.
    private static BigDecimal proportional(BigDecimal amount, long partSeconds, long fullSeconds) {
        if (partSeconds <= 0) return BigDecimal.ZERO;
        return amount.multiply(BigDecimal.valueOf(partSeconds))
            .divide(BigDecimal.valueOf(Math.max(1, fullSeconds)), 0, RoundingMode.HALF_UP);
    }

    // Phân bổ số lượng theo thời gian, giữ mức tối thiểu để dòng phí hợp lệ.
    private static BigDecimal splitQuantity(BigDecimal quantity, long partSeconds, long fullSeconds) {
        BigDecimal result = quantity.multiply(BigDecimal.valueOf(partSeconds))
            .divide(BigDecimal.valueOf(Math.max(1, fullSeconds)), 3, RoundingMode.HALF_UP);
        return result.signum() > 0 ? result : new BigDecimal("0.001");
    }

    // Lưu nguồn gốc và khoảng thời gian của dòng phí sau khi tách.
    private static Map<String, Object> splitSnapshot(Map<String, Object> original, Instant start, Instant end,
                                                       String cause, String segment, UUID sourceChargeId) {
        Map<String, Object> snapshot = new LinkedHashMap<>(original);
        snapshot.put("from", start.toString());
        snapshot.put("to", end.toString());
        snapshot.put("changeCause", cause);
        snapshot.put("chargeSegment", segment);
        snapshot.put("sourceChargeId", sourceChargeId.toString());
        if ("COMBO".equals(snapshot.get("pricingMode"))) {
            snapshot.putIfAbsent("packageDurationMinutes", snapshotInteger(snapshot, "durationMinutes", 0));
        }
        return snapshot;
    }

    // Chọn mốc sớm hơn khi cắt khoảng tính phí.
    private static Instant earlier(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
    // Chọn mốc muộn hơn khi cắt khoảng tính phí.
    private static Instant later(Instant left, Instant right) { return left.isAfter(right) ? left : right; }

    private record RoomChangePlan(List<UUID> chargeIds, List<BookingDtos.ChargeLine> usedCharges,
                                  List<BookingDtos.ChargeLine> nextCharges, BigDecimal originalAmount,
                                  BigDecimal usedAmount, BigDecimal nextAmount) {}

    // Ghép booking, lịch phòng, phí, cọc và đồng hồ thành phản hồi chi tiết.
    private BookingDtos.BookingDetail detail(UUID branchId, BookingRow booking) {
        if (booking == null) throw notFound();
        List<AllocationRow> allocations = repository.allocations(booking.id());
        AllocationRow latest = allocations.isEmpty() ? null : allocations.get(allocations.size() - 1);
        BigDecimal depositBalance = repository.depositBalance(booking.id());
        BigDecimal depositReceivedAmount = repository.depositReceivedAmount(booking.id());
        BookingDtos.BookingSummary summary = new BookingDtos.BookingSummary(booking.id(), booking.code(), booking.contactName(),
            booking.contactPhone(), booking.channel(), booking.plannedStart(), booking.plannedEnd(), booking.actualCheckin(), booking.status(),
            latest == null ? null : latest.roomId(), latest == null ? null : latest.roomCode(), latest == null ? null : latest.roomName(),
            latest == null ? null : latest.roomTypeName(),
            repository.activeChargeTotal(booking.id()), depositBalance, depositReceivedAmount, booking.guestCount(),
            latest == null ? null : latest.operatingStatus());
        List<BookingDtos.AllocationView> allocationViews = allocations.stream().map(a -> new BookingDtos.AllocationView(
            a.id(), a.roomId(), a.roomCode(), a.roomName(), a.startsAt(), a.endsAt(), a.state(), a.changeReason(), a.createdAt())).toList();
        List<BookingDtos.ChargeView> chargeViews = repository.charges(booking.id()).stream().map(this::chargeView).toList();
        MeterRow meter = repository.meter(booking.id());
        return new BookingDtos.BookingDetail(summary, branchId, booking.note(), booking.createdAt(), allocationViews,
            chargeViews, new BookingDtos.DepositView(depositBalance, depositReceivedAmount,
                repository.depositForfeitedAmount(booking.id()), repository.depositCancellationOutcome(booking.id()), booking.refundCutoffMinutes(),
                repository.depositPaymentMethod(booking.id())),
            meter == null ? null : new BookingDtos.MeterView(meter.status(), meter.startedAt(), meter.billedFrom(),
                meter.reservedUntil(), meter.stoppedAt(), meter.billedMinutes(), meter.hourlyPrice(), meter.amount()));
    }

    // Chuyển dòng phí sang DTO cùng snapshot dùng để giải thích cách tính.
    private BookingDtos.ChargeView chargeView(ChargeRow charge) {
        Map<String, Object> snapshot = Map.of();
        try {
            if (charge.snapshot() != null) snapshot = json.readValue(charge.snapshot(), new TypeReference<>() {});
        } catch (Exception ignored) {
            // Bản JSON gốc vẫn ở database; cột JSONB thông thường đã bảo đảm cú pháp hợp lệ.
        }
        return new BookingDtos.ChargeView(charge.id(), charge.kind(), charge.description(), charge.quantity(),
            charge.unitPrice(), charge.amount(), charge.status(), charge.priceRuleId(), charge.comboId(), snapshot, charge.createdAt());
    }

    // Lấy phạm vi chi nhánh từ tài khoản, từ chối tài khoản chưa được phân công.
    private BranchScope branch(Identity identity) {
        BranchScope scope = repository.branchScope(identity.id());
        if (scope == null) throw new BookingFailure(403, "form", "Tài khoản chưa được phân công tại chi nhánh đang hoạt động");
        return scope;
    }

    // Khóa booking theo chi nhánh trước các thay đổi cần chống thao tác đồng thời.
    private void lockBooking(UUID bookingId, UUID branchId) {
        if (repository.lockBookingId(bookingId, branchId) == null) throw notFound();
    }

    // Đổi lỗi ràng buộc trùng lịch từ database thành lỗi phòng bận dễ hiểu.
    private UUID insertAllocation(BranchScope branch, UUID bookingId, UUID roomId, Instant start, Instant end, UUID actorId) {
        try { return repository.insertAllocation(branch.id(), bookingId, roomId, start, end, actorId); }
        catch (DataIntegrityViolationException e) {
            String detail = e.getMostSpecificCause() == null ? "" : e.getMostSpecificCause().getMessage();
            if (detail != null && (detail.contains("no_room_overlap") || detail.contains("no_booking_parallel_rooms"))) throw roomConflict();
            throw e;
        }
    }

    // Dịch và kích hoạt chặng phòng, chuẩn hóa lỗi trùng lịch.
    private int shiftAndOccupyAllocation(UUID allocationId, Instant now, Instant startsAt, Instant endsAt) {
        try { return repository.shiftAndOccupyAllocation(allocationId, now, startsAt, endsAt); }
        catch (DataIntegrityViolationException e) {
            String detail = e.getMostSpecificCause() == null ? "" : e.getMostSpecificCause().getMessage();
            if (detail != null && (detail.contains("no_room_overlap") || detail.contains("no_booking_parallel_rooms")))
                throw roomConflict();
            throw e;
        }
    }

    // Chuyển combo trong database sang lựa chọn trả cho giao diện.
    private static BookingDtos.ComboOption comboOption(ComboRow combo) {
        return new BookingDtos.ComboOption(combo.id(), combo.code(), combo.version(), combo.name(), combo.durationMinutes(),
            combo.price(), combo.depositRequired(), combo.validFrom(), combo.validTo());
    }

    // Chuẩn hóa và kiểm tra kênh tiếp nhận booking.
    private static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) throw new BookingFailure(400, "channel", "Vui lòng chọn kênh nhận đặt phòng");
        String value = channel.trim().toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(value)) throw new BookingFailure(400, "channel", "Kênh nhận đặt phòng không hợp lệ");
        return value;
    }

    // Chuẩn hóa bộ lọc trạng thái và hỗ trợ tên trạng thái từ client cũ.
    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return null;
        String value = status.trim().toUpperCase(Locale.ROOT);
        if ("PENDING".equals(value)) value = "CONFIRMED";
        if ("CHECKED_IN".equals(value)) value = "IN_HOUSE";
        if (!STATUSES.contains(value)) throw new BookingFailure(400, "status", "Trạng thái booking không hợp lệ");
        return value;
    }

    // Kiểm tra trường bắt buộc, cắt khoảng trắng và giới hạn độ dài.
    private static String required(String value, String field, String label, int max) {
        if (value == null || value.trim().isEmpty()) throw new BookingFailure(400, field, "Vui lòng nhập " + label.toLowerCase(Locale.ROOT));
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new BookingFailure(400, field, label + " không được vượt quá " + max + " ký tự");
        return trimmed;
    }

    private static String contactPhone(String value) {
        String phone = required(value, "contactPhone", "Số điện thoại", 15);
        if (!phone.matches("0[0-9]{6,14}"))
            throw new BookingFailure(400, "contactPhone", "Số điện thoại phải bắt đầu bằng 0 và gồm 7–15 chữ số, không chứa dấu hoặc khoảng trắng.");
        return phone;
    }

    // Dùng chính sách mặc định: chuỗi rỗng được xem như không có giá trị.
    private static String nullable(String value, String field, int max) { return nullable(value, field, max, false); }
    // Chuẩn hóa trường tùy chọn, cho phép giữ chuỗi rỗng khi người dùng muốn xóa ghi chú.
    private static String nullable(String value, String field, int max, boolean allowEmpty) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return allowEmpty ? "" : null;
        if (trimmed.length() > max) throw new BookingFailure(400, field, "Trường dữ liệu không được vượt quá " + max + " ký tự");
        return trimmed;
    }

    // Bắt buộc giờ kết thúc lớn hơn giờ bắt đầu.
    private static void validatePeriod(Instant start, Instant end) {
        if (start == null) throw new BookingFailure(400, "plannedStart", "Vui lòng nhập thời gian bắt đầu");
        if (end == null || !end.isAfter(start)) throw new BookingFailure(400, "plannedEnd", "Thời gian kết thúc phải sau thời gian bắt đầu");
    }

    private static void validateHourlyRental(Instant start, Instant end) {
        long seconds = Duration.between(start, end).getSeconds();
        if (seconds < 3600 || seconds > 24 * 3600 || seconds % 3600 != 0)
            throw new BookingFailure(400, "plannedEnd", "Thời lượng tính giờ phải là số giờ nguyên từ 1 đến 24");
    }

    private static void validateEditDuration(Instant start, Instant end) {
        long seconds = Duration.between(start, end).getSeconds();
        if (seconds < 60 || seconds > 24 * 3600 || seconds % 60 != 0)
            throw new BookingFailure(400, "plannedEnd", "Thời lượng sửa booking phải từ 1 phút đến 24 giờ và chính xác đến phút");
    }

    // Tạo lỗi xung đột lịch phòng dùng chung cho các thao tác.
    private static BookingFailure roomConflict() {
        return new BookingFailure(409, "roomId", "Phòng đã có booking khác trong khoảng thời gian này");
    }

    // Ẩn booking ngoài phạm vi chi nhánh dưới phản hồi không tìm thấy.
    private static BookingFailure notFound() { return new BookingFailure(404, "form", "Không tìm thấy booking trong phạm vi chi nhánh của bạn"); }

    // Tạo dữ liệu chi tiết cho sự kiện lịch sử theo cặp khóa và giá trị.
    private static Map<String, Object> detailsMap(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }
}
