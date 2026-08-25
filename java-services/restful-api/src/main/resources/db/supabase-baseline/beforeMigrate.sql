-- Repeat the new-target check at the migration boundary when validation is
-- disabled.  This callback is read-only and does not touch managed schemas.
DO $$
DECLARE
    schema_name text;
    versioned_count bigint;
    reviewed_baseline_count bigint;
BEGIN
    FOR schema_name IN
        SELECT n.nspname
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE c.relname = 'flyway_schema_history'
           AND c.relkind IN ('r', 'p')
    LOOP
        EXECUTE format($query$
            SELECT COUNT(*) FILTER (WHERE version IS NOT NULL),
                   COUNT(*) FILTER (
                       WHERE version = '20'
                         AND type = 'SQL_BASELINE'
                         AND script = 'B20__campuscore_supabase_baseline.sql'
                         AND success
                   )
              FROM %I.flyway_schema_history
        $query$, schema_name)
           INTO versioned_count, reviewed_baseline_count;
        IF versioned_count > 0
           AND NOT (versioned_count = 1 AND reviewed_baseline_count = 1) THEN
            RAISE EXCEPTION USING
                ERRCODE = '55000',
                MESSAGE = 'The CampusCore Supabase baseline is only valid for a new target or its own exact B20 history';
        END IF;
    END LOOP;
END $$;
