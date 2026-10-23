/*
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Thông tin vận hành cơ sở và chặn phát sinh nghiệp vụ khi ngừng hoạt động.
 */
ALTER TABLE gcmb.branch
    ADD COLUMN notes text NOT NULL DEFAULT '',
    ADD COLUMN status_reason text NOT NULL DEFAULT '',
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Khóa chia sẻ bảo đảm tạo booking/phòng không chạy lọt qua thao tác đóng cơ sở.
CREATE FUNCTION gcmb.require_active_branch() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE branch_status text;
BEGIN
    SELECT status INTO branch_status FROM gcmb.branch WHERE id=NEW.branch_id FOR SHARE;
    IF branch_status IS DISTINCT FROM 'ACTIVE' THEN
        RAISE EXCEPTION 'Cơ sở đã tạm ngừng hoặc đóng; không được phát sinh nghiệp vụ mới.'
            USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER booking_active_branch BEFORE INSERT ON gcmb.booking
    FOR EACH ROW EXECUTE FUNCTION gcmb.require_active_branch();
CREATE TRIGGER room_active_branch BEFORE INSERT OR UPDATE ON gcmb.room
    FOR EACH ROW EXECUTE FUNCTION gcmb.require_active_branch();
