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
`updated_at`).

Before any guarded update fires, apply.sql snapshots the column's current
value (timestamps as ISO 8601 with offset — session-format independent) and
stores a v2 before-image in TWO durable places:

1. audit row `demo-th-audit-000`.`"beforeState"` (JSON `cols` + `rows`), and
2. an md5-checksum-protected copy in the `thesis.thesis_topic` table comment
   (`thesis-demo-data v2 md5:<hash>\n<json>`), which survives audit pruning.
   rollback clears it; a pre-existing foreign comment makes apply refuse.

`cols` holds the 23 guarded column-images (`before`/`applied` per column).
`rows` holds the whole-row md5 of every marker row, so rollback detects any
in-place edit or deletion of demo data. `rollback.sql` reads the audit copy
first, falls back to the comment copy (verifying the checksum), requires both
to agree when both exist, and ABORTS when neither is usable — it never deletes
marker rows without a validated image. It restores each column only while its
current value still equals the value apply wrote; an application write in
between makes that column a `skipped_drift` in the rollback report and it is
left untouched. If both copies are ever lost, rebuild the image with
`recover-before-image.sql` (see *Recovery* below).

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
mailboxes). **These accounts may be loginable**: `DemoAccountGate`
(`@Profile("persistence & !test")`) locks or activates them per environment
(`DEMO_ACCOUNTS_ENABLED` + role scope) — in the reference production dump nine
of the nineteen referenced staff accounts were ACTIVE. The package therefore
does NOT rely on account locking for safety; it relies on the lock / guard /
preflight / hash / abort rules below. `manifest.sql` lists the entire
referenced set; `verify.sql` asserts no real account is referenced.

## Safety properties

- **Guarded**: `apply.sql` aborts unless flyway ≥ 142, the five touched rounds
  still match reconnaissance (status + bounded row counts), the structural rows
  it reuses exist, no partial marker state is present, none of the four known
  real accounts is referenced, and every baseline column it would write is
  still `NULL` (a pre-existing reviewer, waiver, or publish stamp is never
  overwritten).
- **Locked while it runs**: both `apply.sql` and `rollback.sql` take
  fixed-order `SHARE ROW EXCLUSIVE` table locks on every table they touch,
  before any check — the weakest mode that blocks application
  `INSERT/UPDATE/DELETE` (which need `ROW EXCLUSIVE`) and serializes two
  concurrent package runs, while plain `SELECT`s stay untouched. There is no
  check-then-act window: a concurrent application write either commits first
  (and the post-lock preflight sees it) or waits for the locks and is never
  silently removed. `lock_timeout = 5 s` makes the package abort cleanly
  instead of queueing behind an in-flight write — so the app waits behind us
  for at most the remainder of the script (sub-second locally; a few seconds
  worst-case over a remote connection). Run it in a quiet maintenance window.
- **One transaction**: the script `BEGIN`s and either `COMMIT`s or `ROLLBACK`s
  (dry-run). Any error aborts everything. Do NOT pass `-1`; the file manages
  its own transaction. `lock_timeout`/`statement_timeout` are set at the top
  of apply, verify and rollback (5 s / 120 s), and `TimeZone`/`DateStyle` are
  pinned to UTC/ISO so the image serializes identically under any client
  session settings.
- **Idempotent, strictly**: deterministic marker ids + `ON CONFLICT` /
  existence guards; a second run on a fully applied database prints
  `already applied — no-op, 0 rows written`, reports drift on the guarded
  columns against the recorded applied values, and writes NOTHING — guarded
  updates never re-fire, so post-apply application changes (retracted rounds,
  removed waivers) are never overwritten. Partial marker states are refused
  (run rollback first, then re-apply).
- **Synthetic-only**: no writes reference accounts outside the manifest; the
  four known real accounts are blocked by explicit id, not by email domain. No
  real emails, names, companies, phones, or external URLs.
- **Reversible, fail-closed**: `rollback.sql` holds the table locks while it
  (a) enumerates every FK into the marker parent tables (`thesis_topic`,
  `thesis_group`, `thesis_council`) from `pg_constraint` — including any
  referencing table outside the static lock set — and counts non-marker rows
  referencing marker rows, plus non-marker counter-review scores on the three
  guarded topics; any hit ABORTS with per-table counts and deletes nothing;
  (b) requires a validated before-image (audit copy, or checksum-protected
  comment copy, or agreement of both); (c) re-hashes every marker row and
  compares with the apply-time hashes — any in-place edit or deletion ABORTS
  with per-table counts. Only then does it conditionally restore the image
  and delete marker rows. With no post-apply application writes the restore
  is exact (all 12 touched-table hashes equal the pre-apply baseline).

