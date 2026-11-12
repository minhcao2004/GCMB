-- Author: HuyDV. Agreed cash workflow: five-day sweep reminder and 500k..1m drawer funding.
-- Funding is an internal transfer, not a branch expense or customer revenue.
ALTER TABLE gcmb.cash_drawer_remittance
  ADD COLUMN balance_before numeric(18,0) CHECK (balance_before>=amount),
  ADD COLUMN full_sweep boolean NOT NULL DEFAULT false,
  ADD CONSTRAINT full_drawer_sweep_balance CHECK (NOT full_sweep OR (balance_before IS NOT NULL AND balance_before=amount));

CREATE TABLE gcmb.cash_drawer_funding (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code varchar(60) NOT NULL UNIQUE,
  branch_id uuid NOT NULL REFERENCES gcmb.branch(id),
  account_id uuid NOT NULL REFERENCES gcmb.financial_account(id),
  amount numeric(18,0) NOT NULL CHECK (amount BETWEEN 500000 AND 1000000),
  reason text NOT NULL CHECK (length(trim(reason))>0),
  status varchar(20) NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED','PAID','REJECTED')),
  requested_by uuid NOT NULL REFERENCES gcmb.user_account(id),
  requested_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  idempotency_key uuid NOT NULL UNIQUE,
  processed_by uuid REFERENCES gcmb.user_account(id),
  processed_at timestamptz,
  payment_reference varchar(200) NOT NULL DEFAULT '',
  resolution_note text NOT NULL DEFAULT '',
  CHECK ((status='REQUESTED' AND processed_by IS NULL AND processed_at IS NULL)
    OR (status<>'REQUESTED' AND processed_by IS NOT NULL AND processed_at IS NOT NULL)),
  CHECK (status<>'REJECTED' OR length(trim(resolution_note))>0)
);
CREATE UNIQUE INDEX one_pending_drawer_funding ON gcmb.cash_drawer_funding(account_id) WHERE status='REQUESTED';
CREATE FUNCTION gcmb.protect_drawer_funding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Drawer funding history is retained'; END IF;
  IF OLD.status<>'REQUESTED' OR NEW.status NOT IN ('PAID','REJECTED') OR
    (to_jsonb(NEW)-ARRAY['status','processed_by','processed_at','payment_reference','resolution_note']) IS DISTINCT FROM
    (to_jsonb(OLD)-ARRAY['status','processed_by','processed_at','payment_reference','resolution_note']) THEN
    RAISE EXCEPTION 'Drawer funding may be settled only once';
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER retain_drawer_funding BEFORE UPDATE OR DELETE ON gcmb.cash_drawer_funding
FOR EACH ROW EXECUTE FUNCTION gcmb.protect_drawer_funding();

CREATE VIEW gcmb.drawer_funding_movement AS
SELECT id::text AS id,account_id,processed_at AS time,amount,'DRAWER_FUNDING'::text AS kind,
  'Cấp tiền két - '||code AS description FROM gcmb.cash_drawer_funding WHERE status='PAID';

CREATE OR REPLACE VIEW gcmb.v_account_balance AS
SELECT a.id,a.code,a.branch_id,COALESCE(sum(e.amount_signed),0) AS balance
FROM gcmb.financial_account a LEFT JOIN (
  SELECT account_id,amount_signed FROM gcmb.financial_entry
  UNION ALL SELECT account_id,amount FROM gcmb.cash_handover_movement
  UNION ALL SELECT account_id,amount FROM gcmb.daily_till_movement
  UNION ALL SELECT account_id,amount FROM gcmb.drawer_funding_movement
  UNION ALL SELECT a.id,p.amount FROM gcmb.payment p
    JOIN gcmb.financial_account a ON a.branch_id=p.branch_id AND a.kind='CASH_DRAWER' AND a.active
    WHERE p.method='CASH' AND NOT EXISTS (
      SELECT 1 FROM gcmb.financial_transaction t JOIN gcmb.financial_entry e ON e.transaction_id=t.id WHERE t.payment_id=p.id
    )
) e ON e.account_id=a.id GROUP BY a.id,a.code,a.branch_id;
