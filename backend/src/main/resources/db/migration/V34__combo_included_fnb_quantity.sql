ALTER TABLE gcmb.combo
    ADD COLUMN included_fnb_quantity integer NOT NULL DEFAULT 0;
ALTER TABLE gcmb.combo
    ADD CONSTRAINT ck_combo_included_fnb_quantity_nonnegative CHECK (included_fnb_quantity >= 0);
