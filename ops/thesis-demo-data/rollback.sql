-- Thesis demo-data rollback: safe reversal of apply.sql.
--
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f rollback.sql
--
-- (The script manages its own single transaction; do NOT pass -1.)
--
-- Steps, all inside one transaction:
--   1) LOCK every table the script reads or deletes from, SHARE ROW EXCLUSIVE
--      MODE — the weakest level that blocks every application write
--      (INSERT/UPDATE/DELETE take ROW EXCLUSIVE) and that serializes two
--      concurrent package runs, while plain reads stay untouched. Locks are
--      taken IN THE APPLICATION'S OWN WRITE ORDER: all thesis business tables
--      first (the app writes them first), then any FK-referencing tables the
--      preflight discovers, and only then — LAST — the hot app-wide tables
--      notifications.notification and campuscore_audit."AdminAudit" (the app
--      writes its notification/audit rows last). Right after the table locks
--      the script takes the ROW locks its writes need — FOR UPDATE NOWAIT on
--      the seven guarded baseline rows the restore UPDATEs touch and on every
--      marker row it deletes — so a locked row fails fast instead of queueing
--      behind an application write (a queued row wait is what once let the
--      application transaction become the deadlock victim). Every lock —
--      table and row — is requested with NOWAIT: the whole lock phase waits
--      0 ms, strictly below the application's 1 s deadlock_timeout; the
--      package never queues on a lock, so it can never be half of a wait-for
--      cycle — no deadlock can form and the package is always the
--      transaction that loses (instantly). lock_timeout stays pinned at
--      300 ms as the backstop for any implicit wait, and the script verifies
--      that pin itself below. run.mjs retries the whole script a bounded
--      number of times with jitter; each retry is a fresh transaction and no
--      lock is held while the runner waits. The locks are held only until
--      COMMIT — sub-second on a local connection, a few seconds worst-case
--      over a remote one. The application can still lose only if its writes
--      are left running against the window: keep them paused.
--   2) PREFLIGHT (re-checked only after the locks are held, so a committed
--      write is always seen and an in-flight write can never slip under us):
--      enumerate every foreign key into ALL eleven package tables straight
--      from pg_constraint, recursively following ON DELETE CASCADE edges so
--      a future grandchild table is covered too, and compare the result with
--      the reviewed inventory — the known nine-key set printed below, matched
--      as a multiset of full child/parent column lists plus the delete rule
--      (a duplicated or composite-column FK counts as drift, not a set
--      match); any drift aborts the run. DELETE-capable triggers on the
--      package tables are checked against the reviewed set of four the same
--      way — an unreviewed AFTER DELETE could remove application rows while
--      the marker deletes run. Then lock any referencing table not already
--      covered and count the rows the delete would cascade through plus the
--      rows that pin a doomed parent (matched by ctid, never by an assumed
--      id column) — plus non-marker counter-review scores on the guarded
--      baseline topics. If any exist the script ABORTS with per-table counts
--      and deletes nothing: app-written references are never removed.
--   3) BEFORE-IMAGE: marker rows exist → the image stored by apply.sql is
--      REQUIRED. It is read from audit row 'demo-th-audit-000' first; a
--      checksum-protected copy in the thesis.thesis_topic table comment is the
--      fallback. When both copies exist they must agree. Missing, unreadable
--      or disagreeing copies ABORT the script with nothing changed.
--   4) CONTENT CHECK: for every marker row the image recorded, the hashed
--      columns (an explicit per-table list — every column apply writes EXCEPT
--      volatile bookkeeping columns: version, updated_at everywhere and
--      is_read/read_at on notifications) are re-hashed canonically and
--      compared per column. Three-way classification per marker row:
--        MISSING (the app deleted it — e.g. a results retract removes the
--          demo notifications): tolerated; counted and reported as
--          'already removed by app', deletion continues with what remains.
--        DIFFERS (row exists but a hashed column changed — an in-place app
--          edit): BLOCKS rollback; nothing is deleted.
--        EXTRA (a marker id exists that the image never wrote): BLOCKS
--          rollback; nothing is deleted.
--      A migration that ADDs a column changes no hash; a migration that drops
--      a hashed column narrows the comparison to the surviving columns.
--   5) RESTORE: re-apply the before-image column values (baseline round/topic
--      column values, including version and updated_at). A column is restored
--      only when its current value still equals the value apply wrote;
--      drifted columns are skipped and reported as skipped_drift.
--   6) DELETE the marker rows in FK-safe order and clear the comment copy.
--   7) Report residue (all zeros expected) and restore results.

BEGIN;

-- every session setting that can change text/bytea/timestamp rendering is
-- pinned for the transaction so the recorded hashes round-trip under any
-- client settings (the runner also uses psql -X).
SET LOCAL lock_timeout = '300ms';
SET LOCAL statement_timeout = '120s';
SET LOCAL TimeZone = 'UTC';
SET LOCAL DateStyle = 'ISO, MDY';
SET LOCAL bytea_output = 'hex';
SET LOCAL extra_float_digits = 0;
SET LOCAL IntervalStyle = 'iso_8601';
SET LOCAL standard_conforming_strings = 'on';
-- the marker data and the before-image are UTF-8: pin the client encoding so
-- a session started with a different PGCLIENTENCODING cannot read or compare
-- the Vietnamese text as mojibake.
SET LOCAL client_encoding = 'UTF8';

