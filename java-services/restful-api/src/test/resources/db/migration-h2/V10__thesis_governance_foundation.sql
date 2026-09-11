-- V10__thesis_governance_foundation.sql (H2 twin of production V30)
-- Keep the H2 persistence-test schema aligned with the production V30 shape.
-- H2 has no filtered indexes; the generated role_slot column mirrors the
-- chair/secretary partial unique indexes.

UPDATE thesis.thesis_registration_round
SET thesis_type = 'KLTN'
WHERE thesis_type = 'CAPSTONE';

ALTER TABLE thesis.thesis_registration_round ADD COLUMN IF NOT EXISTS lecturer_submit_start TIMESTAMP WITH TIME ZONE;
ALTER TABLE thesis.thesis_registration_round ADD COLUMN IF NOT EXISTS lecturer_submit_end TIMESTAMP WITH TIME ZONE;
ALTER TABLE thesis.thesis_registration_round ADD COLUMN IF NOT EXISTS gvpb_deadline TIMESTAMP WITH TIME ZONE;

UPDATE thesis.thesis_registration_round
SET lecturer_submit_start = registration_start,
    lecturer_submit_end = registration_end
WHERE lecturer_submit_start IS NULL OR lecturer_submit_end IS NULL;

ALTER TABLE thesis.thesis_registration_round ALTER COLUMN lecturer_submit_start SET NOT NULL;
ALTER TABLE thesis.thesis_registration_round ALTER COLUMN lecturer_submit_end SET NOT NULL;

ALTER TABLE thesis.thesis_registration_round ADD CONSTRAINT IF NOT EXISTS thesis_round_type_valid
    CHECK (thesis_type IN ('MON_HOC', 'NCKH', 'TLCN', 'KLTN'));

-- Window ordering is application-enforced (see production V30 note): legacy
-- backfilled rounds keep identical lecturer/student windows.

ALTER TABLE thesis.thesis_topic ADD COLUMN IF NOT EXISTS final_score NUMERIC(4, 2);
ALTER TABLE thesis.thesis_topic ADD COLUMN IF NOT EXISTS final_score_finalized_by VARCHAR(120);
ALTER TABLE thesis.thesis_topic ADD COLUMN IF NOT EXISTS final_score_finalized_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE thesis.thesis_topic ADD COLUMN IF NOT EXISTS result_status VARCHAR(32);

CREATE TABLE IF NOT EXISTS thesis.thesis_council (
    id UUID PRIMARY KEY,
    round_id UUID NOT NULL REFERENCES thesis.thesis_registration_round (id),
    name VARCHAR(180) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS thesis.thesis_council_member (
    id UUID PRIMARY KEY,
    council_id UUID NOT NULL REFERENCES thesis.thesis_council (id) ON DELETE CASCADE,
    lecturer_id VARCHAR(120) NOT NULL,
    member_role VARCHAR(20) NOT NULL,
    role_slot VARCHAR(40) AS (
        CASE WHEN member_role IN ('CHAIR', 'SECRETARY')
            THEN member_role ELSE CAST(id AS VARCHAR(36)) END),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_council_member_role_valid
        CHECK (member_role IN ('CHAIR', 'SECRETARY', 'MEMBER')),
    CONSTRAINT thesis_council_member_unique UNIQUE (council_id, lecturer_id),
    -- Mirrors the PostgreSQL partial unique indexes for one chair and one
    -- secretary per council: role_slot is the role itself for privileged
    -- roles and the row id for plain members.
    CONSTRAINT thesis_council_role_slot_unique UNIQUE (council_id, role_slot)
);

CREATE TABLE IF NOT EXISTS thesis.thesis_council_topic (
    id UUID PRIMARY KEY,
    council_id UUID NOT NULL REFERENCES thesis.thesis_council (id) ON DELETE CASCADE,
    topic_id UUID NOT NULL REFERENCES thesis.thesis_topic (id) ON DELETE CASCADE,
    assigned_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
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
    graded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
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
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
