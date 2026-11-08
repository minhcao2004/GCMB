ALTER TABLE gcmb.room
    ADD COLUMN mr_code varchar(40) NOT NULL
        DEFAULT ('MR-' || upper(replace(gen_random_uuid()::text, '-', '')));

CREATE UNIQUE INDEX uq_room_mr_code ON gcmb.room(mr_code);

ALTER TABLE gcmb.fb_order
    ADD COLUMN assigned_employee_id uuid,
    ADD CONSTRAINT fk_fb_order_assigned_employee
        FOREIGN KEY (assigned_employee_id) REFERENCES gcmb.employee(id) ON DELETE RESTRICT;

CREATE INDEX ix_fb_order_assigned_employee_status
    ON gcmb.fb_order(assigned_employee_id, status, created_at DESC);

COMMENT ON COLUMN gcmb.fb_order.assigned_employee_id IS
    'Lễ tân đang trong ca được giao phục vụ đơn; khác với người xác nhận tại accepted_by.';

COMMENT ON COLUMN gcmb.room.mr_code IS
    'Mã MR ngẫu nhiên, ổn định theo phòng; dùng trong QR công khai và không chứa room UUID nội bộ.';
