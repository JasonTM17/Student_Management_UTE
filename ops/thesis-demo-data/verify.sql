-- Thesis demo-data integrity verification (read-only; safe to run any time).
-- Each check reports expected vs actual plus a PASS/FAIL flag. The runner fails
-- whenever any row reports FAIL. Nothing here writes to the database — a few
-- checks use session-local TEMP tables that vanish when psql exits.
--
--   psql "$DATABASE_URL" -P pager=off -f verify.sql

\set QUIET on
\pset footer off

SET lock_timeout = '5s';
SET statement_timeout = '120s';
SET TimeZone = 'UTC';
SET DateStyle = 'ISO, MDY';
SET bytea_output = 'hex';
SET extra_float_digits = 0;
SET IntervalStyle = 'iso_8601';
SET standard_conforming_strings = 'on';
-- the demo text is UTF-8: pin the client encoding for this session too, so a
-- non-UTF-8 client cannot make the byte-level checks below read mojibake.
SET client_encoding = 'UTF8';

-- fail closed if either end of the session is not UTF-8: under a different
-- encoding the mojibake check cannot tell 'correct' from 'already garbled'.
DO $$
BEGIN
  IF current_setting('client_encoding') <> 'UTF8'
     OR current_setting('server_encoding') <> 'UTF8' THEN
    RAISE EXCEPTION 'verify aborted: client_encoding=% server_encoding=% — UTF8 is required end-to-end',
      current_setting('client_encoding'), current_setting('server_encoding')
      USING ERRCODE = '22000', CONSTRAINT = 'thesis_demo_verify_encoding';
  END IF;
END $$;

WITH m(p) AS (VALUES ('ed1a0000-0000-4000-8000-%'))
SELECT 'marker_count.' || sec.name AS check_name,
       sec.expected::text AS expected,
       cnt.actual::text AS actual,
       CASE WHEN cnt.actual = sec.expected THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM (VALUES
      ('thesis_topic',            17),
      ('thesis_topic_supervisor', 18),
      ('thesis_group',            20),
      ('thesis_group_member',     64),
      ('thesis_council',           4),
      ('thesis_council_member',   16),
      ('thesis_council_topic',    16),
      ('thesis_topic_score',      62),
      ('thesis_group_report',     17)) sec(name, expected)
JOIN LATERAL (
  SELECT CASE sec.name
           WHEN 'thesis_topic'            THEN (SELECT count(*) FROM thesis.thesis_topic            WHERE id::text LIKE (SELECT p FROM m))
           WHEN 'thesis_topic_supervisor' THEN (SELECT count(*) FROM thesis.thesis_topic_supervisor WHERE id::text LIKE (SELECT p FROM m))
           WHEN 'thesis_group'            THEN (SELECT count(*) FROM thesis.thesis_group            WHERE id::text LIKE (SELECT p FROM m))
           WHEN 'thesis_group_member'     THEN (SELECT count(*) FROM thesis.thesis_group_member     WHERE id::text LIKE (SELECT p FROM m))
           WHEN 'thesis_council'          THEN (SELECT count(*) FROM thesis.thesis_council          WHERE id::text LIKE (SELECT p FROM m))
           WHEN 'thesis_council_member'   THEN (SELECT count(*) FROM thesis.thesis_council_member   WHERE id::text LIKE (SELECT p FROM m))
           WHEN 'thesis_council_topic'    THEN (SELECT count(*) FROM thesis.thesis_council_topic    WHERE id::text LIKE (SELECT p FROM m))
           WHEN 'thesis_topic_score'      THEN (SELECT count(*) FROM thesis.thesis_topic_score      WHERE id::text LIKE (SELECT p FROM m))
           WHEN 'thesis_group_report'     THEN (SELECT count(*) FROM thesis.thesis_group_report     WHERE id::text LIKE (SELECT p FROM m))
         END AS actual
) cnt ON TRUE
UNION ALL
SELECT 'marker_count.notification', '107',
       (SELECT count(*)::text FROM notifications.notification WHERE id LIKE 'demo-th-%'),
       CASE WHEN (SELECT count(*) FROM notifications.notification WHERE id LIKE 'demo-th-%') = 107 THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT 'marker_count.admin_audit', '6',
       (SELECT count(*)::text FROM campuscore_audit."AdminAudit" WHERE id LIKE 'demo-th-%'),
       CASE WHEN (SELECT count(*) FROM campuscore_audit."AdminAudit" WHERE id LIKE 'demo-th-%') = 6 THEN 'PASS' ELSE 'FAIL' END
