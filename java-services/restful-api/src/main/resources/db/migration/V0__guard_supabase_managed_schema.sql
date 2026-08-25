-- This legacy migration stream must never run against a Supabase project.
-- The application-level FlywayMigrationStrategy performs the same check before
-- any migration; this SQL guard also protects direct Flyway CLI invocation.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM pg_namespace n
          JOIN pg_roles r ON r.oid = n.nspowner
         WHERE n.nspname = 'auth' AND r.rolname = 'supabase_admin'
    )
    AND EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'storage')
    AND EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'realtime') THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'Legacy CampusCore migrations are blocked on Supabase-managed schemas; use db/supabase-baseline';
    END IF;
END $$;
