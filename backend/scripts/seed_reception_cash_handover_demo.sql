-- Local development fixture for receptionist cash handover (UC-85/86).
-- Run after Flyway migrations with: psql -v ON_ERROR_STOP=1 -f backend/scripts/seed_reception_cash_handover_demo.sql
-- Uses yesterday in the Hoa Bang branch so both shifts have ended.
-- Cash expected by the screen comes from existing drawer movements (BR-95); this fixture does not invent ledger entries.
-- The new account uses the current password of letan.hoabang. Change it through the app if this is a shared database.
-- Do not run against production. Once a handover is confirmed, its history is retained by the database.
BEGIN;
SET LOCAL search_path TO gcmb, public;

DO $$
DECLARE
  b gcmb.branch%ROWTYPE;
  outgoing gcmb.user_account%ROWTYPE;
  manager_id uuid;
  outgoing_employee uuid;
  demo_day date;
  demo_user constant uuid := 'ca5a0000-0000-4000-8000-000000000001';
  demo_employee constant uuid := 'ca5a0000-0000-4000-8000-000000000002';
  first_shift uuid;
  second_shift uuid;
BEGIN
  SELECT * INTO STRICT b FROM gcmb.branch WHERE code = 'HN_HOA_BANG';
  -- BR-177: staff cannot change business data while the branch is suspended.
  IF b.status <> 'ACTIVE' THEN
    RAISE EXCEPTION 'Hoa Bang must be active for the receptionist handover demo';
  END IF;
  demo_day := (CURRENT_TIMESTAMP AT TIME ZONE b.timezone)::date - 1;
  SELECT u.* INTO STRICT outgoing FROM gcmb.user_account u
    JOIN gcmb.role r ON r.id = u.role_id
    WHERE u.username = 'letan.hoabang' AND r.code = 'RECEPTIONIST'
      AND u.branch_id = b.id AND u.status = 'ACTIVE';
  SELECT e.id INTO STRICT outgoing_employee FROM gcmb.employee e
    WHERE e.user_id = outgoing.id AND e.position = 'RECEPTIONIST' AND e.status = 'ACTIVE';
  SELECT u.id INTO STRICT manager_id FROM gcmb.user_account u
    JOIN gcmb.role r ON r.id = u.role_id
    WHERE u.username = 'qlcs.hoabang' AND r.code = 'BRANCH_MANAGER'
      AND u.branch_id = b.id AND u.status = 'ACTIVE';

  IF (SELECT count(*) FROM gcmb.financial_account
      WHERE branch_id = b.id AND kind = 'CASH_DRAWER' AND active) <> 1 THEN
    RAISE EXCEPTION 'Hoa Bang needs exactly one active cash drawer';
  END IF;
  IF EXISTS (SELECT 1 FROM gcmb.financial_period
      WHERE month = date_trunc('month', demo_day)::date AND status = 'CLOSED') THEN
    RAISE EXCEPTION 'The demo day belongs to a closed financial period';
  END IF;
  IF EXISTS (SELECT 1 FROM gcmb.shift_cash_dispatch
      WHERE branch_id = b.id AND business_date = demo_day AND shift_number = 1)
    OR EXISTS (SELECT 1 FROM gcmb.shift_cash_handover
      WHERE branch_id = b.id AND business_date = demo_day AND shift_number = 1) THEN
    RAISE EXCEPTION 'Shift 1 handover already exists for the demo day';
  END IF;
  IF gcmb.employee_month_closed(b.id, outgoing_employee, demo_day)
    OR gcmb.employee_month_closed(b.id, demo_employee, demo_day) THEN
    RAISE EXCEPTION 'Attendance for the demo day is already closed';
  END IF;
  IF EXISTS (SELECT 1 FROM gcmb.user_account
      WHERE (lower(username) = 'demo.cash.receiver' OR lower(email) = 'demo.cash.receiver@example.invalid')
        AND id <> demo_user)
    OR EXISTS (SELECT 1 FROM gcmb.employee
      WHERE code = 'DEMO-CASH-RECEIVER' AND id <> demo_employee) THEN
    RAISE EXCEPTION 'A demo username, email, or employee code is already in use';
  END IF;
  IF EXISTS (SELECT 1 FROM gcmb.user_account WHERE id = demo_user
      AND (username <> 'demo.cash.receiver' OR branch_id <> b.id OR status <> 'ACTIVE'))
    OR EXISTS (SELECT 1 FROM gcmb.employee WHERE id = demo_employee
      AND (code <> 'DEMO-CASH-RECEIVER' OR user_id <> demo_user OR status <> 'ACTIVE')) THEN
    RAISE EXCEPTION 'Demo IDs already belong to different records';
  END IF;

  INSERT INTO gcmb.user_account
    (id, username, email, password_hash, role_id, status, failed_login_count, password_changed_at, branch_id)
  VALUES (demo_user, 'demo.cash.receiver', 'demo.cash.receiver@example.invalid',
    outgoing.password_hash, outgoing.role_id, 'ACTIVE', 0, CURRENT_TIMESTAMP, b.id)
  ON CONFLICT (id) DO NOTHING;
  INSERT INTO gcmb.employee (id, code, user_id, full_name, hired_on, status, position)
  VALUES (demo_employee, 'DEMO-CASH-RECEIVER', demo_user,
    'Lễ tân demo ca nhận tiền', demo_day, 'ACTIVE', 'RECEPTIONIST')
  ON CONFLICT (id) DO NOTHING;
  INSERT INTO gcmb.employee_assignment
    (id, employee_id, branch_id, valid_from, is_branch_manager, assigned_by, reason)
  VALUES ('ca5a0000-0000-4000-8000-000000000003', demo_employee, b.id,
    (demo_day::timestamp AT TIME ZONE b.timezone), false, manager_id,
    'DEMO-CASH: phân công tạm cho kiểm thử bàn giao ca')
  ON CONFLICT (id) DO NOTHING;

  -- UC-85/86: the outgoing receptionist must be confirmed on shift 1 (00:00-07:00),
  -- and the incoming receptionist on shift 2 (07:00-12:00) of the same business day.
  INSERT INTO gcmb.scheduled_shift
    (id, branch_id, template_id, work_date, starts_at, ends_at, capacity, status, created_by)
  SELECT 'ca5a0000-0000-4000-8000-000000000011', b.id, t.id, demo_day,
    (demo_day::timestamp AT TIME ZONE b.timezone),
    ((demo_day::timestamp + interval '7 hours') AT TIME ZONE b.timezone),
    1, 'OPEN', manager_id
  FROM gcmb.shift_template t WHERE t.start_minute = 0 AND t.end_minute = 420
  ON CONFLICT (branch_id, template_id, work_date) DO NOTHING;
  INSERT INTO gcmb.scheduled_shift
    (id, branch_id, template_id, work_date, starts_at, ends_at, capacity, status, created_by)
  SELECT 'ca5a0000-0000-4000-8000-000000000012', b.id, t.id, demo_day,
    ((demo_day::timestamp + interval '7 hours') AT TIME ZONE b.timezone),
    ((demo_day::timestamp + interval '12 hours') AT TIME ZONE b.timezone),
    1, 'OPEN', manager_id
  FROM gcmb.shift_template t WHERE t.start_minute = 420 AND t.end_minute = 720
  ON CONFLICT (branch_id, template_id, work_date) DO NOTHING;
  SELECT s.id INTO STRICT first_shift FROM gcmb.scheduled_shift s
    JOIN gcmb.shift_template t ON t.id = s.template_id
    WHERE s.branch_id = b.id AND s.work_date = demo_day
      AND t.start_minute = 0 AND t.end_minute = 420 AND s.status <> 'CANCELLED';
  SELECT s.id INTO STRICT second_shift FROM gcmb.scheduled_shift s
    JOIN gcmb.shift_template t ON t.id = s.template_id
    WHERE s.branch_id = b.id AND s.work_date = demo_day
      AND t.start_minute = 420 AND t.end_minute = 720 AND s.status <> 'CANCELLED';
  IF NOT EXISTS (SELECT 1 FROM gcmb.scheduled_shift s WHERE s.id = first_shift
      AND s.starts_at = (demo_day::timestamp AT TIME ZONE b.timezone)
      AND s.ends_at = ((demo_day::timestamp + interval '7 hours') AT TIME ZONE b.timezone))
    OR NOT EXISTS (SELECT 1 FROM gcmb.scheduled_shift s WHERE s.id = second_shift
      AND s.starts_at = ((demo_day::timestamp + interval '7 hours') AT TIME ZONE b.timezone)
      AND s.ends_at = ((demo_day::timestamp + interval '12 hours') AT TIME ZONE b.timezone)) THEN
    RAISE EXCEPTION 'Existing shifts do not match the cash handover time windows';
  END IF;
  IF EXISTS (SELECT 1 FROM gcmb.shift_assignment a
      WHERE a.shift_id = first_shift AND a.status = 'CONFIRMED' AND a.employee_id <> outgoing_employee)
    OR EXISTS (SELECT 1 FROM gcmb.shift_assignment a
      WHERE a.shift_id = second_shift AND a.status = 'CONFIRMED' AND a.employee_id <> demo_employee) THEN
    RAISE EXCEPTION 'An existing confirmed assignment conflicts with the demo shifts';
  END IF;
  INSERT INTO gcmb.shift_assignment (id, shift_id, employee_id, source, status, assigned_by)
  SELECT 'ca5a0000-0000-4000-8000-000000000021', first_shift, outgoing_employee,
    'MANAGER', 'CONFIRMED', manager_id
  WHERE NOT EXISTS (SELECT 1 FROM gcmb.shift_assignment
    WHERE shift_id = first_shift AND employee_id = outgoing_employee AND status = 'CONFIRMED')
  ON CONFLICT (id) DO NOTHING;
  INSERT INTO gcmb.shift_assignment (id, shift_id, employee_id, source, status, assigned_by)
  SELECT 'ca5a0000-0000-4000-8000-000000000022', second_shift, demo_employee,
    'MANAGER', 'CONFIRMED', manager_id
  WHERE NOT EXISTS (SELECT 1 FROM gcmb.shift_assignment
    WHERE shift_id = second_shift AND employee_id = demo_employee AND status = 'CONFIRMED')
  ON CONFLICT (id) DO NOTHING;
  IF NOT EXISTS (SELECT 1 FROM gcmb.shift_assignment
      WHERE shift_id = first_shift AND employee_id = outgoing_employee AND status = 'CONFIRMED')
    OR NOT EXISTS (SELECT 1 FROM gcmb.shift_assignment
      WHERE shift_id = second_shift AND employee_id = demo_employee AND status = 'CONFIRMED') THEN
    RAISE EXCEPTION 'The two confirmed receptionist assignments were not created';
  END IF;
  RAISE NOTICE 'DEMO-CASH ready: branch %, date %, outgoing %, incoming %',
    b.code, demo_day, outgoing.username, 'demo.cash.receiver';
END $$;
COMMIT;
