# Supabase baseline location

This location is opt-in. A hosted Supabase runtime must set:

```text
FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/supabase-baseline
```

`B20__campuscore_supabase_baseline.sql` represents the schema after the normal
V1 through V20 chain. On an empty CampusCore environment, Flyway applies B20
and ignores V1 through V20. Existing installations keep their V-history and
apply V20 normally.

The baseline contains schema only. It never creates or changes Supabase-owned
`auth`, `storage`, `realtime`, or `supabase_migrations` objects and contains no
users, password hashes, sessions, challenges, rate buckets, chat history,
Mailpit content, or demo/test rows.

Do not enable this location on a database that already has CampusCore V-history.
Do not run `CampusCoreSupabaseBaselinePostgresIT` against a hosted database; its
managed-schema sentinels are intentionally local test fixtures. Follow
`docs/integrations/supabase-database.md` for the guarded hosted workflow.
