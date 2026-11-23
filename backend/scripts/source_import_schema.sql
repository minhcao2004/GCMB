-- Source-faithful imports. These rows remain separate from operational booking,
-- bill, and payment tables until they have been reconciled and mapped.
CREATE TABLE IF NOT EXISTS gcmb.source_import_batch (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  branch_id uuid NOT NULL REFERENCES gcmb.branch(id),
  source_system varchar(40) NOT NULL,
  source_file text NOT NULL,
  source_sha256 char(64) NOT NULL,
  period_start date NOT NULL,
  period_end date NOT NULL,
  row_count integer NOT NULL CHECK (row_count >= 0),
  imported_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (branch_id, source_system, source_sha256),
  CHECK (period_end >= period_start)
);

CREATE TABLE IF NOT EXISTS gcmb.source_import_record (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id uuid NOT NULL REFERENCES gcmb.source_import_batch(id),
  branch_id uuid NOT NULL REFERENCES gcmb.branch(id),
  record_type varchar(40) NOT NULL,
  source_sheet text NOT NULL,
  source_row integer NOT NULL CHECK (source_row > 0),
  business_date date NOT NULL,
  source_key text,
  payload jsonb NOT NULL,
  imported_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (batch_id, source_sheet, source_row)
);

CREATE INDEX IF NOT EXISTS ix_source_import_record_branch_date
  ON gcmb.source_import_record(branch_id, business_date, record_type);

COMMENT ON TABLE gcmb.source_import_batch IS
  'Provenance and idempotency metadata for spreadsheet imports; source rows are not operational ledger entries.';

COMMENT ON TABLE gcmb.source_import_record IS
  'Original spreadsheet records normalized by source date and retained as JSONB for reconciliation.';
