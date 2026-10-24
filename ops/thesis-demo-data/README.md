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
stores a v3 before-image in TWO durable places:

1. audit row `demo-th-audit-000`.`"beforeState"` (JSON `format`/`cols`/
   `hashcols`/`rows`), and
2. an md5-checksum-protected copy in the `thesis.thesis_topic` table comment
   (`thesis-demo-data v3 md5:<hash>\n<json>`), which survives audit pruning.
   rollback clears it; a pre-existing foreign comment makes apply refuse.

`cols` holds the 23 guarded column-images (`before`/`applied` per column).
`hashcols` names the explicit, versioned column list each marker table is
hashed over — exactly the columns apply writes, minus volatile bookkeeping
the application legitimately touches (`version`, `updated_at`, notification
`is_read`/`read_at`, audit `createdAt`). `rows` holds a per-column canonical
md5 for every marker row (bytea as `encode(…,'hex')`, everything else via
`to_jsonb(col)#>>'{}'` under pinned session settings) — so a schema
`ADD COLUMN` to a marker table does NOT change the hashes, and an app edit
to a bookkeeping column is not misread as demo-data tampering.

`rollback.sql` reads the audit copy first, falls back to the comment copy
(verifying the checksum), requires both to agree when both exist, and ABORTS
when neither is usable — it never deletes marker rows without a validated
image. It then classifies every live marker row per table:

- **missing** (deleted by the application, e.g. a retracted round's
  notification recall): NOT a blocker — reported as `already removed by the
  application: N` and the remaining rows are rolled back;
- **differs** (a live marker row whose hashed columns changed): blocker;
- **extra** (a marker-id row the image never wrote): blocker.

Only rows that exist and differ, extras, or non-marker rows referencing
marker rows block the delete, all reported with per-table counts. It restores
each guarded column only while its current value still equals the value apply
wrote; an application write in between makes that column a `skipped_drift`
in the rollback report and it is left untouched. If both copies are ever
lost, rebuild the image with `recover-before-image.sql` (see *Recovery*).

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
  it reuses exist, none of the four known real accounts is referenced, and
  every baseline column it would write is still `NULL` (a pre-existing
  reviewer, waiver, council assignment, or publish stamp is never overwritten).
  Marker state is classified before anything writes:
  `absent` (zero markers) → apply; `full` or `app_deleted_subset` (live marker
  ids ⊆ image ids — the application deleted some, e.g. a retract) →
  `already applied` no-op writing nothing; `foreign_partial` (markers present
  but the image is missing, inconsistent, or live ids are not in it) → refuse
  with the recovery instruction.
- **Locked in application write order**: `apply.sql` and `rollback.sql` take
  `SHARE ROW EXCLUSIVE` locks in the SAME order the application writes —
  thesis business tables and dynamically discovered FK-referencing tables
  first, `notifications.notification` next, `campuscore_audit."AdminAudit"`
  last — so a concurrent app transaction (which writes thesis rows first and
  audit last) never forms a deadlock cycle against the package. There is no
  check-then-act window: a concurrent write either commits first (and the
  post-lock preflight sees it) or waits for the locks.
- **Package is the lock victim, never the app**: every lock is requested
  with `NOWAIT`, so the package never queues behind an application write or
  an autovacuum/ANALYZE worker — it can never be one edge of a wait-for
  cycle, no deadlock can form, and the application transaction is never
  killed; the package simply fails fast with nothing changed. `run.mjs`
  retries only lock failures — up to 8 attempts, fresh psql process and
  transaction per attempt, jittered backoff; it never holds locks while
  waiting, and reports `blocked by autovacuum/analyze` (counts only) when
  the conflicting side is a vacuum worker. Raw `psql -f` runs fail once
  with a clear message instead of retrying. Run it in a quiet maintenance
  window regardless.
