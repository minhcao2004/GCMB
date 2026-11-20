package com.gcmb.backend.finance.service;

import java.time.LocalDate;
import java.time.YearMonth;

/** Calendar rules for recurring expense cycles; independent of persistence. */
public final class RecurringExpenseCalendar {
    private RecurringExpenseCalendar() {}

    public static LocalDate dueDate(YearMonth dueMonth, int dueDay) {
        if (dueMonth == null || dueDay < 1 || dueDay > 31) {
            throw new IllegalArgumentException("Ngày trả cố định phải từ 1 đến 31.");
        }
        return dueMonth.atDay(Math.min(dueDay, dueMonth.lengthOfMonth()));
    }

    public static LocalDate periodMonth(YearMonth dueMonth, String expenseBelongsTo) {
        if (dueMonth == null) throw new IllegalArgumentException("Tháng đến hạn không hợp lệ.");
        return switch (expenseBelongsTo == null ? "" : expenseBelongsTo) {
            case "SAME_MONTH" -> dueMonth.atDay(1);
            case "PREVIOUS_MONTH" -> dueMonth.minusMonths(1).atDay(1);
            default -> throw new IllegalArgumentException("Tháng ghi nhận chi phí không hợp lệ.");
        };
    }

    public static boolean withinTerm(LocalDate periodMonth, LocalDate startMonth, LocalDate endMonth) {
        if (periodMonth == null || startMonth == null) return false;
        return !periodMonth.isBefore(startMonth) && (endMonth == null || !periodMonth.isAfter(endMonth));
    }
}
