ALTER TABLE gcmb.stock_receipt_line ADD COLUMN expiry_date date;

COMMENT ON COLUMN gcmb.stock_receipt_line.expiry_date IS 'Hạn sử dụng của mặt hàng trong phiếu nhập; phiếu cũ có thể không có giá trị.';

UPDATE gcmb.inventory_item
SET category = CASE lower(trim(category))
    WHEN lower('Đồ uống') THEN 'Đồ uống'
    WHEN lower('Đồ ăn') THEN 'Đồ ăn'
    WHEN lower('Nguyên liệu') THEN 'Nguyên liệu'
    WHEN lower('Vật tư') THEN 'Vật tư'
    WHEN lower('Khác') THEN 'Khác'
    END
WHERE lower(trim(category)) IN (lower('Đồ uống'), lower('Đồ ăn'), lower('Nguyên liệu'), lower('Vật tư'), lower('Khác'));
