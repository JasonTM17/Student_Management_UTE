-- V58__seed_conduct_activities_per_student.sql
-- The demo conduct activities were previously only inserted for the literal
-- id 'student-profile', which forced AcademicConductController to alias real
-- students onto that magic id (hardcoded identity leak). That aliasing has
-- been removed; this migration seeds the same demo activity set for every
-- student that has a conduct score row, so the normal query path
-- (student_id = <real id>) returns data.

INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer
)
SELECT
    'act-drl-' || s."id" || '-' || seed.seq,
    s."id",
    score.semester_id,
    seed.title,
    seed.category,
    seed.points,
    seed.activity_date::DATE,
    seed.organizer
FROM academic."Student" s
JOIN academic.conduct_semester_score score ON score.student_id = s."id"
CROSS JOIN (VALUES
    (1, 'Tham gia Ngày hội việc làm UTE Career Expo 2026', 'Kỹ năng & Hướng nghiệp', 5.0, '2026-09-02', 'Trung tâm Dịch vụ Sinh viên & Hướng nghiệp'),
    (2, 'Hiến máu tình nguyện "Giọt hồng Công nghệ Kỹ thuật"', 'Tình nguyện vì cộng đồng', 8.0, '2026-08-25', 'Đoàn Thanh niên - Hội Chữ thập đỏ UTE'),
    (3, 'Hội thảo Trí tuệ Nhân tạo & Dữ liệu lớn trong chuyển đổi số', 'Học thuật & Nghiên cứu khoa học', 5.0, '2026-08-18', 'Khoa Công nghệ Thông tin'),
    (4, 'Chiến dịch Mùa hè xanh và Tiếp sức đến trường', 'Tình nguyện vì cộng đồng', 7.0, '2026-08-05', 'Hội Sinh viên ĐH Công nghệ Kỹ thuật TP.HCM')
) AS seed(seq, title, category, points, activity_date, organizer)
-- 'student-profile' already holds its own copy from V34; avoid duplicate
-- content for that student while giving every real student their own rows.
WHERE s."id" <> 'student-profile'
ON CONFLICT (id) DO NOTHING;
