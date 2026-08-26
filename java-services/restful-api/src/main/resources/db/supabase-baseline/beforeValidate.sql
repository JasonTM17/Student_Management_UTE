-- The baseline callback accepts only the reviewed history states: marker-only,
-- marker+B20, or marker+B20+V21. Existing V-history databases are not targets.
DO $$
DECLARE
    schema_name text;
    history_table_count bigint;
    total_count bigint;
    schema_creation_count bigint;
    reviewed_baseline_count bigint;
    reviewed_v21_count bigint;
    unexpected_application_state boolean;
BEGIN
    SELECT COUNT(*)
      INTO history_table_count
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE c.relname = 'flyway_schema_history'
       AND c.relkind IN ('r', 'p');
    IF history_table_count > 1 THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'The CampusCore Supabase baseline requires exactly one Flyway history table';
    END IF;
    FOR schema_name IN
        SELECT n.nspname
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE c.relname = 'flyway_schema_history'
           AND c.relkind IN ('r', 'p')
    LOOP
        EXECUTE format($query$
            SELECT COUNT(*),
                   COUNT(*) FILTER (
                       WHERE installed_rank = 0
                         AND version IS NULL
                         AND description = '<< Flyway Schema Creation >>'
                         AND type = 'SCHEMA'
                         AND script = '"thesis"'
                         AND checksum IS NULL
                         AND success
                   ),
                   COUNT(*) FILTER (
                       WHERE installed_rank = 1
                         AND version = '20'
                         AND description = 'campuscore supabase baseline'
                         AND type = 'SQL_BASELINE'
                         AND script = 'B20__campuscore_supabase_baseline.sql'
                         AND checksum = 1841726166
                         AND success
                   ),
                   COUNT(*) FILTER (
                       WHERE installed_rank = 2
                         AND version = '21'
                         AND description = 'persist enrollment registration round'
                         AND type = 'SQL'
                         AND script = 'V21__persist_enrollment_registration_round.sql'
                         AND checksum = -249127582
                         AND success
                   )
              FROM %I.flyway_schema_history
        $query$, schema_name)
           INTO total_count, schema_creation_count, reviewed_baseline_count, reviewed_v21_count;
        IF NOT (
            (total_count = 1 AND schema_creation_count = 1 AND reviewed_baseline_count = 0 AND reviewed_v21_count = 0)
            OR (total_count = 2 AND schema_creation_count = 1 AND reviewed_baseline_count = 1 AND reviewed_v21_count = 0)
            OR (total_count = 3 AND schema_creation_count = 1 AND reviewed_baseline_count = 1 AND reviewed_v21_count = 1)
        ) THEN
            RAISE EXCEPTION USING
                ERRCODE = '55000',
                MESSAGE = 'The CampusCore Supabase baseline requires one of the allowlisted Flyway histories: marker-only, marker+B20, or marker+B20+V21';
        END IF;
        IF total_count = 1 THEN
            SELECT EXISTS (
                SELECT 1
                  FROM pg_namespace n
                 WHERE n.nspname IN ('academic', 'assistant', 'campuscore_auth', 'engagement', 'notifications', 'thesis')
                   AND (
                       n.nspname <> 'thesis'
                       OR EXISTS (
                           SELECT 1
                             FROM pg_class c
                            WHERE c.relnamespace = n.oid
                              AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
                              AND NOT (c.relname = 'flyway_schema_history' AND c.relkind IN ('r', 'p'))
                       )
                       OR EXISTS (SELECT 1 FROM pg_proc p WHERE p.pronamespace = n.oid)
                       OR EXISTS (
                           SELECT 1 FROM pg_type t
                            WHERE t.typnamespace = n.oid AND t.typtype IN ('d', 'e', 'm', 'r')
                       )
                   )
            ) INTO unexpected_application_state;
            IF unexpected_application_state THEN
                RAISE EXCEPTION USING
                    ERRCODE = '55000',
                    MESSAGE = 'The CampusCore Supabase baseline refuses pre-existing application objects';
            END IF;
        END IF;
    END LOOP;
END $$;
