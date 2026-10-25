-- Do not choose which employee loses access automatically.
SELECT pg_advisory_xact_lock(1300261013);
LOCK TABLE gcmb.branch_manager_assignment IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE overloaded text;
BEGIN
    SELECT string_agg(code, ', ' ORDER BY code) INTO overloaded
    FROM gcmb.branch b
    WHERE (SELECT count(*) FROM gcmb.branch_manager_assignment a WHERE a.branch_id=b.id)>2;
    IF overloaded IS NOT NULL THEN
        RAISE EXCEPTION 'Branches exceed two managers: %. Remove the extra assignments before applying V20.', overloaded;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION gcmb.enforce_branch_manager_limit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE previous_user uuid; manager_count integer;
BEGIN
    PERFORM pg_advisory_xact_lock(1300261013);
    PERFORM id FROM gcmb.branch WHERE id=NEW.branch_id FOR UPDATE;
    IF TG_OP='UPDATE' THEN previous_user := OLD.user_id; END IF;
    SELECT count(*) INTO manager_count FROM gcmb.branch_manager_assignment
        WHERE branch_id=NEW.branch_id AND user_id IS DISTINCT FROM previous_user;
    IF manager_count >= 2 THEN
        RAISE EXCEPTION 'A branch may have at most two managers' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