ORDER BY 1;

-- referential integrity: every marker row's parents must exist
SELECT 'orphan.members_missing_group' AS check_name, '0' AS expected, count(*)::text AS actual,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM thesis.thesis_group_member m
WHERE m.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group g WHERE g.id = m.group_id)
UNION ALL
SELECT 'orphan.members_missing_student', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_group_member m
WHERE m.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  AND NOT EXISTS (SELECT 1 FROM academic."Student" s WHERE s."id" = m.student_id)
UNION ALL
SELECT 'orphan.supervisors_missing_lecturer', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_topic_supervisor s
WHERE s.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  AND NOT EXISTS (SELECT 1 FROM academic."Lecturer" l WHERE l."id" = s.lecturer_id)
UNION ALL
SELECT 'orphan.scores_missing_lecturer', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_topic_score s
WHERE s.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  AND NOT EXISTS (SELECT 1 FROM academic."Lecturer" l WHERE l."id" = s.lecturer_id)
UNION ALL
SELECT 'orphan.reports_missing_group', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_group_report r
WHERE r.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group g WHERE g.id = r.group_id)
UNION ALL
SELECT 'orphan.council_members_missing_lecturer', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_council_member cm
WHERE cm.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  AND NOT EXISTS (SELECT 1 FROM academic."Lecturer" l WHERE l."id" = cm.lecturer_id)
UNION ALL
SELECT 'orphan.notifications_missing_user', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM notifications.notification n
WHERE n.id LIKE 'demo-th-%'
  AND NOT EXISTS (SELECT 1 FROM campuscore_auth."User" u WHERE u."id" = n.user_id);

-- synthetic-only references: every account id touched by marker rows must be a
-- seeded demo profile/user (no real account may ever appear)
SELECT 'synthetic.non_demo_student_refs' AS check_name, '0' AS expected, count(*)::text AS actual,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM thesis.thesis_group_member m
WHERE m.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND m.student_id !~ '^student-profile-[0-9]+$'
UNION ALL
SELECT 'synthetic.non_demo_lecturer_refs', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM (
  SELECT lecturer_id FROM thesis.thesis_topic_supervisor WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
  UNION ALL SELECT lecturer_id FROM thesis.thesis_council_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
  UNION ALL SELECT lecturer_id FROM thesis.thesis_topic_score WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
) lec
WHERE lec.lecturer_id !~ '^lecturer-profile(-[0-9a-z]+)*$'
UNION ALL
SELECT 'synthetic.non_demo_user_refs', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM (
  SELECT t.created_by AS uid FROM thesis.thesis_topic t WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  UNION ALL SELECT g.approved_by FROM thesis.thesis_group g WHERE g.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND g.approved_by IS NOT NULL
  UNION ALL SELECT g.size_exception_by FROM thesis.thesis_group g WHERE g.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND g.size_exception_by IS NOT NULL
  UNION ALL SELECT t.final_score_finalized_by FROM thesis.thesis_topic t WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND t.final_score_finalized_by IS NOT NULL
  UNION ALL SELECT t.result_by FROM thesis.thesis_topic t WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND t.result_by IS NOT NULL
  UNION ALL SELECT c.created_by FROM thesis.thesis_council c WHERE c.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  UNION ALL SELECT ct.assigned_by FROM thesis.thesis_council_topic ct WHERE ct.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  UNION ALL SELECT n.user_id FROM notifications.notification n WHERE n.id LIKE 'demo-th-%'
  UNION ALL SELECT a."actorId" FROM campuscore_audit."AdminAudit" a WHERE a.id LIKE 'demo-th-%'
) refs
WHERE refs.uid IS NULL
   OR NOT EXISTS (SELECT 1 FROM campuscore_auth."User" u WHERE u."id" = refs.uid)
