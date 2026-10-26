ALTER TABLE gcmb.menu_item
    ADD COLUMN deleted_at timestamptz,
    ADD COLUMN deleted_by uuid,
    ADD CONSTRAINT fk_menu_item_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES gcmb.user_account(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_menu_item_deleted_actor
        CHECK ((deleted_at IS NULL) = (deleted_by IS NULL)),
    ADD CONSTRAINT ck_menu_item_deleted_inactive
        CHECK (deleted_at IS NULL OR active = false);

CREATE INDEX ix_menu_item_branch_active_name
    ON gcmb.menu_item(branch_id, active, lower(name))
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN gcmb.menu_item.deleted_at IS 'Thời điểm xóa mềm; giữ món để bảo toàn lịch sử bán hàng.';
COMMENT ON COLUMN gcmb.menu_item.deleted_by IS 'Tài khoản thực hiện xóa mềm món.';
