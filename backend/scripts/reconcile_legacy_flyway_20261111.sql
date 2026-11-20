-- One-time reconciliation for the verified pre-merge local history (42 migrations).
-- Stop the backend and back up public.flyway_schema_history before running.
-- V3-V5 differ only by removed copyright comments; all moved SQL is unchanged.
-- Does not mark the new finance migration V40 as applied or change business data.
BEGIN;
LOCK TABLE public.flyway_schema_history IN EXCLUSIVE MODE;

CREATE TEMP TABLE migration_mapping (
    old_version varchar PRIMARY KEY, old_script varchar NOT NULL,
    old_checksum integer NOT NULL, new_version varchar NOT NULL,
    new_script varchar NOT NULL, new_checksum integer NOT NULL
) ON COMMIT DROP;

INSERT INTO migration_mapping VALUES
('3','V3__room_price_catalog.sql',341774739,'3','V3__room_price_catalog.sql',1759023455),
('4','V4__versioned_price_rules.sql',1594290223,'4','V4__versioned_price_rules.sql',2097460310),
('5','V5__branch_room_management.sql',-167495418,'5','V5__branch_room_management.sql',-262747397),
('32','V32__payroll_and_cash_closing.sql',-97488013,'41','V41__payroll_and_cash_closing.sql',-97488013),
('33','V33__branch_payroll_submission.sql',1364011196,'42','V42__branch_payroll_submission.sql',1364011196),
('34','V34__shared_combo_fnb_sku.sql',-518340809,'32','V32__shared_combo_fnb_sku.sql',-518340809),
('35','V35__shared_combo_fnb_identity.sql',1923902276,'33','V33__shared_combo_fnb_identity.sql',1923902276),
('36','V36__combo_included_fnb_quantity.sql',1712058100,'34','V34__combo_included_fnb_quantity.sql',1712058100),
('37','V37__combo_food_gift_and_booking_snapshot.sql',22207210,'35','V35__combo_food_gift_and_booking_snapshot.sql',22207210),
('38','V38__equipment_catalog_and_incident_workflow.sql',622925342,'36','V36__equipment_catalog_and_incident_workflow.sql',622925342),
('39','V39__incident_approval_workflow.sql',1798804647,'37','V37__incident_approval_workflow.sql',1798804647),
('40','V40__incident_service_provider_and_replacement_details.sql',709498388,'38','V38__incident_service_provider_and_replacement_details.sql',709498388),
('41','V41__incident_repair_invoice_and_transfer_proof.sql',-549942008,'39','V39__incident_repair_invoice_and_transfer_proof.sql',-549942008),
('42','V42__branch_payroll_parts.sql',-300041297,'43','V43__branch_payroll_parts.sql',-300041297);

DO $$
BEGIN
    IF (SELECT count(*) FROM public.flyway_schema_history) <> 42
       OR EXISTS (SELECT 1 FROM public.flyway_schema_history WHERE NOT success)
       OR (SELECT count(DISTINCT version) FROM public.flyway_schema_history
           WHERE version IN (SELECT n::text FROM generate_series(1,42) n)) <> 42
       OR (SELECT count(*) FROM public.flyway_schema_history h JOIN migration_mapping m
           ON h.version=m.old_version AND h.script=m.old_script AND h.checksum=m.old_checksum) <> 14
    THEN
        RAISE EXCEPTION 'History differs from the verified legacy database. No changes made; inspect before reconciling.';
    END IF;
END $$;

UPDATE public.flyway_schema_history h
SET version=m.new_version, script=m.new_script, checksum=m.new_checksum
FROM migration_mapping m WHERE h.version=m.old_version;

COMMIT;
