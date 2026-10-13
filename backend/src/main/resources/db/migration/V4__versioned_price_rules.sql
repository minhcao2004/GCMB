/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 12/10/2026
 * Description: Bổ sung phiên bản và thời hạn áp dụng cho quy tắc giá.
 */

-- Effective-dated, versioned global room price rules.
ALTER TABLE gcmb.price_rule ADD COLUMN code varchar(60);
ALTER TABLE gcmb.price_rule ADD COLUMN version integer NOT NULL DEFAULT 1;
ALTER TABLE gcmb.price_rule ADD COLUMN weekdays smallint[] NOT NULL DEFAULT ARRAY[1,2,3,4,5,6,7]::smallint[];

UPDATE gcmb.price_rule
SET code = CASE
  WHEN name = 'Queen · Giờ lẻ' THEN 'BG-QN-HOURLY'
  WHEN name = 'King · Giờ lẻ' THEN 'BG-KG-HOURLY'
  ELSE 'BG-' || upper(substr(replace(id::text, '-', ''), 1, 12))
END
WHERE code IS NULL;

ALTER TABLE gcmb.price_rule ALTER COLUMN code SET NOT NULL;
ALTER TABLE gcmb.price_rule ADD CONSTRAINT price_rule_version_check CHECK (version > 0);
ALTER TABLE gcmb.price_rule ADD CONSTRAINT price_rule_weekdays_check CHECK (cardinality(weekdays) BETWEEN 1 AND 7);
ALTER TABLE gcmb.price_rule ADD CONSTRAINT price_rule_code_version_unique UNIQUE (code, version);
CREATE INDEX ix_price_rule_catalog ON gcmb.price_rule(branch_id, room_type_id, active, valid_from);
