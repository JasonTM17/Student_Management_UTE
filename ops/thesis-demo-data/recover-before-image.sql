-- Thesis demo-data before-image recovery.
--
-- Rebuilds audit row 'demo-th-audit-000' and the checksum-protected comment
-- copy when BOTH durable copies of the apply-time before-image were lost
-- (audit pruning, manual deletion). rollback.sql refuses to run without a
-- valid image, so this script is the documented way back.
--
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f recover-before-image.sql
--
-- (The script manages its own single transaction; do NOT pass -1.)
--
-- What it does, in one transaction:
--   1) takes SHARE ROW EXCLUSIVE locks in the application's write order
--      (thesis tables first, notification + audit last), lock_timeout 300 ms;
--   2) REFUSES when no marker rows exist (nothing to recover) and when a
--      usable image copy still exists (fix the bad copy deliberately first);
--   3) rebuilds the v3 image FROM RECORDED FACTS ONLY — never from live
--      marker-row content:
--        'cols'     — the documented guarded-column before/applied values,
--                     but ONLY for entries whose current value still equals
--                     what apply wrote. An entry the app has since drifted is
--                     dropped (and counted), so recovery can never arm a
--                     restore against an application change.
--        'rows' +   — the expected applied content hashes checked into the
--        'hashcols'   package (expected-image.sql), NOT a re-hash of whatever
--                     happens to be live right now. This is what keeps the
--                     old P11 failure impossible: if the application edited
--                     or re-uploaded a marker row while the image was gone,
--                     the rebuilt image still holds the ORIGINAL hash, and
--                     rollback classifies that row as 'differed' and REFUSES
--                     — it never deletes the application's version.
--   4) compares live marker rows against the expected image and reports how
--      many already differ (informational — rollback enforces it);
--   5) writes the image back into the audit row (created when missing) and
--      into the table comment; afterwards `run.mjs rollback` works normally.

BEGIN;

SET LOCAL lock_timeout = '300ms';
SET LOCAL statement_timeout = '120s';
SET LOCAL TimeZone = 'UTC';
SET LOCAL DateStyle = 'ISO, MDY';
SET LOCAL bytea_output = 'hex';
SET LOCAL extra_float_digits = 0;
SET LOCAL IntervalStyle = 'iso_8601';
SET LOCAL standard_conforming_strings = 'on';

-- locks in the application's write order: thesis tables first, then the hot
-- app-wide tables last. Same 300 ms fail-fast rule as rollback.
DO $$
BEGIN
  BEGIN
    EXECUTE 'LOCK TABLE thesis.thesis_council, thesis.thesis_council_member, thesis.thesis_council_topic,'
         || ' thesis.thesis_group, thesis.thesis_group_member, thesis.thesis_group_report,'
         || ' thesis.thesis_registration_round, thesis.thesis_topic, thesis.thesis_topic_score,'
         || ' thesis.thesis_topic_supervisor IN SHARE ROW EXCLUSIVE MODE';
  EXCEPTION WHEN lock_not_available THEN
    RAISE EXCEPTION 'recovery aborted: could not lock the package tables within lock_timeout — an application write is in progress; nothing was changed'
      USING ERRCODE = '55P03', CONSTRAINT = 'thesis_demo_recover_lock';
  END;
  BEGIN
    EXECUTE 'LOCK TABLE notifications.notification, campuscore_audit."AdminAudit" IN SHARE ROW EXCLUSIVE MODE';
  EXCEPTION WHEN lock_not_available THEN
    RAISE EXCEPTION 'recovery aborted: could not lock the audit/notification tables within lock_timeout — an application write is in progress; nothing was changed'
      USING ERRCODE = '55P03', CONSTRAINT = 'thesis_demo_recover_lock';
  END;
END $$;

-- expected applied content (generated file; see its header for regeneration).
\ir expected-image.sql

-- marker tables/predicates and the hash column lists, identical to
-- apply.sql / rollback.sql — needed for the live-vs-expected comparison.
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

CREATE TEMP TABLE _live_hashes (tbl text, id text, hashes jsonb) ON COMMIT DROP;

DO $$
DECLARE
  v_markers bigint;
  v_has_img boolean;
  v_exp jsonb;
  v_cols jsonb;
  v_img jsonb;
  v_kept int := 0;
  v_dropped int := 0;
  v_still boolean;
  v_differs bigint := 0;
  v_missing bigint := 0;
  v_extra bigint := 0;
  r record;
  m record;
