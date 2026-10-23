-- Match availability/create checks: completed allocations are history, not room holds.
-- Keep their original planned/actual timestamps and the per-booking history constraint.
ALTER TABLE gcmb.room_allocation DROP CONSTRAINT no_room_overlap;

ALTER TABLE gcmb.room_allocation ADD CONSTRAINT no_room_overlap
    EXCLUDE USING gist (room_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)
    WHERE (state IN ('RESERVED', 'OCCUPIED'))
    DEFERRABLE INITIALLY IMMEDIATE;
