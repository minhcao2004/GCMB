ALTER TABLE gcmb.finance_attachment
  DROP CONSTRAINT finance_attachment_owner_type_check,
  DROP CONSTRAINT finance_attachment_kind_check;

ALTER TABLE gcmb.finance_attachment
  ADD CONSTRAINT finance_attachment_owner_type_check
    CHECK (owner_type IN ('FUND_TRANSFER','EXPENSE_REQUEST','FINANCIAL_TRANSACTION')),
  ADD CONSTRAINT finance_attachment_kind_check
    CHECK (kind IN ('TRANSFER_PROOF','INVOICE','PAYMENT_PROOF','ADJUSTMENT_EVIDENCE'));

CREATE INDEX finance_attachment_transaction_history
  ON gcmb.finance_attachment(owner_id,created_at,id)
  WHERE owner_type='FINANCIAL_TRANSACTION';