BEGIN
  SELECT (
    (SELECT count(*) FROM thesis.thesis_topic            WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
    (SELECT count(*) FROM thesis.thesis_topic_supervisor WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
    (SELECT count(*) FROM thesis.thesis_group            WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
    (SELECT count(*) FROM thesis.thesis_group_member     WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
    (SELECT count(*) FROM thesis.thesis_council          WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
    (SELECT count(*) FROM thesis.thesis_council_member   WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
    (SELECT count(*) FROM thesis.thesis_council_topic    WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
    (SELECT count(*) FROM thesis.thesis_topic_score      WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
    (SELECT count(*) FROM thesis.thesis_group_report     WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
    (SELECT count(*) FROM notifications.notification     WHERE id LIKE 'demo-th-%') +
    (SELECT count(*) FROM campuscore_audit."AdminAudit"  WHERE id LIKE 'demo-th-%')
  ) INTO v_markers;
  IF v_markers = 0 THEN
    RAISE EXCEPTION 'recovery refused: no marker rows present — nothing to recover'
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_recover_guard';
  END IF;

  SELECT EXISTS (
           SELECT 1 FROM campuscore_audit."AdminAudit"
            WHERE id = 'demo-th-audit-000' AND "beforeState" ~ '^\s*\{')
         OR obj_description('thesis.thesis_topic'::regclass) LIKE 'thesis-demo-data v3 %'
    INTO v_has_img;
  IF v_has_img THEN
    RAISE EXCEPTION 'recovery refused: a before-image copy still exists — rebuilding could bless a wrong baseline; inspect it instead'
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_recover_guard';
  END IF;

  SELECT img INTO v_exp FROM _expected_image;
  IF v_exp IS NULL
     OR jsonb_typeof(v_exp->'rows') IS DISTINCT FROM 'object'
     OR jsonb_typeof(v_exp->'hashcols') IS DISTINCT FROM 'object' THEN
    RAISE EXCEPTION 'recovery impossible: expected-image.sql is missing or malformed — regenerate it from a clean apply on the documented baseline'
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_recover_guard';
  END IF;
  -- the expected image must describe exactly this package's hash column lists
  IF EXISTS (
    SELECT 1 FROM _hashcols hc
    GROUP BY hc.tbl
    HAVING (SELECT coalesce(jsonb_agg(c ORDER BY c), '[]'::jsonb)
              FROM jsonb_array_elements_text(coalesce(v_exp->'hashcols'->hc.tbl, '[]'::jsonb)) c)
           IS DISTINCT FROM jsonb_agg(hc.col ORDER BY hc.col)) THEN
    RAISE EXCEPTION 'recovery impossible: expected-image.sql hash column lists do not match this package — regenerate it'
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_recover_guard';
  END IF;

  -- guarded columns: only the documented entries whose current value STILL
  -- equals what apply wrote are recovered; drifted entries are dropped.
  v_cols := '[]'::jsonb;
  FOR r IN
    SELECT c.tbl, c.id, c.col, c.before, c.applied,
           format_type(a.atttypid, a.atttypmod) AS coltype
      FROM (VALUES
        ('thesis_registration_round','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','version','5','6'),
        ('thesis_registration_round','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','updated_at','2026-09-12T02:02:51.808842+00:00','2026-10-16T17:00:00+00:00'),
        ('thesis_registration_round','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','results_published_at',NULL,'2026-10-16T17:00:00+00:00'),
        ('thesis_registration_round','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','version','5','6'),
        ('thesis_registration_round','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','updated_at','2026-09-09T17:11:14.668478+00:00','2026-10-23T09:00:00+00:00'),
        ('thesis_registration_round','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','results_published_at',NULL,'2026-10-23T09:00:00+00:00'),
        ('thesis_registration_round','bad8eba0-2d12-4414-8aab-7424174b3b97','version','5','6'),
        ('thesis_registration_round','bad8eba0-2d12-4414-8aab-7424174b3b97','updated_at','2026-09-12T17:05:51.854712+00:00','2026-10-22T09:00:00+00:00'),
        ('thesis_registration_round','bad8eba0-2d12-4414-8aab-7424174b3b97','results_published_at',NULL,'2026-10-22T09:00:00+00:00'),
        ('thesis_topic','018f4a7c-3b12-4d90-a5e1-7c82b9045001','version','0','1'),
        ('thesis_topic','018f4a7c-3b12-4d90-a5e1-7c82b9045001','updated_at','2026-09-17T05:58:25.896143+00:00','2026-10-23T15:30:00+00:00'),
        ('thesis_topic','018f4a7c-3b12-4d90-a5e1-7c82b9045001','gvpb_lecturer_id',NULL,'lecturer-profile-025'),
        ('thesis_topic','6f64670b-cd6b-4e1b-ae9d-0b497f2472a8','version','0','1'),
        ('thesis_topic','6f64670b-cd6b-4e1b-ae9d-0b497f2472a8','updated_at','2026-09-17T05:58:25.896143+00:00','2026-10-23T15:30:00+00:00'),
        ('thesis_topic','6f64670b-cd6b-4e1b-ae9d-0b497f2472a8','gvpb_lecturer_id',NULL,'lecturer-profile-024'),
        ('thesis_topic','f580eadf-fffb-4db2-a7d8-423e83934dc2','version','0','1'),
        ('thesis_topic','f580eadf-fffb-4db2-a7d8-423e83934dc2','updated_at','2026-09-17T05:58:25.896143+00:00','2026-10-23T15:30:00+00:00'),
        ('thesis_topic','f580eadf-fffb-4db2-a7d8-423e83934dc2','gvpb_lecturer_id',NULL,'lecturer-profile-013'),
        ('thesis_topic','67c74df5-5c7f-4e74-aa02-d51af7207fe9','version','1','2'),
        ('thesis_topic','67c74df5-5c7f-4e74-aa02-d51af7207fe9','updated_at','2026-10-19T08:32:54.276145+00:00','2026-10-23T15:30:00+00:00'),
        ('thesis_topic','67c74df5-5c7f-4e74-aa02-d51af7207fe9','gvpb_waived_reason',NULL,'Miễn phản biện theo Quyết định 214/QĐ-KCNTT: đề tài thuộc nhóm chuyên ngành hẹp, đã qua hai vòng xét duyệt của Bộ môn'),
        ('thesis_topic','67c74df5-5c7f-4e74-aa02-d51af7207fe9','gvpb_waived_by',NULL,'truongkhoa-user-001'),
        ('thesis_topic','67c74df5-5c7f-4e74-aa02-d51af7207fe9','gvpb_waived_at',NULL,'2026-10-15T09:00:00+00:00')
      ) c(tbl, id, col, before, applied)
      LEFT JOIN pg_attribute a ON a.attrelid = ('thesis.' || c.tbl)::regclass
                              AND a.attname = c.col AND a.attnum > 0 AND NOT a.attisdropped
  LOOP
    v_still := false;
    IF r.coltype IS NOT NULL THEN
      EXECUTE format('SELECT to_jsonb(%I) #>> ''{}'' IS NOT DISTINCT FROM $1 FROM thesis.%I WHERE id::text = $2',
                     r.col, r.tbl)
         INTO v_still USING r.applied, r.id;
    END IF;
    IF v_still THEN
      v_cols := v_cols || jsonb_build_object('tbl', r.tbl, 'id', r.id, 'col', r.col,
                                             'before', r.before, 'applied', r.applied);
      v_kept := v_kept + 1;
    ELSE
      v_dropped := v_dropped + 1;
    END IF;
  END LOOP;

  v_img := jsonb_build_object('format', 3, 'cols', v_cols,
                              'hashcols', v_exp->'hashcols', 'rows', v_exp->'rows');

  -- informational: compare live marker rows with the expected content so the
  -- operator sees upfront what rollback will classify as 'differed'.
  FOR m IN
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
      GROUP BY hc.tbl
  LOOP
    EXECUTE format('INSERT INTO _live_hashes SELECT %L, id::text, jsonb_build_object(%s) FROM %s t WHERE %s',
                   m.tbl, m.pairs, m.tbl,
                   (SELECT pred FROM _tables WHERE tbl = m.tbl));
  END LOOP;
  SELECT coalesce(sum(x.d), 0), coalesce(sum(x.m), 0), coalesce(sum(x.e), 0)
    INTO v_differs, v_missing, v_extra
    FROM (
      SELECT (SELECT count(*) FROM jsonb_each(v_exp->'rows'->t.tbl) e
               WHERE NOT EXISTS (SELECT 1 FROM _live_hashes l
                                  WHERE l.tbl = t.tbl AND l.id = e.key)) AS m,
             (SELECT count(*) FROM jsonb_each(v_exp->'rows'->t.tbl) e
               JOIN _live_hashes l ON l.tbl = t.tbl AND l.id = e.key
               WHERE NOT EXISTS (SELECT 1 FROM jsonb_object_keys(e.value) k WHERE l.hashes ? k)
                  OR EXISTS (SELECT 1 FROM jsonb_each_text(e.value) v
                              WHERE l.hashes ? v.key AND l.hashes->>v.key IS DISTINCT FROM v.value)) AS d,
             (SELECT count(*) FROM _live_hashes l
               WHERE l.tbl = t.tbl
                 AND NOT (v_exp->'rows'->t.tbl ? l.id)) AS e
        FROM _tables t
    ) x;

  INSERT INTO campuscore_audit."AdminAudit"
    (id, "actorId", "actorLabel", action, "entityType", "entityId", summary, "beforeState", "afterState", "createdAt")
  VALUES ('demo-th-audit-000','admin-user',NULL,'THESIS_DEMO_DATA_ENRICHED','THESIS_PACKAGE','thesis-demo-data',
          'Before-image rebuilt by recover-before-image.sql from recorded baseline facts (expected-image.sql); drifted guarded columns dropped; live row edits remain rollback blockers',
          v_img::text, NULL, CURRENT_TIMESTAMP)
  ON CONFLICT (id) DO UPDATE SET "beforeState" = EXCLUDED."beforeState";

  EXECUTE format('COMMENT ON TABLE thesis.thesis_topic IS %L',
    'thesis-demo-data v3 md5:' || md5(v_img::text) || E'\n' || v_img::text);

  RAISE NOTICE 'before-image rebuilt from recorded baseline: % guarded-column entries kept (% dropped as app-drifted), % expected marker rows — % live row(s) differ, % app-deleted, % unexpected; rollback will refuse on differed/unexpected rows',
    v_kept, v_dropped,
    (SELECT count(*) FROM jsonb_each(v_exp->'rows') rt
       CROSS JOIN LATERAL jsonb_each(rt.value)),
    v_differs, v_missing, v_extra;
END $$;

COMMIT;
