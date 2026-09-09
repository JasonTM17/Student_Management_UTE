-- Keep the H2 persistence-test schema aligned with the production V29 shape.
ALTER TABLE campuscore_auth."User" ALTER COLUMN "avatar" VARCHAR(200000);

ALTER TABLE thesis.thesis_group_member
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(150);

ALTER TABLE thesis.thesis_group_member
    ADD COLUMN IF NOT EXISTS contact VARCHAR(150);

ALTER TABLE thesis.thesis_group_member
    ADD COLUMN IF NOT EXISTS is_external BOOLEAN NOT NULL DEFAULT FALSE;
