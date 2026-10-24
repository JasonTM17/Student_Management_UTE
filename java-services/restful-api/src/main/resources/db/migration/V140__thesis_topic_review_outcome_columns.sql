-- Counter-review and topic-outcome escape hatches.
--
-- A GVPB deadline miss or a counter-review below the passing threshold used to
-- wedge a topic permanently: finalizeScores kept rejecting it, publishResults
-- kept counting it against the round, and no transition let governance either
-- close the topic out or lift the requirement. These columns carry the two
-- audited outcomes:
--
--   gvpb_waived_*    the counter-review requirement was waived for this topic;
--                    the reason is mandatory so the waiver stays self-
--                    describing in topic reads. Clearing the triple re-arms
--                    the requirement (reversible while no final score exists).
--   result_status    gains two non-score outcomes next to 'GRADED':
--                    'REVIEW_FAILED' (latest counter-review score below the
--                    pass threshold, applied when the score is written and
--                    cleared if a resubmission reaches it) and 'WITHDRAWN'
--                    (governance removed the topic from the round). Both are
--                    excluded from the publication completeness check.
--   result_*         who/when/why for the stored outcome; reason is required
--                    for WITHDRAWN, optional for the score-derived failure.
--
-- Nullable-only additions: existing rows keep NULL and behave exactly as
-- before, so the migration is a no-op for data already in production.

ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS gvpb_waived_reason VARCHAR(500);
ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS gvpb_waived_by VARCHAR(120);
ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS gvpb_waived_at TIMESTAMPTZ;
ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS result_reason VARCHAR(500);
ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS result_by VARCHAR(120);
ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS result_at TIMESTAMPTZ;
