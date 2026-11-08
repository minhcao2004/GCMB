-- UC-77..82: employment details are independent of login roles (BR-52/171).
ALTER TABLE gcmb.employee
    ADD COLUMN position varchar(60) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN contact_email varchar(254),
    ADD COLUMN notes text NOT NULL DEFAULT '',
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

UPDATE gcmb.employee e SET position=r.code
FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id
WHERE e.user_id=u.id;

CREATE INDEX ix_employee_staff_search ON gcmb.employee(status,full_name,id);

-- BR-171 also protects changes made by another module, not just the staff screen.
CREATE FUNCTION gcmb.lock_inactive_employee_account() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status <> 'ACTIVE' AND NEW.user_id IS NOT NULL THEN
        UPDATE gcmb.user_account SET status='REVOKED' WHERE id=NEW.user_id;
        UPDATE gcmb.auth_token SET revoked_at=CURRENT_TIMESTAMP
            WHERE user_id=NEW.user_id AND revoked_at IS NULL;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER employee_account_status AFTER INSERT OR UPDATE OF status,user_id ON gcmb.employee
FOR EACH ROW EXECUTE FUNCTION gcmb.lock_inactive_employee_account();
