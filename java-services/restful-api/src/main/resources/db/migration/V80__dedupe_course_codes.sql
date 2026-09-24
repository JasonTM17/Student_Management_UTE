-- V80: deduplicate course codes SE401-SE404 by renumbering the light department-fit-software family
-- to SE421-SE424 and adding a UNIQUE index on academic."Course"(code).
--
-- Background: V74 seeded 4 courses in department-fit-software with codes SE401-SE404,
-- which collided with the core demo courses (SE401 Java, SE402 Web, SE403 DSA, SE404 DB).
-- The department-fit-software courses have 0 enrollments and are cleanly renumbered to SE421-SE424.

UPDATE academic."Course"
SET "code" = 'SE421'
WHERE "id" = 'course-se-arch' AND "code" = 'SE401';

UPDATE academic."Course"
SET "code" = 'SE422'
WHERE "id" = 'course-se-quality' AND "code" = 'SE402';

UPDATE academic."Course"
SET "code" = 'SE423'
WHERE "id" = 'course-se-devops' AND "code" = 'SE403';

UPDATE academic."Course"
SET "code" = 'SE424'
WHERE "id" = 'course-se-analysis' AND "code" = 'SE404';

-- Update sectionNumbers for these sections to match the new course codes
UPDATE academic."Section"
SET "sectionNumber" = 'SE421-01'
WHERE "courseId" = 'course-se-arch' AND "sectionNumber" = 'SE401-01';

UPDATE academic."Section"
SET "sectionNumber" = 'SE422-01'
WHERE "courseId" = 'course-se-quality' AND "sectionNumber" = 'SE402-01';

UPDATE academic."Section"
SET "sectionNumber" = 'SE423-01'
WHERE "courseId" = 'course-se-devops' AND "sectionNumber" = 'SE403-01';

UPDATE academic."Section"
SET "sectionNumber" = 'SE424-01'
WHERE "courseId" = 'course-se-analysis' AND "sectionNumber" = 'SE404-01';

-- Enforce uniqueness of course codes across the entire institution
CREATE UNIQUE INDEX IF NOT EXISTS ux_course_code ON academic."Course"("code");
