-- ADM-P0-3: an audit trail for destructive administrative operations.
--
-- Announcements already had one (V15), which proves the pattern exists in this
-- repository — it was simply never generalised. Everything else that destroys or
-- re-privileges state was untraceable: user create / role change / password reset
-- / hard delete, catalog deletion, and enrollment deletion (which is the only
-- hard delete of an enrollment together with its grade history).
--
-- Design notes that matter:
--
--   * Written inside the SAME transaction as the mutation it records, so an
--     audited change and its audit row cannot diverge. A recorder that runs
--     afterwards on a separate connection would leave a window where the change
--     landed and the record did not.
--   * `subjectId` is nullable: a change made by a scheduled or migration actor has
--     no interactive principal, and refusing to record it would be worse than
--     recording who it was.
--   * No foreign key to the user table. The most important audit rows are the ones
--     about accounts that no longer exist, so a cascading or restrictive FK would
--     either erase the evidence or block the delete.
--   * Credentials must never reach these columns; the recorder redacts them rather
--     than relying on every call site to remember.

CREATE SCHEMA IF NOT EXISTS campuscore_audit;

CREATE TABLE IF NOT EXISTS campuscore_audit."AdminAudit" (
    "id" VARCHAR(120) PRIMARY KEY,
    "actorId" VARCHAR(120),
    "actorLabel" VARCHAR(240),
    "action" VARCHAR(48) NOT NULL,
    "entityType" VARCHAR(48) NOT NULL,
    "entityId" VARCHAR(120),
    -- Human-readable one-liner so an operator reading the table directly does not
    -- have to parse the state blobs to learn what happened.
    "summary" VARCHAR(500),
    "beforeState" TEXT,
    "afterState" TEXT,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT admin_audit_action_ck CHECK (length(trim("action")) > 0),
    CONSTRAINT admin_audit_entity_ck CHECK (length(trim("entityType")) > 0)
);

-- "What happened to this record" is the common investigation, and the table is
-- append-only so it only grows.
CREATE INDEX IF NOT EXISTS admin_audit_entity_idx
    ON campuscore_audit."AdminAudit" ("entityType", "entityId", "createdAt" DESC);

-- "What did this administrator do" is the other one.
CREATE INDEX IF NOT EXISTS admin_audit_actor_idx
    ON campuscore_audit."AdminAudit" ("actorId", "createdAt" DESC);
