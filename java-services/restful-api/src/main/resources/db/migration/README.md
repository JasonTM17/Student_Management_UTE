# Flyway migrations — rules for changing this directory

The chain in `db/migration/` is the runtime schema authority (V1…, numbering
gaps are recorded in the file header of the affected version; V56 is an
intentional numbering gap — the file never existed). A parallel, trimmed H2
fixture chain lives in `src/test/resources/db/migration-h2/` with its own
numbering; both chains are covered by the immutability rule below.

## 1. A merged migration is immutable

Never edit a migration that has been merged to `main` and applied by any
environment (local stacks count). Flyway validates stored checksums on every
boot (`validate-on-migrate` defaults to true), so editing an applied file turns
the next startup of that environment into a hard failure — and it silently
diverges what production ran from what git says the version is.

CI enforces this: the `compose` job fails when a diff against `origin/main`
touches an existing file under `db/migration/` or `db/migration-h2/`.

## 2. Corrections are new forward migrations

Fix a bad migration with `V<n+1>__what_it_fixes.sql`, following the existing
patterns: exact-address/idempotent data fixes (see V48/V50/V77/V82), `IF NOT
EXISTS` guards for DDL backfills (see V81). Write the header comment so the
next reader knows which earlier version it corrects and why.

## 3. If an environment already holds stale checksums

Twelve migrations were edited after creation during early development
(V1, V2, V3, V8, V25, V34, V52, V54, V64, V65, V68, V73), so long-lived
databases that applied the pre-edit bytes hold stale checksums and will refuse
the next boot. Repair per environment, before its next deploy:

```bash
# 1. Inventory what the environment actually applied (keep the output).
psql "$DB_URL" -c "SELECT installed_rank, version, checksum, success
                   FROM thesis.flyway_schema_history ORDER BY installed_rank"

# 2. Confirm the drift is the known kind (validation names the file).
mvn -f java-services/pom.xml flyway:validate -Dflyway.url="$DB_URL"

# 3. Rewrite stored checksums to match current files. repair performs no DDL
#    and touches no data.
mvn -f java-services/pom.xml flyway:repair -Dflyway.url="$DB_URL"

# 4. Re-run validate, then boot the service.
```

Fresh clones and CI need nothing: a clean replay of V1…V<latest> matches the
files in git by construction. Do **not** "fix" drift by re-editing files to
restore old checksums (that breaks fresh clones) and do **not** disable
`validateOnMigrate` (it is the alarm that catches genuine future drift).
