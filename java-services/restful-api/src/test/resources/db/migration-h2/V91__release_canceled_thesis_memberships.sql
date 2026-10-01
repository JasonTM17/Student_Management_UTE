-- H2 twin of PostgreSQL V91. The generated nullable key expresses active-only
-- uniqueness; ThesisMutationService releases it atomically during cancellation.
ALTER TABLE thesis.thesis_group_member
    ADD COLUMN IF NOT EXISTS active_participation BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE thesis.thesis_group_member member
SET active_participation = FALSE
WHERE EXISTS (
    SELECT 1 FROM thesis.thesis_group group_row
    WHERE group_row.id = member.group_id
      AND group_row.status = 'CANCELLED'
);

ALTER TABLE thesis.thesis_group_member
    DROP CONSTRAINT IF EXISTS thesis_student_one_group_per_round;

ALTER TABLE thesis.thesis_group_member
    ADD COLUMN IF NOT EXISTS active_student_id VARCHAR(120)
        GENERATED ALWAYS AS (CASE WHEN active_participation THEN student_id ELSE NULL END);

CREATE UNIQUE INDEX IF NOT EXISTS thesis_student_one_active_group_per_round
    ON thesis.thesis_group_member (round_id, active_student_id);
