CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.analytics_snapshot (
    id          UUID PRIMARY KEY,
    snapshot_type VARCHAR(50) NOT NULL,
    payload     JSONB NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_analytics_snapshot_type ON analytics.analytics_snapshot (snapshot_type);
CREATE INDEX IF NOT EXISTS idx_analytics_snapshot_created ON analytics.analytics_snapshot (created_at);
