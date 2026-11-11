DO $$
DECLARE constraint_row record;
BEGIN
  FOR constraint_row IN
    SELECT conname FROM pg_constraint
    WHERE conrelid='gcmb.asset_incident'::regclass AND contype='c'
      AND pg_get_constraintdef(oid) ILIKE '%status%'
  LOOP
    EXECUTE format('ALTER TABLE gcmb.asset_incident DROP CONSTRAINT %I', constraint_row.conname);
  END LOOP;
END $$;
ALTER TABLE gcmb.asset_incident ADD CONSTRAINT ck_asset_incident_status_v37
  CHECK (status IN ('REPORTED','WAITING_APPROVAL','REJECTED','ASSESSED','IN_PROGRESS','VERIFIED','CLOSED'));
