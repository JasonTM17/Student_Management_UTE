-- V131__purge_probe_artifacts_and_fill_contact_metadata.sql
-- Purpose:
--   1. Remove the leftover E2E rehearsal artifacts that survived earlier cleanups:
--      two archived "E2E PROBE" announcements, their AnnouncementAudit trail
--      rows (FK ON DELETE RESTRICT), the ~450 fan-out inbox notifications, and
--      the admin audit rows recorded for two disposable probe user accounts.
--   2. Backfill announcement display snapshots (sectionNumber/courseCode/
--      courseName/semesterName) for section-scoped rows whose denormalized
--      columns were never populated.
--   3. Fill institutional contact metadata that the demo catalog never seeded:
--      faculty dean/email/phone/building, department-demo chair + contacts,
--      lecturer phone/office, and User.phone for lecturer accounts and the
--      fourth demo student.
--   4. Backfill Section.classroomId for the 20 demo sections that carry a
--      schedule room but no denormalized classroom pointer.
--   5. Standardize department contact emails to the dept.<code>@campuscore.edu
--      convention already used by the newer catalog rows.

-- =============================================================================
-- 1. E2E PROBE ANNOUNCEMENT FAN-OUT (inbox notifications)
-- =============================================================================
DELETE FROM notifications.notification
WHERE "title" LIKE '[Announcement] E2E PROBE%';

-- =============================================================================
-- 2. E2E PROBE ANNOUNCEMENTS (+ audit trail rows first: ON DELETE RESTRICT)
-- =============================================================================
DELETE FROM engagement."AnnouncementAudit"
WHERE "announcementId" IN (
    '13cb7f5c-af3d-4ce1-a312-c48f33ad65aa',
    '033f6675-c040-4e1f-bda4-5fce1901a835'
);

DELETE FROM engagement."Announcement"
WHERE "id" IN (
    '13cb7f5c-af3d-4ce1-a312-c48f33ad65aa',
    '033f6675-c040-4e1f-bda4-5fce1901a835'
)
   OR "title" LIKE 'E2E PROBE%';

-- =============================================================================
-- 3. ADMIN AUDIT ROWS FOR THE TWO DISPOSABLE PROBE USER ACCOUNTS
-- =============================================================================
DELETE FROM campuscore_audit."AdminAudit"
WHERE "entityId" IN (
    'b58e1980-28c5-4b86-91fe-11b3cb0f1984',
    '1ab94051-6b63-44d5-8578-bc43e68ed5ab'
)
   OR "summary" ILIKE '%probe.student.%@campuscore.demo%'
   OR "summary" ILIKE '%@resend.dev%';

-- =============================================================================
-- 4. ANNOUNCEMENT DISPLAY SNAPSHOT BACKFILL
--    Section-scoped rows written before the denormalized columns existed carry
--    sectionId but NULL sectionNumber/courseCode/courseName.
-- =============================================================================
UPDATE engagement."Announcement" a
SET "sectionNumber" = s."sectionNumber",
    "courseCode"    = c."code",
    "courseName"    = COALESCE(c."nameVi", c."name"),
    "updatedAt"     = CURRENT_TIMESTAMP
FROM academic."Section" s
JOIN academic."Course" c ON c."id" = s."courseId"
WHERE a."sectionId" = s."id"
  AND (a."sectionNumber" IS NULL OR a."courseCode" IS NULL);

UPDATE engagement."Announcement" a
SET "semesterName" = sem."nameVi",
    "updatedAt"    = CURRENT_TIMESTAMP
FROM academic."Semester" sem
WHERE a."semesterId" = sem."id"
  AND a."semesterName" IS NULL;

-- =============================================================================
-- 5. FACULTY DEAN + INSTITUTIONAL CONTACT BACKFILL
--    Deans are the senior academic officers already seeded in the catalog
--    (department chairs / the truongkhoa demo account).
-- =============================================================================
UPDATE academic."Faculty" SET
    "dean"      = 'PGS.TS. Lê Văn Vinh',
    "email"     = 'khoa.cntt@campuscore.edu',
    "phone"     = '028-3722-1223',
    "building"  = 'Tòa nhà A1 - Tầng 2',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'faculty-demo' AND "dean" IS NULL;

UPDATE academic."Faculty" SET
    "dean"      = 'PGS.TS. Nguyễn Văn Hùng',
    "email"     = 'khoa.fme@campuscore.edu',
    "phone"     = '028-3722-1223',
    "building"  = 'Tòa nhà C2 - Tầng 1',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'faculty-fme' AND "dean" IS NULL;

UPDATE academic."Faculty" SET
    "dean"      = 'TS. Phan Thị Diệu Linh',
    "email"     = 'khoa.foe@campuscore.edu',
    "phone"     = '028-3722-1223',
    "building"  = 'Tòa nhà Trung tâm - Tầng 5',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'faculty-foe' AND "dean" IS NULL;

UPDATE academic."Faculty" SET
    "dean"      = 'ThS. Vũ Thanh Mai',
    "email"     = 'khoa.ffl@campuscore.edu',
    "phone"     = '028-3722-1223',
    "building"  = 'Tòa nhà B - Tầng 2',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'faculty-ffl' AND "dean" IS NULL;

UPDATE academic."Faculty" SET
    "dean"      = 'TS. Đoàn Văn Long',
    "email"     = 'khoa.fce@campuscore.edu',
    "phone"     = '028-3722-1223',
    "building"  = 'Tòa nhà D - Tầng 1',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'faculty-fce' AND "dean" IS NULL;

