-- Thesis demo-data rollback: deletes ONLY rows carrying the package marker
-- ('ed1a0000-0000-4000-8000-%' uuids in thesis.* and 'demo-th-%' ids in
-- notifications/audit) and reverts the handful of guarded column updates the
-- package made on pre-existing baseline rows. Idempotent: every statement is a
-- plain conditional delete/update, so a second run deletes nothing more.
--
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -1 -f rollback.sql

BEGIN;

-- 1) leaf rows first (FK-safe order). Groups go before members: cancelled-group
-- rosters are read-only by trigger, but deleting the parent cascades the roster
-- away through the ON DELETE CASCADE FK (the member-level guard sees no parent).
DELETE FROM thesis.thesis_topic_score WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_council_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_group_report WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_group WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_group_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';

-- 2) revert guarded updates on baseline rows (their pre-package state was NULL)
UPDATE thesis.thesis_topic
   SET gvpb_lecturer_id = NULL, updated_at = CURRENT_TIMESTAMP, version = version + 1
 WHERE id IN ('018f4a7c-3b12-4d90-a5e1-7c82b9045001',
              '6f64670b-cd6b-4e1b-ae9d-0b497f2472a8',
              'f580eadf-fffb-4db2-a7d8-423e83934dc2')
   AND gvpb_lecturer_id IN ('lecturer-profile-024','lecturer-profile-025','lecturer-profile-013');

UPDATE thesis.thesis_topic
   SET gvpb_waived_reason = NULL, gvpb_waived_by = NULL, gvpb_waived_at = NULL,
       updated_at = CURRENT_TIMESTAMP, version = version + 1
 WHERE id = '67c74df5-5c7f-4e74-aa02-d51af7207fe9'
   AND gvpb_waived_reason LIKE 'Miễn phản biện theo Quyết định 214/QĐ-KCNTT%';

UPDATE thesis.thesis_registration_round
   SET results_published_at = NULL, updated_at = CURRENT_TIMESTAMP, version = version + 1
 WHERE id IN ('b35e9ff1-1eb8-4e73-812e-04e1a66041f1',
              'be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1',
              'bad8eba0-2d12-4414-8aab-7424174b3b97')
   AND status = 'RESULTS_PUBLISHED'
   AND results_published_at IN ('2026-10-16T17:00:00Z'::timestamptz,
                                '2026-10-23T09:00:00Z'::timestamptz,
                                '2026-10-22T09:00:00Z'::timestamptz);

-- 3) structural rows
DELETE FROM thesis.thesis_council_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_council WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_topic_supervisor WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';
DELETE FROM thesis.thesis_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%';

-- 4) marker-owned side effects
DELETE FROM notifications.notification WHERE id LIKE 'demo-th-%';
DELETE FROM campuscore_audit."AdminAudit" WHERE id LIKE 'demo-th-%';

-- 5) post-rollback residue report (all zeros expected)
SELECT 'thesis_topic' AS section, count(*) AS residual FROM thesis.thesis_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_topic_supervisor', count(*) FROM thesis.thesis_topic_supervisor WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_group', count(*) FROM thesis.thesis_group WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_group_member', count(*) FROM thesis.thesis_group_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_council', count(*) FROM thesis.thesis_council WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_council_member', count(*) FROM thesis.thesis_council_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_council_topic', count(*) FROM thesis.thesis_council_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_topic_score', count(*) FROM thesis.thesis_topic_score WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'thesis_group_report', count(*) FROM thesis.thesis_group_report WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'notification', count(*) FROM notifications.notification WHERE id LIKE 'demo-th-%'
UNION ALL SELECT 'admin_audit', count(*) FROM campuscore_audit."AdminAudit" WHERE id LIKE 'demo-th-%'
UNION ALL SELECT 'baseline.topics', (SELECT count(*) FROM thesis.thesis_topic WHERE id::text NOT LIKE 'ed1a0000-0000-4000-8000-%')
UNION ALL SELECT 'baseline.groups', (SELECT count(*) FROM thesis.thesis_group WHERE id::text NOT LIKE 'ed1a0000-0000-4000-8000-%')
ORDER BY 1;

COMMIT;
