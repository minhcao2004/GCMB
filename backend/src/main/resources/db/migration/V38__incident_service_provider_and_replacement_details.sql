ALTER TABLE gcmb.asset_incident ADD COLUMN IF NOT EXISTS service_provider varchar(200);
ALTER TABLE gcmb.asset_incident ADD COLUMN IF NOT EXISTS replacement_details text;
ALTER TABLE gcmb.asset_incident ADD COLUMN IF NOT EXISTS estimated_cost numeric(18,0);
