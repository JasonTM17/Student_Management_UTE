# Student_Management hosted synchronization evidence

This directory is a redacted, commit-bound manifest of the 2026-08-25
schema-only synchronization. It contains no access token, database password,
database URL, user data, session, challenge token, Mailpit message, or local
`.env` content. The Git commit containing this directory is the immutable
provenance boundary; the source SQL backup and JSON snapshots remain outside
the repository under an operator-controlled retention directory. The manifest
records portable artifact IDs and hashes only; machine-specific paths are
intentionally redacted.

## Target identity

| Field | Observed value |
| --- | --- |
| Project name | `Student_Management` |
| Project ref | `kbptwmwitojjjwvwckom` |
| Region | `ap-south-1` |
| Project status | `ACTIVE_HEALTHY` |
| PostgreSQL | `17.6` (engine major 17) |
| Schema authority | Flyway `11.7.2`, reviewed B20 baseline plus V21 successor |

The Healthcare connector uses a different project ref and was not used for
this write. The migration was performed through the verified database pooler
with SSL required. The short-lived JIT mapping and JIT feature were revoked
and disabled after postflight; SSL enforcement remains enabled.

## Observed apply result

- Preflight found no CampusCore schema, table, or Flyway history.
- B20 was the hosted schema baseline and V21 was then applied as the reviewed
  forward successor. The exact `thesis.flyway_schema_history` now contains the
  schema marker, B20 checksum `1841726166`, and V21 checksum `-249127582`.
  The Supabase migration record is `20260825155849`.
- Six CampusCore schemas were created: `campuscore_auth`, `academic`,
  `thesis`, `assistant`, `engagement`, and `notifications`.
- Postflight found 48 application tables and two private-auth compatibility
  views. Every application table is empty except the three expected Flyway
  history rows (schema creation, B20 baseline and V21 successor).
- The managed `auth`, `storage`, and `realtime` relation/owner snapshot was
  unchanged: 36 managed relations before and after, owned by the platform.
- Security advisors returned zero SQL lints. The current advisor also reports
  a critical RLS finding for all 48 CampusCore tables because the tables are
  intentionally direct-server tables with RLS disabled. RLS/policy design is
  not auto-applied: enabling it without policies would block access, so this
  remains a user decision and a production HOLD. Performance advisors returned
  66 INFO-only notices for an empty database (`unindexed_foreign_keys` and
  `unused_index`); workload tuning is deferred until data exists.

## Boundary and rollback status

This is an infrastructure-only schema initialization, not application
readiness or production cutover. No local users, catalog rows, knowledge
documents, enrollment activity, sessions, challenges, tokens, credentials, or
Mailpit data were uploaded. Spring remains the authentication authority.

The hosted project had PITR/backups unavailable on its current plan and no
restore point was available during this window. A vanilla PostgreSQL 17
restore attempt of the Supabase logical schema dump was intentionally stopped
at the Supabase-only `supabase_vault` extension (not installed in vanilla
PostgreSQL); see `restore-rehearsal.md`. Consequently hosted rollback and
restoreability remain `HOLD/NOT_PROVEN`. Do not call this a production release
or perform traffic cutover until a plan-supported backup/restore rehearsal is
available.

See the operator runbook at
`docs/integrations/supabase-database.md` for the guarded procedure and
secret-handling rules.

The hosted B20 apply was executed from the sync SHA recorded in `manifest.json`.
V21 was applied later from the reviewed successor candidate after the local
V21/migration/concurrency gates passed. The manifest also records the later
auth-hardening SHA and confirms that the V20/B20 artifact and external hashes
are unchanged; verify ancestry with `git merge-base` before relying on the
bundle.
