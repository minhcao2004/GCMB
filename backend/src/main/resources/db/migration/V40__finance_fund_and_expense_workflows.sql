CREATE TABLE gcmb.finance_fund_transfer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(40) NOT NULL UNIQUE,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    sent_on DATE NOT NULL,
    transfer_reference VARCHAR(200),
    sent_by UUID NOT NULL REFERENCES gcmb.user_account(id),
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_by UUID REFERENCES gcmb.user_account(id),
    received_at TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL CHECK (status IN ('WAITING_CONFIRMATION','RECEIVED')),
    CHECK ((status='WAITING_CONFIRMATION' AND received_by IS NULL AND received_at IS NULL)
        OR (status='RECEIVED' AND received_by IS NOT NULL AND received_at IS NOT NULL))
);

CREATE TABLE gcmb.finance_expense_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(500) NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES gcmb.user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE gcmb.finance_expense_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(40) NOT NULL UNIQUE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('PAYMENT','REIMBURSEMENT')),
    branch_id UUID NOT NULL REFERENCES gcmb.branch(id),
    category_id UUID NOT NULL REFERENCES gcmb.finance_expense_category(id),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    expense_on DATE NOT NULL,
    description VARCHAR(1000) NOT NULL,
    beneficiary_name VARCHAR(160) NOT NULL,
    bank_account VARCHAR(80) NOT NULL,
    bank_name VARCHAR(120) NOT NULL,
    requested_by UUID NOT NULL REFERENCES gcmb.user_account(id),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL CHECK (status IN ('WAITING_ACCOUNTANT','WAITING_HEAD_OFFICE','APPROVED_TO_PAY','REJECTED','PAID')),
    accountant_reviewed_by UUID REFERENCES gcmb.user_account(id),
    accountant_reviewed_at TIMESTAMPTZ,
    head_office_reviewed_by UUID REFERENCES gcmb.user_account(id),
    head_office_reviewed_at TIMESTAMPTZ,
    paid_by UUID REFERENCES gcmb.user_account(id),
    paid_at TIMESTAMPTZ,
    paid_reference VARCHAR(200),
    CHECK ((status='PAID' AND paid_by IS NOT NULL AND paid_at IS NOT NULL)
        OR (status<>'PAID' AND paid_by IS NULL AND paid_at IS NULL))
);

CREATE TABLE gcmb.finance_attachment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_type VARCHAR(24) NOT NULL CHECK (owner_type IN ('FUND_TRANSFER','EXPENSE_REQUEST')),
    owner_id UUID NOT NULL,
    kind VARCHAR(24) NOT NULL CHECK (kind IN ('TRANSFER_PROOF','INVOICE','PAYMENT_PROOF')),
    object_key VARCHAR(260) NOT NULL UNIQUE,
    original_name VARCHAR(200) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size > 0),
    sha256 CHAR(64) NOT NULL,
    created_by UUID NOT NULL REFERENCES gcmb.user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE gcmb.finance_request_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES gcmb.finance_expense_request(id),
    actor_id UUID NOT NULL REFERENCES gcmb.user_account(id),
    action VARCHAR(32) NOT NULL,
    note VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX finance_request_branch_history ON gcmb.finance_expense_request(branch_id, requested_at DESC);
CREATE INDEX finance_request_status_queue ON gcmb.finance_expense_request(status, requested_at);
CREATE INDEX finance_history_request ON gcmb.finance_request_history(request_id, created_at);
