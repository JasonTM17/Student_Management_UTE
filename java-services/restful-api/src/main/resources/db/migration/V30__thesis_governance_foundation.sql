-- V30__thesis_governance_foundation.sql
-- Phase P1a of plans/20260909-pdf-requirements-audit: align the thesis domain
-- with the course brief — constrained round types, two-phase windows, councils
-- with chair/secretary, component scoring, and leader-only reports.
-- Additive only: 'CAPSTONE' maps to 'KLTN' before the CHECK constraint lands,
-- and the lecturer window is backfilled from the existing student window so
-- every pre-existing row and seed keeps working.

UPDATE thesis.thesis_registration_round
SET thesis_type = 'KLTN'
WHERE thesis_type = 'CAPSTONE';

ALTER TABLE thesis.thesis_registration_round
    ADD COLUMN IF NOT EXISTS lecturer_submit_start TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS lecturer_submit_end TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS gvpb_deadline TIMESTAMPTZ;

UPDATE thesis.thesis_registration_round
SET lecturer_submit_start = registration_start,
    lecturer_submit_end = registration_end
WHERE lecturer_submit_start IS NULL OR lecturer_submit_end IS NULL;

ALTER TABLE thesis.thesis_registration_round
    ALTER COLUMN lecturer_submit_start SET NOT NULL,
    ALTER COLUMN lecturer_submit_end SET NOT NULL;

-- Postgres lacks ADD CONSTRAINT IF NOT EXISTS; the DO block keeps re-runs safe.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'thesis_round_type_valid') THEN
        ALTER TABLE thesis.thesis_registration_round
            ADD CONSTRAINT thesis_round_type_valid
            CHECK (thesis_type IN ('MON_HOC', 'NCKH', 'TLCN', 'KLTN'));
    END IF;
END $$;

-- Note: window ordering (lecturer window closes before student registration
-- opens) is enforced by the application at round creation; the legacy
-- backfilled rounds keep identical windows, so a database CHECK here would
-- reject every pre-existing row.

ALTER TABLE thesis.thesis_topic
    ADD COLUMN IF NOT EXISTS final_score NUMERIC(4, 2),
    ADD COLUMN IF NOT EXISTS final_score_finalized_by VARCHAR(120),
    ADD COLUMN IF NOT EXISTS final_score_finalized_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS result_status VARCHAR(32);

CREATE TABLE IF NOT EXISTS thesis.thesis_council (
    id UUID PRIMARY KEY,
    round_id UUID NOT NULL REFERENCES thesis.thesis_registration_round (id),
    name VARCHAR(180) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS thesis.thesis_council_member (
    id UUID PRIMARY KEY,
    council_id UUID NOT NULL REFERENCES thesis.thesis_council (id) ON DELETE CASCADE,
    lecturer_id VARCHAR(120) NOT NULL,
    member_role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_council_member_role_valid
        CHECK (member_role IN ('CHAIR', 'SECRETARY', 'MEMBER')),
    CONSTRAINT thesis_council_member_unique UNIQUE (council_id, lecturer_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS thesis_council_chair_unique
    ON thesis.thesis_council_member (council_id)
    WHERE member_role = 'CHAIR';

CREATE UNIQUE INDEX IF NOT EXISTS thesis_council_secretary_unique
    ON thesis.thesis_council_member (council_id)
    WHERE member_role = 'SECRETARY';

CREATE TABLE IF NOT EXISTS thesis.thesis_council_topic (
    id UUID PRIMARY KEY,
    council_id UUID NOT NULL REFERENCES thesis.thesis_council (id) ON DELETE CASCADE,
    topic_id UUID NOT NULL REFERENCES thesis.thesis_topic (id) ON DELETE CASCADE,
    assigned_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS thesis_council_topic_unique
    ON thesis.thesis_council_topic (topic_id);

CREATE TABLE IF NOT EXISTS thesis.thesis_topic_score (
    id UUID PRIMARY KEY,
    topic_id UUID NOT NULL REFERENCES thesis.thesis_topic (id) ON DELETE CASCADE,
    council_id UUID NOT NULL REFERENCES thesis.thesis_council (id) ON DELETE CASCADE,
    lecturer_id VARCHAR(120) NOT NULL,
    component VARCHAR(60) NOT NULL DEFAULT 'DEFENSE',
    score NUMERIC(4, 2) NOT NULL CHECK (score >= 0 AND score <= 10),
    graded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS thesis_topic_score_unique
    ON thesis.thesis_topic_score (topic_id, lecturer_id, component);

CREATE TABLE IF NOT EXISTS thesis.thesis_group_report (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL UNIQUE REFERENCES thesis.thesis_group (id) ON DELETE CASCADE,
    round_id UUID NOT NULL REFERENCES thesis.thesis_registration_round (id),
    submitted_by VARCHAR(120) NOT NULL,
    title VARCHAR(240),
    url VARCHAR(500) NOT NULL,
    note VARCHAR(500),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
