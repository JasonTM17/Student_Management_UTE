-- V126__enrich_dean_and_thesis_rounds_demo_dataset.sql
-- Purpose:
--   1. Complete 3-member rosters on V43 demo groups in round c8f1a234 so they
--      satisfy the V113 3..5 member contract without stale rejection notes, and
--      populate every FIT department (SE, IS, Network, AI, Faculty) with topics
--      and student groups.
--   2. Seed faculty topics, supervisors, and 3-member student groups into KLTN
--      Round 2 (b3d980d4) and TLCN 2023-2027 (22d65ee6) so switching rounds on
--      the Dean Observation and Thesis workspaces always shows rich cohort data
--      (preserving student-profile and topic 44444444-4444-4444-4444-444444445001
--      untouched for the disposable report-upload E2E fixture).
--   3. Assign teaching sections, schedules, grade items, enrollments, grades,
--      attendance records, and notifications to the seeded Dean
--      (lecturer-profile-truongkhoa-001) and Department Head
--      (lecturer-profile-truongkhoa-002) accounts so every sidebar tab renders
--      meaningful academic data.
-- Idempotent and guarded by foreign-key existence checks.

-- =============================================================================
-- 1. COMPLETE 3-MEMBER ROSTERS FOR V43 GROUPS IN MAIN KLTN ROUND (c8f1a234)
-- =============================================================================
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader,
    is_external, active_participation, created_at
)
SELECT
    seed.member_id::uuid,
    seed.group_id::uuid,
    'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid,
    seed.student_id,
    3,
    FALSE,
    FALSE,
    TRUE,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('66666666-6666-4666-8666-000000000011', '55555555-5555-5555-5555-555544444011', 'student-profile-101'),
    ('66666666-6666-4666-8666-000000000012', '55555555-5555-5555-5555-555544444012', 'student-profile-102'),
    ('66666666-6666-4666-8666-000000000013', '55555555-5555-5555-5555-555544444013', 'student-profile-103'),
    ('66666666-6666-4666-8666-000000000014', '55555555-5555-5555-5555-555544444014', 'student-profile-104'),
    ('66666666-6666-4666-8666-000000000016', '55555555-5555-5555-5555-555544444016', 'student-profile-105'),
    ('66666666-6666-4666-8666-000000000017', '55555555-5555-5555-5555-555544444017', 'student-profile-106'),
    ('66666666-6666-4666-8666-000000000026', '55555555-5555-5555-5555-555544444026', 'student-profile-107'),
    ('66666666-6666-4666-8666-000000000027', '55555555-5555-5555-5555-555544444027', 'student-profile-108')
) AS seed(member_id, group_id, student_id)
WHERE EXISTS (
    SELECT 1 FROM thesis.thesis_group g
    WHERE g.id = seed.group_id::uuid
      AND g.round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid
      AND g.status <> 'CANCELLED'
      AND NOT g.legacy_group_size_exception
)
  AND EXISTS (SELECT 1 FROM academic."Student" s WHERE s."id" = seed.student_id)
  AND (SELECT COUNT(*) FROM thesis.thesis_group_member m WHERE m.group_id = seed.group_id::uuid) = 2
  AND NOT EXISTS (
      SELECT 1 FROM thesis.thesis_group_member m
      WHERE m.round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid
        AND m.student_id = seed.student_id
        AND m.active_participation
  )
ON CONFLICT (id) DO NOTHING;

-- Restore APPROVED status on 6 of the completed 3-member V43 groups and clear
-- the migration-generated size warning on the remaining 2 pending groups.
UPDATE thesis.thesis_group g
SET approval_status = 'APPROVED',
    approved_by = COALESCE(g.approved_by, 'truongkhoa-user-001'),
    approved_at = COALESCE(g.approved_at, CURRENT_TIMESTAMP),
    rejection_reason = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE g.id IN (
    '55555555-5555-5555-5555-555544444011'::uuid,
    '55555555-5555-5555-5555-555544444012'::uuid,
    '55555555-5555-5555-5555-555544444014'::uuid,
    '55555555-5555-5555-5555-555544444016'::uuid,
    '55555555-5555-5555-5555-555544444026'::uuid,
    '55555555-5555-5555-5555-555544444027'::uuid
)
  AND g.status <> 'CANCELLED'
  AND NOT g.legacy_group_size_exception
  AND (SELECT COUNT(*) FROM thesis.thesis_group_member m WHERE m.group_id = g.id) BETWEEN 3 AND 5
  AND (g.approval_status <> 'APPROVED' OR g.rejection_reason IS NOT NULL);

UPDATE thesis.thesis_group g
SET rejection_reason = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE g.id IN (
    '55555555-5555-5555-5555-555544444013'::uuid,
    '55555555-5555-5555-5555-555544444017'::uuid
)
  AND g.rejection_reason LIKE 'Group requires %';

