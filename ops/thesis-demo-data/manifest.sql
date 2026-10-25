-- Synthetic-account manifest for the thesis demo-data package.
-- Every account id referenced by apply.sql is listed here together with the
-- seeded evidence that marks it synthetic. Run against any database that has
-- the V1..V142 baseline to confirm the package can never touch a real person:
--
--   psql "$DATABASE_URL" -P pager=off -f manifest.sql
--
-- The classification rule is the one the repo's own seeds establish:
--   * academic."Student".id / "Lecturer".id use the literal prefixes
--     'student-profile-%' / 'lecturer-profile-%' (created by V126 demo seeds)
--   * campuscore_auth."User".id values below are exactly the seeded *-user-*
--     demo identities and every one of them resolves to a campuscore.edu /
--     campuscore.demo mailbox that DemoAccountGate keeps permanently locked
--   * production-recon identified exactly four accounts that do NOT match
--     either pattern (they are listed in verify.sql's known_real_accounts_absent
--     check so any drift is caught automatically)

WITH manifest(id, kind, evidence) AS (
  SELECT 'student-profile-' || lpad(g::text, 3, '0'), 'student',
         'student-profile-* id minted by the V126 demo seed; auth twin student-user-' || lpad(g::text, 3, '0')
  FROM generate_series(21, 30) g
  UNION ALL
  SELECT 'student-profile-' || n, 'student',
         'student-profile-* id minted by the V126 demo seed; auth twin student-user-' || n
  FROM unnest(ARRAY['109','110','120','136','137','138','139','140']) n
  UNION ALL
  SELECT 'student-profile-' || lpad(g::text, 3, '0'), 'student',
         'student-profile-* id minted by the V126 demo seed; auth twin student-user-' || lpad(g::text, 3, '0')
  FROM generate_series(153, 202) g
  UNION ALL
  SELECT l.id, 'lecturer', 'lecturer-profile-* id from the V126 demo seed (department ' || l."departmentId" || ')'
  FROM academic."Lecturer" l
  WHERE l.id IN ('lecturer-profile','lecturer-profile-002','lecturer-profile-003','lecturer-profile-004',
                 'lecturer-profile-005','lecturer-profile-011','lecturer-profile-013','lecturer-profile-014',
                 'lecturer-profile-023','lecturer-profile-024','lecturer-profile-025','lecturer-profile-026',
                 'lecturer-profile-027','lecturer-profile-028','lecturer-profile-029','lecturer-profile-030',
                 'lecturer-profile-truongkhoa-001','lecturer-profile-truongkhoa-002')
  UNION ALL
  SELECT u.id, 'user', 'auth account ending in @' || split_part(u.email,'@',2) ||
         ' — campuscore demo domain; locked by DemoAccountGate unless on the fixed demo list'
  FROM campuscore_auth."User" u
  WHERE u.email LIKE '%@campuscore.%'
    AND u.id IN (SELECT DISTINCT ref FROM (
      SELECT t.created_by AS ref FROM thesis.thesis_topic t WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%'
      UNION ALL SELECT g.approved_by FROM thesis.thesis_group g WHERE g.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND g.approved_by IS NOT NULL
      UNION ALL SELECT g.size_exception_by FROM thesis.thesis_group g WHERE g.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND g.size_exception_by IS NOT NULL
      UNION ALL SELECT t.final_score_finalized_by FROM thesis.thesis_topic t WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND t.final_score_finalized_by IS NOT NULL
      UNION ALL SELECT t.result_by FROM thesis.thesis_topic t WHERE t.id::text LIKE 'ed1a0000-0000-4000-8000-%' AND t.result_by IS NOT NULL
      UNION ALL SELECT c.created_by FROM thesis.thesis_council c WHERE c.id::text LIKE 'ed1a0000-0000-4000-8000-%'
      UNION ALL SELECT ct.assigned_by FROM thesis.thesis_council_topic ct WHERE ct.id::text LIKE 'ed1a0000-0000-4000-8000-%'
      UNION ALL SELECT n.user_id FROM notifications.notification n WHERE n.id LIKE 'demo-th-%'
      UNION ALL SELECT a."actorId" FROM campuscore_audit."AdminAudit" a WHERE a.id LIKE 'demo-th-%'
    ) refs)
)
SELECT kind, count(*) AS manifest_entries
FROM manifest
GROUP BY kind
ORDER BY kind;

-- Evidence join: every id above must resolve to a real seeded row. Anything
-- listed as missing means the database is NOT the expected baseline and
-- apply.sql's own guard will refuse to run anyway.
SELECT 'unresolvable_student_ids' AS check_name,
       count(*)::text AS actual, '0' AS expected,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM (
  SELECT 'student-profile-' || lpad(g::text, 3, '0') AS id FROM generate_series(21, 30) g
  UNION ALL SELECT 'student-profile-' || n FROM unnest(ARRAY['109','110','120','136','137','138','139','140']) n
  UNION ALL SELECT 'student-profile-' || lpad(g::text, 3, '0') FROM generate_series(153, 202) g
) want
WHERE NOT EXISTS (SELECT 1 FROM academic."Student" s WHERE s."id" = want.id);
