ALTER TABLE gcmb.finance_expense_category
  ADD COLUMN cost_type VARCHAR(5) NOT NULL DEFAULT 'OPEX'
    CHECK (cost_type IN ('OPEX', 'CAPEX'));

ALTER TABLE gcmb.finance_expense_request
  ADD COLUMN category_type VARCHAR(5) NOT NULL DEFAULT 'OPEX'
    CHECK (category_type IN ('OPEX', 'CAPEX'));

CREATE INDEX finance_request_category ON gcmb.finance_expense_request(category_id);