-- =============================================================================
-- 2. ENRICH MAIN KLTN ROUND (c8f1a234) ACROSS ALL FIT DEPARTMENTS
-- =============================================================================
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
)
SELECT
    seed.id::uuid,
    'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid,
    seed.dept_id,
    seed.title,
    seed.description,
    seed.max_groups,
    'PUBLISHED',
    seed.created_by,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('44444444-4444-4444-4444-444444444101', 'department-fit-software',
     'Kiến trúc nền tảng Micro-Frontend và cổng dịch vụ đại học số chịu tải cao',
     'Thiết kế hệ thống cổng học vụ phân tán sử dụng Next.js App Router, Spring Boot 3 và cơ chế đồng bộ phiên đăng nhập đa tab thời gian thực.',
     2, 'truongkhoa-user-001'),
    ('44444444-4444-4444-4444-444444444102', 'department-fit-is',
     'Kho dữ liệu Lakehouse hợp nhất và hệ thống phân tích học vụ thông minh thời gian thực',
     'Xây dựng kiến trúc Data Lakehouse thu thập dữ liệu học tập, điểm rèn luyện và dự báo sớm nguy cơ cảnh báo học vụ cho sinh viên toàn trường.',
     2, 'truongkhoa-user-002'),
    ('44444444-4444-4444-4444-444444444103', 'department-fit-is',
     'Hệ thống hoạch định nguồn lực ERP tích hợp quy trình kiểm toán số cho trường đại học',
     'Phát triển phân hệ quản trị danh mục đào tạo, phân công giảng dạy và đối soát nhật ký kiểm toán bất biến theo chuẩn Four-Eyes.',
     2, 'lecturer-user-029'),
    ('44444444-4444-4444-4444-444444444104', 'department-fit-network',
     'Kiến trúc Zero-Trust Network Access và giám sát an ninh SIEM tự động cho hạ tầng Cloud',
     'Nghiên cứu mô hình xác thực liên tục theo phiên, kiểm soát truy cập vi phân đoạn và phát hiện bất thường lưu lượng API trong môi trường đám mây.',
     2, 'lecturer-user-028')
) AS seed(id, dept_id, title, description, max_groups, created_by)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_registration_round r WHERE r.id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid)
  AND EXISTS (SELECT 1 FROM academic."Department" d WHERE d."id" = seed.dept_id)
ON CONFLICT (id) DO NOTHING;

INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
SELECT
    seed.sup_id::uuid,
    seed.topic_id::uuid,
    seed.lecturer_id,
    1,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('44444444-4444-4444-9444-444444444101', '44444444-4444-4444-4444-444444444101', 'lecturer-profile-truongkhoa-001'),
    ('44444444-4444-4444-9444-444444444102', '44444444-4444-4444-4444-444444444102', 'lecturer-profile-truongkhoa-002'),
    ('44444444-4444-4444-9444-444444444103', '44444444-4444-4444-4444-444444444103', 'lecturer-profile-029'),
    ('44444444-4444-4444-9444-444444444104', '44444444-4444-4444-4444-444444444104', 'lecturer-profile-028')
) AS seed(sup_id, topic_id, lecturer_id)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_topic t WHERE t.id = seed.topic_id::uuid)
  AND EXISTS (SELECT 1 FROM academic."Lecturer" l WHERE l."id" = seed.lecturer_id)
ON CONFLICT (topic_id, supervisor_order) DO NOTHING;

-- Insert 3 new FIT groups in c8f1a234 (SE, IS, Network) as PENDING first, then
-- populate their 3-member rosters and promote the approved ones.
INSERT INTO thesis.thesis_group (
    id, round_id, leader_student_id, topic_id, status, approval_status, version, created_at, updated_at
)
SELECT
    seed.group_id::uuid,
    'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid,
    seed.leader_id,
    seed.topic_id::uuid,
    'ASSIGNED',
    'PENDING',
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('55555555-5555-5555-5555-555544444101', 'student-profile-111', '44444444-4444-4444-4444-444444444101'),
    ('55555555-5555-5555-5555-555544444102', 'student-profile-114', '44444444-4444-4444-4444-444444444102'),
    ('55555555-5555-5555-5555-555544444103', 'student-profile-117', '44444444-4444-4444-4444-444444444030')
) AS seed(group_id, leader_id, topic_id)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_topic t WHERE t.id = seed.topic_id::uuid)
  AND EXISTS (SELECT 1 FROM academic."Student" s WHERE s."id" = seed.leader_id)
  AND NOT EXISTS (
      SELECT 1 FROM thesis.thesis_group_member m
      WHERE m.round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid
        AND m.student_id = seed.leader_id
        AND m.active_participation
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, is_external, active_participation, created_at
)
SELECT
    seed.member_id::uuid,
    seed.group_id::uuid,
    'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid,
    seed.student_id,
    seed.member_order,
    seed.is_leader,
    FALSE,
    TRUE,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('66666666-6666-4666-8666-000000000101', '55555555-5555-5555-5555-555544444101', 'student-profile-111', 1, TRUE),
    ('66666666-6666-4666-8666-000000000102', '55555555-5555-5555-5555-555544444101', 'student-profile-112', 2, FALSE),
    ('66666666-6666-4666-8666-000000000103', '55555555-5555-5555-5555-555544444101', 'student-profile-113', 3, FALSE),
    ('66666666-6666-4666-8666-000000000104', '55555555-5555-5555-5555-555544444102', 'student-profile-114', 1, TRUE),
    ('66666666-6666-4666-8666-000000000105', '55555555-5555-5555-5555-555544444102', 'student-profile-115', 2, FALSE),
    ('66666666-6666-4666-8666-000000000106', '55555555-5555-5555-5555-555544444102', 'student-profile-116', 3, FALSE),
    ('66666666-6666-4666-8666-000000000107', '55555555-5555-5555-5555-555544444103', 'student-profile-117', 1, TRUE),
    ('66666666-6666-4666-8666-000000000108', '55555555-5555-5555-5555-555544444103', 'student-profile-118', 2, FALSE),
    ('66666666-6666-4666-8666-000000000109', '55555555-5555-5555-5555-555544444103', 'student-profile-119', 3, FALSE)
) AS seed(member_id, group_id, student_id, member_order, is_leader)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_group g WHERE g.id = seed.group_id::uuid)
  AND EXISTS (SELECT 1 FROM academic."Student" s WHERE s."id" = seed.student_id)
  AND NOT EXISTS (
      SELECT 1 FROM thesis.thesis_group_member m
      WHERE m.round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid
        AND m.student_id = seed.student_id
        AND m.active_participation
  )
