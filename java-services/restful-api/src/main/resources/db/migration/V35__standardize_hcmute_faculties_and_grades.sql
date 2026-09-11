-- Institutional labels used by the demo catalog. Historical course codes remain unchanged.
CREATE TABLE IF NOT EXISTS academic."GradeItemLegacyArchive" (
    "sourceGradeItemId" VARCHAR(120) PRIMARY KEY,
    "sectionId" VARCHAR(120) NOT NULL,
    "name" VARCHAR(180) NOT NULL,
    "type" VARCHAR(60) NOT NULL,
    "maxScore" NUMERIC(5, 2) NOT NULL,
    "weight" NUMERIC(5, 2) NOT NULL,
    "gradedAt" TIMESTAMPTZ,
    "createdAt" TIMESTAMPTZ,
    "archivedByMigration" VARCHAR(40) NOT NULL DEFAULT 'V35',
    "archivedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."StudentGradeLegacyArchive" (
    "sourceStudentGradeId" VARCHAR(120) PRIMARY KEY,
    "enrollmentId" VARCHAR(120) NOT NULL,
    "gradeItemId" VARCHAR(120) NOT NULL,
    "score" NUMERIC(5, 2),
    "archivedByMigration" VARCHAR(40) NOT NULL DEFAULT 'V35',
    "archivedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO academic."GradeItemLegacyArchive" (
    "sourceGradeItemId", "sectionId", "name", "type", "maxScore", "weight", "gradedAt", "createdAt"
)
SELECT "id", "sectionId", "name", "type", "maxScore", "weight", "gradedAt", "createdAt"
FROM academic."GradeItem"
ON CONFLICT ("sourceGradeItemId") DO NOTHING;

INSERT INTO academic."StudentGradeLegacyArchive" (
    "sourceStudentGradeId", "enrollmentId", "gradeItemId", "score"
)
SELECT "id", "enrollmentId", "gradeItemId", "score"
FROM academic."StudentGrade"
ON CONFLICT ("sourceStudentGradeId") DO NOTHING;

UPDATE academic."Faculty"
SET "name" = 'Khoa Công nghệ Thông tin', "nameVi" = 'Khoa Công nghệ Thông tin',
    "nameEn" = 'Faculty of Information Technology', "code" = 'CNTT'
WHERE "id" = 'faculty-demo' OR "code" IN ('FIT', 'SE') OR lower("nameEn") = 'software engineering';

UPDATE academic."Department"
SET "name" = 'Khoa Công nghệ Thông tin', "nameVi" = 'Khoa Công nghệ Thông tin',
    "nameEn" = 'Faculty of Information Technology', "code" = 'CNTT'
WHERE "id" = 'department-demo' OR "code" = 'SE' OR lower("nameEn") = 'software engineering';

UPDATE academic."Curriculum"
SET "name" = 'Công nghệ Thông tin', "nameVi" = 'Công nghệ Thông tin',
    "nameEn" = 'Information Technology', "code" = 'CNTT2026'
WHERE "id" = 'curriculum-demo' OR lower("nameEn") = 'software engineering';

-- Convert legacy midterm/project components into the canonical process component.
WITH ranked AS (
    SELECT "id", row_number() OVER (PARTITION BY "sectionId" ORDER BY "createdAt", "id") AS position
    FROM academic."GradeItem"
    WHERE "type" <> 'FINAL'
)
UPDATE academic."GradeItem" item
SET "type" = 'PROCESS', "name" = 'Điểm quá trình (ĐQT - 50%)', "maxScore" = 10, "weight" = 50
FROM ranked
WHERE item."id" = ranked."id" AND ranked.position = 1;

UPDATE academic."GradeItem"
SET "name" = 'Điểm cuối kỳ (ĐCK - 50%)', "maxScore" = 10, "weight" = 50
WHERE "type" = 'FINAL';

INSERT INTO academic."GradeItem" ("id", "sectionId", "name", "type", "maxScore", "weight")
SELECT CASE
           WHEN length(section."id" || '-process-50') <= 120 THEN section."id" || '-process-50'
           ELSE 'v35-process-' || md5(section."id")
       END,
       section."id", 'Điểm quá trình (ĐQT - 50%)', 'PROCESS', 10, 50
FROM academic."Section" section
WHERE NOT EXISTS (SELECT 1 FROM academic."GradeItem" item WHERE item."sectionId" = section."id" AND item."type" = 'PROCESS');

INSERT INTO academic."GradeItem" ("id", "sectionId", "name", "type", "maxScore", "weight")
SELECT CASE
           WHEN length(section."id" || '-final-50') <= 120 THEN section."id" || '-final-50'
           ELSE 'v35-final-' || md5(section."id")
       END,
       section."id", 'Điểm cuối kỳ (ĐCK - 50%)', 'FINAL', 10, 50
FROM academic."Section" section
WHERE NOT EXISTS (SELECT 1 FROM academic."GradeItem" item WHERE item."sectionId" = section."id" AND item."type" = 'FINAL');

WITH component_scores AS (
    SELECT enrollment."id",
           max(grade."score") FILTER (WHERE item."type" = 'PROCESS') AS process_score,
           max(grade."score") FILTER (WHERE item."type" = 'FINAL') AS final_score
    FROM academic."Enrollment" enrollment
    LEFT JOIN academic."StudentGrade" grade ON grade."enrollmentId" = enrollment."id"
    LEFT JOIN academic."GradeItem" item ON item."id" = grade."gradeItemId"
    GROUP BY enrollment."id"
), totals AS (
    SELECT "id", round((process_score + final_score) / 2, 2) AS total
    FROM component_scores WHERE process_score IS NOT NULL AND final_score IS NOT NULL
)
UPDATE academic."Enrollment" enrollment
SET "finalGrade" = totals.total,
    "letterGrade" = CASE
        WHEN totals.total >= 9.0 THEN 'A+' WHEN totals.total >= 8.5 THEN 'A'
        WHEN totals.total >= 8.0 THEN 'B+' WHEN totals.total >= 7.0 THEN 'B'
        WHEN totals.total >= 6.5 THEN 'C+' WHEN totals.total >= 5.5 THEN 'C'
        WHEN totals.total >= 5.0 THEN 'D+' WHEN totals.total >= 4.0 THEN 'D' ELSE 'F' END,
    "updatedAt" = CURRENT_TIMESTAMP
FROM totals WHERE enrollment."id" = totals."id";
