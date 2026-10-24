-- Thesis demo-data enrichment package (ops/thesis-demo-data/apply.sql)
-- Fills the sparse thesis rounds with internally consistent demo rows that
-- exercise every workflow state. Idempotent: every write is keyed by the
-- deterministic marker id namespace 'ed1a0000-0000-4000-8000-%' (thesis.* uuids)
-- or 'demo-th-%' (notification / audit ids) plus ON CONFLICT / existence guards,
-- so a second run is a proven no-op. Run with (the script manages its own
-- single transaction; do NOT pass -1):
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v dry_run=0 -f apply.sql
-- dry-run: -v dry_run=1 prints the same counts then rolls everything back.
--
-- SAFETY: the guard block aborts unless the database is exactly the expected
-- production baseline (flyway >= 142, the seven known rounds present with their
-- recon statuses and baseline row counts), is already fully enriched, or is
-- fully enriched minus marker rows the application deleted afterwards. On the
-- baseline path it also aborts when a guarded column already carries a value
-- (pre-existing reviewer, waiver or council assignment), so apply can never
-- insert a counter-review score that mismatches an existing reviewer.
--
-- STATE CLASSIFICATION (exact, id-set based — see _class below):
--   absent              zero marker rows                     -> apply
--   full                live marker ids == image ids         -> 'already applied'
--   app_deleted_subset  live ids are a strict subset of the  -> 'already applied'
--                       image ids (the app deleted some of   no-op; re-adding
--                       them, e.g. a results retract)        what the app
--                                                            removed is wrong
--   foreign_partial     any marker rows exist but the image  -> REFUSE with the
--                       is missing/unreadable, or live       recovery steps
--                       contains ids the image does not
--
-- LOCKING: before anything is checked or written the script takes SHARE ROW
-- EXCLUSIVE locks on every table it touches, IN THE APPLICATION'S WRITE ORDER
-- — the thesis business tables first (the app writes them first), then
-- notifications.notification and campuscore_audit."AdminAudit" last (the app
-- writes its audit row last). SHARE ROW EXCLUSIVE is the weakest level that
-- blocks application INSERT/UPDATE/DELETE (ROW EXCLUSIVE) and serializes two
-- concurrent package runs, while plain reads stay untouched. That removes
-- every check-then-act window between the guard and the writes. The locks are
-- held until COMMIT; the script is short (sub-second on a local connection, a
-- few seconds over a remote one), so that is also the longest an application
-- write can ever wait behind it. lock_timeout = 300 ms — deliberately below
-- the default deadlock_timeout (1 s) — makes the package ALWAYS the victim of
-- any lock-ordering conflict: it aborts cleanly instead of queueing, and the
-- application transaction is never killed. run.mjs retries the whole script a
-- bounded number of times with jitter, each attempt a fresh transaction, so no
-- lock is ever held while the runner waits. Run it in a quiet maintenance
-- window, exactly like rollback.

BEGIN;

-- every session setting that can change text/bytea/timestamp rendering is
-- pinned for the transaction: the image hashes must round-trip identically
-- under any client session settings (the runner also uses psql -X so no
-- startup file can pre-set different values).
SET LOCAL lock_timeout = '300ms';
SET LOCAL statement_timeout = '120s';
SET LOCAL TimeZone = 'UTC';
SET LOCAL DateStyle = 'ISO, MDY';
SET LOCAL bytea_output = 'hex';
SET LOCAL extra_float_digits = 0;
SET LOCAL IntervalStyle = 'iso_8601';
SET LOCAL standard_conforming_strings = 'on';

-- locks in the application's own write order: thesis business tables first,
-- the hot app-wide notification and audit tables last.
DO $$
BEGIN
  BEGIN
    EXECUTE 'LOCK TABLE thesis.thesis_council, thesis.thesis_council_member, thesis.thesis_council_topic,'
         || ' thesis.thesis_group, thesis.thesis_group_member, thesis.thesis_group_report,'
         || ' thesis.thesis_registration_round, thesis.thesis_topic, thesis.thesis_topic_score,'
         || ' thesis.thesis_topic_supervisor, notifications.notification,'
         || ' campuscore_audit."AdminAudit" IN SHARE ROW EXCLUSIVE MODE';
  EXCEPTION WHEN lock_not_available THEN
    RAISE EXCEPTION 'apply aborted: could not lock the package tables within lock_timeout — an application write is in progress; nothing was changed, retry when the app is idle'
      USING ERRCODE = '55P03', CONSTRAINT = 'thesis_demo_apply_lock';
  END;
END $$;

