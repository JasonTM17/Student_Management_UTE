-- Thesis demo-data integrity verification (SELECT-only; safe to run any time).
-- Each check reports expected vs actual plus a PASS/FAIL flag. The runner fails
-- whenever any row reports FAIL. Nothing here writes to the database.
--
--   psql "$DATABASE_URL" -P pager=off -f verify.sql

\set QUIET on
\pset footer off

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
      ('thesis_topic_score',      48),
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
SELECT 'marker_count.admin_audit', '5',
       (SELECT count(*)::text FROM campuscore_audit."AdminAudit" WHERE id LIKE 'demo-th-%'),
       CASE WHEN (SELECT count(*) FROM campuscore_audit."AdminAudit" WHERE id LIKE 'demo-th-%') = 5 THEN 'PASS' ELSE 'FAIL' END
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
   OR NOT EXISTS (SELECT 1 FROM campuscore_auth."User" u
                  WHERE u."id" = refs.uid AND u.email LIKE '%@campuscore.%')
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
       OR r.file_size IS DISTINCT FROM length(r.file_data)
       OR r.file_sha256 IS DISTINCT FROM encode(thesis.digest(r.file_data, 'sha256'), 'hex'))
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
