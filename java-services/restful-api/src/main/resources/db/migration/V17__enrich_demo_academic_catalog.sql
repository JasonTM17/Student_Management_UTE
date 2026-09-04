-- Rich synthetic catalog for local/demo environments.  All rows are stable,
-- deterministic and idempotent so a rebuild never duplicates academic data.

INSERT INTO academic."Course" ("id", "code", "name", "nameEn", "nameVi", "description", "descriptionEn", "descriptionVi", "credits", "departmentId", "semesterId")
SELECT seed."id", seed."code", seed."name", seed."nameEn", seed."nameVi", seed."description", seed."descriptionEn", seed."descriptionVi", seed."credits", 'department-demo', 'semester-demo'
FROM (VALUES
    ('course-algorithms-demo', 'SE403', 'Cấu trúc dữ liệu và giải thuật', 'Data Structures and Algorithms', 'Cấu trúc dữ liệu và giải thuật', 'Phân tích độ phức tạp, cấu trúc dữ liệu tuyến tính và cây, đồ thị, cùng các kỹ thuật thiết kế giải thuật.', 'Complexity analysis, linear and tree structures, graphs, and algorithm design techniques.', 'Phân tích độ phức tạp, cấu trúc dữ liệu tuyến tính và cây, đồ thị, cùng các kỹ thuật thiết kế giải thuật.', 4),
    ('course-database-demo', 'SE404', 'Cơ sở dữ liệu nâng cao', 'Advanced Database Systems', 'Cơ sở dữ liệu nâng cao', 'Thiết kế lược đồ, chỉ mục, giao dịch, tối ưu truy vấn và thực hành PostgreSQL cho hệ thống nhiều người dùng.', 'Schema design, indexing, transactions, query optimization, and PostgreSQL practice for multi-user systems.', 'Thiết kế lược đồ, chỉ mục, giao dịch, tối ưu truy vấn và thực hành PostgreSQL cho hệ thống nhiều người dùng.', 3),
    ('course-software-architecture-demo', 'SE405', 'Kiến trúc phần mềm', 'Software Architecture', 'Kiến trúc phần mềm', 'Mẫu kiến trúc, phân ranh giới module, thiết kế API và đánh giá chất lượng cho sản phẩm phần mềm.', 'Architectural patterns, module boundaries, API design, and quality evaluation for software products.', 'Mẫu kiến trúc, phân ranh giới module, thiết kế API và đánh giá chất lượng cho sản phẩm phần mềm.', 3),
    ('course-testing-demo', 'SE406', 'Kiểm thử và đảm bảo chất lượng', 'Software Testing and Quality Assurance', 'Kiểm thử và đảm bảo chất lượng', 'Từ kiểm thử đơn vị đến kiểm thử tích hợp, kiểm thử hồi quy, tự động hóa và quản lý chất lượng phát hành.', 'Unit and integration testing, regression testing, automation, and release quality management.', 'Từ kiểm thử đơn vị đến kiểm thử tích hợp, kiểm thử hồi quy, tự động hóa và quản lý chất lượng phát hành.', 3),
    ('course-devops-demo', 'SE407', 'DevOps và triển khai liên tục', 'DevOps and Continuous Delivery', 'DevOps và triển khai liên tục', 'Quản lý cấu hình, container, pipeline CI/CD, quan sát hệ thống và chiến lược khôi phục an toàn.', 'Configuration management, containers, CI/CD pipelines, observability, and safe recovery strategies.', 'Quản lý cấu hình, container, pipeline CI/CD, quan sát hệ thống và chiến lược khôi phục an toàn.', 3),
    ('course-cloud-demo', 'SE408', 'Điện toán đám mây', 'Cloud Computing', 'Điện toán đám mây', 'Mô hình dịch vụ đám mây, thiết kế có khả năng mở rộng, bảo mật, chi phí và độ tin cậy vận hành.', 'Cloud service models, scalable design, security, cost, and operational reliability.', 'Mô hình dịch vụ đám mây, thiết kế có khả năng mở rộng, bảo mật, chi phí và độ tin cậy vận hành.', 3),
    ('course-security-demo', 'SE409', 'An toàn thông tin ứng dụng', 'Application Security', 'An toàn thông tin ứng dụng', 'Nhận diện lỗ hổng web, xác thực, phân quyền, bảo vệ dữ liệu và thực hành phòng thủ theo OWASP.', 'Web vulnerability detection, authentication, authorization, data protection, and OWASP-aligned defense.', 'Nhận diện lỗ hổng web, xác thực, phân quyền, bảo vệ dữ liệu và thực hành phòng thủ theo OWASP.', 3),
    ('course-mobile-demo', 'SE410', 'Phát triển ứng dụng di động', 'Mobile Application Development', 'Phát triển ứng dụng di động', 'Thiết kế trải nghiệm mobile, quản lý trạng thái, kết nối API và kiểm thử ứng dụng đa nền tảng.', 'Mobile UX, state management, API integration, and cross-platform application testing.', 'Thiết kế trải nghiệm mobile, quản lý trạng thái, kết nối API và kiểm thử ứng dụng đa nền tảng.', 3),
    ('course-ai-demo', 'SE411', 'Nhập môn trí tuệ nhân tạo', 'Introduction to Artificial Intelligence', 'Nhập môn trí tuệ nhân tạo', 'Các khái niệm học máy, biểu diễn tri thức, đánh giá mô hình và nguyên tắc sử dụng AI có trách nhiệm.', 'Machine learning concepts, knowledge representation, model evaluation, and responsible AI principles.', 'Các khái niệm học máy, biểu diễn tri thức, đánh giá mô hình và nguyên tắc sử dụng AI có trách nhiệm.', 3),
    ('course-project-demo', 'SE412', 'Đồ án chuyên ngành', 'Software Engineering Capstone', 'Đồ án chuyên ngành', 'Xây dựng sản phẩm theo vòng đời phát triển đầy đủ, gồm yêu cầu, thiết kế, triển khai, kiểm thử và trình bày.', 'Build a product through the full lifecycle: requirements, design, delivery, testing, and presentation.', 'Xây dựng sản phẩm theo vòng đời phát triển đầy đủ, gồm yêu cầu, thiết kế, triển khai, kiểm thử và trình bày.', 4)
) AS seed("id", "code", "name", "nameEn", "nameVi", "description", "descriptionEn", "descriptionVi", "credits")
WHERE NOT EXISTS (SELECT 1 FROM academic."Course" existing WHERE existing."id" = seed."id");

