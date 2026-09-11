-- 1. Correct role scoping: Student-only announcements (isGlobal = FALSE, targetRoles = ['STUDENT'])
UPDATE engagement."Announcement"
SET "isGlobal" = FALSE, "targetRoles" = ARRAY['STUDENT']::text[]
WHERE id IN (
  'announcement-ute-tuition-payment-notice',
  'announcement-v26-scholarship',
  'announcement-v32-exchange-prog',
  'announcement-ute-english-it-exit-benchmark',
  'announcement-registration-window',
  'announcement-add-drop-window',
  'announcement-v26-academic-warning',
  'announcement-v32-job-fair',
  'announcement-v26-career-day',
  'announcement-classroom-a101',
  'announcement-course-descriptions',
  'announcement-v26-english-club',
  'announcement-prerequisite-check',
  'announcement-v26-research-group',
  'announcement-grade-policy',
  'announcement-library-resources'
);

-- 2. Correct role scoping: Lecturer-only announcements (isGlobal = FALSE, targetRoles = ['LECTURER'])
UPDATE engagement."Announcement"
SET "isGlobal" = FALSE, "targetRoles" = ARRAY['LECTURER']::text[]
WHERE id IN (
  'announcement-v26-lecturer-training',
  'announcement-v26-gradebook-window'
);

-- 3. Correct role scoping: Shared announcements (isGlobal = TRUE, targetRoles = ['STUDENT', 'LECTURER'])
UPDATE engagement."Announcement"
SET "isGlobal" = TRUE, "targetRoles" = ARRAY['STUDENT', 'LECTURER']::text[]
WHERE id IN (
  'announcement-v32-ute-research',
  'announcement-v32-smart-campus',
  'announcement-ute-career-fair-tech-2026',
  'announcement-ute-exam-regulations-schedule',
  'announcement-ute-bigdata-ai-center',
  'announcement-ute-system-upgrade-maintenance',
  '0967096b-c6fa-46e6-a724-1221059b659e',
  'announcement-catalog-enriched',
  'announcement-ai-rag',
  'announcement-registration-guide',
  'announcement-welcome',
  'announcement-ute-scholarship-dr-criteria',
  'announcement-ute-academic-warning-counseling',
  'announcement-v26-lab-upgrade',
  'announcement-schedule-published',
  'announcement-ute-student-scientific-research-awards',
  'announcement-ute-digital-transcript-signature',
  'announcement-ute-ojt-internship-semester1',
  'announcement-ute-thesis-registration-fall',
  'announcement-exam-preparation',
  'announcement-v26-parking-notice',
  'announcement-assistant-citations',
  'announcement-maintenance-window',
  'announcement-support-channel',
  'announcement-ute-course-reg-official'
);

