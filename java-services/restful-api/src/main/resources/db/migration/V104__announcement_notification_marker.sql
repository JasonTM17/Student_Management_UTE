-- V104: marker column for the scheduled-announcement notification refire.
--
-- AnnouncementStudentNotifier returns 0 without sending when publishAt is in
-- the future, and nothing ever re-ran the fan-out when the scheduled time
-- arrived — scheduled announcements silently never reached student inboxes.
-- The job ScheduledAnnouncementNotifyJob picks up rows whose publishAt has
-- passed while notifiedAt is still NULL.
--
-- Backfill: rows already visible at migration time are stamped so existing
-- announcements do not mass-refire student inboxes on deploy.

ALTER TABLE engagement."Announcement"
    ADD COLUMN IF NOT EXISTS "notifiedAt" TIMESTAMPTZ;

UPDATE engagement."Announcement"
SET "notifiedAt" = COALESCE("publishAt", "createdAt")
WHERE "notifiedAt" IS NULL
  AND ("publishAt" IS NULL OR "publishAt" <= now());

CREATE INDEX IF NOT EXISTS announcement_notification_due_idx
    ON engagement."Announcement" ("publishAt")
    WHERE "notifiedAt" IS NULL;