-- marker census: computed once while the locks are held and reused by the
-- guard, the already-applied branch and the final summary.
CREATE TEMP TABLE _marker_counts ON COMMIT DROP AS
SELECT s.name, s.expected,
  CASE s.name
    WHEN 'thesis_topic'            THEN (SELECT count(*) FROM thesis.thesis_topic            WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%')
    WHEN 'thesis_topic_supervisor' THEN (SELECT count(*) FROM thesis.thesis_topic_supervisor WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%')
    WHEN 'thesis_group'            THEN (SELECT count(*) FROM thesis.thesis_group            WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%')
    WHEN 'thesis_group_member'     THEN (SELECT count(*) FROM thesis.thesis_group_member     WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%')
    WHEN 'thesis_council'          THEN (SELECT count(*) FROM thesis.thesis_council          WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%')
    WHEN 'thesis_council_member'   THEN (SELECT count(*) FROM thesis.thesis_council_member   WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%')
    WHEN 'thesis_council_topic'    THEN (SELECT count(*) FROM thesis.thesis_council_topic    WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%')
    WHEN 'thesis_topic_score'      THEN (SELECT count(*) FROM thesis.thesis_topic_score      WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%')
    WHEN 'thesis_group_report'     THEN (SELECT count(*) FROM thesis.thesis_group_report     WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%')
    WHEN 'notification'            THEN (SELECT count(*) FROM notifications.notification     WHERE id LIKE 'demo-th-%')
    WHEN 'admin_audit'             THEN (SELECT count(*) FROM campuscore_audit."AdminAudit"  WHERE id LIKE 'demo-th-%')
  END AS cnt
FROM (VALUES ('thesis_topic',17),('thesis_topic_supervisor',18),('thesis_group',20),
             ('thesis_group_member',64),('thesis_council',4),('thesis_council_member',16),
             ('thesis_council_topic',16),('thesis_topic_score',62),('thesis_group_report',17),
             ('notification',107),('admin_audit',6)) s(name, expected);

-- the eleven marker tables and their marker-row predicates, reused by the
-- state classification and the hash capture.
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

-- explicit, versioned per-table column lists the row hashes cover: every
-- column the package writes EXCEPT volatile bookkeeping columns the
-- application legitimately bumps on its own (version, updated_at everywhere;
-- is_read/read_at on notifications — markAllRead touches those). A future
-- migration that ADDS a column therefore changes no hash; a migration that
-- drops a hashed column simply removes it from the comparison (the remaining
-- columns are still verified). The same literal list lives in rollback.sql
-- and must stay identical.
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

-- state classification: compare the live marker-id set with the ids recorded
-- in the before-image (audit row first, checksum-verified comment copy as
-- fallback). Outcome decides whether this script applies, no-ops, or refuses.
CREATE TEMP TABLE _class (state text, missing_rows bigint, extra_rows bigint, detail text) ON COMMIT DROP;

DO $$
DECLARE
  v_live bigint;
  v_img jsonb;
  v_copy jsonb;
  v_raw text;
  v_comment text;
  v_decl text;
  v_payload text;
  v_missing bigint := 0;
  v_extra bigint := 0;
  v_m bigint;
  v_x bigint;
  v_detail text := '';
  r record;
BEGIN
  SELECT coalesce(sum(cnt), 0) INTO v_live FROM _marker_counts;
  IF v_live = 0 THEN
    INSERT INTO _class VALUES ('absent', 0, 0, '');
    RETURN;
  END IF;

  -- locate a usable before-image: audit-row copy first, then the
  -- checksum-protected comment copy (md5 verified before trusting it).
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
  v_img := coalesce(v_img, v_copy);
  IF v_img IS NULL
     OR coalesce(v_img->>'format', '') <> '3'
     OR jsonb_typeof(v_img->'rows') IS DISTINCT FROM 'object' THEN
    INSERT INTO _class VALUES ('foreign_partial', 0, 0, 'before-image missing or unreadable');
    RETURN;
  END IF;

  FOR r IN SELECT tbl, pred FROM _tables LOOP
    EXECUTE format(
      'WITH exp AS (SELECT e.key AS id FROM jsonb_each(coalesce($1, ''{}''::jsonb)) e),
            act AS (SELECT id::text AS id FROM %s t WHERE %s)
       SELECT (SELECT count(*) FROM exp e WHERE NOT EXISTS (SELECT 1 FROM act a WHERE a.id = e.id)),
              (SELECT count(*) FROM act a WHERE NOT EXISTS (SELECT 1 FROM exp e WHERE e.id = a.id))',
      r.tbl, r.pred)
      INTO v_m, v_x USING (v_img->'rows'->r.tbl);
    v_missing := v_missing + v_m;
    v_extra := v_extra + v_x;
    IF v_m > 0 THEN v_detail := v_detail || r.tbl || '=' || v_m || ' '; END IF;
  END LOOP;
  INSERT INTO _class VALUES (
    CASE WHEN v_extra > 0 THEN 'foreign_partial'
         WHEN v_missing > 0 THEN 'app_deleted_subset'
         ELSE 'full' END,
    v_missing, v_extra, trim(v_detail));
END $$;

DO $$
DECLARE
  v_max int;
  v_state text;
BEGIN
  SELECT max(version::int) INTO v_max FROM thesis.flyway_schema_history WHERE success;
  IF v_max IS NULL OR v_max < 142 THEN
    RAISE EXCEPTION 'demo-data guard: expected flyway schema >= 142, found %', v_max
      USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
  END IF;

  -- real/ambiguous accounts are classified by explicit id, never by email
  -- domain: a real account may legitimately carry a campuscore-domain address.
  -- The package must never reference them; verify.sql enforces the same list.
  IF EXISTS (
    SELECT 1 FROM (
      SELECT student_id AS ref FROM thesis.thesis_group_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
      UNION ALL SELECT lecturer_id FROM thesis.thesis_topic_supervisor WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
      UNION ALL SELECT lecturer_id FROM thesis.thesis_council_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
      UNION ALL SELECT lecturer_id FROM thesis.thesis_topic_score WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
      UNION ALL SELECT user_id FROM notifications.notification WHERE id LIKE 'demo-th-%'
    ) r
    WHERE r.ref IN ('16a1b2be-6556-4d47-83d9-e9a07daf109d',
                    '39b5f10c-ebba-4fbf-8beb-595ebf5f4a70',
                    'b3b4b4cd-3f5d-44a8-9b68-5ad06916423c',
                    'ec54ede5-1660-4161-98e3-2ad733c17e74')) THEN
    RAISE EXCEPTION 'demo-data guard: a known real account id is referenced by package rows — refusing to run'
      USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
  END IF;

  -- refuse a foreign partial state: marker rows exist but the before-image is
  -- missing/unreadable, or live marker ids exist that the image never wrote.
  -- The exact way back is recovery (it rebuilds the image from the manifest,
  -- not from live content) followed by rollback, or a deliberate marker
  -- cleanup — never a blind re-apply.
  SELECT state INTO v_state FROM _class;
  IF v_state = 'foreign_partial' THEN
    RAISE EXCEPTION 'demo-data guard: foreign partial marker state (%) — marker rows exist but the before-image cannot vouch for them. Run recover-before-image.sql to rebuild the image from the recorded baseline, then rollback.sql; or remove the marker rows deliberately. Re-apply is refused.',
      (SELECT detail FROM _class)
      USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
  END IF;
  IF v_state = 'absent' THEN
    -- pristine baseline: the five touched rounds must still look like recon.
    -- Counts are upper bounds so both the production baseline and the verified
    -- pre-deploy snapshot (which predates some prod drift) are accepted.
    IF NOT EXISTS (SELECT 1 FROM thesis.thesis_registration_round WHERE id = 'b35e9ff1-1eb8-4e73-812e-04e1a66041f1' AND status = 'RESULTS_PUBLISHED')
       OR (SELECT count(*) FROM thesis.thesis_topic WHERE round_id = 'b35e9ff1-1eb8-4e73-812e-04e1a66041f1') > 2 THEN
      RAISE EXCEPTION 'demo-data guard: round % no longer matches the recon baseline (published, <=2 topics)', 'b35e9ff1-1eb8-4e73-812e-04e1a66041f1'
        USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM thesis.thesis_registration_round WHERE id = 'be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1' AND status = 'RESULTS_PUBLISHED')
       OR (SELECT count(*) FROM thesis.thesis_topic WHERE round_id = 'be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1') > 1 THEN
      RAISE EXCEPTION 'demo-data guard: round % no longer matches the recon baseline (published, <=1 topic)', 'be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1'
        USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM thesis.thesis_registration_round WHERE id = 'bad8eba0-2d12-4414-8aab-7424174b3b97' AND status = 'RESULTS_PUBLISHED')
       OR (SELECT count(*) FROM thesis.thesis_topic WHERE round_id = 'bad8eba0-2d12-4414-8aab-7424174b3b97') > 1 THEN
      RAISE EXCEPTION 'demo-data guard: round % no longer matches the recon baseline (published, <=1 topic)', 'bad8eba0-2d12-4414-8aab-7424174b3b97'
        USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM thesis.thesis_registration_round WHERE id = '22d65ee6-f485-40ae-a66b-528d35007745' AND status IN ('REGISTRATION_OPEN','REGISTRATION_CLOSED'))
       OR (SELECT count(*) FROM thesis.thesis_topic WHERE round_id = '22d65ee6-f485-40ae-a66b-528d35007745') > 10
       OR (SELECT count(*) FROM thesis.thesis_group WHERE round_id = '22d65ee6-f485-40ae-a66b-528d35007745') > 4 THEN
      RAISE EXCEPTION 'demo-data guard: round % no longer matches the recon baseline (registration phase, <=10 topics, <=4 groups)', '22d65ee6-f485-40ae-a66b-528d35007745'
        USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM thesis.thesis_registration_round WHERE id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60' AND status IN ('REGISTRATION_OPEN','REGISTRATION_CLOSED','PROPOSALS_PUBLISHED'))
       OR (SELECT count(*) FROM thesis.thesis_topic WHERE round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60') > 50 THEN
      RAISE EXCEPTION 'demo-data guard: round % no longer matches the recon baseline (catalogue, <=50 topics)', 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'
        USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
    END IF;
    -- structural rows the dataset depends on (reused councils + R4 topics)
    IF NOT EXISTS (SELECT 1 FROM thesis.thesis_council WHERE id = '7ea8f499-949e-42c2-b11e-02be39b8ec52')
       OR NOT EXISTS (SELECT 1 FROM thesis.thesis_council WHERE id = '274ce36d-b5b0-4afa-bd33-19eb6b0095ca') THEN
      RAISE EXCEPTION 'demo-data guard: expected existing councils are missing — dataset must be re-reviewed'
        USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
    END IF;
    IF (SELECT count(*) FROM thesis.thesis_topic WHERE id IN (
         '018f4a7c-3b12-4d90-a5e1-7c82b9045001','33622854-38c4-4c3f-a7eb-bab11d2ff421',
         '6f64670b-cd6b-4e1b-ae9d-0b497f2472a8','f580eadf-fffb-4db2-a7d8-423e83934dc2',
         'd1f6f12b-62c7-4f60-a9c9-49a3e122c765','67c74df5-5c7f-4e74-aa02-d51af7207fe9')) <> 6 THEN
      RAISE EXCEPTION 'demo-data guard: expected R4 topics for new groups are missing — dataset must be re-reviewed'
        USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
    END IF;

    -- the five baseline topics the package assigns to the new council must not
    -- already carry a council assignment
    IF EXISTS (SELECT 1 FROM thesis.thesis_council_topic WHERE topic_id IN (
         '018f4a7c-3b12-4d90-a5e1-7c82b9045001','33622854-38c4-4c3f-a7eb-bab11d2ff421',
         '6f64670b-cd6b-4e1b-ae9d-0b497f2472a8','d1f6f12b-62c7-4f60-a9c9-49a3e122c765',
         '67c74df5-5c7f-4e74-aa02-d51af7207fe9')) THEN
      RAISE EXCEPTION 'demo-data guard: a baseline topic already has a council assignment — refusing to double-assign'
        USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
    END IF;

    -- guarded columns the package writes must still be untouched, otherwise the
    -- package could record a counter-review score that disagrees with an
    -- existing reviewer (or clobber a real waiver). Fail closed instead.
    IF EXISTS (SELECT 1 FROM thesis.thesis_topic
               WHERE id IN ('018f4a7c-3b12-4d90-a5e1-7c82b9045001',
                            '6f64670b-cd6b-4e1b-ae9d-0b497f2472a8',
                            'f580eadf-fffb-4db2-a7d8-423e83934dc2')
                 AND gvpb_lecturer_id IS NOT NULL) THEN
      RAISE EXCEPTION 'demo-data guard: a baseline topic already has a counter-reviewer assigned — refusing to write a mismatched counter-review score'
        USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
    END IF;
    IF EXISTS (SELECT 1 FROM thesis.thesis_topic
               WHERE id = '67c74df5-5c7f-4e74-aa02-d51af7207fe9'
                 AND gvpb_waived_reason IS NOT NULL) THEN
      RAISE EXCEPTION 'demo-data guard: the baseline waiver topic already carries waiver data — refusing to overwrite'
        USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
    END IF;

    -- every account the package references must resolve in the auth tables
    IF (SELECT count(*) FROM campuscore_auth."User" WHERE id IN (
         'admin-user','truongkhoa-user-001','truongkhoa-user-002','lecturer-user',
         'lecturer-user-002','lecturer-user-003','lecturer-user-004','lecturer-user-005',
         'lecturer-user-011','lecturer-user-013','lecturer-user-014','lecturer-user-023',
         'lecturer-user-024','lecturer-user-025','lecturer-user-026','lecturer-user-027',
         'lecturer-user-028','lecturer-user-029','lecturer-user-030')) <> 19 THEN
      RAISE EXCEPTION 'demo-data guard: referenced staff accounts are missing — dataset must be re-reviewed'
        USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
    END IF;
    IF (SELECT count(*) FROM academic."Department" WHERE id IN (
         'department-fit-is','department-fit-network','department-fit-ai',
         'department-demo','department-fit-software','department-feee-telecom')) <> 6 THEN
      RAISE EXCEPTION 'demo-data guard: referenced departments are missing — dataset must be re-reviewed'
        USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
    END IF;

    -- the redundant before-image copy lives in this table's comment; refuse to
    -- overwrite a foreign one rather than destroy operator metadata
    IF obj_description('thesis.thesis_topic'::regclass) IS NOT NULL
       AND obj_description('thesis.thesis_topic'::regclass) NOT LIKE 'thesis-demo-data v3 %' THEN
      RAISE EXCEPTION 'demo-data guard: thesis.thesis_topic carries a non-package comment — refusing to overwrite metadata'
        USING ERRCODE = '23514', CONSTRAINT = 'thesis_demo_data_guard';
    END IF;
  END IF;
END $$;

-- full marker set (or a strict subset left by application deletes) already
-- present: a re-apply must change NOTHING. It prints 'already applied' plus
-- the census and skips every guarded update, so it can never overwrite
-- application changes made since the first apply — including not re-adding
-- rows the application deliberately removed.
SELECT (state IN ('full','app_deleted_subset')) AS already_applied,
       state, missing_rows, detail FROM _class \gset

\if :already_applied

SELECT CASE WHEN :'state' = 'app_deleted_subset'
  THEN 'thesis demo-data already applied — the application removed ' || :missing_rows
       || ' marker row(s) since apply (' || :'detail' || '); no-op, 0 rows written'
  ELSE 'thesis demo-data already applied — no-op, 0 rows written' END AS result;

-- report drift on the guarded columns against the recorded applied values so
-- post-apply application changes stay visible without being touched
DO $$
DECLARE
  r record;
  n int;
  v_img jsonb;
  v_comment text;
  v_decl text;
  v_payload text;
  v_drift int := 0;
  v_names text := '';
BEGIN
  SELECT "beforeState"::jsonb INTO v_img FROM campuscore_audit."AdminAudit"
   WHERE id = 'demo-th-audit-000' AND "beforeState" ~ '^\s*\{';
  IF v_img IS NULL THEN
    v_comment := obj_description('thesis.thesis_topic'::regclass);
    IF v_comment LIKE 'thesis-demo-data v3 md5:%' THEN
      v_decl := substring(v_comment from 'md5:([0-9a-f]{32})');
      v_payload := substr(v_comment, position(E'\n' in v_comment) + 1);
      IF v_decl IS NOT NULL AND md5(v_payload) = v_decl THEN
        BEGIN v_img := v_payload::jsonb; EXCEPTION WHEN OTHERS THEN v_img := NULL; END;
      END IF;
    END IF;
  END IF;
  IF v_img IS NULL OR jsonb_typeof(v_img->'cols') IS DISTINCT FROM 'array' THEN
    RAISE WARNING 'demo-data already applied, but the before-image is missing — rollback will refuse; rebuild it with recover-before-image.sql';
    RETURN;
  END IF;
  FOR r IN
    SELECT (e->>'tbl') AS tbl, (e->>'id') AS rid, (e->>'col') AS col, (e->>'applied') AS app,
           format_type(a.atttypid, a.atttypmod) AS coltype
    FROM jsonb_array_elements(v_img->'cols') e
    JOIN pg_attribute a ON a.attrelid = ('thesis.' || (e->>'tbl'))::regclass
                        AND a.attname = (e->>'col')
  LOOP
    EXECUTE format('SELECT count(*) FROM thesis.%I WHERE id::text = $1 AND %I IS DISTINCT FROM $2::%s',
                   r.tbl, r.col, r.coltype)
      INTO n USING r.rid, r.app;
    IF n > 0 THEN v_drift := v_drift + 1; v_names := v_names || r.tbl || '.' || r.col || ' '; END IF;
  END LOOP;
  IF v_drift > 0 THEN
    RAISE WARNING 'demo-data already applied — % guarded column(s) drifted since apply (%); nothing was changed', v_drift, v_names;
  END IF;
END $$;

SELECT name AS section, cnt AS rows FROM _marker_counts ORDER BY 1;

\else

-- topics (17)
INSERT INTO thesis.thesis_topic
    (id, round_id, department_id, title, description, max_groups, status, created_by, version,
     created_at, updated_at, final_score, final_score_finalized_by, final_score_finalized_at,
     result_status, result_reason, result_by, result_at, gvpb_lecturer_id)
SELECT v.id::uuid, v.round_id::uuid, v.dept, v.title, v.descr, v.maxg, v.status, v.cby, 1,
     v.crat::timestamptz, v.upat::timestamptz, v.fin, v.finby, v.finat::timestamptz,
     v.rstatus, v.rreason, v.rby, v.rat::timestamptz, v.gvpblec
FROM (VALUES
    ('ed1a0000-0000-4000-8000-000100000001','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','department-fit-is','Hệ thống khai phá dữ liệu nhật ký học tập phục vụ cảnh báo học vụ sớm','Xây dựng pipeline thu thập và phân tích nhật ký truy cập cổng học vụ, ứng dụng luật kết hợp để phát hiện dấu hiệu sa sút kết quả học tập của sinh viên.',2,'PUBLISHED','lecturer-user-029','2025-01-22T09:00:00Z','2025-01-22T09:00:00Z',7.88,'lecturer-user-003','2026-10-15T14:00:00Z','GRADED',NULL,NULL,NULL,'lecturer-profile-024'),
    ('ed1a0000-0000-4000-8000-000100000002','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','department-fit-network','Giám sát an ninh mạng nội bộ bằng mô hình phát hiện bất thường lưu lượng','Triển khai hệ thống thu thập NetFlow kết hợp thuật toán Isolation Forest nhằm cảnh báo sớm truy cập bất thường trong mạng ký túc xá và phòng máy.',2,'PUBLISHED','lecturer-user-028','2025-01-22T09:00:00Z','2025-01-22T09:00:00Z',8.38,'lecturer-user-003','2026-10-15T14:30:00Z','GRADED',NULL,NULL,NULL,'lecturer-profile-025'),
    ('ed1a0000-0000-4000-8000-000100000003','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','department-fit-ai','Mô hình gợi ý lộ trình học tập cá nhân hóa dựa trên kết quả tích lũy','Ứng dụng lọc cộng tác và đồ thị tri thức môn học nhằm đề xuất thứ tự học phần tối ưu cho từng sinh viên theo năng lực và mục tiêu tốt nghiệp.',2,'PUBLISHED','lecturer-user-026','2025-01-22T09:00:00Z','2025-01-22T09:00:00Z',6.63,'lecturer-user-027','2026-10-16T10:00:00Z','GRADED',NULL,NULL,NULL,'lecturer-profile-024'),
    ('ed1a0000-0000-4000-8000-000100000004','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','department-demo','Cổng tra cứu văn bằng điện tử tích hợp xác thực chữ ký số cho phòng đào tạo','Thiết kế luồng cấp phát, tra cứu và xác thực văn bằng số áp dụng chữ ký số và mã định danh QR, bảo đảm chống giả mạo theo chuẩn Four-Eyes.',2,'PUBLISHED','truongkhoa-user-001','2025-01-22T09:00:00Z','2025-01-22T09:00:00Z',9.00,'lecturer-user-027','2026-10-16T10:30:00Z','GRADED',NULL,NULL,NULL,'lecturer-profile-023'),
    ('ed1a0000-0000-4000-8000-000100000005','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','department-fit-software','Nền tảng quản lý bài tập lớn nhóm với phân tích đóng góp thành viên','Phát triển công cụ theo dõi cam kết mã nguồn và phân bổ công việc trong nhóm đồ án, sinh báo cáo đóng góp cá nhân cho giảng viên hướng dẫn.',1,'PUBLISHED','lecturer-user-027','2025-01-22T09:00:00Z','2025-01-22T09:00:00Z',NULL,NULL,NULL,'REVIEW_FAILED','Counter-review score below the 5.0 pass threshold','lecturer-user-025','2026-10-10T14:00:00Z','lecturer-profile-025'),
    ('ed1a0000-0000-4000-8000-000100000006','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','department-fit-is','Hệ thống lập lịch phòng thi tối ưu theo ràng buộc giảng viên và sĩ số','Nghiên cứu bài toán xếp lịch thi ràng buộc mềm, áp dụng thuật toán tô màu đồ thị và tìm kiếm cục bộ để giảm xung đột lịch cho sinh viên.',1,'PUBLISHED','lecturer-user-023','2025-01-22T09:00:00Z','2025-01-22T09:00:00Z',NULL,NULL,NULL,'WITHDRAWN','Đề tài bị thu hồi do trùng phạm vi nghiên cứu với hồ sơ đã phê duyệt tại Khoa (biên bản ngày 06/10/2026)','truongkhoa-user-001','2026-10-06T11:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000100000007','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','department-fit-software','Kiến trúc sự kiện phân tán cho hệ thống đăng ký học phần chịu tải cao','Nghiên cứu mô hình Event-Driven kết hợp hàng đợi tin nhắn và chống trùng lặp idempotent nhằm bảo đảm tính nhất quán khi hàng chục nghìn sinh viên đăng ký đồng thời.',2,'PUBLISHED','lecturer-user-027','2025-08-12T09:00:00Z','2025-08-12T09:00:00Z',8.00,'lecturer-user','2026-10-21T09:30:00Z','GRADED',NULL,NULL,NULL,'lecturer-profile-028'),
    ('ed1a0000-0000-4000-8000-000100000008','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','department-fit-ai','Phát hiện đạo văn học thuật tiếng Việt bằng embedding đa ngữ và đồ thị trích dẫn','Kết hợp mô hình ngôn ngữ đa ngữ và phân tích mạng trích dẫn để phát hiện trùng lặp ý tưởng trong báo cáo khóa luận trước khi phản biện.',2,'PUBLISHED','lecturer-user-026','2025-08-12T09:00:00Z','2025-08-12T09:00:00Z',7.50,'lecturer-user','2026-10-21T10:00:00Z','GRADED',NULL,NULL,NULL,'lecturer-profile-024'),
    ('ed1a0000-0000-4000-8000-000100000009','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','department-fit-is','Kho dữ liệu phục vụ đánh giá chuẩn đầu ra chương trình đào tạo theo chu kỳ kiểm chứng','Thiết kế mô hình dữ liệu đa chiều hợp nhất điểm thành phần, điểm rèn luyện và khảo sát cơ sở nhằm trực quan hóa mức độ đạt PLO theo niên khóa.',2,'PUBLISHED','lecturer-user-029','2025-08-12T09:00:00Z','2025-08-12T09:00:00Z',8.70,'lecturer-user','2026-10-22T09:00:00Z','GRADED',NULL,NULL,NULL,'lecturer-profile-023'),
    ('ed1a0000-0000-4000-8000-000100000010','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','department-feee-telecom','Mạng cảm biến không dây giám sát môi trường phòng thí nghiệm thời gian thực','Triển khai cụm nút cảm biến nhiệt độ - độ ẩm - chất lượng không khí truyền dữ liệu qua MQTT kèm bảng điều khiển cảnh báo ngưỡng cho phòng thí nghiệm.',2,'PUBLISHED','lecturer-user-014','2025-08-12T09:00:00Z','2025-08-12T09:00:00Z',7.00,'lecturer-user-027','2026-10-22T09:30:00Z','GRADED',NULL,NULL,NULL,'lecturer-profile-013'),
    ('ed1a0000-0000-4000-8000-000100000011','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','department-fit-network','Phân tích và đánh giá chính sách kiểm soát truy cập Zero-Trust cho hệ thống học vụ','Khảo sát các mô hình ZTNA, đề xuất khung đánh giá mức độ trưởng thành kiểm soát truy cập cho hạ tầng đào tạo phân tán nhiều campus.',2,'PUBLISHED','lecturer-user-028','2025-08-12T09:00:00Z','2025-08-12T09:00:00Z',NULL,NULL,NULL,'WITHDRAWN','Nhóm xin rút đề tài do thay đổi kế hoạch thực tập tốt nghiệp của toàn bộ thành viên (đơn đã được Bộ môn chấp thuận)','truongkhoa-user-002','2026-10-12T15:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000100000012','bad8eba0-2d12-4414-8aab-7424174b3b97','department-fit-is','Hệ thống hỗ trợ ra quyết định xét cảnh báo học tập theo ma trận rủi ro','Xây dựng bảng điều khiển tổng hợp điểm quá trình, chuyên cần và lịch sử cảnh báo nhằm gợi ý mức xử lý phù hợp cho từng sinh viên theo quy chế.',2,'PUBLISHED','lecturer-user-029','2026-01-20T09:00:00Z','2026-01-20T09:00:00Z',8.25,'lecturer-user-026','2026-10-21T10:00:00Z','GRADED',NULL,NULL,NULL,'lecturer-profile-030'),
    ('ed1a0000-0000-4000-8000-000100000013','bad8eba0-2d12-4414-8aab-7424174b3b97','department-fit-software','Bộ công cụ kiểm thử hợp đồng API tự động cho hệ thống microservice học vụ','Sinh ca kiểm thử hợp đồng từ tài liệu OpenAPI, tích hợp kiểm chứng schema hai chiều và báo cáo sai lệch phiên bản trong pipeline CI của trường.',2,'PUBLISHED','lecturer-user-030','2026-01-20T09:00:00Z','2026-01-20T09:00:00Z',7.63,'lecturer-user-026','2026-10-21T10:30:00Z','GRADED',NULL,NULL,NULL,'lecturer-profile-027'),
    ('ed1a0000-0000-4000-8000-000100000014','bad8eba0-2d12-4414-8aab-7424174b3b97','department-fit-network','Hệ thống điểm danh phòng máy bằng nhận diện thẻ từ và đối chiếu lịch sử dụng','Thiết kế luồng điểm danh tự động gắn với lịch đặt phòng, phát hiện ngồi sai ca và sinh báo cáo sử dụng thiết bị cho quản trị phòng máy.',2,'PUBLISHED','lecturer-user-028','2026-01-20T09:00:00Z','2026-01-20T09:00:00Z',8.00,'lecturer-user-026','2026-10-21T16:00:00Z','GRADED',NULL,NULL,NULL,'lecturer-profile-025'),
    ('ed1a0000-0000-4000-8000-000100000015','bad8eba0-2d12-4414-8aab-7424174b3b97','department-demo','Cổng thông tin tuyển sinh thông minh tích hợp tư vấn ngành học tự động','Xây dựng trang thông tin tuyển sinh cá nhân hóa kết hợp chatbot tư vấn ngành dựa trên hồ sơ năng lực và nguyện vọng của thí sinh.',2,'PUBLISHED','truongkhoa-user-002','2026-01-20T09:00:00Z','2026-01-20T09:00:00Z',NULL,NULL,NULL,'WITHDRAWN','Đề tài chuyển sang đợt KLTN Học kỳ 2 năm học 2026 - 2027 theo đề nghị phân bổ đề tài của Bộ môn','truongkhoa-user-001','2026-10-14T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000100000016','c8f1a234-9b7e-412d-8301-1b2c3d4e5f60','department-fit-network','Đánh giá hiệu năng mạng riêng ảo WireGuard cho kết nối chi nhánh campus','Khảo sát độ trễ và thông lượng của WireGuard so với IPsec trong mô hình kết nối các cơ sở đào tạo phân tán, đề xuất cấu hình tối ưu.',1,'ARCHIVED','lecturer-user-028','2026-08-20T09:00:00Z','2026-08-20T09:00:00Z',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000100000017','c8f1a234-9b7e-412d-8301-1b2c3d4e5f60','department-fit-software','Trợ lý chấm bài lập trình tự động bằng phân tích AST và đối chiếu kiểm thử','Nghiên cứu phương pháp chấm bài lập trình dựa trên cây cú pháp trừu tượng kết hợp bộ kiểm thử sinh tự động, hỗ trợ phản hồi chi tiết cho sinh viên.',1,'REJECTED','lecturer-user-030','2026-09-18T09:00:00Z','2026-09-18T09:00:00Z',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)
) AS v(id, round_id, dept, title, descr, maxg, status, cby, crat, upat, fin, finby, finat, rstatus, rreason, rby, rat, gvpblec)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_registration_round r WHERE r.id = v.round_id::uuid)
  AND EXISTS (SELECT 1 FROM academic."Department" d WHERE d.id = v.dept)
ON CONFLICT (id) DO NOTHING;

-- topic supervisors (18)
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
SELECT v.id::uuid, v.tid::uuid, v.lec, v.ord, v.at::timestamptz
FROM (VALUES
    ('ed1a0000-0000-4000-8000-000200000001','ed1a0000-0000-4000-8000-000100000001','lecturer-profile-029',1,'2025-01-22T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000002','ed1a0000-0000-4000-8000-000100000002','lecturer-profile-028',1,'2025-01-22T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000003','ed1a0000-0000-4000-8000-000100000003','lecturer-profile-026',1,'2025-01-22T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000004','ed1a0000-0000-4000-8000-000100000004','lecturer-profile-truongkhoa-001',1,'2025-01-22T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000005','ed1a0000-0000-4000-8000-000100000005','lecturer-profile-027',1,'2025-01-22T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000006','ed1a0000-0000-4000-8000-000100000006','lecturer-profile-023',1,'2025-01-22T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000007','ed1a0000-0000-4000-8000-000100000006','lecturer-profile-029',2,'2025-01-22T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000008','ed1a0000-0000-4000-8000-000100000007','lecturer-profile-027',1,'2025-08-12T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000009','ed1a0000-0000-4000-8000-000100000008','lecturer-profile-026',1,'2025-08-12T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000010','ed1a0000-0000-4000-8000-000100000009','lecturer-profile-029',1,'2025-08-12T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000011','ed1a0000-0000-4000-8000-000100000010','lecturer-profile-014',1,'2025-08-12T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000012','ed1a0000-0000-4000-8000-000100000011','lecturer-profile-028',1,'2025-08-12T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000013','ed1a0000-0000-4000-8000-000100000012','lecturer-profile-029',1,'2026-01-20T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000014','ed1a0000-0000-4000-8000-000100000013','lecturer-profile-030',1,'2026-01-20T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000015','ed1a0000-0000-4000-8000-000100000014','lecturer-profile-028',1,'2026-01-20T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000016','ed1a0000-0000-4000-8000-000100000015','lecturer-profile-truongkhoa-002',1,'2026-01-20T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000017','ed1a0000-0000-4000-8000-000100000016','lecturer-profile-028',1,'2026-08-20T09:00:00Z'),
    ('ed1a0000-0000-4000-8000-000200000018','ed1a0000-0000-4000-8000-000100000017','lecturer-profile-030',1,'2026-09-18T09:00:00Z')
) AS v(id, tid, lec, ord, at)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_topic t WHERE t.id = v.tid::uuid)
  AND EXISTS (SELECT 1 FROM academic."Lecturer" l WHERE l."id" = v.lec)
ON CONFLICT (topic_id, supervisor_order) DO NOTHING;

-- councils (4) and seats (16)
INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by, created_at, updated_at)
SELECT v.id::uuid, v.round::uuid, v.name, 'ACTIVE', v.by, v.at::timestamptz, v.at::timestamptz
FROM (VALUES
    ('ed1a0000-0000-4000-8000-000500000001','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','Hội đồng ĐACN Hệ thống Thông tin & An toàn 02','admin-user','2026-10-05T08:30:00Z'),
    ('ed1a0000-0000-4000-8000-000500000002','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','Hội đồng KLTN Phần mềm & Dữ liệu 02','admin-user','2026-10-08T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000500000003','bad8eba0-2d12-4414-8aab-7424174b3b97','Hội đồng TLCN Trí tuệ Nhân tạo & Dữ liệu 02','admin-user','2026-10-10T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000500000004','22d65ee6-f485-40ae-a66b-528d35007745','Hội đồng TLCN Học kỳ 1 - Bảo vệ & Phản biện','admin-user','2026-10-21T08:00:00Z')
) AS v(id, round, name, by, at)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_registration_round r WHERE r.id = v.round::uuid)
ON CONFLICT (id) DO NOTHING;

INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role, created_at)
SELECT v.id::uuid, v.cid::uuid, v.lec, v.role, v.at::timestamptz
FROM (VALUES
    ('ed1a0000-0000-4000-8000-000600000001','ed1a0000-0000-4000-8000-000500000001','lecturer-profile-027','CHAIR','2026-10-05T08:30:00Z'),
    ('ed1a0000-0000-4000-8000-000600000002','ed1a0000-0000-4000-8000-000500000001','lecturer-profile-029','SECRETARY','2026-10-05T08:30:00Z'),
    ('ed1a0000-0000-4000-8000-000600000003','ed1a0000-0000-4000-8000-000500000001','lecturer-profile-030','MEMBER','2026-10-05T08:30:00Z'),
    ('ed1a0000-0000-4000-8000-000600000004','ed1a0000-0000-4000-8000-000500000001','lecturer-profile-028','MEMBER','2026-10-05T08:30:00Z'),
    ('ed1a0000-0000-4000-8000-000600000005','ed1a0000-0000-4000-8000-000500000002','lecturer-profile-027','CHAIR','2026-10-08T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000600000006','ed1a0000-0000-4000-8000-000500000002','lecturer-profile-029','SECRETARY','2026-10-08T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000600000007','ed1a0000-0000-4000-8000-000500000002','lecturer-profile-030','MEMBER','2026-10-08T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000600000008','ed1a0000-0000-4000-8000-000500000002','lecturer-profile-026','MEMBER','2026-10-08T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000600000009','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-026','CHAIR','2026-10-10T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000600000010','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-023','SECRETARY','2026-10-10T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000600000011','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-024','MEMBER','2026-10-10T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000600000012','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-011','MEMBER','2026-10-10T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000600000013','ed1a0000-0000-4000-8000-000500000004','lecturer-profile-003','CHAIR','2026-10-21T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000600000014','ed1a0000-0000-4000-8000-000500000004','lecturer-profile-004','SECRETARY','2026-10-21T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000600000015','ed1a0000-0000-4000-8000-000500000004','lecturer-profile-005','MEMBER','2026-10-21T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000600000016','ed1a0000-0000-4000-8000-000500000004','lecturer-profile-002','MEMBER','2026-10-21T08:00:00Z')
) AS v(id, cid, lec, role, at)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_council c WHERE c.id = v.cid::uuid)
  AND EXISTS (SELECT 1 FROM academic."Lecturer" l WHERE l."id" = v.lec)
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_council_member m WHERE m.council_id = v.cid::uuid AND m.lecturer_id = v.lec)
ON CONFLICT (council_id, lecturer_id) DO NOTHING;

-- groups (20)
INSERT INTO thesis.thesis_group
    (id, round_id, leader_student_id, topic_id, status, approval_status, approved_by, approved_at,
     rejection_reason, version, created_at, updated_at, size_exception_reason, size_exception_by, size_exception_at)
-- groups are staged as PENDING and flipped to their final approval_status after
-- the member insert: the membership trigger requires an approved group to
-- already have 3-5 members, so the approval must land after the roster exists.
SELECT v.id::uuid, v.round::uuid, v.leader, v.topic::uuid, v.status,
     CASE WHEN v.approval = 'APPROVED' THEN 'PENDING' ELSE v.approval END,
     CASE WHEN v.approval = 'APPROVED' THEN NULL ELSE v.appby END,
     CASE WHEN v.approval = 'APPROVED' THEN NULL ELSE v.appat::timestamptz END,
     v.rej, 1, v.crat::timestamptz, v.upat::timestamptz,
     v.sreason, v.sby, v.sat::timestamptz
FROM (VALUES
    ('ed1a0000-0000-4000-8000-000300000001','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-021','ed1a0000-0000-4000-8000-000100000001','SUBMITTED','APPROVED','truongkhoa-user-001','2025-03-20T10:00:00Z',NULL,'2025-03-10T09:00:00Z','2025-03-10T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000002','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-025','ed1a0000-0000-4000-8000-000100000002','SUBMITTED','APPROVED','truongkhoa-user-001','2025-03-20T10:00:00Z',NULL,'2025-03-10T09:00:00Z','2025-03-10T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000003','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-028','ed1a0000-0000-4000-8000-000100000003','SUBMITTED','APPROVED','truongkhoa-user-001','2025-03-20T10:00:00Z',NULL,'2025-03-10T09:00:00Z','2025-03-10T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000004','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-109','ed1a0000-0000-4000-8000-000100000004','SUBMITTED','APPROVED','truongkhoa-user-001','2025-03-20T10:00:00Z',NULL,'2025-03-10T09:00:00Z','2025-03-10T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000005','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-138','ed1a0000-0000-4000-8000-000100000005','SUBMITTED','APPROVED','truongkhoa-user-001','2025-03-20T10:00:00Z',NULL,'2025-03-10T09:00:00Z','2025-03-10T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000006','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-153','ed1a0000-0000-4000-8000-000100000006','SUBMITTED','APPROVED','truongkhoa-user-001','2025-03-20T10:00:00Z',NULL,'2025-03-10T09:00:00Z','2025-03-10T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000007','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-157','ed1a0000-0000-4000-8000-000100000007','SUBMITTED','APPROVED','truongkhoa-user-001','2025-10-08T10:00:00Z',NULL,'2025-09-20T09:00:00Z','2025-09-20T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000008','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-161','ed1a0000-0000-4000-8000-000100000008','SUBMITTED','APPROVED','truongkhoa-user-001','2025-10-08T10:00:00Z',NULL,'2025-09-20T09:00:00Z','2025-09-20T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000009','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-164','ed1a0000-0000-4000-8000-000100000009','SUBMITTED','APPROVED','truongkhoa-user-001','2025-10-08T10:00:00Z',NULL,'2025-09-20T09:00:00Z','2025-09-20T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000010','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-167','ed1a0000-0000-4000-8000-000100000010','SUBMITTED','APPROVED','truongkhoa-user-001','2025-10-08T10:00:00Z',NULL,'2025-09-20T09:00:00Z','2025-09-20T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000011','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-170','ed1a0000-0000-4000-8000-000100000011','SUBMITTED','APPROVED','truongkhoa-user-001','2025-10-08T10:00:00Z',NULL,'2025-09-20T09:00:00Z','2025-09-20T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000012','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-175','ed1a0000-0000-4000-8000-000100000012','SUBMITTED','APPROVED','truongkhoa-user-001','2026-03-18T10:00:00Z',NULL,'2026-03-05T09:00:00Z','2026-03-05T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000013','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-179','ed1a0000-0000-4000-8000-000100000013','SUBMITTED','APPROVED','truongkhoa-user-001','2026-03-18T10:00:00Z',NULL,'2026-03-05T09:00:00Z','2026-03-05T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000014','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-182','ed1a0000-0000-4000-8000-000100000014','SUBMITTED','APPROVED','truongkhoa-user-001','2026-03-18T10:00:00Z',NULL,'2026-03-05T09:00:00Z','2026-03-05T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000015','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-185','ed1a0000-0000-4000-8000-000100000015','SUBMITTED','APPROVED','truongkhoa-user-001','2026-03-18T10:00:00Z',NULL,'2026-03-05T09:00:00Z','2026-03-05T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000016','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-189','018f4a7c-3b12-4d90-a5e1-7c82b9045001','ASSIGNED','APPROVED','truongkhoa-user-001','2026-10-14T10:00:00Z',NULL,'2026-10-12T09:00:00Z','2026-10-12T09:00:00Z','Đề tài phần cứng nhúng đặc thù chỉ phù hợp nhóm 2 sinh viên; Bộ môn chấp thuận ngoại lệ sĩ số theo biên bản 08/10/2026','truongkhoa-user-001','2026-10-14T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000017','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-191','33622854-38c4-4c3f-a7eb-bab11d2ff421','SUBMITTED','REJECTED',NULL,NULL,'Danh sách thành viên chưa đủ sĩ số tối thiểu 3 sinh viên; nhóm cần bổ sung thành viên và nộp lại trước hạn đăng ký','2026-10-13T09:00:00Z','2026-10-13T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000018','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-193','6f64670b-cd6b-4e1b-ae9d-0b497f2472a8','ASSIGNED','APPROVED','truongkhoa-user-001','2026-10-13T10:00:00Z',NULL,'2026-10-11T09:00:00Z','2026-10-11T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000019','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-196','6f64670b-cd6b-4e1b-ae9d-0b497f2472a8','ASSIGNED','APPROVED','truongkhoa-user-001','2026-10-20T10:00:00Z',NULL,'2026-10-19T09:00:00Z','2026-10-19T09:00:00Z',NULL,NULL,NULL),
    ('ed1a0000-0000-4000-8000-000300000020','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-200','f580eadf-fffb-4db2-a7d8-423e83934dc2','SUBMITTED','PENDING',NULL,NULL,NULL,'2026-10-21T09:00:00Z','2026-10-21T09:00:00Z',NULL,NULL,NULL)
) AS v(id, round, leader, topic, status, approval, appby, appat, rej, crat, upat, sreason, sby, sat)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_registration_round r WHERE r.id = v.round::uuid)
  AND EXISTS (SELECT 1 FROM thesis.thesis_topic t WHERE t.id = v.topic::uuid)
  AND EXISTS (SELECT 1 FROM academic."Student" s WHERE s."id" = v.leader)
