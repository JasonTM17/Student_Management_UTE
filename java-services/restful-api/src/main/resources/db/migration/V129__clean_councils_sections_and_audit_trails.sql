-- V129__clean_councils_sections_and_audit_trails.sql
-- Purpose:
--   1. Standardize thesis defense council names (replacing test/audit labels
--      such as "WUKONG Council", "Hoi dong KLTN Audit", "Hoi dong TLCN luong
--      that 0912", "Hội đồng 1"), complete 3-member roster on round 22d65ee6's
--      council, and re-key repetitive 55555555-* council UUIDs to deterministic
--      RFC 4122 UUIDv4 identifiers.
--   2. Assign an active mechanical-engineering lecturer to sections
--      section-demo-MF401..MF404 where lecturerId was NULL.
--   3. Replace the placeholder "Admin Hai Demo" name on admin-user-002 with an
--      institutional Four-Eyes reviewer identity ("Ban Kiểm duyệt Học vụ").
--   4. Remove leftover @campuscore.test probe rows from campuscore_audit."AdminAudit"
--      and backfill NULL actorLabel values from campuscore_auth."User".

-- =============================================================================
-- 1. STANDARDIZE THESIS COUNCIL NAMES & RE-KEY 55555555-* COUNCIL UUIDS
-- =============================================================================
UPDATE thesis.thesis_council
SET name = 'Hội đồng ĐACN Kỹ thuật Phần mềm & Dữ liệu 01',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'WUKONG Council';

UPDATE thesis.thesis_council
SET name = 'Hội đồng KLTN Công nghệ Phần mềm & Trí tuệ Nhân tạo 01',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'Hoi dong KLTN Audit';

UPDATE thesis.thesis_council
SET name = 'Hội đồng TLCN Hệ thống Thông tin & Mạng máy tính 01',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'Hoi dong TLCN luong that 0912';

UPDATE thesis.thesis_council
SET name = 'Hội đồng TLCN Công nghệ Thông tin 01',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'Hội đồng 1'
  AND round_id = '22d65ee6-f485-40ae-a66b-528d35007745'::uuid;

-- Ensure the TLCN council in round 22d65ee6 has a complete 3-member roster
INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role, created_at)
SELECT
    'b4e17c92-6a31-4f80-9c12-3d5e7a9b1001'::uuid,
    c.id,
    'lecturer-profile-029',
    'SECRETARY',
    CURRENT_TIMESTAMP
FROM thesis.thesis_council c
WHERE c.round_id = '22d65ee6-f485-40ae-a66b-528d35007745'::uuid
  AND EXISTS (SELECT 1 FROM academic."Lecturer" l WHERE l."id" = 'lecturer-profile-029' AND l."isActive" = TRUE)
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_council_member cm WHERE cm.council_id = c.id AND cm.member_role = 'SECRETARY')
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_council_member cm WHERE cm.council_id = c.id AND cm.lecturer_id = 'lecturer-profile-029')
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role, created_at)
SELECT
    'b4e17c92-6a31-4f80-9c12-3d5e7a9b1002'::uuid,
    c.id,
    'lecturer-profile-028',
    'MEMBER',
    CURRENT_TIMESTAMP
FROM thesis.thesis_council c
WHERE c.round_id = '22d65ee6-f485-40ae-a66b-528d35007745'::uuid
  AND EXISTS (SELECT 1 FROM academic."Lecturer" l WHERE l."id" = 'lecturer-profile-028' AND l."isActive" = TRUE)
  AND (SELECT COUNT(*) FROM thesis.thesis_council_member cm WHERE cm.council_id = c.id) < 3
  AND NOT EXISTS (SELECT 1 FROM thesis.thesis_council_member cm WHERE cm.council_id = c.id AND cm.lecturer_id = 'lecturer-profile-028')
LIMIT 1
ON CONFLICT DO NOTHING;

DO $$
DECLARE
    rec RECORD;
    new_id UUID;
    h TEXT;
