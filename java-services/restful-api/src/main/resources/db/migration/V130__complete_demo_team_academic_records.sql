-- V130__complete_demo_team_academic_records.sql
-- Purpose:
--   1. Populate current-semester ENROLLED sections and completed/published
--      course grade history (including StudentGrade component rows) for the
--      fourth quick-fill demo student (student-profile-team-004 /
--      student4@campuscore.edu - Vũ Quốc Huy, MSSV 24162050) so all four
--      students listed on the login quick-fill panel have complete schedules,
--      enrollments, grades, and transcripts.
--   2. Populate Attendance rows and Conduct (DRL) semester scores + activities
--      for student-profile-058 (student2), student-profile-059 (student3), and
--      student-profile-team-004 (student4) so /dashboard/attendance and
--      /dashboard/conduct render rich data for every demo student account.

-- =============================================================================
-- 1. CURRENT-SEMESTER ENROLLMENTS & COMPLETED GRADE HISTORY FOR STUDENT 4
-- =============================================================================
INSERT INTO academic."Enrollment" (
    "id", "studentId", "sectionId", "semesterId", "status",
    "enrolledAt", "gradeStatus", "courseId", "roundId", "creditsSnapshot", "version"
)
SELECT
    'enr-s4-demo-' || src."sectionId",
    'student-profile-team-004',
    src."sectionId",
    src."semesterId",
    'ENROLLED',
    CURRENT_TIMESTAMP,
    'NOT_GRADED',
    src."courseId",
    src."roundId",
    src."creditsSnapshot",
    0
FROM academic."Enrollment" src
WHERE src."studentId" = 'student-profile'
  AND src."status" = 'ENROLLED'
  AND EXISTS (SELECT 1 FROM academic."Student" st WHERE st."id" = 'student-profile-team-004')
  AND NOT EXISTS (
      SELECT 1 FROM academic."Enrollment" existing
      WHERE existing."studentId" = 'student-profile-team-004'
        AND (existing."sectionId" = src."sectionId"
             OR (existing."courseId" = src."courseId" AND existing."semesterId" = src."semesterId"))
  )
ON CONFLICT ("id") DO NOTHING;

WITH src AS (
    SELECT e.*, ROW_NUMBER() OVER (ORDER BY e."id") AS rn
    FROM academic."Enrollment" e
    WHERE e."studentId" = 'student-profile'
      AND e."status" = 'COMPLETED'
)
INSERT INTO academic."Enrollment" (
    "id", "studentId", "sectionId", "semesterId", "status",
    "enrolledAt", "gradeStatus", "finalGrade", "letterGrade",
    "courseId", "roundId", "creditsSnapshot", "version"
)
SELECT
    'enr-s4-hist-' || src.rn,
    'student-profile-team-004',
    src."sectionId",
    src."semesterId",
    'COMPLETED',
    src."enrolledAt",
    'PUBLISHED',
    GREATEST(5.5, LEAST(10.0, ROUND(COALESCE(src."finalGrade", 8.0) - 0.1, 2))),
    CASE
        WHEN GREATEST(5.5, LEAST(10.0, ROUND(COALESCE(src."finalGrade", 8.0) - 0.1, 2))) >= 9.0 THEN 'A+'
        WHEN GREATEST(5.5, LEAST(10.0, ROUND(COALESCE(src."finalGrade", 8.0) - 0.1, 2))) >= 8.5 THEN 'A'
        WHEN GREATEST(5.5, LEAST(10.0, ROUND(COALESCE(src."finalGrade", 8.0) - 0.1, 2))) >= 8.0 THEN 'B+'
        WHEN GREATEST(5.5, LEAST(10.0, ROUND(COALESCE(src."finalGrade", 8.0) - 0.1, 2))) >= 7.0 THEN 'B'
        WHEN GREATEST(5.5, LEAST(10.0, ROUND(COALESCE(src."finalGrade", 8.0) - 0.1, 2))) >= 6.5 THEN 'C+'
        ELSE 'C'
    END,
    src."courseId",
    src."roundId",
    src."creditsSnapshot",
    0
FROM src
WHERE EXISTS (SELECT 1 FROM academic."Student" st WHERE st."id" = 'student-profile-team-004')
  AND NOT EXISTS (
      SELECT 1 FROM academic."Enrollment" existing
      WHERE existing."studentId" = 'student-profile-team-004'
        AND existing."sectionId" = src."sectionId"
        AND existing."semesterId" = src."semesterId"
  )
ON CONFLICT ("id") DO NOTHING;

INSERT INTO academic."StudentGrade" ("id", "enrollmentId", "gradeItemId", "score")
SELECT
    'sg-' || e."id" || '-' || gi."type",
    e."id",
    gi."id",
    CASE
        WHEN gi."type" = 'PROCESS' THEN GREATEST(5.0, LEAST(10.0, COALESCE(e."finalGrade", 8.0) - 0.2))
        ELSE GREATEST(5.0, LEAST(10.0, COALESCE(e."finalGrade", 8.0) + 0.2))
    END
