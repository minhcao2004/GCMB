-- Author: HuyDV
-- Created: 27/10/2026
-- UC-83..86: detect stale capacity/publication/replacement confirmations.
ALTER TABLE gcmb.scheduled_shift ADD COLUMN version bigint NOT NULL DEFAULT 0;
CREATE INDEX ix_shift_branch_week ON gcmb.scheduled_shift(branch_id,work_date);
