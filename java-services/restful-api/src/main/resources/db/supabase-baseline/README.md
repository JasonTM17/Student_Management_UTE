# Supabase baseline location

This location is opt-in. A hosted Supabase runtime must set:

```text
FLYWAY_LOCATIONS=classpath:db/supabase-baseline
```

`B20__campuscore_supabase_baseline.sql` represents the schema after the normal
V1 through V20 chain. On an empty hosted environment, Flyway resolves B20 and
then the explicitly reviewed V21 successor. Existing installations keep their
V-history and use only `db/migration` to apply V21 normally.

The application has a fail-closed safety strategy. Profile-independent
`beforeValidate` and `beforeMigrate` callbacks reject unknown managed-platform
schema/role markers and any `auth` schema outside the reviewed historical local
signatures. The callbacks accept only the reviewed Flyway history states:
marker-only, marker+B20, or marker+B20+V21. The hosted safety switch accepts
exactly the single allowlisted baseline location above; a combined
legacy+baseline location is rejected.
This avoids both managed-schema mutation and lower-version validation drift on
existing V12/V18/V20 histories.

The baseline contains schema only. It never creates or changes Supabase-owned
`auth`, `storage`, `realtime`, or `supabase_migrations` objects and contains no
users, password hashes, sessions, challenges, rate buckets, chat history,
Mailpit content, or demo/test rows.

Do not enable this location on a database that already has CampusCore V-history.
Do not run `CampusCoreSupabaseBaselinePostgresIT` against a hosted database; its
managed-schema sentinels are intentionally local test fixtures. Follow
`docs/integrations/supabase-database.md` for the guarded hosted workflow.
