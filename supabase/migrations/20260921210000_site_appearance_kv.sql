-- Supabase Migration: Site Appearance Key-Value Row
-- Mirrors Flyway V73__site_appearance_kv.sql for Supabase PostgreSQL parity
-- Holds the single site-wide branding payload (accent theme, homepage hero copy,
-- homepage post order) that used to live in the Next.js server's filesystem and
-- vanished on every redeploy. Public-safe read, administrator-only write.

CREATE SCHEMA IF NOT EXISTS site;

CREATE TABLE IF NOT EXISTS site."Appearance" (
    "id" VARCHAR(40) PRIMARY KEY,
    "payload" TEXT NOT NULL,
    -- Writer label from the JWT subject; SiteAppearanceStore.MAX_UPDATED_BY_CHARS
    -- clips to this width so a long subject cannot fail the save.
    "updatedBy" VARCHAR(255),
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT site_appearance_payload_ck CHECK (length("payload") <= 131072)
);
