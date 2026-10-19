-- V128__standardize_academic_codes_and_thesis_uuids.sql
-- Purpose:
--   1. Remove leftover synthetic @campuscore.test / otp-test probe accounts
--      that have no real academic history so admin user/lecturer/student lists
--      open on clean institutional records instead of random test codes
--      (GVLFMV9*, SVLFMV9*, SPB9FPRLT, SP4019FMXZA).
--   2. Standardize lecturer employeeId codes from LEC-DEMO-001..035 /
--      LEC-DEMO-TK001..002 to official HCMUTE-style codes (GV101001..GV101035,
--      GV101101, GV101102).
--   3. Normalize Vietnamese lecturer display names where V43 placed the
--      academic honorific inside firstName ("Trần Đình" + "GS.TS. Khôi" ->
--      "Trần Đình GS.TS. Khôi") so every lecturer renders in standard
--      Vietnamese order ("GS.TS. Trần Đình Khôi"), and give the seeded Dean and
--      Department Head realistic academic names ("PGS.TS. Lê Văn Vinh" and
--      "TS. Nguyễn Minh Đạo").
--   4. Standardize studentId numbers from CS-DEMO-xxx and 7-digit 24110xx to
--      8-digit HCMUTE student numbers (22110xxx, 241100xx), keeping the four
--      canonical demo team numbers (24110054, 24110345, 24110051, 24162050)
--      untouched.
--   5. Replace repetitive synthetic thesis UUIDs (44444444-..., 55555555-...,
--      66666666-...) with deterministic RFC 4122 UUIDv4 identifiers (following
--      V69 precedent) while keeping topic 018f4a7c-3b12-4d90-a5e1-7c82b9045001
--      first by id in round 22d65ee6 for the disposable report-upload E2E test.

-- =============================================================================
-- 1. CLEAN UP SYNTHETIC @campuscore.test / OTP-TEST PROBE ACCOUNTS
-- =============================================================================
DELETE FROM campuscore_auth."Session" s
USING campuscore_auth."User" u
WHERE s."userId" = u."id"
  AND (u."email" LIKE '%@campuscore.test'
       OR EXISTS (SELECT 1 FROM academic."Student" st WHERE st."userId" = u."id" AND st."studentId" = 'otp-test-001'));

DELETE FROM campuscore_auth."TwoFactorChallenge" c
USING campuscore_auth."User" u
WHERE c."userId" = u."id"
  AND (u."email" LIKE '%@campuscore.test'
       OR EXISTS (SELECT 1 FROM academic."Student" st WHERE st."userId" = u."id" AND st."studentId" = 'otp-test-001'));

DELETE FROM campuscore_auth."PasswordResetToken" prt
USING campuscore_auth."User" u
WHERE prt."userId" = u."id"
  AND (u."email" LIKE '%@campuscore.test'
       OR EXISTS (SELECT 1 FROM academic."Student" st WHERE st."userId" = u."id" AND st."studentId" = 'otp-test-001'));

DELETE FROM notifications.notification n
USING campuscore_auth."User" u
WHERE n.user_id = u."id"
  AND (u."email" LIKE '%@campuscore.test'
       OR EXISTS (SELECT 1 FROM academic."Student" st WHERE st."userId" = u."id" AND st."studentId" = 'otp-test-001'));

DELETE FROM academic."Student" st
USING campuscore_auth."User" u
WHERE st."userId" = u."id"
  AND (u."email" LIKE '%@campuscore.test' OR st."studentId" = 'otp-test-001')
  AND NOT EXISTS (SELECT 1 FROM academic."Enrollment" e WHERE e."studentId" = st."id")
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member gm WHERE gm.student_id = st."id");

DELETE FROM academic."Lecturer" l
USING campuscore_auth."User" u
WHERE l."userId" = u."id"
  AND u."email" LIKE '%@campuscore.test'
  AND NOT EXISTS (SELECT 1 FROM academic."Section" sec WHERE sec."lecturerId" = l."id")
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_topic_supervisor ts WHERE ts.lecturer_id = l."id")
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_council_member cm WHERE cm.lecturer_id = l."id");

