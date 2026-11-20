ALTER TABLE gcmb.combo
    ADD COLUMN included_food_quantity integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_combo_included_food_quantity_range CHECK (included_food_quantity BETWEEN 0 AND 20);

ALTER TABLE gcmb.booking
    ADD COLUMN combo_included_drink_quantity integer NOT NULL DEFAULT 0,
    ADD COLUMN combo_included_food_quantity integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_booking_combo_drink_quantity_range CHECK (combo_included_drink_quantity BETWEEN 0 AND 20),
    ADD CONSTRAINT ck_booking_combo_food_quantity_range CHECK (combo_included_food_quantity BETWEEN 0 AND 20);
