-- V135__purge_verification_probe_notifications.sql
-- Purpose:
--   Remove inbox notifications fanned out by disposable verification
--   announcements (QA, E2E, TinyMCE and deploy-check probes). The announcements
--   were archived afterwards, but archiving never recalls notifications that
--   were already delivered, so every student kept seeing the probe titles.
--   Only titles of the known probe announcements are matched; the real
--   "[Announcement] ..." notices are untouched.

DELETE FROM notifications.notification
WHERE "title" LIKE '[Announcement] [verify]%'
   OR "title" LIKE '[Announcement] [QA-TEST]%'
   OR "title" LIKE '[Announcement] [E2E]%'
   OR "title" LIKE '[Announcement] PROBE-VERIFY%'
   OR "title" LIKE '[Announcement] QA-tinymce-probe%';
