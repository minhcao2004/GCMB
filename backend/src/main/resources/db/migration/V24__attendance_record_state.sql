-- Author: HuyDV
-- Created: 27/10/2026
-- UC-87..89 / BR-193, BR-194, BR-204: explicit absence and stale-write protection.
ALTER TABLE gcmb.attendance
  ADD COLUMN status varchar(20) NOT NULL DEFAULT 'WORKED',
  ADD COLUMN version bigint NOT NULL DEFAULT 1;
UPDATE gcmb.attendance SET status='ABSENT'
WHERE actual_in IS NULL AND actual_out IS NULL AND worked_minutes=0;
ALTER TABLE gcmb.attendance ADD CONSTRAINT attendance_status_valid
  CHECK (status IN ('WORKED','ABSENT'));
ALTER TABLE gcmb.attendance_period ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- BR-63: JDBC connections need not have gcmb in search_path; keep the existing lock guard effective.
CREATE OR REPLACE FUNCTION gcmb.guard_attendance_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE pid uuid; s text;
BEGIN
  IF TG_OP <> 'INSERT' THEN
    SELECT status INTO s FROM gcmb.attendance_period WHERE id=OLD.period_id FOR SHARE;
    IF s IS DISTINCT FROM 'OPEN' THEN RAISE EXCEPTION 'Closed attendance cannot be edited'; END IF;
  END IF;
  IF TG_OP <> 'DELETE' THEN
    pid:=NEW.period_id;
    SELECT status INTO s FROM gcmb.attendance_period WHERE id=pid FOR SHARE;
    IF s IS DISTINCT FROM 'OPEN' THEN RAISE EXCEPTION 'Attendance period must be open'; END IF;
    RETURN NEW;
  END IF;
  RETURN OLD;
END; $$;

-- BR-62/63: a concurrent issue report/resolution must not change an already finalized sheet.
CREATE FUNCTION gcmb.guard_attendance_issue_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s text;
BEGIN
  IF TG_OP <> 'INSERT' THEN
    SELECT p.status INTO s FROM gcmb.attendance a JOIN gcmb.attendance_period p ON p.id=a.period_id
    WHERE a.id=OLD.attendance_id FOR SHARE OF p;
    IF s IS DISTINCT FROM 'OPEN' THEN RAISE EXCEPTION 'Closed attendance issues cannot be edited'; END IF;
  END IF;
  IF TG_OP <> 'DELETE' THEN
    SELECT p.status INTO s FROM gcmb.attendance a JOIN gcmb.attendance_period p ON p.id=a.period_id
    WHERE a.id=NEW.attendance_id FOR SHARE OF p;
    IF s IS DISTINCT FROM 'OPEN' THEN RAISE EXCEPTION 'Attendance issue period must be open'; END IF;
    RETURN NEW;
  END IF;
  RETURN OLD;
END; $$;
CREATE TRIGGER attendance_issue_lock BEFORE INSERT OR UPDATE OR DELETE ON gcmb.attendance_issue
FOR EACH ROW EXECUTE FUNCTION gcmb.guard_attendance_issue_change();
