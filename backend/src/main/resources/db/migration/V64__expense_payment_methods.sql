ALTER TABLE gcmb.finance_expense_request
    ADD COLUMN payment_method VARCHAR(8),
    ADD COLUMN paid_payment_method VARCHAR(8),
    ADD CONSTRAINT finance_request_payment_method_check CHECK (payment_method IN ('CASH','BANK')),
    ADD CONSTRAINT finance_request_paid_payment_method_check CHECK (paid_payment_method IN ('CASH','BANK')),
    ADD CONSTRAINT finance_request_method_on_payment_check
        CHECK (paid_payment_method IS NULL OR status = 'PAID'),
    ADD CONSTRAINT finance_request_new_payment_method_check
        CHECK (status <> 'PAID' OR payment_method IS NULL OR paid_payment_method IS NOT NULL);

-- Historical payments have no reliable method to backfill; keep them unclassified.
