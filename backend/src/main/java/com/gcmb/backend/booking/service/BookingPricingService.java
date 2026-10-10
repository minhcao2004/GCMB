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

    public BookingPricingService(BookingRepository repository) { this.repository = repository; }

    public BookingDtos.PriceQuote quote(UUID roomTypeId, UUID branchId, ZoneId timezone,
                                        Instant start, Instant end, String mode, UUID comboId) {
        if (start == null || end == null || !end.isAfter(start))
            throw new BookingFailure(400, "plannedEnd", "Thời gian kết thúc phải sau thời gian bắt đầu");
        String normalizedMode = mode == null || mode.isBlank() ? "HOURLY" : mode.trim().toUpperCase();
        if ("COMBO".equals(normalizedMode)) return comboQuote(roomTypeId, start, end, comboId);
        if (!"HOURLY".equals(normalizedMode)) throw new BookingFailure(400, "pricingMode", "Hình thức tính giá không hợp lệ");
        if (comboId != null) throw new BookingFailure(400, "comboId", "Không chọn combo khi tính giá theo giờ");
        return hourlyQuote(roomTypeId, branchId, timezone, start, end);
    }

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
        snapshot.put("depositRequired", combo.depositRequired());
        snapshot.put("overtimeHourlyPrice", combo.overtimeHourlyPrice());
        BookingDtos.ChargeLine line = new BookingDtos.ChargeLine("COMBO", combo.name(), BigDecimal.ONE,
            combo.price(), combo.price(), null, combo.id(), snapshot);
        return new BookingDtos.PriceQuote("COMBO", combo.price(), combo.depositRequired(), List.of(line));
    }

    private BookingDtos.PriceQuote hourlyQuote(UUID roomTypeId, UUID branchId, ZoneId timezone, Instant start, Instant end) {
        LocalDate firstDate = start.atZone(timezone).toLocalDate();
        LocalDate lastDate = end.minusNanos(1).atZone(timezone).toLocalDate();
        List<PricingRule> rules = repository.pricingRules(roomTypeId, branchId, firstDate, lastDate);
        if (rules.isEmpty()) throw new BookingFailure(409, "plannedStart", "Chưa cấu hình giá giờ cho loại phòng và thời gian đã chọn");

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
                .orElseThrow(() -> new BookingFailure(409, "plannedStart", "Không có quy tắc giá áp dụng cho toàn bộ thời gian đã chọn"));
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
        for (Segment segment : segments) {
            long seconds = Duration.between(segment.start(), segment.end()).getSeconds();
            long actualMinutes = Math.max(1, (seconds + 59) / 60);
            long step = segment.rule().billingStepMinutes();
            long billedMinutes = ((actualMinutes + step - 1) / step) * step;
            BigDecimal quantity = BigDecimal.valueOf(billedMinutes).divide(BigDecimal.valueOf(60), 3, RoundingMode.HALF_UP);
            BigDecimal amount = segment.rule().hourlyPrice().multiply(BigDecimal.valueOf(billedMinutes))
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("pricingMode", "HOURLY"); snapshot.put("ruleName", segment.rule().name());
            snapshot.put("ruleId", segment.rule().id().toString()); snapshot.put("from", segment.start().toString());
            snapshot.put("to", segment.end().toString()); snapshot.put("actualMinutes", actualMinutes);
            snapshot.put("billedMinutes", billedMinutes); snapshot.put("billingStepMinutes", step);
            snapshot.put("hourlyPrice", segment.rule().hourlyPrice());
            charges.add(new BookingDtos.ChargeLine("ROOM", segment.rule().name(), quantity,
                segment.rule().hourlyPrice(), amount, segment.rule().id(), null, snapshot));
            total = total.add(amount);
        }
        return new BookingDtos.PriceQuote("HOURLY", total, BigDecimal.ZERO, List.copyOf(charges));
    }

    private static boolean applies(PricingRule rule, LocalDate date, int minute) {
        if (date.isBefore(rule.validFrom()) || (rule.validTo() != null && !date.isBefore(rule.validTo()))) return false;
        if (minute < rule.minuteFrom() || minute >= rule.minuteTo()) return false;
        return switch (rule.dayKind()) {
            case "SPECIFIC" -> date.equals(rule.specificDate());
            case "WEEKEND" -> date.getDayOfWeek().getValue() >= 6;
            case "WEEKDAY" -> date.getDayOfWeek().getValue() < 6;
            case "ALL" -> true;
            // The current schema has no holiday calendar with which to classify a date.
            case "HOLIDAY" -> false;
            default -> false;
        };
    }

    private static int specificity(String dayKind) {
        return switch (dayKind) {
            case "SPECIFIC" -> 4;
            case "HOLIDAY" -> 3;
            case "WEEKDAY", "WEEKEND" -> 2;
            default -> 1;
        };
    }

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