- **One transaction**: the script `BEGIN`s and either `COMMIT`s or `ROLLBACK`s
  (dry-run). Any error aborts everything. Do NOT pass `-1`; the file manages
  its own transaction. `statement_timeout` is 120 s; `TimeZone`/`DateStyle`
  are pinned to UTC/ISO and `bytea_output`, `extra_float_digits`,
  `IntervalStyle`, `standard_conforming_strings`, and `client_encoding` are
  pinned too — UTF-8 end-to-end, with an explicit abort if either end of the
  session is not UTF-8, so the Vietnamese text can never be written or
  compared as mojibake. `run.mjs` invokes psql with `-X --no-psqlrc` and
  `PGCLIENTENCODING=UTF8` so a startup file or shell locale cannot change
  settings.
- **Idempotent, strictly**: deterministic marker ids + `ON CONFLICT` /
  existence guards; a second run on a fully applied database prints
  `already applied — no-op, 0 rows written`, reports drift on the guarded
  columns against the recorded applied values, and writes NOTHING — guarded
  updates never re-fire, so post-apply application changes (retracted rounds,
  removed waivers) are never overwritten. An app-deleted subset is also a
  no-op — apply never recreates rows the application removed.
- **Synthetic-only**: no writes reference accounts outside the manifest; the
  four known real accounts are blocked by explicit id, not by email domain. No
  real emails, names, companies, phones, or external URLs.
- **Reversible, fail-closed**: `rollback.sql` holds the table locks while it
  (a) enumerates every FK into ALL eleven package tables from
  `pg_constraint`, recursively following `ON DELETE CASCADE` edges so a
  future grandchild table is covered too, and refuses when the live
  inventory differs from the reviewed nine-key set —
  `thesis.thesis_council_member.council_id>thesis.thesis_council(c)`,
  `thesis.thesis_council_topic.council_id>thesis.thesis_council(c)`,
  `thesis.thesis_council_topic.topic_id>thesis.thesis_topic(c)`,
  `thesis.thesis_group_member.group_id>thesis.thesis_group(c)`,
  `thesis.thesis_group_report.group_id>thesis.thesis_group(r)`,
  `thesis.thesis_group.topic_id>thesis.thesis_topic(a)`,
  `thesis.thesis_topic_score.council_id>thesis.thesis_council(c)`,
  `thesis.thesis_topic_score.topic_id>thesis.thesis_topic(c)`,
  `thesis.thesis_topic_supervisor.topic_id>thesis.thesis_topic(c)` —
  then locks each referencing table and counts the application rows the
  delete would cascade through plus the rows pinning a doomed parent
  (matched by ctid, so a referencing table without an `id` column is
  covered), plus non-marker counter-review scores on the three guarded
  topics; any hit ABORTS with per-table counts and deletes nothing;
  (b) requires a validated before-image (audit copy, or checksum-protected
  comment copy, or agreement of both); (c) re-hashes every live marker row
  over the image's `hashcols` lists — a row that exists and differs, or a
  marker-id row absent from the image, ABORTS with per-table counts, while
  rows the application deleted are skipped and reported. Only then does it
  conditionally restore the image and delete marker rows. With no post-apply
  application writes the restore is exact (all 12 touched-table hashes equal
  the pre-apply baseline).

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
node run.mjs verify     # 59 SELECT-only integrity checks, all PASS expected
node run.mjs counts     # marker row counts only
node run.mjs rollback   # remove marker rows, revert guarded updates
node run.mjs recover    # rebuild the before-image when BOTH copies are gone
node run.mjs manifest   # synthetic-account manifest + resolvability check
node selftest.mjs       # full regression suite (scratch DB only, see below)
PGDATABASE=<template> node mutants.mjs   # guard-weakening mutation harness
```

`run.mjs` always invokes psql with `-X --no-psqlrc` and
`PGCLIENTENCODING=UTF8`, and retries lock-window failures
(`could not obtain lock` / `deadlock detected` / `lock timeout`)
up to 8 attempts with jitter — each attempt a fresh process and transaction.
`THESIS_DEMO_MAX_ATTEMPTS` overrides the cap. When a lock failure carries
SQLSTATE 55P03 the runner queries `pg_stat_activity`/`pg_locks` and says
whether the blocker looked like autovacuum/ANALYZE or another lock holder —
counts only, never query text or user data. If all attempts are exhausted,
check `pg_stat_activity` for autovacuum/ANALYZE workers (or other lock
holders) on the package tables before retrying.

Raw psql equivalent (no retry — one attempt each):

```bash
psql -X "$THESIS_DEMO_DB_URL" -v ON_ERROR_STOP=1 -v dry_run=1 -f apply.sql
psql -X "$THESIS_DEMO_DB_URL" -v ON_ERROR_STOP=1 -v dry_run=0 -f apply.sql
psql -X "$THESIS_DEMO_DB_URL" -P pager=off -f verify.sql
psql -X "$THESIS_DEMO_DB_URL" -v ON_ERROR_STOP=1 -f rollback.sql
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
exist or a usable copy still exists, then rebuilds the image from RECORDED
facts only — never from live marker content:

