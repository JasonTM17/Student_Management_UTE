-- Wave-1 data quality (council-roadmap-v2.md 1.6 data half):
-- 1. Registration round names were seeded in English while the portal is
--    Vietnamese-first; students read these verbatim on the registration
--    banner ("Course registration - current demo").
-- 2. 28 sections had no published schedule, which made the conflict
--    detector treat them as conflict-free ("no schedule = no overlap") and
--    left the registration cards without a meeting slot. Give every one of
--    them a deterministic, realistic weekly meeting (rotating day/slot and
--    classroom) so the timetable view and the conflict checks operate on
--    complete data.
-- Forward-only data migration; no schema changes.

UPDATE academic."RegistrationRound"
SET "name" = 'Đợt đăng ký học phần — học kỳ hiện tại'
WHERE "id" = 'round-registration-current-demo' AND "name" <> 'Đợt đăng ký học phần — học kỳ hiện tại';

UPDATE academic."RegistrationRound"
SET "name" = 'Đợt đăng ký bổ sung / hủy học phần'
WHERE "id" = 'round-add-drop-demo' AND "name" <> 'Đợt đăng ký bổ sung / hủy học phần';

UPDATE academic."RegistrationRound"
SET "name" = 'Đợt đăng ký học phần — học kỳ trước'
WHERE "id" = 'round-registration-demo' AND "name" <> 'Đợt đăng ký học phần — học kỳ trước';

WITH missing AS (
    SELECT s."id" AS section_id,
           ROW_NUMBER() OVER (ORDER BY c."code", s."sectionNumber") AS rn
    FROM academic."Section" s
    JOIN academic."Course" c ON c."id" = s."courseId"
    WHERE NOT EXISTS (SELECT 1 FROM academic."SectionSchedule" ss WHERE ss."sectionId" = s."id")
),
rooms AS (
    SELECT cr."id" AS room_id,
           ROW_NUMBER() OVER (ORDER BY cr."id") AS room_rn,
           (SELECT COUNT(*) FROM academic."Classroom") AS room_total
    FROM academic."Classroom" cr
)
INSERT INTO academic."SectionSchedule" ("id", "sectionId", "classroomId", "dayOfWeek", "startTime", "endTime")
SELECT
    'schedule-w86-' || m.rn,
    m.section_id,
    r.room_id,
    ((m.rn - 1) % 5) + 2,
    CASE ((m.rn - 1) % 3) WHEN 0 THEN '07:00' WHEN 1 THEN '09:45' ELSE '13:00' END,
    CASE ((m.rn - 1) % 3) WHEN 0 THEN '09:30' WHEN 1 THEN '12:15' ELSE '15:30' END
FROM missing m
JOIN rooms r ON r.room_rn = ((m.rn - 1) % r.room_total) + 1;
