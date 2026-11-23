-- Local-only preview fixture for the Branch Manager overview.
-- Run against the local `gcmb` database only. All inserted rows are clearly
-- marked PV-DASH and belong to the existing Hoa Bang manager branch.
-- Idempotent: fixed UUIDs/codes and ON CONFLICT keep reruns from duplicating rows.
BEGIN;
SET LOCAL search_path TO gcmb, public;

CREATE TEMP TABLE dashboard_preview_tx ON COMMIT DROP AS
WITH dates AS (
  SELECT n,
    CASE n
      WHEN 1 THEN CURRENT_DATE
      WHEN 2 THEN CURRENT_DATE - 2
      WHEN 3 THEN CURRENT_DATE - 5
      WHEN 4 THEN CURRENT_DATE - 10
      WHEN 5 THEN CURRENT_DATE - 15
      ELSE (date_trunc('month', CURRENT_DATE - make_interval(months => n - 5))::date + 12)
    END AS sale_date
  FROM generate_series(1, 10) AS n
), context AS (
  SELECT d.n, d.sale_date, b.id AS branch_id, u.id AS manager_id,
    e.id AS employee_id,
    (SELECT r.id FROM gcmb.room r WHERE r.branch_id=b.id AND r.operating_status <> 'INACTIVE'
     ORDER BY r.code OFFSET ((d.n - 1) % (SELECT count(*) FROM gcmb.room x WHERE x.branch_id=b.id AND x.operating_status <> 'INACTIVE')) LIMIT 1) AS room_id,
    (SELECT id FROM gcmb.menu_item WHERE branch_id=b.id AND active AND deleted_at IS NULL ORDER BY code LIMIT 1) AS menu_one_id,
    (SELECT id FROM gcmb.menu_item WHERE branch_id=b.id AND active AND deleted_at IS NULL ORDER BY code OFFSET 1 LIMIT 1) AS menu_two_id,
    (200000 + d.n * 25000)::numeric AS room_amount
  FROM dates d
  JOIN gcmb.branch b ON b.code='HN_HOA_BANG'
  JOIN gcmb.branch_manager_assignment bma ON bma.branch_id=b.id
  JOIN gcmb.user_account u ON u.id=bma.user_id AND u.username='qlcs.hoabang'
  JOIN gcmb.employee e ON e.code='GENZ-REC-HOA-BANG'
)
SELECT c.*,
  ('d45b0000-0000-4000-8000-' || lpad(c.n::text, 12, '0'))::uuid AS booking_id,
  ('d45b1000-0000-4000-8000-' || lpad(c.n::text, 12, '0'))::uuid AS allocation_id,
  ('d45b2000-0000-4000-8000-' || lpad(c.n::text, 12, '0'))::uuid AS charge_id,
  ('d45b3000-0000-4000-8000-' || lpad(c.n::text, 12, '0'))::uuid AS order_id,
  ('d45b4000-0000-4000-8000-' || lpad(c.n::text, 12, '0'))::uuid AS bill_id,
  ('d45b5000-0000-4000-8000-' || lpad(c.n::text, 12, '0'))::uuid AS payment_id
FROM context c;

DO $$
BEGIN
  IF (SELECT count(*) FROM dashboard_preview_tx WHERE room_id IS NULL OR menu_one_id IS NULL OR menu_two_id IS NULL) > 0 THEN
    RAISE EXCEPTION 'Preview fixture needs active rooms and two menu items in HN_HOA_BANG.';
  END IF;
END $$;

INSERT INTO gcmb.booking (id, branch_id, code, contact_name, contact_phone, guest_count, channel,
  planned_start, planned_end, actual_checkin, actual_checkout, status, refund_cutoff_minutes, created_by, note)
