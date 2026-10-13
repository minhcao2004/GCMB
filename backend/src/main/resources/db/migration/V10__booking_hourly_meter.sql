CREATE TABLE gcmb.booking_hourly_meter (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id uuid NOT NULL UNIQUE REFERENCES gcmb.booking(id) ON DELETE RESTRICT,
  allocation_id uuid NOT NULL REFERENCES gcmb.room_allocation(id) ON DELETE RESTRICT,
  price_rule_id uuid NOT NULL REFERENCES gcmb.price_rule(id) ON DELETE RESTRICT,
  started_at timestamptz NOT NULL,
  billed_from timestamptz NOT NULL,
  original_allocation_end timestamptz NOT NULL,
  reserved_until timestamptz NOT NULL,
  hourly_price numeric(18,0) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'RUNNING',
  stopped_at timestamptz,
  billed_minutes integer,
  amount numeric(18,0),
  started_by uuid NOT NULL REFERENCES gcmb.user_account(id) ON DELETE RESTRICT,
  stopped_by uuid REFERENCES gcmb.user_account(id) ON DELETE RESTRICT,
  CHECK (status IN ('RUNNING','STOPPED')),
  CHECK (reserved_until > billed_from AND hourly_price >= 0),
  CHECK ((status='RUNNING' AND stopped_at IS NULL AND billed_minutes IS NULL AND amount IS NULL AND stopped_by IS NULL)
      OR (status='STOPPED' AND stopped_at IS NOT NULL AND billed_minutes IS NOT NULL AND billed_minutes >= 0
          AND amount IS NOT NULL AND amount >= 0 AND stopped_by IS NOT NULL))
);

COMMENT ON TABLE gcmb.booking_hourly_meter IS 'Phiên gia hạn giờ lẻ được lưu trên máy chủ; chốt số phút và phụ thu khi dừng đồng hồ.';