UNION ALL
SELECT 'synthetic.known_real_accounts_absent', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM (
  SELECT m.student_id AS ref FROM thesis.thesis_group_member m WHERE m.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  UNION ALL SELECT s.lecturer_id FROM thesis.thesis_topic_supervisor s WHERE s.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  UNION ALL SELECT cm.lecturer_id FROM thesis.thesis_council_member cm WHERE cm.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  UNION ALL SELECT sc.lecturer_id FROM thesis.thesis_topic_score sc WHERE sc.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  UNION ALL SELECT n.user_id FROM notifications.notification n WHERE n.id LIKE 'demo-th-%'
) r
WHERE r.ref IN ('16a1b2be-6556-4d47-83d9-e9a07daf109d','39b5f10c-ebba-4fbf-8beb-595ebf5f4a70',
                'b3b4b4cd-3f5d-44a8-9b68-5ad06916423c','ec54ede5-1660-4161-98e3-2ad733c17e74');

-- group invariants
SELECT 'group.one_leader_each' AS check_name, '0' AS expected, count(*)::text AS actual,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM (
  SELECT g.id FROM thesis.thesis_group g
  WHERE g.id::text LIKE 'ed1a0000-0000-4000-8000-%'
    AND (SELECT count(*) FROM thesis.thesis_group_member m
         WHERE m.group_id = g.id AND m.is_leader) <> 1
) bad
UNION ALL
SELECT 'group.leader_matches_group_leader', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_group g
WHERE g.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member m
                  WHERE m.group_id = g.id AND m.student_id = g.leader_student_id AND m.is_leader)
UNION ALL
SELECT 'group.approved_size_rule', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_group g
WHERE g.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  AND g.approval_status = 'APPROVED' AND g.status <> 'CANCELLED'
  AND g.size_exception_reason IS NULL
  AND (SELECT count(*) FROM thesis.thesis_group_member m WHERE m.group_id = g.id) NOT BETWEEN 3 AND 5
UNION ALL
SELECT 'group.member_order_unique', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM (
  SELECT m.group_id, m.member_order FROM thesis.thesis_group_member m
  WHERE m.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  GROUP BY m.group_id, m.member_order HAVING count(*) > 1
) dup
UNION ALL
SELECT 'group.cancelled_releases_roster', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_group_member m
JOIN thesis.thesis_group g ON g.id = m.group_id
WHERE g.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND g.status = 'CANCELLED' AND m.active_participation
UNION ALL
SELECT 'group.one_active_per_student_round', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM (
  SELECT m.round_id, m.student_id FROM thesis.thesis_group_member m
  JOIN thesis.thesis_group g ON g.id = m.group_id
  WHERE m.active_participation AND g.status <> 'CANCELLED' AND g.approval_status <> 'REJECTED'
  GROUP BY m.round_id, m.student_id HAVING count(*) > 1
) dup;

-- topic capacity: live groups must respect max_groups
SELECT 'topic.capacity_respected' AS check_name, '0' AS expected, count(*)::text AS actual,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM (
  SELECT t.id FROM thesis.thesis_topic t
  WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%'
    AND (SELECT count(*) FROM thesis.thesis_group g
         WHERE g.topic_id = t.id AND g.status <> 'CANCELLED' AND g.approval_status <> 'REJECTED') > t.max_groups
) over;

