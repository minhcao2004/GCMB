-- Author: HuyDV. Ca trước kiểm đếm và giao trước khi ca sau được nhận; không tạo thu/chi.
-- Yêu cầu bổ sung của người dùng, chưa có mã BR riêng; giữ nguyên lịch sử nhận từ V54.
CREATE TABLE gcmb.shift_cash_dispatch (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL REFERENCES gcmb.branch(id),
  business_date date NOT NULL,
  shift_number smallint NOT NULL CHECK (shift_number BETWEEN 1 AND 4),
  expected_amount numeric(18,0) NOT NULL,
  actual_amount numeric(18,0) NOT NULL CHECK (actual_amount>=0),
  variance numeric(18,0) GENERATED ALWAYS AS (actual_amount-expected_amount) STORED,
  source_revision uuid NOT NULL,
  source_snapshot jsonb NOT NULL,
  note text NOT NULL DEFAULT '' CHECK (length(note)<=2000),
  status varchar(20) NOT NULL CHECK (status IN ('MATCHED','DISCREPANCY','RESOLVED')),
  handed_by uuid NOT NULL REFERENCES gcmb.user_account(id),
  handed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  resolved_by uuid REFERENCES gcmb.user_account(id),
  resolved_at timestamptz,
  resolution text NOT NULL DEFAULT '' CHECK (length(resolution)<=2000),
  UNIQUE (branch_id,business_date,shift_number),
  CHECK ((actual_amount=expected_amount AND status='MATCHED') OR
    (actual_amount<>expected_amount AND status IN ('DISCREPANCY','RESOLVED') AND length(trim(note))>0)),
  CHECK ((status='RESOLVED' AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL AND length(trim(resolution))>0)
    OR (status<>'RESOLVED' AND resolved_by IS NULL AND resolved_at IS NULL AND resolution=''))
);
CREATE INDEX shift_cash_dispatch_pending ON gcmb.shift_cash_dispatch(branch_id,business_date)
  WHERE status='DISCREPANCY';
CREATE TRIGGER retain_shift_cash_dispatch BEFORE UPDATE OR DELETE ON gcmb.shift_cash_dispatch
  FOR EACH ROW EXECUTE FUNCTION gcmb.protect_shift_cash_handover();
