ALTER TABLE gcmb.booking
  ADD COLUMN guest_count integer NOT NULL DEFAULT 1 CHECK (guest_count > 0);

COMMENT ON COLUMN gcmb.booking.guest_count IS 'Số khách trong booking; phải nằm trong sức chứa phòng được gán.';
