-- Historical no-show records become cancelled bookings; keep events and deposit ledger for audit.
UPDATE gcmb.booking b
SET status = 'CANCELLED',
    cancelled_at = COALESCE(b.cancelled_at, (
      SELECT e.created_at FROM gcmb.booking_event e
      WHERE e.booking_id = b.id AND e.event_type = 'BOOKING_NO_SHOW'
      ORDER BY e.created_at DESC, e.id DESC LIMIT 1
    ), b.planned_start),
    cancellation_reason = COALESCE(NULLIF(b.cancellation_reason, ''), (
      SELECT NULLIF(e.reason, '') FROM gcmb.booking_event e
      WHERE e.booking_id = b.id AND e.event_type = 'BOOKING_NO_SHOW'
      ORDER BY e.created_at DESC, e.id DESC LIMIT 1
    ), 'Khách không đến')
WHERE b.status = 'NO_SHOW';

ALTER TABLE gcmb.booking DROP CONSTRAINT booking_status_check;
ALTER TABLE gcmb.booking ADD CONSTRAINT booking_status_check
  CHECK (status IN ('DRAFT', 'CONFIRMED', 'IN_HOUSE', 'COMPLETED', 'CANCELLED'));

COMMENT ON COLUMN gcmb.booking.status IS 'DRAFT/CONFIRMED/IN_HOUSE/COMPLETED/CANCELLED';
COMMENT ON COLUMN gcmb.booking.channel IS 'Nguồn khách: FACEBOOK, TIKTOK, ZALO, INSTAGRAM, WALK_IN; PHONE và OTHER chỉ giữ cho dữ liệu cũ.';
