-- H2 parity fixture for the V150 backfill: same predicate and idempotent
-- shape as the PostgreSQL migration so persistence tests exercise the same
-- rows a retry would leave untouched.

UPDATE academic."Enrollment"
   SET "gradePublishedAt" = CURRENT_TIMESTAMP
 WHERE "gradeStatus" IN ('PUBLISHED', 'APPEALED')
   AND "gradePublishedAt" IS NULL
   AND "status" IS DISTINCT FROM 'WITHDRAWN';
