-- Store the branch-independent F&B identity for shared combo contents.
ALTER TABLE gcmb.combo_item ADD COLUMN menu_item_name varchar(200);
ALTER TABLE gcmb.combo_item ADD COLUMN menu_item_category varchar(200);
UPDATE gcmb.combo_item ci SET menu_item_name=m.name, menu_item_category=m.category
FROM gcmb.menu_item m WHERE m.id=ci.menu_item_id;
ALTER TABLE gcmb.combo_item ALTER COLUMN menu_item_name SET NOT NULL;
ALTER TABLE gcmb.combo_item ALTER COLUMN menu_item_category SET NOT NULL;
CREATE INDEX ix_combo_item_menu_identity ON gcmb.combo_item(lower(menu_item_name),lower(menu_item_category));