## Usage

Connection comes from the environment only:

```bash
export THESIS_DEMO_DB_URL='postgresql://user:pass@host:5432/db'
# or PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD (+ optional PSQL_BIN)
```

With the bundled runner (prints counts only):

```bash
node run.mjs dry-run    # planned inserts, rolled back
node run.mjs apply      # real apply (no-op when already applied)
node run.mjs verify     # 54 SELECT-only integrity checks, all PASS expected
node run.mjs counts     # marker row counts only
node run.mjs rollback   # remove marker rows, revert guarded updates
node run.mjs recover    # rebuild the before-image when BOTH copies are gone
node run.mjs manifest   # synthetic-account manifest + resolvability check
node selftest.mjs       # full regression suite (scratch DB only, see below)
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

## Recovery — before-image lost on both copies

`rollback.sql` refuses to run while marker rows exist but no valid
before-image copy survives (audit row deleted AND comment copy cleared or
corrupted). To unblock deliberately:

```bash
node run.mjs recover   # then: node run.mjs rollback
```

`recover-before-image.sql` takes the same locks, refuses when no marker rows
exist or a usable copy still exists, then rebuilds the image: the `cols`
section restores the KNOWN baseline values of the 23 guarded columns of the
documented Oct-24 production snapshot, and `rows` re-hashes the marker rows
as they currently are — so recovery assumes the marker rows are still in
their applied state. The rebuilt image feeds the same conditional-restore
rule, so post-apply application changes are still never clobbered.

## Regression suite (scratch DB only)

`selftest.mjs` runs the whole safety matrix against a disposable scratch
database restored from the production fixture — never against a live DB:
it applies and rolls back repeatedly, simulates application writes
(referencing rows, a sleeping insert that commits mid-rollback, a write that
arrives during the lock window, an in-place report edit), deletes each
before-image copy separately and together, runs apply/rollback under hostile
`DateStyle`/`TimeZone` session settings, and compares whole-table md5 sets of
all 12 touched tables before and after. It prints one PASS/FAIL line per
check plus counts, and exits non-zero on any FAIL.

```bash
createdb enr_scratch --template=<restored-fixture-db>
PGDATABASE=enr_scratch node selftest.mjs
```

## Proof on a disposable copy

Executed on a scratch database restored from a fresh read-only `pg_dump` of
the production application database (flyway 142, ~22 MB):

- dry-run → all inserts/updates executed, `ROLLBACK` (zero residue by hashes)
- apply → `COMMIT`; verify → 54/54 PASS; second apply → `already applied`,
  zero writes
- apply under `DateStyle=German`+`TimeZone=Asia/Tokyo`, rollback under
  `DateStyle=SQL,DMY`+`TimeZone=Pacific/Kiritimati` → identical restore
- race: an app transaction that commits a referencing score mid-rollback →
  rollback waits for the lock (~2 s), then **aborts** with per-table counts;
  the app row survives; a write arriving during our lock window is rejected
  by FK after we finish
- simulated app activity (non-marker score, council-topic, group member,
  report on marker rows) → rollback **aborted** with per-table counts,
  nothing deleted; after removing those rows rollback succeeded
- in-place edit of a marker report row → rollback **aborted** on the
  content-hash check; reverting the edit allows rollback to proceed
- audit-row image deleted → rollback completes from the checksum-protected
  comment copy; BOTH copies deleted → rollback **aborts** until
  `recover-before-image.sql` rebuilds it
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
3. `node run.mjs apply` — one transaction in a quiet window; it first takes
   `SHARE ROW EXCLUSIVE` locks on the 12 package tables (expected hold time
   sub-second to a few seconds; application writes block behind it for at
   most that long, reads never block). If the locks are not free within 5 s
   it aborts having changed nothing — retry when the app is idle.
4. `node run.mjs verify` — every row must read PASS.
5. Public smoke: open the three published rounds' pages and confirm topics,
   councils, results and report downloads render.
6. If anything is wrong: `node run.mjs rollback`. Same lock behaviour and
   hold time. It aborts — deleting nothing — when (a) application rows now
   reference marker rows (remove them first), (b) a marker row was edited or
   deleted in place (inspect first), or (c) no valid before-image copy
   survives (`node run.mjs recover` first). Columns the application changed
   after apply are reported as `skipped_drift` and left as the app set them.
