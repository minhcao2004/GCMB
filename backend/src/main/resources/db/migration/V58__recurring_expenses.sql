CREATE TABLE gcmb.finance_recurring_expense (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  branch_id UUID NOT NULL REFERENCES gcmb.branch(id),
  category_id UUID NOT NULL REFERENCES gcmb.finance_expense_category(id),
  beneficiary_name VARCHAR(160) NOT NULL CHECK (length(btrim(beneficiary_name)) > 0),
  bank_account VARCHAR(80) NOT NULL CHECK (length(btrim(bank_account)) > 0),
  bank_name VARCHAR(120) NOT NULL CHECK (length(btrim(bank_name)) > 0),
  due_day SMALLINT NOT NULL CHECK (due_day BETWEEN 1 AND 31),
  monthly_amount NUMERIC DEFAULT NULL CHECK (monthly_amount IS NULL OR (monthly_amount > 0 AND monthly_amount=trunc(monthly_amount))),
  expected_amount NUMERIC DEFAULT NULL CHECK (expected_amount IS NULL OR (expected_amount > 0 AND expected_amount=trunc(expected_amount))),
  expense_belongs_to VARCHAR(16) NOT NULL CHECK (expense_belongs_to IN ('SAME_MONTH','PREVIOUS_MONTH')),
  start_month DATE NOT NULL CHECK (start_month=date_trunc('month',start_month)::date),
  end_month DATE DEFAULT NULL CHECK (end_month IS NULL OR end_month=date_trunc('month',end_month)::date),
  reminder_days SMALLINT NOT NULL DEFAULT 3 CHECK (reminder_days BETWEEN 0 AND 31),
  status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','TERMINATED')),
  created_by UUID NOT NULL REFERENCES gcmb.user_account(id),
  updated_by UUID NOT NULL REFERENCES gcmb.user_account(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  terminated_at TIMESTAMPTZ,
  CONSTRAINT finance_recurring_expense_month_range_check CHECK (end_month IS NULL OR end_month >= start_month),
  CONSTRAINT finance_recurring_expense_termination_check CHECK (
    (status='ACTIVE' AND terminated_at IS NULL) OR (status='TERMINATED' AND terminated_at IS NOT NULL)
  )
);

CREATE UNIQUE INDEX finance_recurring_expense_active_identity
  ON gcmb.finance_recurring_expense(branch_id,category_id,lower(btrim(beneficiary_name)))
  WHERE status='ACTIVE';

CREATE INDEX finance_recurring_expense_branch_status
  ON gcmb.finance_recurring_expense(branch_id,status,category_id);

CREATE TABLE gcmb.finance_recurring_expense_period (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  recurring_id UUID NOT NULL REFERENCES gcmb.finance_recurring_expense(id),
  period_month DATE NOT NULL CHECK (period_month=date_trunc('month',period_month)::date),
  due_date DATE NOT NULL,
  branch_id UUID NOT NULL REFERENCES gcmb.branch(id),
  category_id UUID NOT NULL REFERENCES gcmb.finance_expense_category(id),
  beneficiary_name VARCHAR(160) NOT NULL,
  bank_account VARCHAR(80) NOT NULL,
  bank_name VARCHAR(120) NOT NULL,
  amount NUMERIC DEFAULT NULL CHECK (amount IS NULL OR (amount > 0 AND amount=trunc(amount))),
  default_amount NUMERIC DEFAULT NULL CHECK (default_amount IS NULL OR (default_amount > 0 AND default_amount=trunc(default_amount))),
  expected_amount NUMERIC DEFAULT NULL CHECK (expected_amount IS NULL OR (expected_amount > 0 AND expected_amount=trunc(expected_amount))),
  reminder_days SMALLINT NOT NULL CHECK (reminder_days BETWEEN 0 AND 31),
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID','SKIPPED')),
  request_id UUID UNIQUE REFERENCES gcmb.finance_expense_request(id),
  skip_reason VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT finance_recurring_expense_period_unique UNIQUE (recurring_id,period_month),
  CONSTRAINT finance_recurring_expense_period_state_check CHECK (
    (status='PENDING' AND request_id IS NULL AND skip_reason IS NULL)
    OR (status='PAID' AND request_id IS NOT NULL AND amount IS NOT NULL AND skip_reason IS NULL)
    OR (status='SKIPPED' AND request_id IS NULL AND skip_reason IS NOT NULL AND length(btrim(skip_reason)) > 0)
  )
);

CREATE INDEX finance_recurring_expense_period_branch_due
  ON gcmb.finance_recurring_expense_period(branch_id,due_date,status);

CREATE INDEX finance_recurring_expense_period_month_status
  ON gcmb.finance_recurring_expense_period(period_month,status,recurring_id);

CREATE TABLE gcmb.finance_recurring_expense_notice (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  period_id UUID NOT NULL REFERENCES gcmb.finance_recurring_expense_period(id),
  recipient_id UUID NOT NULL REFERENCES gcmb.user_account(id),
  reminder_type VARCHAR(16) NOT NULL CHECK (reminder_type IN ('BEFORE_DUE','DUE','OVERDUE')),
  reminder_date DATE NOT NULL,
  message VARCHAR(500) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT finance_recurring_expense_notice_unique UNIQUE (period_id,recipient_id,reminder_type,reminder_date)
);

CREATE INDEX finance_recurring_expense_notice_recipient
  ON gcmb.finance_recurring_expense_notice(recipient_id,created_at DESC);

-- BR-103: recurring expenses follow the template's recognition month, while cash payment stays on the paid request.
CREATE OR REPLACE VIEW gcmb.v_expense_recognition AS
  SELECT r.id AS request_id,r.branch_id,r.category_id,r.category_type,
    r.recognition_mode,date_trunc('month',r.expense_on)::date AS period_month,
    NULL::INTEGER AS seq,r.amount::NUMERIC AS amount
  FROM gcmb.finance_expense_request r
  WHERE r.status='PAID' AND r.recognition_mode='IMMEDIATE'
    AND NOT EXISTS (
      SELECT 1 FROM gcmb.finance_recurring_expense_period p
      WHERE p.request_id=r.id AND p.status='PAID'
    )
  UNION ALL
  SELECT r.id AS request_id,p.branch_id,p.category_id,r.category_type,
    r.recognition_mode,p.period_month,NULL::INTEGER AS seq,r.amount::NUMERIC AS amount
  FROM gcmb.finance_recurring_expense_period p
  JOIN gcmb.finance_expense_request r ON r.id=p.request_id
  WHERE p.status='PAID' AND r.status='PAID' AND r.recognition_mode='IMMEDIATE'
  UNION ALL
  SELECT r.id AS request_id,a.branch_id,a.category_id,r.category_type,
    r.recognition_mode,a.period_month,a.seq,a.amount::NUMERIC
  FROM gcmb.finance_expense_allocation a
  JOIN gcmb.finance_expense_request r ON r.id=a.request_id
  WHERE r.status='PAID' AND r.recognition_mode='ALLOCATED' AND a.status='SCHEDULED';
