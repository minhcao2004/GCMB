ALTER TABLE gcmb.financial_period ADD COLUMN close_note varchar(1000) NOT NULL DEFAULT '';

COMMENT ON COLUMN gcmb.financial_period.close_note IS 'Ghi chú khi kế toán khóa kỳ.';
