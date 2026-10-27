-- Author: HuyDV
-- Created: 27/10/2026
-- UC-127, BR-125: theo dõi xử lý bên ngoài; không thay đổi bill hoặc thanh toán.
CREATE TABLE gcmb.tax_invoice_request (
    booking_id uuid PRIMARY KEY REFERENCES gcmb.booking(id),
    buyer_name varchar(200) NOT NULL,
    tax_code varchar(30),
    buyer_address varchar(2000),
    contact_email varchar(254),
    status varchar(30) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED','PREPARING','ISSUED','DELIVERED','DELIVERY_FAILED')),
    note varchar(2000) NOT NULL DEFAULT '',
    delivery_result varchar(2000) NOT NULL DEFAULT '',
    version bigint NOT NULL DEFAULT 0,
    created_by uuid NOT NULL REFERENCES gcmb.user_account(id),
    updated_by uuid NOT NULL REFERENCES gcmb.user_account(id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE gcmb.tax_invoice_read (
    booking_id uuid NOT NULL REFERENCES gcmb.tax_invoice_request(booking_id),
    user_id uuid NOT NULL REFERENCES gcmb.user_account(id),
    read_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (booking_id,user_id)
);
CREATE INDEX ix_tax_invoice_updated ON gcmb.tax_invoice_request(updated_at DESC);
