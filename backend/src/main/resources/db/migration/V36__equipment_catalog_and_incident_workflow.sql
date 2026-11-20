-- Shared equipment catalog maintained by Head Office.
ALTER TABLE gcmb.asset_category ALTER COLUMN branch_id DROP NOT NULL;
ALTER TABLE gcmb.asset_category ADD COLUMN IF NOT EXISTS description varchar(500) NOT NULL DEFAULT '';
CREATE UNIQUE INDEX IF NOT EXISTS ux_asset_category_global_code ON gcmb.asset_category(code) WHERE branch_id IS NULL;
COMMENT ON COLUMN gcmb.asset_category.branch_id IS 'NULL denotes the chain-wide catalog managed by Head Office.';

-- Keep legacy incidents readable while adding severity, urgency, planning and an auditable progress timeline.
ALTER TABLE gcmb.asset_incident ADD COLUMN IF NOT EXISTS severity varchar(16) NOT NULL DEFAULT 'MEDIUM';
ALTER TABLE gcmb.asset_incident ADD COLUMN IF NOT EXISTS priority varchar(16) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE gcmb.asset_incident ADD COLUMN IF NOT EXISTS expected_completion_on date;
ALTER TABLE gcmb.asset_incident ADD COLUMN IF NOT EXISTS proposed_solution text;
ALTER TABLE gcmb.asset_incident ADD COLUMN IF NOT EXISTS closed_by uuid;
ALTER TABLE gcmb.asset_incident ADD COLUMN IF NOT EXISTS closed_at timestamptz;
ALTER TABLE gcmb.asset_incident ADD CONSTRAINT ck_asset_incident_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL'));
ALTER TABLE gcmb.asset_incident ADD CONSTRAINT ck_asset_incident_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT'));

CREATE TABLE IF NOT EXISTS gcmb.asset_incident_progress (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  incident_id uuid NOT NULL REFERENCES gcmb.asset_incident(id),
  logged_by uuid NOT NULL,
  status varchar(20) NOT NULL,
  note text NOT NULL,
  expected_completion_on date,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_asset_incident_progress_incident ON gcmb.asset_incident_progress(incident_id, created_at);
