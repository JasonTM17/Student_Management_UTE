-- Migration 20260919204500_populate_academic_operations.sql
-- Hoàn thiện dữ liệu nghiệp vụ: Đơn nâng hạn mức tín chỉ, Điểm danh và Nhật ký quản trị

-- 1. Đơn xin nâng hạn mức tín chỉ (CreditLimitApplication)
INSERT INTO academic."CreditLimitApplication" (
    "id", "studentId", "semesterId", "roundId", "requestedLimit", "reason",
    "status", "reviewedBy", "reviewedAt", "reviewerNote", "createdAt", "updatedAt", "version"
) VALUES
(
    'cla-demo-001',
    'student-profile',
    'semester-demo',
    'round-registration-current-demo',
    30,
    'Em đã hoàn thành 125 tín chỉ tích lũy với GPA 3.82/4.0. Em làm đơn này kính xin Phòng Đào tạo xem xét cho phép em nâng trần hạn mức đăng ký học phần lên 30 tín chỉ trong Học kỳ 1 năm học 2026-2027 để đăng ký Khóa luận tốt nghiệp và hoàn thành sớm chương trình đào tạo.',
    'APPROVED',
    'admin-user',
    NOW() - INTERVAL '2 days',
    'Hồ sơ sinh viên đạt điều kiện học lực Xuất sắc (GPA > 3.6, ĐRL Xuất sắc). Phòng Đào tạo đồng ý phê duyệt trần 30 tín chỉ theo quy định ngoại lệ Điều 14 Quy chế Đào tạo tín chỉ.',
    NOW() - INTERVAL '3 days',
    NOW() - INTERVAL '2 days',
    1
),
(
    'cla-demo-002',
    'student-profile-002',
    'semester-demo',
    'round-registration-current-demo',
    30,
    'Em làm đơn xin nâng hạn mức đăng ký từ 28 lên 30 tín chỉ để kịp học phần thay thế tốt nghiệp và học phần Tiếng Anh chuyên ngành.',
    'APPROVED',
    'admin-user',
    NOW() - INTERVAL '1 day',
    'Chấp thuận. Sinh viên thuộc diện tích lũy tiến độ tốt nghiệp nhanh.',
    NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '1 day',
    1
),
(
    'cla-demo-003',
    'student-profile-003',
    'semester-demo',
    'round-registration-current-demo',
    30,
    'Kính xin Phòng Đào tạo cấp phép nâng trần đăng ký lên 30 tín chỉ do cần học thêm học phần chuyên ngành tự chọn và hoàn thành tiến độ đào tạo.',
    'PENDING',
    NULL,
    NULL,
    NULL,
    NOW() - INTERVAL '12 hours',
    NOW() - INTERVAL '12 hours',
    0
)
ON CONFLICT ("id") DO UPDATE SET
    "requestedLimit" = EXCLUDED."requestedLimit",
    "reason" = EXCLUDED."reason",
    "status" = EXCLUDED."status",
    "reviewedBy" = EXCLUDED."reviewedBy",
    "reviewedAt" = EXCLUDED."reviewedAt",
    "reviewerNote" = EXCLUDED."reviewerNote";

-- 2. Điểm danh sinh viên (Attendance)
INSERT INTO academic."Attendance" (
    "id", "studentId", "sectionId", "date", "status", "notes", "createdAt", "updatedAt"
) VALUES
(
    'att-demo-cloud-01',
    'student-profile',
    'section-cloud-demo',
    NOW() - INTERVAL '14 days',
    'PRESENT',
    'Tham gia đầy đủ buổi học Giới thiệu kiến trúc Điện toán đám mây & AWS Core Services',
    NOW() - INTERVAL '14 days',
    NOW() - INTERVAL '14 days'
),
(
    'att-demo-cloud-02',
    'student-profile',
    'section-cloud-demo',
    NOW() - INTERVAL '7 days',
    'PRESENT',
    'Thực hành cấu hình VPC, Subnet và triển khai EC2 Cluster',
    NOW() - INTERVAL '7 days',
    NOW() - INTERVAL '7 days'
),
(
    'att-demo-algo-01',
    'student-profile',
    'section-algorithms-demo',
    NOW() - INTERVAL '12 days',
    'PRESENT',
    'Thuyết trình thuật toán đồ thị và Dijkstra cải tiến',
    NOW() - INTERVAL '12 days',
    NOW() - INTERVAL '12 days'
),
(
    'att-demo-algo-02',
    'student-profile',
    'section-algorithms-demo',
    NOW() - INTERVAL '5 days',
    'PRESENT',
    'Làm bài kiểm tra thực hành giải thuật Quy hoạch động',
    NOW() - INTERVAL '5 days',
    NOW() - INTERVAL '5 days'
),
(
    'att-demo-db-01',
    'student-profile',
    'section-database-demo',
    NOW() - INTERVAL '10 days',
    'PRESENT',
    'Phân tích chuẩn hóa cơ sở dữ liệu BCNF và chỉ mục B-Tree',
    NOW() - INTERVAL '10 days',
    NOW() - INTERVAL '10 days'
),
(
    'att-demo-arch-01',
    'student-profile',
    'section-architecture-demo',
    NOW() - INTERVAL '8 days',
    'PRESENT',
    'Thiết kế kiến trúc hướng sự kiện Event-Driven Architecture với Apache Kafka',
    NOW() - INTERVAL '8 days',
    NOW() - INTERVAL '8 days'
),
(
    'att-demo-java-01',
    'student-profile',
    'section-java-demo',
    NOW() - INTERVAL '9 days',
    'PRESENT',
    'Xây dựng ứng dụng đa luồng Virtual Threads trên Java 25',
    NOW() - INTERVAL '9 days',
    NOW() - INTERVAL '9 days'
)
ON CONFLICT ("id") DO UPDATE SET
    "status" = EXCLUDED."status",
    "notes" = EXCLUDED."notes";

