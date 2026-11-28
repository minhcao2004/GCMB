-- Disable the local receptionist cash handover fixture after testing.
-- Historical dispatch/receipt records are intentionally retained by V54/V56.
-- Run before closing the attendance month, which freezes shift assignments.
BEGIN;
SET LOCAL search_path TO gcmb, public;

UPDATE gcmb.shift_assignment
SET status = 'CANCELLED', cancelled_at = clock_timestamp(),
    reason = 'DEMO-CASH: kết thúc kiểm thử bàn giao ca'
WHERE id IN ('ca5a0000-0000-4000-8000-000000000021',
             'ca5a0000-0000-4000-8000-000000000022')
  AND status = 'CONFIRMED';

-- V26 revokes the linked login when the demo employee becomes inactive.
UPDATE gcmb.employee
SET status = 'INACTIVE'
WHERE id = 'ca5a0000-0000-4000-8000-000000000002'
  AND code = 'DEMO-CASH-RECEIVER' AND status = 'ACTIVE';

COMMIT;
