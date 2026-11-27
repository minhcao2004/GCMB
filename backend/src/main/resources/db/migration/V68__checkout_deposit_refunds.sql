ALTER TABLE gcmb.refund_request
    ADD COLUMN checkout_bill_id uuid,
    ADD CONSTRAINT refund_request_checkout_bill_fk
        FOREIGN KEY (checkout_bill_id, booking_id) REFERENCES gcmb.bill(id, booking_id) ON DELETE RESTRICT,
    ADD CONSTRAINT refund_request_checkout_bill_unique UNIQUE (checkout_bill_id),
    ADD CONSTRAINT refund_request_checkout_deposit_check CHECK (checkout_bill_id IS NULL OR kind = 'DEPOSIT');