-- fail closed if either end of the session is not UTF-8.
DO $$
BEGIN
  IF current_setting('client_encoding') <> 'UTF8'
     OR current_setting('server_encoding') <> 'UTF8' THEN
    RAISE EXCEPTION 'rollback aborted: client_encoding=% server_encoding=% — UTF8 is required end-to-end; nothing was changed',
      current_setting('client_encoding'), current_setting('server_encoding')
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_rollback_encoding';
  END IF;
END $$;

-- verify the lock_timeout pin actually held: the lock phase below is
-- NOWAIT-only, so its total wait is 0 ms — strictly below the application's
-- 1 s deadlock_timeout; this 300 ms setting is the backstop for any implicit
-- wait elsewhere. If a startup setting (psqlrc, PGOPTIONS, ALTER ROLE or
-- ALTER DATABASE) overrode it, refuse rather than run with a longer wait.
DO $$
BEGIN
  IF current_setting('lock_timeout') <> '300ms' THEN
    RAISE EXCEPTION 'rollback aborted: lock_timeout is %, expected 300ms — the script pins it strictly below the application deadlock_timeout; nothing was changed',
      current_setting('lock_timeout')
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_rollback_lock_timeout';
  END IF;
END $$;

-- the eleven marker tables and their marker-row predicates (same list as
-- apply.sql; the audit image row demo-th-audit-000 is excluded — it carries
-- the image and is deleted unconditionally below). Kept literal on purpose:
-- the preflight below materializes its doomed-row set from these predicates.
CREATE TEMP TABLE _tables (tbl text, pred text) ON COMMIT DROP;
INSERT INTO _tables VALUES
  ('thesis.thesis_topic','id::text LIKE ''ed1a0000-0000-4000-8000-%'''),
  ('thesis.thesis_topic_supervisor','id::text LIKE ''ed1a0000-0000-4000-8000-%'''),
  ('thesis.thesis_group','id::text LIKE ''ed1a0000-0000-4000-8000-%'''),
  ('thesis.thesis_group_member','id::text LIKE ''ed1a0000-0000-4000-8000-%'''),
  ('thesis.thesis_council','id::text LIKE ''ed1a0000-0000-4000-8000-%'''),
  ('thesis.thesis_council_member','id::text LIKE ''ed1a0000-0000-4000-8000-%'''),
  ('thesis.thesis_council_topic','id::text LIKE ''ed1a0000-0000-4000-8000-%'''),
  ('thesis.thesis_topic_score','id::text LIKE ''ed1a0000-0000-4000-8000-%'''),
  ('thesis.thesis_group_report','id::text LIKE ''ed1a0000-0000-4000-8000-%'''),
  ('notifications.notification','id LIKE ''demo-th-%'''),
  ('campuscore_audit."AdminAudit"','id LIKE ''demo-th-%'' AND id <> ''demo-th-audit-000''');

-- 1) locks in the application's write order, phase 1: the thesis business
--    tables. NOWAIT: the package never waits, so it can never be one edge of
--    a deadlock cycle — it is always the transaction that loses, instantly.
DO $$
BEGIN
  BEGIN
    EXECUTE 'LOCK TABLE thesis.thesis_council, thesis.thesis_council_member, thesis.thesis_council_topic,'
         || ' thesis.thesis_group, thesis.thesis_group_member, thesis.thesis_group_report,'
         || ' thesis.thesis_registration_round, thesis.thesis_topic, thesis.thesis_topic_score,'
         || ' thesis.thesis_topic_supervisor IN SHARE ROW EXCLUSIVE MODE NOWAIT';
  EXCEPTION WHEN lock_not_available THEN
    RAISE EXCEPTION 'rollback aborted: a package table is locked (an application write or an autovacuum/analyze worker holds it) — nothing was changed, retry when the database is idle'
      USING ERRCODE = '55P03', CONSTRAINT = 'thesis_demo_rollback_lock';
  END;
END $$;

-- 2a) foreign-key inventory: every FK that references ANY of the eleven
--     package tables, straight from pg_constraint — any schema — then
--     recursively every ON DELETE CASCADE edge whose parent is itself a
--     discovered referencing table, so a future grandchild two cascade hops
--     away is covered too.
CREATE TEMP TABLE _fk_refs ON COMMIT DROP AS
WITH RECURSIVE reach AS (
  SELECT con.oid AS conoid, con.conrelid AS child_relid, 1 AS depth
    FROM pg_constraint con
   WHERE con.contype = 'f'
     AND con.confrelid IN ('thesis.thesis_topic'::regclass,
                           'thesis.thesis_topic_supervisor'::regclass,
                           'thesis.thesis_group'::regclass,
                           'thesis.thesis_group_member'::regclass,
                           'thesis.thesis_council'::regclass,
                           'thesis.thesis_council_member'::regclass,
                           'thesis.thesis_council_topic'::regclass,
                           'thesis.thesis_topic_score'::regclass,
                           'thesis.thesis_group_report'::regclass,
                           'notifications.notification'::regclass,
                           'campuscore_audit."AdminAudit"'::regclass)
   UNION
  SELECT con.oid, con.conrelid, r.depth + 1
    FROM reach r
    JOIN pg_constraint con ON con.confrelid = r.child_relid
   WHERE con.contype = 'f' AND con.confdeltype = 'c' AND r.depth < 8
),
uniq AS (SELECT conoid, min(depth) AS depth FROM reach GROUP BY conoid)
SELECT con.conname, cn.nspname AS sch, cls.relname AS tbl,
       att.attname AS col, pn.nspname AS psch, pcl.relname AS ptbl,
       patt.attname AS pcol, con.confdeltype AS del, uniq.depth,
       cn.nspname || '.' || cls.relname || '.(' || cols.list || ')>'
       || pn.nspname || '.' || pcl.relname || '.(' || pcols.list || ')'
       || '[' || con.confdeltype::text || ']' AS key
  FROM uniq
  JOIN pg_constraint con ON con.oid = uniq.conoid
  JOIN pg_class cls ON cls.oid = con.conrelid
  JOIN pg_namespace cn ON cn.oid = cls.relnamespace
  JOIN pg_class pcl ON pcl.oid = con.confrelid
  JOIN pg_namespace pn ON pn.oid = pcl.relnamespace
  JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = con.conkey[1]
  JOIN pg_attribute patt ON patt.attrelid = con.confrelid AND patt.attnum = con.confkey[1]
  CROSS JOIN LATERAL (SELECT string_agg(a.attname, ',' ORDER BY u.ord) AS list
                        FROM unnest(con.conkey) WITH ORDINALITY u(attnum, ord)
                        JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = u.attnum) cols
  CROSS JOIN LATERAL (SELECT string_agg(a.attname, ',' ORDER BY u.ord) AS list
                        FROM unnest(con.confkey) WITH ORDINALITY u(attnum, ord)
                        JOIN pg_attribute a ON a.attrelid = con.confrelid AND a.attnum = u.attnum) pcols;

