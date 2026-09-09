-- V29: profile avatars and richer thesis group member details.
-- 1) The web portal stores the avatar as a base64 data URL produced by a
--    client-side resize, so the legacy VARCHAR(500) column must widen.
ALTER TABLE campuscore_auth."User" ALTER COLUMN "avatar" TYPE TEXT;

-- 2) Thesis groups must describe members who are not registerable students
--    (different department or school). display_name/contact carry their
--    identity for the reviewer; is_external marks them as unverified.
ALTER TABLE thesis.thesis_group_member
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS contact VARCHAR(150),
    ADD COLUMN IF NOT EXISTS is_external BOOLEAN NOT NULL DEFAULT FALSE;