SELECT booking_id, branch_id, 'PV-DASH-' || to_char(sale_date,'YYYYMMDD') || '-' || lpad(n::text,2,'0'),
  'Khách xem trước ' || n, '090000' || lpad(n::text,4,'0'), 2, 'WALK_IN',
  ((sale_date + time '10:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'),
  ((sale_date + time '12:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'),
  ((sale_date + time '10:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'),
  ((sale_date + time '12:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'),
  'COMPLETED', 0, manager_id, 'Dữ liệu xem trước dashboard local (PV-DASH)'
FROM dashboard_preview_tx ON CONFLICT (id) DO NOTHING;

INSERT INTO gcmb.room_allocation (id, branch_id, booking_id, room_id, starts_at, ends_at,
  actual_start, actual_end, state, created_by)
SELECT allocation_id, branch_id, booking_id, room_id,
  ((sale_date + time '10:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'),
  ((sale_date + time '12:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'),
  ((sale_date + time '10:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'),
  ((sale_date + time '12:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'), 'FINISHED', manager_id
FROM dashboard_preview_tx t WHERE NOT EXISTS (SELECT 1 FROM gcmb.room_allocation a WHERE a.id=t.allocation_id)
ON CONFLICT (id) DO NOTHING;

INSERT INTO gcmb.booking_charge (id, booking_id, allocation_id, kind, description, quantity,
  unit_price, amount, pricing_snapshot, status)
SELECT charge_id, booking_id, allocation_id, 'ROOM', 'Tiền phòng · xem trước', 1,
  room_amount, room_amount, '{"fixture":"PV-DASH"}'::jsonb, 'ACTIVE'
FROM dashboard_preview_tx t WHERE NOT EXISTS (SELECT 1 FROM gcmb.booking_charge c WHERE c.id=t.charge_id)
ON CONFLICT (id) DO NOTHING;

INSERT INTO gcmb.fb_order (id, branch_id, booking_id, allocation_id, channel, created_by,
  status, accepted_by, accepted_at, served_at, idempotency_key, assigned_employee_id)
SELECT order_id, branch_id, booking_id, allocation_id, 'STAFF', manager_id, 'SERVED', manager_id,
  ((sale_date + time '11:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'),
  ((sale_date + time '11:30') AT TIME ZONE 'Asia/Ho_Chi_Minh'), 'PV-DASH-' || n, employee_id
FROM dashboard_preview_tx t WHERE NOT EXISTS (SELECT 1 FROM gcmb.fb_order o WHERE o.id=t.order_id)
ON CONFLICT (id) DO NOTHING;

INSERT INTO gcmb.order_line (id, order_id, branch_id, booking_id, menu_item_id, name_snapshot, quantity, unit_price)
SELECT ('d45b6000-0000-4000-8000-' || lpad((n*2-1)::text,12,'0'))::uuid, order_id, branch_id,
  booking_id, menu_one_id, (SELECT name FROM gcmb.menu_item WHERE id=menu_one_id), 1,
  (SELECT sale_price FROM gcmb.menu_item WHERE id=menu_one_id)
FROM dashboard_preview_tx t WHERE NOT EXISTS (SELECT 1 FROM gcmb.order_line ol WHERE ol.id=('d45b6000-0000-4000-8000-' || lpad((t.n*2-1)::text,12,'0'))::uuid)
ON CONFLICT (id) DO NOTHING;
INSERT INTO gcmb.order_line (id, order_id, branch_id, booking_id, menu_item_id, name_snapshot, quantity, unit_price)
SELECT ('d45b6000-0000-4000-8000-' || lpad((n*2)::text,12,'0'))::uuid, order_id, branch_id,
  booking_id, menu_two_id, (SELECT name FROM gcmb.menu_item WHERE id=menu_two_id), 2,
  (SELECT sale_price FROM gcmb.menu_item WHERE id=menu_two_id)
FROM dashboard_preview_tx t WHERE NOT EXISTS (SELECT 1 FROM gcmb.order_line ol WHERE ol.id=('d45b6000-0000-4000-8000-' || lpad((t.n*2)::text,12,'0'))::uuid)
ON CONFLICT (id) DO NOTHING;

INSERT INTO gcmb.bill (id, booking_id, branch_id, number, status, gross_amount, discount_amount,
  net_amount, issued_at, completed_at, issued_by, checkout_at, inspection_note)
SELECT bill_id, booking_id, branch_id, 'PV-DASH-' || to_char(sale_date,'YYYYMMDD') || '-' || lpad(n::text,2,'0'),
  'SETTLED', room_amount + fnb.amount, 0, room_amount + fnb.amount,
  ((sale_date + time '12:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'),
  ((sale_date + time '12:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'), manager_id,
  ((sale_date + time '12:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'), 'Dữ liệu xem trước dashboard local'
FROM dashboard_preview_tx t
CROSS JOIN LATERAL (SELECT sum(ol.quantity * ol.unit_price) amount FROM gcmb.order_line ol WHERE ol.order_id=t.order_id) fnb
ON CONFLICT (id) DO NOTHING;

INSERT INTO gcmb.bill_line (bill_id, booking_charge_id, description, quantity, unit_price, amount)
SELECT bill_id, charge_id, 'Tiền phòng · xem trước', 1, room_amount, room_amount
FROM dashboard_preview_tx ON CONFLICT (booking_charge_id) DO NOTHING;
INSERT INTO gcmb.bill_line (bill_id, order_line_id, description, quantity, unit_price, amount)
SELECT t.bill_id, ol.id, ol.name_snapshot, ol.quantity, ol.unit_price, ol.quantity * ol.unit_price
FROM dashboard_preview_tx t JOIN gcmb.order_line ol ON ol.order_id=t.order_id
ON CONFLICT (order_line_id) DO NOTHING;

INSERT INTO gcmb.payment (id, branch_id, booking_id, bill_id, purpose, method, amount,
  cash_tendered, cash_change, received_at, received_by, idempotency_key)
SELECT t.payment_id, t.branch_id, t.booking_id, t.bill_id, 'BILL', 'CASH', b.net_amount,
  b.net_amount, 0, b.completed_at, t.manager_id, 'PV-DASH-PAY-' || t.n
FROM dashboard_preview_tx t JOIN gcmb.bill b ON b.id=t.bill_id
ON CONFLICT (id) DO NOTHING;

-- One clearly labeled low-stock product so the alert card is populated.
INSERT INTO gcmb.inventory_item (id, branch_id, sku, name, unit_id, tracking_mode, active, category)
SELECT 'd45b7000-0000-4000-8000-000000000001', b.id, 'PV-DASH-DRINK', 'Nước ngọt (xem trước)', u.id,
  'MANUAL', TRUE, 'Dữ liệu xem trước'
FROM gcmb.branch b CROSS JOIN gcmb.unit u
WHERE b.code='HN_HOA_BANG' AND u.code='LON'
ON CONFLICT (id) DO NOTHING;
INSERT INTO gcmb.stock_balance (id, inventory_item_id, quantity, minimum_quantity, version)
VALUES ('d45b7000-0000-4000-8000-000000000002', 'd45b7000-0000-4000-8000-000000000001', 3, 12, 0)
ON CONFLICT (id) DO NOTHING;

-- One clearly labeled high-value request to populate the head-office approval card.
INSERT INTO gcmb.finance_expense_category
  (id, code, name, description, category_group, cost_nature, recognition_mode, cost_type,
   invoice_required, reimbursement_allowed, created_by)
SELECT 'd45bc000-0000-4000-8000-000000000001', 'PV-DASH-CAT', 'Bảo trì thiết bị (xem trước)',
  'Danh mục chỉ phục vụ xem trước dashboard local.', 'MAINTENANCE', 'VARIABLE', 'IMMEDIATE', 'OPEX', FALSE, TRUE, u.id
FROM gcmb.user_account u WHERE u.username='qlcs.hoabang'
ON CONFLICT (id) DO NOTHING;
INSERT INTO gcmb.finance_expense_request
  (id, code, kind, branch_id, category_id, category_type, amount, expense_on, description,
   beneficiary_name, bank_account, bank_name, requested_by, status)
SELECT 'd45bc100-0000-4000-8000-000000000001', 'PV-DASH-APPROVAL', 'PAYMENT', b.id, c.id, 'OPEX',
  5800000, CURRENT_DATE, 'Đề nghị bảo trì thiết bị — dữ liệu xem trước',
  'Đơn vị kỹ thuật demo', '0123456789', 'Ngân hàng demo', u.id, 'WAITING_HEAD_OFFICE'
FROM gcmb.branch b JOIN gcmb.user_account u ON u.username='qlcs.hoabang'
JOIN gcmb.finance_expense_category c ON c.id='d45bc000-0000-4000-8000-000000000001'
WHERE b.code='HN_HOA_BANG'
ON CONFLICT (id) DO NOTHING;
INSERT INTO gcmb.finance_request_history (id, request_id, actor_id, action, note)
SELECT 'd45bc200-0000-4000-8000-000000000001', r.id, r.requested_by, 'SUBMITTED',
  'Dữ liệu xem trước dashboard local; không duyệt hoặc thanh toán.'
FROM gcmb.finance_expense_request r WHERE r.id='d45bc100-0000-4000-8000-000000000001'
ON CONFLICT (id) DO NOTHING;

-- Put two staff on today's existing morning shift and mark attendance.
INSERT INTO gcmb.attendance_period (id, branch_id, month, status)
SELECT 'd45b8000-0000-4000-8000-000000000001', b.id, date_trunc('month',CURRENT_DATE)::date, 'OPEN'
FROM gcmb.branch b WHERE b.code='HN_HOA_BANG'
ON CONFLICT (branch_id,month) DO NOTHING;
INSERT INTO gcmb.shift_assignment (id, shift_id, employee_id, source, status, assigned_by)
SELECT ('d45b9000-0000-4000-8000-' || lpad(row_number() OVER (ORDER BY e.code)::text,12,'0'))::uuid,
  s.id, e.id, 'MANAGER', 'CONFIRMED', u.id
FROM gcmb.branch b
JOIN gcmb.user_account u ON u.username='qlcs.hoabang'
JOIN gcmb.scheduled_shift s ON s.branch_id=b.id AND s.work_date=(CURRENT_TIMESTAMP AT TIME ZONE b.timezone)::date
JOIN gcmb.shift_template st ON st.id=s.template_id AND st.code='MORNING'
JOIN gcmb.employee e ON e.code IN ('GENZ-REC-HOA-BANG','GENZ-ACC')
WHERE b.code='HN_HOA_BANG' AND NOT EXISTS (
  SELECT 1 FROM gcmb.shift_assignment existing WHERE existing.shift_id=s.id AND existing.employee_id=e.id
)
ON CONFLICT (id) DO NOTHING;
INSERT INTO gcmb.attendance (id, assignment_id, period_id, actual_in, actual_out, break_minutes,
  worked_minutes, recorded_by, status)
SELECT ('d45ba000-0000-4000-8000-' || lpad(row_number() OVER (ORDER BY e.code)::text,12,'0'))::uuid,
  sa.id, ap.id,
  ((CURRENT_DATE + time '07:05') AT TIME ZONE 'Asia/Ho_Chi_Minh'),
  ((CURRENT_DATE + time '11:50') AT TIME ZONE 'Asia/Ho_Chi_Minh'), 15, 270, u.id, 'WORKED'
FROM gcmb.branch b
JOIN gcmb.user_account u ON u.username='qlcs.hoabang'
JOIN gcmb.attendance_period ap ON ap.branch_id=b.id AND ap.month=date_trunc('month',CURRENT_DATE)::date
JOIN gcmb.scheduled_shift s ON s.branch_id=b.id AND s.work_date=(CURRENT_TIMESTAMP AT TIME ZONE b.timezone)::date
JOIN gcmb.shift_template st ON st.id=s.template_id AND st.code='MORNING'
JOIN gcmb.shift_assignment sa ON sa.shift_id=s.id AND sa.status='CONFIRMED'
JOIN gcmb.employee e ON e.id=sa.employee_id AND e.code IN ('GENZ-REC-HOA-BANG','GENZ-ACC')
WHERE b.code='HN_HOA_BANG' AND NOT EXISTS (SELECT 1 FROM gcmb.attendance existing WHERE existing.assignment_id=sa.id)
ON CONFLICT (assignment_id) DO NOTHING;

COMMIT;