-- the reviewed inventory (flyway-142 fixture): exactly these nine
-- single-column foreign keys reference the package tables. The comparison is
-- a multiset over full child/parent column lists plus the delete rule: a
-- duplicated constraint, a composite replacement, or a changed delete action
-- all mean the schema moved since this script was reviewed — refuse rather
-- than count.
CREATE TEMP TABLE _fk_expected (key text) ON COMMIT DROP;
INSERT INTO _fk_expected VALUES
  ('thesis.thesis_council_member.(council_id)>thesis.thesis_council.(id)[c]'),
  ('thesis.thesis_council_topic.(council_id)>thesis.thesis_council.(id)[c]'),
  ('thesis.thesis_council_topic.(topic_id)>thesis.thesis_topic.(id)[c]'),
  ('thesis.thesis_group_member.(group_id)>thesis.thesis_group.(id)[c]'),
  ('thesis.thesis_group_report.(group_id)>thesis.thesis_group.(id)[r]'),
  ('thesis.thesis_group.(topic_id)>thesis.thesis_topic.(id)[a]'),
  ('thesis.thesis_topic_score.(council_id)>thesis.thesis_council.(id)[c]'),
  ('thesis.thesis_topic_score.(topic_id)>thesis.thesis_topic.(id)[c]'),
  ('thesis.thesis_topic_supervisor.(topic_id)>thesis.thesis_topic.(id)[c]');

DO $$
DECLARE
  v_missing text;
  v_extra text;
BEGIN
  RAISE NOTICE 'fk inventory into the package tables: %',
    coalesce((SELECT string_agg(key, ', ' ORDER BY key) FROM _fk_refs), '-');
  SELECT string_agg(key, ', ' ORDER BY key) INTO v_missing
    FROM (SELECT key FROM _fk_expected EXCEPT ALL SELECT key FROM _fk_refs) d(key);
  SELECT string_agg(key, ', ' ORDER BY key) INTO v_extra
    FROM (SELECT key FROM _fk_refs EXCEPT ALL SELECT key FROM _fk_expected) d(key);
  IF v_missing IS NOT NULL OR v_extra IS NOT NULL THEN
    RAISE EXCEPTION 'rollback aborted: the foreign-key inventory into the package tables changed since this script was reviewed (expected % keys; missing: %; unexpected: %) — re-run the FK inventory preflight from the README and re-review the package before rolling back; nothing was changed',
      (SELECT count(*) FROM _fk_expected), coalesce(v_missing, '-'), coalesce(v_extra, '-')
      USING ERRCODE = '23503', CONSTRAINT = 'thesis_demo_rollback_preflight';
  END IF;
END $$;

-- delete-capable triggers on the package tables, checked against the
-- reviewed set — the FK inventory alone says nothing about what a delete
-- fires. The reviewed schema has exactly four DELETE-capable triggers, and
-- none of them deletes rows: three AFTER-row triggers — the
-- guard_legacy_group_size_exception on thesis.thesis_group (raises on
-- deleting a flagged legacy group) and derive_group_member_active_
-- participation plus guard_legacy_group_size_roster on thesis_group_member
-- (both maintain/validate roster state) — and the BEFORE-row
-- enforce_group_membership_state on thesis_group_member. Anything beyond
-- them — an AFTER DELETE that removes application rows mid-rollback, or a
-- BEFORE DELETE that swallows a marker delete — changes what step 6 does;
-- refuse rather than discover it halfway through.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
      FROM pg_trigger t
      JOIN pg_class c ON c.oid = t.tgrelid
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE NOT t.tgisinternal
       AND (t.tgtype & 8) <> 0
       AND n.nspname || '.' || c.relname IN (
         'thesis.thesis_council','thesis.thesis_council_member','thesis.thesis_council_topic',
         'thesis.thesis_group','thesis.thesis_group_member','thesis.thesis_group_report',
         'thesis.thesis_registration_round','thesis.thesis_topic','thesis.thesis_topic_score',
         'thesis.thesis_topic_supervisor','notifications.notification','campuscore_audit.AdminAudit')
       AND (n.nspname, c.relname, t.tgname) NOT IN (
         ('thesis','thesis_group','thesis_legacy_group_size_marker'),
         ('thesis','thesis_group_member','thesis_group_member_active_participation'),
         ('thesis','thesis_group_member','thesis_group_member_legacy_size_read_only'),
         ('thesis','thesis_group_member','thesis_group_membership_state_member'))) THEN
    RAISE EXCEPTION 'rollback aborted: a DELETE-capable trigger exists on a package table beyond the reviewed set (thesis.thesis_group/thesis_legacy_group_size_marker, thesis.thesis_group_member/thesis_group_member_active_participation, thesis.thesis_group_member/thesis_group_member_legacy_size_read_only, thesis.thesis_group_member/thesis_group_membership_state_member) — the schema moved since this script was reviewed; nothing was deleted'
      USING ERRCODE = '23503', CONSTRAINT = 'thesis_demo_rollback_preflight';
  END IF;
