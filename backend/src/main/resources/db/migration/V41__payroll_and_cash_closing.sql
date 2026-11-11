-- Author: HuyDV | UC-91..101 / BR-64..81, BR-95..98, BR-208/213/225.
ALTER TABLE gcmb.payroll_period ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE gcmb.daily_cash_closing ADD COLUMN source_snapshot jsonb NOT NULL DEFAULT '[]';
CREATE TABLE gcmb.payroll_setting (
  effective_from date PRIMARY KEY,
  normal_multiplier numeric(10,4) NOT NULL CHECK (normal_multiplier >= 0),
  rest_multiplier numeric(10,4) NOT NULL CHECK (rest_multiplier >= 0),
  holiday_multiplier numeric(10,4) NOT NULL CHECK (holiday_multiplier >= 0),
  night_rate numeric(10,4) NOT NULL CHECK (night_rate >= 0),
  changed_by uuid NOT NULL REFERENCES gcmb.user_account(id),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE gcmb.payroll_calendar (
  work_date date PRIMARY KEY,
  day_kind varchar(10) NOT NULL CHECK (day_kind IN ('HOLIDAY','REST','NORMAL')),
  description varchar(500) NOT NULL,
  changed_by uuid NOT NULL REFERENCES gcmb.user_account(id)
);
CREATE TABLE gcmb.payroll_calendar_year (
  year integer PRIMARY KEY CHECK (year BETWEEN 2000 AND 9998),
  confirmed_by uuid NOT NULL REFERENCES gcmb.user_account(id),
  confirmed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE gcmb.cash_closing_evidence (
  closing_id uuid PRIMARY KEY REFERENCES gcmb.daily_cash_closing(id),
  original_name varchar(200) NOT NULL,
  mime_type varchar(100) NOT NULL,
  object_key varchar(200) NOT NULL
);
-- Existing V1 trigger bodies use unqualified names even on JDBC sessions without search_path.
ALTER FUNCTION gcmb.require_open_financial_period() SET search_path = gcmb, pg_catalog;
ALTER FUNCTION gcmb.validate_financial_entry() SET search_path = gcmb, pg_catalog;

-- Create empty operational drawers only; never invent opening cash or fund inflows.
INSERT INTO gcmb.financial_account(code,name,kind,branch_id,active)
SELECT 'CASH-'||b.id::text,'Két tiền mặt - '||b.name,'CASH_DRAWER',b.id,true
FROM gcmb.branch b WHERE b.status='ACTIVE' AND NOT EXISTS
  (SELECT 1 FROM gcmb.financial_account a WHERE a.branch_id=b.id AND a.kind='CASH_DRAWER' AND a.active);
INSERT INTO gcmb.financial_account(code,name,kind,active)
SELECT 'PAYROLL-CENTRAL','Quỹ kế toán','CENTRAL_FUND',true WHERE NOT EXISTS
  (SELECT 1 FROM gcmb.financial_account WHERE kind='CENTRAL_FUND' AND active);

-- Settlement versions may advance after confirmation; approval and month cannot be rewritten.
CREATE FUNCTION gcmb.protect_payroll_period() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payroll periods are retained'; END IF;
  IF OLD.status='CONFIRMED' AND ((to_jsonb(NEW)-'version') IS DISTINCT FROM (to_jsonb(OLD)-'version') OR NEW.version<>OLD.version+1) THEN
    RAISE EXCEPTION 'Confirmed payroll approval is immutable';
  END IF;
  RETURN NEW;
END; $$;
DROP TRIGGER lock_payroll_period ON gcmb.payroll_period;
CREATE TRIGGER lock_payroll_period BEFORE UPDATE OR DELETE ON gcmb.payroll_period
FOR EACH ROW EXECUTE FUNCTION gcmb.protect_payroll_period();

-- BR-208: reconciliation may change status, never the original cash figures or explanation.
CREATE FUNCTION gcmb.protect_submitted_cash_closing() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Cash closings are retained'; END IF;
  IF OLD.status IN ('SUBMITTED','RECONCILED') AND
    (to_jsonb(NEW)-'status') IS DISTINCT FROM (to_jsonb(OLD)-'status') THEN
    RAISE EXCEPTION 'Submitted cash closing figures are immutable';
  END IF;
  IF OLD.status='RECONCILED' AND NEW.status<>OLD.status THEN
    RAISE EXCEPTION 'Reconciled cash closing is immutable';
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER cash_closing_history BEFORE UPDATE OR DELETE ON gcmb.daily_cash_closing
FOR EACH ROW EXECUTE FUNCTION gcmb.protect_submitted_cash_closing();
