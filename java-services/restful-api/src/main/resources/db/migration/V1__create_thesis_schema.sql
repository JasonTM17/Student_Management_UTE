CREATE SCHEMA IF NOT EXISTS thesis;

CREATE TABLE thesis.thesis_registration_round (
    id UUID PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    thesis_type VARCHAR(40) NOT NULL,
    registration_start TIMESTAMPTZ NOT NULL,
    registration_end TIMESTAMPTZ NOT NULL,
    proposal_publish_at TIMESTAMPTZ,
    report_date TIMESTAMPTZ,
    defense_date TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_round_dates_valid CHECK (registration_end > registration_start)
);
CREATE INDEX thesis_round_status_idx
    ON thesis.thesis_registration_round (status);

CREATE TABLE thesis.thesis_topic (
    id UUID PRIMARY KEY,
    round_id UUID NOT NULL REFERENCES thesis.thesis_registration_round (id),
    department_id VARCHAR(120) NOT NULL,
    title VARCHAR(240) NOT NULL,
    description TEXT NOT NULL,
    max_groups INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_topic_group_limit_valid CHECK (max_groups BETWEEN 1 AND 20)
);

CREATE INDEX thesis_topic_round_idx
    ON thesis.thesis_topic (round_id, status);
CREATE INDEX thesis_topic_department_idx
    ON thesis.thesis_topic (department_id, status);

CREATE TABLE thesis.thesis_topic_supervisor (
    id UUID PRIMARY KEY,
    topic_id UUID NOT NULL REFERENCES thesis.thesis_topic (id) ON DELETE CASCADE,
    lecturer_id VARCHAR(120) NOT NULL,
    supervisor_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_topic_supervisor_order_valid CHECK (supervisor_order BETWEEN 1 AND 2),
    CONSTRAINT thesis_topic_supervisor_unique UNIQUE (topic_id, lecturer_id),
    CONSTRAINT thesis_topic_supervisor_order_unique UNIQUE (topic_id, supervisor_order)
);

CREATE TABLE thesis.thesis_group (
    id UUID PRIMARY KEY,
    round_id UUID NOT NULL REFERENCES thesis.thesis_registration_round (id),
    leader_student_id VARCHAR(120) NOT NULL,
    topic_id UUID REFERENCES thesis.thesis_topic (id),
    status VARCHAR(32) NOT NULL,
    approval_status VARCHAR(32) NOT NULL,
    approved_by VARCHAR(120),
    approved_at TIMESTAMPTZ,
    rejection_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX thesis_group_round_idx
    ON thesis.thesis_group (round_id, status, approval_status);

CREATE TABLE thesis.thesis_group_member (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES thesis.thesis_group (id) ON DELETE CASCADE,
    round_id UUID NOT NULL REFERENCES thesis.thesis_registration_round (id),
    student_id VARCHAR(120) NOT NULL,
    member_order INTEGER NOT NULL,
    is_leader BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_group_member_order_valid CHECK (member_order BETWEEN 1 AND 3),
    CONSTRAINT thesis_group_member_unique UNIQUE (group_id, student_id),
    CONSTRAINT thesis_student_one_group_per_round UNIQUE (round_id, student_id)
);