ON CONFLICT (id) DO NOTHING;

UPDATE thesis.thesis_group g
SET approval_status = 'APPROVED',
    approved_by = 'truongkhoa-user-001',
    approved_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE g.id IN (
    '55555555-5555-5555-5555-555544444101'::uuid,
    '55555555-5555-5555-5555-555544444102'::uuid
)
  AND (SELECT COUNT(*) FROM thesis.thesis_group_member m WHERE m.group_id = g.id) BETWEEN 3 AND 5
  AND g.approval_status <> 'APPROVED';

-- =============================================================================
-- 3. SEED KLTN ROUND 2 (b3d980d4-8d69-4fa8-a423-6e8582b66aea) WITH TOPICS & GROUPS
-- =============================================================================
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
)
SELECT
    seed.id::uuid,
    'b3d980d4-8d69-4fa8-a423-6e8582b66aea'::uuid,
    seed.dept_id,
    seed.title,
    seed.description,
    seed.max_groups,
    'PUBLISHED',
    seed.created_by,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('44444444-4444-4444-4444-444444446001', 'department-demo',
     'Xây dựng nền tảng đại học số hợp nhất quản lý đào tạo tín chỉ và khóa luận tốt nghiệp',
     'Phát triển hệ thống quản trị vòng đời học vụ từ đăng ký học phần, xét điều kiện nhận đồ án đến chấm điểm hội đồng bảo vệ trực tuyến.',
     3, 'truongkhoa-user-001'),
    ('44444444-4444-4444-4444-444444446002', 'department-demo',
     'Hệ thống trợ lý học vụ RAG đa tác tử tích hợp kiểm chứng trích dẫn quy chế đào tạo',
     'Kết hợp tìm kiếm từ vựng, truy hồi vector và định tuyến độ khó câu hỏi nhằm giải đáp chính xác quy chế học vụ kèm trích dẫn nguồn văn bản.',
     2, 'lecturer-user'),
    ('44444444-4444-4444-4444-444444446003', 'department-fit-software',
     'Nền tảng DevSecOps tự động hóa kiểm thử bảo mật và triển khai liên tục cho ứng dụng Cloud-Native',
     'Xây dựng pipeline CI/CD tích hợp phân tích tĩnh SAST, kiểm thử phụ thuộc, kiểm chứng migration cơ sở dữ liệu và giám sát canary release.',
     2, 'lecturer-user-027'),
    ('44444444-4444-4444-4444-444444446004', 'department-fit-software',
     'Kiến trúc xử lý giao dịch đồng thời cao ứng dụng khóa bi quan và sổ cái Idempotency',
     'Nghiên cứu giải pháp chống đăng ký vượt sĩ số và chống gửi trùng yêu cầu trong các đợt cao điểm đăng ký học phần toàn trường.',
     2, 'lecturer-user-030'),
    ('44444444-4444-4444-4444-444444446005', 'department-fit-is',
     'Hệ thống phân tích dữ liệu lớn đánh giá chuẩn đầu ra chương trình đào tạo AUN-QA',
     'Tổng hợp dữ liệu điểm thành phần, khảo sát người học và theo dõi mức độ đạt chuẩn đầu ra PLO/CLO theo từng khóa tuyển sinh.',
     2, 'truongkhoa-user-002'),
    ('44444444-4444-4444-4444-444444446006', 'department-fit-is',
     'Kho dữ liệu thông minh hỗ trợ điều phối phòng học, giảng viên và dự báo nhu cầu mở lớp',
     'Ứng dụng mô hình dự báo chuỗi thời gian để ước lượng số lượng sinh viên đăng ký từng học phần và tối ưu công suất sử dụng phòng học.',
     2, 'lecturer-user-029'),
    ('44444444-4444-4444-4444-444444446007', 'department-fit-network',
     'Hệ thống phát hiện xâm nhập bất thường và phòng chống tấn công đánh cắp phiên JWT',
     'Triển khai cơ chế xoay vòng refresh token, phát hiện tái sử dụng token bị đánh cắp và thu hồi tức thì toàn bộ phiên truy cập liên quan.',
     2, 'lecturer-user-028'),
    ('44444444-4444-4444-4444-444444446008', 'department-fit-ai',
     'Mô hình thị giác máy tính và xử lý ngôn ngữ tự nhiên hỗ trợ số hóa hồ sơ minh chứng học thuật',
     'Tự động nhận dạng văn bản tiếng Việt có dấu, bóc tách thông tin chứng chỉ ngoại ngữ/tin học và đối chiếu quy chuẩn xét tốt nghiệp.',
     2, 'lecturer-user-026')
) AS seed(id, dept_id, title, description, max_groups, created_by)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_registration_round r WHERE r.id = 'b3d980d4-8d69-4fa8-a423-6e8582b66aea'::uuid)
  AND EXISTS (SELECT 1 FROM academic."Department" d WHERE d."id" = seed.dept_id)
