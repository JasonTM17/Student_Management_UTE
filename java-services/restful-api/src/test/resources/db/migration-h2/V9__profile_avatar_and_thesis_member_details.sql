-- Keep the H2 persistence-test schema aligned with the production V29 shape.
-- IF NOT EXISTS guards ordering flakes: a flyway-disabled test class whose
-- fixture created campuscore_auth."User" first leaves the table without the
-- column when this migration finally runs (V2's CREATE is IF NOT EXISTS too).
ALTER TABLE campuscore_auth."User" ADD COLUMN IF NOT EXISTS "avatar" VARCHAR(500);
ALTER TABLE campuscore_auth."User" ALTER COLUMN "avatar" VARCHAR(200000);

ALTER TABLE thesis.thesis_group_member
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(150);

ALTER TABLE thesis.thesis_group_member
    ADD COLUMN IF NOT EXISTS contact VARCHAR(150);

ALTER TABLE thesis.thesis_group_member
    ADD COLUMN IF NOT EXISTS is_external BOOLEAN NOT NULL DEFAULT FALSE;