-- 4. Insert 3 new rich Lecturer-specific announcements
INSERT INTO engagement."Announcement" (
  id, title, content, priority, "targetRoles", "targetYears", "isGlobal",
  "publishAt", "publishedBy", "semesterId", "semesterName", "createdAt", "updatedAt", version
) VALUES
(
  'announcement-ute-lecturer-exam-proctoring',
  'Kế hoạch phân công Cán bộ coi thi và thời hạn nộp Đề thi trích lục Học kỳ 1 năm học 2026-2027',
  '<p>Phòng Khảo thí & Đảm bảo Chất lượng thông báo đến Quý Thầy/Cô kế hoạch công tác khảo thí Học kỳ 1:</p>
<ul>
  <li><strong>Thời hạn nộp Đề thi trích lục:</strong> Quý Thầy/Cô phụ trách học phần hoàn tất nộp 02 đề thi trích lục (kèm đáp án, biểu điểm và ma trận đề thi theo chuẩn đầu ra CLO) trước 17:00 ngày 20/11/2026 tại Văn phòng Phòng Khảo thí (Phòng A1-201).</li>
  <li><strong>Lịch phân công Cán bộ coi thi:</strong> Danh sách phân ca coi thi chính thức cho từng giảng viên đã được cập nhật chi tiết trên Cổng thông tin Giảng viên và gửi về Văn phòng các Khoa/Bộ môn.</li>
  <li><strong>Quy định phòng thi:</strong> Giảng viên có mặt tại Phòng Hội đồng thi trước giờ phát đề 20 phút để nhận túi bài thi, danh sách thí sinh và thẻ cán bộ coi thi.</li>
</ul>',
  'HIGH',
  ARRAY['LECTURER']::text[],
  ARRAY[]::integer[],
  FALSE,
  '2026-09-08 09:00:00+07',
  'Phòng Khảo Thí & Đảm Bảo Chất Lượng',
  'semester-demo',
  'Học kỳ 1 năm học 2026-2027',
  '2026-09-08 09:00:00+07',
  '2026-09-08 09:00:00+07',
  1
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title,
  content = EXCLUDED.content,
  priority = EXCLUDED.priority,
  "targetRoles" = EXCLUDED."targetRoles",
  "isGlobal" = EXCLUDED."isGlobal",
  "publishedBy" = EXCLUDED."publishedBy";

INSERT INTO engagement."Announcement" (
  id, title, content, priority, "targetRoles", "targetYears", "isGlobal",
  "publishAt", "publishedBy", "semesterId", "semesterName", "createdAt", "updatedAt", version
) VALUES
(
  'announcement-ute-lecturer-syllabus-review',
  'Hướng dẫn rà soát và cập nhật Đề cương chi tiết học phần (Course Syllabus) theo chuẩn ABET',
  '<p>Ban Chủ nhiệm Khoa Công nghệ Thông tin phối hợp cùng Phòng Đào tạo đề nghị Quý Thầy/Cô phụ trách học phần thực hiện công tác rà soát chuyên môn:</p>
<ul>
  <li><strong>Chuẩn hóa mục tiêu học phần:</strong> Đối sánh và ánh xạ ma trận chuẩn đầu ra học phần (CLO) tương thích với Chuẩn đầu ra chương trình đào tạo (PLO) theo tiêu chuẩn kiểm định quốc tế ABET.</li>
  <li><strong>Cập nhật học liệu điện tử:</strong> Bổ sung danh mục giáo trình tham khảo xuất bản từ năm 2022 trở lại đây và tích hợp đường dẫn bài giảng số lên hệ thống Canvas/LMS của Trường.</li>
  <li><strong>Thời hạn hoàn thành:</strong> Nộp bản mềm Đề cương chi tiết đã có ý kiến phê duyệt của Trưởng Bộ môn qua Cổng học vụ trước ngày 25/09/2026.</li>
</ul>',
  'NORMAL',
  ARRAY['LECTURER']::text[],
  ARRAY[]::integer[],
  FALSE,
  '2026-09-07 14:00:00+07',
  'Khoa Công nghệ Thông tin & Phòng Đào Tạo',
  'semester-demo',
  'Học kỳ 1 năm học 2026-2027',
  '2026-09-07 14:00:00+07',
  '2026-09-07 14:00:00+07',
  1
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title,
  content = EXCLUDED.content,
  priority = EXCLUDED.priority,
  "targetRoles" = EXCLUDED."targetRoles",
  "isGlobal" = EXCLUDED."isGlobal",
  "publishedBy" = EXCLUDED."publishedBy";

INSERT INTO engagement."Announcement" (
  id, title, content, priority, "targetRoles", "targetYears", "isGlobal",
  "publishAt", "publishedBy", "semesterId", "semesterName", "createdAt", "updatedAt", version
) VALUES
(
  'announcement-ute-lecturer-research-norm',
  'Kế hoạch nghiệm thu đề tài NCKH cấp Trường và đăng ký định mức giờ chuẩn Giảng viên năm học 2026-2027',
  '<p>Khoa Công nghệ Thông tin phối hợp cùng Phòng Đào tạo thông báo đến Quý Thầy/Cô cán bộ giảng dạy:</p>
<ul>
  <li><strong>Kê khai định mức giờ giảng & NCKH:</strong> Quý Thầy/Cô hoàn thành bản kê khai kế hoạch giảng dạy, hướng dẫn đồ án và nghiên cứu khoa học năm học 2026-2027 trên Cổng quản trị viên chức trước ngày 30/10/2026.</li>
  <li><strong>Chính sách khen thưởng bài báo khoa học:</strong> Nhà trường tiếp tục áp dụng chính sách khen thưởng đột xuất cho các công trình công bố trên các tạp chí quốc tế uy tín thuộc danh mục Web of Science (Q1, Q2) và Scopus.</li>
  <li><strong>Hỗ trợ kinh phí nghiên cứu:</strong> Đăng ký chủ trì đề tài NCKH cấp Trường trọng điểm ưu tiên các hướng: Trí tuệ nhân tạo (AI), Dữ liệu lớn (Big Data), Thiết kế vi mạch bán dẫn và Chuyển đổi số giáo dục.</li>
</ul>',
  'NORMAL',
  ARRAY['LECTURER']::text[],
  ARRAY[]::integer[],
  FALSE,
  '2026-09-06 10:30:00+07',
  'Khoa Công nghệ Thông tin & Phòng Đào Tạo',
  'semester-demo',
  'Học kỳ 1 năm học 2026-2027',
  '2026-09-06 10:30:00+07',
  '2026-09-06 10:30:00+07',
  1
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title,
  content = EXCLUDED.content,
  priority = EXCLUDED.priority,
  "targetRoles" = EXCLUDED."targetRoles",
  "isGlobal" = EXCLUDED."isGlobal",
  "publishedBy" = EXCLUDED."publishedBy";