ON CONFLICT (id) DO NOTHING;

INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
SELECT
    seed.sup_id::uuid,
    seed.topic_id::uuid,
    seed.lecturer_id,
    1,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('44444444-4444-4444-9444-444444446001', '44444444-4444-4444-4444-444444446001', 'lecturer-profile-truongkhoa-001'),
    ('44444444-4444-4444-9444-444444446002', '44444444-4444-4444-4444-444444446002', 'lecturer-profile'),
    ('44444444-4444-4444-9444-444444446003', '44444444-4444-4444-4444-444444446003', 'lecturer-profile-027'),
    ('44444444-4444-4444-9444-444444446004', '44444444-4444-4444-4444-444444446004', 'lecturer-profile-030'),
    ('44444444-4444-4444-9444-444444446005', '44444444-4444-4444-4444-444444446005', 'lecturer-profile-truongkhoa-002'),
    ('44444444-4444-4444-9444-444444446006', '44444444-4444-4444-4444-444444446006', 'lecturer-profile-029'),
    ('44444444-4444-4444-9444-444444446007', '44444444-4444-4444-4444-444444446007', 'lecturer-profile-028'),
    ('44444444-4444-4444-9444-444444446008', '44444444-4444-4444-4444-444444446008', 'lecturer-profile-026')
) AS seed(sup_id, topic_id, lecturer_id)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_topic t WHERE t.id = seed.topic_id::uuid)
  AND EXISTS (SELECT 1 FROM academic."Lecturer" l WHERE l."id" = seed.lecturer_id)
ON CONFLICT (topic_id, supervisor_order) DO NOTHING;

INSERT INTO thesis.thesis_group (
    id, round_id, leader_student_id, topic_id, status, approval_status, version, created_at, updated_at
)
SELECT
    seed.group_id::uuid,
    'b3d980d4-8d69-4fa8-a423-6e8582b66aea'::uuid,
    seed.leader_id,
    seed.topic_id::uuid,
    seed.group_status,
    'PENDING',
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('55555555-5555-5555-5555-555544446001', 'student-profile-121', '44444444-4444-4444-4444-444444446001', 'ASSIGNED'),
    ('55555555-5555-5555-5555-555544446002', 'student-profile-124', '44444444-4444-4444-4444-444444446003', 'ASSIGNED'),
    ('55555555-5555-5555-5555-555544446003', 'student-profile-127', '44444444-4444-4444-4444-444444446005', 'ASSIGNED'),
    ('55555555-5555-5555-5555-555544446004', 'student-profile-130', '44444444-4444-4444-4444-444444446007', 'SUBMITTED'),
    ('55555555-5555-5555-5555-555544446005', 'student-profile-133', '44444444-4444-4444-4444-444444446008', 'SUBMITTED')
) AS seed(group_id, leader_id, topic_id, group_status)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_topic t WHERE t.id = seed.topic_id::uuid)
  AND EXISTS (SELECT 1 FROM academic."Student" s WHERE s."id" = seed.leader_id)
  AND NOT EXISTS (
      SELECT 1 FROM thesis.thesis_group_member m
      WHERE m.round_id = 'b3d980d4-8d69-4fa8-a423-6e8582b66aea'::uuid
        AND m.student_id = seed.leader_id
        AND m.active_participation
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, is_external, active_participation, created_at
)
SELECT
    seed.member_id::uuid,
    seed.group_id::uuid,
    'b3d980d4-8d69-4fa8-a423-6e8582b66aea'::uuid,
    seed.student_id,
    seed.member_order,
    seed.is_leader,
    FALSE,
    TRUE,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('66666666-6666-4666-8666-000000006001', '55555555-5555-5555-5555-555544446001', 'student-profile-121', 1, TRUE),
    ('66666666-6666-4666-8666-000000006002', '55555555-5555-5555-5555-555544446001', 'student-profile-122', 2, FALSE),
    ('66666666-6666-4666-8666-000000006003', '55555555-5555-5555-5555-555544446001', 'student-profile-123', 3, FALSE),
    ('66666666-6666-4666-8666-000000006004', '55555555-5555-5555-5555-555544446002', 'student-profile-124', 1, TRUE),
    ('66666666-6666-4666-8666-000000006005', '55555555-5555-5555-5555-555544446002', 'student-profile-125', 2, FALSE),
    ('66666666-6666-4666-8666-000000006006', '55555555-5555-5555-5555-555544446002', 'student-profile-126', 3, FALSE),
    ('66666666-6666-4666-8666-000000006007', '55555555-5555-5555-5555-555544446003', 'student-profile-127', 1, TRUE),
    ('66666666-6666-4666-8666-000000006008', '55555555-5555-5555-5555-555544446003', 'student-profile-128', 2, FALSE),
    ('66666666-6666-4666-8666-000000006009', '55555555-5555-5555-5555-555544446003', 'student-profile-129', 3, FALSE),
    ('66666666-6666-4666-8666-000000006010', '55555555-5555-5555-5555-555544446004', 'student-profile-130', 1, TRUE),
    ('66666666-6666-4666-8666-000000006011', '55555555-5555-5555-5555-555544446004', 'student-profile-131', 2, FALSE),
    ('66666666-6666-4666-8666-000000006012', '55555555-5555-5555-5555-555544446004', 'student-profile-132', 3, FALSE),
    ('66666666-6666-4666-8666-000000006013', '55555555-5555-5555-5555-555544446005', 'student-profile-133', 1, TRUE),
    ('66666666-6666-4666-8666-000000006014', '55555555-5555-5555-5555-555544446005', 'student-profile-134', 2, FALSE),
    ('66666666-6666-4666-8666-000000006015', '55555555-5555-5555-5555-555544446005', 'student-profile-135', 3, FALSE)
) AS seed(member_id, group_id, student_id, member_order, is_leader)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_group g WHERE g.id = seed.group_id::uuid)
  AND EXISTS (SELECT 1 FROM academic."Student" s WHERE s."id" = seed.student_id)
  AND NOT EXISTS (
      SELECT 1 FROM thesis.thesis_group_member m
      WHERE m.round_id = 'b3d980d4-8d69-4fa8-a423-6e8582b66aea'::uuid
        AND m.student_id = seed.student_id
        AND m.active_participation
  )
