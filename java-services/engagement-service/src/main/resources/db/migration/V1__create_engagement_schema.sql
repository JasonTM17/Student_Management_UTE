CREATE SCHEMA IF NOT EXISTS engagement;

CREATE TABLE engagement.announcement (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title varchar(200) NOT NULL,
    content text NOT NULL,
    priority varchar(20) NOT NULL DEFAULT 'NORMAL',
    target_roles varchar(50) NOT NULL DEFAULT '{}',
    target_years integer[] NOT NULL DEFAULT '{}',
    is_global boolean NOT NULL DEFAULT false,
    publish_at timestamptz,
    expires_at timestamptz,
    semester_id uuid,
    section_id uuid,
    lecturer_id uuid,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX announcement_priority_idx ON engagement.announcement (priority);
CREATE INDEX announcement_semester_idx ON engagement.announcement (semester_id);

CREATE TABLE engagement.support_ticket (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_number varchar(20) NOT NULL UNIQUE,
    user_id uuid NOT NULL,
    user_email varchar(180) NOT NULL,
    user_display_name varchar(180),
    subject varchar(200) NOT NULL,
    description text NOT NULL,
    category varchar(40) NOT NULL,
    priority varchar(20) NOT NULL DEFAULT 'MEDIUM',
    status varchar(20) NOT NULL DEFAULT 'OPEN',
    assigned_to uuid,
    assigned_to_display_name varchar(180),
    resolved_at timestamptz,
    closed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX support_ticket_user_idx ON engagement.support_ticket (user_id);
CREATE INDEX support_ticket_status_idx ON engagement.support_ticket (status);

CREATE TABLE engagement.ticket_response (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id uuid NOT NULL REFERENCES engagement.support_ticket(id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    user_email varchar(180) NOT NULL,
    user_display_name varchar(180),
    message text NOT NULL,
    is_internal boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ticket_response_ticket_idx ON engagement.ticket_response (ticket_id);
