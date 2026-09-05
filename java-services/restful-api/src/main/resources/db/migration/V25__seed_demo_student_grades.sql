-- Seed a rich, deterministic demo transcript for every local student.
-- Historical rows live in a closed semester so current registration/schedule data stays active.
-- All identifiers are synthetic and the migration is safe to rerun.

INSERT INTO academic."AcademicYear" ("id", "year", "startDate", "endDate", "isCurrent")
SELECT 'academic-year-history-demo', 2025, '2025-01-01T00:00:00Z', '2025-12-31T23:59:59Z', FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM academic."AcademicYear" WHERE "id" = 'academic-year-history-demo'
);

INSERT INTO academic."Semester" (
    "id", "name", "nameEn", "nameVi", "type", "academicYearId",
    "startDate", "endDate", "registrationStart", "registrationEnd", "status"
)
SELECT
    'semester-history-demo',
    'Học kỳ 2 năm học 2025-2026',
    'Semester 2 2025-2026',
    'Học kỳ 2 năm học 2025-2026',
    'SECOND',
    'academic-year-history-demo',
    '2026-01-15T00:00:00Z',
    '2026-06-30T23:59:59Z',
    '2025-12-15T00:00:00Z',
    '2026-01-20T23:59:59Z',
    'CLOSED'
WHERE NOT EXISTS (
    SELECT 1 FROM academic."Semester" WHERE "id" = 'semester-history-demo'
);

-- Fifteen additional local-only identities make lecturer/admin grade screens useful.
WITH generated AS (
    SELECT n,
           'student-user-' || lpad(n::text, 3, '0') AS user_id,
           'student' || lpad(n::text, 3, '0') || '@campuscore.demo' AS email,
           'Sinh viên ' || lpad(n::text, 3, '0') AS first_name,
           'Demo' AS last_name
    FROM generate_series(2, 16) AS series(n)
)
INSERT INTO campuscore_auth."User" (
    "id", "email", "password", "firstName", "lastName", "status", "emailVerified"
)
SELECT generated.user_id,
       generated.email,
       (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'student-user'),
       generated.first_name,
       generated.last_name,
       'ACTIVE',
       TRUE
FROM generated
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."User" existing WHERE existing."id" = generated.user_id
);

WITH generated AS (
    SELECT n,
           'student-user-' || lpad(n::text, 3, '0') AS user_id,
           'student-profile-' || lpad(n::text, 3, '0') AS student_profile_id,
           'CS-DEMO-' || lpad(n::text, 3, '0') AS student_number
    FROM generate_series(2, 16) AS series(n)
)
INSERT INTO academic."Student" (
    "id", "userId", "studentId", "curriculumId", "year", "status", "admissionDate"
)
SELECT generated.student_profile_id,
       generated.user_id,
       generated.student_number,
       'curriculum-demo',
       CASE WHEN generated.n % 3 = 0 THEN 3 ELSE 2 END,
       'ACTIVE',
       TIMESTAMPTZ '2024-09-01T00:00:00Z' + ((generated.n - 2) % 4) * INTERVAL '30 days'
FROM generated
WHERE NOT EXISTS (
    SELECT 1 FROM academic."Student" existing WHERE existing."id" = generated.student_profile_id
);

WITH generated AS (
    SELECT n,
           'student-user-' || lpad(n::text, 3, '0') AS user_id,
           'user-role-student-' || lpad(n::text, 3, '0') AS user_role_id
    FROM generate_series(2, 16) AS series(n)
)
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT generated.user_role_id, generated.user_id, 'role-student'
FROM generated
WHERE NOT EXISTS (
    SELECT 1
    FROM campuscore_auth."UserRole" existing
    WHERE existing."userId" = generated.user_id
      AND existing."roleId" = 'role-student'
);

-- Ensure every representative section has weighted midterm/final components.
INSERT INTO academic."GradeItem" (
    "id", "sectionId", "name", "type", "maxScore", "weight", "gradedAt"
)
SELECT 'grade-history-' || section."id" || '-midterm',
       section."id",
       'Giữa kỳ',
       'MIDTERM',
       10,
       40,
       CURRENT_TIMESTAMP
FROM academic."Section" section
JOIN academic."Course" course ON course."id" = section."courseId"
WHERE section."semesterId" = 'semester-demo'
  AND section."sectionNumber" LIKE '%-01'
  AND course."code" IN ('SE401', 'SE402', 'SE403', 'SE404', 'SE405', 'SE406', 'SE407', 'SE408', 'SE409', 'SE410', 'SE411', 'SE412')
  AND NOT EXISTS (
      SELECT 1 FROM academic."GradeItem" item WHERE item."id" = 'grade-history-' || section."id" || '-midterm'
  );

INSERT INTO academic."GradeItem" (
    "id", "sectionId", "name", "type", "maxScore", "weight", "gradedAt"
)
SELECT 'grade-history-' || section."id" || '-final',
       section."id",
       'Cuối kỳ',
       'FINAL',
       10,
       60,
       CURRENT_TIMESTAMP
FROM academic."Section" section
JOIN academic."Course" course ON course."id" = section."courseId"
WHERE section."semesterId" = 'semester-demo'
  AND section."sectionNumber" LIKE '%-01'
  AND course."code" IN ('SE401', 'SE402', 'SE403', 'SE404', 'SE405', 'SE406', 'SE407', 'SE408', 'SE409', 'SE410', 'SE411', 'SE412')
  AND NOT EXISTS (
      SELECT 1 FROM academic."GradeItem" item WHERE item."id" = 'grade-history-' || section."id" || '-final'
  );

