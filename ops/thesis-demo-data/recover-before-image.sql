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
--   1) takes the same fixed-order SHARE ROW EXCLUSIVE table locks as
--      rollback.sql, under the same 5 s lock_timeout;
--   2) REFUSES when no marker rows exist (nothing to recover) and when a
--      usable image copy still exists (rebuilding could bless a wrong
--      baseline — fix the bad copy instead);
--   3) rebuilds the v2 image: the 'cols' section restores the KNOWN baseline
--      values of the 23 guarded columns of the documented production
--      snapshot, and the 'rows' section re-hashes the marker rows as they are
--      RIGHT NOW. That means recovery assumes the marker rows are still in
--      their applied state — if the application has edited them, inspect and
--      decide deliberately before running rollback.
--   4) writes the image back into the audit row (created when missing) and
--      into the table comment; afterwards `run.mjs rollback` works normally
--      and restores the baseline through the same conditional-restore rule
--      (a column is only reverted while its current value still equals the
--      value apply wrote, so post-apply application changes are never
--      clobbered).

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';
SET LOCAL TimeZone = 'UTC';
SET LOCAL DateStyle = 'ISO, MDY';

DO $$
BEGIN
  BEGIN
    EXECUTE 'LOCK TABLE campuscore_audit."AdminAudit", notifications.notification,'
         || ' thesis.thesis_council, thesis.thesis_council_member, thesis.thesis_council_topic,'
         || ' thesis.thesis_group, thesis.thesis_group_member, thesis.thesis_group_report,'
         || ' thesis.thesis_registration_round, thesis.thesis_topic, thesis.thesis_topic_score,'
         || ' thesis.thesis_topic_supervisor IN SHARE ROW EXCLUSIVE MODE';
  EXCEPTION WHEN lock_not_available THEN
    RAISE EXCEPTION 'recovery aborted: could not lock the package tables within lock_timeout — an application write is in progress; nothing was changed'
      USING ERRCODE = '55P03', CONSTRAINT = 'thesis_demo_recover_lock';
  END;
END $$;

DO $$
DECLARE
  v_markers bigint;
  v_has_img boolean;
  v_rows jsonb;
  v_img jsonb;
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
         OR obj_description('thesis.thesis_topic'::regclass) LIKE 'thesis-demo-data v2 %'
    INTO v_has_img;
  IF v_has_img THEN
    RAISE EXCEPTION 'recovery refused: a before-image copy still exists — rebuilding could bless a wrong baseline; inspect it instead'
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_recover_guard';
  END IF;

  -- live content hashes of the marker rows as they are now
  SELECT jsonb_object_agg(tbl, ids) INTO v_rows FROM (
    SELECT 'thesis.thesis_topic' AS tbl, jsonb_object_agg(id::text, md5(t::text)) AS ids
      FROM thesis.thesis_topic t WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%' GROUP BY 1
    UNION ALL
    SELECT 'thesis.thesis_topic_supervisor', jsonb_object_agg(id::text, md5(t::text))
      FROM thesis.thesis_topic_supervisor t WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%' GROUP BY 1
    UNION ALL
    SELECT 'thesis.thesis_group', jsonb_object_agg(id::text, md5(t::text))
      FROM thesis.thesis_group t WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%' GROUP BY 1
    UNION ALL
    SELECT 'thesis.thesis_group_member', jsonb_object_agg(id::text, md5(t::text))
      FROM thesis.thesis_group_member t WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%' GROUP BY 1
    UNION ALL
    SELECT 'thesis.thesis_council', jsonb_object_agg(id::text, md5(t::text))
      FROM thesis.thesis_council t WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%' GROUP BY 1
    UNION ALL
    SELECT 'thesis.thesis_council_member', jsonb_object_agg(id::text, md5(t::text))
      FROM thesis.thesis_council_member t WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%' GROUP BY 1
    UNION ALL
    SELECT 'thesis.thesis_council_topic', jsonb_object_agg(id::text, md5(t::text))
      FROM thesis.thesis_council_topic t WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%' GROUP BY 1
    UNION ALL
    SELECT 'thesis.thesis_topic_score', jsonb_object_agg(id::text, md5(t::text))
      FROM thesis.thesis_topic_score t WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%' GROUP BY 1
    UNION ALL
    SELECT 'thesis.thesis_group_report', jsonb_object_agg(id::text, md5(t::text))
      FROM thesis.thesis_group_report t WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%' GROUP BY 1
    UNION ALL
    SELECT 'notifications.notification', jsonb_object_agg(id::text, md5(t::text))
      FROM notifications.notification t WHERE id LIKE 'demo-th-%' GROUP BY 1
    UNION ALL
    SELECT 'campuscore_audit."AdminAudit"', jsonb_object_agg(id::text, md5(t::text))
      FROM campuscore_audit."AdminAudit" t WHERE id LIKE 'demo-th-%' AND id <> 'demo-th-audit-000' GROUP BY 1
  ) s;

  -- the 23 guarded-column before/applied values of the documented Oct-24
  -- production snapshot (flyway 142); 'before' values are ISO 8601 with offset
  v_img := jsonb_build_object(
    'format', 2,
    'cols', (SELECT jsonb_agg(jsonb_build_object('tbl', c.tbl, 'id', c.id, 'col', c.col,
                                                 'before', c.before, 'applied', c.applied))
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
      ) c(tbl, id, col, before, applied)),
    'rows', v_rows);

  INSERT INTO campuscore_audit."AdminAudit"
    (id, "actorId", "actorLabel", action, "entityType", "entityId", summary, "beforeState", "afterState", "createdAt")
  VALUES ('demo-th-audit-000','admin-user',NULL,'THESIS_DEMO_DATA_ENRICHED','THESIS_PACKAGE','thesis-demo-data',
          'Before-image rebuilt by recover-before-image.sql after both durable copies were lost; guarded-column baseline values of the documented Oct-24 production snapshot',
          v_img::text, NULL, CURRENT_TIMESTAMP)
  ON CONFLICT (id) DO UPDATE SET "beforeState" = EXCLUDED."beforeState";

  EXECUTE format('COMMENT ON TABLE thesis.thesis_topic IS %L',
    'thesis-demo-data v2 md5:' || md5(v_img::text) || E'\n' || v_img::text);

  RAISE NOTICE 'before-image rebuilt: % guarded-column entries, % marker-table hash maps — run rollback.sql now',
    jsonb_array_length(v_img->'cols'), (SELECT count(*) FROM jsonb_each(v_img->'rows'));
END $$;

COMMIT;
