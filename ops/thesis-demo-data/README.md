# Thesis demo-data package

Operations package that fills the sparse thesis registration rounds with
realistic, internally consistent demo rows so the product exercises every
workflow state. It is NOT a Flyway migration and NOT a startup seeder — it only
runs when an operator runs it, against a connection they supply.

Designed and proven against the production snapshot of
2026-10-15 plus migrations V135–V142 (flyway 142 required).

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
| thesis.thesis_topic_score | 48 | same namespace |
| thesis.thesis_group_report | 17 | same namespace |
| notifications.notification | 107 | `demo-th-notif-*` ids |
| campuscore_audit."AdminAudit" | 5 | `demo-th-audit-*` ids, action `THESIS_DEMO_DATA_ENRICHED` |

Plus guarded column updates on six pre-existing rows (three
`results_published_at` stamps, three `gvpb_lecturer_id` reviewer assignments,
one `gvpb_waived_reason` waiver) — each reverted by `rollback.sql`.

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
  it reuses exist, and no partial marker state is present.
- **One transaction**: the script `BEGIN`s and either `COMMIT`s or `ROLLBACK`s
  (dry-run). Any error aborts everything. Do NOT pass `-1`; the file manages
  its own transaction.
- **Idempotent**: deterministic marker ids + `ON CONFLICT` / existence guards;
  a second run inserts zero rows and commits.
- **Synthetic-only**: no writes reference accounts outside the manifest; no
  real emails, names, companies, phones, or external URLs.
- **Reversible**: `rollback.sql` deletes only marker rows and reverts the six
  guarded updates, restoring the baseline exactly.

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
node run.mjs verify     # 42 SELECT-only integrity checks, all PASS expected
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

Executed on `enr1_scratch` (native Postgres, restored Oct-15 production
snapshot + V135–V142 replayed): dry-run → apply → verify (42/42 PASS) →
apply again (all `INSERT 0 0`) → rollback (0 marker rows, baseline restored:
75 topics / 23 groups / 68 members) → re-apply → verify (42/42 PASS).

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
6. If anything is wrong: `node run.mjs rollback` restores the baseline.
