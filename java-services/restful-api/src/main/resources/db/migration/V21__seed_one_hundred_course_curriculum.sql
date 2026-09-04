-- Expand the local academic catalogue to exactly 100 courses.  Rows are
-- deterministic and synthetic; the three curriculum buckets are represented
-- by CurriculumCourse membership and planned year/semester:
--   n 13-40: in plan for the demo student's current year (2)
--   n 41-70: in curriculum but scheduled for a later year (out of plan)
--   n 71-100: deliberately not linked to a curriculum (out of program)

WITH generated AS (
    SELECT n,
           'course-auto-' || lpad(n::text, 3, '0') AS id,
           'SE' || lpad(n::text, 3, '0') AS code,
           'Chuyên đề kỹ thuật phần mềm ' || lpad(n::text, 3, '0') AS name,
           'Software Engineering Special Topic ' || lpad(n::text, 3, '0') AS name_en,
           'Chuyên đề kỹ thuật phần mềm ' || lpad(n::text, 3, '0') AS name_vi,
           CASE n % 5
               WHEN 0 THEN 'Thiết kế và xây dựng hệ thống phần mềm theo hướng kiểm chứng được, có ví dụ thực hành và tiêu chí nghiệm thu rõ ràng.'
               WHEN 1 THEN 'Phân tích bài toán, mô hình hóa dữ liệu, triển khai API và đánh giá chất lượng trong bối cảnh sản phẩm học vụ.'
               WHEN 2 THEN 'Thực hành quy trình phát triển hiện đại: yêu cầu, kiến trúc, kiểm thử, bảo mật, vận hành và cải tiến liên tục.'
               WHEN 3 THEN 'Kết hợp nền tảng lý thuyết với bài tập phòng lab, review mã nguồn, tài liệu kỹ thuật và bài đánh giá cuối kỳ.'
               ELSE 'Học phần định hướng dự án với đầu ra gồm sản phẩm chạy được, báo cáo kỹ thuật, dữ liệu kiểm thử và phần trình bày.'
           END AS description_vi,
           CASE n % 5
               WHEN 0 THEN 'Design and build verifiable software systems with practical examples and explicit acceptance criteria.'
               WHEN 1 THEN 'Analyze requirements, model data, deliver APIs, and evaluate quality in an academic product context.'
               WHEN 2 THEN 'Practice a modern delivery lifecycle covering requirements, architecture, testing, security, operations, and continuous improvement.'
               WHEN 3 THEN 'Combine theory with lab work, code review, technical documentation, and a final assessment.'
               ELSE 'Project-oriented learning with a working product, technical report, test evidence, and presentation as outcomes.'
           END AS description_en,
           2 + (n % 3) AS credits
    FROM generate_series(13, 100) AS series(n)
)
INSERT INTO academic."Course" ("id", "code", "name", "nameEn", "nameVi", "description", "descriptionEn", "descriptionVi", "credits", "departmentId", "semesterId")
SELECT id, code, name, name_en, name_vi, description_vi, description_en, description_vi, credits, 'department-demo', 'semester-demo'
FROM generated
WHERE NOT EXISTS (SELECT 1 FROM academic."Course" existing WHERE existing."id" = generated.id);

WITH generated AS (
    SELECT n,
           'curriculum-course-auto-' || lpad(n::text, 3, '0') AS id,
           'course-auto-' || lpad(n::text, 3, '0') AS course_id,
           CASE WHEN n <= 40 THEN 2 ELSE 3 + ((n - 41) % 2) END AS year,
           CASE WHEN n % 2 = 0 THEN 1 ELSE 2 END AS semester,
           n <= 58 AS mandatory
    FROM generate_series(13, 70) AS series(n)
)
INSERT INTO academic."CurriculumCourse" ("id", "curriculumId", "courseId", "year", "semester", "isMandatory")
SELECT id, 'curriculum-demo', course_id, year, semester, mandatory
FROM generated
WHERE NOT EXISTS (SELECT 1 FROM academic."CurriculumCourse" existing WHERE existing."id" = generated.id);

