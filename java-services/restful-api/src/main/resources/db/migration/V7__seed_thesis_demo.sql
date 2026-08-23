INSERT INTO thesis.thesis_registration_round (
    id,
    name,
    thesis_type,
    registration_start,
    registration_end,
    proposal_publish_at,
    report_date,
    defense_date,
    status
)
SELECT
    '22222222-2222-2222-2222-222222222101',
    'Đồ án tốt nghiệp 2026-2027',
    'CAPSTONE',
    '2026-08-01T00:00:00Z',
    '2026-09-30T23:59:59Z',
    '2026-10-05T00:00:00Z',
    '2027-01-05T00:00:00Z',
    '2027-01-15T00:00:00Z',
    'REGISTRATION_OPEN'
WHERE NOT EXISTS (
    SELECT 1
    FROM thesis.thesis_registration_round
    WHERE id = '22222222-2222-2222-2222-222222222101'
);

INSERT INTO thesis.thesis_topic (
    id,
    round_id,
    department_id,
    title,
    description,
    max_groups,
    status,
    created_by
)
SELECT
    '22222222-2222-2222-2222-222222222201',
    '22222222-2222-2222-2222-222222222101',
    'department-demo',
    'Cổng quản lý sinh viên RESTful API',
    'Xây dựng cổng quản lý sinh viên bằng Spring Boot, PostgreSQL, Next.js và ứng dụng di động.',
    3,
    'PUBLISHED',
    'lecturer-user'
WHERE NOT EXISTS (
    SELECT 1
    FROM thesis.thesis_topic
    WHERE id = '22222222-2222-2222-2222-222222222201'
);

INSERT INTO thesis.thesis_topic_supervisor (
    id,
    topic_id,
    lecturer_id,
    supervisor_order
)
SELECT
    '22222222-2222-2222-2222-222222222202',
    '22222222-2222-2222-2222-222222222201',
    'lecturer-profile',
    1
WHERE NOT EXISTS (
    SELECT 1
    FROM thesis.thesis_topic_supervisor
    WHERE id = '22222222-2222-2222-2222-222222222202'
);

INSERT INTO thesis.thesis_group (
    id,
    round_id,
    leader_student_id,
    topic_id,
    status,
    approval_status
)
SELECT
    '22222222-2222-2222-2222-222222222301',
    '22222222-2222-2222-2222-222222222101',
    'student-profile',
    '22222222-2222-2222-2222-222222222201',
    'DRAFT',
    'PENDING'
WHERE NOT EXISTS (
    SELECT 1
    FROM thesis.thesis_group
    WHERE id = '22222222-2222-2222-2222-222222222301'
);

INSERT INTO thesis.thesis_group_member (
    id,
    group_id,
    round_id,
    student_id,
    member_order,
    is_leader
)
SELECT
    '22222222-2222-2222-2222-222222222302',
    '22222222-2222-2222-2222-222222222301',
    '22222222-2222-2222-2222-222222222101',
    'student-profile',
    1,
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM thesis.thesis_group_member
    WHERE id = '22222222-2222-2222-2222-222222222302'
);
