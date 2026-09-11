-- V34__student_conduct_points_drl.sql
-- Purpose: Support official Student Conduct / Training Points ("Điểm rèn luyện" - DRL)
-- adhering to the Ministry of Education & Training (MOET) and HCMUTE standards (5 evaluation criteria, max 100 points).

CREATE TABLE IF NOT EXISTS academic.conduct_semester_score (
    id VARCHAR(120) PRIMARY KEY,
    student_id VARCHAR(120) NOT NULL REFERENCES academic."Student" ("id") ON DELETE CASCADE,
    semester_id VARCHAR(120) NOT NULL REFERENCES academic."Semester" ("id") ON DELETE CASCADE,
    criteria1_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    criteria2_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    criteria3_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    criteria4_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    criteria5_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    total_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    classification VARCHAR(32) NOT NULL,
    classification_vi VARCHAR(60) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
    evaluator_name VARCHAR(160),
    evaluated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT conduct_student_semester_unique UNIQUE (student_id, semester_id)
);

CREATE TABLE IF NOT EXISTS academic.conduct_activity (
    id VARCHAR(120) PRIMARY KEY,
    student_id VARCHAR(120) NOT NULL REFERENCES academic."Student" ("id") ON DELETE CASCADE,
    semester_id VARCHAR(120) NOT NULL REFERENCES academic."Semester" ("id") ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(120) NOT NULL,
    points NUMERIC(4,1) NOT NULL,
    activity_date DATE NOT NULL,
    organizer VARCHAR(180) NOT NULL,
    certificate_url VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- SEED DRL FOR DEMO STUDENT (student-profile, Nguyễn Tiến Sơn - MSSV 24110054)
-- 1. Current Semester (Học kỳ 1 2026-2027) - Điểm: 88.0 (Tốt)
INSERT INTO academic.conduct_semester_score (
    id, student_id, semester_id, criteria1_score, criteria2_score, criteria3_score,
    criteria4_score, criteria5_score, total_score, classification, classification_vi,
    status, evaluator_name
)
SELECT 'conduct-score-demo-current', 'student-profile', 'semester-demo',
       18.0, 24.0, 17.0, 22.0, 7.0, 88.0, 'TOT', 'Tốt', 'APPROVED', 'Hội đồng đánh giá rèn luyện Khoa CNTT'
WHERE NOT EXISTS (
    SELECT 1 FROM academic.conduct_semester_score WHERE id = 'conduct-score-demo-current'
);

-- 2. Previous Semester 1 (Học kỳ 2 2025-2026) - Điểm: 92.0 (Xuất sắc)
INSERT INTO academic.conduct_semester_score (
    id, student_id, semester_id, criteria1_score, criteria2_score, criteria3_score,
    criteria4_score, criteria5_score, total_score, classification, classification_vi,
    status, evaluator_name
)
SELECT 'conduct-score-demo-hist-1', 'student-profile', 'semester-history-demo',
       19.5, 25.0, 18.5, 22.0, 7.0, 92.0, 'XUAT_SAC', 'Xuất sắc', 'APPROVED', 'Hội đồng đánh giá rèn luyện Khoa CNTT'
WHERE NOT EXISTS (
    SELECT 1 FROM academic.conduct_semester_score WHERE id = 'conduct-score-demo-hist-1'
);

-- 3. Previous Semester 2 (Học kỳ 1 2025-2026) - Điểm: 85.0 (Tốt)
INSERT INTO academic.conduct_semester_score (
    id, student_id, semester_id, criteria1_score, criteria2_score, criteria3_score,
    criteria4_score, criteria5_score, total_score, classification, classification_vi,
    status, evaluator_name
)
SELECT 'conduct-score-demo-hist-2', 'student-profile', 'semester-history-demo-1',
       17.5, 23.5, 16.0, 21.0, 7.0, 85.0, 'TOT', 'Tốt', 'APPROVED', 'Hội đồng đánh giá rèn luyện Khoa CNTT'
WHERE NOT EXISTS (
    SELECT 1 FROM academic.conduct_semester_score WHERE id = 'conduct-score-demo-hist-2'
);

-- SEED PARTICIPATED ACTIVITIES FOR DEMO STUDENT
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer
)
SELECT seed.id, 'student-profile', 'semester-demo', seed.title, seed.category, seed.points, seed.activity_date::DATE, seed.organizer
FROM (VALUES
    ('act-drl-001', 'Tham gia Ngày hội việc làm UTE Career Expo 2026', 'Kỹ năng & Hướng nghiệp', 5.0, '2026-09-02', 'Trung tâm Dịch vụ Sinh viên & Hướng nghiệp'),
    ('act-drl-002', 'Hiến máu tình nguyện "Giọt hồng Sư phạm Kỹ thuật" đợt 1', 'Tình nguyện vì cộng đồng', 8.0, '2026-08-25', 'Đoàn Thanh niên - Hội Chữ thập đỏ UTE'),
    ('act-drl-003', 'Hội thảo Trí tuệ Nhân tạo & Dữ liệu lớn trong chuyển đổi số', 'Học thuật & Nghiên cứu khoa học', 5.0, '2026-08-18', 'Khoa Công nghệ Thông tin'),
    ('act-drl-004', 'Chiến dịch Mùa hè xanh và Tiếp sức đến trường 2026', 'Tình nguyện vì cộng đồng', 7.0, '2026-08-05', 'Hội Sinh viên ĐH Công nghệ Kỹ thuật TP.HCM')
) AS seed(id, title, category, points, activity_date, organizer)
WHERE NOT EXISTS (
    SELECT 1 FROM academic.conduct_activity existing WHERE existing.id = seed.id
);
