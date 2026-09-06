-- Purpose: enrich the demo campus population so admin/lecturer/student
-- surfaces read like a living school. Adds lecturers, students, teaching
-- assignments, current-term enrollments, graded history for the new students
-- and a wider announcement stream. Every row is synthetic, deterministic and
-- idempotent; existing demo identities (student-user, lecturer-user,
-- admin-user) are never modified.

-- ------------------------------------------------------------------ lecturers
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT seed.user_id, seed.email,
       (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'lecturer-user'),
       seed.first_name, seed.last_name, 'ACTIVE', TRUE
FROM (VALUES
    ('lecturer-user-002', 'lecturer002@campuscore.demo', 'ThS.', 'Trần Văn Bình', 'Khoa CNTT'),
    ('lecturer-user-003', 'lecturer003@campuscore.demo', 'TS.', 'Nguyễn Thị Hoa', 'Khoa CNTT'),
    ('lecturer-user-004', 'lecturer004@campuscore.demo', 'ThS.', 'Lê Minh Tuấn', 'Khoa CNTT'),
    ('lecturer-user-005', 'lecturer005@campuscore.demo', 'TS.', 'Phạm Thị Ngọc Lan', 'Khoa CNTT'),
    ('lecturer-user-006', 'lecturer006@campuscore.demo', 'ThS.', 'Hoàng Quốc Dũng', 'Khoa CNTT'),
    ('lecturer-user-007', 'lecturer007@campuscore.demo', 'ThS.', 'Vũ Ngọc Mai', 'Khoa CNTT'),
    ('lecturer-user-008', 'lecturer008@campuscore.demo', 'TS.', 'Đặng Thái Sơn', 'Khoa CNTT'),
    ('lecturer-user-009', 'lecturer009@campuscore.demo', 'ThS.', 'Bùi Thị Thu Hà', 'Khoa CNTT'),
    ('lecturer-user-010', 'lecturer010@campuscore.demo', 'ThS.', 'Dương Công Danh', 'Khoa CNTT'),
    ('lecturer-user-011', 'lecturer011@campuscore.demo', 'TS.', 'Lý Thanh Trúc', 'Khoa CNTT'),
    ('lecturer-user-012', 'lecturer012@campuscore.demo', 'ThS.', 'Mai Hoàng Long', 'Khoa CNTT')
) AS seed(user_id, email, title, last_name, first_name)
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."User" existing WHERE existing."id" = seed.user_id
);

-- V25-style title/specialization live on the Lecturer profile, so map the
-- academic title separately from the display name above.
INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT seed.profile_id, seed.user_id, 'department-demo', seed.employee_id, seed.title, seed.specialization
FROM (VALUES
    ('lecturer-profile-002', 'lecturer-user-002', 'LEC-DEMO-002', 'ThS.', 'Cơ sở dữ liệu và hệ thống thông tin'),
    ('lecturer-profile-003', 'lecturer-user-003', 'LEC-DEMO-003', 'TS.', 'Học máy và phân tích dữ liệu'),
    ('lecturer-profile-004', 'lecturer-user-004', 'LEC-DEMO-004', 'ThS.', 'Mạng máy tính và truyền thông'),
    ('lecturer-profile-005', 'lecturer-user-005', 'LEC-DEMO-005', 'TS.', 'Kiểm thử và đảm bảo chất lượng phần mềm'),
    ('lecturer-profile-006', 'lecturer-user-006', 'LEC-DEMO-006', 'ThS.', 'Kiến trúc phần mềm và điện toán đám mây'),
    ('lecturer-profile-007', 'lecturer-user-007', 'LEC-DEMO-007', 'ThS.', 'An ninh mạng'),
    ('lecturer-profile-008', 'lecturer-user-008', 'LEC-DEMO-008', 'TS.', 'Kỹ thuật phần mềm và quản trị dự án'),
    ('lecturer-profile-009', 'lecturer-user-009', 'LEC-DEMO-009', 'ThS.', 'Thị giác máy tính'),
    ('lecturer-profile-010', 'lecturer-user-010', 'LEC-DEMO-010', 'ThS.', 'Lập trình web và dịch vụ RESTful'),
    ('lecturer-profile-011', 'lecturer-user-011', 'LEC-DEMO-011', 'TS.', 'Xử lý ngôn ngữ tự nhiên'),
    ('lecturer-profile-012', 'lecturer-user-012', 'LEC-DEMO-012', 'ThS.', 'Đồ họa máy tính và thực tế ảo')
) AS seed(profile_id, user_id, employee_id, title, specialization)
WHERE NOT EXISTS (
    SELECT 1 FROM academic."Lecturer" existing WHERE existing."id" = seed.profile_id
);

INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-' || seed.profile_id, seed.user_id, 'role-lecturer'
FROM (VALUES
    ('lecturer-profile-002', 'lecturer-user-002'), ('lecturer-profile-003', 'lecturer-user-003'),
    ('lecturer-profile-004', 'lecturer-user-004'), ('lecturer-profile-005', 'lecturer-user-005'),
    ('lecturer-profile-006', 'lecturer-user-006'), ('lecturer-profile-007', 'lecturer-user-007'),
    ('lecturer-profile-008', 'lecturer-user-008'), ('lecturer-profile-009', 'lecturer-user-009'),
    ('lecturer-profile-010', 'lecturer-user-010'), ('lecturer-profile-011', 'lecturer-user-011'),
    ('lecturer-profile-012', 'lecturer-user-012')
) AS seed(profile_id, user_id)
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."UserRole" existing
    WHERE existing."userId" = seed.user_id AND existing."roleId" = 'role-lecturer'
);

-- ------------------------------------------------------------------- students
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT generated.user_id, generated.email,
       (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'student-user'),
       generated.first_name, generated.last_name, 'ACTIVE', TRUE
FROM (
    WITH name_pool(idx, last_name, first_name) AS (VALUES
        (0, 'Nguyễn', 'Minh Anh'), (1, 'Trần', 'Thành Đạt'), (2, 'Lê', 'Ngọc Hân'),
        (3, 'Phạm', 'Quốc Bảo'), (4, 'Hoàng', 'Thu Hà'), (5, 'Huỳnh', 'Công Danh'),
        (6, 'Phan', 'Khánh Linh'), (7, 'Vũ', 'Văn Đức'), (8, 'Đặng', 'Mỹ Duyên'),
        (9, 'Bùi', 'Tuấn Kiệt'), (10, 'Đỗ', 'Thanh Trúc'), (11, 'Dương', 'Gia Hân'),
        (12, 'Nguyễn', 'Đức Anh'), (13, 'Trần', 'Hồng Nhung'), (14, 'Lê', 'Trung Tín'),
        (15, 'Phạm', 'Diễm My'), (16, 'Hoàng', 'Hoàng Long'), (17, 'Huỳnh', 'Kim Ngân'),
        (18, 'Phan', 'Nhật Tân'), (19, 'Vũ', 'Thảo Vy'), (20, 'Đặng', 'Hữu Phước'),
        (21, 'Bùi', 'Ánh Dương'), (22, 'Đỗ', 'Chí Cường'), (23, 'Dương', 'Bảo Trân'),
        (24, 'Nguyễn', 'Thanh Phong'), (25, 'Trần', 'Ngọc Diệp'), (26, 'Lê', 'Văn Hậu'),
        (27, 'Phạm', 'Tường Vy'), (28, 'Hoàng', 'Đình Khoa'), (29, 'Huỳnh', 'Mạnh Cường')
    )
    SELECT 'student-user-' || lpad(n::text, 3, '0') AS user_id,
           'student' || lpad(n::text, 3, '0') || '@campuscore.demo' AS email,
           pool.last_name AS last_name,
           pool.first_name AS first_name
    FROM generate_series(101, 300) AS series(n)
    JOIN name_pool pool ON pool.idx = n % 30
) AS generated
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."User" existing WHERE existing."id" = generated.user_id
);

INSERT INTO academic."Student" ("id", "userId", "studentId", "curriculumId", "year", "status", "admissionDate")
SELECT generated.profile_id, generated.user_id, generated.student_number, 'curriculum-demo',
       1 + (generated.n % 3), 'ACTIVE',
       TIMESTAMPTZ '2024-09-01T00:00:00Z' + (generated.n % 3) * INTERVAL '365 days'