-- score domain + outcome semantics
SELECT 'score.in_range' AS check_name, '0' AS expected, count(*)::text AS actual,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM thesis.thesis_topic_score s
WHERE s.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND (s.score < 0 OR s.score > 10)
UNION ALL
SELECT 'score.component_council_consistency', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_topic_score s
WHERE s.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  AND ((s.component = 'DEFENSE' AND s.council_id IS NULL) OR (s.component = 'GVPB' AND s.council_id IS NOT NULL))
UNION ALL
SELECT 'score.defense_by_council_member', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_topic_score s
WHERE s.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND s.component = 'DEFENSE'
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_council_member cm
                  WHERE cm.council_id = s.council_id AND cm.lecturer_id = s.lecturer_id)
UNION ALL
SELECT 'score.graded_final_matches_avg', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_topic t
WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND t.result_status = 'GRADED'
  AND t.final_score IS DISTINCT FROM (
    SELECT round(avg(s.score), 2) FROM thesis.thesis_topic_score s
    WHERE s.topic_id = t.id AND s.component = 'DEFENSE'
      AND NOT EXISTS (SELECT 1 FROM thesis.thesis_topic_supervisor sup
                      WHERE sup.topic_id = t.id AND sup.lecturer_id = s.lecturer_id))
UNION ALL
SELECT 'score.review_failed_below_pass', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_topic t
WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND t.result_status = 'REVIEW_FAILED'
  AND COALESCE((SELECT max(s.score) FROM thesis.thesis_topic_score s
                WHERE s.topic_id = t.id AND s.component = 'GVPB'), 10) >= 5.0
UNION ALL
SELECT 'score.outcome_metadata_complete', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_topic t
WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND t.result_status IN ('REVIEW_FAILED','WITHDRAWN')
  AND (t.result_reason IS NULL OR t.result_by IS NULL OR t.result_at IS NULL)
UNION ALL
SELECT 'score.graded_has_finalize_meta', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_topic t
WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND t.result_status = 'GRADED'
  AND (t.final_score IS NULL OR t.final_score_finalized_by IS NULL OR t.final_score_finalized_at IS NULL);

-- V141 publication guard on the three enriched published rounds
SELECT 'publish.completeness_guard' AS check_name, '0' AS expected, count(*)::text AS actual,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM thesis.thesis_registration_round r
JOIN thesis.thesis_group g ON g.round_id = r.id AND g.approval_status = 'APPROVED' AND g.status <> 'CANCELLED'
JOIN thesis.thesis_topic t ON t.id = g.topic_id
WHERE r.status = 'RESULTS_PUBLISHED'
  AND r.id IN ('b35e9ff1-1eb8-4e73-812e-04e1a66041f1','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','bad8eba0-2d12-4414-8aab-7424174b3b97')
  AND t.result_status IS DISTINCT FROM 'WITHDRAWN'
  AND t.final_score IS NULL
  AND NOT (t.result_status = 'REVIEW_FAILED' AND r.gvpb_deadline IS NOT NULL AND r.gvpb_deadline < now())
UNION ALL
SELECT 'publish.results_published_at_set', '3', count(*)::text,
       CASE WHEN count(*) = 3 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_registration_round r
WHERE r.id IN ('b35e9ff1-1eb8-4e73-812e-04e1a66041f1','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','bad8eba0-2d12-4414-8aab-7424174b3b97')
  AND r.results_published_at IS NOT NULL;

-- report blob integrity (database storage provider)
SELECT 'report.blob_integrity' AS check_name, '0' AS expected, count(*)::text AS actual,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM thesis.thesis_group_report r
WHERE r.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  AND (r.storage_provider <> 'database'
       OR r.file_data IS NULL
       OR r.file_size IS DISTINCT FROM octet_length(r.file_data)
       OR r.file_sha256 IS DISTINCT FROM encode(sha256(r.file_data), 'hex'))
