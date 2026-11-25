-- Keep the full branch code (up to 60 characters) and allow the serial to grow.
ALTER TABLE gcmb.asset ALTER COLUMN code TYPE varchar(120);

CREATE TABLE gcmb.equipment_asset_code_counter (
  branch_code varchar(60) PRIMARY KEY,
  last_number numeric(53,0) NOT NULL CHECK (last_number >= 0)
);

-- Seed by the code prefix, including transferred assets and codes recorded before deletion or edits.
WITH existing_codes AS (
  SELECT code FROM gcmb.asset
  UNION ALL
  SELECT history.snapshot->>'code'
  FROM gcmb.audit_event
  CROSS JOIN LATERAL (VALUES (before_data), (after_data)) AS history(snapshot)
  WHERE entity_type = 'ASSET'
), numbered AS (
  SELECT regexp_match(upper(code), '^ASSET-([A-Z0-9_-]{2,60})-([0-9]{4,})$') AS parts
  FROM existing_codes
  WHERE length(code) <= 60
)
INSERT INTO gcmb.equipment_asset_code_counter (branch_code, last_number)
SELECT parts[1], max(parts[2]::numeric)
FROM numbered
WHERE parts IS NOT NULL
GROUP BY parts[1];
