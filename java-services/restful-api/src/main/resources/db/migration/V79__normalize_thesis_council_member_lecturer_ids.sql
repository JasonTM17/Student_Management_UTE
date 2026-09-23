-- V79: normalize thesis_council_member.lecturer_id to the Lecturer profile id.
--
-- V32 seeded council members with USER account ids ('lecturer-user-023',
-- 'lecturer-user-003', ...) while the runtime contract everywhere else in the
-- thesis schema is the Lecturer profile id (the JWT `lecturerId` claim, the
-- values stored by ThesisCouncilService.addMember, thesis_topic_supervisor,
-- and the demo council rows created after V32).
--
-- Symptom (reproduced in round-11 ws-lecturer): a seeded MEMBER of council
-- 55555555-5555-5555-5555-555555555001 logged in and received
-- 403 COUNCIL_MEMBER_REQUIRED on POST /thesis/councils/{id}/topics/{id}/scores
-- even though the membership row exists, because hasMember() compares the
-- profile-id claim against the user-id stored by the seed.
--
-- The rewrite is idempotent: rows whose lecturer_id already resolves to a
-- Lecturer profile id are left untouched; a user-id row is translated to its
-- profile id, and if the profile-id row already exists (both flavors seeded)
-- the redundant user-id row is removed instead of violating
-- thesis_council_member_unique (council_id, lecturer_id).

UPDATE thesis.thesis_council_member m
SET lecturer_id = lp.id
FROM academic."Lecturer" lp
WHERE m.lecturer_id = lp."userId"
  AND NOT EXISTS (
      SELECT 1 FROM thesis.thesis_council_member keep
      WHERE keep.council_id = m.council_id
        AND keep.lecturer_id = lp.id
  );

DELETE FROM thesis.thesis_council_member m
USING academic."Lecturer" lp
WHERE m.lecturer_id = lp."userId"
  AND EXISTS (
      SELECT 1 FROM thesis.thesis_council_member keep
      WHERE keep.council_id = m.council_id
        AND keep.lecturer_id = lp.id
  );
