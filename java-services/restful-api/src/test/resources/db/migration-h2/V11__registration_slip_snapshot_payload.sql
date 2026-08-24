ALTER TABLE academic."RegistrationSlip"
    ADD COLUMN IF NOT EXISTS "snapshotPayload" CLOB;