FROM (
    SELECT n,
           'student-profile-' || lpad(n::text, 3, '0') AS profile_id,
           'student-user-' || lpad(n::text, 3, '0') AS user_id,
           'CS-DEMO-' || lpad(n::text, 3, '0') AS student_number
    FROM generate_series(101, 300) AS series(n)
) AS generated
WHERE NOT EXISTS (
    SELECT 1 FROM academic."Student" existing WHERE existing."id" = generated.profile_id
);

INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT generated.user_role_id, generated.user_id, 'role-student'
FROM (
    SELECT 'user-role-student-' || lpad(n::text, 3, '0') AS user_role_id,
           'student-user-' || lpad(n::text, 3, '0') AS user_id
    FROM generate_series(101, 300) AS series(n)
) AS generated
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."UserRole" existing
    WHERE existing."userId" = generated.user_id AND existing."roleId" = 'role-student'
);

-- ------------------------------------------------- teaching assignments
-- Spread the generated auto sections across the new lecturers so the
-- admin/lecturer views reflect a real teaching load, while the demo lecturer
-- keeps the SE40x -01 sections used by the gradebook walkthrough.
WITH ordered AS (
    SELECT "id", row_number() OVER (ORDER BY "id")::INTEGER AS rn
    FROM academic."Section"
    WHERE "id" LIKE 'section-auto-%'
)
UPDATE academic."Section" section
SET "lecturerId" = 'lecturer-profile-' || lpad((2 + (ordered.rn % 11))::text, 3, '0')
FROM ordered
WHERE section."id" = ordered."id";

-- ------------------------------------------- current-term enrollments
-- Seven deterministic OPEN sections per new student, guarded by live capacity
-- and deduplicated per course so the active-unique indexes always hold.
WITH new_students AS (
    SELECT profile."id" AS student_profile_id,
           row_number() OVER (ORDER BY profile."id")::INTEGER AS seq
    FROM academic."Student" profile
    WHERE profile."id" LIKE 'student-profile-1%'
      AND profile."id" <> 'student-profile-100'
      AND NOT EXISTS (SELECT 1 FROM academic."Enrollment" e WHERE e."studentId" = profile."id")
), open_sections AS (
    SELECT section."id" AS section_id, section."courseId" AS course_id,
           section."semesterId" AS semester_id, course."credits" AS credits,
           section."capacity" AS capacity, section."enrolledCount" AS enrolled_count,
           row_number() OVER (ORDER BY section."id")::INTEGER AS idx,
           count(*) OVER () AS total
    FROM academic."Section" section
    JOIN academic."Course" course ON course."id" = section."courseId"
    WHERE section."semesterId" = 'semester-demo' AND section."status" = 'OPEN'
      AND section."enrolledCount" < section."capacity"
), picks AS (
    SELECT DISTINCT student_profile_id, section_id, course_id, semester_id, credits
    FROM (
        SELECT students.student_profile_id,
               sections.section_id, sections.course_id, sections.semester_id, sections.credits,
               row_number() OVER (
                   PARTITION BY students.student_profile_id, sections.course_id
                   ORDER BY k
               ) AS course_rank
        FROM new_students students
        CROSS JOIN generate_series(0, 6) AS offsets(k)
        JOIN LATERAL (
            SELECT o.section_id, o.course_id, o.semester_id, o.credits
            FROM open_sections o
            WHERE o.idx = 1 + ((students.seq * 13 + offsets.k * 29) % o.total)
        ) sections ON TRUE
    ) dedup
    WHERE course_rank = 1
)
INSERT INTO academic."Enrollment" (
    "id", "studentId", "sectionId", "semesterId", "status", "enrolledAt",
    "gradeStatus", "courseId", "roundId", "creditsSnapshot", "version"
)
SELECT 'enrollment-current-v26-' || md5(picks.student_profile_id || picks.section_id),
       picks.student_profile_id, picks.section_id, picks.semester_id, 'ENROLLED',
       CURRENT_TIMESTAMP - (mod(hashtext(picks.student_profile_id || picks.section_id), 4320) || ' minutes')::interval,
       'NOT_GRADED', picks.course_id, 'round-registration-current-demo', picks.credits, 0