BEGIN
    FOR rec IN
        SELECT * FROM thesis.thesis_council
        WHERE id::text LIKE '55555555-%'
        ORDER BY id
    LOOP
        h := md5('campuscore-v129-council:' || rec.id::text);
        new_id := (
            substring(h from 1 for 8) || '-' ||
            substring(h from 9 for 4) || '-4' ||
            substring(h from 14 for 3) || '-a' ||
            substring(h from 18 for 3) || '-' ||
            substring(h from 21 for 12)
        )::uuid;

        INSERT INTO thesis.thesis_council (
            id, round_id, name, status, created_by, created_at, updated_at
        ) VALUES (
            new_id, rec.round_id, rec.name, rec.status, rec.created_by, rec.created_at, rec.updated_at
        )
        ON CONFLICT (id) DO NOTHING;

        UPDATE thesis.thesis_council_member SET council_id = new_id WHERE council_id = rec.id;
        UPDATE thesis.thesis_council_topic SET council_id = new_id WHERE council_id = rec.id;
        UPDATE thesis.thesis_topic_score SET council_id = new_id WHERE council_id = rec.id;

        DELETE FROM thesis.thesis_council WHERE id = rec.id;
    END LOOP;
END $$;

-- =============================================================================
-- 2. ASSIGN LECTURER TO UNASSIGNED MF401..MF404 SECTIONS
-- =============================================================================
UPDATE academic."Section"
SET "lecturerId" = 'lecturer-profile-033',
    "classroomId" = COALESCE("classroomId", 'classroom-a101'),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" IN ('section-demo-MF401', 'section-demo-MF402', 'section-demo-MF403', 'section-demo-MF404')
  AND "lecturerId" IS NULL
  AND EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile-033' AND "isActive" = TRUE);

-- =============================================================================
-- 3. STANDARDIZE SECOND ADMIN DISPLAY NAME (admin002@campuscore.demo)
-- =============================================================================
UPDATE campuscore_auth."User"
SET "lastName" = 'Ban Kiểm duyệt',
    "firstName" = 'Học vụ',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'admin-user-002'
  AND ("firstName" = 'Demo' OR "lastName" = 'Admin Hai');

-- =============================================================================
-- 4. CLEAN UP SYNTHETIC PROBE AUDIT ROWS & BACKFILL ACTOR LABELS
-- =============================================================================
DELETE FROM campuscore_audit."AdminAudit"
WHERE "summary" LIKE '%@campuscore.test%'
   OR "entityId" IN (
       'ac5942c1-6ebf-4d6a-ade2-c6246b6af3a7',
       'e12d0c57-42cc-4921-ab70-2756a7b883f5',
       '3d100c8b-dfab-4119-9edd-9de2c9ad5ce6',
       'da7d8ddd-26f2-46a7-8776-48b3bfdc7849',
       '4cddb89e-6e30-446f-8387-e5b3ed36e03a',
       '34c7897f-972e-4eb7-b3c8-341cfe90c541',
       '94a3bbfb-8da4-4968-a9db-b75332d2607f',
       'fd71ef68-acb9-450a-b6e7-a8f81ed8739c',
       'eacc17ce-ebf5-4092-9b80-d127a4706776',
       'a66e64e4-02fb-48ed-80ea-e9c0cd22eb93',
       'a8c62be9-35df-4cbb-bcc5-2cf16354db75'
   );

UPDATE campuscore_audit."AdminAudit" a
SET "actorLabel" = TRIM(COALESCE(u."lastName", '') || ' ' || COALESCE(u."firstName", '')) || ' (' || u."email" || ')'
FROM campuscore_auth."User" u
WHERE u."id" = a."actorId"
  AND (a."actorLabel" IS NULL OR TRIM(a."actorLabel") = '');

UPDATE engagement."AnnouncementAudit"
SET "actorLabel" = REPLACE("actorLabel", 'ThS. Hoàng Tiến Dũng', 'Phòng Đào tạo')
WHERE "actorLabel" LIKE '%ThS. Hoàng Tiến Dũng%';
