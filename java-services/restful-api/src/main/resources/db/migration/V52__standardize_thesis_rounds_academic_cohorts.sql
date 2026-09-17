-- V52__standardize_thesis_rounds_academic_cohorts.sql
-- Purpose: Standardize thesis registration rounds by Academic Cohorts (Niên khóa) and Semesters/Academic Years
-- according to HCMUTE academic regulations. Clean up test names and seed realistic topics.

-- 1. Standardize Thesis Rounds by Niên khóa (Insert if fresh database, update if existing)
INSERT INTO thesis.thesis_registration_round (
    id,
    name,
    thesis_type,
    status,
    registration_start,
    registration_end,
    lecturer_submit_start,
    lecturer_submit_end
) VALUES
    (
        '22222222-2222-2222-2222-222222222101'::UUID,
        'Khóa luận Tốt nghiệp - Niên khóa 2022 - 2026 (Năm học 2026 - 2027)',
        'KLTN',
        'REGISTRATION_OPEN',
        '2026-09-15 00:00:00+00',
        '2026-11-30 23:59:59+00',
        '2026-08-01 00:00:00+00',
        '2026-09-14 23:59:59+00'
    ),
    (
        '22d65ee6-f485-40ae-a66b-528d35007745'::UUID,
        'Tiểu luận Chuyên ngành - Niên khóa 2023 - 2027 (Học kỳ 1, Năm học 2026 - 2027)',
        'TLCN',
        'REGISTRATION_OPEN',
        '2026-09-10 00:00:00+00',
        '2026-10-31 23:59:59+00',
        '2026-08-01 00:00:00+00',
        '2026-09-09 23:59:59+00'
    ),
    (
        'b3d980d4-8d69-4fa8-a423-6e8582b66aea'::UUID,
        'Khóa luận Tốt nghiệp (Đợt 2) - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2026 - 2027)',
        'KLTN',
        'PROPOSAL_OPEN',
        '2027-01-10 00:00:00+00',
        '2027-02-28 23:59:59+00',
        '2026-12-01 00:00:00+00',
        '2027-01-09 23:59:59+00'
    ),
    (
        'bad8eba0-2d12-4414-8aab-7424174b3b97'::UUID,
        'Tiểu luận Chuyên ngành - Niên khóa 2022 - 2026 (Học kỳ 2, Năm học 2025 - 2026)',
        'TLCN',
        'RESULTS_PUBLISHED',
        '2026-02-15 00:00:00+00',
        '2026-03-31 23:59:59+00',
        '2026-01-01 00:00:00+00',
        '2026-02-14 23:59:59+00'
    ),
    (
        'be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1'::UUID,
        'Khóa luận Tốt nghiệp - Niên khóa 2021 - 2025 (Đợt 1, Năm học 2025 - 2026)',
        'KLTN',
        'RESULTS_PUBLISHED',
        '2025-09-01 00:00:00+00',
        '2025-10-31 23:59:59+00',
        '2025-08-01 00:00:00+00',
        '2025-08-31 23:59:59+00'
    ),
    (
        'b35e9ff1-1eb8-4e73-812e-04e1a66041f1'::UUID,
        'Đồ án Chuyên ngành - Niên khóa 2021 - 2025 (Học kỳ 2, Năm học 2024 - 2025)',
        'TLCN',
        'RESULTS_PUBLISHED',
        '2025-02-15 00:00:00+00',
        '2025-03-31 23:59:59+00',
        '2025-01-01 00:00:00+00',
        '2025-02-14 23:59:59+00'
    )
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    thesis_type = EXCLUDED.thesis_type,
    status = EXCLUDED.status,
    registration_start = EXCLUDED.registration_start,
    registration_end = EXCLUDED.registration_end,
    lecturer_submit_start = EXCLUDED.lecturer_submit_start,
    lecturer_submit_end = EXCLUDED.lecturer_submit_end,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Standardize scratch topic titles into formal academic topics
UPDATE thesis.thesis_topic
SET title = 'Hệ thống quản lý điểm rèn luyện sinh viên theo chuẩn đầu ra',
    description = 'Phát triển mô đun ghi nhận và đánh giá hoạt động phong trào, tình nguyện, nghiên cứu khoa học của sinh viên theo từng học kỳ.'
