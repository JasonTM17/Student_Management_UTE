# Thesis demo-data package

Operations package that fills the sparse thesis registration rounds with
realistic, internally consistent demo rows so the product exercises every
workflow state. It is NOT a Flyway migration and NOT a startup seeder — it only
runs when an operator runs it, against a connection they supply.

Designed and proven against a read-only logical dump of the production
database restored on a scratch Postgres (flyway 142 required).

## What it writes

| Table | Rows | Marker |
|---|---|---|
| thesis.thesis_topic | 17 | `ed1a0000-…-0001…` uuid namespace |
| thesis.thesis_topic_supervisor | 18 | same namespace |
| thesis.thesis_group | 20 | same namespace |
| thesis.thesis_group_member | 64 | same namespace |
| thesis.thesis_council | 4 | same namespace |
| thesis.thesis_council_member | 16 | same namespace |
| thesis.thesis_council_topic | 16 | same namespace |
| thesis.thesis_topic_score | 62 | same namespace |
| thesis.thesis_group_report | 17 | same namespace |
| notifications.notification | 107 | `demo-th-notif-*` ids |
| campuscore_audit."AdminAudit" | 6 | `demo-th-audit-*` ids, action `THESIS_DEMO_DATA_ENRICHED` |

Plus guarded column updates on seven pre-existing rows (three
`results_published_at` round stamps, three `gvpb_lecturer_id` reviewer
assignments, one `gvpb_waived_*` waiver set — each with `version` +
`updated_at`). Before any guarded update fires, apply.sql snapshots the
column's current value into a temp `_demo_before` table and persists it as
JSON in audit row `demo-th-audit-000`.`"beforeState"` (23 column-images).
`rollback.sql` restores each column only while its current value still equals
the value apply wrote; an application write in between makes that column a
`skipped_drift` in the rollback report and it is left untouched.

State coverage: published rounds with graded topics (final scores from council
averages), a counter-review failure, a withdrawn topic, a registration-phase
round with a size-exception roster, a rejected roster, a cancelled group with
topic reassignment, a pending submission, counter-review scores with comments,
a review waiver, council seats and topic assignments, database-stored report
blobs, and archived/rejected topic catalogue states. The untouched rounds
(cancelled draft round, proposals-published round, and the already-rich open
catalogue) are left exactly as they are.

Every referenced person is a seeded demo profile (`student-profile-*`,
`lecturer-profile-*`, `*-user-*` on `@campuscore.edu` / `@campuscore.demo`
mailboxes, permanently locked by `DemoAccountGate`). `manifest.sql` lists the
entire referenced set; `verify.sql` asserts no real account is referenced.

## Safety properties

- **Guarded**: `apply.sql` aborts unless flyway ≥ 142, the five touched rounds
  still match reconnaissance (status + bounded row counts), the structural rows
  it reuses exist, no partial marker state is present, none of the four known
  real accounts is referenced, and every baseline column it would write is
  still `NULL` (a pre-existing reviewer, waiver, or publish stamp is never
  overwritten).
- **One transaction**: the script `BEGIN`s and either `COMMIT`s or `ROLLBACK`s
  (dry-run). Any error aborts everything. Do NOT pass `-1`; the file manages
  its own transaction. `lock_timeout`/`statement_timeout` are set at the top
  of apply, verify and rollback (5 s / 120 s).
- **Idempotent**: deterministic marker ids + `ON CONFLICT` / existence guards;
  a second run inserts zero rows, and the stored before-image is preserved
  (never overwritten with an empty image).
- **Synthetic-only**: no writes reference accounts outside the manifest; the
  four known real accounts are blocked by explicit id, not by email domain. No
  real emails, names, companies, phones, or external URLs.
- **Reversible**: `rollback.sql` first runs a PREFLIGHT inside the same
  transaction that enumerates every FK into the marker parent tables
  (`thesis_topic`, `thesis_group`, `thesis_council`) from `pg_constraint` and
  counts non-marker rows referencing marker rows. If any exist it ABORTS with
  per-table counts and deletes nothing — application-owned rows are never
  removed. When the way is clear it restores the before-image conditionally,
  then deletes only marker rows. With no post-apply application writes the
  restore is exact (all table hashes equal the pre-apply baseline).

## Usage

Connection comes from the environment only:

```bash
export THESIS_DEMO_DB_URL='postgresql://user:pass@host:5432/db'
# or PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD (+ optional PSQL_BIN)
```

With the bundled runner (prints counts only):

```bash
node run.mjs dry-run    # planned inserts, rolled back
node run.mjs apply      # real apply
node run.mjs verify     # 49 SELECT-only integrity checks, all PASS expected
node run.mjs counts     # marker row counts only
node run.mjs rollback   # remove marker rows, revert guarded updates
node run.mjs manifest   # synthetic-account manifest + resolvability check
```

Raw psql equivalent:

```bash
psql "$THESIS_DEMO_DB_URL" -v ON_ERROR_STOP=1 -v dry_run=1 -f apply.sql
psql "$THESIS_DEMO_DB_URL" -v ON_ERROR_STOP=1 -v dry_run=0 -f apply.sql
psql "$THESIS_DEMO_DB_URL" -P pager=off -f verify.sql
psql "$THESIS_DEMO_DB_URL" -v ON_ERROR_STOP=1 -f rollback.sql
```

`verify.sql` prints one row per check with a `verdict` column; any `FAIL` line
means the package or baseline drifted — investigate before shipping.

## Proof on a disposable copy

Executed on a scratch database restored from a fresh read-only `pg_dump` of
the production application database (flyway 142, ~22 MB):

- dry-run → all inserts/updates executed, `ROLLBACK` (zero residue)
- apply → `COMMIT`; verify → 49/49 PASS; apply again → `INSERT 0 0` everywhere
- simulated app activity (non-marker score, council-topic, group member,
  report on marker rows) → rollback **aborted** with per-table counts,
  nothing deleted; after removing those rows rollback succeeded
- drift test: a guarded topic's reviewer/version/updated_at changed after
  apply → rollback reported `skipped_drift` for those 3 columns and restored
  the other 20
- clean apply → rollback → all 12 touched-table hashes identical to the
  pre-apply baseline (exact restoration, including `version`/`updated_at`)
- guard test: pre-existing reviewer on a baseline topic → apply aborted,
  zero marker rows written

## Production application (separate gated release — do not run from here)

1. Snapshot first: `pg_dump -n thesis -n notifications -Fc` of the production
   database, then verify the dump by restoring it locally.
2. `node run.mjs dry-run` — confirm planned counts match this README.
3. `node run.mjs apply` — one transaction; a few seconds at most (row-count
   inserts, no table rewrites; locks are ordinary row locks on inserted rows
   plus the three `results_published_at` row updates).
4. `node run.mjs verify` — every row must read PASS.
5. Public smoke: open the three published rounds' pages and confirm topics,
   councils, results and report downloads render.
6. If anything is wrong: `node run.mjs rollback`. It aborts (deleting
   nothing) if the application has since written rows that reference marker
   rows — remove those references first. Columns the application changed
   after apply are reported as `skipped_drift` and left as the app set them.
