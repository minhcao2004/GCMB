-- Remove only the local PV-DASH fixture inserted by seed_branch_dashboard_preview.sql.
BEGIN;
SET LOCAL search_path TO gcmb, public;

DELETE FROM gcmb.payment WHERE bill_id IN (SELECT id FROM gcmb.bill WHERE number LIKE 'PV-DASH-%');
DELETE FROM gcmb.bill_line WHERE bill_id IN (SELECT id FROM gcmb.bill WHERE number LIKE 'PV-DASH-%');
DELETE FROM gcmb.bill WHERE number LIKE 'PV-DASH-%';
DELETE FROM gcmb.order_line WHERE order_id IN (SELECT id FROM gcmb.fb_order WHERE id::text LIKE 'd45b3000-%');
DELETE FROM gcmb.fb_order WHERE id::text LIKE 'd45b3000-%';
DELETE FROM gcmb.booking_charge WHERE id::text LIKE 'd45b2000-%';
DELETE FROM gcmb.room_allocation WHERE id::text LIKE 'd45b1000-%';
DELETE FROM gcmb.booking WHERE code LIKE 'PV-DASH-%';

DELETE FROM gcmb.finance_request_history WHERE request_id='d45bc100-0000-4000-8000-000000000001';
DELETE FROM gcmb.finance_expense_request WHERE id='d45bc100-0000-4000-8000-000000000001';
DELETE FROM gcmb.finance_expense_category WHERE id='d45bc000-0000-4000-8000-000000000001';

DELETE FROM gcmb.attendance WHERE id::text LIKE 'd45ba000-%';
DELETE FROM gcmb.shift_assignment WHERE id::text LIKE 'd45b9000-%';
DELETE FROM gcmb.attendance_period
WHERE id='d45b8000-0000-4000-8000-000000000001'
  AND NOT EXISTS (SELECT 1 FROM gcmb.attendance WHERE period_id='d45b8000-0000-4000-8000-000000000001');
DELETE FROM gcmb.stock_balance WHERE id='d45b7000-0000-4000-8000-000000000002';
DELETE FROM gcmb.inventory_item WHERE id='d45b7000-0000-4000-8000-000000000001';

COMMIT;
