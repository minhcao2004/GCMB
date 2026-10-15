/*
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Lưu phân bổ tiền mặt tạm; chỉ ghi khoản thu khi hoàn tất bill.
 */
ALTER TABLE gcmb.bill
    ADD COLUMN planned_cash numeric(18,0) NOT NULL DEFAULT 0,
    ADD COLUMN planned_cash_tendered numeric(18,0) NOT NULL DEFAULT 0,
    ADD CONSTRAINT bill_planned_cash_valid CHECK (
        planned_cash >= 0 AND planned_cash_tendered >= planned_cash
        AND (planned_cash > 0 OR planned_cash_tendered = 0)
    );

-- Ghi bản điều chỉnh riêng: không sửa/xóa payment vốn là bảng chỉ ghi thêm.
CREATE TABLE gcmb.checkout_cash_reclassification (
    payment_id uuid PRIMARY KEY REFERENCES gcmb.payment(id),
    reason text NOT NULL DEFAULT 'Tiền mặt checkout cũ được chuyển về phân bổ tạm bởi V13',
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER immutable_checkout_cash_reclassification
    BEFORE UPDATE OR DELETE ON gcmb.checkout_cash_reclassification
    FOR EACH ROW EXECUTE FUNCTION gcmb.reject_history_mutation();

-- Chỉ chuyển tiền mặt checkout chưa hoàn tất/chưa nhận chuyển khoản/chưa vào sổ quỹ.
WITH drafts AS (
    SELECT p.bill_id,sum(p.amount) AS amount,sum(p.cash_tendered) AS tendered
    FROM gcmb.payment p JOIN gcmb.bill b ON b.id=p.bill_id
    WHERE b.status='ISSUED' AND p.purpose='BILL' AND p.method='CASH'
        AND p.idempotency_key LIKE 'CHECKOUT-CASH-%'
        AND NOT EXISTS (SELECT 1 FROM gcmb.payment x WHERE x.bill_id=b.id AND x.method<>'CASH')
        AND NOT EXISTS (SELECT 1 FROM gcmb.financial_transaction t JOIN gcmb.payment x ON x.id=t.payment_id WHERE x.bill_id=b.id)
    GROUP BY p.bill_id,b.net_amount
    HAVING sum(p.amount)<b.net_amount
)
UPDATE gcmb.bill b SET planned_cash=d.amount,planned_cash_tendered=d.tendered
FROM drafts d WHERE b.id=d.bill_id;

INSERT INTO gcmb.checkout_cash_reclassification(payment_id)
SELECT p.id FROM gcmb.payment p JOIN gcmb.bill b ON b.id=p.bill_id
WHERE b.status='ISSUED' AND b.planned_cash>0
    AND p.purpose='BILL' AND p.method='CASH' AND p.idempotency_key LIKE 'CHECKOUT-CASH-%';

CREATE OR REPLACE VIEW gcmb.v_bill_balance AS
SELECT b.id,b.booking_id,b.net_amount,
    COALESCE((SELECT sum(e.amount) FROM gcmb.deposit_entry e WHERE e.bill_id=b.id AND e.kind='APPLY'),0) AS deposit_applied,
    COALESCE((SELECT sum(p.amount) FROM gcmb.payment p WHERE p.bill_id=b.id AND p.purpose='BILL' AND NOT EXISTS (SELECT 1 FROM gcmb.checkout_cash_reclassification c WHERE c.payment_id=p.id)),0) AS payments,
    b.net_amount+COALESCE((SELECT sum(x.amount_signed) FROM gcmb.business_adjustment x WHERE x.bill_id=b.id),0)
    -COALESCE((SELECT sum(e.amount) FROM gcmb.deposit_entry e WHERE e.bill_id=b.id AND e.kind='APPLY'),0)
    -COALESCE((SELECT sum(p.amount) FROM gcmb.payment p WHERE p.bill_id=b.id AND p.purpose='BILL' AND NOT EXISTS (SELECT 1 FROM gcmb.checkout_cash_reclassification c WHERE c.payment_id=p.id)),0) AS remaining_due
FROM gcmb.bill b;

COMMENT ON COLUMN gcmb.bill.planned_cash IS 'Phân bổ tiền mặt tạm, chưa phải khoản thu hay tiền cọc';
COMMENT ON TABLE gcmb.checkout_cash_reclassification IS 'Bản điều chỉnh khoản tiền mặt checkout cũ; giữ nguyên payment và không tính là thu thực tế';
