-- H2 parity fixture for department-targeted announcements (ATT1). Same
-- additive, idempotent column as the PostgreSQL V148; the H2 harness declares
-- arrays with the VARCHAR ARRAY spelling its tests already use.

ALTER TABLE engagement."Announcement"
    ADD COLUMN IF NOT EXISTS "targetDepartments" VARCHAR ARRAY NOT NULL DEFAULT ARRAY[];