ON CONFLICT (id) DO NOTHING;

-- group members (64)
INSERT INTO thesis.thesis_group_member
    (id, group_id, round_id, student_id, member_order, is_leader, is_external, active_participation, created_at)
SELECT v.id::uuid, v.gid::uuid, v.round::uuid, v.stu, v.ord, v.leader, FALSE, TRUE, v.at::timestamptz
FROM (VALUES
    ('ed1a0000-0000-4000-8000-000400000001','ed1a0000-0000-4000-8000-000300000001','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-021',1,TRUE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000002','ed1a0000-0000-4000-8000-000300000001','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-022',2,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000003','ed1a0000-0000-4000-8000-000300000001','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-023',3,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000004','ed1a0000-0000-4000-8000-000300000001','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-024',4,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000005','ed1a0000-0000-4000-8000-000300000002','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-025',1,TRUE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000006','ed1a0000-0000-4000-8000-000300000002','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-026',2,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000007','ed1a0000-0000-4000-8000-000300000002','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-027',3,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000008','ed1a0000-0000-4000-8000-000300000003','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-028',1,TRUE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000009','ed1a0000-0000-4000-8000-000300000003','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-029',2,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000010','ed1a0000-0000-4000-8000-000300000003','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-030',3,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000011','ed1a0000-0000-4000-8000-000300000004','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-109',1,TRUE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000012','ed1a0000-0000-4000-8000-000300000004','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-110',2,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000013','ed1a0000-0000-4000-8000-000300000004','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-120',3,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000014','ed1a0000-0000-4000-8000-000300000004','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-136',4,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000015','ed1a0000-0000-4000-8000-000300000004','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-137',5,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000016','ed1a0000-0000-4000-8000-000300000005','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-138',1,TRUE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000017','ed1a0000-0000-4000-8000-000300000005','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-139',2,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000018','ed1a0000-0000-4000-8000-000300000005','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-140',3,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000019','ed1a0000-0000-4000-8000-000300000006','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-153',1,TRUE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000020','ed1a0000-0000-4000-8000-000300000006','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-154',2,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000021','ed1a0000-0000-4000-8000-000300000006','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-155',3,FALSE,'2025-03-10T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000022','ed1a0000-0000-4000-8000-000300000007','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-157',1,TRUE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000023','ed1a0000-0000-4000-8000-000300000007','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-158',2,FALSE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000024','ed1a0000-0000-4000-8000-000300000007','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-159',3,FALSE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000025','ed1a0000-0000-4000-8000-000300000007','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-160',4,FALSE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000026','ed1a0000-0000-4000-8000-000300000008','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-161',1,TRUE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000027','ed1a0000-0000-4000-8000-000300000008','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-162',2,FALSE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000028','ed1a0000-0000-4000-8000-000300000008','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-163',3,FALSE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000029','ed1a0000-0000-4000-8000-000300000009','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-164',1,TRUE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000030','ed1a0000-0000-4000-8000-000300000009','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-165',2,FALSE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000031','ed1a0000-0000-4000-8000-000300000009','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-166',3,FALSE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000032','ed1a0000-0000-4000-8000-000300000010','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-167',1,TRUE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000033','ed1a0000-0000-4000-8000-000300000010','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-168',2,FALSE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000034','ed1a0000-0000-4000-8000-000300000010','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-169',3,FALSE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000035','ed1a0000-0000-4000-8000-000300000011','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-170',1,TRUE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000036','ed1a0000-0000-4000-8000-000300000011','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-171',2,FALSE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000037','ed1a0000-0000-4000-8000-000300000011','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-172',3,FALSE,'2025-09-20T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000038','ed1a0000-0000-4000-8000-000300000012','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-175',1,TRUE,'2026-03-05T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000039','ed1a0000-0000-4000-8000-000300000012','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-176',2,FALSE,'2026-03-05T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000040','ed1a0000-0000-4000-8000-000300000012','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-177',3,FALSE,'2026-03-05T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000041','ed1a0000-0000-4000-8000-000300000012','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-178',4,FALSE,'2026-03-05T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000042','ed1a0000-0000-4000-8000-000300000013','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-179',1,TRUE,'2026-03-05T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000043','ed1a0000-0000-4000-8000-000300000013','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-180',2,FALSE,'2026-03-05T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000044','ed1a0000-0000-4000-8000-000300000013','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-181',3,FALSE,'2026-03-05T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000045','ed1a0000-0000-4000-8000-000300000014','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-182',1,TRUE,'2026-03-05T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000046','ed1a0000-0000-4000-8000-000300000014','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-183',2,FALSE,'2026-03-05T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000047','ed1a0000-0000-4000-8000-000300000014','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-184',3,FALSE,'2026-03-05T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000048','ed1a0000-0000-4000-8000-000300000015','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-185',1,TRUE,'2026-03-05T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000049','ed1a0000-0000-4000-8000-000300000015','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-186',2,FALSE,'2026-03-05T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000050','ed1a0000-0000-4000-8000-000300000015','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-187',3,FALSE,'2026-03-05T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000051','ed1a0000-0000-4000-8000-000300000016','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-189',1,TRUE,'2026-10-12T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000052','ed1a0000-0000-4000-8000-000300000016','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-190',2,FALSE,'2026-10-12T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000053','ed1a0000-0000-4000-8000-000300000017','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-191',1,TRUE,'2026-10-13T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000054','ed1a0000-0000-4000-8000-000300000017','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-192',2,FALSE,'2026-10-13T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000055','ed1a0000-0000-4000-8000-000300000018','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-193',1,TRUE,'2026-10-11T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000056','ed1a0000-0000-4000-8000-000300000018','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-194',2,FALSE,'2026-10-11T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000057','ed1a0000-0000-4000-8000-000300000018','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-195',3,FALSE,'2026-10-11T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000058','ed1a0000-0000-4000-8000-000300000019','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-196',1,TRUE,'2026-10-19T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000059','ed1a0000-0000-4000-8000-000300000019','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-197',2,FALSE,'2026-10-19T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000060','ed1a0000-0000-4000-8000-000300000019','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-198',3,FALSE,'2026-10-19T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000061','ed1a0000-0000-4000-8000-000300000019','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-199',4,FALSE,'2026-10-19T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000062','ed1a0000-0000-4000-8000-000300000020','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-200',1,TRUE,'2026-10-21T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000063','ed1a0000-0000-4000-8000-000300000020','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-201',2,FALSE,'2026-10-21T09:05:00Z'),
    ('ed1a0000-0000-4000-8000-000400000064','ed1a0000-0000-4000-8000-000300000020','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-202',3,FALSE,'2026-10-21T09:05:00Z')
) AS v(id, gid, round, stu, ord, leader, at)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_group g WHERE g.id = v.gid::uuid AND g.status <> 'CANCELLED')
  AND EXISTS (SELECT 1 FROM academic."Student" s WHERE s."id" = v.stu)
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member m
                  WHERE m.round_id = v.round::uuid AND m.student_id = v.stu AND m.active_participation)
