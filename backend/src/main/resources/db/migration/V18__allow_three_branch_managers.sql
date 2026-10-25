-- A manager still belongs to one branch; a branch may have up to three managers.
ALTER TABLE gcmb.branch_manager_assignment DROP CONSTRAINT branch_manager_assignment_pkey;
ALTER TABLE gcmb.branch_manager_assignment ADD PRIMARY KEY (branch_id, user_id);

CREATE FUNCTION gcmb.enforce_branch_manager_limit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE previous_user uuid; manager_count integer;
BEGIN
    PERFORM pg_advisory_xact_lock(1300261013);
    PERFORM id FROM gcmb.branch WHERE id=NEW.branch_id FOR UPDATE;
    IF TG_OP='UPDATE' THEN previous_user := OLD.user_id; END IF;
    SELECT count(*) INTO manager_count FROM gcmb.branch_manager_assignment
        WHERE branch_id=NEW.branch_id AND user_id IS DISTINCT FROM previous_user;
    IF manager_count >= 3 THEN
        RAISE EXCEPTION 'A branch may have at most three managers' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER branch_manager_limit BEFORE INSERT OR UPDATE ON gcmb.branch_manager_assignment
    FOR EACH ROW EXECUTE FUNCTION gcmb.enforce_branch_manager_limit();