FROM picks
ON CONFLICT ("id") DO NOTHING;

UPDATE academic."Section" section
SET "enrolledCount" = section."enrolledCount" + filled.count
FROM (
    SELECT "sectionId", count(*)::INTEGER AS count
    FROM academic."Enrollment"
    WHERE "id" LIKE 'enrollment-current-v26-%'
    GROUP BY "sectionId"
) filled
WHERE section."id" = filled."sectionId"
  AND section."enrolledCount" + filled.count <= section."capacity";

-- ------------------------------------------ graded history for new students
-- Reuse the V25 closed-semester pattern: the SE40x -01 sections keep their
-- weighted grade items, new students get COMPLETED/PUBLISHED enrollments with
-- deterministic midterm/final scores and component grades.
INSERT INTO academic."GradeItem" ("id", "sectionId", "name", "type", "maxScore", "weight", "gradedAt")
SELECT 'grade-history-' || section."id" || '-midterm', section."id", 'Giữa kỳ', 'MIDTERM', 10, 40, CURRENT_TIMESTAMP
FROM academic."Section" section
JOIN academic."Course" course ON course."id" = section."courseId"
WHERE section."semesterId" = 'semester-demo' AND section."sectionNumber" LIKE '%-01'
  AND course."code" IN ('SE401','SE402','SE403','SE404','SE405','SE406','SE407','SE408','SE409','SE410','SE411','SE412')
  AND NOT EXISTS (SELECT 1 FROM academic."GradeItem" item WHERE item."id" = 'grade-history-' || section."id" || '-midterm');

INSERT INTO academic."GradeItem" ("id", "sectionId", "name", "type", "maxScore", "weight", "gradedAt")
SELECT 'grade-history-' || section."id" || '-final', section."id", 'Cuối kỳ', 'FINAL', 10, 60, CURRENT_TIMESTAMP
FROM academic."Section" section
JOIN academic."Course" course ON course."id" = section."courseId"
WHERE section."semesterId" = 'semester-demo' AND section."sectionNumber" LIKE '%-01'
  AND course."code" IN ('SE401','SE402','SE403','SE404','SE405','SE406','SE407','SE408','SE409','SE410','SE411','SE412')
  AND NOT EXISTS (SELECT 1 FROM academic."GradeItem" item WHERE item."id" = 'grade-history-' || section."id" || '-final');

