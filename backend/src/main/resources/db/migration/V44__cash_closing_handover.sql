-- Author: HuyDV. UC-98..101 / BR-95/96/208: retain count and variance;
-- agreed extension: only Head Office receipt removes the counted cash from the drawer.
CREATE TABLE gcmb.cash_closing_handover (
  closing_id uuid PRIMARY KEY REFERENCES gcmb.daily_cash_closing(id),
  account_id uuid NOT NULL REFERENCES gcmb.financial_account(id),
  requested_by uuid NOT NULL REFERENCES gcmb.user_account(id),
  requested_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  received_by uuid REFERENCES gcmb.user_account(id),
  received_at timestamptz,
  CHECK ((received_by IS NULL) = (received_at IS NULL)),
  CHECK (received_at IS NULL OR received_at >= requested_at)
);
CREATE UNIQUE INDEX one_pending_cash_handover ON gcmb.cash_closing_handover(account_id)
  WHERE received_at IS NULL;

CREATE FUNCTION gcmb.protect_cash_handover() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Cash handovers are retained'; END IF;
  IF TG_OP='INSERT' THEN
    IF NEW.received_at IS NOT NULL OR NOT EXISTS (
      SELECT 1 FROM gcmb.daily_cash_closing c
      WHERE c.id=NEW.closing_id AND c.account_id=NEW.account_id AND c.status IN ('SUBMITTED','RECONCILED')
    ) THEN RAISE EXCEPTION 'Invalid cash handover'; END IF;
  ELSE
    IF OLD.received_at IS NOT NULL OR NEW.received_at IS NULL OR
      (to_jsonb(NEW)-'received_by'-'received_at') IS DISTINCT FROM
      (to_jsonb(OLD)-'received_by'-'received_at') THEN
      RAISE EXCEPTION 'Cash handover receipt is immutable';
    END IF;
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER cash_handover_history BEFORE INSERT OR UPDATE OR DELETE
  ON gcmb.cash_closing_handover FOR EACH ROW EXECUTE FUNCTION gcmb.protect_cash_handover();

-- Separate the physical remittance (-actual) and count variance (actual-expected).
-- Their net removes the old expected cash; original variance remains unreconciled.
CREATE VIEW gcmb.cash_handover_movement AS
SELECT h.closing_id::text || '-' || m.kind AS id,h.account_id,h.received_at AS time,
  m.amount,m.kind,m.description
FROM gcmb.cash_closing_handover h JOIN gcmb.daily_cash_closing c ON c.id=h.closing_id
CROSS JOIN LATERAL (VALUES
  (-c.actual_closing,'REMITTANCE','Nộp toàn bộ tiền kiểm đếm cho QLCC'),
  (c.variance,'COUNT_VARIANCE','Chênh lệch kiểm đếm gốc (đối soát riêng)')
) m(amount,kind,description)
WHERE h.received_at IS NOT NULL AND m.amount<>0;

CREATE OR REPLACE VIEW gcmb.v_account_balance AS
SELECT a.id,a.code,a.branch_id,COALESCE(sum(e.amount_signed),0) AS balance
FROM gcmb.financial_account a LEFT JOIN (
  SELECT account_id,amount_signed FROM gcmb.financial_entry
  UNION ALL SELECT account_id,amount FROM gcmb.cash_handover_movement
  UNION ALL
  SELECT a.id,p.amount FROM gcmb.payment p
  JOIN gcmb.financial_account a ON a.branch_id=p.branch_id AND a.kind='CASH_DRAWER' AND a.active
  WHERE p.method='CASH' AND NOT EXISTS (
    SELECT 1 FROM gcmb.financial_transaction t JOIN gcmb.financial_entry e ON e.transaction_id=t.id
    WHERE t.payment_id=p.id
  )
) e ON e.account_id=a.id
GROUP BY a.id,a.code,a.branch_id;
