INSERT INTO academic."User" ("id", "email", "firstName", "lastName", "status")
SELECT 'student-user', 'student@campuscore.edu', 'Demo', 'Student', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM academic."User" WHERE "id" = 'student-user');
INSERT INTO academic."User" ("id", "email", "firstName", "lastName", "status")
SELECT 'lecturer-user', 'lecturer@campuscore.edu', 'Demo', 'Lecturer', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM academic."User" WHERE "id" = 'lecturer-user');

INSERT INTO academic."Faculty" ("id", "name", "nameEn", "nameVi", "code", "description")
SELECT 'faculty-demo', 'Khoa Công nghệ thông tin', 'Faculty of Information Technology', 'Khoa Công nghệ thông tin', 'FIT', 'Course demo faculty'
WHERE NOT EXISTS (SELECT 1 FROM academic."Faculty" WHERE "id" = 'faculty-demo');
INSERT INTO academic."Department" ("id", "name", "nameEn", "nameVi", "code", "facultyId")
SELECT 'department-demo', 'Công nghệ phần mềm', 'Software Engineering', 'Công nghệ phần mềm', 'SE', 'faculty-demo'
WHERE NOT EXISTS (SELECT 1 FROM academic."Department" WHERE "id" = 'department-demo');
INSERT INTO academic."AcademicYear" ("id", "year", "startDate", "endDate", "isCurrent")
SELECT 'academic-year-demo', 2026, '2026-01-01T00:00:00Z', '2026-12-31T23:59:59Z', TRUE
WHERE NOT EXISTS (SELECT 1 FROM academic."AcademicYear" WHERE "id" = 'academic-year-demo');
INSERT INTO academic."Semester" ("id", "name", "nameEn", "nameVi", "type", "academicYearId", "startDate", "endDate", "registrationStart", "registrationEnd", "status")
SELECT 'semester-demo', 'Học kỳ 1 năm học 2026-2027', 'Semester 1 2026-2027', 'Học kỳ 1 năm học 2026-2027', 'FIRST', 'academic-year-demo', '2026-08-01T00:00:00Z', '2027-01-15T23:59:59Z', '2026-07-20T00:00:00Z', '2026-08-30T23:59:59Z', 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM academic."Semester" WHERE "id" = 'semester-demo');
INSERT INTO academic."Curriculum" ("id", "name", "nameEn", "nameVi", "code", "departmentId", "academicYearId", "totalCredits")
SELECT 'curriculum-demo', 'Kỹ thuật phần mềm', 'Software Engineering', 'Kỹ thuật phần mềm', 'SE2026', 'department-demo', 'academic-year-demo', 140
WHERE NOT EXISTS (SELECT 1 FROM academic."Curriculum" WHERE "id" = 'curriculum-demo');
INSERT INTO academic."Course" ("id", "code", "name", "nameEn", "nameVi", "credits", "departmentId", "semesterId")
SELECT 'course-java-demo', 'SE401', 'Lập trình Java nâng cao', 'Advanced Java Programming', 'Lập trình Java nâng cao', 3, 'department-demo', 'semester-demo'
WHERE NOT EXISTS (SELECT 1 FROM academic."Course" WHERE "id" = 'course-java-demo');
INSERT INTO academic."Course" ("id", "code", "name", "nameEn", "nameVi", "credits", "departmentId", "semesterId")
SELECT 'course-web-demo', 'SE402', 'Phát triển ứng dụng web', 'Web Application Development', 'Phát triển ứng dụng web', 3, 'department-demo', 'semester-demo'
WHERE NOT EXISTS (SELECT 1 FROM academic."Course" WHERE "id" = 'course-web-demo');
INSERT INTO academic."Classroom" ("id", "building", "roomNumber", "capacity", "type")
SELECT 'classroom-a101', 'A', '101', 45, 'LECTURE'
WHERE NOT EXISTS (SELECT 1 FROM academic."Classroom" WHERE "id" = 'classroom-a101');
INSERT INTO academic."Classroom" ("id", "building", "roomNumber", "capacity", "type")
SELECT 'classroom-a102', 'A', '102', 35, 'LAB'
WHERE NOT EXISTS (SELECT 1 FROM academic."Classroom" WHERE "id" = 'classroom-a102');
INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT 'lecturer-profile', 'lecturer-user', 'department-demo', 'LEC-DEMO-001', 'ThS.', 'Java and web systems'
WHERE NOT EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile');
INSERT INTO academic."Student" ("id", "userId", "studentId", "curriculumId", "year", "admissionDate")
SELECT 'student-profile', 'student-user', 'CS-DEMO-001', 'curriculum-demo', 2, '2025-09-01T00:00:00Z'
WHERE NOT EXISTS (SELECT 1 FROM academic."Student" WHERE "id" = 'student-profile');
INSERT INTO academic."Section" ("id", "sectionNumber", "courseId", "semesterId", "lecturerId", "classroomId", "capacity", "enrolledCount", "status")
SELECT 'section-java-demo', 'SE401-01', 'course-java-demo', 'semester-demo', 'lecturer-profile', 'classroom-a101', 45, 1, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM academic."Section" WHERE "id" = 'section-java-demo');
INSERT INTO academic."Section" ("id", "sectionNumber", "courseId", "semesterId", "lecturerId", "classroomId", "capacity", "enrolledCount", "status")
SELECT 'section-web-demo', 'SE402-01', 'course-web-demo', 'semester-demo', 'lecturer-profile', 'classroom-a102', 35, 0, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM academic."Section" WHERE "id" = 'section-web-demo');
INSERT INTO academic."SectionSchedule" ("id", "sectionId", "classroomId", "dayOfWeek", "startTime", "endTime")
SELECT 'schedule-java-demo', 'section-java-demo', 'classroom-a101', 2, '07:00', '09:30'
WHERE NOT EXISTS (SELECT 1 FROM academic."SectionSchedule" WHERE "id" = 'schedule-java-demo');
INSERT INTO academic."SectionSchedule" ("id", "sectionId", "classroomId", "dayOfWeek", "startTime", "endTime")
SELECT 'schedule-web-demo', 'section-web-demo', 'classroom-a102', 4, '13:00', '15:30'
WHERE NOT EXISTS (SELECT 1 FROM academic."SectionSchedule" WHERE "id" = 'schedule-web-demo');
INSERT INTO academic."Enrollment" ("id", "studentId", "sectionId", "semesterId", "status", "enrolledAt", "gradeStatus")
SELECT 'enrollment-java-demo', 'student-profile', 'section-java-demo', 'semester-demo', 'ENROLLED', CURRENT_TIMESTAMP, 'NOT_GRADED'
WHERE NOT EXISTS (SELECT 1 FROM academic."Enrollment" WHERE "id" = 'enrollment-java-demo');
INSERT INTO academic."GradeItem" ("id", "sectionId", "name", "type", "maxScore", "weight")
SELECT 'grade-java-midterm', 'section-java-demo', 'Giữa kỳ', 'MIDTERM', 10, 40
WHERE NOT EXISTS (SELECT 1 FROM academic."GradeItem" WHERE "id" = 'grade-java-midterm');
INSERT INTO academic."GradeItem" ("id", "sectionId", "name", "type", "maxScore", "weight")
SELECT 'grade-java-final', 'section-java-demo', 'Cuối kỳ', 'FINAL', 10, 60
WHERE NOT EXISTS (SELECT 1 FROM academic."GradeItem" WHERE "id" = 'grade-java-final');
INSERT INTO academic."StudentGrade" ("id", "enrollmentId", "gradeItemId", "score")
SELECT 'student-grade-java-midterm', 'enrollment-java-demo', 'grade-java-midterm', 8.5
WHERE NOT EXISTS (SELECT 1 FROM academic."StudentGrade" WHERE "id" = 'student-grade-java-midterm');
INSERT INTO academic."StudentGrade" ("id", "enrollmentId", "gradeItemId", "score")
SELECT 'student-grade-java-final', 'enrollment-java-demo', 'grade-java-final', 9.0
WHERE NOT EXISTS (SELECT 1 FROM academic."StudentGrade" WHERE "id" = 'student-grade-java-final');

INSERT INTO engagement."Announcement" ("id", "title", "content", "priority", "isGlobal", "publishAt", "publishedBy")
SELECT 'announcement-welcome', 'Thông báo đăng ký học phần', 'Sinh viên kiểm tra lịch đăng ký và số chỗ trước khi xác nhận học phần.', 'NORMAL', TRUE, CURRENT_TIMESTAMP, 'admin-user'
WHERE NOT EXISTS (SELECT 1 FROM engagement."Announcement" WHERE "id" = 'announcement-welcome');
INSERT INTO notifications.notification (id, user_id, title, message, type, is_read)
SELECT 'notification-student-welcome', 'student-user', 'Chào mừng đến CampusCore', 'Bạn có thể bắt đầu từ trang đăng ký học phần.', 'INFO', FALSE
WHERE NOT EXISTS (SELECT 1 FROM notifications.notification WHERE id = 'notification-student-welcome');
