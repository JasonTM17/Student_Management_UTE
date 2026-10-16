-- Separate the report-submission deadline from the GVPB grading deadline.
--
-- gvpb_deadline bounds council counter-review scoring
-- (ThesisCouncilService). The report-deadline escape hatch
-- (PUT /thesis/rounds/{id}/report-deadline) used to move that shared column,
-- so extending the report window silently reopened the grading window too —
-- plan risk R4.
--
-- report_deadline is an override written only by that endpoint:
-- ThesisReportService.requireReportWritable reads
-- COALESCE(report_deadline, gvpb_deadline), so extending it reopens report
-- submission while gvpb_deadline — and therefore the GVPB scoring freeze —
-- stays exactly where it was.

ALTER TABLE thesis.thesis_registration_round
    ADD COLUMN IF NOT EXISTS report_deadline TIMESTAMPTZ;
