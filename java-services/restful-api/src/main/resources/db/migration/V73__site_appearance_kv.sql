-- Site appearance (accent, homepage hero copy, homepage post order) used to
-- live only in the Next.js server's filesystem. On serverless targets that
-- file disappears on every deploy, so the site-wide branding promise silently
-- broke. A single-row KV table makes the persistence contract honest: one
-- authoritative payload, readable anonymously (it is the public homepage
-- chrome), writable only by administrators.
CREATE SCHEMA IF NOT EXISTS site;

CREATE TABLE IF NOT EXISTS site."Appearance" (
    "id" VARCHAR(40) PRIMARY KEY,
    "payload" TEXT NOT NULL,
    "updatedBy" VARCHAR(120),
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT site_appearance_payload_ck CHECK (length("payload") <= 131072)
);
