-- Backfill the publication instant for official grades written after V147 by
-- paths that never set "gradePublishedAt" (ad-hoc seed inserts; the withdrawal
-- writer is fixed alongside so new rows always stamp the instant). Withdrawn
-- W records are not appealable and keep NULL. Restricting the update to NULL
-- keeps a retry from moving appeal deadlines forward; IS DISTINCT FROM also
-- repairs a pathological PUBLISHED row whose status is NULL.

UPDATE academic."Enrollment"
   SET "gradePublishedAt" = CURRENT_TIMESTAMP
 WHERE "gradeStatus" IN ('PUBLISHED', 'APPEALED')
   AND "gradePublishedAt" IS NULL
   AND "status" IS DISTINCT FROM 'WITHDRAWN';