END $$;

-- lock each referencing table not already in the static set, same mode — a
-- referencing table with a matching inventory is still locked so no row can
-- be inserted between the preflight count and the delete.
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT DISTINCT sch, tbl FROM _fk_refs
    WHERE (sch, tbl) NOT IN (
      ('campuscore_audit','AdminAudit'), ('notifications','notification'),
      ('thesis','thesis_council'), ('thesis','thesis_council_member'),
      ('thesis','thesis_council_topic'), ('thesis','thesis_group'),
      ('thesis','thesis_group_member'), ('thesis','thesis_group_report'),
      ('thesis','thesis_registration_round'), ('thesis','thesis_topic'),
      ('thesis','thesis_topic_score'), ('thesis','thesis_topic_supervisor'))
    ORDER BY sch, tbl
  LOOP
    BEGIN
      EXECUTE format('LOCK TABLE %I.%I IN SHARE ROW EXCLUSIVE MODE NOWAIT', r.sch, r.tbl);
    EXCEPTION WHEN lock_not_available THEN
      RAISE EXCEPTION 'rollback aborted: referencing table %.% is locked (an application write or an autovacuum/analyze worker holds it) — nothing was changed',
        r.sch, r.tbl USING ERRCODE = '55P03', CONSTRAINT = 'thesis_demo_rollback_lock';
    END;
  END LOOP;
END $$;

-- 2b) preflight, now that every relevant table is locked: no committed write
--     can appear mid-check and no in-flight write can slip under us.
--     Materialize the doomed-row set — the marker rows the script deletes,
--     propagated along every discovered ON DELETE CASCADE edge until fixed
--     point — then count the rows the application owns that the delete would
--     cascade through, plus the rows whose non-cascade references pin a
--     doomed parent. Rows are matched by ctid, so a referencing table
--     without an id column (or with a different marker shape) is covered.
CREATE TEMP TABLE _doomed (sch text, tbl text, row_ctid tid) ON COMMIT DROP;
CREATE TEMP TABLE _blocking (tbl text, refs bigint) ON COMMIT DROP;

DO $$
DECLARE
  r record;
  n bigint := 1;
  m bigint;
  v_pred text;
