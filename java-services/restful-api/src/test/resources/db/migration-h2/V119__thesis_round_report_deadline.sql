-- H2 twin of production V118__thesis_round_report_deadline.sql.
--
-- report_deadline is the overridable bound for report submission; the write
-- window reads COALESCE(report_deadline, gvpb_deadline) so gvpb_deadline keeps
-- bounding GVPB council scoring untouched.

ALTER TABLE thesis.thesis_registration_round
    ADD COLUMN IF NOT EXISTS report_deadline TIMESTAMP WITH TIME ZONE;
