CREATE OR REPLACE FUNCTION gcmb.validate_customer_payment()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  r gcmb.payment_request%ROWTYPE;
  b gcmb.bank_transaction%ROWTYPE;
BEGIN
  IF NEW.method = 'BANK_TRANSFER' THEN
    SELECT * INTO r FROM gcmb.payment_request WHERE id = NEW.request_id FOR UPDATE;
    SELECT * INTO b FROM gcmb.bank_transaction WHERE id = NEW.bank_transaction_id FOR UPDATE;
    IF r.id IS NULL OR b.id IS NULL OR r.status <> 'PENDING' OR b.match_status <> 'UNMATCHED'
       OR b.direction <> 'IN' OR b.account_id <> r.account_id OR b.amount <> r.expected_amount
       OR NEW.amount <> r.expected_amount OR NEW.purpose <> r.purpose
       OR NEW.booking_id <> r.booking_id OR NEW.branch_id <> r.branch_id
       OR NEW.bill_id IS DISTINCT FROM r.bill_id THEN
      RAISE EXCEPTION 'Invalid, duplicated or mismatched payment';
    END IF;
    UPDATE gcmb.payment_request SET status = 'SETTLED' WHERE id = r.id;
    UPDATE gcmb.bank_transaction SET match_status = 'MATCHED' WHERE id = b.id;
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION gcmb.validate_deposit_entry()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  d gcmb.deposit%ROWTYPE;
  bal numeric;
  p gcmb.payment%ROWTYPE;
  f gcmb.refund_request%ROWTYPE;
  bid uuid;
BEGIN
  SELECT * INTO d FROM gcmb.deposit WHERE id = NEW.deposit_id FOR UPDATE;
  IF d.id IS NULL THEN RAISE EXCEPTION 'Missing deposit account'; END IF;

  SELECT COALESCE(SUM(CASE WHEN kind = 'RECEIVE' THEN amount ELSE -amount END), 0)
    INTO bal FROM gcmb.deposit_entry WHERE deposit_id = d.id;

  IF NEW.kind = 'RECEIVE' THEN
    SELECT * INTO p FROM gcmb.payment WHERE id = NEW.payment_id;
    IF p.id IS NULL OR p.purpose <> 'DEPOSIT' OR p.booking_id <> d.booking_id OR p.amount <> NEW.amount THEN
      RAISE EXCEPTION 'Invalid deposit receipt source';
    END IF;
  ELSE
    IF NEW.amount > bal THEN RAISE EXCEPTION 'Deposit over-consumption'; END IF;
    IF NEW.kind = 'APPLY' THEN
      SELECT booking_id INTO bid FROM gcmb.bill WHERE id = NEW.bill_id;
      IF bid IS DISTINCT FROM d.booking_id THEN RAISE EXCEPTION 'Deposit and bill belong to different bookings'; END IF;
    ELSIF NEW.kind = 'REFUND' THEN
      SELECT * INTO f FROM gcmb.refund_request WHERE id = NEW.refund_id;
      IF f.id IS NULL OR f.deposit_id IS DISTINCT FROM d.id OR f.status <> 'PAID' OR f.amount <> NEW.amount THEN
        RAISE EXCEPTION 'Refund is not confirmed or has an invalid source';
      END IF;
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