BEGIN
  -- seeds: the marker rows the script itself deletes
  FOR r IN SELECT tbl, pred FROM _tables LOOP
    EXECUTE format(
      'INSERT INTO _doomed (sch, tbl, row_ctid)
         SELECT n.nspname, c.relname, t.ctid
           FROM %s t
           JOIN pg_class c ON c.oid = t.tableoid
           JOIN pg_namespace n ON n.oid = c.relnamespace
          WHERE %s',
      r.tbl, r.pred);
  END LOOP;

  -- propagate along every discovered cascade edge until fixed point: a
  -- referencing row of a doomed row is doomed too.
  WHILE n > 0 LOOP
    n := 0;
    FOR r IN SELECT sch, tbl, col, psch, ptbl, pcol FROM _fk_refs WHERE del = 'c' LOOP
      EXECUTE format(
        'INSERT INTO _doomed (sch, tbl, row_ctid)
           SELECT %L, %L, c.ctid
             FROM %I.%I c
            WHERE EXISTS (SELECT 1 FROM _doomed d
                            JOIN %I.%I p ON p.ctid = d.row_ctid
                           WHERE d.sch = %L AND d.tbl = %L
                             AND p.%I = c.%I)
              AND NOT EXISTS (SELECT 1 FROM _doomed d2
                               WHERE d2.sch = %L AND d2.tbl = %L
                                 AND d2.row_ctid = c.ctid)',
        r.sch, r.tbl, r.sch, r.tbl,
        r.psch, r.ptbl, r.psch, r.ptbl, r.pcol, r.col,
        r.sch, r.tbl);
      GET DIAGNOSTICS m = ROW_COUNT;
      n := n + m;
    END LOOP;
  END LOOP;

  -- rows that pin a doomed parent through a non-cascade edge: the child row
  -- is not deleted, but the delete fails (NO ACTION / RESTRICT) or the
  -- reference is rewritten (SET NULL / SET DEFAULT) — either way the app
  -- loses data integrity, so they count as blockers. Rows already doomed
  -- (marker rows of a package table) are expected deletions, not blockers.
  FOR r IN SELECT sch, tbl, col, psch, ptbl, pcol FROM _fk_refs WHERE del <> 'c' LOOP
    EXECUTE format(
      'INSERT INTO _blocking
         SELECT %L, count(*) FROM %I.%I c
          WHERE EXISTS (SELECT 1 FROM _doomed d
                          JOIN %I.%I p ON p.ctid = d.row_ctid
                         WHERE d.sch = %L AND d.tbl = %L
                           AND p.%I = c.%I)
            AND NOT EXISTS (SELECT 1 FROM _doomed d2
                             WHERE d2.sch = %L AND d2.tbl = %L
                               AND d2.row_ctid = c.ctid)',
      r.tbl || '.' || r.col, r.sch, r.tbl,
      r.psch, r.ptbl, r.psch, r.ptbl, r.pcol, r.col,
      r.sch, r.tbl);
  END LOOP;

  -- cascade victims the application owns: every doomed row in a table
  -- outside the package, and non-marker doomed rows inside it.
  FOR r IN SELECT sch, tbl FROM _doomed GROUP BY sch, tbl LOOP
    SELECT pt.pred INTO v_pred FROM _tables pt
     WHERE regexp_replace(pt.tbl, '"', '', 'g') = r.sch || '.' || r.tbl;
    IF v_pred IS NULL THEN
      EXECUTE format(
        'INSERT INTO _blocking SELECT %L, count(*) FROM _doomed d WHERE d.sch = %L AND d.tbl = %L',
        r.sch || '.' || r.tbl || '(cascade)', r.sch, r.tbl);
    ELSE
      EXECUTE format(
        'INSERT INTO _blocking SELECT %L, count(*) FROM %I.%I c
           JOIN _doomed d ON d.row_ctid = c.ctid AND d.sch = %L AND d.tbl = %L
          WHERE NOT (%s)',
        r.sch || '.' || r.tbl || '(cascade)', r.sch, r.tbl, r.sch, r.tbl, v_pred);
    END IF;
  END LOOP;

  -- non-marker counter-review scores on the three baseline topics whose
  -- gvpb_lecturer_id this script resets: deleting nothing, but restoring NULL
  -- would leave an application score orphaned from its reviewer.
  INSERT INTO _blocking
  SELECT 'thesis_topic_score.guarded_gvpb', count(*)
  FROM thesis.thesis_topic_score s
  WHERE s.component = 'GVPB'
    AND s.topic_id IN ('018f4a7c-3b12-4d90-a5e1-7c82b9045001',
                       '6f64670b-cd6b-4e1b-ae9d-0b497f2472a8',
                       'f580eadf-fffb-4db2-a7d8-423e83934dc2')
    AND s.id::text NOT LIKE 'ed1a0000-0000-4000-8000-%';
  IF EXISTS (SELECT 1 FROM _blocking WHERE refs > 0) THEN
    RAISE EXCEPTION 'rollback aborted: % application-owned row(s) reference marker rows or would be cascade-deleted (%). Remove those references first; rollback never deletes application-owned rows.',
      (SELECT sum(refs) FROM _blocking),
      (SELECT string_agg(tbl || '=' || refs, ', ' ORDER BY tbl) FROM _blocking WHERE refs > 0)
      USING ERRCODE = '23503', CONSTRAINT = 'thesis_demo_rollback_preflight';
  END IF;
END $$;

-- locks, phase 2 (LAST, matching the app which writes audit/notification
-- last): the two hot app-wide tables. NOWAIT like every other lock.
DO $$
BEGIN
  BEGIN
    EXECUTE 'LOCK TABLE notifications.notification, campuscore_audit."AdminAudit" IN SHARE ROW EXCLUSIVE MODE NOWAIT';
  EXCEPTION WHEN lock_not_available THEN
    RAISE EXCEPTION 'rollback aborted: an audit/notification table is locked (an application write or an autovacuum/analyze worker holds it) — nothing was changed, retry when the database is idle'
      USING ERRCODE = '55P03', CONSTRAINT = 'thesis_demo_rollback_lock';
  END;
END $$;

-- row locks up front, NOWAIT like the table locks — one statement per table:
-- the seven guarded baseline rows the restore UPDATEs touch (three rounds,
-- four topics), then every marker row the delete removes, in the same
-- write-order table sequence as apply (the audit image row demo-th-audit-000
-- included — it is deleted unconditionally). A row held by an application
-- transaction fails instantly with the same retryable lock error — without
-- these, the restore UPDATEs and marker deletes below would queue on row
-- locks a statement at a time, and a queued wait can outlive the
-- application's deadlock_timeout, which is exactly how the application was
-- once chosen as the deadlock victim.
DO $$
DECLARE r record;
BEGIN
  BEGIN
    PERFORM 1 FROM thesis.thesis_registration_round
     WHERE id IN ('b35e9ff1-1eb8-4e73-812e-04e1a66041f1',
                  'bad8eba0-2d12-4414-8aab-7424174b3b97',
                  'be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1')
     ORDER BY id FOR UPDATE NOWAIT;
    PERFORM 1 FROM thesis.thesis_topic
     WHERE id IN ('018f4a7c-3b12-4d90-a5e1-7c82b9045001',
                  '67c74df5-5c7f-4e74-aa02-d51af7207fe9',
                  '6f64670b-cd6b-4e1b-ae9d-0b497f2472a8',
                  'f580eadf-fffb-4db2-a7d8-423e83934dc2')
     ORDER BY id FOR UPDATE NOWAIT;
    FOR r IN SELECT tbl, pred FROM _tables LOOP
      EXECUTE format('SELECT ctid FROM %s t WHERE %s ORDER BY ctid FOR UPDATE NOWAIT',
                     r.tbl, r.pred);
    END LOOP;
    EXECUTE 'SELECT ctid FROM campuscore_audit."AdminAudit" t WHERE id = ''demo-th-audit-000'' FOR UPDATE NOWAIT';
  EXCEPTION WHEN lock_not_available THEN
    RAISE EXCEPTION 'rollback aborted: a row the package must write is locked by another transaction — nothing was changed, retry when the database is idle'
      USING ERRCODE = '55P03', CONSTRAINT = 'thesis_demo_rollback_lock';
  END;
