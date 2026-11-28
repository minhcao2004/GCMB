-- BR-159: các order đã được bỏ qua lúc chốt bill cũ không còn được để ở trạng thái chờ.
-- Hoàn lại số lượng đã trừ khi xác nhận order, trước khi chuyển order sang CANCELLED.
DO $$
DECLARE
    sale record;
    stock_before numeric(18,3);
BEGIN
    FOR sale IN
        SELECT sm.id, sm.inventory_item_id, -sm.quantity_delta AS quantity,
               COALESCE(b.issued_by, booking.created_by) AS actor_id, o.booking_id
        FROM gcmb.stock_movement sm
        JOIN gcmb.order_line line ON line.id=sm.order_line_id
        JOIN gcmb.fb_order o ON o.id=line.order_id
        JOIN gcmb.bill b ON b.booking_id=o.booking_id AND b.status IN ('ISSUED','SETTLED')
        JOIN gcmb.booking booking ON booking.id=o.booking_id
        WHERE o.status IN ('PENDING','ACCEPTED') AND sm.kind='SALE'
          AND NOT EXISTS (
              SELECT 1 FROM gcmb.stock_movement reversal
              WHERE reversal.kind='RETURN' AND reversal.reverses_id=sm.id)
        ORDER BY sm.inventory_item_id,sm.id
    LOOP
        SELECT quantity INTO STRICT stock_before FROM gcmb.stock_balance
        WHERE inventory_item_id=sale.inventory_item_id FOR UPDATE;
        INSERT INTO gcmb.stock_movement(inventory_item_id,kind,quantity_delta,quantity_before,
            quantity_after,reverses_id,reason,actor_id,idempotency_key)
        VALUES (sale.inventory_item_id,'RETURN',sale.quantity,stock_before,
            stock_before+sale.quantity,sale.id,
            'Hoàn kho order chưa phục vụ sau bill đã chốt của booking '||sale.booking_id,
            sale.actor_id,'RETURN-'||sale.id);
    END LOOP;
END;
$$;

-- Chỉ tạm ngưng guard trên header để sửa dữ liệu lịch sử; guard phí và dòng món vẫn giữ nguyên.
ALTER TABLE gcmb.fb_order DISABLE TRIGGER checkout_guard_order_header;
UPDATE gcmb.fb_order o SET status='CANCELLED'
FROM gcmb.bill b
WHERE b.booking_id=o.booking_id AND b.status IN ('ISSUED','SETTLED')
  AND o.status IN ('PENDING','ACCEPTED');
ALTER TABLE gcmb.fb_order ENABLE TRIGGER checkout_guard_order_header;