INSERT INTO academic."CurriculumCourse" ("id", "curriculumId", "courseId", "year", "semester", "isMandatory")
SELECT seed."id", 'curriculum-demo', seed."courseId", seed."year", seed."semester", seed."isMandatory"
FROM (VALUES
    ('curriculum-course-se403', 'course-algorithms-demo', 2, 1, TRUE),
    ('curriculum-course-se404', 'course-database-demo', 2, 2, TRUE),
    ('curriculum-course-se405', 'course-software-architecture-demo', 3, 1, TRUE),
    ('curriculum-course-se406', 'course-testing-demo', 3, 1, TRUE),
    ('curriculum-course-se407', 'course-devops-demo', 3, 2, FALSE),
    ('curriculum-course-se408', 'course-cloud-demo', 3, 2, FALSE),
    ('curriculum-course-se409', 'course-security-demo', 3, 2, TRUE),
    ('curriculum-course-se410', 'course-mobile-demo', 3, 1, FALSE),
    ('curriculum-course-se411', 'course-ai-demo', 3, 1, FALSE),
    ('curriculum-course-se412', 'course-project-demo', 4, 1, TRUE)
) AS seed("id", "courseId", "year", "semester", "isMandatory")
WHERE NOT EXISTS (SELECT 1 FROM academic."CurriculumCourse" existing WHERE existing."id" = seed."id");

INSERT INTO academic."Classroom" ("id", "building", "roomNumber", "capacity", "type")
SELECT seed."id", 'A', seed."roomNumber", seed."capacity", seed."type"
FROM (VALUES
    ('classroom-a103', '103', 45, 'LECTURE'), ('classroom-a104', '104', 40, 'LECTURE'),
    ('classroom-a105', '105', 36, 'LAB'), ('classroom-a106', '106', 36, 'LAB'),
    ('classroom-a107', '107', 50, 'LECTURE'), ('classroom-a108', '108', 45, 'LECTURE'),
    ('classroom-a109', '109', 40, 'LAB'), ('classroom-a110', '110', 60, 'LECTURE')
) AS seed("id", "roomNumber", "capacity", "type")
WHERE NOT EXISTS (SELECT 1 FROM academic."Classroom" existing WHERE existing."id" = seed."id");