END $$;

-- marker census reused by the image gate, the hash check and the residue report
CREATE TEMP TABLE _marker_counts ON COMMIT DROP AS
SELECT 'thesis_topic' AS name, count(*) AS cnt FROM thesis.thesis_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_topic_supervisor', count(*) FROM thesis.thesis_topic_supervisor WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_group', count(*) FROM thesis.thesis_group WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_group_member', count(*) FROM thesis.thesis_group_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_council', count(*) FROM thesis.thesis_council WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_council_member', count(*) FROM thesis.thesis_council_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_council_topic', count(*) FROM thesis.thesis_council_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_topic_score', count(*) FROM thesis.thesis_topic_score WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_group_report', count(*) FROM thesis.thesis_group_report WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'notification', count(*) FROM notifications.notification WHERE id LIKE 'demo-th-%'
UNION ALL SELECT 'admin_audit', count(*) FROM campuscore_audit."AdminAudit" WHERE id LIKE 'demo-th-%';

-- explicit, versioned per-table column lists the row hashes cover — the same
-- literal list as apply.sql (every column apply writes EXCEPT version,
-- updated_at and notification is_read/read_at). Columns a migration dropped
-- simply fall out of the comparison via the pg_attribute join below; columns
-- a migration added are never hashed.
CREATE TEMP TABLE _hashcols (tbl text, col text, ord int) ON COMMIT DROP;
INSERT INTO _hashcols
SELECT j.tbl, c.col, c.ord::int
FROM (VALUES
  ('thesis.thesis_topic', '["id","round_id","department_id","title","description","max_groups","status","created_by","created_at","final_score","final_score_finalized_by","final_score_finalized_at","result_status","gvpb_lecturer_id","gvpb_waived_reason","gvpb_waived_by","gvpb_waived_at","result_reason","result_by","result_at"]'),
  ('thesis.thesis_topic_supervisor', '["id","topic_id","lecturer_id","supervisor_order","created_at"]'),
  ('thesis.thesis_group', '["id","round_id","leader_student_id","topic_id","status","approval_status","approved_by","approved_at","rejection_reason","created_at","legacy_group_size_exception","size_exception_reason","size_exception_by","size_exception_at"]'),
  ('thesis.thesis_group_member', '["id","group_id","round_id","student_id","member_order","is_leader","created_at","display_name","contact","is_external","active_participation"]'),
  ('thesis.thesis_council', '["id","round_id","name","status","created_by","created_at"]'),
  ('thesis.thesis_council_member', '["id","council_id","lecturer_id","member_role","created_at"]'),
  ('thesis.thesis_council_topic', '["id","council_id","topic_id","assigned_by","created_at"]'),
  ('thesis.thesis_topic_score', '["id","topic_id","council_id","lecturer_id","component","score","graded_at","created_at","comment"]'),
  ('thesis.thesis_group_report', '["id","group_id","round_id","submitted_by","title","url","note","submitted_at","file_name","file_type","file_size","file_data","storage_provider","storage_bucket","storage_key","file_sha256"]'),
  ('notifications.notification', '["id","user_id","title","message","type","link","created_at","announcement_id","source_key"]'),
  ('campuscore_audit."AdminAudit"', '["id","actorId","actorLabel","action","entityType","entityId","summary","beforeState","afterState"]')
) j(tbl, cols)
CROSS JOIN LATERAL jsonb_array_elements_text(j.cols::jsonb) WITH ORDINALITY c(col, ord);

-- 3) locate and validate the before-image: primary copy in audit row
--    'demo-th-audit-000', checksum-protected fallback in the table comment.
CREATE TEMP TABLE _image (img jsonb, src text) ON COMMIT DROP;

DO $$
DECLARE
  v_markers bigint;
  v_raw text;
  v_img jsonb;
  v_copy jsonb;
  v_comment text;
  v_decl text;
  v_payload text;
  v_src text;
