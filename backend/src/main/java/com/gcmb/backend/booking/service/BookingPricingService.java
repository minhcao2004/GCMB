package com.gcmb.backend.booking.service;

import com.gcmb.backend.booking.dto.BookingDtos;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.BookingRepository.ComboRow;
import com.gcmb.backend.booking.repository.BookingRepository.PricingRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BookingPricingService {
    private final BookingRepository repository;

    // Rule giá và combo luôn đọc từ cấu hình database thay vì giá do client gửi.
    public BookingPricingService(BookingRepository repository) { this.repository = repository; }

    // BR-13: báo giá dùng cấu hình hiện hành và lưu snapshot để booking giữ nguyên giá đã chốt.
    public BookingDtos.PriceQuote quote(UUID roomTypeId, UUID branchId, ZoneId timezone,
                                        Instant start, Instant end, String mode, UUID comboId) {
        if (start == null || end == null || !end.isAfter(start))
            throw new BookingFailure(400, "plannedEnd", "Thời gian kết thúc phải sau thời gian bắt đầu.");
        String normalizedMode = mode == null || mode.isBlank() ? "HOURLY" : mode.trim().toUpperCase();
        if ("COMBO".equals(normalizedMode)) {
            var result = comboQuote(roomTypeId, start, end, comboId);
            return new BookingDtos.PriceQuote(result.pricingMode(), result.expectedAmount(), depositFor(result.expectedAmount()), result.charges());
        }
        if (!"HOURLY".equals(normalizedMode)) throw new BookingFailure(400, "pricingMode", "Hình thức tính giá không hợp lệ");
        if (comboId != null) throw new BookingFailure(400, "comboId", "Không chọn combo khi tính giá theo giờ");
        var result = hourlyQuote(roomTypeId, branchId, timezone, start, end);
        return new BookingDtos.PriceQuote(result.pricingMode(), result.expectedAmount(), depositFor(result.expectedAmount()), result.charges());
    }

    public static BigDecimal depositFor(BigDecimal amount) {
        return amount.multiply(new BigDecimal("0.30")).setScale(0, RoundingMode.HALF_UP);
    }

    // Chọn đơn giá giờ lẻ tại thời điểm bắt đầu đồng hồ theo chi nhánh và hạng phòng.
    public PricingRule meteredRate(UUID roomTypeId, UUID branchId, ZoneId timezone, Instant at) {
        ZonedDateTime local = at.atZone(timezone);
        LocalDate date = local.toLocalDate();
        int minute = local.getHour() * 60 + local.getMinute();
        return repository.pricingRules(roomTypeId, branchId, date, date).stream()
            .filter(rule -> applies(rule, date, minute))
            .max(Comparator.comparing((PricingRule rule) -> rule.branchId() != null)
                .thenComparingInt(PricingRule::priority)
                .thenComparingInt(rule -> specificity(rule.dayKind())))
            .orElseThrow(() -> new BookingFailure(409, "pricingMode", "Chưa cấu hình giá giờ lẻ cho phòng và thời điểm bắt đầu"));
    }

    // BR-13: lưu giá và điều khoản combo hiện hành vào snapshot của booking.
    private BookingDtos.PriceQuote comboQuote(UUID roomTypeId, Instant start, Instant end, UUID comboId) {
        if (comboId == null) throw new BookingFailure(400, "comboId", "Vui lòng chọn combo");
        ComboRow combo = repository.combo(comboId);
        if (combo == null || !roomTypeId.equals(combo.roomTypeId()))
            throw new BookingFailure(400, "comboId", "Combo không áp dụng cho loại phòng đã chọn");
        if (combo.validFrom().isAfter(start) || (combo.validTo() != null && !start.isBefore(combo.validTo())))
            throw new BookingFailure(409, "comboId", "Combo không có hiệu lực tại thời gian đặt phòng");
        long expectedSeconds = combo.durationMinutes() * 60L;
        if (!Duration.between(start, end).equals(Duration.ofSeconds(expectedSeconds)))
            throw new BookingFailure(400, "plannedEnd", "Thời lượng đặt phòng phải khớp thời lượng combo đã chọn");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("pricingMode", "COMBO"); snapshot.put("code", combo.code());
        snapshot.put("version", combo.version()); snapshot.put("name", combo.name());
        snapshot.put("durationMinutes", combo.durationMinutes());
        snapshot.put("from", start.toString()); snapshot.put("to", end.toString());
        snapshot.put("depositRequired", depositFor(combo.price()));
        snapshot.put("overtimeHourlyPrice", combo.overtimeHourlyPrice());
        BookingDtos.ChargeLine line = new BookingDtos.ChargeLine("COMBO", combo.name(), BigDecimal.ONE,
            combo.price(), combo.price(), null, combo.id(), snapshot);
        return new BookingDtos.PriceQuote("COMBO", combo.price(), combo.depositRequired(), List.of(line));
    }

    // BR-13: chia khoảng theo price rule và lưu snapshot của từng phần giá đã chốt.
    private BookingDtos.PriceQuote hourlyQuote(UUID roomTypeId, UUID branchId, ZoneId timezone, Instant start, Instant end) {
        return hourlyQuote(roomTypeId, branchId, timezone, start, end, false);
    }

    // BR-169/170: miễn phí <=5 phút trên TOÀN khoảng quá giờ; vượt ngưỡng tính đủ số giây.
    public List<BookingDtos.ChargeLine> overstay(UUID roomTypeId, UUID branchId, ZoneId timezone, Instant start, Instant end) {
        if (!end.isAfter(start) || Duration.between(start, end).compareTo(Duration.ofMinutes(5)) <= 0) return List.of();
        return hourlyQuote(roomTypeId, branchId, timezone, start, end, true).charges();
    }

    // BR-170/215: không làm tròn thời gian; làm tròn tiền lên đồng trên từng khung giá.
    public static BigDecimal actualAmount(BigDecimal rate, long seconds) {
        return rate.multiply(BigDecimal.valueOf(Math.max(0, seconds))).divide(BigDecimal.valueOf(3600), 0, RoundingMode.CEILING);
    }

    private BookingDtos.PriceQuote hourlyQuote(UUID roomTypeId, UUID branchId, ZoneId timezone, Instant start, Instant end, boolean actual) {
        LocalDate firstDate = start.atZone(timezone).toLocalDate();
        LocalDate lastDate = end.minusNanos(1).atZone(timezone).toLocalDate();
        List<PricingRule> rules = repository.pricingRules(roomTypeId, branchId, firstDate, lastDate);
        if (rules.isEmpty()) throw new BookingFailure(409, "plannedStart", "Không có phòng hoặc mức giá phù hợp với lựa chọn hiện tại. Vui lòng thay đổi thời gian, hạng phòng hoặc hình thức thuê.");

        List<Segment> segments = new ArrayList<>();
        Instant cursor = start;
        while (cursor.isBefore(end)) {
            ZonedDateTime local = cursor.atZone(timezone);
            LocalDate date = local.toLocalDate();
            int minute = local.getHour() * 60 + local.getMinute();
            PricingRule selected = rules.stream().filter(rule -> applies(rule, date, minute))
                .max(Comparator.comparing((PricingRule rule) -> rule.branchId() != null)
                    .thenComparingInt(PricingRule::priority)
                    .thenComparingInt(rule -> specificity(rule.dayKind())))
                .orElseThrow(() -> new BookingFailure(409, "plannedStart", "Không có phòng hoặc mức giá phù hợp với lựa chọn hiện tại. Vui lòng thay đổi thời gian, hạng phòng hoặc hình thức thuê."));
            Instant nextBoundary = nextBoundary(cursor, end, date, minute, rules, timezone);
            if (!segments.isEmpty() && segments.get(segments.size() - 1).rule().id().equals(selected.id())) {
                Segment previous = segments.remove(segments.size() - 1);
                segments.add(new Segment(selected, previous.start(), nextBoundary));
            } else {
                segments.add(new Segment(selected, cursor, nextBoundary));
            }
            cursor = nextBoundary;
        }

        List<BookingDtos.ChargeLine> charges = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        // Mỗi đoạn áp dụng bước làm tròn của chính rule đó; đồng hồ giờ lẻ dùng cách tính khác.
        for (Segment segment : segments) {
            long seconds = Duration.between(segment.start(), segment.end()).getSeconds();
            long actualMinutes = Math.max(1, (seconds + 59) / 60);
            long step = segment.rule().billingStepMinutes();
            long billedMinutes = ((actualMinutes + step - 1) / step) * step;
            BigDecimal quantity = BigDecimal.valueOf(billedMinutes).divide(BigDecimal.valueOf(60), 3, RoundingMode.HALF_UP);
            BigDecimal amount = segment.rule().hourlyPrice().multiply(BigDecimal.valueOf(billedMinutes))
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
            if (actual) {
                quantity = BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 3, RoundingMode.UP);
                amount = actualAmount(segment.rule().hourlyPrice(), seconds);
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("pricingMode", "HOURLY"); snapshot.put("ruleName", segment.rule().name());
            snapshot.put("ruleId", segment.rule().id().toString()); snapshot.put("from", segment.start().toString());
            snapshot.put("to", segment.end().toString()); snapshot.put("actualMinutes", actualMinutes);
            snapshot.put("billedMinutes", billedMinutes); snapshot.put("billingStepMinutes", step);
            snapshot.put("hourlyPrice", segment.rule().hourlyPrice());
            if (actual) { snapshot.put("billedSeconds", seconds); snapshot.put("billing", "ACTUAL_SECONDS_BR170"); }
            charges.add(new BookingDtos.ChargeLine("ROOM", segment.rule().name(), quantity,
                segment.rule().hourlyPrice(), amount, segment.rule().id(), null, snapshot));
            total = total.add(amount);
        }
        return new BookingDtos.PriceQuote("HOURLY", total, BigDecimal.ZERO, List.copyOf(charges));
    }

    // Kiểm tra price rule có áp dụng cho ngày và phút địa phương hiện tại không.
    private static boolean applies(PricingRule rule, LocalDate date, int minute) {
        if (date.isBefore(rule.validFrom()) || (rule.validTo() != null && !date.isBefore(rule.validTo()))) return false;
        if (minute < rule.minuteFrom() || minute >= rule.minuteTo()) return false;
        return switch (rule.dayKind()) {
            case "SPECIFIC" -> date.equals(rule.specificDate());
            case "WEEKEND" -> date.getDayOfWeek().getValue() >= 6;
            case "WEEKDAY" -> date.getDayOfWeek().getValue() < 6;
            case "ALL" -> true;
            // Schema hiện chưa có lịch ngày lễ để xác định một ngày thuộc loại HOLIDAY.
            case "HOLIDAY" -> false;
            default -> false;
        };
    }

    // Phân xử độ cụ thể khi các rule có cùng phạm vi chi nhánh và mức ưu tiên.
    private static int specificity(String dayKind) {
        return switch (dayKind) {
            case "SPECIFIC" -> 4;
            case "HOLIDAY" -> 3;
            case "WEEKDAY", "WEEKEND" -> 2;
            default -> 1;
        };
    }

    // Tìm ranh giới kế tiếp để một đoạn chỉ dùng một price rule.
    private static Instant nextBoundary(Instant cursor, Instant end, LocalDate date, int currentMinute,
                                        List<PricingRule> rules, ZoneId timezone) {
        Instant next = date.plusDays(1).atStartOfDay(timezone).toInstant();
        for (PricingRule rule : rules) {
            if (!date.isBefore(rule.validFrom()) && (rule.validTo() == null || date.isBefore(rule.validTo()))) {
                for (int minute : new int[]{rule.minuteFrom(), rule.minuteTo()}) {
                    if (minute > currentMinute && minute < 1440) {
                        Instant boundary = LocalDateTime.of(date, LocalTime.MIDNIGHT).plusMinutes(minute).atZone(timezone).toInstant();
                        if (boundary.isAfter(cursor) && boundary.isBefore(next)) next = boundary;
                    }
                }
            }
        }
        return next.isBefore(end) ? next : end;
    }

    private record Segment(PricingRule rule, Instant start, Instant end) {}
}
