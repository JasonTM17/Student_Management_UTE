-- Accounts are issued by the Academic Office. A flagged user must rotate the
-- issued temporary password before any business API accepts the session.
ALTER TABLE "campuscore_auth"."User"
    ADD COLUMN IF NOT EXISTS "mustChangePassword" BOOLEAN NOT NULL DEFAULT FALSE;
