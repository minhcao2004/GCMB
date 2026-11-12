-- Author: HuyDV. UC-83..89 / BR-56/62/63/64.
-- Agreed extension: three people per shift; full-month closing per selected employee, including early closing.
CREATE TABLE gcmb.employee_attendance_closing (
  period_id uuid NOT NULL REFERENCES gcmb.attendance_period(id),
  employee_id uuid NOT NULL REFERENCES gcmb.employee(id),
  closed_by uuid NOT NULL REFERENCES gcmb.user_account(id),
  closed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  source_snapshot jsonb NOT NULL,
  PRIMARY KEY(period_id,employee_id)
);
CREATE FUNCTION gcmb.employee_month_closed(branch uuid,employee uuid,day date) RETURNS boolean
LANGUAGE sql STABLE AS $$
  SELECT EXISTS(SELECT 1 FROM gcmb.attendance_period p
    LEFT JOIN gcmb.employee_attendance_closing c ON c.period_id=p.id AND c.employee_id=employee
    WHERE p.branch_id=branch AND p.month=date_trunc('month',day)::date
      AND (p.status='CLOSED' OR c.employee_id IS NOT NULL));
$$;
CREATE FUNCTION gcmb.retain_employee_closing() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Closed employee attendance history is immutable'; END; $$;
CREATE TRIGGER retain_employee_closing BEFORE UPDATE OR DELETE ON gcmb.employee_attendance_closing
FOR EACH ROW EXECUTE FUNCTION gcmb.retain_employee_closing();

-- Keep past slots and finalized attendance intact; never remove existing participants.
UPDATE gcmb.scheduled_shift s SET capacity=greatest(3,
  (SELECT count(*)::integer FROM gcmb.shift_assignment a WHERE a.shift_id=s.id AND a.status='CONFIRMED')),version=version+1
WHERE s.ends_at>CURRENT_TIMESTAMP AND s.status<>'CANCELLED'
  AND NOT EXISTS(SELECT 1 FROM gcmb.attendance_period p WHERE p.branch_id=s.branch_id
    AND p.month=date_trunc('month',s.work_date)::date AND p.status='CLOSED');

CREATE OR REPLACE FUNCTION gcmb.guard_attendance_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r record; s text;
BEGIN
  PERFORM pg_advisory_xact_lock(1300261013);
  IF TG_OP<>'INSERT' THEN
    SELECT x.employee_id,t.branch_id,t.work_date INTO r FROM gcmb.shift_assignment x
      JOIN gcmb.scheduled_shift t ON t.id=x.shift_id WHERE x.id=OLD.assignment_id;
    SELECT status INTO s FROM gcmb.attendance_period WHERE id=OLD.period_id FOR SHARE;
    IF s IS DISTINCT FROM 'OPEN' OR gcmb.employee_month_closed(r.branch_id,r.employee_id,r.work_date)
      THEN RAISE EXCEPTION 'Closed employee attendance cannot be edited'; END IF;
  END IF;
  IF TG_OP<>'DELETE' THEN
    SELECT x.employee_id,t.branch_id,t.work_date INTO r FROM gcmb.shift_assignment x
      JOIN gcmb.scheduled_shift t ON t.id=x.shift_id WHERE x.id=NEW.assignment_id;
    SELECT status INTO s FROM gcmb.attendance_period WHERE id=NEW.period_id FOR SHARE;
    IF s IS DISTINCT FROM 'OPEN' OR gcmb.employee_month_closed(r.branch_id,r.employee_id,r.work_date)
      THEN RAISE EXCEPTION 'Employee attendance month is closed'; END IF;
    IF NOT EXISTS(SELECT 1 FROM gcmb.attendance_period p WHERE p.id=NEW.period_id AND p.branch_id=r.branch_id
      AND p.month=date_trunc('month',r.work_date)::date) THEN RAISE EXCEPTION 'Attendance period does not match shift'; END IF;
    RETURN NEW;
  END IF;
  RETURN OLD;
END; $$;

CREATE OR REPLACE FUNCTION gcmb.guard_attendance_issue_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r record;
BEGIN
  PERFORM pg_advisory_xact_lock(1300261013);
  IF TG_OP<>'INSERT' THEN
    SELECT x.employee_id,s.branch_id,s.work_date INTO r FROM gcmb.attendance a
      JOIN gcmb.shift_assignment x ON x.id=a.assignment_id JOIN gcmb.scheduled_shift s ON s.id=x.shift_id
      WHERE a.id=OLD.attendance_id;
    IF gcmb.employee_month_closed(r.branch_id,r.employee_id,r.work_date)
      THEN RAISE EXCEPTION 'Closed employee attendance issues cannot be edited'; END IF;
  END IF;
  IF TG_OP<>'DELETE' THEN
    SELECT x.employee_id,s.branch_id,s.work_date INTO r FROM gcmb.attendance a
      JOIN gcmb.shift_assignment x ON x.id=a.assignment_id JOIN gcmb.scheduled_shift s ON s.id=x.shift_id
      WHERE a.id=NEW.attendance_id;
    IF gcmb.employee_month_closed(r.branch_id,r.employee_id,r.work_date)
      THEN RAISE EXCEPTION 'Employee attendance month is closed'; END IF;
    RETURN NEW;
  END IF;
  RETURN OLD;
END; $$;

CREATE FUNCTION gcmb.guard_closed_employee_assignment() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s record;
BEGIN
  PERFORM pg_advisory_xact_lock(1300261013);
  IF TG_OP<>'INSERT' THEN
    SELECT branch_id,work_date INTO s FROM gcmb.scheduled_shift WHERE id=OLD.shift_id;
    IF gcmb.employee_month_closed(s.branch_id,OLD.employee_id,s.work_date)
      THEN RAISE EXCEPTION 'Closed employee assignments cannot change'; END IF;
  END IF;
  IF TG_OP<>'DELETE' THEN
    SELECT branch_id,work_date INTO s FROM gcmb.scheduled_shift WHERE id=NEW.shift_id;
    IF gcmb.employee_month_closed(s.branch_id,NEW.employee_id,s.work_date)
      THEN RAISE EXCEPTION 'Employee cannot receive new shifts in a closed month'; END IF;
    RETURN NEW;
  END IF;
  RETURN OLD;
END; $$;
CREATE TRIGGER closed_employee_assignment BEFORE INSERT OR UPDATE OR DELETE ON gcmb.shift_assignment
FOR EACH ROW EXECUTE FUNCTION gcmb.guard_closed_employee_assignment();

CREATE FUNCTION gcmb.guard_employee_closed_shift() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  PERFORM pg_advisory_xact_lock(1300261013);
  IF EXISTS(SELECT 1 FROM gcmb.shift_assignment x WHERE x.shift_id=OLD.id
    AND gcmb.employee_month_closed(OLD.branch_id,x.employee_id,OLD.work_date)) THEN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Shift has finalized attendance'; END IF;
    IF NEW.branch_id<>OLD.branch_id OR NEW.work_date<>OLD.work_date OR NEW.template_id<>OLD.template_id
      OR NEW.starts_at<>OLD.starts_at OR NEW.ends_at<>OLD.ends_at OR NEW.status='CANCELLED'
      THEN RAISE EXCEPTION 'Shift with finalized attendance is immutable'; END IF;
  END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER employee_closed_shift BEFORE UPDATE OR DELETE ON gcmb.scheduled_shift
FOR EACH ROW EXECUTE FUNCTION gcmb.guard_employee_closed_shift();