- `hashcols` and `rows` come from `expected-image.sql`, the recorded
  apply-time content of every marker row (generated from a verified clean
  apply against the production-derived fixture — it ships with the package);
- `cols` restores the KNOWN baseline values of the 23 guarded columns, and
  keeps an entry only while the live column still equals the value apply
  wrote — a column the application changed is dropped from the restore set
  and reported, never clobbered.

Because `rows` is rebuilt from expected content, not live content, a marker
row the application edited after apply (e.g. a re-uploaded report blob)
hashes differently from the recovered image and becomes a rollback BLOCKER —
recovery can never bless tampered live state, and a rollback that follows it
refuses with per-table counts instead of deleting the app's file. Recovery
prints expected-vs-live differences; inspect them before rolling back.

`expected-image.sql` is keyed to the documented baseline (flyway 142, the
marker row set above). If the dataset ever changes, regenerate it from a
verified clean apply on a production-derived scratch copy and re-run the
selftest — never edit it against live data.

## Regression suite (scratch DB only)

`selftest.mjs` runs the whole safety matrix against a disposable scratch
database restored from the production fixture — never against a live DB:
it applies and rolls back repeatedly, simulates application writes
(referencing rows, a sleeping insert that commits mid-rollback, a write that
arrives during the lock window, an in-place report edit, a dean retract that
deletes a round's marker notifications, a migration `ADD COLUMN`, volatile
`version`/`updated_at`/`is_read` bumps), deletes each before-image copy
separately and together, runs the recovery path and proves a re-uploaded
report survives, tampers with each image copy (disagreement, bad checksum),
runs apply/rollback under hostile `DateStyle`/`TimeZone`/`bytea_output`
session settings and under a `PGCLIENTENCODING=LATIN1` client, runs the
deterministic five-writer deadlock schedule (four staggered app holders +
an app transaction requesting `thesis_council`) proving the NOWAIT package
— never the app — gives up, proves a future `ON DELETE CASCADE` child table
makes rollback refuse on the FK inventory, proves a guarded column the app
drifted is restored as `skipped_drift` rather than overwritten, and
compares whole-table md5 sets of all 12 touched tables before and after.
It prints one PASS/FAIL line per check plus counts, and exits non-zero on
any FAIL. `SELFTEST_STOP_ON_FAIL=1` stops at the first failure (used by the
mutation harness).

```bash
createdb enr_scratch --template=<restored-fixture-db>
PGDATABASE=enr_scratch node selftest.mjs
```

`mutants.mjs` is the mutation harness: for each entry in its patch table (one
guard removal per guard in apply/rollback/recover) it clones the baseline
template database, applies the patch to a scratch copy of the package, runs
the selftest inside the copy, and prints `killed`/`SURVIVED`/`EQUIVALENT`
per mutant. Kill detection is structured — a kill needs a non-zero selftest
exit AND a real `FAIL ` detail line (or a parsed totals line with FAIL>0);
summary text like `0 FAIL` can never count. `control.identity_noop` replaces
a line by itself and must come back SURVIVED — if it is 'killed' the oracle
is broken and the harness exits non-zero, as it does for any surviving
non-equivalent mutant. Rerunnable:

