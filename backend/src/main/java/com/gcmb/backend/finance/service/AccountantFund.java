/** HuyDV | BR-83/87: một số dư và một khóa chi tiền cho quỹ kế toán toàn chuỗi. */
package com.gcmb.backend.finance.service;

import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;

public final class AccountantFund {
    private AccountantFund() {}

    public static void lock(JdbcTemplate db) {
        db.execute("SELECT pg_advisory_xact_lock(1300261103)");
    }

    public static BigDecimal balance(JdbcTemplate db) {
        // Thu/cấp quỹ và đề nghị chi dùng sổ mới; lương/ứng và giao dịch quỹ cũ đã ghi sổ cái.
        return db.queryForObject("""
            SELECT COALESCE((SELECT sum(amount) FROM gcmb.finance_fund_transfer WHERE status='RECEIVED'),0)
              - COALESCE((SELECT sum(amount) FROM gcmb.finance_expense_request WHERE status='PAID'),0)
              - COALESCE((SELECT sum(amount) FROM gcmb.cash_drawer_funding WHERE status='PAID'),0)
              + COALESCE((SELECT sum(e.amount_signed) FROM gcmb.financial_entry e
                  JOIN gcmb.financial_account a ON a.id=e.account_id WHERE a.kind='CENTRAL_FUND'),0)
            """, BigDecimal.class);
    }
}