UNION ALL
SELECT 'report.one_per_group', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM (
  SELECT r.group_id FROM thesis.thesis_group_report r
  WHERE r.id::text LIKE 'ed1a0000-0000-4000-8000-%' OR r.group_id IN
    (SELECT id FROM thesis.thesis_group WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%')
  GROUP BY r.group_id HAVING count(*) > 1
) dup;

-- council seat rules on marker councils
SELECT 'council.seat_roles' AS check_name, '0' AS expected, count(*)::text AS actual,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM (
  SELECT cm.council_id, cm.member_role FROM thesis.thesis_council_member cm
  WHERE cm.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND cm.member_role IN ('CHAIR','SECRETARY')
  GROUP BY cm.council_id, cm.member_role HAVING count(*) > 1
) dup
UNION ALL
SELECT 'council.topic_single_assignment', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM (
  SELECT ct.topic_id FROM thesis.thesis_council_topic ct
  WHERE ct.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  GROUP BY ct.topic_id HAVING count(*) > 1
) dup;

-- application business rules (ThesisCouncilService / ThesisMutationService):
-- every graded topic is finalized by its assigned council's CHAIR user,
-- no counter-reviewer sits on that topic's own council or supervises it,
-- no supervisor scores their own topic, council seats are distinct lecturers,
-- every eligible council member scored each graded topic on that council.
SELECT 'rule.finalizer_is_chair' AS check_name, '0' AS expected, count(*)::text AS actual,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM thesis.thesis_topic t
JOIN thesis.thesis_council_topic ct ON ct.topic_id = t.id
JOIN thesis.thesis_council_member cm ON cm.council_id = ct.council_id AND cm.member_role = 'CHAIR'
JOIN academic."Lecturer" l ON l."id" = cm.lecturer_id
WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND t.result_status = 'GRADED'
  AND t.final_score_finalized_by IS DISTINCT FROM l."userId"
UNION ALL
SELECT 'rule.reviewer_not_on_own_council', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_topic t
WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND t.gvpb_lecturer_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM thesis.thesis_council_topic ct
              JOIN thesis.thesis_council_member cm ON cm.council_id = ct.council_id
              WHERE ct.topic_id = t.id AND cm.lecturer_id = t.gvpb_lecturer_id)
UNION ALL
SELECT 'rule.reviewer_not_supervisor', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_topic t
WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND t.gvpb_lecturer_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM thesis.thesis_topic_supervisor s
              WHERE s.topic_id = t.id AND s.lecturer_id = t.gvpb_lecturer_id)
UNION ALL
SELECT 'rule.supervisor_not_scoring', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_topic_score s
WHERE s.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  AND EXISTS (SELECT 1 FROM thesis.thesis_topic_supervisor sup
              WHERE sup.topic_id = s.topic_id AND sup.lecturer_id = s.lecturer_id)
UNION ALL
SELECT 'rule.council_seats_distinct', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM (
  SELECT cm.council_id, cm.lecturer_id FROM thesis.thesis_council_member cm
  WHERE cm.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  GROUP BY cm.council_id, cm.lecturer_id HAVING count(*) > 1
) dup
UNION ALL
SELECT 'rule.eligible_graders_complete', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_topic t
JOIN thesis.thesis_council_topic ct ON ct.topic_id = t.id
JOIN thesis.thesis_council_member cm ON cm.council_id = ct.council_id
WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND t.result_status = 'GRADED'
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_topic_supervisor sup
                  WHERE sup.topic_id = t.id AND sup.lecturer_id = cm.lecturer_id)
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_topic_score s
                  WHERE s.topic_id = t.id AND s.component = 'DEFENSE' AND s.lecturer_id = cm.lecturer_id)
UNION ALL
SELECT 'report.no_demo_wording', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM thesis.thesis_group_report r
WHERE r.id::text LIKE 'ed1a0000-0000-4000-8000-%'
  AND (r.title ILIKE '%demo%' OR r.file_name ILIKE '%demo%' OR r.note ILIKE '%demo%')
UNION ALL
SELECT 'audit.not_in_future', '0', count(*)::text,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM campuscore_audit."AdminAudit" a
WHERE a.id LIKE 'demo-th-%' AND a."createdAt" > now() + interval '1 minute';

