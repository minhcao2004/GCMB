CREATE TABLE gcmb.salary_payment_batch (
    id uuid PRIMARY KEY,
    payroll_period_id uuid NOT NULL REFERENCES gcmb.payroll_period(id),
    branch_id uuid NOT NULL REFERENCES gcmb.branch(id),
    payment_date date NOT NULL,
    bank_name varchar(200) NOT NULL,
    external_reference varchar(200) NOT NULL,
    created_by uuid NOT NULL REFERENCES gcmb.user_account(id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE gcmb.salary_payment
    ADD COLUMN batch_id uuid REFERENCES gcmb.salary_payment_batch(id);

CREATE INDEX ix_salary_payment_batch_id ON gcmb.salary_payment(batch_id) WHERE batch_id IS NOT NULL;
CREATE INDEX ix_salary_payment_history_date ON gcmb.salary_payment ((COALESCE(paid_at, created_at)));
