ALTER TABLE gcmb.bill
    ADD COLUMN checkout_rounding_adjustment numeric(18,0) NOT NULL DEFAULT 0;

COMMENT ON COLUMN gcmb.bill.checkout_rounding_adjustment IS
    'Signed settlement-only rounding adjustment: positive adds to net_amount, negative reduces it; bill lines and historical bills remain unchanged.';

CREATE OR REPLACE VIEW gcmb.v_bill_balance AS
SELECT b.id,b.booking_id,b.net_amount,
    COALESCE((SELECT sum(e.amount) FROM gcmb.deposit_entry e WHERE e.bill_id=b.id AND e.kind='APPLY'),0) AS deposit_applied,
    COALESCE((SELECT sum(p.amount) FROM gcmb.payment p WHERE p.bill_id=b.id AND p.purpose='BILL' AND NOT EXISTS (SELECT 1 FROM gcmb.checkout_cash_reclassification c WHERE c.payment_id=p.id)),0) AS payments,
    b.net_amount+b.checkout_rounding_adjustment+COALESCE((SELECT sum(x.amount_signed) FROM gcmb.business_adjustment x WHERE x.bill_id=b.id),0)
    -COALESCE((SELECT sum(e.amount) FROM gcmb.deposit_entry e WHERE e.bill_id=b.id AND e.kind='APPLY'),0)
    -COALESCE((SELECT sum(p.amount) FROM gcmb.payment p WHERE p.bill_id=b.id AND p.purpose='BILL' AND NOT EXISTS (SELECT 1 FROM gcmb.checkout_cash_reclassification c WHERE c.payment_id=p.id)),0) AS remaining_due
FROM gcmb.bill b;
