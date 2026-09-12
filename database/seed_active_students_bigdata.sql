-- Seed Active Students Big Data for CampusUTE
-- Ensures all 226 students have exactly 5 active enrollments in uncompleted courses in semester-demo.
--
-- NOTE (2026-09-12 remediation): V26__enrich_demo_campus_population.sql seeds up to 7 active
-- sections per student (generate_series(0,6) with per-course dedup). Students whose picks never
-- collided therefore kept 7 active courses, which violates the "4 to 6 active sections" contract
-- of plans/20260911-active-students-bigdata-and-thesis-lifecycle. Step 4 below normalizes every
-- student down to exactly 5 active enrollments so the population stays inside the contract no
-- matter how many rows the migrations created.

BEGIN;

-- 1. Clean conflicting demo enrollments for student-profile
DELETE FROM "academic"."Enrollment"
WHERE "studentId" = 'student-profile'
  AND "semesterId" = 'semester-demo';

-- 2. Enroll student-profile in 5 uncompleted curriculum courses
INSERT INTO "academic"."Enrollment" (
    id, "studentId", "sectionId", "courseId", "semesterId",
    status, "gradeStatus", "creditsSnapshot", "enrolledAt",
    "createdAt", "updatedAt", version
) VALUES
('enr-prof-013', 'student-profile', 'section-auto-013', 'course-auto-013', 'semester-demo', 'ENROLLED', 'NOT_GRADED', 3, NOW(), NOW(), NOW(), 0),
('enr-prof-014', 'student-profile', 'section-auto-014', 'course-auto-014', 'semester-demo', 'ENROLLED', 'NOT_GRADED', 3, NOW(), NOW(), NOW(), 0),
('enr-prof-015', 'student-profile', 'section-auto-015', 'course-auto-015', 'semester-demo', 'ENROLLED', 'NOT_GRADED', 3, NOW(), NOW(), NOW(), 0),
('enr-prof-016', 'student-profile', 'section-auto-016', 'course-auto-016', 'semester-demo', 'ENROLLED', 'NOT_GRADED', 3, NOW(), NOW(), NOW(), 0),
('enr-prof-017', 'student-profile', 'section-auto-017', 'course-auto-017', 'semester-demo', 'ENROLLED', 'NOT_GRADED', 3, NOW(), NOW(), NOW(), 0);

-- 3. DO block for the remaining 225 students:
DO $$
DECLARE
    r_student RECORD;
    v_current_active INT;
    v_needed INT;
    r_sec RECORD;
BEGIN
    FOR r_student IN 
        SELECT id FROM "academic"."Student" WHERE id <> 'student-profile' ORDER BY id
    LOOP
        -- Count how many active enrollments student already has in semester-demo
        SELECT COUNT(*) INTO v_current_active
        FROM "academic"."Enrollment"
        WHERE "studentId" = r_student.id 
          AND "semesterId" = 'semester-demo'
          AND status IN ('ENROLLED', 'CONFIRMED');

        v_needed := 5 - v_current_active;

        IF v_needed > 0 THEN
            -- Find available sections for courses the student has NOT completed and NOT already enrolled in semester-demo
            FOR r_sec IN
                SELECT s.id as sec_id, s."courseId" as course_id
                FROM "academic"."Section" s
                JOIN "academic"."CurriculumCourse" cc ON cc."courseId" = s."courseId" AND cc."curriculumId" = 'curriculum-demo'
                WHERE s."semesterId" = 'semester-demo'
                  AND s."courseId" NOT IN (
                      -- Completed in any semester
                      SELECT e."courseId" FROM "academic"."Enrollment" e
                      WHERE e."studentId" = r_student.id
                        AND (e.status = 'COMPLETED' OR (e."gradeStatus" = 'PUBLISHED' AND e."letterGrade" IS NOT NULL AND e."letterGrade" <> 'F'))
                  )
                  AND s."courseId" NOT IN (
                      -- Already enrolled in semester-demo
                      SELECT e."courseId" FROM "academic"."Enrollment" e
                      WHERE e."studentId" = r_student.id AND e."semesterId" = 'semester-demo'
                  )
                ORDER BY s."enrolledCount" ASC, s.id ASC
                LIMIT v_needed
            LOOP
                INSERT INTO "academic"."Enrollment" (
                    id, "studentId", "sectionId", "courseId", "semesterId",
                    status, "gradeStatus", "creditsSnapshot", "enrolledAt",
                    "createdAt", "updatedAt", version
                ) VALUES (
                    'enr-bigdata-' || substr(md5(random()::text || clock_timestamp()::text), 1, 16),
                    r_student.id,
                    r_sec.sec_id,
                    r_sec.course_id,
                    'semester-demo',
                    'ENROLLED',
                    'NOT_GRADED',
                    3,
                    NOW(),
                    NOW(),
                    NOW(),
                    0
                );
            END LOOP;
        END IF;
    END LOOP;
END $$;

-- 4. Normalize: no student may exceed 5 active enrollments in semester-demo.
--    Keeps the 5 oldest (deterministic by enrolledAt, then id) and removes the excess created
--    by migration V26 for students whose generated picks never collided on the same course.
WITH ranked AS (
    SELECT e.id,
           row_number() OVER (
               PARTITION BY e."studentId"
               ORDER BY e."enrolledAt" ASC, e.id ASC
           ) AS rn
    FROM "academic"."Enrollment" e
    WHERE e."semesterId" = 'semester-demo'
      AND e.status IN ('ENROLLED', 'CONFIRMED')
)
DELETE FROM "academic"."Enrollment" e
USING ranked r
WHERE e.id = r.id
  AND r.rn > 5;

-- 5. Update section enrolled counts
UPDATE "academic"."Section" s
SET "enrolledCount" = (
    SELECT COUNT(*) FROM "academic"."Enrollment" e
    WHERE e."sectionId" = s.id AND e.status IN ('ENROLLED', 'CONFIRMED')
)
WHERE s."semesterId" = 'semester-demo';

COMMIT;