ON CONFLICT (id) DO NOTHING;

-- flip staged groups to their approved state now that rosters exist (idempotent:
-- only rows still PENDING are touched)
UPDATE thesis.thesis_group g SET approval_status = 'APPROVED', approved_by = v.by,
     approved_at = v.at::timestamptz, version = version + 1
FROM (VALUES
    ('ed1a0000-0000-4000-8000-000300000001','truongkhoa-user-001','2025-03-20T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000002','truongkhoa-user-001','2025-03-20T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000003','truongkhoa-user-001','2025-03-20T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000004','truongkhoa-user-001','2025-03-20T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000005','truongkhoa-user-001','2025-03-20T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000006','truongkhoa-user-001','2025-03-20T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000007','truongkhoa-user-001','2025-10-08T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000008','truongkhoa-user-001','2025-10-08T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000009','truongkhoa-user-001','2025-10-08T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000010','truongkhoa-user-001','2025-10-08T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000011','truongkhoa-user-001','2025-10-08T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000012','truongkhoa-user-001','2026-03-18T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000013','truongkhoa-user-001','2026-03-18T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000014','truongkhoa-user-001','2026-03-18T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000015','truongkhoa-user-001','2026-03-18T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000016','truongkhoa-user-001','2026-10-14T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000018','truongkhoa-user-001','2026-10-13T10:00:00Z'),
    ('ed1a0000-0000-4000-8000-000300000019','truongkhoa-user-001','2026-10-20T10:00:00Z')
) AS v(id, by, at)
WHERE g.id = v.id::uuid AND g.approval_status = 'PENDING';

-- council topic assignments (16)
INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by, created_at)
SELECT v.id::uuid, v.cid::uuid, v.tid::uuid, v.by, v.at::timestamptz
FROM (VALUES
    ('ed1a0000-0000-4000-8000-000700000001','7ea8f499-949e-42c2-b11e-02be39b8ec52','ed1a0000-0000-4000-8000-000100000001','admin-user','2026-10-13T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000002','7ea8f499-949e-42c2-b11e-02be39b8ec52','ed1a0000-0000-4000-8000-000100000002','admin-user','2026-10-13T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000003','ed1a0000-0000-4000-8000-000500000001','ed1a0000-0000-4000-8000-000100000003','admin-user','2026-10-13T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000004','ed1a0000-0000-4000-8000-000500000001','ed1a0000-0000-4000-8000-000100000004','admin-user','2026-10-13T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000005','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','ed1a0000-0000-4000-8000-000100000007','admin-user','2026-10-19T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000006','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','ed1a0000-0000-4000-8000-000100000008','admin-user','2026-10-19T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000007','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','ed1a0000-0000-4000-8000-000100000009','admin-user','2026-10-19T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000008','ed1a0000-0000-4000-8000-000500000002','ed1a0000-0000-4000-8000-000100000010','admin-user','2026-10-19T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000009','ed1a0000-0000-4000-8000-000500000003','ed1a0000-0000-4000-8000-000100000012','admin-user','2026-10-19T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000010','ed1a0000-0000-4000-8000-000500000003','ed1a0000-0000-4000-8000-000100000013','admin-user','2026-10-19T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000011','ed1a0000-0000-4000-8000-000500000003','ed1a0000-0000-4000-8000-000100000014','admin-user','2026-10-19T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000012','ed1a0000-0000-4000-8000-000500000004','018f4a7c-3b12-4d90-a5e1-7c82b9045001','admin-user','2026-10-22T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000013','ed1a0000-0000-4000-8000-000500000004','33622854-38c4-4c3f-a7eb-bab11d2ff421','admin-user','2026-10-22T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000014','ed1a0000-0000-4000-8000-000500000004','6f64670b-cd6b-4e1b-ae9d-0b497f2472a8','admin-user','2026-10-22T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000015','ed1a0000-0000-4000-8000-000500000004','d1f6f12b-62c7-4f60-a9c9-49a3e122c765','admin-user','2026-10-22T08:00:00Z'),
    ('ed1a0000-0000-4000-8000-000700000016','ed1a0000-0000-4000-8000-000500000004','67c74df5-5c7f-4e74-aa02-d51af7207fe9','admin-user','2026-10-22T08:00:00Z')
) AS v(id, cid, tid, by, at)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_council c WHERE c.id = v.cid::uuid)
  AND EXISTS (SELECT 1 FROM thesis.thesis_topic t WHERE t.id = v.tid::uuid)
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_council_topic ct WHERE ct.topic_id = v.tid::uuid)
ON CONFLICT (id) DO NOTHING;

