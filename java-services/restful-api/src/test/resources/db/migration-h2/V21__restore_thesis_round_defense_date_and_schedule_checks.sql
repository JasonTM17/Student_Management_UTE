-- H2 twin of PostgreSQL V66. H2 enforces the same row-level schedule oracle;
-- service tests cover conditional presence and phase-specific requirements.
-- The production profile creates the academic catalogue in V3. The compact
-- H2 fixture omits that catalogue, but topic mutations still resolve active
-- departments from the authoritative table. Keep the minimum surface here.
CREATE SCHEMA IF NOT EXISTS academic;
CREATE TABLE IF NOT EXISTS academic."Faculty" (
    "id" VARCHAR(120) PRIMARY KEY,
    "name" VARCHAR(240) NOT NULL,
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS academic."Department" (
    "id" VARCHAR(120) PRIMARY KEY,
    "name" VARCHAR(240) NOT NULL,
    "nameEn" VARCHAR(240),
    "nameVi" VARCHAR(240),
    "code" VARCHAR(80),
    "facultyId" VARCHAR(120),
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO academic."Faculty" ("id", "name", "isActive")
SELECT 'faculty-demo', 'Faculty of Information Technology', TRUE
WHERE NOT EXISTS (SELECT 1 FROM academic."Faculty" WHERE "id" = 'faculty-demo');
INSERT INTO academic."Department" ("id", "name", "nameEn", "nameVi", "code", "facultyId", "isActive")
SELECT 'department-demo', 'Bộ môn Công nghệ Phần mềm', 'Software Engineering',
       'Bộ môn Công nghệ Phần mềm', 'SE', 'faculty-demo', TRUE
WHERE NOT EXISTS (SELECT 1 FROM academic."Department" WHERE "id" = 'department-demo');
INSERT INTO academic."Department" ("id", "name", "nameEn", "nameVi", "code", "facultyId", "isActive")
SELECT 'department-other', 'Bộ môn Hệ thống Thông tin', 'Information Systems',
       'Bộ môn Hệ thống Thông tin', 'IS', 'faculty-demo', TRUE
WHERE NOT EXISTS (SELECT 1 FROM academic."Department" WHERE "id" = 'department-other');

-- V10 backfills old rounds with identical lecturer/student windows. Repair
-- only that mechanical legacy shape before installing the stricter check;
-- independently authored dates are left for the PostgreSQL census to review.
UPDATE thesis.thesis_registration_round
SET lecturer_submit_end = registration_start,
    lecturer_submit_start = registration_start - INTERVAL '1' SECOND
WHERE lecturer_submit_start = registration_start
  AND lecturer_submit_end = registration_end
  AND registration_end > registration_start;

ALTER TABLE thesis.thesis_registration_round
    ADD COLUMN IF NOT EXISTS defense_date TIMESTAMP WITH TIME ZONE;

ALTER TABLE thesis.thesis_registration_round
    ADD CONSTRAINT IF NOT EXISTS thesis_round_schedule_order_valid
    CHECK (
        lecturer_submit_end > lecturer_submit_start
        AND registration_end > registration_start
        AND registration_start >= lecturer_submit_end
        AND (proposal_publish_at IS NULL OR proposal_publish_at >= lecturer_submit_end)
        AND (gvpb_deadline IS NULL OR gvpb_deadline >= registration_end)
        AND (report_date IS NULL OR gvpb_deadline IS NULL OR report_date >= gvpb_deadline)
        AND (defense_date IS NULL OR report_date IS NULL OR defense_date >= report_date)
    );