INSERT INTO academic."Section" ("id", "sectionNumber", "courseId", "semesterId", "lecturerId", "classroomId", "capacity", "enrolledCount", "status")
SELECT seed."id", seed."sectionNumber", seed."courseId", 'semester-demo', 'lecturer-profile', seed."classroomId", seed."capacity", 0, 'OPEN'
FROM (VALUES
    ('section-algorithms-demo', 'SE403-01', 'course-algorithms-demo', 'classroom-a103', 45),
    ('section-database-demo', 'SE404-01', 'course-database-demo', 'classroom-a104', 40),
    ('section-architecture-demo', 'SE405-01', 'course-software-architecture-demo', 'classroom-a105', 36),
    ('section-testing-demo', 'SE406-01', 'course-testing-demo', 'classroom-a106', 36),
    ('section-devops-demo', 'SE407-01', 'course-devops-demo', 'classroom-a107', 50),
    ('section-cloud-demo', 'SE408-01', 'course-cloud-demo', 'classroom-a108', 45),
    ('section-security-demo', 'SE409-01', 'course-security-demo', 'classroom-a109', 40),
    ('section-mobile-demo', 'SE410-01', 'course-mobile-demo', 'classroom-a110', 60),
    ('section-ai-demo', 'SE411-01', 'course-ai-demo', 'classroom-a107', 50),
    ('section-project-demo', 'SE412-01', 'course-project-demo', 'classroom-a108', 45),
    ('section-database-demo-02', 'SE404-02', 'course-database-demo', 'classroom-a109', 40),
    ('section-testing-demo-02', 'SE406-02', 'course-testing-demo', 'classroom-a105', 36),
    ('section-security-demo-02', 'SE409-02', 'course-security-demo', 'classroom-a106', 36),
    ('section-mobile-demo-02', 'SE410-02', 'course-mobile-demo', 'classroom-a104', 40)
) AS seed("id", "sectionNumber", "courseId", "classroomId", "capacity")
WHERE NOT EXISTS (SELECT 1 FROM academic."Section" existing WHERE existing."id" = seed."id");

INSERT INTO academic."SectionSchedule" ("id", "sectionId", "classroomId", "dayOfWeek", "startTime", "endTime")
SELECT seed."id", seed."sectionId", seed."classroomId", seed."dayOfWeek", seed."startTime", seed."endTime"
FROM (VALUES
    ('schedule-se403', 'section-algorithms-demo', 'classroom-a103', 2, '09:45', '11:45'),
    ('schedule-se404', 'section-database-demo', 'classroom-a104', 3, '07:00', '09:30'),
    ('schedule-se405', 'section-architecture-demo', 'classroom-a105', 4, '07:00', '09:30'),
    ('schedule-se406', 'section-testing-demo', 'classroom-a106', 5, '07:00', '09:30'),
    ('schedule-se407', 'section-devops-demo', 'classroom-a107', 6, '07:00', '09:30'),
    ('schedule-se408', 'section-cloud-demo', 'classroom-a108', 7, '07:00', '09:30'),
    ('schedule-se409', 'section-security-demo', 'classroom-a109', 2, '13:00', '15:30'),
    ('schedule-se410', 'section-mobile-demo', 'classroom-a110', 3, '13:00', '15:30'),
    ('schedule-se411', 'section-ai-demo', 'classroom-a107', 4, '13:00', '15:30'),
    ('schedule-se412', 'section-project-demo', 'classroom-a108', 5, '13:00', '16:00'),
    ('schedule-se404-02', 'section-database-demo-02', 'classroom-a109', 6, '13:00', '15:30'),
    ('schedule-se406-02', 'section-testing-demo-02', 'classroom-a105', 7, '13:00', '15:30'),
    ('schedule-se409-02', 'section-security-demo-02', 'classroom-a106', 3, '09:45', '12:15'),
    ('schedule-se410-02', 'section-mobile-demo-02', 'classroom-a104', 4, '09:45', '12:15')
) AS seed("id", "sectionId", "classroomId", "dayOfWeek", "startTime", "endTime")
WHERE NOT EXISTS (SELECT 1 FROM academic."SectionSchedule" existing WHERE existing."id" = seed."id");