WITH generated AS (
    SELECT n,
           'section-auto-' || lpad(n::text, 3, '0') AS id,
           'SE' || lpad(n::text, 3, '0') || '-01' AS section_number,
           'course-auto-' || lpad(n::text, 3, '0') AS course_id,
           'classroom-a' || lpad((103 + ((n - 13) % 8))::text, 3, '0') AS classroom_id,
           30 + ((n * 7) % 31) AS capacity
    FROM generate_series(13, 100) AS series(n)
)
INSERT INTO academic."Section" ("id", "sectionNumber", "courseId", "semesterId", "lecturerId", "classroomId", "capacity", "enrolledCount", "status")
SELECT id, section_number, course_id, 'semester-demo', 'lecturer-profile', classroom_id, capacity, 0, 'OPEN'
FROM generated
WHERE NOT EXISTS (SELECT 1 FROM academic."Section" existing WHERE existing."id" = generated.id);

WITH generated AS (
    SELECT n,
           'schedule-auto-' || lpad(n::text, 3, '0') AS id,
           'section-auto-' || lpad(n::text, 3, '0') AS section_id,
           'classroom-a' || lpad((103 + ((n - 13) % 8))::text, 3, '0') AS classroom_id,
           2 + ((n - 13) % 6) AS day_of_week,
           CASE n % 4 WHEN 0 THEN '07:00' WHEN 1 THEN '09:45' WHEN 2 THEN '13:00' ELSE '15:45' END AS start_time,
           CASE n % 4 WHEN 0 THEN '09:30' WHEN 1 THEN '12:15' WHEN 2 THEN '15:30' ELSE '18:15' END AS end_time
    FROM generate_series(13, 100) AS series(n)
)
INSERT INTO academic."SectionSchedule" ("id", "sectionId", "classroomId", "dayOfWeek", "startTime", "endTime")
SELECT id, section_id, classroom_id, day_of_week, start_time, end_time
FROM generated
WHERE NOT EXISTS (SELECT 1 FROM academic."SectionSchedule" existing WHERE existing."id" = generated.id);

WITH generated AS (
    SELECT n,
           'grade-auto-' || lpad(n::text, 3, '0') || '-project' AS id,
           'section-auto-' || lpad(n::text, 3, '0') AS section_id
    FROM generate_series(13, 100) AS series(n)
)
INSERT INTO academic."GradeItem" ("id", "sectionId", "name", "type", "maxScore", "weight")
SELECT id, section_id, 'Bài đánh giá dự án', 'PROJECT', 10, 100
FROM generated
WHERE NOT EXISTS (SELECT 1 FROM academic."GradeItem" existing WHERE existing."id" = generated.id);

-- Give the demo student a realistic current timetable: six active courses,
-- 18 credits in total, with distinct meeting slots.  This is seed data only;
-- normal users still pass through the credit cap and conflict checks.
INSERT INTO academic."Enrollment" ("id", "studentId", "sectionId", "semesterId", "status", "enrolledAt", "gradeStatus", "courseId", "roundId", "creditsSnapshot", "version")
SELECT seed.id, 'student-profile', seed.section_id, 'semester-demo', 'ENROLLED', CURRENT_TIMESTAMP, 'NOT_GRADED', seed.course_id, 'round-registration-current-demo', seed.credits, 0
FROM (VALUES
    ('enrollment-web-demo', 'section-web-demo', 'course-web-demo', 3),
    ('enrollment-algorithms-demo', 'section-algorithms-demo', 'course-algorithms-demo', 4),
    ('enrollment-architecture-demo', 'section-architecture-demo', 'course-software-architecture-demo', 3),
    ('enrollment-testing-demo', 'section-testing-demo', 'course-testing-demo', 3),
    ('enrollment-devops-demo', 'section-devops-demo', 'course-devops-demo', 3)
) AS seed(id, section_id, course_id, credits)
WHERE NOT EXISTS (SELECT 1 FROM academic."Enrollment" existing WHERE existing."id" = seed.id);

UPDATE academic."Section" section
SET "enrolledCount" = counts.total
FROM (
    SELECT "sectionId" AS section_id, COUNT(*)::integer AS total
    FROM academic."Enrollment"
    WHERE "status" IN ('ENROLLED', 'PENDING', 'CONFIRMED')
    GROUP BY "sectionId"
) counts
WHERE section."id" = counts.section_id;
