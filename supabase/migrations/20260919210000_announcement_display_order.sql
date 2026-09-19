-- Supabase Migration: Announcement Display Ordering
-- Mirrors Flyway V70__announcement_display_order.sql for Supabase PostgreSQL parity
-- Adds a nullable, administrator-assigned display order to engagement."Announcement"
-- for the public homepage feed and the admin list.

ALTER TABLE engagement."Announcement"
    ADD COLUMN IF NOT EXISTS "displayOrder" INTEGER;

CREATE INDEX IF NOT EXISTS engagement_announcement_display_order_idx
    ON engagement."Announcement" ("displayOrder")
    WHERE "displayOrder" IS NOT NULL;