ON CONFLICT (id) DO NOTHING;

UPDATE thesis.thesis_group g
SET approval_status = 'APPROVED',
    approved_by = 'truongkhoa-user-001',
    approved_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE g.id IN (
    '55555555-5555-5555-5555-555544446001'::uuid,
    '55555555-5555-5555-5555-555544446002'::uuid,
    '55555555-5555-5555-5555-555544446003'::uuid
)
  AND (SELECT COUNT(*) FROM thesis.thesis_group_member m WHERE m.group_id = g.id) BETWEEN 3 AND 5
  AND g.approval_status <> 'APPROVED';

-- =============================================================================
-- 4. ENRICH TLCN 2023-2027 ROUND (22d65ee6-f485-40ae-a66b-528d35007745)
--    Keep topic 44444444-4444-4444-4444-444444445001 and student-profile free
--    so the disposable report-upload E2E fixture remains untouched.
-- =============================================================================
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
)
SELECT
    seed.id::uuid,
    '22d65ee6-f485-40ae-a66b-528d35007745'::uuid,
    seed.dept_id,
    seed.title,
    seed.description,
    seed.max_groups,
    'PUBLISHED',
    seed.created_by,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('44444444-4444-4444-4444-444444445006', 'department-fit-software',
     'Xây dựng hệ thống quản lý phòng thí nghiệm và đặt lịch mượn thiết bị IoT trực tuyến',
     'Ứng dụng web hỗ trợ sinh viên đăng ký mượn bộ kit thực hành vi điều khiển, theo dõi tình trạng thiết bị bằng mã QR.',
     2, 'lecturer-user-027'),
    ('44444444-4444-4444-4444-444444445007', 'department-fit-is',
     'Hệ thống quản lý minh chứng hoạt động ngoại khóa và chấm điểm rèn luyện tự động',
     'Số hóa quy trình nộp minh chứng tham gia sự kiện học thuật, tình nguyện và đối soát điểm rèn luyện theo khung đánh giá của Bộ GD&ĐT.',
     2, 'truongkhoa-user-002'),
    ('44444444-4444-4444-4444-444444445008', 'department-fit-network',
     'Xây dựng công cụ quét cấu hình bảo mật ứng dụng web và cảnh báo sớm lỗ hổng OWASP Top 10',
     'Tự động kiểm tra cấu hình HTTP security headers, chính sách CORS/CSRF và phát hiện rò rỉ thông tin nhạy cảm trên môi trường staging.',
     2, 'lecturer-user-028'),
    ('44444444-4444-4444-4444-444444445009', 'department-fit-ai',
     'Ứng dụng học máy phân loại phản hồi sinh viên và gợi ý cải tiến chất lượng giảng dạy',
     'Phân tích sắc thái ý kiến đóng góp cuối học kỳ bằng mô hình PhoBERT, trực quan hóa các chủ đề ưu tiên cho ban chủ nhiệm khoa.',
     2, 'lecturer-user-026')
) AS seed(id, dept_id, title, description, max_groups, created_by)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_registration_round r WHERE r.id = '22d65ee6-f485-40ae-a66b-528d35007745'::uuid)
  AND EXISTS (SELECT 1 FROM academic."Department" d WHERE d."id" = seed.dept_id)
ON CONFLICT (id) DO NOTHING;

INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
SELECT
    seed.sup_id::uuid,
    seed.topic_id::uuid,
    seed.lecturer_id,
    1,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('44444444-4444-4444-9444-444444445001', '44444444-4444-4444-4444-444444445001', 'lecturer-profile'),
    ('44444444-4444-4444-9444-444444445002', '44444444-4444-4444-4444-444444445002', 'lecturer-profile-truongkhoa-001'),
    ('44444444-4444-4444-9444-444444445006', '44444444-4444-4444-4444-444444445006', 'lecturer-profile-027'),
    ('44444444-4444-4444-9444-444444445007', '44444444-4444-4444-4444-444444445007', 'lecturer-profile-truongkhoa-002'),
    ('44444444-4444-4444-9444-444444445008', '44444444-4444-4444-4444-444444445008', 'lecturer-profile-028'),
    ('44444444-4444-4444-9444-444444445009', '44444444-4444-4444-4444-444444445009', 'lecturer-profile-026')
) AS seed(sup_id, topic_id, lecturer_id)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_topic t WHERE t.id = seed.topic_id::uuid)
  AND EXISTS (SELECT 1 FROM academic."Lecturer" l WHERE l."id" = seed.lecturer_id)
ON CONFLICT (topic_id, supervisor_order) DO NOTHING;

