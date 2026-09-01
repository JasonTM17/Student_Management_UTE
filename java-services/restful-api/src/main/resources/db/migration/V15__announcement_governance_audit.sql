-- Additive governance metadata for safe Admin announcement editing.
ALTER TABLE engagement."Announcement"
    ADD COLUMN IF NOT EXISTS "version" INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS "archivedAt" TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS "archivedBy" VARCHAR(120);

CREATE TABLE IF NOT EXISTS engagement."AnnouncementAudit" (
    "id" VARCHAR(120) PRIMARY KEY,
    "announcementId" VARCHAR(120) NOT NULL,
    "action" VARCHAR(32) NOT NULL,
    "actorId" VARCHAR(120) NOT NULL,
    "actorLabel" VARCHAR(240),
    "reason" VARCHAR(500) NOT NULL,
    "version" INTEGER NOT NULL,
    "beforeState" TEXT,
    "afterState" TEXT,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT announcement_audit_action_ck
        CHECK ("action" IN ('CREATED', 'UPDATED', 'ARCHIVED', 'RESTORED')),
    CONSTRAINT announcement_audit_reason_ck
        CHECK (length(trim("reason")) BETWEEN 1 AND 500),
    CONSTRAINT announcement_audit_announcement_fk
        FOREIGN KEY ("announcementId")
        REFERENCES engagement."Announcement" ("id")
        ON DELETE RESTRICT
);

-- Keep upgrades from an interrupted/partial rollout compatible with the
-- response contract; new writes always populate the label from the JWT.
ALTER TABLE engagement."AnnouncementAudit"
    ADD COLUMN IF NOT EXISTS "actorLabel" VARCHAR(240);

CREATE INDEX IF NOT EXISTS engagement_announcement_audit_lookup_idx
    ON engagement."AnnouncementAudit" ("announcementId", "createdAt" DESC);

CREATE INDEX IF NOT EXISTS engagement_announcement_active_idx
    ON engagement."Announcement" ("archivedAt", "createdAt" DESC);
