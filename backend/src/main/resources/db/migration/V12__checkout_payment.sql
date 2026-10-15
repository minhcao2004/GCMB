/* HuyDV - 14/10/2026: Lưu thời điểm chốt sử dụng và kết quả kiểm tra phòng. */
ALTER TABLE gcmb.bill
    ADD COLUMN checkout_at timestamptz,
    ADD COLUMN inspection_note text NOT NULL DEFAULT '',
    ADD COLUMN issued_by uuid REFERENCES gcmb.user_account(id);

-- Bill đã chốt không được phát sinh thêm phí/order từ các luồng chạy đồng thời.
CREATE FUNCTION gcmb.guard_checkout_charge() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target uuid;
BEGIN
    IF TG_OP='DELETE' THEN target=OLD.booking_id; ELSE target=NEW.booking_id; END IF;
    PERFORM id FROM gcmb.booking WHERE id=target FOR UPDATE;
    IF EXISTS(SELECT 1 FROM gcmb.bill WHERE booking_id=target AND status IN ('ISSUED','SETTLED')) THEN
        RAISE EXCEPTION 'Bill đã chốt; không được thay đổi phí hoặc order.' USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END;
$$;
CREATE TRIGGER checkout_guard_charge BEFORE INSERT OR UPDATE OR DELETE ON gcmb.booking_charge
    FOR EACH ROW EXECUTE FUNCTION gcmb.guard_checkout_charge();
CREATE TRIGGER checkout_guard_order BEFORE INSERT OR UPDATE OR DELETE ON gcmb.order_line
    FOR EACH ROW EXECUTE FUNCTION gcmb.guard_checkout_charge();
CREATE TRIGGER checkout_guard_order_header BEFORE INSERT OR UPDATE OR DELETE ON gcmb.fb_order
    FOR EACH ROW EXECUTE FUNCTION gcmb.guard_checkout_charge();

CREATE FUNCTION gcmb.guard_checkout_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM id FROM gcmb.booking WHERE id=NEW.booking_id FOR UPDATE;
    IF EXISTS(SELECT 1 FROM gcmb.bill WHERE booking_id=NEW.booking_id AND status IN ('ISSUED','SETTLED'))
       AND NOT (TG_OP='UPDATE' AND NEW.state='FINISHED' AND
           EXISTS(SELECT 1 FROM gcmb.bill WHERE booking_id=NEW.booking_id AND status='SETTLED')) THEN
        RAISE EXCEPTION 'Booking đang checkout; không được đổi phòng hoặc gia hạn.' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER checkout_guard_allocation BEFORE INSERT OR UPDATE ON gcmb.room_allocation
    FOR EACH ROW EXECUTE FUNCTION gcmb.guard_checkout_allocation();
