-- Set a shared local-development password for the six GenZ seed accounts.
SET LOCAL search_path TO gcmb, public;

DO $$
DECLARE
    updated_count integer;
BEGIN
    UPDATE gcmb.user_account AS u
    SET password_hash = seed.password_hash,
        password_changed_at = CURRENT_TIMESTAMP,
        failed_login_count = 0,
        locked_until = NULL
    FROM (VALUES
        ('letan.hoabang', '{pbkdf2-gcmb}f1193dd4afc92a142fa15f173551b9fd40c10e9eea5a095ce70d6c6e3d508c15ff02c5642e02ac6c16f7df4796c7bc37'),
        ('letan.nguyenluongbang', '{pbkdf2-gcmb}31344d04611f8b317966a06764057716ecf775a71d92403d05a1f92a1909eb6a565a5bc7453a42c4c8241b6e25aa0c4c'),
        ('qlcs.hoabang', '{pbkdf2-gcmb}d618f99c55c6fbdcce89be333e7109445553b9f21c5627f4227819737f7ec283ee6bfff47dfd83fa437b9b6481260855'),
        ('qlcs.nguyenluongbang', '{pbkdf2-gcmb}acb8b17c4602380ddd30d8188b1f6fd4e989076c5852a28201fe00aaaa513e9db900705e9a66259ad8e0dbe82029f6c4'),
        ('qlcc', '{pbkdf2-gcmb}81c13e7dfc902788ed957300bcd896309e7bd8703480e4c50750cfd7ee48715011f3ee7e8da69584d6f229603f31344a'),
        ('ketoan', '{pbkdf2-gcmb}fba2102b128aaa5ac9d2032c9409cd8377d7af3802ef1964ca81657e79c3f3e496e1f3838cf9563c9c9c5b9b7298a7b8')
    ) AS seed(username, password_hash)
    WHERE lower(u.username) = seed.username;

    GET DIAGNOSTICS updated_count = ROW_COUNT;
    IF updated_count <> 6 THEN
        RAISE EXCEPTION 'Expected to update 6 GenZ seed accounts, updated %', updated_count;
    END IF;

    UPDATE gcmb.auth_token AS t
    SET revoked_at = CURRENT_TIMESTAMP
    FROM gcmb.user_account AS u
    WHERE t.user_id = u.id
      AND lower(u.username) IN (
          'letan.hoabang',
          'letan.nguyenluongbang',
          'qlcs.hoabang',
          'qlcs.nguyenluongbang',
          'qlcc',
          'ketoan'
      )
      AND t.revoked_at IS NULL;
END $$;
