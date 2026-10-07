-- Demo showcase: give the canonical demo student (Nguyen Tien Son, student@campuscore.edu)
-- two real group-mate accounts so the thesis workspace can demonstrate the full
-- three-member group story end to end.
--
--   student2@campuscore.edu / student3@campuscore.edu  (password = the shared
--   demo password, same bcrypt hash as the other seeded demo accounts)
--
-- The migration is intentionally adaptive because the live and seeded
-- databases have diverged:
--   * production keeps Son's SUBMITTED group f9a4d567 in the open KLTN round,
--     so the new members join that roster in place;
--   * a freshly seeded database has that group CANCELLED, so a new group is
--     created in the same open round and everyone is enrolled there.
-- Every insert is guarded (WHERE NOT EXISTS / ON CONFLICT DO NOTHING) so the
-- migration stays re-runnable on partially provisioned databases.
--
-- Data planted per member: user + both student profiles + STUDENT role,
-- ENROLLED registrations copied from the leader's current semester roster
-- (section enrolledCount is incremented to stay consistent), and the leader's
-- completed-grade history copied with per-member variation so transcripts and
-- GPA surfaces render a full record.

-- ---------------------------------------------------------------------------
-- 1. Auth users (shared demo password hash = password123, same as student-user)
-- ---------------------------------------------------------------------------
INSERT INTO campuscore_auth."User" (
    "id", "email", "password", "firstName", "lastName",
    "phone", "gender", "status", "emailVerified",
    "isSuperAdmin", "failedLoginAttempts", "mustChangePassword", "twoFactorEnabled"
)
SELECT 'student-user-2', 'student2@campuscore.edu',
       '$2a$10$raV9MB3Qmj1Rbu2Rmo1vNup7VsC2OM3AqmcTcTLzbNyMyI4r2rJBe',
       'Minh Quân', 'Trần', '0909334455', 'MALE', 'ACTIVE',
       TRUE, FALSE, 0, FALSE, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."User" WHERE "email" = 'student2@campuscore.edu'
);

INSERT INTO campuscore_auth."User" (
    "id", "email", "password", "firstName", "lastName",
    "phone", "gender", "status", "emailVerified",
    "isSuperAdmin", "failedLoginAttempts", "mustChangePassword", "twoFactorEnabled"
)
SELECT 'student-user-3', 'student3@campuscore.edu',
       '$2a$10$raV9MB3Qmj1Rbu2Rmo1vNup7VsC2OM3AqmcTcTLzbNyMyI4r2rJBe',
       'Thu Hằng', 'Lê Thị', '0909667788', 'FEMALE', 'ACTIVE',
       TRUE, FALSE, 0, FALSE, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."User" WHERE "email" = 'student3@campuscore.edu'
);

INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-student-2', 'student-user-2', 'role-student'
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."UserRole"
    WHERE "userId" = 'student-user-2' AND "roleId" = 'role-student'
);
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-student-3', 'student-user-3', 'role-student'
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."UserRole"
    WHERE "userId" = 'student-user-3' AND "roleId" = 'role-student'
);

-- ---------------------------------------------------------------------------
-- 2. Student profiles in both stores (auth directory + academic record).
--    Same cohort, curriculum, and admission year as the group leader so the
--    trio reads as real classmates.
-- ---------------------------------------------------------------------------
INSERT INTO campuscore_auth."Student" (
    "id", "userId", "studentId", "curriculumId", "year", "status", "admissionDate"
)
SELECT 'student-profile-058', 'student-user-2', '24110058', 'curriculum-demo', 2,
       'ACTIVE', '2025-09-01'::timestamptz
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."Student" WHERE "userId" = 'student-user-2'
);
INSERT INTO campuscore_auth."Student" (
    "id", "userId", "studentId", "curriculumId", "year", "status", "admissionDate"
)
SELECT 'student-profile-059', 'student-user-3', '24110059', 'curriculum-demo', 2,
       'ACTIVE', '2025-09-01'::timestamptz
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."Student" WHERE "userId" = 'student-user-3'
);

INSERT INTO academic."Student" (
    "id", "userId", "studentId", "curriculumId", "year", "status", "admissionDate"
)
SELECT 'student-profile-058', 'student-user-2', '24110058', 'curriculum-demo', 2,
       'ACTIVE', '2025-09-01'::timestamptz