INSERT INTO thesis.thesis_group (
    id, round_id, leader_student_id, topic_id, status, approval_status, version, created_at, updated_at
)
SELECT
    seed.group_id::uuid,
    '22d65ee6-f485-40ae-a66b-528d35007745'::uuid,
    seed.leader_id,
    seed.topic_id::uuid,
    'ASSIGNED',
    'PENDING',
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('55555555-5555-5555-5555-555544445002', 'student-profile-141', '44444444-4444-4444-4444-444444445002'),
    ('55555555-5555-5555-5555-555544445006', 'student-profile-144', '44444444-4444-4444-4444-444444445006'),
    ('55555555-5555-5555-5555-555544445007', 'student-profile-147', '44444444-4444-4444-4444-444444445007'),
    ('55555555-5555-5555-5555-555544445008', 'student-profile-150', '44444444-4444-4444-4444-444444445008')
) AS seed(group_id, leader_id, topic_id)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_topic t WHERE t.id = seed.topic_id::uuid)
  AND EXISTS (SELECT 1 FROM academic."Student" s WHERE s."id" = seed.leader_id)
  AND NOT EXISTS (
      SELECT 1 FROM thesis.thesis_group_member m
      WHERE m.round_id = '22d65ee6-f485-40ae-a66b-528d35007745'::uuid
        AND m.student_id = seed.leader_id
        AND m.active_participation
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, is_external, active_participation, created_at
)
SELECT
    seed.member_id::uuid,
    seed.group_id::uuid,
    '22d65ee6-f485-40ae-a66b-528d35007745'::uuid,
    seed.student_id,
    seed.member_order,
    seed.is_leader,
    FALSE,
    TRUE,
    CURRENT_TIMESTAMP
FROM (VALUES
    ('66666666-6666-4666-8666-000000005001', '55555555-5555-5555-5555-555544445002', 'student-profile-141', 1, TRUE),
    ('66666666-6666-4666-8666-000000005002', '55555555-5555-5555-5555-555544445002', 'student-profile-142', 2, FALSE),
    ('66666666-6666-4666-8666-000000005003', '55555555-5555-5555-5555-555544445002', 'student-profile-143', 3, FALSE),
    ('66666666-6666-4666-8666-000000005004', '55555555-5555-5555-5555-555544445006', 'student-profile-144', 1, TRUE),
    ('66666666-6666-4666-8666-000000005005', '55555555-5555-5555-5555-555544445006', 'student-profile-145', 2, FALSE),
    ('66666666-6666-4666-8666-000000005006', '55555555-5555-5555-5555-555544445006', 'student-profile-146', 3, FALSE),
    ('66666666-6666-4666-8666-000000005007', '55555555-5555-5555-5555-555544445007', 'student-profile-147', 1, TRUE),
    ('66666666-6666-4666-8666-000000005008', '55555555-5555-5555-5555-555544445007', 'student-profile-148', 2, FALSE),
    ('66666666-6666-4666-8666-000000005009', '55555555-5555-5555-5555-555544445007', 'student-profile-149', 3, FALSE),
    ('66666666-6666-4666-8666-000000005010', '55555555-5555-5555-5555-555544445008', 'student-profile-150', 1, TRUE),
    ('66666666-6666-4666-8666-000000005011', '55555555-5555-5555-5555-555544445008', 'student-profile-151', 2, FALSE),
    ('66666666-6666-4666-8666-000000005012', '55555555-5555-5555-5555-555544445008', 'student-profile-152', 3, FALSE)
) AS seed(member_id, group_id, student_id, member_order, is_leader)
WHERE EXISTS (SELECT 1 FROM thesis.thesis_group g WHERE g.id = seed.group_id::uuid)
  AND EXISTS (SELECT 1 FROM academic."Student" s WHERE s."id" = seed.student_id)
  AND NOT EXISTS (
      SELECT 1 FROM thesis.thesis_group_member m
      WHERE m.round_id = '22d65ee6-f485-40ae-a66b-528d35007745'::uuid
        AND m.student_id = seed.student_id
        AND m.active_participation
  )
ON CONFLICT (id) DO NOTHING;

UPDATE thesis.thesis_group g
SET approval_status = 'APPROVED',
    approved_by = 'truongkhoa-user-001',
    approved_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE g.id IN (
    '55555555-5555-5555-5555-555544445002'::uuid,
    '55555555-5555-5555-5555-555544445006'::uuid,
    '55555555-5555-5555-5555-555544445007'::uuid
)
  AND (SELECT COUNT(*) FROM thesis.thesis_group_member m WHERE m.group_id = g.id) BETWEEN 3 AND 5
  AND g.approval_status <> 'APPROVED';

-- =============================================================================
-- 5. TEACHING SECTIONS, SCHEDULES, GRADES, ATTENDANCE & NOTIFICATIONS FOR
--    DEAN (lecturer-profile-truongkhoa-001) & DEPARTMENT HEAD (lecturer-profile-truongkhoa-002)
-- =============================================================================
UPDATE academic."Section"
SET "lecturerId" = 'lecturer-profile-truongkhoa-001',
    "classroomId" = COALESCE("classroomId", 'classroom-a101'),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" IN ('section-demo-SE401', 'section-demo-SE402')
  AND ("lecturerId" IS DISTINCT FROM 'lecturer-profile-truongkhoa-001' OR "classroomId" IS NULL)
  AND EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile-truongkhoa-001');

