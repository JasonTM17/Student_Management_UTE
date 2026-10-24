-- Thesis demo-data rollback: safe reversal of apply.sql.
--
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f rollback.sql
--
-- (The script manages its own single transaction; do NOT pass -1.)
--
-- Steps, all inside one transaction:
--   1) LOCK every table the script reads or deletes from, in one fixed order,
--      SHARE ROW EXCLUSIVE MODE — the weakest level that blocks every
--      application write (INSERT/UPDATE/DELETE take ROW EXCLUSIVE) and that
--      serializes two concurrent package runs against each other, while plain
--      reads stay untouched. lock_timeout = 5 s makes the script give up
--      cleanly instead of queueing behind an application write; the app waits
--      behind us for at most the short remainder of this script (well under a
--      second on a local connection, a few seconds worst-case over a remote
--      one — the lock is held only until COMMIT).
--   2) PREFLIGHT (re-checked only after the locks are held, so a committed
--      write is always seen and an in-flight write can never slip under us):
--      enumerate every foreign key into the three marker parent tables
--      (thesis_topic, thesis_group, thesis_council) straight from
--      pg_constraint, lock any referencing table not already covered, then
--      count every non-marker row that references a marker row — plus
--      non-marker counter-review scores on the guarded baseline topics. If
--      any exist the script ABORTS with per-table counts and deletes nothing:
--      app-written references are never removed by rollback.
--   3) BEFORE-IMAGE: marker rows exist → the image stored by apply.sql is
--      REQUIRED. It is read from audit row 'demo-th-audit-000' first; a
--      checksum-protected copy in the thesis.thesis_topic table comment is the
--      fallback. When both copies exist they must agree. Missing, unreadable
--      or disagreeing copies ABORT the script with nothing changed.
--   4) CONTENT CHECK: every marker row is hashed (md5 of the whole row) and
--      compared with the hash recorded at apply time. Any difference — an
--      in-place edit, a deleted marker row — means the application touched
--      demo data; the script ABORTS with per-table counts and the owner
--      decides. Nothing is deleted.
--   5) RESTORE: re-apply the before-image column values (baseline round/topic
--      column values, including version and updated_at). A column is restored
--      only when its current value still equals the value apply wrote;
--      drifted columns are skipped and reported as skipped_drift.
--   6) DELETE the marker rows in FK-safe order and clear the comment copy.
--   7) Report residue (all zeros expected) and restore results.

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';
SET LOCAL TimeZone = 'UTC';
SET LOCAL DateStyle = 'ISO, MDY';

-- 1) fixed-order table locks; a lock wait that exceeds lock_timeout aborts the
--    whole run with a clear reason instead of silently queueing behind the app.
DO $$
BEGIN
  BEGIN
    EXECUTE 'LOCK TABLE campuscore_audit."AdminAudit", notifications.notification,'
         || ' thesis.thesis_council, thesis.thesis_council_member, thesis.thesis_council_topic,'
         || ' thesis.thesis_group, thesis.thesis_group_member, thesis.thesis_group_report,'
         || ' thesis.thesis_registration_round, thesis.thesis_topic, thesis.thesis_topic_score,'
         || ' thesis.thesis_topic_supervisor IN SHARE ROW EXCLUSIVE MODE';
  EXCEPTION WHEN lock_not_available THEN
    RAISE EXCEPTION 'rollback aborted: could not lock the package tables within lock_timeout — an application write is in progress; nothing was changed, retry when the app is idle'
      USING ERRCODE = '55P03', CONSTRAINT = 'thesis_demo_rollback_lock';
  END;
END $$;

-- 2a) discover every FK into the three marker parent tables from pg_constraint
--     (any child schema, so a future cross-schema FK is still covered), then
--     lock each referencing table not already in the static set, same mode.
CREATE TEMP TABLE _fk_refs ON COMMIT DROP AS
SELECT con.conname, n2.nspname AS sch, cls.relname AS tbl, att.attname AS col, pcl.relname AS parent
FROM pg_constraint con
JOIN pg_class cls ON cls.oid = con.conrelid
JOIN pg_namespace n2 ON n2.oid = cls.relnamespace
JOIN pg_class pcl ON pcl.oid = con.confrelid
JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = con.conkey[1]
WHERE con.contype = 'f'
  AND con.confrelid IN ('thesis.thesis_topic'::regclass,
                        'thesis.thesis_group'::regclass,
                        'thesis.thesis_council'::regclass);

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
      EXECUTE format('LOCK TABLE %I.%I IN SHARE ROW EXCLUSIVE MODE', r.sch, r.tbl);
    EXCEPTION WHEN lock_not_available THEN
      RAISE EXCEPTION 'rollback aborted: could not lock referencing table %.% within lock_timeout — an application write is in progress; nothing was changed',
        r.sch, r.tbl USING ERRCODE = '55P03', CONSTRAINT = 'thesis_demo_rollback_lock';
    END;
  END LOOP;
END $$;

-- 2b) preflight, now that every relevant table is locked: no committed write
--     can appear mid-check and no in-flight write can slip under us.
CREATE TEMP TABLE _blocking (tbl text, refs bigint) ON COMMIT DROP;

DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT sch, tbl, col FROM _fk_refs LOOP
    EXECUTE format(
      'INSERT INTO _blocking SELECT %L, count(*) FROM %I.%I c
        WHERE c.%I::text LIKE ''ed1a0000-0000-4000-8000-%%''
          AND c.id::text NOT LIKE ''ed1a0000-0000-4000-8000-%%''',
      r.tbl || '.' || r.col, r.sch, r.tbl, r.col);
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
    RAISE EXCEPTION 'rollback aborted: % non-marker row(s) reference marker rows (%). Remove those references first; rollback never deletes application-owned rows.',
      (SELECT sum(refs) FROM _blocking),
      (SELECT string_agg(tbl || '=' || refs, ', ' ORDER BY tbl) FROM _blocking WHERE refs > 0)
      USING ERRCODE = '23503', CONSTRAINT = 'thesis_demo_rollback_preflight';
  END IF;
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
  IF v_comment LIKE 'thesis-demo-data v2 md5:%' THEN
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
     OR coalesce(v_img->>'format', '') <> '2'
     OR jsonb_typeof(v_img->'cols') IS DISTINCT FROM 'array'
     OR coalesce(jsonb_array_length(v_img->'cols'), 0) = 0
     OR jsonb_typeof(v_img->'rows') IS DISTINCT FROM 'object' THEN
    RAISE EXCEPTION 'rollback aborted: marker rows exist but the before-image is missing or unreadable (audit-row copy and checksum-protected comment copy both failed) — nothing was changed; rebuild it with recover-before-image.sql first'
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_rollback_image';
  END IF;
  INSERT INTO _image VALUES (v_img, v_src);
END $$;

-- 4) content check: every marker row is re-hashed and compared with the hash
--    the image recorded at apply time (whole-row md5, order-free by id).
CREATE TEMP TABLE _hash_actual (tbl text, id text, h text) ON COMMIT DROP;

DO $$
DECLARE
  r record;
  v_img jsonb;
BEGIN
  SELECT img INTO v_img FROM _image;
  IF v_img IS NULL THEN RETURN; END IF;
  FOR r IN SELECT e.key AS tbl FROM jsonb_each(v_img->'rows') e ORDER BY 1 LOOP
    IF r.tbl NOT IN ('thesis.thesis_topic','thesis.thesis_topic_supervisor','thesis.thesis_group',
                     'thesis.thesis_group_member','thesis.thesis_council','thesis.thesis_council_member',
                     'thesis.thesis_council_topic','thesis.thesis_topic_score','thesis.thesis_group_report',
                     'notifications.notification','campuscore_audit."AdminAudit"') THEN
      RAISE EXCEPTION 'rollback aborted: before-image names an unknown table (%) — refusing to trust a corrupted image', r.tbl
        USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_rollback_image';
    END IF;
    EXECUTE format(
      'INSERT INTO _hash_actual SELECT %L, id::text, md5(t::text) FROM %s t WHERE %s',
      r.tbl, r.tbl,
      CASE r.tbl
        WHEN 'notifications.notification'      THEN 'id LIKE ''demo-th-%'''
        WHEN 'campuscore_audit."AdminAudit"'   THEN 'id LIKE ''demo-th-%'' AND id <> ''demo-th-audit-000'''
        ELSE 'id::text LIKE ''ed1a0000-0000-4000-8000-%''' END);
  END LOOP;
END $$;

CREATE TEMP TABLE _hash_mismatch (tbl text, n bigint) ON COMMIT DROP;

DO $$
DECLARE v_img jsonb;
BEGIN
  SELECT img INTO v_img FROM _image;
  IF v_img IS NULL THEN RETURN; END IF;
  INSERT INTO _hash_mismatch
  WITH exp AS (
    SELECT rt.key AS tbl, e.key AS id, e.value AS h
    FROM _image i, jsonb_each(i.img->'rows') rt, jsonb_each_text(rt.value) e
  )
  SELECT coalesce(e.tbl, a.tbl) AS tbl, count(*) AS n
  FROM exp e
  FULL OUTER JOIN _hash_actual a ON a.tbl = e.tbl AND a.id = e.id
  WHERE e.h IS NULL OR a.h IS NULL OR a.h <> e.h
  GROUP BY 1;
  IF EXISTS (SELECT 1 FROM _hash_mismatch) THEN
    RAISE EXCEPTION 'rollback aborted: % marker row(s) differ from the apply-time content hash (%) — the application touched demo data; nothing was deleted. Inspect the reported table(s), then either restore the rows or remove the markers deliberately.',
      (SELECT sum(n) FROM _hash_mismatch),
      (SELECT string_agg(tbl || '=' || n, ', ' ORDER BY tbl) FROM _hash_mismatch)
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_rollback_hash';
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
  IF obj_description('thesis.thesis_topic'::regclass) LIKE 'thesis-demo-data v2 %' THEN
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
UNION ALL SELECT 'image_comment_copy', coalesce((obj_description('thesis.thesis_topic'::regclass) LIKE 'thesis-demo-data v2 %')::int, 0)
UNION ALL SELECT 'baseline.topics', (SELECT count(*) FROM thesis.thesis_topic WHERE id::text NOT LIKE 'ed1a0000-0000-4000-8000-%')
UNION ALL SELECT 'baseline.groups', (SELECT count(*) FROM thesis.thesis_group WHERE id::text NOT LIKE 'ed1a0000-0000-4000-8000-%')
ORDER BY 1;

COMMIT;
