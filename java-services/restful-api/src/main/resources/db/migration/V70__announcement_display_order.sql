-- Flyway Migration V70: Announcement display ordering
-- Adds a nullable, administrator-assigned display order to engagement."Announcement"
-- for the public homepage feed and the admin list. Column naming follows the
-- table's established quoted-CamelCase convention ("featuredOrder", "targetRoles");
-- the JSON contract key is displayOrder.

ALTER TABLE engagement."Announcement"
    ADD COLUMN IF NOT EXISTS "displayOrder" INTEGER;

-- Partial index mirrors announcement_featured_idx (V64): only rows that carry
-- an order participate, and ordering scans stay small.
CREATE INDEX IF NOT EXISTS engagement_announcement_display_order_idx
    ON engagement."Announcement" ("displayOrder")
    WHERE "displayOrder" IS NOT NULL;
