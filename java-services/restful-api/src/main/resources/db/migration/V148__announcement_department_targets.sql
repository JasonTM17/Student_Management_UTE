-- Department-targeted announcements (ATT1). An announcement may now carry an
-- optional list of department ids: a student sees it when their curriculum's
-- department matches, a lecturer when their own department matches, and the
-- list combines with the existing role/year/section dimensions (AND across
-- dimensions, OR within one). Zero or empty targets keep the current
-- behaviour — every department matches — so existing rows keep their audience.
-- Forward-only and idempotent: V145 (withdrawal), V146 (curriculum marker)
-- and V147 (grade appeals) are reserved by other branches and are renumbered
-- at integration time, so this file must not depend on any of them.

ALTER TABLE engagement."Announcement"
    ADD COLUMN IF NOT EXISTS "targetDepartments" TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];
