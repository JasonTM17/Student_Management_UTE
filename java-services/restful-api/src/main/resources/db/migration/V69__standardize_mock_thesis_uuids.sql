-- =====================================================================
-- Migration V69: Standardize Mock Thesis UUIDs to RFC 4122 Standard
-- Replaces synthetic demo UUIDs (22222222-2222-2222-2222-222222222101 / 222222222201)
-- with canonical UUIDv4 identifiers to eliminate repetitive '22222222...' address bar noise.
-- =====================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM thesis.thesis_registration_round WHERE id = '22222222-2222-2222-2222-222222222101'::uuid) THEN

        -- 1. Insert standardized replacement round
        INSERT INTO thesis.thesis_registration_round (
            id, name, thesis_type, registration_start, registration_end,
            proposal_publish_at, lecturer_submit_start, lecturer_submit_end,
            gvpb_deadline, report_date, defense_date, status, version, created_at, updated_at
        )
        SELECT
            'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid,
            name, thesis_type, registration_start, registration_end,
            proposal_publish_at, lecturer_submit_start, lecturer_submit_end,
            gvpb_deadline, report_date, defense_date, status, version, created_at, CURRENT_TIMESTAMP
        FROM thesis.thesis_registration_round
        WHERE id = '22222222-2222-2222-2222-222222222101'::uuid
        ON CONFLICT (id) DO NOTHING;

        -- 2. Insert standardized replacement topic
        INSERT INTO thesis.thesis_topic (
            id, round_id, department_id, title, description, max_groups,
            status, created_by, final_score, result_status, version, created_at, updated_at
        )
        SELECT
            'd7e2b345-0c8f-423e-9412-2c3d4e5f6a71'::uuid,
            'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid,
            department_id, title, description, max_groups,
            status, created_by, final_score, result_status, version, created_at, CURRENT_TIMESTAMP
        FROM thesis.thesis_topic
        WHERE id = '22222222-2222-2222-2222-222222222201'::uuid
        ON CONFLICT (id) DO NOTHING;

        -- 3. Insert standardized replacement thesis group
        INSERT INTO thesis.thesis_group (
            id, round_id, leader_student_id, topic_id, status,
            approval_status, approved_by, approved_at, rejection_reason,
            version, created_at, updated_at
        )
        SELECT
            'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid,
            'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid,
            leader_student_id,
            'd7e2b345-0c8f-423e-9412-2c3d4e5f6a71'::uuid,
            status, approval_status, approved_by, approved_at, rejection_reason,
            version, created_at, CURRENT_TIMESTAMP
        FROM thesis.thesis_group
        WHERE id = '22222222-2222-2222-2222-222222222301'::uuid
        ON CONFLICT (id) DO NOTHING;

        -- 4. Update topic supervisor to point to new topic and new ID
        UPDATE thesis.thesis_topic_supervisor
        SET id = 'e8f3c456-1d9a-434f-a523-3d4e5f6a7b82'::uuid,
            topic_id = 'd7e2b345-0c8f-423e-9412-2c3d4e5f6a71'::uuid
        WHERE topic_id = '22222222-2222-2222-2222-222222222201'::uuid;

        -- 5. Update group member to point to new group, new round, and new ID
        UPDATE thesis.thesis_group_member
        SET id = '0ab5e678-3f1c-456b-8745-5f6a7b8c9d04'::uuid,
            group_id = 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid,
            round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid
        WHERE group_id = '22222222-2222-2222-2222-222222222301'::uuid;

        -- 6. Update all other group members in that round to the new round
        UPDATE thesis.thesis_group_member
        SET round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid
        WHERE round_id = '22222222-2222-2222-2222-222222222101'::uuid;

        -- 7. Update group reports
        UPDATE thesis.thesis_group_report
        SET round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid,
            group_id = CASE
                WHEN group_id = '22222222-2222-2222-2222-222222222301'::uuid
                THEN 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid
                ELSE group_id
            END
        WHERE round_id = '22222222-2222-2222-2222-222222222101'::uuid
           OR group_id = '22222222-2222-2222-2222-222222222301'::uuid;

        -- 8. Update all other groups in that round to the new round
        UPDATE thesis.thesis_group
        SET round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid
        WHERE round_id = '22222222-2222-2222-2222-222222222101'::uuid
          AND id != '22222222-2222-2222-2222-222222222301'::uuid;

        -- 9. Update all other topics in that round to the new round
        UPDATE thesis.thesis_topic
        SET round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid
        WHERE round_id = '22222222-2222-2222-2222-222222222101'::uuid
          AND id != '22222222-2222-2222-2222-222222222201'::uuid;

        -- 10. Migrate councils in that round to the new round
        UPDATE thesis.thesis_council
        SET round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid
        WHERE round_id = '22222222-2222-2222-2222-222222222101'::uuid;

        -- 11. Safely delete obsolete legacy synthetic seed rows in reverse dependency order
        DELETE FROM thesis.thesis_group WHERE id = '22222222-2222-2222-2222-222222222301'::uuid;
        DELETE FROM thesis.thesis_topic WHERE id = '22222222-2222-2222-2222-222222222201'::uuid;
        DELETE FROM thesis.thesis_registration_round WHERE id = '22222222-2222-2222-2222-222222222101'::uuid;

        -- 12. Update admin audit log references if any exist
        UPDATE campuscore_audit."AdminAudit"
        SET "entityId" = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'
        WHERE "entityId" = '22222222-2222-2222-2222-222222222101';

        UPDATE campuscore_audit."AdminAudit"
        SET "entityId" = 'd7e2b345-0c8f-423e-9412-2c3d4e5f6a71'
        WHERE "entityId" = '22222222-2222-2222-2222-222222222201';

    END IF;
END $$;
