-- V136: link fan-out inbox rows back to the announcement that created them.
--
-- AnnouncementStudentNotifier writes one notifications.notification row per
-- active student on publish, but the row carried no reference to its source —
-- archiving or deleting the announcement could never recall the delivered
-- notices, so archived announcements kept surfacing in student inboxes.
--
-- The column stays nullable on purpose: rows written before this migration
-- (and notices from other producers) have no safe way to prove their origin,
-- so they keep NULL and are never touched by the recall. No foreign key is
-- added: announcements are soft-deleted (archived), never removed, and an FK
-- would only add lock/validation cost on the live table for no benefit.
-- The partial index keeps the recall delete cheap on a large inbox table.

ALTER TABLE notifications.notification
    ADD COLUMN IF NOT EXISTS announcement_id VARCHAR(120);

CREATE INDEX IF NOT EXISTS notification_announcement_recall_idx
    ON notifications.notification (announcement_id)
    WHERE announcement_id IS NOT NULL;
