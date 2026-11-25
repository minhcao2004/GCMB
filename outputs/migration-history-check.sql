SELECT version, script, checksum, success FROM public.flyway_schema_history WHERE version::numeric >= 59 ORDER BY installed_rank;
