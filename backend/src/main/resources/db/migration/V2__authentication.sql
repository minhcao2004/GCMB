-- Extend the existing identity schema; preserve all existing accounts and tokens.
ALTER TABLE gcmb.auth_token ADD COLUMN code_hash text;
ALTER TABLE gcmb.auth_token ADD COLUMN failed_attempts integer NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0);
CREATE INDEX ix_auth_token_user_kind ON gcmb.auth_token(user_id, kind, created_at DESC);

-- Shared by backend instances; keys contain hashes, never addresses or credentials.
CREATE TABLE gcmb.auth_rate_limit (
  bucket_key varchar(100) PRIMARY KEY,
  window_start timestamptz NOT NULL,
  attempts integer NOT NULL CHECK (attempts > 0)
);
