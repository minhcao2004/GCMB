SELECT row_to_json(h)::text FROM public.flyway_schema_history h ORDER BY installed_rank;
