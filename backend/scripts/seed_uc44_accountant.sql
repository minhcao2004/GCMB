-- Local UC44 accountant login for testing the deposit-refund flow.
-- Run after Flyway migrations and seed_uc34_37_demo.sql.
BEGIN;
SET LOCAL search_path TO gcmb, public;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM gcmb.branch WHERE id = '34370000-0000-4000-8000-000000000001'
  ) THEN
    RAISE EXCEPTION 'Run seed_uc34_37_demo.sql first to create the UC34-37 demo branch';
  END IF;

  IF EXISTS (
    SELECT 1 FROM gcmb.user_account
    WHERE (lower(username) = lower('uc44.accountant') OR lower(email) = lower('uc44.accountant@example.invalid'))
      AND id <> '44440000-0000-4000-8000-000000000001'
  ) THEN
    RAISE EXCEPTION 'The UC44 demo username or email is already used by another account';
  END IF;

  IF EXISTS (
    SELECT 1 FROM gcmb.employee
    WHERE code = 'DEMO-UC44-ACC'
      AND id <> '44440000-0000-4000-8000-000000000002'
  ) THEN
    RAISE EXCEPTION 'The UC44 demo employee code is already used';
  END IF;
END $$;

INSERT INTO gcmb.role (code, name)
VALUES ('ACCOUNTANT', 'Kế toán')
ON CONFLICT (code) DO NOTHING;

INSERT INTO gcmb.user_account (id, username, email, password_hash, role_id, status, failed_login_count, password_changed_at)
SELECT '44440000-0000-4000-8000-000000000001', 'uc44.accountant', 'uc44.accountant@example.invalid',
       '{pbkdf2-gcmb}9afe26c5f4f2c4f61e853fb198dd7ad11af6c922b2954e87488e4251cfd646535b12179edcc81424a24c22bc96edf750',
       r.id, 'ACTIVE', 0, CURRENT_TIMESTAMP
FROM gcmb.role r WHERE r.code = 'ACCOUNTANT'
ON CONFLICT (id) DO UPDATE SET
  username = EXCLUDED.username,
  email = EXCLUDED.email,
  password_hash = EXCLUDED.password_hash,
  role_id = EXCLUDED.role_id,
  status = 'ACTIVE',
  failed_login_count = 0,
  locked_until = NULL,
  password_changed_at = CURRENT_TIMESTAMP;

INSERT INTO gcmb.employee (id, code, user_id, full_name, hired_on, status)
VALUES ('44440000-0000-4000-8000-000000000002', 'DEMO-UC44-ACC',
        '44440000-0000-4000-8000-000000000001', 'Kế toán demo UC44', CURRENT_DATE, 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
  code = EXCLUDED.code,
  user_id = EXCLUDED.user_id,
  full_name = EXCLUDED.full_name,
  status = 'ACTIVE';

INSERT INTO gcmb.employee_assignment (id, employee_id, branch_id, valid_from, is_branch_manager, assigned_by, reason)
VALUES ('44440000-0000-4000-8000-000000000003', '44440000-0000-4000-8000-000000000002',
        '34370000-0000-4000-8000-000000000001', CURRENT_TIMESTAMP - INTERVAL '1 day', FALSE,
        '44440000-0000-4000-8000-000000000001', 'Phân công tài khoản demo UC44')
ON CONFLICT (id) DO UPDATE SET
  employee_id = EXCLUDED.employee_id,
  branch_id = EXCLUDED.branch_id,
  valid_from = EXCLUDED.valid_from,
  valid_to = NULL,
  is_branch_manager = FALSE,
  assigned_by = EXCLUDED.assigned_by,
  reason = EXCLUDED.reason;

COMMIT;
