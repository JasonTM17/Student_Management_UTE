-- V143__thesis_eligibility_demo_academic_records.sql
-- Purpose: give the EXISTING demo student accounts enough published academic
--   history to participate in thesis registration under the accumulated
--   credit regulation (>=110 earned credits, CPA >=2.0 on the 4.0 scale), and
--   deliberately keep a few demo students below the bar so the ineligibility
--   warning state is visible to students and governance roles.
--
-- Seeded ELIGIBLE students (profile ids only):
--   student-profile-058        +~82 new credits (letters A/B+/B, CPA ~3.2)
--   student-profile-059        +~84 new credits (letters A/B+/B, CPA ~3.3)
--   student-profile-team-004   +~78 new credits (letters B+/B/C+, CPA ~2.9)
--   (each already carries ~57 raw / ~38 best-attempt credits from V111-V130;
--    verified transcript totals: 058 -> 122 / CPA 3.22, 059 -> 124 / CPA 3.31,
--    team-004 -> 118 / CPA 2.89 on the production-shaped database)
--
-- Seeded DELIBERATELY INELIGIBLE students (transcript totals incl. the ~38
-- best-attempt credits they already carry):
--   student-profile-106  ~66 credits total  -> INSUFFICIENT_CREDITS
--   student-profile-107  ~100 credits total -> INSUFFICIENT_CREDITS
--   student-profile-108  ~159 credits @ ~1.85 GPA -> LOW_GPA
--   student-profile-105  ~38 credits        -> INSUFFICIENT_CREDITS
--   student-profile-021  no published rows  -> ACADEMIC_DATA_UNAVAILABLE
--   student-profile, student-profile-101 are left untouched and remain below
--   the threshold on their existing records (~38 credits). student-profile is
--   the flagship demo login (student@campuscore.demo) and stays the one
--   login-capable account that shows the ineligible warning.
--
-- Selection rules (release-2 review):
--   * Every seeded row's Enrollment."semesterId" comes from a FINISHED
--     semester (status CLOSED/COMPLETED/ARCHIVED — the app's terminal
--     vocabulary), so no COMPLETED enrollment ever lands inside a live
--     semester. Rows are spread deterministically over the terminal
--     semesters the database actually has (oldest first); a database with
--     no terminal semester picks nothing rather than writing into a live
--     one.
--   * The demo catalog keeps all Section rows on the browsing semester
--     (semester-demo): the 2,730 pre-existing enrollments already reference
--     those catalog sections while carrying a closed history semesterId,
--     and this seed follows the same convention — the section is the course
--     carrier, the enrollment's own semester column marks the term.
--   * Only sections taught by a demo/seed lecturer (lecturerId NULL or
--     'lecturer-profile-%') are eligible, so demo history never credits a
--     real staff account.
--   * At most one section per (student, course) is picked AND a course the
--     student already has an enrollment/grade for is skipped, so the seed
--     can never create a duplicate-course retake inside or across
--     statements.
--
-- Safety: additive only. Deterministic 'enr-el1-*' ids, existence guards, and
-- ON CONFLICT DO NOTHING keep the migration idempotent. Rows are written
-- only for literal student-profile-* ids that already exist in
-- academic."Student" — the same positive-identification rule as
-- ops/thesis-demo-data/manifest.sql; the four UUID-shaped real/ambiguous
-- accounts are never named or matched. No thesis group/member/topic rows
-- are created or changed and no table other than academic."Enrollment" is
-- written.