-- 3. Nhật ký kiểm toán quản trị (AdminAudit)
INSERT INTO campuscore_audit."AdminAudit" (
    "id", "actorId", "actorLabel", "action", "entityType", "entityId", "summary",
    "beforeState", "afterState", "createdAt"
) VALUES
(
    'audit-demo-001',
    'admin-user',
    'Quản trị viên Học vụ (admin@campuscore.edu)',
    'OPEN_REGISTRATION_ROUND',
    'RegistrationRound',
    'round-registration-current-demo',
    'Mở đợt đăng ký học phần chính thức Học kỳ 1 năm học 2026-2027 cho toàn thể sinh viên các khóa',
    '{"status": "SCHEDULED", "isOpen": false}',
    '{"status": "OPEN", "isOpen": true, "maxCredits": 28, "allowOverloadApplication": true}',
    NOW() - INTERVAL '10 days'
),
(
    'audit-demo-002',
    'admin-user',
    'Quản trị viên Học vụ (admin@campuscore.edu)',
    'APPROVE_CREDIT_LIMIT',
    'CreditLimitApplication',
    'cla-demo-001',
    'Phê duyệt nâng hạn mức tín chỉ đặc cách lên 30 tín chỉ cho sinh viên Nguyễn Tiến Sơn (MSSV: 24110054)',
    '{"status": "PENDING_REVIEW", "requestedLimit": 30}',
    '{"status": "APPROVED", "approvedLimit": 30, "reviewerNote": "Đạt điều kiện GPA Xuất sắc 3.82"}',
    NOW() - INTERVAL '2 days'
),
(
    'audit-demo-003',
    'admin-user',
    'Ban Biên tập Website (admin@campuscore.edu)',
    'PUBLISH_EDITORIAL_ARTICLE',
    'Announcement',
    'announcement-semiconductor-cleanroom',
    'Công bố bài viết tiêu biểu: Khánh thành Phòng thí nghiệm Bán dẫn & Vi mạch Cleanroom chuẩn quốc tế tại HCM-UTE',
    '{"status": "PENDING_REVIEW"}',
    '{"status": "PUBLISHED", "featuredOrder": 1, "hasGallery": true, "attachmentCount": 1}',
    NOW() - INTERVAL '9 days'
),
(
    'audit-demo-004',
    'admin-user',
    'Ban Quản lý Luận văn Khoa CNTT',
    'CONFIGURE_THESIS_DEFENSE',
    'ThesisRound',
    '22222222-2222-2222-2222-222222222101',
    'Thiết lập hội đồng đánh giá và lịch bảo vệ Khóa luận tốt nghiệp đợt 2 cho 12 nhóm nghiên cứu',
    '{"councilsFormed": 0, "status": "IN_PROGRESS"}',
    '{"councilsFormed": 8, "status": "DEFENSE_SCHEDULED", "topicsAssigned": 16}',
    NOW() - INTERVAL '6 days'
),
(
    'audit-demo-005',
    'admin-user',
    'Phòng Khảo thí & Đảm bảo Chất lượng',
    'SYNC_STUDENT_CONDUCT_SCORES',
    'ConductSemesterScore',
    'semester-history-demo',
    'Hoàn tất đối soát và khóa sổ điểm rèn luyện toàn trường Học kỳ 2 năm học 2025-2026',
    '{"auditState": "UNLOCKED"}',
    '{"auditState": "LOCKED", "totalScoresProjected": 678, "avgScore": 84.5}',
    NOW() - INTERVAL '15 days'
)
ON CONFLICT ("id") DO NOTHING;