-- topic scores (62): every eligible council seat scores each graded topic
INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score, graded_at, created_at, comment)
SELECT v.id::uuid, v.tid::uuid, v.cid::uuid, v.lec, v.comp, v.score, v.at::timestamptz, v.at::timestamptz, v.comm
FROM (VALUES
    ('ed1a0000-0000-4000-8000-000800000001','ed1a0000-0000-4000-8000-000100000001',NULL,'lecturer-profile-024','GVPB',7.50,'2026-10-07T09:00:00Z','Báo cáo trình bày đầy đủ, kết quả đo lường rõ ràng; đề nghị bổ sung bảng so sánh giải pháp trước khi bảo vệ.'),
    ('ed1a0000-0000-4000-8000-000800000002','ed1a0000-0000-4000-8000-000100000001','7ea8f499-949e-42c2-b11e-02be39b8ec52','lecturer-profile-003','DEFENSE',7.50,'2026-10-14T10:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000003','ed1a0000-0000-4000-8000-000100000001','7ea8f499-949e-42c2-b11e-02be39b8ec52','lecturer-profile-004','DEFENSE',8.00,'2026-10-14T10:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000004','ed1a0000-0000-4000-8000-000100000001','7ea8f499-949e-42c2-b11e-02be39b8ec52','lecturer-profile-005','DEFENSE',8.00,'2026-10-14T10:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000005','ed1a0000-0000-4000-8000-000100000002',NULL,'lecturer-profile-025','GVPB',8.00,'2026-10-08T15:00:00Z','Báo cáo trình bày đầy đủ, kết quả đo lường rõ ràng; đề nghị bổ sung bảng so sánh giải pháp trước khi bảo vệ.'),
    ('ed1a0000-0000-4000-8000-000800000006','ed1a0000-0000-4000-8000-000100000002','7ea8f499-949e-42c2-b11e-02be39b8ec52','lecturer-profile-003','DEFENSE',8.00,'2026-10-14T14:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000007','ed1a0000-0000-4000-8000-000100000002','7ea8f499-949e-42c2-b11e-02be39b8ec52','lecturer-profile-004','DEFENSE',8.50,'2026-10-14T14:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000008','ed1a0000-0000-4000-8000-000100000002','7ea8f499-949e-42c2-b11e-02be39b8ec52','lecturer-profile-005','DEFENSE',8.50,'2026-10-14T14:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000009','ed1a0000-0000-4000-8000-000100000003',NULL,'lecturer-profile-024','GVPB',6.00,'2026-10-09T10:30:00Z','Báo cáo trình bày đầy đủ, kết quả đo lường rõ ràng; đề nghị bổ sung bảng so sánh giải pháp trước khi bảo vệ.'),
    ('ed1a0000-0000-4000-8000-000800000010','ed1a0000-0000-4000-8000-000100000003','ed1a0000-0000-4000-8000-000500000001','lecturer-profile-027','DEFENSE',6.50,'2026-10-15T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000011','ed1a0000-0000-4000-8000-000100000003','ed1a0000-0000-4000-8000-000500000001','lecturer-profile-029','DEFENSE',6.50,'2026-10-15T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000012','ed1a0000-0000-4000-8000-000100000003','ed1a0000-0000-4000-8000-000500000001','lecturer-profile-030','DEFENSE',7.00,'2026-10-15T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000013','ed1a0000-0000-4000-8000-000100000004',NULL,'lecturer-profile-023','GVPB',8.50,'2026-10-10T09:30:00Z','Báo cáo trình bày đầy đủ, kết quả đo lường rõ ràng; đề nghị bổ sung bảng so sánh giải pháp trước khi bảo vệ.'),
    ('ed1a0000-0000-4000-8000-000800000014','ed1a0000-0000-4000-8000-000100000004','ed1a0000-0000-4000-8000-000500000001','lecturer-profile-027','DEFENSE',9.00,'2026-10-15T10:30:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000015','ed1a0000-0000-4000-8000-000100000004','ed1a0000-0000-4000-8000-000500000001','lecturer-profile-029','DEFENSE',9.00,'2026-10-15T10:30:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000016','ed1a0000-0000-4000-8000-000100000004','ed1a0000-0000-4000-8000-000500000001','lecturer-profile-030','DEFENSE',9.00,'2026-10-15T10:30:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000017','ed1a0000-0000-4000-8000-000100000005',NULL,'lecturer-profile-025','GVPB',4.50,'2026-10-10T14:00:00Z','Phần phân tích thiết kế còn mô tả, thiếu số liệu đánh giá; chương kiểm thử chưa đạt yêu cầu phản biện.'),
    ('ed1a0000-0000-4000-8000-000800000018','ed1a0000-0000-4000-8000-000100000007',NULL,'lecturer-profile-028','GVPB',7.50,'2026-10-15T09:00:00Z','Đồng ý cho bảo vệ; cần bổ sung phần thảo luận hạn chế và hướng phát triển trong bản toàn văn.'),
    ('ed1a0000-0000-4000-8000-000800000019','ed1a0000-0000-4000-8000-000100000007','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile-002','DEFENSE',8.00,'2026-10-20T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000020','ed1a0000-0000-4000-8000-000100000007','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile-003','DEFENSE',8.00,'2026-10-20T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000021','ed1a0000-0000-4000-8000-000100000007','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile-004','DEFENSE',8.00,'2026-10-20T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000022','ed1a0000-0000-4000-8000-000100000008',NULL,'lecturer-profile-024','GVPB',7.00,'2026-10-16T10:00:00Z','Đồng ý cho bảo vệ; cần bổ sung phần thảo luận hạn chế và hướng phát triển trong bản toàn văn.'),
    ('ed1a0000-0000-4000-8000-000800000023','ed1a0000-0000-4000-8000-000100000008','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile-002','DEFENSE',7.50,'2026-10-20T10:30:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000024','ed1a0000-0000-4000-8000-000100000008','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile-003','DEFENSE',7.50,'2026-10-20T10:30:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000025','ed1a0000-0000-4000-8000-000100000008','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile-004','DEFENSE',7.50,'2026-10-20T10:30:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000026','ed1a0000-0000-4000-8000-000100000009',NULL,'lecturer-profile-023','GVPB',8.50,'2026-10-17T14:00:00Z','Đồng ý cho bảo vệ; cần bổ sung phần thảo luận hạn chế và hướng phát triển trong bản toàn văn.'),
    ('ed1a0000-0000-4000-8000-000800000027','ed1a0000-0000-4000-8000-000100000009','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile-002','DEFENSE',8.50,'2026-10-21T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000028','ed1a0000-0000-4000-8000-000100000009','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile-003','DEFENSE',8.50,'2026-10-21T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000029','ed1a0000-0000-4000-8000-000100000009','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile-004','DEFENSE',9.00,'2026-10-21T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000030','ed1a0000-0000-4000-8000-000100000010',NULL,'lecturer-profile-013','GVPB',6.50,'2026-10-19T09:30:00Z','Đồng ý cho bảo vệ; cần bổ sung phần thảo luận hạn chế và hướng phát triển trong bản toàn văn.'),
    ('ed1a0000-0000-4000-8000-000800000031','ed1a0000-0000-4000-8000-000100000010','ed1a0000-0000-4000-8000-000500000002','lecturer-profile-027','DEFENSE',7.00,'2026-10-21T14:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000032','ed1a0000-0000-4000-8000-000100000010','ed1a0000-0000-4000-8000-000500000002','lecturer-profile-029','DEFENSE',7.00,'2026-10-21T14:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000033','ed1a0000-0000-4000-8000-000100000010','ed1a0000-0000-4000-8000-000500000002','lecturer-profile-030','DEFENSE',7.00,'2026-10-21T14:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000034','ed1a0000-0000-4000-8000-000100000012',NULL,'lecturer-profile-030','GVPB',8.00,'2026-10-16T09:00:00Z','Nội dung đạt yêu cầu tiểu luận chuyên ngành; đề nghị chỉnh lại hình thức trích dẫn theo khung APA của Khoa.'),
    ('ed1a0000-0000-4000-8000-000800000035','ed1a0000-0000-4000-8000-000100000012','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-026','DEFENSE',8.50,'2026-10-20T09:30:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000036','ed1a0000-0000-4000-8000-000100000012','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-023','DEFENSE',8.00,'2026-10-20T09:30:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000037','ed1a0000-0000-4000-8000-000100000012','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-024','DEFENSE',8.50,'2026-10-20T09:30:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000038','ed1a0000-0000-4000-8000-000100000013',NULL,'lecturer-profile-027','GVPB',7.00,'2026-10-17T10:00:00Z','Nội dung đạt yêu cầu tiểu luận chuyên ngành; đề nghị chỉnh lại hình thức trích dẫn theo khung APA của Khoa.'),
    ('ed1a0000-0000-4000-8000-000800000039','ed1a0000-0000-4000-8000-000100000013','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-026','DEFENSE',7.50,'2026-10-20T11:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000040','ed1a0000-0000-4000-8000-000100000013','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-023','DEFENSE',8.00,'2026-10-20T11:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000041','ed1a0000-0000-4000-8000-000100000013','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-024','DEFENSE',7.50,'2026-10-20T11:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000042','ed1a0000-0000-4000-8000-000100000014',NULL,'lecturer-profile-025','GVPB',8.50,'2026-10-18T14:00:00Z','Nội dung đạt yêu cầu tiểu luận chuyên ngành; đề nghị chỉnh lại hình thức trích dẫn theo khung APA của Khoa.'),
    ('ed1a0000-0000-4000-8000-000800000043','ed1a0000-0000-4000-8000-000100000014','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-026','DEFENSE',8.00,'2026-10-21T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000044','ed1a0000-0000-4000-8000-000100000014','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-023','DEFENSE',8.00,'2026-10-21T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000045','ed1a0000-0000-4000-8000-000100000014','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-024','DEFENSE',8.00,'2026-10-21T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000046','018f4a7c-3b12-4d90-a5e1-7c82b9045001',NULL,'lecturer-profile-025','GVPB',7.00,'2026-10-22T10:00:00Z','Bố cục báo cáo hợp lý; cần bổ sung số liệu thực nghiệm đối chứng trước khi kết luận hiệu quả giải pháp.'),
    ('ed1a0000-0000-4000-8000-000800000047','6f64670b-cd6b-4e1b-ae9d-0b497f2472a8',NULL,'lecturer-profile-024','GVPB',7.50,'2026-10-23T09:00:00Z','Phần cơ sở lý thuyết chặt chẽ; đề nghị nhóm bổ sung phân tích độ phức tạp của thuật toán đề xuất.'),
    ('ed1a0000-0000-4000-8000-000800000048','f580eadf-fffb-4db2-a7d8-423e83934dc2',NULL,'lecturer-profile-013','GVPB',6.50,'2026-10-23T15:00:00Z','Còn thiếu so sánh với ít nhất hai nghiên cứu liên quan; yêu cầu chỉnh sửa trước đợt phản biện tiếp theo.'),
    -- every eligible council seat scores each graded topic, like the real rounds
    ('ed1a0000-0000-4000-8000-000800000049','ed1a0000-0000-4000-8000-000100000001','7ea8f499-949e-42c2-b11e-02be39b8ec52','lecturer-profile-002','DEFENSE',8.00,'2026-10-14T10:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000050','ed1a0000-0000-4000-8000-000100000002','7ea8f499-949e-42c2-b11e-02be39b8ec52','lecturer-profile-002','DEFENSE',8.50,'2026-10-14T14:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000051','ed1a0000-0000-4000-8000-000100000003','ed1a0000-0000-4000-8000-000500000001','lecturer-profile-028','DEFENSE',6.50,'2026-10-15T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000052','ed1a0000-0000-4000-8000-000100000004','ed1a0000-0000-4000-8000-000500000001','lecturer-profile-028','DEFENSE',9.00,'2026-10-15T10:30:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000053','ed1a0000-0000-4000-8000-000100000007','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile','DEFENSE',8.00,'2026-10-20T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000054','ed1a0000-0000-4000-8000-000100000007','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile-005','DEFENSE',8.00,'2026-10-20T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000055','ed1a0000-0000-4000-8000-000100000008','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile','DEFENSE',7.50,'2026-10-20T10:30:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000056','ed1a0000-0000-4000-8000-000100000008','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile-005','DEFENSE',7.50,'2026-10-20T10:30:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000057','ed1a0000-0000-4000-8000-000100000009','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile','DEFENSE',8.50,'2026-10-21T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000058','ed1a0000-0000-4000-8000-000100000009','274ce36d-b5b0-4afa-bd33-19eb6b0095ca','lecturer-profile-005','DEFENSE',9.00,'2026-10-21T09:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000059','ed1a0000-0000-4000-8000-000100000010','ed1a0000-0000-4000-8000-000500000002','lecturer-profile-026','DEFENSE',7.00,'2026-10-21T14:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000060','ed1a0000-0000-4000-8000-000100000012','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-011','DEFENSE',8.00,'2026-10-20T09:30:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000061','ed1a0000-0000-4000-8000-000100000013','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-011','DEFENSE',7.50,'2026-10-20T11:00:00Z',NULL),
    ('ed1a0000-0000-4000-8000-000800000062','ed1a0000-0000-4000-8000-000100000014','ed1a0000-0000-4000-8000-000500000003','lecturer-profile-011','DEFENSE',8.00,'2026-10-21T09:00:00Z',NULL)
) AS v(id, tid, cid, lec, comp, score, at, comm)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_topic t WHERE t.id = v.tid::uuid)
  AND EXISTS (SELECT 1 FROM academic."Lecturer" l WHERE l."id" = v.lec)
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_topic_score s
                  WHERE s.topic_id = v.tid::uuid AND s.lecturer_id = v.lec AND s.component = v.comp)
ON CONFLICT (id) DO NOTHING;

-- group reports (17) — production provider 'database'
INSERT INTO thesis.thesis_group_report
    (id, group_id, round_id, submitted_by, title, url, note, submitted_at, updated_at,
     file_name, file_type, file_size, file_data, storage_provider, storage_bucket, storage_key, file_sha256)
SELECT v.id::uuid, v.gid::uuid, v.round::uuid, v.by, v.title, NULL, v.note, v.at::timestamptz, v.at::timestamptz,
     v.fname, 'application/pdf', v.fsize, decode(v.fhex, 'hex'), 'database', 'thesis-reports', NULL, v.fsha
