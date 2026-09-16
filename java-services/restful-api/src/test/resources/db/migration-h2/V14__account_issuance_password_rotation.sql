-- H2 twin of V47: accounts issued by the Academic Office must rotate their
-- temporary password before the portal accepts them. H2 in PostgreSQL mode
-- rejects multi-column ADD COLUMN in one statement; this migration needs only
-- the single column anyway.
ALTER TABLE "campuscore_auth"."User" ADD COLUMN "mustChangePassword" BOOLEAN NOT NULL DEFAULT FALSE;