DELETE FROM campuscore_auth."UserRole" ur
USING campuscore_auth."User" u
WHERE ur."userId" = u."id"
  AND (u."email" LIKE '%@campuscore.test' OR u."id" = 'a8c62be9-35df-4cbb-bcc5-2cf16354db75')
  AND NOT EXISTS (SELECT 1 FROM academic."Student" st WHERE st."userId" = u."id")
  AND NOT EXISTS (SELECT 1 FROM academic."Lecturer" l WHERE l."userId" = u."id");

DELETE FROM campuscore_auth."User" u
WHERE (u."email" LIKE '%@campuscore.test' OR u."id" = 'a8c62be9-35df-4cbb-bcc5-2cf16354db75')
  AND NOT EXISTS (SELECT 1 FROM academic."Student" st WHERE st."userId" = u."id")
  AND NOT EXISTS (SELECT 1 FROM academic."Lecturer" l WHERE l."userId" = u."id");

-- =============================================================================
-- 2. STANDARDIZE LECTURER EMPLOYEE CODES (LEC-DEMO-* -> GV101*)
-- =============================================================================
UPDATE academic."Lecturer" l
SET "employeeId" = 'GV101' || substring(l."employeeId" from 10),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE l."employeeId" ~ '^LEC-DEMO-[0-9]{3}$'
  AND NOT EXISTS (
      SELECT 1 FROM academic."Lecturer" other
      WHERE other."employeeId" = 'GV101' || substring(l."employeeId" from 10)
  );

UPDATE academic."Lecturer"
SET "employeeId" = 'GV101101',
    "title" = 'PGS.TS.',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'lecturer-profile-truongkhoa-001'
  AND ("employeeId" = 'LEC-DEMO-TK001' OR "title" IS DISTINCT FROM 'PGS.TS.')
  AND NOT EXISTS (
      SELECT 1 FROM academic."Lecturer" other
      WHERE other."employeeId" = 'GV101101' AND other."id" <> 'lecturer-profile-truongkhoa-001'
  );

UPDATE academic."Lecturer"
SET "employeeId" = 'GV101102',
    "title" = 'TS.',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'lecturer-profile-truongkhoa-002'
  AND ("employeeId" = 'LEC-DEMO-TK002' OR "title" IS DISTINCT FROM 'TS.')
  AND NOT EXISTS (
      SELECT 1 FROM academic."Lecturer" other
      WHERE other."employeeId" = 'GV101102' AND other."id" <> 'lecturer-profile-truongkhoa-002'
  );

-- =============================================================================
-- 3. NORMALIZE LECTURER DISPLAY NAMES (HONORIFIC PREFIX ON LASTNAME)
-- =============================================================================
UPDATE campuscore_auth."User"
SET "lastName" = 'PGS.TS. ' || "lastName",
    "firstName" = trim(substring("firstName" from 9)),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "firstName" LIKE 'PGS.TS. %'
  AND "lastName" NOT LIKE '%TS.%'
  AND "lastName" NOT LIKE '%ThS.%';

UPDATE campuscore_auth."User"
SET "lastName" = 'GS.TS. ' || "lastName",
    "firstName" = trim(substring("firstName" from 8)),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "firstName" LIKE 'GS.TS. %'
  AND "lastName" NOT LIKE '%TS.%'
  AND "lastName" NOT LIKE '%ThS.%';

UPDATE campuscore_auth."User"
SET "lastName" = 'ThS. ' || "lastName",
    "firstName" = trim(substring("firstName" from 6)),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "firstName" LIKE 'ThS. %'
  AND "lastName" NOT LIKE '%TS.%'
  AND "lastName" NOT LIKE '%ThS.%';

UPDATE campuscore_auth."User"
SET "lastName" = 'TS. ' || "lastName",
    "firstName" = trim(substring("firstName" from 5)),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "firstName" LIKE 'TS. %'
  AND "lastName" NOT LIKE '%TS.%'
  AND "lastName" NOT LIKE '%ThS.%';

UPDATE campuscore_auth."User"
SET "lastName" = 'PGS.TS. Trần',
    "firstName" = 'Minh Quân',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'lecturer-user-002'
  AND "lastName" = 'Trần'
  AND "firstName" = 'Văn Bình';

UPDATE campuscore_auth."User"
SET "lastName" = 'PGS.TS. Lê Văn',
    "firstName" = 'Vinh',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'truongkhoa-user-001'
  AND ("firstName" = 'Demo' OR "lastName" = 'Trưởng khoa CNTT');