INSERT INTO academic."GradeItem" ("id", "sectionId", "name", "type", "maxScore", "weight")
SELECT seed."id", seed."sectionId", seed."name", seed."type", 10, seed."weight"
FROM (VALUES
    ('grade-se403-midterm', 'section-algorithms-demo', 'Giữa kỳ', 'MIDTERM', 40), ('grade-se403-final', 'section-algorithms-demo', 'Cuối kỳ', 'FINAL', 60),
    ('grade-se404-midterm', 'section-database-demo', 'Giữa kỳ', 'MIDTERM', 40), ('grade-se404-final', 'section-database-demo', 'Cuối kỳ', 'FINAL', 60),
    ('grade-se405-midterm', 'section-architecture-demo', 'Giữa kỳ', 'MIDTERM', 40), ('grade-se405-final', 'section-architecture-demo', 'Cuối kỳ', 'FINAL', 60),
    ('grade-se406-midterm', 'section-testing-demo', 'Giữa kỳ', 'MIDTERM', 40), ('grade-se406-final', 'section-testing-demo', 'Cuối kỳ', 'FINAL', 60),
    ('grade-se407-midterm', 'section-devops-demo', 'Giữa kỳ', 'MIDTERM', 40), ('grade-se407-final', 'section-devops-demo', 'Cuối kỳ', 'FINAL', 60),
    ('grade-se408-midterm', 'section-cloud-demo', 'Giữa kỳ', 'MIDTERM', 40), ('grade-se408-final', 'section-cloud-demo', 'Cuối kỳ', 'FINAL', 60),
    ('grade-se409-midterm', 'section-security-demo', 'Giữa kỳ', 'MIDTERM', 40), ('grade-se409-final', 'section-security-demo', 'Cuối kỳ', 'FINAL', 60),
    ('grade-se410-midterm', 'section-mobile-demo', 'Giữa kỳ', 'MIDTERM', 40), ('grade-se410-final', 'section-mobile-demo', 'Cuối kỳ', 'FINAL', 60),
    ('grade-se411-midterm', 'section-ai-demo', 'Giữa kỳ', 'MIDTERM', 40), ('grade-se411-final', 'section-ai-demo', 'Cuối kỳ', 'FINAL', 60),
    ('grade-se412-midterm', 'section-project-demo', 'Giữa kỳ', 'MIDTERM', 40), ('grade-se412-final', 'section-project-demo', 'Cuối kỳ', 'FINAL', 60)
) AS seed("id", "sectionId", "name", "type", "weight")
WHERE NOT EXISTS (SELECT 1 FROM academic."GradeItem" existing WHERE existing."id" = seed."id");

-- Keep one active registration round even when the original seeded calendar
-- has passed, while retaining the historical round for auditability.
INSERT INTO academic."RegistrationRound" ("id", "semesterId", "name", "kind", "status", "windowStart", "windowEnd", "creditLimit")
SELECT 'round-registration-current-demo', 'semester-demo', 'Course registration - current demo', 'REGISTRATION', 'OPEN', CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP + INTERVAL '120 days', 28
WHERE NOT EXISTS (SELECT 1 FROM academic."RegistrationRound" WHERE "id" = 'round-registration-current-demo');

UPDATE academic."Enrollment"
SET "roundId" = 'round-registration-current-demo'
WHERE "semesterId" = 'semester-demo' AND "roundId" = 'round-registration-demo';

INSERT INTO engagement."Announcement" ("id", "title", "content", "priority", "isGlobal", "publishAt", "publishedBy", "semesterId", "semesterName", "courseCode", "courseName")
SELECT seed."id", seed."title", seed."content", 'NORMAL', TRUE, CURRENT_TIMESTAMP, 'admin-user', 'semester-demo', 'Học kỳ 1 năm học 2026-2027', seed."courseCode", seed."courseName"
FROM (VALUES
    ('announcement-catalog-enriched', 'Danh mục học phần học kỳ 1 đã cập nhật', 'Danh mục hiện có các học phần SE403-SE412 với mô tả, tín chỉ, lịch học và số chỗ để sinh viên tra cứu trước khi đăng ký.', 'SE403', 'Cấu trúc dữ liệu và giải thuật'),
    ('announcement-registration-guide', 'Hướng dẫn chọn lớp học phần', 'Kiểm tra lịch học, số chỗ còn lại và cảnh báo trùng lịch trước khi xác nhận. Trợ lý CampusCore có thể giải thích nội dung học phần.', 'SE404', 'Cơ sở dữ liệu nâng cao'),
    ('announcement-ai-rag', 'Trợ lý học vụ đã bổ sung dữ liệu', 'Kho tri thức song ngữ đang được cập nhật theo bản phát hành có kiểm duyệt; câu trả lời sẽ kèm nguồn để dễ kiểm tra.', NULL, NULL)
) AS seed("id", "title", "content", "courseCode", "courseName")
WHERE NOT EXISTS (SELECT 1 FROM engagement."Announcement" existing WHERE existing."id" = seed."id");
