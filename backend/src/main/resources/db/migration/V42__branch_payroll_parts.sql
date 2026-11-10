-- HuyDV | UC-91/92/95/97, BR-67/75/77: chốt từng cơ sở, trả một lần mỗi tháng.
CREATE TABLE gcmb.branch_payroll_submission (
    period_id uuid NOT NULL REFERENCES gcmb.payroll_period(id),
    employee_id uuid NOT NULL REFERENCES gcmb.employee(id),
    branch_id uuid NOT NULL REFERENCES gcmb.branch(id),
    minutes bigint NOT NULL CHECK (minutes >= 0),
    wage numeric(18,0) NOT NULL CHECK (wage >= 0),
    bonus numeric(18,0) NOT NULL CHECK (bonus >= 0),
    penalty numeric(18,0) NOT NULL CHECK (penalty >= 0),
    reason text NOT NULL,
    source_snapshot jsonb NOT NULL,
    submitted_by uuid NOT NULL REFERENCES gcmb.user_account(id),
    submitted_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (period_id, employee_id, branch_id)
);