UPDATE campuscore_auth."User"
SET "lastName" = 'TS. Nguyễn Minh',
    "firstName" = 'Đạo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'truongkhoa-user-002'
  AND ("firstName" = 'Demo' OR "lastName" = 'Trưởng bộ môn CNTT');

-- =============================================================================
-- 4. STANDARDIZE STUDENT NUMBERS (CS-DEMO-xxx -> 22110xxx, 24110xx -> 241100xx)
-- =============================================================================
UPDATE academic."Student" s
SET "studentId" = '22110' || substring(s."studentId" from 9),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE s."studentId" ~ '^CS-DEMO-[0-9]{3}$'
  AND s."id" NOT IN ('student-profile', 'student-profile-058', 'student-profile-059', 'student-profile-team-004')
  AND NOT EXISTS (
      SELECT 1 FROM academic."Student" other
      WHERE other."studentId" = '22110' || substring(s."studentId" from 9)
  );

UPDATE academic."Student" s
SET "studentId" = '241100' || substring(s."studentId" from 6),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE s."studentId" ~ '^24110[0-9]{2}$'
  AND length(s."studentId") = 7
  AND NOT EXISTS (
      SELECT 1 FROM academic."Student" other
      WHERE other."studentId" = '241100' || substring(s."studentId" from 6)
  );

-- =============================================================================
-- 5. STANDARDIZE SYNTHETIC THESIS UUIDS (44444444-*, 55555555-*, 66666666-*)
--    INTO DETERMINISTIC RFC 4122 UUIDv4 IDENTIFIERS
-- =============================================================================
SET CONSTRAINTS ALL IMMEDIATE;
ALTER TABLE thesis.thesis_group DISABLE TRIGGER thesis_legacy_group_size_marker;
ALTER TABLE thesis.thesis_group DISABLE TRIGGER thesis_cancelled_group_terminal;
ALTER TABLE thesis.thesis_group DISABLE TRIGGER thesis_release_cancelled_group_memberships;
ALTER TABLE thesis.thesis_group_member DISABLE TRIGGER thesis_group_member_legacy_size_read_only;
ALTER TABLE thesis.thesis_group_member DISABLE TRIGGER thesis_group_member_active_participation;
SET CONSTRAINTS ALL DEFERRED;

DO $$
DECLARE
    rec RECORD;
    new_id UUID;
    h TEXT;