-- before-image durability: the v3 image must be present and well-formed in the
-- audit row, and the checksum-protected comment copy must agree with it
SELECT 'image.before_image_valid' AS check_name, '1' AS expected, count(*)::text AS actual,
       CASE WHEN count(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM campuscore_audit."AdminAudit" a
WHERE a.id = 'demo-th-audit-000'
  AND a."beforeState" ~ '^\s*\{'
  AND (a."beforeState"::jsonb ->> 'format') = '3'
  AND jsonb_typeof(a."beforeState"::jsonb -> 'cols') = 'array'
  AND jsonb_typeof(a."beforeState"::jsonb -> 'rows') = 'object'
  AND jsonb_typeof(a."beforeState"::jsonb -> 'hashcols') = 'object';

WITH c AS (
  SELECT obj_description('thesis.thesis_topic'::regclass) AS d,
         (SELECT a."beforeState" FROM campuscore_audit."AdminAudit" a WHERE a.id = 'demo-th-audit-000') AS bs
), p AS (
  SELECT CASE WHEN d LIKE 'thesis-demo-data v3 md5:%' AND position(E'\n' in d) > 0
              THEN substr(d, position(E'\n' in d) + 1) END AS payload,
         substring(d from 'md5:([0-9a-f]{32})') AS decl, bs
  FROM c
), q AS (
  SELECT CASE WHEN payload IS NOT NULL AND payload ~ '^\s*\{' AND md5(payload) = decl
              THEN payload::jsonb END AS pimg,
         CASE WHEN bs ~ '^\s*\{' THEN bs::jsonb END AS bimg
  FROM p
)
SELECT 'image.comment_copy_consistent' AS check_name, '0' AS expected,
       (CASE WHEN pimg IS NOT NULL AND bimg IS NOT NULL AND pimg = bimg THEN 0 ELSE 1 END)::text AS actual,
       CASE WHEN pimg IS NOT NULL AND bimg IS NOT NULL AND pimg = bimg THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM q;

-- image.hashcols must name every marker table and never a volatile column
SELECT 'image.hashcols_no_volatile' AS check_name, '0' AS expected, count(*)::text AS actual,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM campuscore_audit."AdminAudit" a,
     jsonb_each(a."beforeState"::jsonb -> 'hashcols') rt,
     jsonb_array_elements_text(rt.value) c
WHERE a.id = 'demo-th-audit-000' AND a."beforeState" ~ '^\s*\{'
  AND c IN ('version','updated_at','is_read','read_at','createdAt')
UNION ALL
SELECT 'image.hashcols_complete', '11', count(*)::text,
       CASE WHEN count(*) = 11 THEN 'PASS' ELSE 'FAIL' END
FROM (
  SELECT t.tbl FROM (VALUES
    ('thesis.thesis_topic'),('thesis.thesis_topic_supervisor'),('thesis.thesis_group'),
    ('thesis.thesis_group_member'),('thesis.thesis_council'),('thesis.thesis_council_member'),
    ('thesis.thesis_council_topic'),('thesis.thesis_topic_score'),('thesis.thesis_group_report'),
    ('notifications.notification'),('campuscore_audit."AdminAudit"')) t(tbl)
  WHERE EXISTS (SELECT 1 FROM campuscore_audit."AdminAudit" a
                WHERE a.id = 'demo-th-audit-000' AND a."beforeState" ~ '^\s*\{'
                  AND a."beforeState"::jsonb -> 'hashcols' ? t.tbl)
) present;

-- every marker row must still match the image on its hashed columns; rows the
-- application deleted are reported separately from rows it edited
CREATE TEMP TABLE _v_hashcols (tbl text, col text, ord int);
INSERT INTO _v_hashcols
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

CREATE TEMP TABLE _v_tables (tbl text, pred text);
INSERT INTO _v_tables VALUES
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

CREATE TEMP TABLE _v_actual (tbl text, id text, hashes jsonb);
CREATE TEMP TABLE _v_counts (name text, n bigint);

DO $$
DECLARE
  r record;
  v_img jsonb;
BEGIN
  SELECT "beforeState"::jsonb INTO v_img FROM campuscore_audit."AdminAudit"
   WHERE id = 'demo-th-audit-000' AND "beforeState" ~ '^\s*\{';
  IF v_img IS NULL OR jsonb_typeof(v_img->'rows') IS DISTINCT FROM 'object' THEN
    INSERT INTO _v_counts VALUES ('differs', -1), ('missing', -1), ('extra', -1);
    RETURN;
  END IF;
  FOR r IN
    SELECT hc.tbl,
           string_agg(format('%L, md5(CASE WHEN t.%I IS NULL THEN chr(1) ELSE %s END)',
                             hc.col, hc.col,
                             CASE WHEN a.atttypid = 'bytea'::regtype
                                  THEN format('encode(t.%I, ''hex'')', hc.col)
                                  ELSE format('to_jsonb(t.%I) #>> ''{}''', hc.col) END),
                             ', ' ORDER BY hc.ord) AS pairs
      FROM _v_hashcols hc
      JOIN pg_attribute a ON a.attrelid = hc.tbl::regclass AND a.attname = hc.col
                         AND a.attnum > 0 AND NOT a.attisdropped
      GROUP BY hc.tbl
  LOOP
    EXECUTE format('INSERT INTO _v_actual SELECT %L, id::text, jsonb_build_object(%s) FROM %s t WHERE %s',
                   r.tbl, r.pairs, r.tbl,
                   (SELECT pred FROM _v_tables WHERE tbl = r.tbl));
  END LOOP;
  INSERT INTO _v_counts
  SELECT 'differs', coalesce(sum(d),0) FROM (
    SELECT (SELECT count(*) FROM jsonb_each(v_img->'rows'->t.tbl) e
             JOIN _v_actual l ON l.tbl = t.tbl AND l.id = e.key
             WHERE NOT EXISTS (SELECT 1 FROM jsonb_object_keys(e.value) k WHERE l.hashes ? k)
                OR EXISTS (SELECT 1 FROM jsonb_each_text(e.value) v
                            WHERE l.hashes ? v.key AND l.hashes->>v.key IS DISTINCT FROM v.value)) AS d
      FROM _v_tables t) x
  UNION ALL
  SELECT 'missing', coalesce(sum(m),0) FROM (
    SELECT (SELECT count(*) FROM jsonb_each(v_img->'rows'->t.tbl) e
             WHERE NOT EXISTS (SELECT 1 FROM _v_actual l WHERE l.tbl = t.tbl AND l.id = e.key)) AS m
      FROM _v_tables t) y
  UNION ALL
  SELECT 'extra', coalesce(sum(x2),0) FROM (
    SELECT (SELECT count(*) FROM _v_actual l
             WHERE l.tbl = t.tbl AND NOT (v_img->'rows'->t.tbl ? l.id)) AS x2
      FROM _v_tables t) z;
END $$;

SELECT 'image.row_hash_differs' AS check_name, '0' AS expected, n::text AS actual,
       CASE WHEN n = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict FROM _v_counts WHERE name = 'differs'
UNION ALL
SELECT 'image.rows_missing_live', '0', n::text,
       CASE WHEN n = 0 THEN 'PASS' ELSE 'FAIL' END FROM _v_counts WHERE name = 'missing'
UNION ALL
SELECT 'image.rows_unexpected_live', '0', n::text,
       CASE WHEN n = 0 THEN 'PASS' ELSE 'FAIL' END FROM _v_counts WHERE name = 'extra';

-- every guarded column must still carry the applied value
WITH exp AS (
  SELECT (e ->> 'tbl') AS tbl, (e ->> 'id') AS rid, (e ->> 'col') AS col, (e ->> 'applied') AS app
  FROM campuscore_audit."AdminAudit" a,
       jsonb_array_elements(a."beforeState"::jsonb -> 'cols') e
  WHERE a.id = 'demo-th-audit-000' AND a."beforeState" ~ '^\s*\{'
), act AS (
  SELECT 'thesis_registration_round' AS tbl, r.id::text AS rid, x.col, x.val
  FROM thesis.thesis_registration_round r
  CROSS JOIN LATERAL (VALUES
    ('version', to_jsonb(r.version) #>> '{}'),
    ('updated_at', to_jsonb(r.updated_at) #>> '{}'),
    ('results_published_at', to_jsonb(r.results_published_at) #>> '{}')) x(col, val)
  WHERE r.id IN ('b35e9ff1-1eb8-4e73-812e-04e1a66041f1','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','bad8eba0-2d12-4414-8aab-7424174b3b97')
  UNION ALL
  SELECT 'thesis_topic', t.id::text, x.col, x.val
  FROM thesis.thesis_topic t
  CROSS JOIN LATERAL (VALUES
    ('version', to_jsonb(t.version) #>> '{}'),
    ('updated_at', to_jsonb(t.updated_at) #>> '{}'),
    ('gvpb_lecturer_id', to_jsonb(t.gvpb_lecturer_id) #>> '{}'),
    ('gvpb_waived_reason', to_jsonb(t.gvpb_waived_reason) #>> '{}'),
    ('gvpb_waived_by', to_jsonb(t.gvpb_waived_by) #>> '{}'),
    ('gvpb_waived_at', to_jsonb(t.gvpb_waived_at) #>> '{}')) x(col, val)
  WHERE t.id IN ('018f4a7c-3b12-4d90-a5e1-7c82b9045001','6f64670b-cd6b-4e1b-ae9d-0b497f2472a8',
                 'f580eadf-fffb-4db2-a7d8-423e83934dc2','67c74df5-5c7f-4e74-aa02-d51af7207fe9')
)
SELECT 'image.guarded_cols_at_applied' AS check_name, '0' AS expected, count(*)::text AS actual,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM exp
JOIN act ON act.tbl = exp.tbl AND act.rid = exp.rid AND act.col = exp.col
WHERE act.val IS DISTINCT FROM exp.app;

-- encoding: three seeded Vietnamese strings must still be valid UTF-8 bytes
-- exactly as apply.sql wrote them. Any LATIN1/other-encoding write or a
-- double-encoded value fails byte-for-byte, no collation involved.
SELECT 'encoding.vietnamese_utf8_bytes' AS check_name, '3' AS expected,
       count(*)::text AS actual,
       CASE WHEN count(*) = 3 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM (VALUES
  ((SELECT encode(convert_to(n.title, 'UTF8'), 'hex')
      FROM notifications.notification n WHERE n.id = 'demo-th-notif-0001'),
   '4be1babf74207175e1baa3206b68c3b361206c75e1baad6e20c491c3a32063c3b46e672062e1bb91'),
  ((SELECT encode(convert_to(t.title, 'UTF8'), 'hex')
      FROM thesis.thesis_topic t WHERE t.id = 'ed1a0000-0000-4000-8000-000100000001'),
   '48e1bb87207468e1bb916e67206b686169207068c3a12064e1bbaf206c69e1bb8775206e68e1baad74206bc3bd2068e1bb8d632074e1baad70207068e1bba5632076e1bba52063e1baa36e682062c3a16f2068e1bb8d632076e1bba52073e1bb9b6d'),
  ((SELECT encode(convert_to(t.gvpb_waived_reason, 'UTF8'), 'hex')
      FROM thesis.thesis_topic t WHERE t.id = '67c74df5-5c7f-4e74-aa02-d51af7207fe9'),
   '4d69e1bb856e207068e1baa36e206269e1bb876e207468656f20517579e1babf7420c491e1bb8b6e68203231342f51c4902d4b434e54543a20c491e1bb812074c3a06920746875e1bb9963206e68c3b36d2063687579c3aa6e206e67c3a06e682068e1bab9702c20c491c3a320717561206861692076c3b26e672078c3a97420647579e1bb87742063e1bba7612042e1bb99206dc3b46e')
) p(actual_hex, expected_hex)
WHERE p.actual_hex IS NOT DISTINCT FROM p.expected_hex;
