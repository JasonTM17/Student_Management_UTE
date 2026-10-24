-- Thesis demo-data rollback: safe reversal of apply.sql.
--
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f rollback.sql
--
-- (The script manages its own single transaction; do NOT pass -1.)
--
-- Steps, all inside one transaction:
--   1) PREFLIGHT: enumerate every foreign key into the three marker parent
--      tables (thesis_topic, thesis_group, thesis_council) straight from
--      pg_constraint, then count every non-marker row that references a marker
--      row. If any exist the script ABORTS with per-table counts and deletes
--      nothing: app-written references are never removed by rollback.
--   2) RESTORE: re-apply the before-image stored by apply.sql in audit row
--      'demo-th-audit-000' (baseline round/topic column values, including
--      version and updated_at). A column is restored only when its current
--      value still equals the value apply wrote; drifted columns are skipped
--      and reported in the drift summary.
--   3) DELETE the marker rows in FK-safe order.
--   4) Report residue (all zeros expected) and restore results.

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- 1) preflight: every non-marker row referencing a marker parent row blocks rollback
CREATE TEMP TABLE _fk_refs ON COMMIT DROP AS
SELECT con.conname, cls.relname AS tbl, att.attname AS col, pcl.relname AS parent
FROM pg_constraint con
JOIN pg_class cls ON cls.oid = con.conrelid
JOIN pg_namespace n ON n.oid = cls.relnamespace
JOIN pg_class pcl ON pcl.oid = con.confrelid
JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = con.conkey[1]
WHERE con.contype = 'f' AND n.nspname = 'thesis'
  AND con.confrelid IN ('thesis.thesis_topic'::regclass,
                        'thesis.thesis_group'::regclass,
                        'thesis.thesis_council'::regclass);

CREATE TEMP TABLE _blocking (tbl text, refs bigint) ON COMMIT DROP;

DO $$
DECLARE r record; n bigint;
BEGIN
  FOR r IN SELECT tbl, col FROM _fk_refs LOOP
    EXECUTE format(
      'INSERT INTO _blocking SELECT %L, count(*) FROM thesis.%I c
        WHERE c.%I::text LIKE ''ed1a0000-0000-4000-8000-%%''
          AND c.id::text NOT LIKE ''ed1a0000-0000-4000-8000-%%''',
      r.tbl || '.' || r.col, r.tbl, r.col) ;
  END LOOP;
  IF EXISTS (SELECT 1 FROM _blocking WHERE refs > 0) THEN
    RAISE EXCEPTION 'rollback aborted: % non-marker row(s) reference marker rows (%). Remove those references first; rollback never deletes application-owned rows.',
      (SELECT sum(refs) FROM _blocking),
      (SELECT string_agg(tbl || '=' || refs, ', ' ORDER BY tbl) FROM _blocking WHERE refs > 0)
      USING ERRCODE = '23503', CONSTRAINT = 'thesis_demo_rollback_preflight';
  END IF;
END $$;

-- 2) restore before-image columns where the current value still equals the applied value
CREATE TEMP TABLE _restore_results (tbl text, id text, col text, outcome text) ON COMMIT DROP;

DO $$
DECLARE
  r record;
  n int;
  v_found boolean;
BEGIN
  SELECT EXISTS (SELECT 1 FROM campuscore_audit."AdminAudit" WHERE id = 'demo-th-audit-000' AND "beforeState" IS NOT NULL) INTO v_found;
  IF NOT v_found THEN
    RAISE WARNING 'no before-image audit row found; baseline column restore skipped (marker rows are still deleted)';
  ELSE
    FOR r IN
      SELECT (e->>'tbl') AS tbl, (e->>'id') AS rid, (e->>'col') AS col,
             (e->>'before') AS before_val, (e->>'applied') AS applied_val,
             format_type(a.atttypid, a.atttypmod) AS coltype
      FROM campuscore_audit."AdminAudit" au,
           jsonb_array_elements(au."beforeState"::jsonb) e
      JOIN pg_attribute a ON a.attrelid = ('thesis.' || (e->>'tbl'))::regclass
                          AND a.attname = (e->>'col')
      WHERE au.id = 'demo-th-audit-000'
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
  END IF;
END $$;

-- 3) delete marker rows, leaf tables first. Marker groups are deleted before
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

-- 4a) restore outcome summary
SELECT tbl, col, outcome, count(*) AS cols FROM _restore_results GROUP BY 1,2,3 ORDER BY 1,2;

-- 4b) post-rollback residue report (all marker counts zero expected)
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
UNION ALL SELECT 'baseline.topics', (SELECT count(*) FROM thesis.thesis_topic WHERE id::text NOT LIKE 'ed1a0000-0000-4000-8000-%')
UNION ALL SELECT 'baseline.groups', (SELECT count(*) FROM thesis.thesis_group WHERE id::text NOT LIKE 'ed1a0000-0000-4000-8000-%')
ORDER BY 1;

COMMIT;
