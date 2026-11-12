ALTER TABLE gcmb.bank_transaction ADD COLUMN exception_reason varchar(40);

COMMENT ON COLUMN gcmb.bank_transaction.exception_reason IS 'Lý do giao dịch SePay được ghi nhận ở trạng thái ngoại lệ.';
