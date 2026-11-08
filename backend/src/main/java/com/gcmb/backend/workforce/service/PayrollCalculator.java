/** Author: HuyDV. UC-91/94/96 / BR-67..73/210/225/227: phân đoạn phút, không tính trùng. */
package com.gcmb.backend.workforce.service;

import java.math.*;
import java.time.*;
import java.util.*;

public final class PayrollCalculator {

  private PayrollCalculator() {}

  public record Rates(
    BigDecimal hourly,
    BigDecimal normal,
    BigDecimal rest,
    BigDecimal holiday,
    BigDecimal night
  ) {}

  public record Work(
    Instant actualIn,
    Instant actualOut,
    Instant plannedIn,
    Instant plannedOut,
    ZoneId zone
  ) {}

  public record Policy(Rates rates, String dayKind) {}

  public record Segment(
    LocalDate date,
    String kind,
    int minutes,
    BigDecimal hourly,
    BigDecimal multiplier
  ) {
    public BigDecimal minuteAmount() {
      return hourly.multiply(multiplier).multiply(BigDecimal.valueOf(minutes));
    }
  }

  public static Map<String, BigDecimal> calculate(
    Work work,
    java.util.function.Function<LocalDate, Policy> policy
  ) {
    var result = new LinkedHashMap<String, BigDecimal>();
    for (String kind : List.of("BASE", "OVERTIME", "REST", "HOLIDAY", "NIGHT"))
      result.put(kind, BigDecimal.ZERO);
    for (var segment : segments(work, policy))
      result.merge(segment.kind(), segment.minuteAmount(), BigDecimal::add);
    return result;
  }

  public static List<Segment> segments(
    Work work,
    java.util.function.Function<LocalDate, Policy> policy
  ) {
    var result = new LinkedHashMap<String, Segment>();
    for (
      Instant cursor = work.actualIn();
      cursor.isBefore(work.actualOut());
      cursor = cursor.plusSeconds(60)
    ) {
      var local = cursor.atZone(work.zone());
      var p = policy.apply(local.toLocalDate());
      var rate = p.rates();
      String kind;
      BigDecimal multiplier;
      if ("HOLIDAY".equals(p.dayKind())) {
        kind = "HOLIDAY";
        multiplier = rate.holiday();
      } else if ("REST".equals(p.dayKind())) {
        kind = "REST";
        multiplier = rate.rest();
      } else if (cursor.isBefore(work.plannedIn()) || !cursor.isBefore(work.plannedOut())) {
        kind = "OVERTIME";
        multiplier = rate.normal();
      } else {
        kind = "BASE";
        multiplier = BigDecimal.ONE;
      }
      // Keep amount in VND-minutes; divide and round once after grouping all work components.
      add(result, local.toLocalDate(), kind, rate.hourly(), multiplier);
      if (local.getHour() >= 22 || local.getHour() < 6) add(
        result,
        local.toLocalDate(),
        "NIGHT",
        rate.hourly(),
        rate.night()
      );
    }
    return new ArrayList<>(result.values());
  }

  private static void add(
    Map<String, Segment> result,
    LocalDate date,
    String kind,
    BigDecimal hourly,
    BigDecimal multiplier
  ) {
    String key = date + ":" + kind;
    var old = result.get(key);
    result.put(
      key,
      new Segment(date, kind, old == null ? 1 : old.minutes() + 1, hourly, multiplier)
    );
  }

  public static BigDecimal rounded(BigDecimal minuteAmount) {
    return minuteAmount.divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
  }
}
