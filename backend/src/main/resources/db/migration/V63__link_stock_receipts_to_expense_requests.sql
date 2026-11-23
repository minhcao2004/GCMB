CREATE TABLE gcmb.finance_request_stock_receipt (
    request_id uuid NOT NULL REFERENCES gcmb.finance_expense_request(id) ON DELETE RESTRICT,
    receipt_id uuid NOT NULL REFERENCES gcmb.stock_receipt(id) ON DELETE RESTRICT,
    amount numeric(14,2) NOT NULL CHECK (amount > 0),
    active boolean NOT NULL DEFAULT true,
    PRIMARY KEY (request_id, receipt_id)
);

CREATE UNIQUE INDEX finance_request_stock_receipt_active
    ON gcmb.finance_request_stock_receipt(receipt_id) WHERE active;