WHERE id = '82fbc021-c1f4-459e-94c3-db977e69fe77';

UPDATE thesis.thesis_topic
SET title = 'Nghiên cứu kiến trúc vi dịch vụ và xử lý đồng thời trong hệ thống quản lý đào tạo',
    description = 'Phân tích hiệu năng hệ thống đăng ký học phần tải cao sử dụng Spring Boot, Redis cache và kiến trúc Reactive Event-Driven.'
WHERE id = '5dcb2092-37b9-4c92-a112-10789ce64787';

UPDATE thesis.thesis_topic
SET title = 'Hệ thống chấm bài tập lập trình trực tuyến tự động hóa',
    description = 'Xây dựng sandbox chấm mã nguồn độc lập (Online Judge) hỗ trợ C++, Java, Python với giới hạn tài nguyên an toàn.'
WHERE id = '67676bd3-5dc1-4ab4-8e55-e2f93974cd63';

UPDATE thesis.thesis_topic
SET title = 'Ứng dụng Trợ lý ảo tư vấn học vụ và định hướng nghề nghiệp sinh viên',
    description = 'Ứng dụng mô hình xử lý ngôn ngữ tự nhiên tiếng Việt hỗ trợ tra cứu quy chế học vụ, chương trình đào tạo và lộ trình môn học.'
WHERE id = '8774696e-5bb1-4a4b-8a45-80d87d5e5104';

UPDATE thesis.thesis_topic
SET title = 'Xây dựng hệ thống thi và đánh giá kết quả học tập trực tuyến',
    description = 'Thiết kế ngân hàng câu hỏi trắc nghiệm, xáo đề tự động và giám sát thi trực tuyến chống gian lận.'
WHERE id = '1d3646b8-75aa-48af-bd4c-332bfac7020c';

-- 3. Seed realistic topics for round 22d65ee6-f485-40ae-a66b-528d35007745 (TLCN Niên khóa 2023 - 2027)
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by
)
SELECT seed.id::UUID, '22d65ee6-f485-40ae-a66b-528d35007745'::UUID, seed.dept_id, seed.title, seed.description, seed.max_groups, 'PUBLISHED', 'lecturer-user'
FROM (VALUES
    ('44444444-4444-4444-4444-444444445001', 'department-demo', 'Phát triển ứng dụng Web quản lý câu lạc bộ sinh viên UTE', 'Xây dựng website quản lý thành viên, sự kiện và tài chính nội bộ cho các CLB học thuật trường ĐH Công nghệ Kỹ thuật TP.HCM.', 2),
    ('44444444-4444-4444-4444-444444445002', 'department-demo', 'Xây dựng ứng dụng di động theo dõi lịch trình xe buýt thông minh', 'Ứng dụng Flutter tích hợp dữ liệu GPS thời gian thực các tuyến xe buýt kết nối các cơ sở đào tạo của trường.', 2),
    ('44444444-4444-4444-4444-444444445003', 'department-fme-robotics', 'Mô phỏng và điều khiển cánh tay robot mini ứng dụng Arduino và ESP32', 'Nghiên cứu mô hình động học 4 bậc tự do, lập trình điều khiển vị trí qua ứng dụng di động kết nối Bluetooth/WiFi.', 2),
    ('44444444-4444-4444-4444-444444445004', 'department-feee-telecom', 'Thiết kế mạch đo thông số môi trường hiển thị OLED truyền dữ liệu qua BLE', 'Chế tạo phần cứng thu thập nhiệt độ, độ ẩm và chất lượng không khí, đồng bộ dữ liệu lên máy chủ đám mây.', 2),
    ('44444444-4444-4444-4444-444444445005', 'department-foe-mis', 'Phân tích dữ liệu học tập của sinh viên phục vụ cảnh báo sớm kết quả học tập', 'Ứng dụng giải thuật khai phá dữ liệu phân loại sinh viên có nguy cơ học vụ để phòng Đào tạo kịp thời tư vấn.', 2)
) AS seed(id, dept_id, title, description, max_groups)
WHERE NOT EXISTS (
    SELECT 1 FROM thesis.thesis_topic existing WHERE existing.id = seed.id::UUID
);
