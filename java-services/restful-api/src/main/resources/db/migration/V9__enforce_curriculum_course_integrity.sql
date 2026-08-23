ALTER TABLE academic."CurriculumCourse"
    DROP CONSTRAINT IF EXISTS "CurriculumCourse_courseId_fkey";

ALTER TABLE academic."CurriculumCourse"
    ADD CONSTRAINT "CurriculumCourse_courseId_fkey"
    FOREIGN KEY ("courseId") REFERENCES academic."Course" ("id") ON DELETE CASCADE;