WITH targets AS (
    SELECT * FROM (VALUES
        -- targets are NEW credits; totals land ~38 higher (existing rows)
        ('student-profile-058',      's058', 82, 'good'),
        ('student-profile-059',      's059', 84, 'good'),
        ('student-profile-team-004', 's004', 78, 'mid'),
        ('student-profile-106',      's106', 26, 'good'),
        ('student-profile-107',      's107', 60, 'mid'),
        ('student-profile-108',      's108', 120, 'low')
    ) AS t(student_id, tag, target, mode)
),
finished_semesters AS (
    -- terminal semesters the database actually carries, oldest first
    SELECT sem."id" AS semester_id,
           ROW_NUMBER() OVER (ORDER BY sem."endDate" NULLS LAST, sem."id") AS ord,
           COUNT(*) OVER () AS n
    FROM academic."Semester" sem
    WHERE sem."status" IN ('CLOSED', 'COMPLETED', 'ARCHIVED')
),
candidates AS (
    SELECT s."id" AS section_id, s."courseId" AS course_id, c."credits" AS credits
    FROM academic."Section" s
    JOIN academic."Course" c ON c."id" = s."courseId"
    WHERE c."credits" IS NOT NULL AND c."credits" > 0
      AND (s."lecturerId" IS NULL OR s."lecturerId" LIKE 'lecturer-profile%')
),
one_per_course AS (
    -- DISTINCT ON keeps the deterministic first section per course, so two
    -- rows of the same course can never be picked in one statement.
    SELECT DISTINCT ON (t.student_id, cand.course_id)
           t.student_id, t.tag, t.target, t.mode,
           cand.section_id, cand.course_id, cand.credits
    FROM targets t
    JOIN candidates cand
      ON NOT EXISTS (
          SELECT 1 FROM academic."Enrollment" existing
          WHERE existing."studentId" = t.student_id
            AND existing."courseId" = cand.course_id)
    ORDER BY t.student_id, cand.course_id, cand.section_id
),
numbered AS (
    SELECT one.*,
           ROW_NUMBER() OVER (PARTITION BY one.student_id
                              ORDER BY one.course_id) AS rn,
           SUM(one.credits) OVER (PARTITION BY one.student_id
                                  ORDER BY one.course_id
                                  ROWS UNBOUNDED PRECEDING) AS running
    FROM one_per_course one
),
picked AS (
    -- take courses in deterministic order until the per-student target is
    -- met; each row is stamped into a terminal semester (round-robin over
    -- the ordered finished_semesters list) so nothing lands in a live term
    SELECT num.*, fin.semester_id
    FROM numbered num
    JOIN finished_semesters fin
      ON fin.ord = ((num.rn - 1) % fin.n) + 1
    WHERE num.running - num.credits < num.target
)
INSERT INTO academic."Enrollment" (
    "id", "studentId", "sectionId", "semesterId", "status",
    "enrolledAt", "gradeStatus", "finalGrade", "letterGrade",
    "courseId", "roundId", "creditsSnapshot", "version"
)
SELECT
    'enr-el1-' || picked.tag || '-' || lpad(picked.rn::text, 3, '0'),
    picked.student_id,
    picked.section_id,
    picked.semester_id,
    'COMPLETED',
    CURRENT_TIMESTAMP - INTERVAL '400 days' + (picked.rn * INTERVAL '1 day'),
    'PUBLISHED',
    CASE
        WHEN picked.mode = 'low' THEN
            CASE WHEN picked.rn % 5 = 0 THEN 5.9 WHEN picked.rn % 2 = 0 THEN 5.5 ELSE 5.1 END
        WHEN picked.mode = 'mid' THEN
            CASE WHEN picked.rn % 6 = 0 THEN 8.3 WHEN picked.rn % 2 = 0 THEN 7.5 ELSE 6.7 END
        ELSE
            CASE WHEN picked.rn % 7 = 0 THEN 6.6 WHEN picked.rn % 5 = 0 THEN 8.9
                 WHEN picked.rn % 2 = 0 THEN 8.4 ELSE 7.6 END
    END,
    CASE
        WHEN picked.mode = 'low' THEN
            CASE WHEN picked.rn % 5 = 0 THEN 'C' WHEN picked.rn % 2 = 0 THEN 'D+' ELSE 'D' END
        WHEN picked.mode = 'mid' THEN
            CASE WHEN picked.rn % 6 = 0 THEN 'B+' WHEN picked.rn % 2 = 0 THEN 'B' ELSE 'C+' END
        ELSE
            CASE WHEN picked.rn % 7 = 0 THEN 'C+' WHEN picked.rn % 5 = 0 THEN 'A'
                 WHEN picked.rn % 2 = 0 THEN 'B+' ELSE 'B' END
    END,
    picked.course_id,
    NULL,
    picked.credits,
    0
FROM picked
WHERE EXISTS (
        SELECT 1 FROM academic."Student" st WHERE st."id" = picked.student_id)
  -- once a student carries any 'enr-el1-<tag>-*' row the seed is considered
  -- applied for them; the id guard alone is not enough because a re-run
  -- re-picks different leftover courses under fresh row numbers.
  AND NOT EXISTS (
        SELECT 1 FROM academic."Enrollment" e
        WHERE e."id" LIKE 'enr-el1-' || picked.tag || '-%')
ON CONFLICT ("id") DO NOTHING;
