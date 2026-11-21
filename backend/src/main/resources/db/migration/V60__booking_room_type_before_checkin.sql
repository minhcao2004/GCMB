-- A reservation holds a room category; the physical room is assigned at check-in.
ALTER TABLE gcmb.booking ADD COLUMN room_type_id uuid REFERENCES gcmb.room_type(id);

UPDATE gcmb.booking b
SET room_type_id = r.room_type_id
FROM gcmb.room_allocation a JOIN gcmb.room r ON r.id = a.room_id
WHERE a.booking_id = b.id AND b.room_type_id IS NULL
  AND a.id = (SELECT first_a.id FROM gcmb.room_allocation first_a
              WHERE first_a.booking_id = b.id ORDER BY first_a.created_at, first_a.id LIMIT 1);

CREATE INDEX booking_unassigned_room_type_period_idx
  ON gcmb.booking(branch_id, room_type_id, planned_start, planned_end)
  WHERE status = 'CONFIRMED';
