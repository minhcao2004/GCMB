-- Give shared combos a stable F&B SKU that can resolve to each branch's own menu/inventory row.
ALTER TABLE gcmb.combo_item ADD COLUMN menu_item_code varchar(60);
UPDATE gcmb.combo_item ci SET menu_item_code=m.code
FROM gcmb.menu_item m WHERE m.id=ci.menu_item_id;
ALTER TABLE gcmb.combo_item ALTER COLUMN menu_item_code SET NOT NULL;
CREATE INDEX ix_combo_item_menu_item_code ON gcmb.combo_item(menu_item_code);