UPDATE academic."Section"
SET "lecturerId" = 'lecturer-profile-truongkhoa-002',
    "classroomId" = COALESCE("classroomId", 'classroom-a102'),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" IN ('section-demo-IS401', 'section-demo-IS402')
  AND ("lecturerId" IS DISTINCT FROM 'lecturer-profile-truongkhoa-002' OR "classroomId" IS NULL)
  AND EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile-truongkhoa-002');

INSERT INTO academic."SectionSchedule" ("id", "sectionId", "classroomId", "dayOfWeek", "startTime", "endTime")
SELECT seed.id, seed.section_id, seed.classroom_id, seed.day_of_week, seed.start_time, seed.end_time
FROM (VALUES
    ('schedule-v126-se421', 'section-demo-SE401', 'classroom-a101', 2, '07:30', '10:00'),
    ('schedule-v126-se422', 'section-demo-SE402', 'classroom-a102', 4, '09:30', '12:00'),
    ('schedule-v126-is401', 'section-demo-IS401', 'classroom-a101', 3, '13:00', '15:30'),
    ('schedule-v126-is402', 'section-demo-IS402', 'classroom-a102', 5, '15:30', '18:00')
) AS seed(id, section_id, classroom_id, day_of_week, start_time, end_time)
WHERE EXISTS (SELECT 1 FROM academic."Section" s WHERE s."id" = seed.section_id)
  AND EXISTS (SELECT 1 FROM academic."Classroom" c WHERE c."id" = seed.classroom_id)
  AND NOT EXISTS (SELECT 1 FROM academic."SectionSchedule" ss WHERE ss."sectionId" = seed.section_id)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO academic."GradeItem" ("id", "sectionId", "name", "type", "maxScore", "weight")
SELECT s."id" || '-process-50', s."id", 'Điểm quá trình (ĐQT - 50%)', 'PROCESS', 10, 50
FROM academic."Section" s
WHERE s."id" IN ('section-demo-SE401', 'section-demo-SE402', 'section-demo-IS401', 'section-demo-IS402')
  AND NOT EXISTS (SELECT 1 FROM academic."GradeItem" gi WHERE gi."sectionId" = s."id" AND gi."type" = 'PROCESS')
ON CONFLICT ("id") DO NOTHING;

INSERT INTO academic."GradeItem" ("id", "sectionId", "name", "type", "maxScore", "weight")
SELECT s."id" || '-final-50', s."id", 'Điểm cuối kỳ (ĐCK - 50%)', 'FINAL', 10, 50
FROM academic."Section" s
WHERE s."id" IN ('section-demo-SE401', 'section-demo-SE402', 'section-demo-IS401', 'section-demo-IS402')
  AND NOT EXISTS (SELECT 1 FROM academic."GradeItem" gi WHERE gi."sectionId" = s."id" AND gi."type" = 'FINAL')
ON CONFLICT ("id") DO NOTHING;

INSERT INTO academic."Enrollment" (
    "id", "studentId", "sectionId", "semesterId", "status", "enrolledAt",
    "gradeStatus", "courseId", "roundId", "creditsSnapshot", "finalGrade", "letterGrade", "version"
)
SELECT
    seed.enrollment_id,
    seed.student_id,
    seed.section_id,
    s."semesterId",
    'ENROLLED',
    CURRENT_TIMESTAMP - INTERVAL '20 days',
    'DRAFT',
    s."courseId",
    (SELECT r."id" FROM academic."RegistrationRound" r WHERE r."semesterId" = s."semesterId" ORDER BY r."windowStart" DESC LIMIT 1),
    c."credits",
    seed.final_grade,
    seed.letter_grade,
    1
FROM (VALUES
    ('enrollment-v126-se421-01', 'student-profile-021', 'section-demo-SE401', 8.5, 'A'),
    ('enrollment-v126-se421-02', 'student-profile-022', 'section-demo-SE401', 8.0, 'B+'),
    ('enrollment-v126-se421-03', 'student-profile-023', 'section-demo-SE401', 9.0, 'A+'),
    ('enrollment-v126-se422-01', 'student-profile-024', 'section-demo-SE402', 7.8, 'B'),
    ('enrollment-v126-se422-02', 'student-profile-025', 'section-demo-SE402', 8.6, 'A'),
    ('enrollment-v126-is401-01', 'student-profile-026', 'section-demo-IS401', 8.4, 'B+'),
    ('enrollment-v126-is401-02', 'student-profile-027', 'section-demo-IS401', 9.1, 'A+'),
    ('enrollment-v126-is401-03', 'student-profile-028', 'section-demo-IS401', 7.5, 'B'),
    ('enrollment-v126-is402-01', 'student-profile-021', 'section-demo-IS402', 8.2, 'B+'),
    ('enrollment-v126-is402-02', 'student-profile-022', 'section-demo-IS402', 8.8, 'A')
) AS seed(enrollment_id, student_id, section_id, final_grade, letter_grade)
JOIN academic."Section" s ON s."id" = seed.section_id
JOIN academic."Course" c ON c."id" = s."courseId"
WHERE EXISTS (SELECT 1 FROM academic."Student" st WHERE st."id" = seed.student_id)
  AND NOT EXISTS (
      SELECT 1 FROM academic."Enrollment" e
      WHERE e."studentId" = seed.student_id
        AND (e."sectionId" = seed.section_id OR (e."courseId" = s."courseId" AND e."semesterId" = s."semesterId"))
  )
ON CONFLICT ("id") DO NOTHING;

