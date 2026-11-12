ALTER TABLE gcmb.bank_transaction
  ADD COLUMN resolution_status varchar(20) NOT NULL DEFAULT 'UNRESOLVED'
    CHECK (resolution_status IN ('UNRESOLVED','RESOLVED')),
  ADD COLUMN resolved_at timestamptz,
  ADD COLUMN resolved_by uuid REFERENCES gcmb.user_account(id),
  ADD COLUMN resolution_note varchar(1000),
  ADD CONSTRAINT sepay_exception_resolution_complete CHECK (
    (resolution_status='UNRESOLVED' AND resolved_at IS NULL AND resolved_by IS NULL AND resolution_note IS NULL)
    OR (resolution_status='RESOLVED' AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL
        AND resolution_note IS NOT NULL AND length(trim(resolution_note)) > 0)
  );

COMMENT ON COLUMN gcmb.bank_transaction.resolution_status IS 'Trạng thái xử lý ngoại lệ: UNRESOLVED/RESOLVED.';
COMMENT ON COLUMN gcmb.bank_transaction.resolved_at IS 'Thời điểm kế toán đánh dấu ngoại lệ đã xử lý.';
COMMENT ON COLUMN gcmb.bank_transaction.resolved_by IS 'Kế toán đánh dấu ngoại lệ đã xử lý.';
COMMENT ON COLUMN gcmb.bank_transaction.resolution_note IS 'Ghi chú xử lý ngoại lệ SePay.';
