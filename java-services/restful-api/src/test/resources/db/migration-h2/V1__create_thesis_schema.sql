CREATE SCHEMA IF NOT EXISTS thesis;

CREATE TABLE thesis.thesis_registration_round (
    id UUID PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    thesis_type VARCHAR(40) NOT NULL,
    registration_start TIMESTAMP WITH TIME ZONE NOT NULL,
    registration_end TIMESTAMP WITH TIME ZONE NOT NULL,
    proposal_publish_at TIMESTAMP WITH TIME ZONE,
    report_date TIMESTAMP WITH TIME ZONE,
    defense_date TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_round_dates_valid CHECK (registration_end > registration_start)
);

CREATE TABLE thesis.thesis_topic (
    id UUID PRIMARY KEY,
    round_id UUID NOT NULL REFERENCES thesis.thesis_registration_round (id),
    department_id UUID NOT NULL,
    title VARCHAR(240) NOT NULL,
    description TEXT NOT NULL,
    max_groups INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    created_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_topic_group_limit_valid CHECK (max_groups BETWEEN 1 AND 20)
);

CREATE INDEX thesis_topic_round_idx
    ON thesis.thesis_topic (round_id, status);
