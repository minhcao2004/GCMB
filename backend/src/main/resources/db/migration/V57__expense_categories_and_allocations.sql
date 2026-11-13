ALTER TABLE gcmb.finance_expense_category
  ADD COLUMN category_group VARCHAR(20) NOT NULL DEFAULT 'OTHER'
    CHECK (category_group IN ('RENT','UTILITIES','INGREDIENTS','MAINTENANCE','MARKETING','EQUIPMENT','OTHER')),
  ADD COLUMN cost_nature VARCHAR(10) NOT NULL DEFAULT 'VARIABLE'
    CHECK (cost_nature IN ('FIXED','VARIABLE')),
  ADD COLUMN recognition_mode VARCHAR(12) NOT NULL DEFAULT 'IMMEDIATE'
    CHECK (recognition_mode IN ('IMMEDIATE','ALLOCATED')),
  ADD COLUMN default_allocation_months INTEGER DEFAULT NULL,
  ADD COLUMN invoice_required BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN reimbursement_allowed BOOLEAN NOT NULL DEFAULT TRUE,
  ADD CONSTRAINT finance_expense_category_allocation_months_check CHECK (
    (recognition_mode='IMMEDIATE' AND default_allocation_months IS NULL)
    OR (recognition_mode='ALLOCATED' AND default_allocation_months BETWEEN 2 AND 60)
  );

ALTER TABLE gcmb.finance_expense_request
  ADD COLUMN recognition_mode VARCHAR(12) NOT NULL DEFAULT 'IMMEDIATE'
    CHECK (recognition_mode IN ('IMMEDIATE','ALLOCATED')),
  ADD COLUMN allocation_months INTEGER DEFAULT NULL,
  ADD COLUMN allocation_start DATE DEFAULT NULL,
  ADD COLUMN origin VARCHAR(24) NOT NULL DEFAULT 'BRANCH_MANAGER'
    CHECK (origin IN ('BRANCH_MANAGER','ACCOUNTANT_DIRECT')),
  ADD COLUMN idempotency_key UUID DEFAULT NULL,
  ADD CONSTRAINT finance_expense_request_allocation_check CHECK (
    (recognition_mode='IMMEDIATE' AND allocation_months IS NULL AND allocation_start IS NULL)
    OR (recognition_mode='ALLOCATED' AND allocation_months BETWEEN 2 AND 60
        AND (allocation_start IS NULL OR EXTRACT(DAY FROM allocation_start)=1))
  ),
  ADD CONSTRAINT finance_expense_request_allocation_paid_check CHECK (
    status <> 'PAID' OR recognition_mode <> 'ALLOCATED' OR allocation_start IS NOT NULL
  ),
  ADD CONSTRAINT finance_expense_request_allocation_integer_check CHECK (
    recognition_mode <> 'ALLOCATED' OR amount=trunc(amount)
  );

CREATE UNIQUE INDEX finance_expense_request_direct_idempotency
  ON gcmb.finance_expense_request(idempotency_key)
  WHERE origin='ACCOUNTANT_DIRECT' AND idempotency_key IS NOT NULL;

CREATE TABLE gcmb.finance_expense_allocation (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id UUID NOT NULL REFERENCES gcmb.finance_expense_request(id),
  branch_id UUID NOT NULL REFERENCES gcmb.branch(id),
  category_id UUID NOT NULL REFERENCES gcmb.finance_expense_category(id),
  period_month DATE NOT NULL CHECK (period_month=date_trunc('month',period_month)::date),
  seq INTEGER NOT NULL CHECK (seq BETWEEN 1 AND 60),
  amount BIGINT NOT NULL CHECK (amount > 0),
  status VARCHAR(12) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED','CANCELLED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (request_id,period_month),
  UNIQUE (request_id,seq)
);

CREATE INDEX finance_expense_allocation_branch_period
  ON gcmb.finance_expense_allocation(branch_id,period_month,status);

CREATE OR REPLACE VIEW gcmb.v_expense_recognition AS
  SELECT r.id AS request_id,r.branch_id,r.category_id,r.category_type,
    r.recognition_mode,date_trunc('month',r.expense_on)::date AS period_month,
    NULL::INTEGER AS seq,r.amount::NUMERIC AS amount
  FROM gcmb.finance_expense_request r
  WHERE r.status='PAID' AND r.recognition_mode='IMMEDIATE'
  UNION ALL
  SELECT r.id AS request_id,a.branch_id,a.category_id,r.category_type,
    r.recognition_mode,a.period_month,a.seq,a.amount::NUMERIC
  FROM gcmb.finance_expense_allocation a
  JOIN gcmb.finance_expense_request r ON r.id=a.request_id
  WHERE r.status='PAID' AND r.recognition_mode='ALLOCATED' AND a.status='SCHEDULED';
