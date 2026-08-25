-- Repeat the guard at the migration boundary so operators cannot bypass the
-- protection by disabling validation. Keep this callback read-only.
DO $$
DECLARE
    auth_owner text;
    auth_exists boolean;
    auth_has_relations boolean;
    auth_has_unknown_relations boolean;
    auth_has_functions boolean;
    private_is_trusted boolean;
BEGIN
    IF EXISTS (
        SELECT 1
          FROM pg_namespace
         WHERE nspname IN ('storage', 'realtime', 'supabase_migrations')
            OR nspname LIKE 'supabase\_%' ESCAPE '\'
    ) OR EXISTS (
        SELECT 1
          FROM pg_roles
         WHERE rolname LIKE 'supabase\_%' ESCAPE '\'
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '55000',
            MESSAGE = 'Legacy CampusCore migrations are blocked by a Supabase-managed marker; use the exact db/supabase-baseline location';
    END IF;

    SELECT TRUE, pg_get_userbyid(n.nspowner)
      INTO auth_exists, auth_owner
      FROM pg_namespace n
     WHERE n.nspname = 'auth';

    IF auth_exists THEN
        SELECT EXISTS (
                   SELECT 1
                     FROM pg_class c
                     JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'auth'
                      AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
               ),
               EXISTS (
                   SELECT 1
                     FROM pg_class c
                     JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'auth'
                      AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
                      AND c.relname NOT IN (
                          'User', 'Role', 'Permission', 'UserRole',
                          'RolePermission', 'Student', 'Lecturer',
                          'Session', 'AuthChallenge', 'AuthRateLimitBucket'
                      )
               ),
               EXISTS (
                   SELECT 1
                     FROM pg_proc p
                     JOIN pg_namespace n ON n.oid = p.pronamespace
                    WHERE n.nspname = 'auth'
               )
          INTO auth_has_relations, auth_has_unknown_relations, auth_has_functions;

        SELECT EXISTS (
                   SELECT 1
                     FROM pg_namespace n
                    WHERE n.nspname = 'campuscore_auth'
                      AND pg_get_userbyid(n.nspowner) = current_user
               )
               AND NOT EXISTS (
                   SELECT 1
                     FROM pg_class c
                     JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'campuscore_auth'
                      AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
                      AND c.relname NOT IN (
                          'User', 'Role', 'Permission', 'UserRole',
                          'RolePermission', 'Student', 'Lecturer',
                          'Session', 'AuthChallenge', 'AuthRateLimitBucket'
                      )
               )
               AND NOT EXISTS (
                   SELECT 1
                     FROM pg_proc p
                     JOIN pg_namespace n ON n.oid = p.pronamespace
                    WHERE n.nspname = 'campuscore_auth'
               )
               AND EXISTS (
                   SELECT 1
                     FROM pg_class c
                     JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'campuscore_auth'
                      AND c.relname = 'User'
                      AND c.relkind IN ('r', 'p')
               )
               AND EXISTS (
                   SELECT 1
                     FROM pg_class c
                     JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'campuscore_auth'
                      AND c.relname = 'Session'
                      AND c.relkind IN ('r', 'p')
               )
          INTO private_is_trusted;

        IF auth_owner IS DISTINCT FROM current_user
           OR auth_has_unknown_relations
           OR auth_has_functions
           OR (NOT auth_has_relations AND NOT private_is_trusted) THEN
            RAISE EXCEPTION USING
                ERRCODE = '55000',
                MESSAGE = 'Legacy CampusCore migrations are blocked by an auth schema that is not the reviewed local signature';
        END IF;
    END IF;
END $$;
