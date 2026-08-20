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

CREATE TABLE thesis.thesis_group (
    id UUID PRIMARY KEY,
    round_id UUID NOT NULL REFERENCES thesis.thesis_registration_round (id),
    leader_student_id UUID NOT NULL,
    topic_id UUID,
    status VARCHAR(32) NOT NULL,
    approval_status VARCHAR(32) NOT NULL,
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX thesis_group_round_idx ON thesis.thesis_group (round_id, created_at);

CREATE TABLE thesis.thesis_group_member (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES thesis.thesis_group (id) ON DELETE CASCADE,
    round_id UUID NOT NULL REFERENCES thesis.thesis_registration_round (id),
    student_id UUID NOT NULL,
    member_order INTEGER NOT NULL,
    is_leader BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_group_member_unique UNIQUE (group_id, student_id)
);

CREATE TABLE thesis.thesis_defense_council (
    id UUID PRIMARY KEY,
    round_id UUID NOT NULL REFERENCES thesis.thesis_registration_round (id),
    department_id UUID NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE,
    room VARCHAR(120),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX thesis_council_round_idx ON thesis.thesis_defense_council (round_id, scheduled_at);

CREATE TABLE thesis.thesis_council_member (
    id UUID PRIMARY KEY,
    council_id UUID NOT NULL REFERENCES thesis.thesis_defense_council (id) ON DELETE CASCADE,
    lecturer_id UUID NOT NULL,
    member_role VARCHAR(32) NOT NULL,
    member_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_council_member_unique UNIQUE (council_id, lecturer_id),
    CONSTRAINT thesis_council_member_order_unique UNIQUE (council_id, member_order)
);
