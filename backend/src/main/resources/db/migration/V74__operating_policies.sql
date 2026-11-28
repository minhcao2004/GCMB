CREATE TABLE gcmb.operating_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_key VARCHAR(40) NOT NULL CHECK (policy_key IN ('NIGHT_SHIFT_PREMIUM','PAYMENT_APPROVAL_LIMIT')),
    amount NUMERIC(14,0) NOT NULL CHECK (amount >= 0),
    effective_from DATE NOT NULL,
    changed_by UUID REFERENCES gcmb.user_account(id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    reason VARCHAR(500) NOT NULL
);

CREATE INDEX operating_policy_effective ON gcmb.operating_policy(policy_key,effective_from DESC,changed_at DESC);

INSERT INTO gcmb.operating_policy(policy_key,amount,effective_from,reason) VALUES
    ('NIGHT_SHIFT_PREMIUM',3000,DATE '1900-01-01','Mức mặc định trước khi cấu hình.'),
    ('PAYMENT_APPROVAL_LIMIT',3000000,DATE '1900-01-01','Mức mặc định trước khi cấu hình.');

ALTER TABLE gcmb.finance_expense_request ADD COLUMN approval_threshold_amount NUMERIC(14,0);