UPDATE academic."Faculty" SET
    "dean"      = 'PGS.TS. Trịnh Quốc Tuấn',
    "email"     = 'khoa.feee@campuscore.edu',
    "phone"     = '028-3722-1223',
    "building"  = 'Tòa nhà B1 - Tầng 1',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'faculty-feee' AND "dean" IS NULL;

-- =============================================================================
-- 6. DEPARTMENT CONTACT BACKFILL + EMAIL NORMALIZATION
--    department-demo (legacy CNTT bucket for demo courses) gets its chair and
--    contacts; the eight departments still on *@ute.demo.edu.vn move to the
--    dept.<code>@campuscore.edu convention used by the newer catalog rows.
-- =============================================================================
UPDATE academic."Department" SET
    "chair"     = 'TS. Nguyễn Minh Đạo',
    "email"     = 'dept.cntt@campuscore.edu',
    "phone"     = '028-3722-1223',
    "building"  = 'Tòa nhà A1 - Tầng 3',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'department-demo' AND "chair" IS NULL;

UPDATE academic."Department" SET "email" = 'dept.robot@campuscore.edu',    "updatedAt" = CURRENT_TIMESTAMP WHERE "id" = 'department-fme-robotics'   AND "email" LIKE '%@ute.demo.edu.vn';
UPDATE academic."Department" SET "email" = 'dept.mis@campuscore.edu',      "updatedAt" = CURRENT_TIMESTAMP WHERE "id" = 'department-foe-mis'        AND "email" LIKE '%@ute.demo.edu.vn';
UPDATE academic."Department" SET "email" = 'dept.auto@campuscore.edu',     "updatedAt" = CURRENT_TIMESTAMP WHERE "id" = 'department-feee-auto'      AND "email" LIKE '%@ute.demo.edu.vn';
UPDATE academic."Department" SET "email" = 'dept.civil@campuscore.edu',    "updatedAt" = CURRENT_TIMESTAMP WHERE "id" = 'department-fce-civil'      AND "email" LIKE '%@ute.demo.edu.vn';
UPDATE academic."Department" SET "email" = 'dept.autoeng@campuscore.edu',  "updatedAt" = CURRENT_TIMESTAMP WHERE "id" = 'department-fme-automotive' AND "email" LIKE '%@ute.demo.edu.vn';
UPDATE academic."Department" SET "email" = 'dept.bte@campuscore.edu',      "updatedAt" = CURRENT_TIMESTAMP WHERE "id" = 'department-ffl-business'   AND "email" LIKE '%@ute.demo.edu.vn';
UPDATE academic."Department" SET "email" = 'dept.fin@campuscore.edu',      "updatedAt" = CURRENT_TIMESTAMP WHERE "id" = 'department-foe-finance'    AND "email" LIKE '%@ute.demo.edu.vn';
UPDATE academic."Department" SET "email" = 'dept.etec@campuscore.edu',     "updatedAt" = CURRENT_TIMESTAMP WHERE "id" = 'department-feee-telecom'   AND "email" LIKE '%@ute.demo.edu.vn';

-- =============================================================================
-- 7. LECTURER PHONE + OFFICE BACKFILL
--    Deterministic demo contact values derived from the employee code
--    (GV101001 -> 0900101001) so every lecturer row renders contact info in
--    the advisor directory; offices reuse the owning department building.
-- =============================================================================
UPDATE academic."Lecturer" l
SET "phone" = '09' || LPAD(COALESCE(NULLIF(REGEXP_REPLACE(l."employeeId", '\D', '', 'g'), ''), '0'), 8, '0'),
    "office" = 'Phòng ' || (300 + MOD(CAST(COALESCE(NULLIF(REGEXP_REPLACE(l."employeeId", '\D', '', 'g'), ''), '0') AS INTEGER), 60)) || ' - ' || d."building",
    "updatedAt" = CURRENT_TIMESTAMP
FROM academic."Department" d
WHERE d."id" = l."departmentId"
  AND (l."phone" IS NULL OR l."office" IS NULL);

UPDATE academic."Lecturer" l
SET "phone" = '09' || LPAD(COALESCE(NULLIF(REGEXP_REPLACE(l."employeeId", '\D', '', 'g'), ''), '0'), 8, '0'),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE l."phone" IS NULL;

-- Keep the auth user record in sync so profile screens show the same phone.
UPDATE campuscore_auth."User" u
SET "phone" = l."phone",
    "updatedAt" = CURRENT_TIMESTAMP
FROM academic."Lecturer" l
WHERE l."userId" = u."id"
  AND u."phone" IS NULL
  AND l."phone" IS NOT NULL;

-- Fourth quick-fill demo student had no phone on the account record.
UPDATE campuscore_auth."User"
SET "phone" = '0924162050',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'student-user-4'
  AND "phone" IS NULL;

-- =============================================================================
-- 8. SECTION CLASSROOM BACKFILL
--    Sections with a scheduled room but no denormalized classroomId adopt the
--    earliest-scheduled classroom (dayOfWeek, then startTime).
-- =============================================================================
UPDATE academic."Section" s
SET "classroomId" = (
        SELECT ss."classroomId"
        FROM academic."SectionSchedule" ss
        WHERE ss."sectionId" = s."id"
          AND ss."classroomId" IS NOT NULL
        ORDER BY ss."dayOfWeek", ss."startTime"
        LIMIT 1),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE s."classroomId" IS NULL
  AND EXISTS (
      SELECT 1
      FROM academic."SectionSchedule" ss2
      WHERE ss2."sectionId" = s."id"
        AND ss2."classroomId" IS NOT NULL);
