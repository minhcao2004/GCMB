package com.gcmb.backend.finance.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Pure VND allocation math; the first remainder months receive one extra đồng. */
public final class ExpenseAllocationCalculator {
    public static final int MIN_MONTHS = 2;
    public static final int MAX_MONTHS = 60;

    private ExpenseAllocationCalculator() {}

    public static List<Line> calculate(BigDecimal total, int months) {
        if (months < MIN_MONTHS || months > MAX_MONTHS) {
            throw new IllegalArgumentException("Số tháng phân bổ phải từ 2 đến 60.");
        }
        if (total == null || total.signum() <= 0) {
            throw new IllegalArgumentException("Số tiền phân bổ phải lớn hơn 0đ.");
        }
        final long amount;
        try {
            amount = total.longValueExact();
        } catch (ArithmeticException invalidAmount) {
            throw new IllegalArgumentException("Số tiền phân bổ phải là VND nguyên, không có phần thập phân.");
        }
        if (amount < months) {
            throw new IllegalArgumentException("Số tiền phải đủ để mỗi tháng được phân bổ ít nhất 1đ.");
        }

        long base = amount / months;
        long remainder = amount % months;
        List<Line> result = new ArrayList<>(months);
        for (int index = 0; index < months; index++) {
            result.add(new Line(index + 1, base + (index < remainder ? 1 : 0)));
        }
        return List.copyOf(result);
    }

    public record Line(int sequence, long amount) {}
}
