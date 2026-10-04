-- The V101 correction notice supersedes the earlier V72 correction, but that
-- row stayed PUBLISHED and unpinned readers still saw the outdated 3-4 member
-- rule next to the replacement. Archiving keeps the historical record and its
-- audit trail while publicConditions() (status='PUBLISHED' + archivedAt IS
-- NULL) drops it from every feed, including the pinned homepage board.
UPDATE engagement."Announcement"
SET "archivedAt" = CURRENT_TIMESTAMP,
    "updatedAt" = CURRENT_TIMESTAMP,
    "version" = "version" + 1
WHERE "id" = 'announcement-thesis-group-size-correction-v72'
  AND "archivedAt" IS NULL;

INSERT INTO engagement."AnnouncementAudit" (
    "id", "announcementId", "action", "actorId", "actorLabel", "reason", "version",
    "beforeState", "afterState", "createdAt"
)
SELECT 'audit-announcement-thesis-group-size-correction-v72-archive-v102',
       'announcement-thesis-group-size-correction-v72',
       'ARCHIVED',
       'system-migration',
       'Hệ thống – Di trú dữ liệu (V102)',
       'Lưu trữ đính chính V72 đã bị thay thế bởi announcement-thesis-group-size-correction-v101 (quy định hiện hành: nhóm KLTN 1-3 sinh viên). Nội dung lịch sử được giữ nguyên.',
       (SELECT "version" FROM engagement."Announcement"
        WHERE "id" = 'announcement-thesis-group-size-correction-v72'),
       '{"status": "PUBLISHED", "archivedAt": null}',
       '{"status": "PUBLISHED", "archived": true, "supersededBy": "announcement-thesis-group-size-correction-v101"}',
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM engagement."AnnouncementAudit"
    WHERE "id" = 'audit-announcement-thesis-group-size-correction-v72-archive-v102'
)
  AND EXISTS (
    SELECT 1 FROM engagement."Announcement"
    WHERE "id" = 'announcement-thesis-group-size-correction-v72'
      AND "archivedAt" IS NOT NULL
  );
