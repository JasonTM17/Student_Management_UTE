CREATE SCHEMA IF NOT EXISTS notifications;

CREATE TABLE IF NOT EXISTS notifications.notification (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    title       VARCHAR(200) NOT NULL,
    message     TEXT NOT NULL,
    type        VARCHAR(40) NOT NULL,
    link        VARCHAR(500),
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    read_at     TIMESTAMP,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_notification_user_created
    ON notifications.notification (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_read
    ON notifications.notification (is_read);
