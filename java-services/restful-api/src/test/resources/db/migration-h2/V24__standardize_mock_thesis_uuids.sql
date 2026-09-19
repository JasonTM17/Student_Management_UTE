-- =====================================================================
-- Migration V24 (H2 Test Twin of V69): Standardize Mock Thesis UUIDs
-- =====================================================================

INSERT INTO thesis.thesis_registration_round (
    id, name, thesis_type, registration_start, registration_end,
    proposal_publish_at, lecturer_submit_start, lecturer_submit_end,
    gvpb_deadline, report_date, defense_date, status, version, created_at, updated_at
)
SELECT
    'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60',
    name, thesis_type, registration_start, registration_end,
    proposal_publish_at, lecturer_submit_start, lecturer_submit_end,
    gvpb_deadline, report_date, defense_date, status, version, created_at, CURRENT_TIMESTAMP
FROM thesis.thesis_registration_round
WHERE id = '22222222-2222-2222-2222-222222222101'
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_registration_round WHERE id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60');

INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups,
    status, created_by, final_score, result_status, version, created_at, updated_at
)
SELECT
    'd7e2b345-0c8f-423e-9412-2c3d4e5f6a71',
    'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60',
    department_id, title, description, max_groups,
    status, created_by, final_score, result_status, version, created_at, CURRENT_TIMESTAMP
FROM thesis.thesis_topic
WHERE id = '22222222-2222-2222-2222-222222222201'
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_topic WHERE id = 'd7e2b345-0c8f-423e-9412-2c3d4e5f6a71');

INSERT INTO thesis.thesis_topic_supervisor (
    id, topic_id, lecturer_id, supervisor_order, created_at
)
SELECT
    'e8f3c456-1d9a-434f-a523-3d4e5f6a7b82',
    'd7e2b345-0c8f-423e-9412-2c3d4e5f6a71',
    lecturer_id, supervisor_order, created_at
FROM thesis.thesis_topic_supervisor
WHERE topic_id = '22222222-2222-2222-2222-222222222201'
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_topic_supervisor WHERE id = 'e8f3c456-1d9a-434f-a523-3d4e5f6a7b82');

INSERT INTO thesis.thesis_group (
    id, round_id, leader_student_id, topic_id, status,
    approval_status, approved_by, approved_at, rejection_reason,
    version, created_at, updated_at
)
SELECT
    'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93',
    'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60',
    leader_student_id,
    'd7e2b345-0c8f-423e-9412-2c3d4e5f6a71',
    status, approval_status, approved_by, approved_at, rejection_reason,
    version, created_at, CURRENT_TIMESTAMP
FROM thesis.thesis_group
WHERE id = '22222222-2222-2222-2222-222222222301'
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group WHERE id = 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93');

INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader,
    created_at, display_name, contact, is_external
)
SELECT
    '0ab5e678-3f1c-456b-8745-5f6a7b8c9d04',
    'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93',
    'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60',
    student_id, member_order, is_leader,
    created_at, display_name, contact, is_external
FROM thesis.thesis_group_member
WHERE id = '22222222-2222-2222-2222-222222222302'
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member WHERE id = '0ab5e678-3f1c-456b-8745-5f6a7b8c9d04');

UPDATE thesis.thesis_group_member
SET group_id = 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93',
    round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'
WHERE group_id = '22222222-2222-2222-2222-222222222301';

UPDATE thesis.thesis_group_member
SET round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'
WHERE round_id = '22222222-2222-2222-2222-222222222101';

UPDATE thesis.thesis_group
SET round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'
WHERE round_id = '22222222-2222-2222-2222-222222222101';

UPDATE thesis.thesis_topic
SET round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'
WHERE round_id = '22222222-2222-2222-2222-222222222101';

DELETE FROM thesis.thesis_group_member WHERE id = '22222222-2222-2222-2222-222222222302';
DELETE FROM thesis.thesis_group WHERE id = '22222222-2222-2222-2222-222222222301';
DELETE FROM thesis.thesis_topic_supervisor WHERE id = '22222222-2222-2222-2222-222222222202' OR topic_id = '22222222-2222-2222-2222-222222222201';
DELETE FROM thesis.thesis_topic WHERE id = '22222222-2222-2222-2222-222222222201';
DELETE FROM thesis.thesis_registration_round WHERE id = '22222222-2222-2222-2222-222222222101';
