# Supabase database synchronization

This runbook synchronizes the CampusCore schema to one explicitly verified
Supabase project named `Student_Management`. It does not switch production
traffic, replace Spring authentication with Supabase Auth, or authorize changes
to any other project.

## Safety boundary

The first hosted synchronization is schema-only. Do not copy local users,
password hashes, roles assigned to demo users, refresh sessions, auth
challenges, rate-limit buckets, enrollment activity, assistant conversations,
Mailpit messages, tokens, credentials, or test rows. Curated/reference data is
a later, separately reviewed allowlist; an empty allowlist is the safe default.

Supabase owns `auth`, `storage`, `realtime` and `supabase_migrations`.
CampusCore owns only:

- `campuscore_auth`
- `academic`
- `thesis`
- `assistant`
- `engagement`
- `notifications`

V20 moves legacy application-owned auth objects to `campuscore_auth`. The
opt-in `B20__campuscore_supabase_baseline.sql` creates the six CampusCore
schemas without rows, grants, role changes or Supabase-managed objects.

## Local proof before a hosted write

Use two disposable PostgreSQL 15 databases. Point the following integration
test only at those local databases; it deliberately creates managed-schema
sentinels and must never receive a hosted URL.

```powershell
$env:CAMPUSCORE_SUPABASE_SOURCE_POSTGRES_URL = 'jdbc:postgresql://127.0.0.1:<port>/<source-db>'
$env:CAMPUSCORE_SUPABASE_BASELINE_POSTGRES_URL = 'jdbc:postgresql://127.0.0.1:<port>/<baseline-db>'
$env:CAMPUSCORE_SUPABASE_POSTGRES_USER = '<local-user>'
$env:CAMPUSCORE_SUPABASE_POSTGRES_PASSWORD = '<local-password>'
.\mvnw.cmd -q -f java-services/pom.xml -pl restful-api -am `
  "-Dtest=CampusCoreSupabaseBaselinePostgresIT" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

The gate compares the complete application object signature against fresh
V1-V20, preserves the sentinel schemas, proves the B20 Flyway history and
asserts zero application rows.

## Hosted preflight

Stop before migration unless every item is true:

1. The authenticated organization lists exactly the intended project, and its
   display name and immutable project ref are recorded together.
2. The target is not inferred from the currently configured MCP connector,
   repository URL or a similar project name.
3. A current logical backup has been downloaded outside the repository and its
   hash recorded. For a populated project, confirm the Supabase backup/PITR
   status and rehearse restore in a disposable target when the plan requires it.
4. Read-only inventory proves the six CampusCore schemas are absent or empty.
5. A before snapshot of objects and owners in `auth`, `storage`, `realtime` and
   `supabase_migrations` is retained for a post-apply comparison.
6. The exact B20 checksum, candidate commit and Flyway version are frozen.

Use the Supabase CLI only after confirming the project ref. Keep database URLs,
passwords and access tokens in an approved secret environment; never echo them,
place them in command output/evidence, or commit them. The official backup and
migration guides are linked from `docs/RELEASE.md`.

## Hosted Flyway apply

Flyway 11.7.2 is the migration authority. Set the three secret variables in
the current process, resolve the database-resource directory, then run `info`
before `migrate`. Passing `-e NAME` to Docker forwards a value without placing
the secret itself in this command.

```powershell
$env:FLYWAY_URL = 'jdbc:postgresql://<verified-host>:5432/postgres?sslmode=require'
$env:FLYWAY_USER = '<verified-database-user>'
$env:FLYWAY_PASSWORD = '<database-password>'
$dbMigrations = (Resolve-Path '.\java-services\restful-api\src\main\resources\db').Path

docker run --rm `
  -e FLYWAY_URL -e FLYWAY_USER -e FLYWAY_PASSWORD `
  -v "${dbMigrations}:/flyway/sql:ro" `
  redgate/flyway:11.7.2 `
  -locations='filesystem:/flyway/sql/migration,filesystem:/flyway/sql/supabase-baseline' `
  -defaultSchema=thesis -schemas=thesis -cleanDisabled=true `
  -baselineOnMigrate=false -validateMigrationNaming=true info
```

The `info` result must show B20 as the selected baseline and V1-V20 ignored.
Only then run the same command with `migrate`, followed by `validate` and
`info`. Do not run `clean`, `repair`, `baseline`, or an unreviewed SQL editor
paste. Existing CampusCore V-history databases must use only
`classpath:db/migration` and apply V20 normally; they must not opt into B20.

## Post-apply acceptance

The hosted synchronization is a PASS only when read-only evidence proves:

- Flyway history contains one successful `SQL_BASELINE` at version 20 and no
  other versioned CampusCore migration;
- all expected application tables, views, constraints and indexes exist;
- all application base tables have zero rows;
- no CampusCore table/view exists in managed `auth`;
- the before/after managed-schema object/owner snapshot is unchanged;
- Supabase security/performance advisors introduce no new CampusCore finding;
- no secret or raw token appears in the migration log or committed evidence.

If any invariant fails, stop application rollout. Preserve logs without
credentials and restore the verified backup rather than improvising a reverse
migration. Spring traffic remains local until a separate production-cutover
decision is approved.

After the task, remove the three `FLYWAY_*` secret variables from the process.
Do not delete the backup, project, branch, Docker volume or remote database as
part of repository cleanup.

## Completed Student_Management synchronization (2026-08-25)

The guarded hosted run was completed only after the local B20 parity gate,
exact project identity and a schema-only logical backup were proven. The
verified target is `Student_Management` (`kbptwmwitojjjwvwckom`) in
`ap-south-1`, PostgreSQL 17.6. The before-backup and metadata snapshots are
retained outside this repository; the backup SHA-256 is
`F42192E9C5951A8302F072A1184C58A509FF6900D4EA3017EFF0348837240E55`.

Temporary Supabase database access used the project owner's PAT through the
shared pooler with `sslmode=require` and `options=-c jit=true`. SSL enforcement
was enabled as the prerequisite; the short-lived JIT `postgres` mapping and
the JIT feature were revoked/disabled immediately after migration. SSL
enforcement remains enabled as the safer project setting. The PAT itself was
never placed in a URL, file, log, migration or committed example.

Flyway 11.7.2 applied `B20__campuscore_supabase_baseline.sql` as the only
remote migration. Postflight evidence shows six CampusCore schemas, 48
application tables, two private-auth compatibility views, zero rows in every
application table except the two expected Flyway history rows, and no
CampusCore object in managed `auth`. A before/after comparison found the 36
managed relations and owners unchanged. Supabase security advisors returned
zero lints; the 66 performance notices are INFO-only empty-database
unindexed-FK/unused-index observations and are deferred until workload data
exists. Do not copy local demo users, sessions, challenges, Mailpit messages,
tokens or credentials in a later seed wave without a separate allowlist and
review.