FROM (VALUES
    ('ed1a0000-0000-4000-8000-000900000001','ed1a0000-0000-4000-8000-000300000001','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-021','Báo cáo toàn văn đề tài "Hệ thống khai phá dữ liệu nhật ký học tập phục vụ cảnh báo học vụ sớm"','Bản nộp chính thức sau phản biện sơ bộ của giảng viên hướng dẫn.','2026-10-05T16:00:00Z','TLCN_Dot2_NK2125_Nhom1_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000002','ed1a0000-0000-4000-8000-000300000002','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-025','Báo cáo toàn văn đề tài "Giám sát an ninh mạng nội bộ bằng mô hình phát hiện bất thường lưu lượng"','Bản nộp chính thức sau phản biện sơ bộ của giảng viên hướng dẫn.','2026-10-05T16:00:00Z','TLCN_Dot2_NK2125_Nhom2_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000003','ed1a0000-0000-4000-8000-000300000003','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-028','Báo cáo toàn văn đề tài "Mô hình gợi ý lộ trình học tập cá nhân hóa dựa trên kết quả tích lũy"','Bản nộp chính thức sau phản biện sơ bộ của giảng viên hướng dẫn.','2026-10-05T16:00:00Z','TLCN_Dot2_NK2125_Nhom3_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000004','ed1a0000-0000-4000-8000-000300000004','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-109','Báo cáo toàn văn đề tài "Cổng tra cứu văn bằng điện tử tích hợp xác thực chữ ký số cho phòng đào tạo"','Bản nộp chính thức sau phản biện sơ bộ của giảng viên hướng dẫn.','2026-10-05T16:00:00Z','TLCN_Dot2_NK2125_Nhom4_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000005','ed1a0000-0000-4000-8000-000300000005','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-138','Báo cáo toàn văn đề tài "Nền tảng quản lý bài tập lớn nhóm với phân tích đóng góp thành viên"','Bản nộp chính thức sau phản biện sơ bộ của giảng viên hướng dẫn.','2026-10-05T16:00:00Z','TLCN_Dot2_NK2125_Nhom5_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000006','ed1a0000-0000-4000-8000-000300000006','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','student-profile-153','Báo cáo toàn văn đề tài "Hệ thống lập lịch phòng thi tối ưu theo ràng buộc giảng viên và sĩ số"','Bản nộp chính thức sau phản biện sơ bộ của giảng viên hướng dẫn.','2026-10-05T16:00:00Z','TLCN_Dot2_NK2125_Nhom6_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000007','ed1a0000-0000-4000-8000-000300000007','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-157','Báo cáo khóa luận tốt nghiệp "Kiến trúc sự kiện phân tán cho hệ thống đăng ký học phần chịu tải cao"','Bản nộp chính thức; đã đối chiếu danh mục tài liệu tham khảo theo hướng dẫn của giảng viên.','2026-10-14T16:00:00Z','KLTN_Dot1_NK2125_Nhom7_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000008','ed1a0000-0000-4000-8000-000300000008','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-161','Báo cáo khóa luận tốt nghiệp "Phát hiện đạo văn học thuật tiếng Việt bằng embedding đa ngữ và đồ thị trích dẫn"','Bản nộp chính thức; đã đối chiếu danh mục tài liệu tham khảo theo hướng dẫn của giảng viên.','2026-10-14T16:00:00Z','KLTN_Dot1_NK2125_Nhom8_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000009','ed1a0000-0000-4000-8000-000300000009','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-164','Báo cáo khóa luận tốt nghiệp "Kho dữ liệu phục vụ đánh giá chuẩn đầu ra chương trình đào tạo theo chu kỳ kiểm chứng"','Bản nộp chính thức; đã đối chiếu danh mục tài liệu tham khảo theo hướng dẫn của giảng viên.','2026-10-14T16:00:00Z','KLTN_Dot1_NK2125_Nhom9_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000010','ed1a0000-0000-4000-8000-000300000010','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-167','Báo cáo khóa luận tốt nghiệp "Mạng cảm biến không dây giám sát môi trường phòng thí nghiệm thời gian thực"','Bản nộp chính thức; đã đối chiếu danh mục tài liệu tham khảo theo hướng dẫn của giảng viên.','2026-10-14T16:00:00Z','KLTN_Dot1_NK2125_Nhom10_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000011','ed1a0000-0000-4000-8000-000300000011','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','student-profile-170','Báo cáo khóa luận tốt nghiệp "Phân tích và đánh giá chính sách kiểm soát truy cập Zero-Trust cho hệ thống học vụ"','Bản nộp chính thức; đã đối chiếu danh mục tài liệu tham khảo theo hướng dẫn của giảng viên.','2026-10-14T16:00:00Z','KLTN_Dot1_NK2125_Nhom11_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000012','ed1a0000-0000-4000-8000-000300000012','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-175','Báo cáo tiểu luận chuyên ngành "Hệ thống hỗ trợ ra quyết định xét cảnh báo học tập theo ma trận rủi ro"','Bản nộp chính thức theo mẫu tiểu luận của Bộ môn; đã rà soát trích dẫn.','2026-10-12T16:00:00Z','TLCN_HK2_NK2226_Nhom12_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000013','ed1a0000-0000-4000-8000-000300000013','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-179','Báo cáo tiểu luận chuyên ngành "Bộ công cụ kiểm thử hợp đồng API tự động cho hệ thống microservice học vụ"','Bản nộp chính thức theo mẫu tiểu luận của Bộ môn; đã rà soát trích dẫn.','2026-10-12T16:00:00Z','TLCN_HK2_NK2226_Nhom13_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000014','ed1a0000-0000-4000-8000-000300000014','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-182','Báo cáo tiểu luận chuyên ngành "Hệ thống điểm danh phòng máy bằng nhận diện thẻ từ và đối chiếu lịch sử dụng"','Bản nộp chính thức theo mẫu tiểu luận của Bộ môn; đã rà soát trích dẫn.','2026-10-12T16:00:00Z','TLCN_HK2_NK2226_Nhom14_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000015','ed1a0000-0000-4000-8000-000300000015','bad8eba0-2d12-4414-8aab-7424174b3b97','student-profile-185','Báo cáo tiểu luận chuyên ngành "Cổng thông tin tuyển sinh thông minh tích hợp tư vấn ngành học tự động"','Bản nộp chính thức theo mẫu tiểu luận của Bộ môn; đã rà soát trích dẫn.','2026-10-12T16:00:00Z','TLCN_HK2_NK2226_Nhom15_BaoCao.pdf',588,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038313e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f20746f616e2076616e20646520746169206b686f61206c75616e202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333435203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430380a2525454f460a','c35329df0ee0e4c8a3cb7a0d45fe8784b944ca5fa2a50d8c89a6e2c90cc5fc0a'),
    ('ed1a0000-0000-4000-8000-000900000016','ed1a0000-0000-4000-8000-000300000016','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-189','Báo cáo tiến độ tuần 2 - triển khai phần cứng và firmware cơ sở','Báo cáo tiến độ theo yêu cầu giảng viên hướng dẫn; nhóm đã hoàn thành sơ đồ mạch và chạy thử firmware cơ bản.','2026-10-20T16:00:00Z','TLCN_HK1_NK2327_NhomESP_TienDo2.pdf',589,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038323e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f207469656e20646f20646f20616e2063687579656e206e67616e68202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333436203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430390a2525454f460a','12869935e81546f8b3993bbec6fc4aec8b95ac1e18a680e1aed273399ef8aef7'),
    ('ed1a0000-0000-4000-8000-000900000017','ed1a0000-0000-4000-8000-000300000019','22d65ee6-f485-40ae-a66b-528d35007745','student-profile-196','Báo cáo tiến độ tuần 1 - khảo sát yêu cầu và thiết kế tổng thể','Bản khảo sát sau khi nhóm được phân công lại đề tài; phạm vi công việc đã chốt với giảng viên.','2026-10-22T16:00:00Z','TLCN_HK1_NK2327_NhomRe_TienDo1.pdf',589,'255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682038323e3e73747265616d0a4254202f463120313420546620353020373830205464202842616f2063616f207469656e20646f20646f20616e2063687579656e206e67616e68202d206e686f6d2074687563206869656e2920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333436203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3430390a2525454f460a','12869935e81546f8b3993bbec6fc4aec8b95ac1e18a680e1aed273399ef8aef7')
) AS v(id, gid, round, by, title, note, at, fname, fsize, fhex, fsha)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_group g WHERE g.id = v.gid::uuid AND g.status <> 'CANCELLED')
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group_report rep WHERE rep.group_id = v.gid::uuid)
ON CONFLICT (id) DO NOTHING;

-- groups left pointing at withdrawn topics are cancelled the way the app does
-- (roster released by trigger; their reports stay attached as history). Group
-- ...000300000018 is the group that was reassigned to a replacement topic.
UPDATE thesis.thesis_group SET status = 'CANCELLED', updated_at = '2026-10-18T09:00:00Z'::timestamptz, version = version + 1
WHERE id IN ('ed1a0000-0000-4000-8000-000300000006',
             'ed1a0000-0000-4000-8000-000300000011',
             'ed1a0000-0000-4000-8000-000300000015',
             'ed1a0000-0000-4000-8000-000300000018')
  AND status <> 'CANCELLED';

-- notifications (107)
INSERT INTO notifications.notification
    (id, user_id, title, message, type, link, is_read, read_at, created_at, updated_at, source_key, announcement_id)
SELECT v.id, v.uid, v.title, v.msg, v.type, v.link, v.read, CASE WHEN v.read THEN v.at::timestamptz ELSE NULL END,
     v.at::timestamptz, v.at::timestamptz, v.skey, NULL