```bash
PGDATABASE=<restored-fixture-template-db> node mutants.mjs
```

## Proof on a disposable copy

Executed on a scratch database restored from a fresh read-only `pg_dump` of
the production application database (flyway 142, ~22 MB):

- dry-run → all inserts/updates executed, `ROLLBACK` (zero residue by hashes)
- apply → `COMMIT`; verify → 59/59 PASS (the byte-level UTF-8 check was
  added in ENR5 — adjust the number if more checks are added); second apply
  → `already applied`, zero writes
- apply under `DateStyle=German`+`TimeZone=Asia/Tokyo`, rollback under
  `DateStyle=SQL,DMY`+`TimeZone=Pacific/Kiritimati`+`bytea_output=escape` →
  identical restore (hashes are session-independent)
- apply under `PGCLIENTENCODING=LATIN1` → correct UTF-8 bytes written (the
  pinned session encoding overrides the client), confirmed by the
  `encoding.vietnamese_utf8_bytes` check; a session that cannot be UTF-8
  aborts before writing
- dean retract simulation: round flipped to non-published + its 16
  `THESIS_RESULTS:<round>` marker notifications deleted → re-apply reports
  `already applied` (app-deleted subset, no-op, nothing recreated);
  rollback reports `already removed by the application: 16` and completes;
  the app's own round changes are left alone
- migration simulation: `ALTER TABLE … ADD COLUMN` on marker tables →
  rollback still succeeds (hashes cover an explicit column list)
- deadlock schedule: app transaction writes thesis → sleeps → writes audit
  last while rollback runs → the PACKAGE fails instantly on `NOWAIT` and
  the app commits; the five-writer schedule (four staggered app holders on
  council_member/council_topic/group/group_member + an app transaction
  requesting `thesis_council`) repeats 3× with the app never the victim;
  `node run.mjs rollback` retries and completes
- race: an app transaction that commits a referencing score mid-rollback →
  rollback **aborts** with per-table counts; the app row survives
- simulated app activity (non-marker score, council-topic, group member,
  report on marker rows) → rollback **aborted** with per-table counts,
  nothing deleted; after removing those rows rollback succeeded
- in-place edit of a marker report row → rollback **aborted** on the
  content-hash check; reverting the edit allows rollback to proceed
- audit-row image deleted → rollback completes from the checksum-protected
  comment copy; tampered comment (checksum mismatch) or disagreeing copies →
  rollback **aborts**; BOTH copies deleted → rollback **aborts** until
  `recover-before-image.sql` rebuilds it
- recovery + re-upload: both copies deleted, image recovered, then the app
  re-uploads a report blob → rollback **aborts** on the hash check and the
  app's file survives (recovery cannot bless live tampering)
- drift test: a guarded topic's reviewer/version/updated_at changed after
  apply → rollback reported `skipped_drift` for those 3 columns and restored
  the other 20
- clean apply → rollback → all 12 touched-table hashes identical to the
  pre-apply baseline (exact restoration, including `version`/`updated_at`)
- guard tests: pre-existing reviewer / waiver / council assignment / foreign
  comment on a baseline topic → apply aborted, zero marker rows written
- mutation harness: every guard-removal mutant (image disagreement, checksum,
  reviewer-score + FK preflights, image-required, foreign-partial refuse,
  flyway floor, real-account guard, baseline checks, comment guard, recover
  refusals) is killed by the suite — `mutants.mjs` prints the table

## Production application (separate gated release — do not run from here)

Pre-apply checklist — all six conditions must hold before `apply` runs:

