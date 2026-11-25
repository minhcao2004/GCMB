BEGIN;
LOCK TABLE public.flyway_schema_history IN EXCLUSIVE MODE;
DO $$
BEGIN
  IF (SELECT count(*) FROM public.flyway_schema_history WHERE version = '65' AND script = 'V65__cash_closing_notifications.sql' AND checksum = 844837785 AND success) <> 1
     OR EXISTS (SELECT 1 FROM public.flyway_schema_history WHERE version::numeric > 65 OR NOT success)
     OR to_regclass('gcmb.cash_closing_notification') IS NULL
  THEN
    RAISE EXCEPTION 'History differs from verified local state; no changes made';
  END IF;
END $$;
UPDATE public.flyway_schema_history
SET version = '66', description = 'cash closing notifications', script = 'V66__cash_closing_notifications.sql', checksum = 639331970
WHERE version = '65' AND script = 'V65__cash_closing_notifications.sql' AND checksum = 844837785 AND success;
COMMIT;