INSERT INTO academic."StudentGrade" ("id", "enrollmentId", "gradeItemId", "score")
SELECT
    'sg-' || e."id" || '-proc',
    e."id",
    e."sectionId" || '-process-50',
    GREATEST(0, LEAST(10, COALESCE(e."finalGrade", 8.0) - 0.2))
FROM academic."Enrollment" e
WHERE e."id" LIKE 'enrollment-v126-%'
  AND EXISTS (SELECT 1 FROM academic."GradeItem" gi WHERE gi."id" = e."sectionId" || '-process-50')
ON CONFLICT ("id") DO NOTHING;

INSERT INTO academic."StudentGrade" ("id", "enrollmentId", "gradeItemId", "score")
SELECT
    'sg-' || e."id" || '-fin',
    e."id",
    e."sectionId" || '-final-50',
    GREATEST(0, LEAST(10, COALESCE(e."finalGrade", 8.0) + 0.2))
FROM academic."Enrollment" e
WHERE e."id" LIKE 'enrollment-v126-%'
  AND EXISTS (SELECT 1 FROM academic."GradeItem" gi WHERE gi."id" = e."sectionId" || '-final-50')
ON CONFLICT ("id") DO NOTHING;

INSERT INTO academic."Attendance" (
    "id", "studentId", "sectionId", "date", "status", "notes", "createdAt", "updatedAt"
)
SELECT
    'att-' || e."id" || '-w1',
    e."studentId",
    e."sectionId",
    CURRENT_DATE - 7,
    'PRESENT',
    'Tham gia đầy đủ buổi giảng chuyên đề và thảo luận nhóm.',
    CURRENT_TIMESTAMP - INTERVAL '7 days',
    CURRENT_TIMESTAMP - INTERVAL '7 days'
FROM academic."Enrollment" e
WHERE e."id" LIKE 'enrollment-v126-%'
ON CONFLICT ("id") DO NOTHING;

UPDATE academic."Section" section
SET "enrolledCount" = (
        SELECT COUNT(*) FROM academic."Enrollment" enrollment
        WHERE enrollment."sectionId" = section."id"
          AND enrollment."status" IN ('ENROLLED', 'PENDING', 'CONFIRMED')),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE section."id" IN ('section-demo-SE401', 'section-demo-SE402', 'section-demo-IS401', 'section-demo-IS402')
  AND section."enrolledCount" <> (
        SELECT COUNT(*) FROM academic."Enrollment" enrollment
        WHERE enrollment."sectionId" = section."id"
          AND enrollment."status" IN ('ENROLLED', 'PENDING', 'CONFIRMED'));

INSERT INTO notifications.notification (
    id, user_id, title, message, type, link, is_read, read_at, created_at, updated_at
)
SELECT
    seed.id,
    seed.user_id,
    seed.title,
    seed.message,
    seed.type,
    seed.link,
    seed.is_read,
    CASE WHEN seed.is_read THEN CURRENT_TIMESTAMP - INTERVAL '2 hours' ELSE NULL END,
    CURRENT_TIMESTAMP - seed.age,
    CURRENT_TIMESTAMP - seed.age
FROM (VALUES
    ('notif-v126-tk-01', 'truongkhoa-user-001',
     'Tổng hợp đăng ký Khóa luận tốt nghiệp Khoa CNTT',
     'Các bộ môn trực thuộc Khoa Công nghệ Thông tin đã cập nhật danh mục đề tài và danh sách nhóm sinh viên chờ duyệt.',
     'ACADEMIC', '/vi/dashboard/dean', FALSE, INTERVAL '2 hours'),
    ('notif-v126-tk-02', 'truongkhoa-user-001',
     'Lịch giảng dạy học phần SE421 & SE422',
     'Lịch giảng dạy và danh sách sinh viên lớp Kiến trúc phần mềm nâng cao (SE421-01) đã sẵn sàng để điểm danh và nhập điểm.',
     'SCHEDULE', '/vi/dashboard/lecturer/schedule', FALSE, INTERVAL '1 day'),
    ('notif-v126-tk-03', 'truongkhoa-user-001',
     'Đề xuất lập Hội đồng bảo vệ Khóa luận tốt nghiệp',
     'Văn phòng Khoa đề nghị rà soát phân công giảng viên hướng dẫn và giảng viên phản biện trước hạn khóa sổ báo cáo.',
     'REMINDER', '/vi/admin/thesis', TRUE, INTERVAL '3 days'),
    ('notif-v126-tbm-01', 'truongkhoa-user-002',
     'Đề tài và nhóm khóa luận thuộc Bộ môn cần xét duyệt',
     'Có nhóm sinh viên mới đăng ký đề tài thuộc Bộ môn Công nghệ Thông tin đang chờ Trưởng bộ môn phê duyệt.',
     'ACADEMIC', '/vi/dashboard/thesis', FALSE, INTERVAL '3 hours'),
    ('notif-v126-tbm-02', 'truongkhoa-user-002',
     'Lịch giảng dạy học phần IS401 & IS402',
     'Danh sách lớp Hệ thống thông tin doanh nghiệp (IS401-01) và Phân tích dữ liệu kinh doanh (IS402-01) đã cập nhật.',
     'SCHEDULE', '/vi/dashboard/lecturer/schedule', FALSE, INTERVAL '1 day')
) AS seed(id, user_id, title, message, type, link, is_read, age)
WHERE EXISTS (SELECT 1 FROM campuscore_auth."User" u WHERE u."id" = seed.user_id)
  AND NOT EXISTS (SELECT 1 FROM notifications.notification n WHERE n.id = seed.id);