WHERE NOT EXISTS (
    SELECT 1 FROM academic."Student" WHERE "userId" = 'student-user-2'
);
INSERT INTO academic."Student" (
    "id", "userId", "studentId", "curriculumId", "year", "status", "admissionDate"
)
SELECT 'student-profile-059', 'student-user-3', '24110059', 'curriculum-demo', 2,
       'ACTIVE', '2025-09-01'::timestamptz
WHERE NOT EXISTS (
    SELECT 1 FROM academic."Student" WHERE "userId" = 'student-user-3'
);

-- ---------------------------------------------------------------------------
-- 3. Current-semester registrations: mirror the leader's ENROLLED sections so
--    the trio shares one timetable, then top up the denormalised seat counts.
-- ---------------------------------------------------------------------------
INSERT INTO academic."Enrollment" (
    "id", "studentId", "sectionId", "semesterId", "status",
    "enrolledAt", "gradeStatus", "courseId", "roundId", "creditsSnapshot", "version"
)
SELECT 'enr-s2-demo-' || src."sectionId",
       'student-profile-058', src."sectionId", src."semesterId", 'ENROLLED',
       CURRENT_TIMESTAMP, 'NOT_GRADED', src."courseId", src."roundId",
       src."creditsSnapshot", 0
FROM academic."Enrollment" src
WHERE src."studentId" = 'student-profile' AND src."status" = 'ENROLLED'
ON CONFLICT ("id") DO NOTHING;

INSERT INTO academic."Enrollment" (
    "id", "studentId", "sectionId", "semesterId", "status",
    "enrolledAt", "gradeStatus", "courseId", "roundId", "creditsSnapshot", "version"
)
SELECT 'enr-s3-demo-' || src."sectionId",
       'student-profile-059', src."sectionId", src."semesterId", 'ENROLLED',
       CURRENT_TIMESTAMP, 'NOT_GRADED', src."courseId", src."roundId",
       src."creditsSnapshot", 0
FROM academic."Enrollment" src
WHERE src."studentId" = 'student-profile' AND src."status" = 'ENROLLED'
ON CONFLICT ("id") DO NOTHING;

UPDATE academic."Section" s
SET "enrolledCount" = "enrolledCount" + 2
WHERE s.id IN (
    SELECT e."sectionId" FROM academic."Enrollment" e
    WHERE e."studentId" IN ('student-profile-058', 'student-profile-059')
      AND e.status = 'ENROLLED'
    GROUP BY e."sectionId"
    HAVING COUNT(*) = 2
);
UPDATE academic."Section" s
SET "enrolledCount" = "enrolledCount" + 1
WHERE s.id IN (
    SELECT e."sectionId" FROM academic."Enrollment" e
    WHERE e."studentId" IN ('student-profile-058', 'student-profile-059')
      AND e.status = 'ENROLLED'
    GROUP BY e."sectionId"
    HAVING COUNT(*) = 1
);

-- ---------------------------------------------------------------------------
-- 4. Completed-grade history so transcripts/GPA render for the new accounts.
--    Grades are nudged per member (s2 slightly lower, s3 slightly higher) and
--    the letter grade is recomputed on the official band boundaries.
-- ---------------------------------------------------------------------------
WITH src AS (
    SELECT e.*, ROW_NUMBER() OVER (ORDER BY e.id) AS rn
    FROM academic."Enrollment" e
    WHERE e."studentId" = 'student-profile' AND e.status = 'COMPLETED'
)
INSERT INTO academic."Enrollment" (
    "id", "studentId", "sectionId", "semesterId", "status",
    "enrolledAt", "gradeStatus", "finalGrade", "letterGrade",
    "courseId", "roundId", "creditsSnapshot", "version"
)
SELECT 'enr-s2-hist-' || src.rn,
       'student-profile-058', src."sectionId", src."semesterId", 'COMPLETED',
       src."enrolledAt", 'PUBLISHED',
       GREATEST(5.0, ROUND(src."finalGrade" - 0.3, 2)),
       CASE
           WHEN GREATEST(5.0, ROUND(src."finalGrade" - 0.3, 2)) >= 8.5 THEN 'A'
           WHEN GREATEST(5.0, ROUND(src."finalGrade" - 0.3, 2)) >= 8.0 THEN 'B+'
           WHEN GREATEST(5.0, ROUND(src."finalGrade" - 0.3, 2)) >= 7.0 THEN 'B'
           WHEN GREATEST(5.0, ROUND(src."finalGrade" - 0.3, 2)) >= 6.5 THEN 'C+'
           WHEN GREATEST(5.0, ROUND(src."finalGrade" - 0.3, 2)) >= 5.5 THEN 'C'
           WHEN GREATEST(5.0, ROUND(src."finalGrade" - 0.3, 2)) >= 4.0 THEN 'D'
           ELSE 'F'
       END,
       src."courseId", src."roundId", src."creditsSnapshot", 0
