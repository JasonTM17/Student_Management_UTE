-- Purpose: give the demo transcripts a second closed semester so the GPA
-- trend chart shows a real multi-point line. Mirrors the V25 pattern
-- (COMPLETED/PUBLISHED enrollments referencing the SE40x -01 sections) with a
-- different score formula. Fully synthetic, deterministic and idempotent.
-- V26 is already applied and must never be edited; enrichments go in new
-- migrations only.

INSERT INTO academic."Semester" (
    "id", "name", "nameEn", "nameVi", "type", "academicYearId",
    "startDate", "endDate", "registrationStart", "registrationEnd", "status"
)
SELECT
    'semester-history-demo-1',
    'Học kỳ 1 năm học 2025-2026',
    'Semester 1 2025-2026',
    'Học kỳ 1 năm học 2025-2026',
    'FIRST',
    'academic-year-history-demo',
    '2025-09-01T00:00:00Z',
    '2026-01-10T23:59:59Z',
    '2025-08-01T00:00:00Z',
    '2025-09-05T23:59:59Z',
    'CLOSED'
WHERE NOT EXISTS (
    SELECT 1 FROM academic."Semester" WHERE "id" = 'semester-history-demo-1'
);

WITH students AS (
    SELECT 1 AS student_number, 'student-profile' AS student_profile_id
    UNION ALL
    SELECT n, 'student-profile-' || lpad(n::text, 3, '0')
    FROM generate_series(2, 16) AS series(n)
    UNION ALL
    SELECT n, 'student-profile-' || lpad(n::text, 3, '0')
    FROM generate_series(101, 300) AS series(n)
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
      AND course."code" IN ('SE401', 'SE402', 'SE403', 'SE404', 'SE405', 'SE406')
), scored AS (
    SELECT students.student_number,
           students.student_profile_id,
           sections.section_id,
           sections.course_id,
           sections.course_code,
           sections.credits,
           round((6.0 + (((students.student_number * 5 + sections.course_index * 2) % 30)::NUMERIC / 10)), 2) AS midterm_score,
           round((6.5 + (((students.student_number * 3 + sections.course_index * 5) % 30)::NUMERIC / 10)), 2) AS final_score
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
SELECT 'enrollment-history-v27-' || lpad(final_scores.student_number::text, 3, '0') || '-' || final_scores.course_code,
       final_scores.student_profile_id,
       final_scores.section_id,
       'semester-history-demo-1',
       'COMPLETED',
       TIMESTAMPTZ '2025-12-10T00:00:00Z' + (final_scores.student_number * INTERVAL '1 day'),
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

INSERT INTO academic."StudentGrade" ("id", "enrollmentId", "gradeItemId", "score")
SELECT 'student-grade-history-v27-' || lpad(scored.student_number::text, 3, '0') || '-' || scored.course_code || '-' || component.grade_type,
       'enrollment-history-v27-' || lpad(scored.student_number::text, 3, '0') || '-' || scored.course_code,
       item."id",
       component.score
FROM (
    SELECT students.student_number, sections.section_id, sections.course_code,
           round((6.0 + (((students.student_number * 5 + sections.course_index * 2) % 30)::NUMERIC / 10)), 2) AS midterm_score,
           round((6.5 + (((students.student_number * 3 + sections.course_index * 5) % 30)::NUMERIC / 10)), 2) AS final_score
    FROM (
        SELECT 1 AS student_number, 'student-profile' AS student_profile_id
        UNION ALL
        SELECT n, 'student-profile-' || lpad(n::text, 3, '0')
        FROM generate_series(2, 16) AS series(n)
        UNION ALL
        SELECT n, 'student-profile-' || lpad(n::text, 3, '0')
        FROM generate_series(101, 300) AS series(n)
    ) students
    CROSS JOIN (
        SELECT section."id" AS section_id, course."code" AS course_code,
               row_number() OVER (ORDER BY course."code")::INTEGER AS course_index
        FROM academic."Section" section
        JOIN academic."Course" course ON course."id" = section."courseId"
        WHERE section."semesterId" = 'semester-demo'
          AND section."sectionNumber" LIKE '%-01'
          AND course."code" IN ('SE401', 'SE402', 'SE403', 'SE404', 'SE405', 'SE406')
    ) sections
) scored
CROSS JOIN LATERAL (
    VALUES ('MIDTERM', scored.midterm_score), ('FINAL', scored.final_score)
) AS component(grade_type, score)
JOIN academic."GradeItem" item
  ON item."id" = 'grade-history-' || scored.section_id || '-' || lower(component.grade_type)
WHERE NOT EXISTS (
    SELECT 1 FROM academic."StudentGrade" existing
    WHERE existing."id" = 'student-grade-history-v27-' || lpad(scored.student_number::text, 3, '0') || '-' || scored.course_code || '-' || component.grade_type
);
