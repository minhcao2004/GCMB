ALTER TABLE gcmb.payment
  ADD COLUMN receipt_reference varchar(120);

-- Keep webhook-matched transfers distinct from receipts confirmed manually by reception.
DO $$
DECLARE constraint_name text;
BEGIN
  FOR constraint_name IN
    SELECT conname
    FROM pg_constraint
    WHERE conrelid = 'gcmb.payment'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%method%'
  LOOP
    EXECUTE format('ALTER TABLE gcmb.payment DROP CONSTRAINT %I', constraint_name);
  END LOOP;
END $$;

ALTER TABLE gcmb.payment
  ADD CONSTRAINT payment_method_fields_check CHECK (
    (method = 'BANK_TRANSFER' AND request_id IS NOT NULL AND bank_transaction_id IS NOT NULL
      AND cash_tendered IS NULL AND cash_change IS NULL AND receipt_reference IS NULL)
    OR (method = 'CASH' AND request_id IS NULL AND bank_transaction_id IS NULL
      AND cash_tendered IS NOT NULL AND cash_change IS NOT NULL
      AND cash_tendered >= amount AND cash_change = cash_tendered - amount)
    OR (method IN ('MANUAL_TRANSFER','CARD','QR') AND request_id IS NULL AND bank_transaction_id IS NULL
      AND cash_tendered IS NULL AND cash_change IS NULL
      AND received_by IS NOT NULL
      AND (receipt_reference IS NULL OR NULLIF(BTRIM(receipt_reference), '') IS NOT NULL))
  );

ALTER TABLE gcmb.payment
  ADD CONSTRAINT payment_purpose_method_check CHECK (
    (purpose = 'DEPOSIT' AND method IN ('BANK_TRANSFER','MANUAL_TRANSFER','CASH','CARD','QR'))
    OR (purpose = 'BILL' AND method IN ('BANK_TRANSFER','CASH'))
  );

COMMENT ON COLUMN gcmb.payment.receipt_reference IS
  'Mã giao dịch/chứng từ do lễ tân nhập khi xác nhận khoản thu tại quầy.';