FROM (VALUES
    ('demo-th-notif-0001','student-user-021','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0002','student-user-022','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0003','student-user-023','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0004','student-user-024','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0005','student-user-021','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống khai phá dữ liệu nhật ký học tập phục vụ cảnh báo học vụ sớm". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-15T14:00:00Z'),
    ('demo-th-notif-0006','student-user-022','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống khai phá dữ liệu nhật ký học tập phục vụ cảnh báo học vụ sớm". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-15T14:00:00Z'),
    ('demo-th-notif-0007','student-user-023','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống khai phá dữ liệu nhật ký học tập phục vụ cảnh báo học vụ sớm". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-15T14:00:00Z'),
    ('demo-th-notif-0008','student-user-024','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống khai phá dữ liệu nhật ký học tập phục vụ cảnh báo học vụ sớm". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-15T14:00:00Z'),
    ('demo-th-notif-0009','lecturer-user-029','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống khai phá dữ liệu nhật ký học tập phục vụ cảnh báo học vụ sớm". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-15T14:00:00Z'),
    ('demo-th-notif-0010','student-user-025','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0011','student-user-026','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0012','student-user-027','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0013','student-user-025','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Giám sát an ninh mạng nội bộ bằng mô hình phát hiện bất thường lưu lượng". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-15T14:30:00Z'),
    ('demo-th-notif-0014','student-user-026','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Giám sát an ninh mạng nội bộ bằng mô hình phát hiện bất thường lưu lượng". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-15T14:30:00Z'),
    ('demo-th-notif-0015','student-user-027','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Giám sát an ninh mạng nội bộ bằng mô hình phát hiện bất thường lưu lượng". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-15T14:30:00Z'),
    ('demo-th-notif-0016','lecturer-user-028','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Giám sát an ninh mạng nội bộ bằng mô hình phát hiện bất thường lưu lượng". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-15T14:30:00Z'),
    ('demo-th-notif-0017','student-user-028','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0018','student-user-029','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0019','student-user-030','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0020','student-user-028','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Mô hình gợi ý lộ trình học tập cá nhân hóa dựa trên kết quả tích lũy". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-16T10:00:00Z'),
    ('demo-th-notif-0021','student-user-029','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Mô hình gợi ý lộ trình học tập cá nhân hóa dựa trên kết quả tích lũy". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-16T10:00:00Z'),
    ('demo-th-notif-0022','student-user-030','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Mô hình gợi ý lộ trình học tập cá nhân hóa dựa trên kết quả tích lũy". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-16T10:00:00Z'),
    ('demo-th-notif-0023','lecturer-user-026','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Mô hình gợi ý lộ trình học tập cá nhân hóa dựa trên kết quả tích lũy". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-16T10:00:00Z'),
    ('demo-th-notif-0024','student-user-109','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0025','student-user-110','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0026','student-user-120','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0027','student-user-136','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0028','student-user-137','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',TRUE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0029','student-user-109','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Cổng tra cứu văn bằng điện tử tích hợp xác thực chữ ký số cho phòng đào tạo". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-16T10:30:00Z'),
    ('demo-th-notif-0030','student-user-110','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Cổng tra cứu văn bằng điện tử tích hợp xác thực chữ ký số cho phòng đào tạo". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-16T10:30:00Z'),
    ('demo-th-notif-0031','student-user-120','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Cổng tra cứu văn bằng điện tử tích hợp xác thực chữ ký số cho phòng đào tạo". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-16T10:30:00Z'),
    ('demo-th-notif-0032','student-user-136','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Cổng tra cứu văn bằng điện tử tích hợp xác thực chữ ký số cho phòng đào tạo". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-16T10:30:00Z'),
    ('demo-th-notif-0033','student-user-137','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Cổng tra cứu văn bằng điện tử tích hợp xác thực chữ ký số cho phòng đào tạo". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-16T10:30:00Z'),
    ('demo-th-notif-0034','truongkhoa-user-001','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Cổng tra cứu văn bằng điện tử tích hợp xác thực chữ ký số cho phòng đào tạo". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-16T10:30:00Z'),
    ('demo-th-notif-0035','student-user-138','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Đề tài của nhóm bạn không đủ điều kiện bảo vệ trong đợt này — xem chi tiết tại trang khóa luận.','WARNING','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',FALSE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0036','student-user-139','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Đề tài của nhóm bạn không đủ điều kiện bảo vệ trong đợt này — xem chi tiết tại trang khóa luận.','WARNING','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',FALSE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0037','student-user-140','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Đề tài của nhóm bạn không đủ điều kiện bảo vệ trong đợt này — xem chi tiết tại trang khóa luận.','WARNING','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',FALSE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0038','student-user-153','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Đề tài của nhóm bạn không đủ điều kiện bảo vệ trong đợt này — xem chi tiết tại trang khóa luận.','WARNING','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',FALSE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0039','student-user-154','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Đề tài của nhóm bạn không đủ điều kiện bảo vệ trong đợt này — xem chi tiết tại trang khóa luận.','WARNING','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',FALSE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0040','student-user-155','Kết quả khóa luận đã công bố','Kết quả đợt Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025) đã công bố. Đề tài của nhóm bạn không đủ điều kiện bảo vệ trong đợt này — xem chi tiết tại trang khóa luận.','WARNING','/dashboard/thesis','THESIS_RESULTS:b35e9ff1-1eb8-4e73-812e-04e1a66041f1',FALSE,'2026-10-18T09:00:00Z'),
    ('demo-th-notif-0041','student-user-157','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0042','student-user-158','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0043','student-user-159','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0044','student-user-160','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0045','student-user-157','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Kiến trúc sự kiện phân tán cho hệ thống đăng ký học phần chịu tải cao". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T09:30:00Z'),
    ('demo-th-notif-0046','student-user-158','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Kiến trúc sự kiện phân tán cho hệ thống đăng ký học phần chịu tải cao". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T09:30:00Z'),
    ('demo-th-notif-0047','student-user-159','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Kiến trúc sự kiện phân tán cho hệ thống đăng ký học phần chịu tải cao". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T09:30:00Z'),
    ('demo-th-notif-0048','student-user-160','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Kiến trúc sự kiện phân tán cho hệ thống đăng ký học phần chịu tải cao". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T09:30:00Z'),
    ('demo-th-notif-0049','lecturer-user-027','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Kiến trúc sự kiện phân tán cho hệ thống đăng ký học phần chịu tải cao". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T09:30:00Z'),
    ('demo-th-notif-0050','student-user-161','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0051','student-user-162','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0052','student-user-163','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0053','student-user-161','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Phát hiện đạo văn học thuật tiếng Việt bằng embedding đa ngữ và đồ thị trích dẫn". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T10:00:00Z'),
    ('demo-th-notif-0054','student-user-162','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Phát hiện đạo văn học thuật tiếng Việt bằng embedding đa ngữ và đồ thị trích dẫn". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T10:00:00Z'),
    ('demo-th-notif-0055','student-user-163','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Phát hiện đạo văn học thuật tiếng Việt bằng embedding đa ngữ và đồ thị trích dẫn". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T10:00:00Z'),
    ('demo-th-notif-0056','lecturer-user-026','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Phát hiện đạo văn học thuật tiếng Việt bằng embedding đa ngữ và đồ thị trích dẫn". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T10:00:00Z'),
    ('demo-th-notif-0057','student-user-164','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0058','student-user-165','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0059','student-user-166','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0060','student-user-164','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Kho dữ liệu phục vụ đánh giá chuẩn đầu ra chương trình đào tạo theo chu kỳ kiểm chứng". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0061','student-user-165','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Kho dữ liệu phục vụ đánh giá chuẩn đầu ra chương trình đào tạo theo chu kỳ kiểm chứng". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0062','student-user-166','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Kho dữ liệu phục vụ đánh giá chuẩn đầu ra chương trình đào tạo theo chu kỳ kiểm chứng". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0063','lecturer-user-029','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Kho dữ liệu phục vụ đánh giá chuẩn đầu ra chương trình đào tạo theo chu kỳ kiểm chứng". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0064','student-user-167','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0065','student-user-168','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0066','student-user-169','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0067','student-user-167','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Mạng cảm biến không dây giám sát môi trường phòng thí nghiệm thời gian thực". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-22T09:30:00Z'),
    ('demo-th-notif-0068','student-user-168','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Mạng cảm biến không dây giám sát môi trường phòng thí nghiệm thời gian thực". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-22T09:30:00Z'),
    ('demo-th-notif-0069','student-user-169','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Mạng cảm biến không dây giám sát môi trường phòng thí nghiệm thời gian thực". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-22T09:30:00Z'),
    ('demo-th-notif-0070','lecturer-user-014','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Mạng cảm biến không dây giám sát môi trường phòng thí nghiệm thời gian thực". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-22T09:30:00Z'),
    ('demo-th-notif-0071','student-user-170','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Đề tài của nhóm bạn không đủ điều kiện bảo vệ trong đợt này — xem chi tiết tại trang khóa luận.','WARNING','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0072','student-user-171','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Đề tài của nhóm bạn không đủ điều kiện bảo vệ trong đợt này — xem chi tiết tại trang khóa luận.','WARNING','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0073','student-user-172','Kết quả khóa luận đã công bố','Kết quả đợt Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026) đã công bố. Đề tài của nhóm bạn không đủ điều kiện bảo vệ trong đợt này — xem chi tiết tại trang khóa luận.','WARNING','/dashboard/thesis','THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',FALSE,'2026-10-23T09:00:00Z'),
    ('demo-th-notif-0074','student-user-175','Kết quả khóa luận đã công bố','Kết quả đợt Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:bad8eba0-2d12-4414-8aab-7424174b3b97',TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0075','student-user-176','Kết quả khóa luận đã công bố','Kết quả đợt Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:bad8eba0-2d12-4414-8aab-7424174b3b97',TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0076','student-user-177','Kết quả khóa luận đã công bố','Kết quả đợt Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:bad8eba0-2d12-4414-8aab-7424174b3b97',TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0077','student-user-178','Kết quả khóa luận đã công bố','Kết quả đợt Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:bad8eba0-2d12-4414-8aab-7424174b3b97',TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0078','student-user-175','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống hỗ trợ ra quyết định xét cảnh báo học tập theo ma trận rủi ro". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T10:00:00Z'),
    ('demo-th-notif-0079','student-user-176','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống hỗ trợ ra quyết định xét cảnh báo học tập theo ma trận rủi ro". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T10:00:00Z'),
    ('demo-th-notif-0080','student-user-177','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống hỗ trợ ra quyết định xét cảnh báo học tập theo ma trận rủi ro". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T10:00:00Z'),
    ('demo-th-notif-0081','student-user-178','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống hỗ trợ ra quyết định xét cảnh báo học tập theo ma trận rủi ro". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T10:00:00Z'),
    ('demo-th-notif-0082','lecturer-user-029','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống hỗ trợ ra quyết định xét cảnh báo học tập theo ma trận rủi ro". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T10:00:00Z'),
    ('demo-th-notif-0083','student-user-179','Kết quả khóa luận đã công bố','Kết quả đợt Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:bad8eba0-2d12-4414-8aab-7424174b3b97',TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0084','student-user-180','Kết quả khóa luận đã công bố','Kết quả đợt Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:bad8eba0-2d12-4414-8aab-7424174b3b97',TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0085','student-user-181','Kết quả khóa luận đã công bố','Kết quả đợt Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:bad8eba0-2d12-4414-8aab-7424174b3b97',TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0086','student-user-179','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Bộ công cụ kiểm thử hợp đồng API tự động cho hệ thống microservice học vụ". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T10:30:00Z'),
    ('demo-th-notif-0087','student-user-180','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Bộ công cụ kiểm thử hợp đồng API tự động cho hệ thống microservice học vụ". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T10:30:00Z'),
    ('demo-th-notif-0088','student-user-181','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Bộ công cụ kiểm thử hợp đồng API tự động cho hệ thống microservice học vụ". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T10:30:00Z'),
    ('demo-th-notif-0089','lecturer-user-030','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Bộ công cụ kiểm thử hợp đồng API tự động cho hệ thống microservice học vụ". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T10:30:00Z'),
    ('demo-th-notif-0090','student-user-182','Kết quả khóa luận đã công bố','Kết quả đợt Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:bad8eba0-2d12-4414-8aab-7424174b3b97',TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0091','student-user-183','Kết quả khóa luận đã công bố','Kết quả đợt Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:bad8eba0-2d12-4414-8aab-7424174b3b97',TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0092','student-user-184','Kết quả khóa luận đã công bố','Kết quả đợt Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026) đã công bố. Mở trang khóa luận để xem điểm của bạn.','SUCCESS','/dashboard/thesis','THESIS_RESULTS:bad8eba0-2d12-4414-8aab-7424174b3b97',TRUE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0093','student-user-182','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống điểm danh phòng máy bằng nhận diện thẻ từ và đối chiếu lịch sử dụng". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T16:00:00Z'),
    ('demo-th-notif-0094','student-user-183','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống điểm danh phòng máy bằng nhận diện thẻ từ và đối chiếu lịch sử dụng". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T16:00:00Z'),
    ('demo-th-notif-0095','student-user-184','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống điểm danh phòng máy bằng nhận diện thẻ từ và đối chiếu lịch sử dụng". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T16:00:00Z'),
    ('demo-th-notif-0096','lecturer-user-028','Hội đồng đã chốt điểm','Hội đồng bảo vệ đã chốt điểm cho đề tài "Hệ thống điểm danh phòng máy bằng nhận diện thẻ từ và đối chiếu lịch sử dụng". Điểm chính thức hiển thị sau khi đợt công bố kết quả.','INFO','/dashboard/thesis',NULL,TRUE,'2026-10-21T16:00:00Z'),
    ('demo-th-notif-0097','student-user-185','Kết quả khóa luận đã công bố','Kết quả đợt Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026) đã công bố. Đề tài của nhóm bạn không đủ điều kiện bảo vệ trong đợt này — xem chi tiết tại trang khóa luận.','WARNING','/dashboard/thesis','THESIS_RESULTS:bad8eba0-2d12-4414-8aab-7424174b3b97',FALSE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0098','student-user-186','Kết quả khóa luận đã công bố','Kết quả đợt Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026) đã công bố. Đề tài của nhóm bạn không đủ điều kiện bảo vệ trong đợt này — xem chi tiết tại trang khóa luận.','WARNING','/dashboard/thesis','THESIS_RESULTS:bad8eba0-2d12-4414-8aab-7424174b3b97',FALSE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0099','student-user-187','Kết quả khóa luận đã công bố','Kết quả đợt Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026) đã công bố. Đề tài của nhóm bạn không đủ điều kiện bảo vệ trong đợt này — xem chi tiết tại trang khóa luận.','WARNING','/dashboard/thesis','THESIS_RESULTS:bad8eba0-2d12-4414-8aab-7424174b3b97',FALSE,'2026-10-22T09:00:00Z'),
    ('demo-th-notif-0100','student-user-189','Nhóm khóa luận được duyệt','Nhóm của bạn đã được duyệt với ngoại lệ sĩ số theo quyết định Bộ môn. Theo dõi tiến độ tại trang khóa luận.','SUCCESS','/dashboard/thesis',NULL,TRUE,'2026-10-14T10:05:00Z'),
    ('demo-th-notif-0101','student-user-190','Nhóm khóa luận được duyệt','Nhóm của bạn đã được duyệt với ngoại lệ sĩ số theo quyết định Bộ môn. Theo dõi tiến độ tại trang khóa luận.','SUCCESS','/dashboard/thesis',NULL,TRUE,'2026-10-14T10:05:00Z'),
    ('demo-th-notif-0102','student-user-196','Nhóm khóa luận được duyệt','Nhóm của bạn đã được duyệt và được phân công đề tài mới. Theo dõi tiến độ tại trang khóa luận.','SUCCESS','/dashboard/thesis',NULL,TRUE,'2026-10-20T10:05:00Z'),
    ('demo-th-notif-0103','student-user-197','Nhóm khóa luận được duyệt','Nhóm của bạn đã được duyệt và được phân công đề tài mới. Theo dõi tiến độ tại trang khóa luận.','SUCCESS','/dashboard/thesis',NULL,TRUE,'2026-10-20T10:05:00Z'),
    ('demo-th-notif-0104','student-user-198','Nhóm khóa luận được duyệt','Nhóm của bạn đã được duyệt và được phân công đề tài mới. Theo dõi tiến độ tại trang khóa luận.','SUCCESS','/dashboard/thesis',NULL,TRUE,'2026-10-20T10:05:00Z'),
    ('demo-th-notif-0105','student-user-199','Nhóm khóa luận được duyệt','Nhóm của bạn đã được duyệt và được phân công đề tài mới. Theo dõi tiến độ tại trang khóa luận.','SUCCESS','/dashboard/thesis',NULL,TRUE,'2026-10-20T10:05:00Z'),
    ('demo-th-notif-0106','student-user-191','Nhóm khóa luận bị từ chối','Nhóm của bạn bị từ chối. Lý do: Danh sách thành viên chưa đủ sĩ số tối thiểu 3 sinh viên; nhóm cần bổ sung thành viên và nộp lại trước hạn đăng ký','WARNING','/dashboard/thesis',NULL,FALSE,'2026-10-15T10:05:00Z'),
    ('demo-th-notif-0107','student-user-192','Nhóm khóa luận bị từ chối','Nhóm của bạn bị từ chối. Lý do: Danh sách thành viên chưa đủ sĩ số tối thiểu 3 sinh viên; nhóm cần bổ sung thành viên và nộp lại trước hạn đăng ký','WARNING','/dashboard/thesis',NULL,FALSE,'2026-10-15T10:05:00Z')
) AS v(id, uid, title, msg, type, link, skey, read, at)
WHERE EXISTS (SELECT 1 FROM campuscore_auth."User" u WHERE u."id" = v.uid)
ON CONFLICT (id) DO NOTHING;

-- topic-level governance writes (gvpb reviewer assignments + waiver) and round publication instants.
-- Before writing, snapshot the current values of every column the package touches so
-- rollback.sql can restore them exactly (stored in audit row 'demo-th-audit-000').
-- Values are encoded with to_jsonb(... )#>>'{}' — ISO 8601 with offset for
-- timestamps, never the session DateStyle — under the pinned UTC/ISO session
-- settings above, so the image round-trips byte-identically under any client.
CREATE TEMP TABLE _demo_before (tbl text, id text, col text, before_val text, applied_val text) ON COMMIT DROP;
INSERT INTO _demo_before
SELECT 'thesis_registration_round', r.id::text, x.col, x.val,
       CASE x.col WHEN 'version' THEN to_jsonb(r.version + 1)#>>'{}'
                  WHEN 'updated_at' THEN '2026-10-16T17:00:00+00:00'
                  WHEN 'results_published_at' THEN '2026-10-16T17:00:00+00:00' END
FROM thesis.thesis_registration_round r
CROSS JOIN LATERAL (VALUES ('version', to_jsonb(r.version)#>>'{}'),
                           ('updated_at', to_jsonb(r.updated_at)#>>'{}'),
                           ('results_published_at', to_jsonb(r.results_published_at)#>>'{}')) x(col, val)
WHERE r.id = 'b35e9ff1-1eb8-4e73-812e-04e1a66041f1' AND r.results_published_at IS NULL;
INSERT INTO _demo_before
SELECT 'thesis_registration_round', r.id::text, x.col, x.val,
       CASE x.col WHEN 'version' THEN to_jsonb(r.version + 1)#>>'{}'
                  WHEN 'updated_at' THEN '2026-10-23T09:00:00+00:00'
                  WHEN 'results_published_at' THEN '2026-10-23T09:00:00+00:00' END
FROM thesis.thesis_registration_round r
CROSS JOIN LATERAL (VALUES ('version', to_jsonb(r.version)#>>'{}'),
                           ('updated_at', to_jsonb(r.updated_at)#>>'{}'),
                           ('results_published_at', to_jsonb(r.results_published_at)#>>'{}')) x(col, val)
WHERE r.id = 'be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1' AND r.results_published_at IS NULL;
INSERT INTO _demo_before
SELECT 'thesis_registration_round', r.id::text, x.col, x.val,
       CASE x.col WHEN 'version' THEN to_jsonb(r.version + 1)#>>'{}'
                  WHEN 'updated_at' THEN '2026-10-22T09:00:00+00:00'
                  WHEN 'results_published_at' THEN '2026-10-22T09:00:00+00:00' END
FROM thesis.thesis_registration_round r
CROSS JOIN LATERAL (VALUES ('version', to_jsonb(r.version)#>>'{}'),
                           ('updated_at', to_jsonb(r.updated_at)#>>'{}'),
                           ('results_published_at', to_jsonb(r.results_published_at)#>>'{}')) x(col, val)
WHERE r.id = 'bad8eba0-2d12-4414-8aab-7424174b3b97' AND r.results_published_at IS NULL;
INSERT INTO _demo_before
SELECT 'thesis_topic', t.id::text, x.col, x.val,
       CASE x.col WHEN 'version' THEN to_jsonb(t.version + 1)#>>'{}'
                  WHEN 'updated_at' THEN '2026-10-23T15:30:00+00:00'
                  WHEN 'gvpb_lecturer_id' THEN x.new END
FROM thesis.thesis_topic t
CROSS JOIN LATERAL (VALUES
       ('version', to_jsonb(t.version)#>>'{}', NULL::text),
       ('updated_at', to_jsonb(t.updated_at)#>>'{}', NULL::text),
       ('gvpb_lecturer_id', to_jsonb(t.gvpb_lecturer_id)#>>'{}',
            CASE t.id::text WHEN '018f4a7c-3b12-4d90-a5e1-7c82b9045001' THEN 'lecturer-profile-025'
                            WHEN '6f64670b-cd6b-4e1b-ae9d-0b497f2472a8' THEN 'lecturer-profile-024'
                            WHEN 'f580eadf-fffb-4db2-a7d8-423e83934dc2' THEN 'lecturer-profile-013' END)
       ) x(col, val, new)
WHERE t.id IN ('018f4a7c-3b12-4d90-a5e1-7c82b9045001','6f64670b-cd6b-4e1b-ae9d-0b497f2472a8',
               'f580eadf-fffb-4db2-a7d8-423e83934dc2') AND t.gvpb_lecturer_id IS NULL;
INSERT INTO _demo_before
SELECT 'thesis_topic', t.id::text, x.col, x.val, x.new
FROM thesis.thesis_topic t
CROSS JOIN LATERAL (VALUES
       ('version', to_jsonb(t.version)#>>'{}', to_jsonb(t.version + 1)#>>'{}'),
       ('updated_at', to_jsonb(t.updated_at)#>>'{}', '2026-10-23T15:30:00+00:00'),
       ('gvpb_waived_reason', to_jsonb(t.gvpb_waived_reason)#>>'{}',
            'Miễn phản biện theo Quyết định 214/QĐ-KCNTT: đề tài thuộc nhóm chuyên ngành hẹp, đã qua hai vòng xét duyệt của Bộ môn'),
       ('gvpb_waived_by', to_jsonb(t.gvpb_waived_by)#>>'{}', 'truongkhoa-user-001'),
       ('gvpb_waived_at', to_jsonb(t.gvpb_waived_at)#>>'{}', '2026-10-15T09:00:00+00:00')
       ) x(col, val, new)
WHERE t.id = '67c74df5-5c7f-4e74-aa02-d51af7207fe9' AND t.gvpb_waived_reason IS NULL;

UPDATE thesis.thesis_registration_round SET results_published_at = '2026-10-16T17:00:00Z'::timestamptz, updated_at = '2026-10-16T17:00:00Z'::timestamptz, version = version + 1 WHERE id = 'b35e9ff1-1eb8-4e73-812e-04e1a66041f1' AND results_published_at IS NULL;
UPDATE thesis.thesis_registration_round SET results_published_at = '2026-10-23T09:00:00Z'::timestamptz, updated_at = '2026-10-23T09:00:00Z'::timestamptz, version = version + 1 WHERE id = 'be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1' AND results_published_at IS NULL;
UPDATE thesis.thesis_registration_round SET results_published_at = '2026-10-22T09:00:00Z'::timestamptz, updated_at = '2026-10-22T09:00:00Z'::timestamptz, version = version + 1 WHERE id = 'bad8eba0-2d12-4414-8aab-7424174b3b97' AND results_published_at IS NULL;
UPDATE thesis.thesis_topic SET gvpb_lecturer_id = 'lecturer-profile-025', updated_at = '2026-10-23T15:30:00Z'::timestamptz, version = version + 1 WHERE id = '018f4a7c-3b12-4d90-a5e1-7c82b9045001' AND gvpb_lecturer_id IS NULL;
UPDATE thesis.thesis_topic SET gvpb_lecturer_id = 'lecturer-profile-024', updated_at = '2026-10-23T15:30:00Z'::timestamptz, version = version + 1 WHERE id = '6f64670b-cd6b-4e1b-ae9d-0b497f2472a8' AND gvpb_lecturer_id IS NULL;
UPDATE thesis.thesis_topic SET gvpb_lecturer_id = 'lecturer-profile-013', updated_at = '2026-10-23T15:30:00Z'::timestamptz, version = version + 1 WHERE id = 'f580eadf-fffb-4db2-a7d8-423e83934dc2' AND gvpb_lecturer_id IS NULL;
UPDATE thesis.thesis_topic SET gvpb_waived_reason = 'Miễn phản biện theo Quyết định 214/QĐ-KCNTT: đề tài thuộc nhóm chuyên ngành hẹp, đã qua hai vòng xét duyệt của Bộ môn', gvpb_waived_by = 'truongkhoa-user-001', gvpb_waived_at = '2026-10-15T09:00:00Z'::timestamptz, updated_at = '2026-10-23T15:30:00Z'::timestamptz, version = version + 1 WHERE id = '67c74df5-5c7f-4e74-aa02-d51af7207fe9' AND gvpb_waived_reason IS NULL;
-- audit trail (6): one before-image row + five per-round summaries.
-- 'demo-th-audit-000' is the durable before-image store consumed by rollback.sql;
-- it must be inserted BEFORE this transaction commits and is never deleted by apply.
INSERT INTO campuscore_audit."AdminAudit" (id, "actorId", "actorLabel", action, "entityType", "entityId", summary, "beforeState", "afterState", "createdAt")
SELECT v.id, v.actor, NULL, v.action, v.etype, v.eid, v.summary, NULL, v.after, CURRENT_TIMESTAMP
FROM (VALUES
    ('demo-th-audit-000','admin-user','THESIS_DEMO_DATA_ENRICHED','THESIS_PACKAGE','thesis-demo-data','Before-image of every pre-existing row this package updates (round publication fields, topic reviewer/waiver fields, version, updated_at). rollback.sql restores these values when the current value still matches the applied value.',NULL),
    ('demo-th-audit-001','admin-user','THESIS_DEMO_DATA_ENRICHED','THESIS_ROUND','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','Enrichment: 6 topics, 6 approved groups, 1 council, counter-review and defense scores, reports and result notifications added so the published round reads complete','{"topics":6,"groups":6,"councils":1}'),
    ('demo-th-audit-002','admin-user','THESIS_DEMO_DATA_ENRICHED','THESIS_ROUND','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','Enrichment: 5 topics, 5 approved groups, 1 council, counter-review and defense scores, reports and result notifications added so the published round reads complete','{"topics":5,"groups":5,"councils":1}'),
    ('demo-th-audit-003','admin-user','THESIS_DEMO_DATA_ENRICHED','THESIS_ROUND','bad8eba0-2d12-4414-8aab-7424174b3b97','Enrichment: 4 topics, 4 approved groups, 1 council, counter-review and defense scores, reports and result notifications added so the published round reads complete','{"topics":4,"groups":4,"councils":1}'),
    ('demo-th-audit-004','admin-user','THESIS_DEMO_DATA_ENRICHED','THESIS_ROUND','22d65ee6-f485-40ae-a66b-528d35007745','Enrichment: 5 groups (1 size-exception, 1 rejected, 1 cancelled with reassignment), council topic assignments, counter-review scores, waiver and progress reports added','{"groups":5,"councilTopics":5,"gvpbScores":3,"reports":2}'),
    ('demo-th-audit-005','admin-user','THESIS_DEMO_DATA_ENRICHED','THESIS_ROUND','c8f1a234-9b7e-412d-8301-1b2c3d4e5f60','Enrichment: archived and rejected topic examples added so the catalog shows every review state','{"topics":2}')
) AS v(id, actor, action, etype, eid, summary, after)
ON CONFLICT (id) DO NOTHING;

-- persist the apply-time state into the durable before-image (same transaction):
--   format:   3
--   cols:     the guarded-column before/applied values (ISO 8601 with offset
--             for timestamps — session-format independent)
--   hashcols: the explicit per-table column lists the row hashes cover
--   rows:     per marker row, per HASHED column, md5 of its canonical render
--             (to_jsonb(... )#>>'{}' under the pinned session settings;
--             encode(...,'hex') for bytea). rollback.sql re-hashes the same
--             columns: a marker row that still exists but DIFFERS on a hashed
--             column blocks rollback, while a marker row the application
--             deleted is simply reported and skipped. Columns a migration
--             later adds are invisible to the hash, and volatile bookkeeping
--             columns (version, updated_at, notification is_read/read_at) are
--             never hashed.
-- demo-th-audit-000 itself is excluded from the hash set: it carries the image.
CREATE TEMP TABLE _row_hashes (tbl text, id text, hashes jsonb) ON COMMIT DROP;

DO $$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT hc.tbl,
           string_agg(format('%L, md5(CASE WHEN t.%I IS NULL THEN chr(1) ELSE %s END)',
                             hc.col, hc.col,
                             CASE WHEN a.atttypid = 'bytea'::regtype
                                  THEN format('encode(t.%I, ''hex'')', hc.col)
                                  ELSE format('to_jsonb(t.%I) #>> ''{}''', hc.col) END),
                             ', ' ORDER BY hc.ord) AS pairs,
           count(*) AS ncol
      FROM _hashcols hc
      JOIN pg_attribute a ON a.attrelid = hc.tbl::regclass AND a.attname = hc.col
                         AND a.attnum > 0 AND NOT a.attisdropped
      JOIN _tables pt ON pt.tbl = hc.tbl
      GROUP BY hc.tbl
  LOOP
    IF r.ncol = 0 THEN
      RAISE EXCEPTION 'demo apply impossible: every hashed column of % was dropped by a schema change', r.tbl;
    END IF;
    EXECUTE format('INSERT INTO _row_hashes SELECT %L, id::text, jsonb_build_object(%s) FROM %s t WHERE %s',
                   r.tbl, r.pairs, r.tbl,
                   (SELECT pred FROM _tables WHERE tbl = r.tbl));
  END LOOP;
END $$;

CREATE TEMP TABLE _image (img jsonb) ON COMMIT DROP;
INSERT INTO _image
SELECT jsonb_build_object(
  'format', 3,
  'cols', (SELECT coalesce(jsonb_agg(jsonb_build_object('tbl', b.tbl, 'id', b.id, 'col', b.col, 'before', b.before_val, 'applied', b.applied_val)), '[]'::jsonb)
             FROM _demo_before b),
  'hashcols', (SELECT jsonb_object_agg(tbl, cols)
                 FROM (SELECT tbl, jsonb_agg(col ORDER BY ord) AS cols FROM _hashcols GROUP BY tbl) s),
  'rows', (SELECT jsonb_object_agg(tbl, ids)
             FROM (SELECT tbl, jsonb_object_agg(id, hashes) AS ids FROM _row_hashes GROUP BY tbl) s));

-- written on every fresh apply (the image describes THIS apply's marker rows
-- and guarded values; a stale image must never survive a new apply)
UPDATE campuscore_audit."AdminAudit"
SET "beforeState" = (SELECT img::text FROM _image)
WHERE id = 'demo-th-audit-000';

-- checksum-protected redundant copy in the table comment: it survives a single
-- audit-row deletion (and any AdminAudit pruning), and rollback verifies the
-- md5 before trusting it. The comment is cleared by rollback.
SELECT format('COMMENT ON TABLE thesis.thesis_topic IS %L',
              'thesis-demo-data v3 md5:' || md5(img::text) || E'\n' || img::text)
FROM _image \gexec

-- post-write assertion: abort the whole transaction if the marker set is incomplete
-- or any report blob disagrees with its recorded size/sha256 metadata.
DO $$
DECLARE n int;
BEGIN
  SELECT count(*) INTO n FROM thesis.thesis_topic_score WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
  IF n <> 62 THEN RAISE EXCEPTION 'demo apply incomplete: expected 62 marker scores, found %', n; END IF;
  SELECT count(*) INTO n FROM thesis.thesis_group_report WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
  IF n <> 17 THEN RAISE EXCEPTION 'demo apply incomplete: expected 17 marker reports, found %', n; END IF;
  SELECT count(*) INTO n FROM notifications.notification WHERE id LIKE 'demo-th-notif-%';
  IF n <> 107 THEN RAISE EXCEPTION 'demo apply incomplete: expected 107 marker notifications, found %', n; END IF;
  SELECT count(*) INTO n FROM campuscore_audit."AdminAudit" WHERE id LIKE 'demo-th-audit-%';
  IF n <> 6 THEN RAISE EXCEPTION 'demo apply incomplete: expected 6 marker audit rows, found %', n; END IF;
  -- durable before-image must exist in both copies before commit
  SELECT count(*) INTO n FROM campuscore_audit."AdminAudit"
   WHERE id = 'demo-th-audit-000' AND "beforeState" ~ '^\s*\{';
  IF n <> 1 THEN RAISE EXCEPTION 'demo apply incomplete: audit-row before-image missing'; END IF;
  IF obj_description('thesis.thesis_topic'::regclass) NOT LIKE 'thesis-demo-data v3 md5:%' THEN
    RAISE EXCEPTION 'demo apply incomplete: checksum-protected comment copy missing';
  END IF;
  SELECT count(*) INTO n FROM thesis.thesis_group_report
   WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
     AND (file_size IS DISTINCT FROM octet_length(file_data)
          OR file_sha256 IS DISTINCT FROM encode(sha256(file_data), 'hex'));
  IF n <> 0 THEN RAISE EXCEPTION 'demo apply corrupt: % marker reports have file_size/file_sha256 not matching file_data', n; END IF;
END $$;

-- run summary (visible in both modes; rolled back under dry-run)
SELECT 'thesis_topic' AS section, count(*) AS rows FROM thesis.thesis_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
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
ORDER BY 1;

\endif

-- psql client-side branch: dry-run rolls the whole transaction back so the
-- printed counts describe exactly what WOULD be written; real run commits.
\if :dry_run
ROLLBACK;
\else
COMMIT;
\endif