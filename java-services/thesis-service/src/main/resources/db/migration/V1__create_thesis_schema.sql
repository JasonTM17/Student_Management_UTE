CREATE SCHEMA IF NOT EXISTS academic;

CREATE TABLE academic.thesis_registration_round (
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
    ON academic.thesis_registration_round (status);

CREATE TABLE academic.thesis_topic (
    id UUID PRIMARY KEY,
    round_id UUID NOT NULL REFERENCES academic.thesis_registration_round (id),
    department_id UUID NOT NULL,
    title VARCHAR(240) NOT NULL,
    description TEXT NOT NULL,
    max_groups INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    created_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_topic_group_limit_valid CHECK (max_groups BETWEEN 1 AND 20)
);

CREATE INDEX thesis_topic_round_idx
    ON academic.thesis_topic (round_id, status);
CREATE INDEX thesis_topic_department_idx
    ON academic.thesis_topic (department_id, status);

CREATE TABLE academic.thesis_topic_supervisor (
    id UUID PRIMARY KEY,
    topic_id UUID NOT NULL REFERENCES academic.thesis_topic (id) ON DELETE CASCADE,
    lecturer_id UUID NOT NULL,
    supervisor_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_topic_supervisor_order_valid CHECK (supervisor_order BETWEEN 1 AND 2),
    CONSTRAINT thesis_topic_supervisor_unique UNIQUE (topic_id, lecturer_id),
    CONSTRAINT thesis_topic_supervisor_order_unique UNIQUE (topic_id, supervisor_order)
);

CREATE TABLE academic.thesis_group (
    id UUID PRIMARY KEY,
    round_id UUID NOT NULL REFERENCES academic.thesis_registration_round (id),
    leader_student_id UUID NOT NULL,
    topic_id UUID REFERENCES academic.thesis_topic (id),
    status VARCHAR(32) NOT NULL,
    approval_status VARCHAR(32) NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    rejection_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX thesis_group_round_idx
    ON academic.thesis_group (round_id, status, approval_status);

CREATE TABLE academic.thesis_group_member (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES academic.thesis_group (id) ON DELETE CASCADE,
    round_id UUID NOT NULL REFERENCES academic.thesis_registration_round (id),
    student_id UUID NOT NULL,
    member_order INTEGER NOT NULL,
    is_leader BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_group_member_order_valid CHECK (member_order BETWEEN 1 AND 3),
    CONSTRAINT thesis_group_member_unique UNIQUE (group_id, student_id),
    CONSTRAINT thesis_student_one_group_per_round UNIQUE (round_id, student_id)
);

CREATE TABLE academic.thesis_defense_council (
    id UUID PRIMARY KEY,
    round_id UUID NOT NULL REFERENCES academic.thesis_registration_round (id),
    department_id UUID NOT NULL,
    scheduled_at TIMESTAMPTZ,
    room VARCHAR(120),
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX thesis_council_round_idx
    ON academic.thesis_defense_council (round_id, status);

CREATE TABLE academic.thesis_council_member (
    id UUID PRIMARY KEY,
    council_id UUID NOT NULL REFERENCES academic.thesis_defense_council (id) ON DELETE CASCADE,
    lecturer_id UUID NOT NULL,
    member_role VARCHAR(32) NOT NULL,
    member_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_council_member_order_valid CHECK (member_order BETWEEN 1 AND 5),
    CONSTRAINT thesis_council_member_unique UNIQUE (council_id, lecturer_id),
    CONSTRAINT thesis_council_member_order_unique UNIQUE (council_id, member_order)
);

CREATE TABLE academic.thesis_review (
    id UUID PRIMARY KEY,
    council_id UUID NOT NULL REFERENCES academic.thesis_defense_council (id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES academic.thesis_group (id),
    reviewer_id UUID NOT NULL,
    score NUMERIC(5, 2) NOT NULL,
    comment VARCHAR(2000),
    status VARCHAR(32) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_review_score_valid CHECK (score >= 0 AND score <= 10),
    CONSTRAINT thesis_review_unique UNIQUE (council_id, group_id, reviewer_id)
);

CREATE TABLE academic.thesis_result (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES academic.thesis_group (id),
    total_score NUMERIC(5, 2) NOT NULL,
    grade VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    published_by UUID,
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT thesis_result_score_valid CHECK (total_score >= 0 AND total_score <= 10),
    CONSTRAINT thesis_result_group_unique UNIQUE (group_id)
);
