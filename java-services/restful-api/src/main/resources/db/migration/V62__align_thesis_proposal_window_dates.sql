-- Flyway Migration V62: Align Thesis Registration Round Proposal Window Dates
-- Ensures rounds in PROPOSAL_OPEN state have valid active submission dates covering the current semester

UPDATE thesis.thesis_registration_round
SET lecturer_submit_start = '2026-09-01 00:00:00+00',
    lecturer_submit_end = '2026-10-31 23:59:59+00',
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE id = 'b3d980d4-8d69-4fa8-a423-6e8582b66aea'::UUID;

-- Safety guard: Any round in PROPOSAL_OPEN whose lecturer_submit_start is set in the future
-- is aligned so that lecturers can immediately propose topics as intended by the open status.
UPDATE thesis.thesis_registration_round
SET lecturer_submit_start = CURRENT_TIMESTAMP - INTERVAL '1 day',
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE status = 'PROPOSAL_OPEN'
  AND lecturer_submit_start > CURRENT_TIMESTAMP;