BEGIN
    -- 5a. Re-key thesis_topic rows whose id starts with 44444444-
    FOR rec IN
        SELECT * FROM thesis.thesis_topic
        WHERE id::text LIKE '44444444-%'
        ORDER BY id
    LOOP
        IF rec.id = '44444444-4444-4444-4444-444444445001'::uuid THEN
            -- Keep this topic lexicographically first in round 22d65ee6 for
            -- scripts/course-e2e-fixture.mjs (ORDER BY t.id LIMIT 1).
            new_id := '018f4a7c-3b12-4d90-a5e1-7c82b9045001'::uuid;
        ELSE
            h := md5('campuscore-v128-topic:' || rec.id::text);
            new_id := (
                substring(h from 1 for 8) || '-' ||
                substring(h from 9 for 4) || '-4' ||
                substring(h from 14 for 3) || '-a' ||
                substring(h from 18 for 3) || '-' ||
                substring(h from 21 for 12)
            )::uuid;
        END IF;

        INSERT INTO thesis.thesis_topic (
            id, round_id, department_id, title, description, max_groups,
            status, created_by, version, created_at, updated_at,
            final_score, final_score_finalized_by, final_score_finalized_at,
            result_status, gvpb_lecturer_id
        ) VALUES (
            new_id, rec.round_id, rec.department_id, rec.title, rec.description, rec.max_groups,
            rec.status, rec.created_by, rec.version, rec.created_at, rec.updated_at,
            rec.final_score, rec.final_score_finalized_by, rec.final_score_finalized_at,
            rec.result_status, rec.gvpb_lecturer_id
        )
        ON CONFLICT (id) DO NOTHING;

        UPDATE thesis.thesis_topic_supervisor SET topic_id = new_id WHERE topic_id = rec.id;
        UPDATE thesis.thesis_group SET topic_id = new_id WHERE topic_id = rec.id;
        UPDATE thesis.thesis_council_topic SET topic_id = new_id WHERE topic_id = rec.id;
        UPDATE thesis.thesis_topic_score SET topic_id = new_id WHERE topic_id = rec.id;

        DELETE FROM thesis.thesis_topic WHERE id = rec.id;
    END LOOP;

    -- 5b. Re-key thesis_topic_supervisor rows whose id starts with 44444444-
    FOR rec IN
        SELECT id FROM thesis.thesis_topic_supervisor
        WHERE id::text LIKE '44444444-%'
        ORDER BY id
    LOOP
        h := md5('campuscore-v128-supervisor:' || rec.id::text);
        new_id := (
            substring(h from 1 for 8) || '-' ||
            substring(h from 9 for 4) || '-4' ||
            substring(h from 14 for 3) || '-a' ||
            substring(h from 18 for 3) || '-' ||
            substring(h from 21 for 12)
        )::uuid;
        UPDATE thesis.thesis_topic_supervisor SET id = new_id WHERE id = rec.id;
    END LOOP;

    -- 5c. Re-key thesis_group rows whose id starts with 55555555-
    FOR rec IN
        SELECT * FROM thesis.thesis_group
        WHERE id::text LIKE '55555555-%'
        ORDER BY id
    LOOP
        h := md5('campuscore-v128-group:' || rec.id::text);
        new_id := (
            substring(h from 1 for 8) || '-' ||
            substring(h from 9 for 4) || '-4' ||
            substring(h from 14 for 3) || '-a' ||
            substring(h from 18 for 3) || '-' ||
            substring(h from 21 for 12)
        )::uuid;

        INSERT INTO thesis.thesis_group (
            id, round_id, leader_student_id, topic_id, status,
            approval_status, approved_by, approved_at, rejection_reason,
            version, created_at, updated_at, legacy_group_size_exception
        ) VALUES (
            new_id, rec.round_id, rec.leader_student_id, rec.topic_id, rec.status,
            'PENDING', rec.approved_by, rec.approved_at, rec.rejection_reason,
            rec.version, rec.created_at, rec.updated_at, FALSE
        )
        ON CONFLICT (id) DO NOTHING;

        UPDATE thesis.thesis_group_member SET group_id = new_id WHERE group_id = rec.id;
        UPDATE thesis.thesis_group_report SET group_id = new_id WHERE group_id = rec.id;

        UPDATE thesis.thesis_group
        SET approval_status = rec.approval_status,
            legacy_group_size_exception = rec.legacy_group_size_exception
        WHERE id = new_id;

        DELETE FROM thesis.thesis_group WHERE id = rec.id;
    END LOOP;

    -- 5d. Re-key thesis_group_member rows whose id starts with 66666666-
    FOR rec IN
        SELECT id FROM thesis.thesis_group_member
        WHERE id::text LIKE '66666666-%'
        ORDER BY id
    LOOP
        h := md5('campuscore-v128-member:' || rec.id::text);
        new_id := (
            substring(h from 1 for 8) || '-' ||
            substring(h from 9 for 4) || '-4' ||
            substring(h from 14 for 3) || '-a' ||
            substring(h from 18 for 3) || '-' ||
            substring(h from 21 for 12)
        )::uuid;
        UPDATE thesis.thesis_group_member SET id = new_id WHERE id = rec.id;
    END LOOP;
END $$;

SET CONSTRAINTS ALL IMMEDIATE;
ALTER TABLE thesis.thesis_group ENABLE TRIGGER thesis_legacy_group_size_marker;
ALTER TABLE thesis.thesis_group ENABLE TRIGGER thesis_cancelled_group_terminal;
ALTER TABLE thesis.thesis_group ENABLE TRIGGER thesis_release_cancelled_group_memberships;
ALTER TABLE thesis.thesis_group_member ENABLE TRIGGER thesis_group_member_legacy_size_read_only;
ALTER TABLE thesis.thesis_group_member ENABLE TRIGGER thesis_group_member_active_participation;
SET CONSTRAINTS ALL DEFERRED;

DO $$
DECLARE
    checked_group UUID;
BEGIN
    FOR checked_group IN SELECT id FROM thesis.thesis_group ORDER BY id LOOP
        PERFORM thesis.validate_group_membership_state(checked_group);
    END LOOP;
END $$;
