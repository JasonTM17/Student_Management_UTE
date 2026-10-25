-- Faculty-targeted announcements (thesis spec R14). An announcement may now
-- carry an optional list of faculty ids: a student sees it when their
-- curriculum's department belongs to that faculty, a lecturer when their own
-- department does, and the list combines with the existing role/year/section
-- dimensions (AND across dimensions, OR within one). Zero or empty targets
-- keep the current behaviour — every faculty matches — so existing rows keep
-- their audience. Composes with the department-target dimension of
-- V148__announcement_department_targets on the same column family.

ALTER TABLE engagement."Announcement"
    ADD COLUMN IF NOT EXISTS "targetFaculties" TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];
