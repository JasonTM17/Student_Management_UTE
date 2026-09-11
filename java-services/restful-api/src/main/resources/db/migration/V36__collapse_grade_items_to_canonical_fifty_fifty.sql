-- Preserve the original rubric rows before replacing the active gradebook with
-- the institutional 50-50 model. These archive tables are intentionally not
-- referenced back to the live tables because V36 deletes the source rows below.
CREATE TABLE IF NOT EXISTS academic."GradeItemLegacyArchive" (
    "sourceGradeItemId" VARCHAR(120) PRIMARY KEY,
    "sectionId" VARCHAR(120) NOT NULL,
    "name" VARCHAR(180) NOT NULL,
    "type" VARCHAR(60) NOT NULL,
    "maxScore" NUMERIC(5, 2) NOT NULL,
    "weight" NUMERIC(5, 2) NOT NULL,
    "gradedAt" TIMESTAMPTZ,
    "createdAt" TIMESTAMPTZ,
    "archivedByMigration" VARCHAR(40) NOT NULL DEFAULT 'V36',
    "archivedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic."StudentGradeLegacyArchive" (
    "sourceStudentGradeId" VARCHAR(120) PRIMARY KEY,
    "enrollmentId" VARCHAR(120) NOT NULL,
    "gradeItemId" VARCHAR(120) NOT NULL,
    "score" NUMERIC(5, 2),
    "archivedByMigration" VARCHAR(40) NOT NULL DEFAULT 'V36',
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

-- Preserve the normalized score that the 50-50 gradebook should display.
-- Multiple legacy in-course components are collapsed by their configured
-- weights; duplicate final rows follow the same deterministic weighted rule.
DROP TABLE IF EXISTS v36_component_scores;

CREATE TEMP TABLE v36_component_scores AS
SELECT enrollment."id" AS enrollment_id,
       enrollment."sectionId" AS section_id,
       round(
           sum(student_grade."score" * COALESCE(NULLIF(grade_item."weight", 0), 1))
               FILTER (WHERE grade_item."type" <> 'FINAL' AND student_grade."score" IS NOT NULL)
           / NULLIF(
               sum(COALESCE(NULLIF(grade_item."weight", 0), 1))
                   FILTER (WHERE grade_item."type" <> 'FINAL' AND student_grade."score" IS NOT NULL),
               0
           ),
           2
       ) AS process_score,
       round(
           sum(student_grade."score" * COALESCE(NULLIF(grade_item."weight", 0), 1))
               FILTER (WHERE grade_item."type" = 'FINAL' AND student_grade."score" IS NOT NULL)
           / NULLIF(
               sum(COALESCE(NULLIF(grade_item."weight", 0), 1))
                   FILTER (WHERE grade_item."type" = 'FINAL' AND student_grade."score" IS NOT NULL),
               0
           ),
           2
       ) AS final_score
FROM academic."Enrollment" enrollment
LEFT JOIN academic."StudentGradeLegacyArchive" student_grade ON student_grade."enrollmentId" = enrollment."id"
LEFT JOIN academic."GradeItemLegacyArchive" grade_item ON grade_item."sourceGradeItemId" = student_grade."gradeItemId"
GROUP BY enrollment."id", enrollment."sectionId";

DELETE FROM academic."StudentGrade";
DELETE FROM academic."GradeItem";

WITH section_grade_items AS (
    SELECT section."id" AS section_id,
           CASE
               WHEN length(section."id" || '-process-50') <= 120 THEN section."id" || '-process-50'
               ELSE 'v36-process-' || md5(section."id")
           END AS process_grade_item_id,
           CASE
               WHEN length(section."id" || '-final-50') <= 120 THEN section."id" || '-final-50'
               ELSE 'v36-final-' || md5(section."id")
           END AS final_grade_item_id
    FROM academic."Section" section
)
INSERT INTO academic."GradeItem" ("id", "sectionId", "name", "type", "maxScore", "weight")
SELECT process_grade_item_id, section_id, 'Điểm quá trình (ĐQT - 50%)', 'PROCESS', 10, 50
FROM section_grade_items
UNION ALL
SELECT final_grade_item_id, section_id, 'Điểm cuối kỳ (ĐCK - 50%)', 'FINAL', 10, 50
FROM section_grade_items;

WITH section_grade_items AS (
    SELECT section."id" AS section_id,
           CASE
               WHEN length(section."id" || '-process-50') <= 120 THEN section."id" || '-process-50'
               ELSE 'v36-process-' || md5(section."id")
           END AS process_grade_item_id,
           CASE
               WHEN length(section."id" || '-final-50') <= 120 THEN section."id" || '-final-50'
               ELSE 'v36-final-' || md5(section."id")
           END AS final_grade_item_id
    FROM academic."Section" section
)
INSERT INTO academic."StudentGrade" ("id", "enrollmentId", "gradeItemId", "score")
SELECT 'v36-' || md5(scores.enrollment_id || ':PROCESS'), scores.enrollment_id,
       items.process_grade_item_id, scores.process_score
FROM v36_component_scores scores
JOIN section_grade_items items ON items.section_id = scores.section_id
WHERE scores.process_score IS NOT NULL
UNION ALL
SELECT 'v36-' || md5(scores.enrollment_id || ':FINAL'), scores.enrollment_id,
       items.final_grade_item_id, scores.final_score
FROM v36_component_scores scores
JOIN section_grade_items items ON items.section_id = scores.section_id
WHERE scores.final_score IS NOT NULL;

WITH totals AS (
    SELECT enrollment_id, round((process_score + final_score) / 2, 2) AS total
    FROM v36_component_scores
    WHERE process_score IS NOT NULL AND final_score IS NOT NULL
)
UPDATE academic."Enrollment" enrollment
SET "finalGrade" = totals.total,
    "letterGrade" = CASE
        WHEN totals.total >= 9.0 THEN 'A+' WHEN totals.total >= 8.5 THEN 'A'
        WHEN totals.total >= 8.0 THEN 'B+' WHEN totals.total >= 7.0 THEN 'B'
        WHEN totals.total >= 6.5 THEN 'C+' WHEN totals.total >= 5.5 THEN 'C'
        WHEN totals.total >= 5.0 THEN 'D+' WHEN totals.total >= 4.0 THEN 'D' ELSE 'F' END,
    "updatedAt" = CURRENT_TIMESTAMP
FROM totals
WHERE enrollment."id" = totals.enrollment_id;

DROP TABLE IF EXISTS v36_component_scores;