WITH students AS (
    SELECT 1 AS student_number, 'student-profile' AS student_profile_id
    UNION ALL
    SELECT n, 'student-profile-' || lpad(n::text, 3, '0')
    FROM generate_series(2, 16) AS series(n)
), sections AS (
    SELECT section."id" AS section_id,
           section."courseId" AS course_id,
           course."code" AS course_code,
           course."credits",
           row_number() OVER (ORDER BY course."code")::INTEGER AS course_index
    FROM academic."Section" section
    JOIN academic."Course" course ON course."id" = section."courseId"
    WHERE section."semesterId" = 'semester-demo'
      AND section."sectionNumber" LIKE '%-01'
      AND course."code" IN ('SE401', 'SE402', 'SE403', 'SE404', 'SE405', 'SE406', 'SE407', 'SE408', 'SE409', 'SE410', 'SE411', 'SE412')
), scored AS (
    SELECT students.student_number,
           students.student_profile_id,
           sections.section_id,
           sections.course_id,
           sections.course_code,
           sections.credits,
           round((5.5 + (((students.student_number * 7 + sections.course_index * 3) % 40)::NUMERIC / 10)), 2) AS midterm_score,
           round((6.0 + (((students.student_number * 5 + sections.course_index * 4) % 35)::NUMERIC / 10)), 2) AS final_score
    FROM students
    CROSS JOIN sections
), final_scores AS (
    SELECT scored.*,
           round((scored.midterm_score * 0.4 + scored.final_score * 0.6), 2) AS final_grade
    FROM scored
)
INSERT INTO academic."Enrollment" (
    "id", "studentId", "sectionId", "semesterId", "status", "enrolledAt",
    "gradeStatus", "finalGrade", "letterGrade", "courseId", "roundId",
    "creditsSnapshot", "version"
)
SELECT 'enrollment-history-demo-' || lpad(final_scores.student_number::text, 3, '0') || '-' || final_scores.course_code,
       final_scores.student_profile_id,
       final_scores.section_id,
       'semester-history-demo',
       'COMPLETED',
       TIMESTAMPTZ '2026-05-20T00:00:00Z' + (final_scores.student_number * INTERVAL '1 day'),
       'PUBLISHED',
       final_scores.final_grade,
       CASE
           WHEN final_scores.final_grade >= 8.5 THEN 'A'
           WHEN final_scores.final_grade >= 8.0 THEN 'B+'
           WHEN final_scores.final_grade >= 7.0 THEN 'B'
           WHEN final_scores.final_grade >= 6.5 THEN 'C+'
           WHEN final_scores.final_grade >= 5.5 THEN 'C'
           WHEN final_scores.final_grade >= 4.0 THEN 'D'
           ELSE 'F'
       END,
       final_scores.course_id,
       'round-registration-current-demo',
       final_scores.credits,
       0
FROM final_scores
ON CONFLICT ("id") DO NOTHING;

WITH students AS (
    SELECT 1 AS student_number, 'student-profile' AS student_profile_id
    UNION ALL
    SELECT n, 'student-profile-' || lpad(n::text, 3, '0')
    FROM generate_series(2, 16) AS series(n)
), sections AS (
    SELECT section."id" AS section_id,
           course."code" AS course_code,
           row_number() OVER (ORDER BY course."code")::INTEGER AS course_index
    FROM academic."Section" section
    JOIN academic."Course" course ON course."id" = section."courseId"
    WHERE section."semesterId" = 'semester-demo'
      AND section."sectionNumber" LIKE '%-01'
      AND course."code" IN ('SE401', 'SE402', 'SE403', 'SE404', 'SE405', 'SE406', 'SE407', 'SE408', 'SE409', 'SE410', 'SE411', 'SE412')
), scored AS (
    SELECT students.student_number,
           students.student_profile_id,
           sections.section_id,
           sections.course_code,
           round((5.5 + (((students.student_number * 7 + sections.course_index * 3) % 40)::NUMERIC / 10)), 2) AS midterm_score,
           round((6.0 + (((students.student_number * 5 + sections.course_index * 4) % 35)::NUMERIC / 10)), 2) AS final_score
    FROM students
    CROSS JOIN sections
)
INSERT INTO academic."StudentGrade" ("id", "enrollmentId", "gradeItemId", "score")
SELECT 'student-grade-history-demo-' || lpad(scored.student_number::text, 3, '0') || '-' || scored.course_code || '-' || component.grade_type,
       'enrollment-history-demo-' || lpad(scored.student_number::text, 3, '0') || '-' || scored.course_code,
       item."id",
       component.score
FROM scored
CROSS JOIN LATERAL (
    VALUES ('MIDTERM', scored.midterm_score), ('FINAL', scored.final_score)
) AS component(grade_type, score)
JOIN academic."GradeItem" item
  ON item."id" = 'grade-history-' || scored.section_id || '-' || lower(component.grade_type)
WHERE NOT EXISTS (
    SELECT 1
    FROM academic."StudentGrade" existing
    WHERE existing."id" = 'student-grade-history-demo-' || lpad(scored.student_number::text, 3, '0') || '-' || scored.course_code || '-' || component.grade_type
);