FROM src
ON CONFLICT ("id") DO NOTHING;

WITH src AS (
    SELECT e.*, ROW_NUMBER() OVER (ORDER BY e.id) AS rn
    FROM academic."Enrollment" e
    WHERE e."studentId" = 'student-profile' AND e.status = 'COMPLETED'
)
INSERT INTO academic."Enrollment" (
    "id", "studentId", "sectionId", "semesterId", "status",
    "enrolledAt", "gradeStatus", "finalGrade", "letterGrade",
    "courseId", "roundId", "creditsSnapshot", "version"
)
SELECT 'enr-s3-hist-' || src.rn,
       'student-profile-059', src."sectionId", src."semesterId", 'COMPLETED',
       src."enrolledAt", 'PUBLISHED',
       LEAST(10.0, ROUND(src."finalGrade" + 0.2, 2)),
       CASE
           WHEN LEAST(10.0, ROUND(src."finalGrade" + 0.2, 2)) >= 8.5 THEN 'A'
           WHEN LEAST(10.0, ROUND(src."finalGrade" + 0.2, 2)) >= 8.0 THEN 'B+'
           WHEN LEAST(10.0, ROUND(src."finalGrade" + 0.2, 2)) >= 7.0 THEN 'B'
           WHEN LEAST(10.0, ROUND(src."finalGrade" + 0.2, 2)) >= 6.5 THEN 'C+'
           WHEN LEAST(10.0, ROUND(src."finalGrade" + 0.2, 2)) >= 5.5 THEN 'C'
           WHEN LEAST(10.0, ROUND(src."finalGrade" + 0.2, 2)) >= 4.0 THEN 'D'
           ELSE 'F'
       END,
       src."courseId", src."roundId", src."creditsSnapshot", 0
FROM src
ON CONFLICT ("id") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. Thesis trio in the open KLTN round.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_round_id CONSTANT UUID := 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60';
    v_group_id UUID;
BEGIN
    -- Production path: reuse the leader's live (non-cancelled) group in the
    -- open round. Fresh-seed path: nothing survives, so create a roster.
    SELECT g.id INTO v_group_id
    FROM thesis.thesis_group g
    WHERE g."leader_student_id" = 'student-profile'
      AND g.round_id = v_round_id
      AND g.status <> 'CANCELLED'
    ORDER BY g.created_at DESC
    LIMIT 1;

    IF v_group_id IS NULL THEN
        v_group_id := 'a7c5e2d1-8f3b-4a6c-9d2e-5b7a8c9d0e1f';
        INSERT INTO thesis.thesis_group (
            id, round_id, leader_student_id, topic_id, status,
            approval_status, version, created_at, updated_at
        )
        VALUES (
            v_group_id, v_round_id, 'student-profile',
            'd7e2b345-0c8f-423e-9412-2c3d4e5f6a71',
            'SUBMITTED', 'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        )
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO thesis.thesis_group_member (
            id, group_id, round_id, student_id,
            member_order, is_leader, is_external, created_at
        )
        SELECT gen_random_uuid(), v_group_id, v_round_id, 'student-profile',
               1, TRUE, FALSE, CURRENT_TIMESTAMP
        WHERE NOT EXISTS (
            SELECT 1 FROM thesis.thesis_group_member
            WHERE group_id = v_group_id AND student_id = 'student-profile'
        );
    END IF;

    INSERT INTO thesis.thesis_group_member (
        id, group_id, round_id, student_id,
        member_order, is_leader, is_external, created_at
    )
    SELECT gen_random_uuid(), v_group_id, v_round_id, 'student-profile-058',
           2, FALSE, FALSE, CURRENT_TIMESTAMP
    WHERE NOT EXISTS (
        SELECT 1 FROM thesis.thesis_group_member
        WHERE group_id = v_group_id AND student_id = 'student-profile-058'
    );

    INSERT INTO thesis.thesis_group_member (
        id, group_id, round_id, student_id,
        member_order, is_leader, is_external, created_at
    )
    SELECT gen_random_uuid(), v_group_id, v_round_id, 'student-profile-059',
           3, FALSE, FALSE, CURRENT_TIMESTAMP
    WHERE NOT EXISTS (
        SELECT 1 FROM thesis.thesis_group_member
        WHERE group_id = v_group_id AND student_id = 'student-profile-059'
    );
END $$;