1. **UTF-8 end-to-end**: `export PGCLIENTENCODING=UTF8` (the runner sets it
   for every psql child regardless) and `psql "$THESIS_DEMO_DB_URL" -c 'SHOW
   client_encoding'` must print `UTF8`. The scripts abort before writing
   when either end is not UTF-8 — the Vietnamese text must never be mojibake.
2. **Owner preflight** — the redundant image copy lives in a table COMMENT,
   and only the table's owner may write it. Confirm the connecting role
   owns the table (production dump showed a single owner `neondb_owner`):
   ```sql
   SELECT tablename, tableowner, current_user
   FROM pg_tables WHERE schemaname='thesis' AND tablename='thesis_topic';
   ```
   Require `tableowner = current_user` (or a member of it); if not, STOP —
   the package would apply fine but the comment copy would fail.
3. **Quiet window with application writes paused.** Every lock is NOWAIT —
   a busy table makes the package exit immediately (exit 3-style failure)
   and the runner retries with jitter; after `run.mjs` exhausts its
   attempts, check `pg_stat_activity` for autovacuum/ANALYZE workers (the
   runner prints `blocked by autovacuum/analyze` when it sees them, counts
   only) before retrying — the package cannot out-wait a worker that is
   only cancelled after ~1 s, so retry once the worker finishes.
4. **Snapshot including campuscore_audit, with a tested restore**:
   `pg_dump -n thesis -n notifications -n campuscore_audit -Fc` of the
   production database, then restore it into a scratch database
   (`createdb snap_test && pg_restore -d snap_test dump.fc && dropdb
   snap_test`) — a snapshot that was never restored is not a snapshot.
5. **Verify exactly 59 PASS** (`node run.mjs verify`; 59 since ENR5 added
   the byte-level UTF-8 check — update this number if checks are added),
   then look at the Vietnamese text in the UI afterwards (e.g. 'Kết quả
   khóa luận đã công bố' on the published rounds) — it must render
   correctly, not as mojibake.
6. **FK inventory re-check before any rollback**: the rollback preflight
   prints the live inventory; require exactly the known nine-key set listed
   under *Reversible, fail-closed* — the script refuses on any drift, but
   confirm no migration added a referencing table since the apply.

Then:

- `node run.mjs dry-run` — confirm planned counts match this README.
- `node run.mjs apply` — one transaction in the quiet window. Lock order is
  the application's write order (thesis tables → FK-referencing tables →
  notifications → audit last); hold time is one script run — sub-second
  locally, roughly 60–90 SQL round trips (estimate, not measured — on a
  remote connection such as Neon expect seconds, not milliseconds; live
  Neon timing was NOT measured). Every lock is `NOWAIT`, so a busy database
  makes the PACKAGE give up, never the app; the runner retries lock
  failures up to 8 times with jitter. Pause or quiet application writes
  during the window for the cleanest run — the package still fails closed
  if you do not.
- `node run.mjs verify` — every row must read PASS (59 expected).
- Public smoke: open the three published rounds' pages and confirm topics,
  councils, results and report downloads render — and that the Vietnamese
  text is correct.
- If anything is wrong: `node run.mjs rollback` after re-running the FK
  inventory (checklist 6). Same lock behaviour. It aborts — deleting
  nothing — when (a) application rows now reference marker rows or would be
  cascade-deleted (remove them first), (b) a live marker row differs from
  the image or an unknown marker-id row exists (inspect first), (c) the two
  image copies disagree — both readable but different (tampering —
  investigate), or (d) no valid before-image copy survives
  (`node run.mjs recover` first — it rebuilds from recorded expected
  content only and refuses to bless live state). A comment copy whose md5
  checksum FAILS is not an abort on its own: rollback reads the comment,
  discards the copy as unreadable when the checksum does not verify, and
  proceeds on the intact audit copy — exactly as if the comment were
  absent. Rows the application deleted are skipped and reported, never a
  blocker. Columns the application changed after apply are reported as
  `skipped_drift` and left as the app set them.
