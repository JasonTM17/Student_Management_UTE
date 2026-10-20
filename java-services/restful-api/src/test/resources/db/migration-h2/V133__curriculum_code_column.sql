-- H2 mirror alignment: production V3 created academic."Curriculum" with a
-- "code" column, but the minimal V117 scaffold left it out. The thesis
-- teammate search selects cur."code" AS curriculum_code, so the mirror must
-- carry the column for that query to run under H2.
ALTER TABLE academic."Curriculum" ADD COLUMN IF NOT EXISTS "code" VARCHAR(40);
