CREATE OR REPLACE FUNCTION gcmb.post_stock_movement()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
DECLARE
  q numeric;
  src record;
BEGIN
  SELECT quantity INTO q
    FROM gcmb.stock_balance
    WHERE inventory_item_id = NEW.inventory_item_id
    FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Create stock_balance=0 before first movement';
  END IF;

  IF q <> NEW.quantity_before
      OR NEW.quantity_after <> q + NEW.quantity_delta
      OR NEW.quantity_after < 0 THEN
    RAISE EXCEPTION 'Stale or negative stock movement';
  END IF;

  IF NEW.kind = 'RECEIPT' THEN
    SELECT l.inventory_item_id, l.quantity, r.status INTO src
      FROM gcmb.stock_receipt_line l
      JOIN gcmb.stock_receipt r ON r.id = l.receipt_id
      WHERE l.id = NEW.receipt_line_id;

    IF NOT FOUND
        OR src.inventory_item_id <> NEW.inventory_item_id
        OR src.quantity <> NEW.quantity_delta
        OR src.status <> 'POSTED' THEN
      RAISE EXCEPTION 'Invalid stock receipt source';
    END IF;
  ELSIF NEW.kind = 'SALE' THEN
    SELECT m.inventory_item_id, l.quantity, o.status, i.tracking_mode INTO src
      FROM gcmb.order_line l
      JOIN gcmb.fb_order o ON o.id = l.order_id
      JOIN gcmb.menu_item m ON m.id = l.menu_item_id
      JOIN gcmb.inventory_item i ON i.id = m.inventory_item_id
      WHERE l.id = NEW.order_line_id;

    IF NOT FOUND
        OR src.inventory_item_id <> NEW.inventory_item_id
        OR -src.quantity <> NEW.quantity_delta
        OR src.status NOT IN ('ACCEPTED', 'SERVED')
        OR src.tracking_mode <> 'AUTO_ON_SALE' THEN
      RAISE EXCEPTION 'Invalid stock sale source';
    END IF;
  END IF;

  UPDATE gcmb.stock_balance
    SET quantity = NEW.quantity_after, version = version + 1
    WHERE inventory_item_id = NEW.inventory_item_id;

  RETURN NEW;
END;
$$;
