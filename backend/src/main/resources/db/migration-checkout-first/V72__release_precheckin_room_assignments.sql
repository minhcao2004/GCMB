-- BR-03, BR-04: generated reservations hold a room type; choose the physical room at check-in.
-- Limit this repair to legacy seed codes (BK-yymmdd-sequence); preserve allocation history.
WITH latest_reserved AS (
  SELECT DISTINCT ON (a.booking_id) a.booking_id, r.room_type_id
  FROM gcmb.room_allocation a
  JOIN gcmb.room r ON r.id = a.room_id
  WHERE a.state = 'RESERVED' AND a.actual_start IS NULL
  ORDER BY a.booking_id, a.created_at DESC, a.id DESC
)
UPDATE gcmb.booking b
SET room_type_id = latest_reserved.room_type_id
FROM latest_reserved
WHERE b.id = latest_reserved.booking_id
  AND b.status = 'CONFIRMED' AND b.actual_checkin IS NULL AND b.room_type_id IS NULL
  AND b.code ~ '^BK-[0-9]{6}-[0-9]{4}$';

UPDATE gcmb.booking_charge c
SET allocation_id = NULL
FROM gcmb.room_allocation a
JOIN gcmb.booking b ON b.id = a.booking_id
WHERE c.allocation_id = a.id AND c.booking_id = b.id AND c.status = 'ACTIVE'
  AND b.status = 'CONFIRMED' AND b.actual_checkin IS NULL AND b.room_type_id IS NOT NULL
  AND b.code ~ '^BK-[0-9]{6}-[0-9]{4}$'
  AND a.state = 'RESERVED' AND a.actual_start IS NULL;

UPDATE gcmb.room_allocation a
SET state = 'RELEASED', change_reason = COALESCE(NULLIF(a.change_reason, ''), 'Chọn phòng cụ thể khi check-in')
FROM gcmb.booking b
WHERE a.booking_id = b.id
  AND b.status = 'CONFIRMED' AND b.actual_checkin IS NULL AND b.room_type_id IS NOT NULL
  AND b.code ~ '^BK-[0-9]{6}-[0-9]{4}$'
  AND a.state = 'RESERVED' AND a.actual_start IS NULL;