BEGIN
  SELECT coalesce(sum(cnt), 0) INTO v_markers FROM _marker_counts;
  IF v_markers = 0 THEN
    RAISE NOTICE 'no marker rows present — nothing to roll back';
    RETURN;
  END IF;

  SELECT "beforeState" INTO v_raw FROM campuscore_audit."AdminAudit" WHERE id = 'demo-th-audit-000';
  IF v_raw IS NOT NULL AND v_raw ~ '^\s*\{' THEN
    BEGIN v_img := v_raw::jsonb; EXCEPTION WHEN OTHERS THEN v_img := NULL; END;
  END IF;

  v_comment := obj_description('thesis.thesis_topic'::regclass);
  IF v_comment LIKE 'thesis-demo-data v3 md5:%' THEN
    v_decl := substring(v_comment from 'md5:([0-9a-f]{32})');
    v_payload := substr(v_comment, position(E'\n' in v_comment) + 1);
    IF v_decl IS NOT NULL AND coalesce(v_payload, '') <> '' AND md5(v_payload) = v_decl THEN
      BEGIN v_copy := v_payload::jsonb; EXCEPTION WHEN OTHERS THEN v_copy := NULL; END;
    END IF;
  END IF;

  IF v_img IS NOT NULL AND v_copy IS NOT NULL AND v_img::text <> v_copy::text THEN
    RAISE EXCEPTION 'rollback aborted: the audit-row before-image and the checksum-protected comment copy disagree — possible tampering or a partial restore; nothing was changed (see recover-before-image.sql)'
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_rollback_image';
  END IF;
  IF v_img IS NULL AND v_copy IS NOT NULL THEN
    v_img := v_copy;
    v_src := 'comment';
  ELSIF v_copy IS NOT NULL THEN
    v_src := 'audit+comment';
  ELSE
    v_src := 'audit';
  END IF;

  IF v_img IS NULL
     OR coalesce(v_img->>'format', '') <> '3'
     OR jsonb_typeof(v_img->'cols') IS DISTINCT FROM 'array'
     OR jsonb_typeof(v_img->'rows') IS DISTINCT FROM 'object'
     OR jsonb_typeof(v_img->'hashcols') IS DISTINCT FROM 'object' THEN
    RAISE EXCEPTION 'rollback aborted: marker rows exist but the before-image is missing or unreadable (audit-row copy and checksum-protected comment copy both failed) — nothing was changed; rebuild it with recover-before-image.sql first'
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_rollback_image';
  END IF;
  -- the image's recorded hash column lists must match this script's literals:
  -- a hand-built or foreign image could otherwise narrow the comparison.
  IF EXISTS (
    SELECT 1 FROM _hashcols hc
    GROUP BY hc.tbl
    HAVING (SELECT coalesce(jsonb_agg(c ORDER BY c), '[]'::jsonb)
              FROM jsonb_array_elements_text(coalesce(v_img->'hashcols'->hc.tbl, '[]'::jsonb)) c)
           IS DISTINCT FROM jsonb_agg(hc.col ORDER BY hc.col)) THEN
    RAISE EXCEPTION 'rollback aborted: the before-image hash column lists do not match this rollback script — refusing to trust a foreign image'
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_rollback_image';
  END IF;
  INSERT INTO _image VALUES (v_img, v_src);
END $$;

-- 4) content check: the hashed columns of every live marker row are re-hashed
--    canonically (to_jsonb(...)#>>'{}' under the pinned settings, encode hex
--    for bytea) and compared with the per-column hashes the image recorded.
CREATE TEMP TABLE _hash_actual (tbl text, id text, hashes jsonb) ON COMMIT DROP;

DO $$
DECLARE
  r record;
  v_img jsonb;
BEGIN
  SELECT img INTO v_img FROM _image;
  IF v_img IS NULL THEN RETURN; END IF;
  FOR r IN
    SELECT hc.tbl,
           string_agg(format('%L, md5(CASE WHEN t.%I IS NULL THEN chr(1) ELSE %s END)',
                             hc.col, hc.col,
                             CASE WHEN a.atttypid = 'bytea'::regtype
                                  THEN format('encode(t.%I, ''hex'')', hc.col)
                                  ELSE format('to_jsonb(t.%I) #>> ''{}''', hc.col) END),
                             ', ' ORDER BY hc.ord) AS pairs
      FROM _hashcols hc
      JOIN pg_attribute a ON a.attrelid = hc.tbl::regclass AND a.attname = hc.col
                         AND a.attnum > 0 AND NOT a.attisdropped
      JOIN _tables pt ON pt.tbl = hc.tbl
      GROUP BY hc.tbl
  LOOP
    EXECUTE format('INSERT INTO _hash_actual SELECT %L, id::text, jsonb_build_object(%s) FROM %s t WHERE %s',
                   r.tbl, r.pairs, r.tbl,
                   (SELECT pred FROM _tables WHERE tbl = r.tbl));
  END LOOP;
END $$;

-- three-way classification per marker row and per table:
--   missing: image id absent live (the app deleted it) -> tolerated
--   differs: id live, a hashed column changed (or none shared) -> blocks
--   extra:   live marker id the image never wrote                -> blocks
CREATE TEMP TABLE _hash_report (tbl text, missing bigint, differs bigint, extra bigint) ON COMMIT DROP;

DO $$
DECLARE
  r record;
  v_img jsonb;
BEGIN
  SELECT img INTO v_img FROM _image;
  IF v_img IS NULL THEN RETURN; END IF;
  FOR r IN
    SELECT e.key AS tbl FROM jsonb_each(v_img->'rows') e ORDER BY 1 LOOP
    IF r.tbl NOT IN (SELECT tbl FROM _tables) THEN
      RAISE EXCEPTION 'rollback aborted: before-image names an unknown table (%) — refusing to trust a corrupted image', r.tbl
        USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_rollback_image';
    END IF;
    EXECUTE format(
      'WITH exp AS (SELECT e.key AS id, e.value AS hashes
                      FROM jsonb_each(coalesce($1, ''{}''::jsonb)) e),
            act AS (SELECT id, hashes FROM _hash_actual WHERE tbl = %L)
       INSERT INTO _hash_report
       SELECT %L,
         (SELECT count(*) FROM exp e WHERE NOT EXISTS (SELECT 1 FROM act a WHERE a.id = e.id)),
         (SELECT count(*) FROM exp e JOIN act a ON a.id = e.id
           WHERE NOT EXISTS (SELECT 1 FROM jsonb_object_keys(e.hashes) k WHERE a.hashes ? k)
              OR EXISTS (SELECT 1 FROM jsonb_each_text(e.hashes) x
                          WHERE a.hashes ? x.key AND a.hashes->>x.key IS DISTINCT FROM x.value)),
         (SELECT count(*) FROM act a WHERE NOT EXISTS (SELECT 1 FROM exp e WHERE e.id = a.id))',
      r.tbl, r.tbl, r.tbl) USING (v_img->'rows'->r.tbl);
  END LOOP;
  IF EXISTS (SELECT 1 FROM _hash_report WHERE differs > 0 OR extra > 0) THEN
    RAISE EXCEPTION 'rollback aborted: % marker row(s) differ from the apply-time content and % unexpected marker row(s) are not in the image (%) — the application touched demo data; nothing was deleted. Inspect the reported table(s), then either restore the rows or remove the markers deliberately.',
      (SELECT coalesce(sum(differs),0) FROM _hash_report),
      (SELECT coalesce(sum(extra),0) FROM _hash_report),
      (SELECT string_agg(tbl || ': ' ||
               CASE WHEN differs > 0 THEN differs || ' differed' ELSE '' END ||
               CASE WHEN differs > 0 AND extra > 0 THEN ', ' ELSE '' END ||
               CASE WHEN extra > 0 THEN extra || ' unknown' ELSE '' END,
             '; ' ORDER BY tbl) FROM _hash_report WHERE differs > 0 OR extra > 0)
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_rollback_hash';
  END IF;
  IF EXISTS (SELECT 1 FROM _hash_report WHERE missing > 0) THEN
    RAISE NOTICE 'rollback: % marker row(s) were already removed by the application (%) — continuing with the remaining rows',
      (SELECT sum(missing) FROM _hash_report),
      (SELECT string_agg(tbl || '=' || missing, ', ' ORDER BY tbl) FROM _hash_report WHERE missing > 0);
  END IF;