FROM academic."Enrollment" e
JOIN academic."GradeItem" gi ON gi."sectionId" = e."sectionId"
WHERE e."studentId" = 'student-profile-team-004'
  AND e."status" = 'COMPLETED'
  AND NOT EXISTS (
      SELECT 1 FROM academic."StudentGrade" sg
      WHERE sg."enrollmentId" = e."id" AND sg."gradeItemId" = gi."id"
  )
ON CONFLICT ("id") DO NOTHING;

-- Reconcile Section.enrolledCount after adding student4's active enrollments
UPDATE academic."Section" section
SET "enrolledCount" = (
        SELECT COUNT(*) FROM academic."Enrollment" enrollment
        WHERE enrollment."sectionId" = section."id"
          AND enrollment."status" IN ('ENROLLED', 'PENDING', 'CONFIRMED')),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE section."enrolledCount" <> (
        SELECT COUNT(*) FROM academic."Enrollment" enrollment
        WHERE enrollment."sectionId" = section."id"
          AND enrollment."status" IN ('ENROLLED', 'PENDING', 'CONFIRMED'));

-- =============================================================================
-- 2. ATTENDANCE RECORDS FOR STUDENT 2, STUDENT 3, AND STUDENT 4
-- =============================================================================
INSERT INTO academic."Attendance" (
    "id", "studentId", "sectionId", "date", "status", "notes", "createdAt", "updatedAt"
)
SELECT
    src."id" || '-' || member.suffix,
    member.profile_id,
    src."sectionId",
    src."date",
    src."status",
    src."notes",
    src."createdAt",
    src."updatedAt"
FROM academic."Attendance" src
CROSS JOIN (VALUES
    ('student-profile-058', 's2'),
    ('student-profile-059', 's3'),
    ('student-profile-team-004', 's4')
) AS member(profile_id, suffix)
WHERE src."studentId" = 'student-profile'
  AND EXISTS (SELECT 1 FROM academic."Student" st WHERE st."id" = member.profile_id)
  AND NOT EXISTS (
      SELECT 1 FROM academic."Attendance" existing
      WHERE existing."id" = src."id" || '-' || member.suffix
  )
ON CONFLICT ("id") DO NOTHING;

-- =============================================================================
-- 3. CONDUCT (DRL) SCORES & ACTIVITIES FOR STUDENT 2, STUDENT 3, AND STUDENT 4
-- =============================================================================
INSERT INTO academic.conduct_semester_score (
    id, student_id, semester_id, criteria1_score, criteria2_score, criteria3_score,
    criteria4_score, criteria5_score, total_score, classification, classification_vi,
    status, evaluator_name, evaluated_at, created_at, updated_at
)
SELECT
    src.id || '-' || member.suffix,
    member.profile_id,
    src.semester_id,
    GREATEST(0, src.criteria1_score + member.delta),
    src.criteria2_score,
    src.criteria3_score,
    src.criteria4_score,
    src.criteria5_score,
    GREATEST(0, LEAST(100, src.total_score + member.delta)),
    CASE
        WHEN GREATEST(0, LEAST(100, src.total_score + member.delta)) >= 90 THEN 'XUAT_SAC'
        WHEN GREATEST(0, LEAST(100, src.total_score + member.delta)) >= 80 THEN 'TOT'
        ELSE 'KHA'
    END,
    CASE
        WHEN GREATEST(0, LEAST(100, src.total_score + member.delta)) >= 90 THEN 'Xuất sắc'
        WHEN GREATEST(0, LEAST(100, src.total_score + member.delta)) >= 80 THEN 'Tốt'
        ELSE 'Khá'
    END,
    src.status,
    src.evaluator_name,
    src.evaluated_at,
    src.created_at,
    src.updated_at
FROM academic.conduct_semester_score src
CROSS JOIN (VALUES
    ('student-profile-058', 's2', -1.0),
    ('student-profile-059', 's3', 1.0),
    ('student-profile-team-004', 's4', -2.0)
) AS member(profile_id, suffix, delta)
WHERE src.student_id = 'student-profile'
  AND EXISTS (SELECT 1 FROM academic."Student" st WHERE st."id" = member.profile_id)
  AND NOT EXISTS (
      SELECT 1 FROM academic.conduct_semester_score existing
      WHERE existing.student_id = member.profile_id
        AND existing.semester_id = src.semester_id
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, certificate_url, created_at
)
SELECT
    src.id || '-' || member.suffix,
    member.profile_id,
    src.semester_id,
    src.title,
    src.category,
    src.points,
    src.activity_date,
    src.organizer,
    src.certificate_url,
    src.created_at
FROM academic.conduct_activity src
CROSS JOIN (VALUES
    ('student-profile-058', 's2'),
    ('student-profile-059', 's3'),
    ('student-profile-team-004', 's4')
) AS member(profile_id, suffix)
WHERE src.student_id = 'student-profile'
  AND EXISTS (SELECT 1 FROM academic."Student" st WHERE st."id" = member.profile_id)
  AND NOT EXISTS (
      SELECT 1 FROM academic.conduct_activity existing
      WHERE existing.id = src.id || '-' || member.suffix
  )
ON CONFLICT (id) DO NOTHING;
