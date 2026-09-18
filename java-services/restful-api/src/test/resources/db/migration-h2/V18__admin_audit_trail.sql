-- H2 mirror of V60__admin_audit_trail.sql (parity required by plan R4).
--
-- The H2 tree is RENUMBERED against the production tree - H2 V14 corresponds to
-- production V48, H2 V17 to production V55 - so the version numbers deliberately
-- do not match. Parity is by description, not by number. Keep the columns and
-- constraints identical to the production migration; only the dialect differs.

CREATE SCHEMA IF NOT EXISTS campuscore_audit;

CREATE TABLE IF NOT EXISTS campuscore_audit."AdminAudit" (
    "id" VARCHAR(120) PRIMARY KEY,
    "actorId" VARCHAR(120),
    "actorLabel" VARCHAR(240),
    "action" VARCHAR(48) NOT NULL,
    "entityType" VARCHAR(48) NOT NULL,
    "entityId" VARCHAR(120),
    "summary" VARCHAR(500),
    "beforeState" TEXT,
    "afterState" TEXT,
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT admin_audit_action_ck CHECK (length(trim("action")) > 0),
    CONSTRAINT admin_audit_entity_ck CHECK (length(trim("entityType")) > 0)
);

CREATE INDEX IF NOT EXISTS admin_audit_entity_idx
    ON campuscore_audit."AdminAudit" ("entityType", "entityId", "createdAt" DESC);

CREATE INDEX IF NOT EXISTS admin_audit_actor_idx
    ON campuscore_audit."AdminAudit" ("actorId", "createdAt" DESC);