WITH students AS (
    SELECT profile."id" AS student_profile_id,
           regexp_replace(profile."studentId", '\D', '', 'g')::INTEGER AS student_number
    FROM academic."Student" profile
    WHERE profile."id" LIKE 'student-profile-1%' AND profile."id" <> 'student-profile-100'
), sections AS (
    SELECT section."id" AS section_id, section."courseId" AS course_id,
           course."code" AS course_code, course."credits",
           row_number() OVER (ORDER BY course."code")::INTEGER AS course_index
    FROM academic."Section" section
    JOIN academic."Course" course ON course."id" = section."courseId"
    WHERE section."semesterId" = 'semester-demo' AND section."sectionNumber" LIKE '%-01'
      AND course."code" IN ('SE401','SE402','SE403','SE404','SE405','SE406','SE407','SE408','SE409','SE410','SE411','SE412')
), scored AS (
    SELECT students.student_number, students.student_profile_id,
           sections.section_id, sections.course_id, sections.course_code, sections.credits,
           round((5.5 + (((students.student_number * 7 + sections.course_index * 3) % 40)::NUMERIC / 10)), 2) AS midterm_score,
           round((6.0 + (((students.student_number * 5 + sections.course_index * 4) % 35)::NUMERIC / 10)), 2) AS final_score
    FROM students CROSS JOIN sections
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
SELECT 'enrollment-history-v26-' || lpad(final_scores.student_number::text, 3, '0') || '-' || final_scores.course_code,
       final_scores.student_profile_id, final_scores.section_id, 'semester-history-demo',
       'COMPLETED', TIMESTAMPTZ '2026-05-20T00:00:00Z' + (final_scores.student_number * INTERVAL '1 day'),
       'PUBLISHED', final_scores.final_grade,
       CASE
           WHEN final_scores.final_grade >= 8.5 THEN 'A'
           WHEN final_scores.final_grade >= 8.0 THEN 'B+'
           WHEN final_scores.final_grade >= 7.0 THEN 'B'
           WHEN final_scores.final_grade >= 6.5 THEN 'C+'
           WHEN final_scores.final_grade >= 5.5 THEN 'C'
           WHEN final_scores.final_grade >= 4.0 THEN 'D'
           ELSE 'F'
       END,
       final_scores.course_id, 'round-registration-current-demo', final_scores.credits, 0
FROM final_scores
ON CONFLICT ("id") DO NOTHING;

INSERT INTO academic."StudentGrade" ("id", "enrollmentId", "gradeItemId", "score")
SELECT 'student-grade-history-v26-' || lpad(scored.student_number::text, 3, '0') || '-' || scored.course_code || '-' || component.grade_type,
       'enrollment-history-v26-' || lpad(scored.student_number::text, 3, '0') || '-' || scored.course_code,
       item."id", component.score
FROM (
    SELECT students.student_number, sections.section_id, sections.course_code,
           round((5.5 + (((students.student_number * 7 + sections.course_index * 3) % 40)::NUMERIC / 10)), 2) AS midterm_score,
           round((6.0 + (((students.student_number * 5 + sections.course_index * 4) % 35)::NUMERIC / 10)), 2) AS final_score
    FROM (
        SELECT profile."id" AS student_profile_id,
               regexp_replace(profile."studentId", '\D', '', 'g')::INTEGER AS student_number
        FROM academic."Student" profile
        WHERE profile."id" LIKE 'student-profile-1%' AND profile."id" <> 'student-profile-100'
    ) students
    CROSS JOIN (
        SELECT section."id" AS section_id, course."code" AS course_code,
               row_number() OVER (ORDER BY course."code")::INTEGER AS course_index
        FROM academic."Section" section
        JOIN academic."Course" course ON course."id" = section."courseId"
        WHERE section."semesterId" = 'semester-demo' AND section."sectionNumber" LIKE '%-01'
          AND course."code" IN ('SE401','SE402','SE403','SE404','SE405','SE406','SE407','SE408','SE409','SE410','SE411','SE412')
    ) sections
) scored
CROSS JOIN LATERAL (
    VALUES ('MIDTERM', scored.midterm_score), ('FINAL', scored.final_score)
) AS component(grade_type, score)
JOIN academic."GradeItem" item
  ON item."id" = 'grade-history-' || scored.section_id || '-' || lower(component.grade_type)
WHERE NOT EXISTS (
    SELECT 1 FROM academic."StudentGrade" existing
    WHERE existing."id" = 'student-grade-history-v26-' || lpad(scored.student_number::text, 3, '0') || '-' || scored.course_code || '-' || component.grade_type
);

-- ----------------------------------------------------- announcement stream
INSERT INTO engagement."Announcement"
    ("id", "title", "content", "priority", "targetRoles", "targetYears", "isGlobal",
     "publishAt", "expiresAt", "publishedBy", "semesterId", "semesterName", "courseCode", "courseName")
SELECT seed."id", seed."title", seed."content", seed."priority", seed."targetRoles", seed."targetYears", seed."isGlobal",
       CURRENT_TIMESTAMP - seed."age", CURRENT_TIMESTAMP + seed."ttl", 'admin-user',
       'semester-demo', 'Học kỳ 1 năm học 2026-2027', seed."courseCode", seed."courseName"
FROM (VALUES
    ('announcement-v26-thesis-round', 'Mở vòng đăng ký luận văn tốt nghiệp', 'Sinh viên năm cuối xem danh sách đề tài và đăng ký nhóm trước hạn; kiểm tra yêu cầu tín chỉ tích lũy.', 'HIGH', ARRAY['STUDENT']::TEXT[], ARRAY[4]::INTEGER[], FALSE, INTERVAL '3 hours', INTERVAL '150 days', NULL, NULL),
    ('announcement-v26-scholarship', 'Học bổng khuyến khích học tập đợt này', 'Sinh viên có GPA từ 3.2 trở lên và điểm rèn luyện khá xem xét hồ sơ tự động; kết quả công bố trong trung tâm thông báo.', 'HIGH', ARRAY['STUDENT']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '1 day', INTERVAL '120 days', NULL, NULL),
    ('announcement-v26-lecturer-training', 'Tập huấn công cụ nhập điểm trực tuyến', 'Giảng viên xem hướng dẫn nhập điểm thành phần và công bố điểm trên cổng trước kỳ giữa kỳ.', 'NORMAL', ARRAY['LECTURER']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '2 days', INTERVAL '90 days', NULL, NULL),
    ('announcement-v26-lab-upgrade', 'Nâng cấp phòng lab phần mềm', 'Các phòng lab đã bổ sung máy mới cho môn lập trình, kiểm thử và cơ sở dữ liệu; sinh viên đến đúng ca đã đăng ký.', 'NORMAL', ARRAY['STUDENT','LECTURER']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '3 days', INTERVAL '120 days', 'SE402', 'Cơ sở dữ liệu'),
    ('announcement-v26-career-day', 'Ngày hội việc làm khoa CNTT', 'Doanh nghiệp đối tác phỏng vấn thực tập ngay tại trường; sinh viên mang CV và bảng điểm để nộp trực tiếp.', 'NORMAL', ARRAY['STUDENT']::TEXT[], ARRAY[3,4]::INTEGER[], TRUE, INTERVAL '4 days', INTERVAL '60 days', NULL, NULL),
    ('announcement-v26-academic-warning', 'Nhắc lịch phúc khảo điểm', 'Thời hạn gửi phúc khảo là 7 ngày kể từ khi điểm được công bố trên cổng học vụ.', 'NORMAL', ARRAY['STUDENT']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '5 days', INTERVAL '90 days', NULL, NULL),
    ('announcement-v26-english-club', 'Câu lạc bộ tiếng Anh chuyên ngành', 'Sinh viên tham gia buổi sinh hoạt hằng tuần để luyện từ vựng công nghệ phục vụ học phần và luận văn.', 'LOW', ARRAY['STUDENT']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '6 days', INTERVAL '60 days', NULL, NULL),
    ('announcement-v26-research-group', 'Tuyển thành viên nhóm nghiên cứu AI', 'Giảng viên hướng dẫn tuyển sinh viên yêu thích học máy, ưu tiên đã hoàn thành môn cơ sở dữ liệu và giải thuật.', 'LOW', ARRAY['STUDENT']::TEXT[], ARRAY[3,4]::INTEGER[], TRUE, INTERVAL '7 days', INTERVAL '120 days', NULL, NULL),
    ('announcement-v26-gradebook-window', 'Kết thúc kỳ nhập điểm giữa kỳ', 'Giảng viên hoàn tất nhập điểm giữa kỳ trước mốc kết thúc để sinh viên xem đúng tiến độ học tập.', 'HIGH', ARRAY['LECTURER']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '8 days', INTERVAL '45 days', NULL, NULL),
    ('announcement-v26-parking-notice', 'Điều chỉnh gửi xe khu vực A', 'Khu vực A bảo trì; sinh viên và giảng viên gửi xe tại bãi tạm phía nhà B trong thời gian này.', 'LOW', ARRAY['STUDENT','LECTURER']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '9 days', INTERVAL '20 days', NULL, NULL)
) AS seed("id", "title", "content", "priority", "targetRoles", "targetYears", "isGlobal", "age", "ttl", "courseCode", "courseName")
WHERE NOT EXISTS (SELECT 1 FROM engagement."Announcement" existing WHERE existing."id" = seed."id");

-- ------------------------------------------- wider notification stream
INSERT INTO notifications.notification
    (id, user_id, title, message, type, link, is_read, read_at, created_at, updated_at)
SELECT seed.id, 'student-user', seed.title, seed.message, seed.type, seed.link, seed.is_read,
       CASE WHEN seed.is_read THEN CURRENT_TIMESTAMP - INTERVAL '1 hour' ELSE NULL END,
       CURRENT_TIMESTAMP - seed.age, CURRENT_TIMESTAMP - seed.age
FROM (VALUES
    ('notification-v26-thesis-open', 'Vòng đăng ký luận văn đã mở', 'Danh sách đề tài luận văn đã có trên cổng; xem đề tài phù hợp với chuyên ngành của bạn.', 'ACADEMIC', '/vi/dashboard/thesis', FALSE, INTERVAL '4 hours'),
    ('notification-v26-scholarship', 'Bạn nằm trong diện xét học bổng', 'Hồ sơ của bạn đủ điều kiện xét học bổng khuyến khích học tập kỳ này.', 'REMINDER', '/vi/dashboard', FALSE, INTERVAL '1 day'),
    ('notification-v26-career-day', 'Ngày hội việc làm sắp diễn ra', 'Đăng ký tham gia quầy phỏng vấn với các doanh nghiệp đối tác của khoa.', 'EVENT', '/vi/dashboard/announcements', FALSE, INTERVAL '2 days'),
    ('notification-v26-grade-published', 'Điểm giữa kỳ đã có cập nhật', 'Một số học phần đã công bố điểm giữa kỳ; kiểm tra trang Điểm số.', 'ACADEMIC', '/vi/dashboard/grades', TRUE, INTERVAL '3 days'),
    ('notification-v26-english-club', 'Lịch sinh hoạt câu lạc bộ tiếng Anh', 'Buổi tới thảo luận chủ đề công nghệ thông tin bằng tiếng Anh.', 'EVENT', '/vi/dashboard/announcements', TRUE, INTERVAL '5 days'),
    ('notification-v26-lab-schedule', 'Cập nhật lịch lab', 'Phòng lab phần mềm đã nâng cấp máy; lịch học không thay đổi.', 'SCHEDULE', '/vi/dashboard/schedule', TRUE, INTERVAL '6 days'),
    ('notification-v26-tuition-reminder', 'Nhắc hoàn tất học phí học kỳ', 'Hoàn tất học phí trước mốc quy định để không ảnh hưởng điểm danh.', 'REMINDER', '/vi/dashboard', FALSE, INTERVAL '7 days'),
    ('notification-v26-assistant-tips', 'Mẹo dùng trợ lý học vụ', 'Hỏi trợ lý về điều kiện tiên quyết của môn trước khi đăng ký để tránh trùng lịch.', 'ASSISTANT', '/vi/dashboard', TRUE, INTERVAL '8 days')
) AS seed(id, title, message, type, link, is_read, age)
WHERE NOT EXISTS (SELECT 1 FROM notifications.notification existing WHERE existing.id = seed.id);

INSERT INTO notifications.notification
    (id, user_id, title, message, type, link, is_read, read_at, created_at, updated_at)
SELECT seed.id, 'lecturer-user', seed.title, seed.message, seed.type, seed.link, seed.is_read,
       CASE WHEN seed.is_read THEN CURRENT_TIMESTAMP - INTERVAL '2 hours' ELSE NULL END,
       CURRENT_TIMESTAMP - seed.age, CURRENT_TIMESTAMP - seed.age
FROM (VALUES
    ('notification-v26-lect-gradebook', 'Kỳ nhập điểm giữa kỳ đang mở', 'Lớp học phần của bạn sẵn sàng nhập điểm; mở quản lý điểm để cập nhật.', 'ACADEMIC', '/vi/dashboard/lecturer/grades', FALSE, INTERVAL '5 hours'),
    ('notification-v26-lect-roster', 'Danh sách lớp có cập nhật', 'Có sinh viên mới đăng ký vào lớp học phần trong tuần này.', 'REGISTRATION', '/vi/dashboard/lecturer', FALSE, INTERVAL '2 days'),
    ('notification-v26-lect-training', 'Lịch tập huấn cổng học vụ', 'Tham gia buổi hướng dẫn nhập điểm và phê duyệt luận văn trực tuyến.', 'EVENT', '/vi/dashboard/lecturer', TRUE, INTERVAL '4 days')
) AS seed(id, title, message, type, link, is_read, age)
WHERE NOT EXISTS (SELECT 1 FROM notifications.notification existing WHERE existing.id = seed.id);
