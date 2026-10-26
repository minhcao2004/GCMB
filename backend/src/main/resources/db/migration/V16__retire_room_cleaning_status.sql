-- Checkout now makes a room available immediately; keep historical status records.
UPDATE gcmb.room
SET operating_status = 'AVAILABLE',
    status_reason = 'Bỏ bước dọn phòng sau checkout',
    expected_ready_at = NULL
WHERE operating_status = 'CLEANING';

-- Retain the old values in room_operating_status_history for historical lookup.
ALTER TABLE gcmb.room
    ADD CONSTRAINT room_operating_status_without_cleaning_check
    CHECK (operating_status IN ('AVAILABLE', 'UNDER_MAINTENANCE', 'INACTIVE'));

COMMENT ON COLUMN gcmb.room.operating_status IS 'AVAILABLE/UNDER_MAINTENANCE/INACTIVE';
