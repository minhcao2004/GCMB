-- Author: HuyDV. UC-98..101 / BR-95..98/208: daily cash/bank/bill reconciliation.
-- Preserve V44 receipts as history; new reports never create Head Office handovers.
CREATE TABLE gcmb.cash_closing_report_revision (
  closing_id uuid NOT NULL REFERENCES gcmb.daily_cash_closing(id),
  revision integer NOT NULL CHECK (revision>0),
  payload jsonb NOT NULL,
  submitted_by uuid NOT NULL REFERENCES gcmb.user_account(id),
  submitted_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(closing_id,revision)
);
CREATE TABLE gcmb.cash_closing_report (
  closing_id uuid PRIMARY KEY REFERENCES gcmb.daily_cash_closing(id),
  revision integer NOT NULL,
  state varchar(25) NOT NULL CHECK (state IN ('SUBMITTED','NEEDS_EXPLANATION','RECONCILED')),
  FOREIGN KEY(closing_id,revision) REFERENCES gcmb.cash_closing_report_revision(closing_id,revision)
);
CREATE TABLE gcmb.cash_closing_revision_evidence (
  closing_id uuid NOT NULL,
  revision integer NOT NULL,
  original_name varchar(200) NOT NULL,
  mime_type varchar(100) NOT NULL,
  object_key varchar(200) NOT NULL,
  PRIMARY KEY(closing_id,revision),
  FOREIGN KEY(closing_id,revision) REFERENCES gcmb.cash_closing_report_revision(closing_id,revision)
);
CREATE TABLE gcmb.cash_drawer_remittance (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES gcmb.financial_account(id),
  branch_id uuid NOT NULL REFERENCES gcmb.branch(id),
  occurred_at timestamptz NOT NULL,
  amount numeric(18,0) NOT NULL CHECK (amount>0),
  reason text NOT NULL CHECK (length(trim(reason))>0),
  reference varchar(200) NOT NULL,
  recorded_by uuid NOT NULL REFERENCES gcmb.user_account(id),
  idempotency_key uuid NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX cash_drawer_remittance_by_day ON gcmb.cash_drawer_remittance(account_id,occurred_at);
CREATE FUNCTION gcmb.retain_till_report_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Till report history is immutable'; END; $$;
CREATE TRIGGER retain_till_revision BEFORE UPDATE OR DELETE ON gcmb.cash_closing_report_revision
FOR EACH ROW EXECUTE FUNCTION gcmb.retain_till_report_history();
CREATE TRIGGER retain_till_evidence BEFORE UPDATE OR DELETE ON gcmb.cash_closing_revision_evidence
FOR EACH ROW EXECUTE FUNCTION gcmb.retain_till_report_history();
CREATE TRIGGER retain_cash_remittance BEFORE UPDATE OR DELETE ON gcmb.cash_drawer_remittance
FOR EACH ROW EXECUTE FUNCTION gcmb.retain_till_report_history();
CREATE FUNCTION gcmb.guard_till_report_state() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' OR OLD.state='RECONCILED' THEN RAISE EXCEPTION 'Final till report is immutable'; END IF;
  IF NOT ((OLD.state='SUBMITTED' AND NEW.state IN ('NEEDS_EXPLANATION','RECONCILED') AND NEW.revision=OLD.revision)
    OR (OLD.state='NEEDS_EXPLANATION' AND NEW.state='SUBMITTED' AND NEW.revision=OLD.revision+1))
    OR NEW.closing_id<>OLD.closing_id THEN RAISE EXCEPTION 'Invalid till report transition'; END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER guard_till_state BEFORE UPDATE OR DELETE ON gcmb.cash_closing_report
FOR EACH ROW EXECUTE FUNCTION gcmb.guard_till_report_state();

-- Reconciled count variance corrects the recorded drawer, without being a cash receipt.
-- It takes effect at the end of the counted day so the next day carries the actual balance.
CREATE VIEW gcmb.daily_till_movement AS
SELECT r.id::text AS id,r.account_id,r.occurred_at AS time,-r.amount AS amount,
  'REMITTANCE'::text AS kind,r.reason AS description
FROM gcmb.cash_drawer_remittance r
UNION ALL
SELECT c.id::text||'-COUNT',c.account_id,
  ((c.business_date+1)::timestamp AT TIME ZONE b.timezone)-interval '1 microsecond',
  (v.payload->>'variance')::numeric,'COUNT_VARIANCE','Chênh lệch kiểm đếm đã được kế toán đối soát'
FROM gcmb.cash_closing_report r JOIN gcmb.cash_closing_report_revision v
  ON v.closing_id=r.closing_id AND v.revision=r.revision
JOIN gcmb.daily_cash_closing c ON c.id=r.closing_id JOIN gcmb.branch b ON b.id=c.branch_id
WHERE r.state='RECONCILED' AND (v.payload->>'variance')::numeric<>0;

CREATE OR REPLACE VIEW gcmb.v_account_balance AS
SELECT a.id,a.code,a.branch_id,COALESCE(sum(e.amount_signed),0) AS balance
FROM gcmb.financial_account a LEFT JOIN (
  SELECT account_id,amount_signed FROM gcmb.financial_entry
  UNION ALL SELECT account_id,amount FROM gcmb.cash_handover_movement
  UNION ALL SELECT account_id,amount FROM gcmb.daily_till_movement
  UNION ALL SELECT a.id,p.amount FROM gcmb.payment p
    JOIN gcmb.financial_account a ON a.branch_id=p.branch_id AND a.kind='CASH_DRAWER' AND a.active
    WHERE p.method='CASH' AND NOT EXISTS (
      SELECT 1 FROM gcmb.financial_transaction t JOIN gcmb.financial_entry e ON e.transaction_id=t.id WHERE t.payment_id=p.id
    )
) e ON e.account_id=a.id GROUP BY a.id,a.code,a.branch_id;