END $$;

-- 5) restore before-image columns where the current value still equals the applied value
CREATE TEMP TABLE _restore_results (tbl text, id text, col text, outcome text) ON COMMIT DROP;

DO $$
DECLARE
  r record;
  n int;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM _image) THEN RETURN; END IF;
  FOR r IN
    SELECT (e->>'tbl') AS tbl, (e->>'id') AS rid, (e->>'col') AS col,
           (e->>'before') AS before_val, (e->>'applied') AS applied_val,
           format_type(a.atttypid, a.atttypmod) AS coltype
    FROM _image i,
         jsonb_array_elements(i.img->'cols') e
    JOIN pg_attribute a ON a.attrelid = ('thesis.' || (e->>'tbl'))::regclass
                        AND a.attname = (e->>'col')
  LOOP
    EXECUTE format(
      'UPDATE thesis.%I SET %I = $1::%s WHERE id::text = $2 AND %I IS NOT DISTINCT FROM $3::%s',
      r.tbl, r.col, r.coltype, r.col, r.coltype)
      USING r.before_val, r.rid, r.applied_val;
    GET DIAGNOSTICS n = ROW_COUNT;
    INSERT INTO _restore_results VALUES (r.tbl, r.rid, r.col, CASE WHEN n = 1 THEN 'restored' ELSE 'skipped_drift' END);
  END LOOP;
  IF EXISTS (SELECT 1 FROM _restore_results WHERE outcome = 'skipped_drift') THEN
    RAISE WARNING 'rollback: % column(s) skipped because the app changed them after apply',
      (SELECT count(*) FROM _restore_results WHERE outcome = 'skipped_drift');
  END IF;
END $$;

-- 6) delete marker rows, leaf tables first. Marker groups are deleted before
-- their rosters intentionally: the cancelled-group trigger makes member rows
-- read-only, and the member FK cascades the roster away with the parent.
DELETE FROM thesis.thesis_topic_score WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_council_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_group_report WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_group WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_group_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_council_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_council WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_topic_supervisor WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM notifications.notification WHERE id LIKE 'demo-th-%';
DELETE FROM campuscore_audit."AdminAudit" WHERE id LIKE 'demo-th-%';

-- clear the checksum-protected comment copy so no package residue survives
DO $$
BEGIN
  IF obj_description('thesis.thesis_topic'::regclass) LIKE 'thesis-demo-data v3 %' THEN
    EXECUTE 'COMMENT ON TABLE thesis.thesis_topic IS NULL';
  END IF;
END $$;

-- 7a) restore outcome summary
SELECT tbl, col, outcome, count(*) AS cols FROM _restore_results GROUP BY 1,2,3 ORDER BY 1,2;

-- 7b) post-rollback residue report (all marker counts zero expected)
SELECT 'thesis_topic' AS section, count(*) AS residual FROM thesis.thesis_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_topic_supervisor', count(*) FROM thesis.thesis_topic_supervisor WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_group', count(*) FROM thesis.thesis_group WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_group_member', count(*) FROM thesis.thesis_group_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_council', count(*) FROM thesis.thesis_council WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_council_member', count(*) FROM thesis.thesis_council_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_council_topic', count(*) FROM thesis.thesis_council_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_topic_score', count(*) FROM thesis.thesis_topic_score WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_group_report', count(*) FROM thesis.thesis_group_report WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'notification', count(*) FROM notifications.notification WHERE id LIKE 'demo-th-%'
UNION ALL SELECT 'admin_audit', count(*) FROM campuscore_audit."AdminAudit" WHERE id LIKE 'demo-th-%'
UNION ALL SELECT 'image_comment_copy', coalesce((obj_description('thesis.thesis_topic'::regclass) LIKE 'thesis-demo-data v3 %')::int, 0)
UNION ALL SELECT 'already_removed_by_app', coalesce((SELECT sum(missing) FROM _hash_report), 0)
UNION ALL SELECT 'baseline.topics', (SELECT count(*) FROM thesis.thesis_topic WHERE id::text NOT LIKE 'ed1a0000-0000-4000-8000-%')
UNION ALL SELECT 'baseline.groups', (SELECT count(*) FROM thesis.thesis_group WHERE id::text NOT LIKE 'ed1a0000-0000-4000-8000-%')
ORDER BY 1;

COMMIT;
