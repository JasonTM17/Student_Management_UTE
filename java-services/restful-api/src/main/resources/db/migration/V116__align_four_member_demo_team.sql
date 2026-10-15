CREATE TEMP TABLE v116_demo_team (
    user_id TEXT PRIMARY KEY,
    profile_id TEXT NOT NULL UNIQUE,
    email TEXT NOT NULL UNIQUE,
    student_number TEXT NOT NULL UNIQUE,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    full_name TEXT NOT NULL,
    is_leader BOOLEAN NOT NULL,
    member_order INTEGER NOT NULL UNIQUE
) ON COMMIT DROP;

INSERT INTO v116_demo_team VALUES
('student-user', 'student-profile', 'student@campuscore.edu', '24110054', 'Tiến Sơn', 'Nguyễn', 'Nguyễn Tiến Sơn', TRUE, 1),
('student-user-2', 'student-profile-058', 'student2@campuscore.edu', '24110345', 'Hồng Thủy', 'Huỳnh', 'Huỳnh Hồng Thủy', FALSE, 2),
('student-user-3', 'student-profile-059', 'student3@campuscore.edu', '24110051', 'Ngọc Quyên', 'Trần Thị', 'Trần Thị Ngọc Quyên', FALSE, 3),
('student-user-4', 'student-profile-team-004', 'student4@campuscore.edu', '24162050', 'Quốc Huy', 'Vũ', 'Vũ Quốc Huy', FALSE, 4);

SET LOCAL lock_timeout = '10s';
LOCK TABLE campuscore_auth."User", campuscore_auth."UserRole", campuscore_auth."Role",
    academic."Student", thesis.thesis_registration_round, thesis.thesis_group,
    thesis.thesis_group_member IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM campuscore_auth."Role" WHERE "id" = 'role-student' AND "name" = 'STUDENT') THEN
        RAISE EXCEPTION 'V116 demo student role is missing';
    END IF;
    IF EXISTS (
        SELECT 1 FROM v116_demo_team t
        LEFT JOIN campuscore_auth."User" u ON u."id" = t.user_id
        WHERE t.member_order <= 3 AND (u."id" IS NULL OR u."email" <> t.email OR u."isSuperAdmin")
    ) OR EXISTS (
        SELECT 1 FROM v116_demo_team t JOIN campuscore_auth."User" u
            ON u."id" = t.user_id OR u."email" = t.email
        WHERE u."id" <> t.user_id OR u."email" <> t.email OR u."isSuperAdmin"
    ) THEN
        RAISE EXCEPTION 'V116 demo account identity conflict; no accounts changed';
    END IF;
    IF EXISTS (
        SELECT 1 FROM v116_demo_team t
        LEFT JOIN academic."Student" s ON s."userId" = t.user_id
        WHERE t.member_order <= 3 AND (s."id" IS NULL OR s."id" <> t.profile_id)
    ) OR EXISTS (
        SELECT 1 FROM v116_demo_team t JOIN academic."Student" s
            ON s."userId" = t.user_id OR s."id" = t.profile_id OR s."studentId" = t.student_number
        WHERE s."userId" <> t.user_id OR s."id" <> t.profile_id
    ) THEN
        RAISE EXCEPTION 'V116 student identity or number conflict; no profiles changed';
    END IF;
    IF EXISTS (
        SELECT 1 FROM campuscore_auth."UserRole" ur
        JOIN v116_demo_team t ON t.user_id = ur."userId"
        JOIN campuscore_auth."Role" r ON r."id" = ur."roleId"
        WHERE r."name" <> 'STUDENT'
    ) OR EXISTS (
        SELECT 1 FROM v116_demo_team t WHERE t.member_order <= 3
            AND NOT EXISTS (SELECT 1 FROM campuscore_auth."UserRole" ur
                WHERE ur."userId" = t.user_id AND ur."roleId" = 'role-student')
    ) THEN
        RAISE EXCEPTION 'V116 demo account role conflict';
    END IF;
    -- Adopt path: a pre-provisioned member4 keeps its password and flags, but it
    -- must not carry copied academic history. Members 1-3 are the original
    -- seeded accounts; their history is their own. By this point the identity
    -- checks above have already pinned any member4 profile to
    -- 'student-profile-team-004' owned by 'student-user-4'.
    IF EXISTS (
        SELECT 1 FROM academic."Student" s
        WHERE s."userId" = 'student-user-4'
            AND (EXISTS (SELECT 1 FROM academic."Enrollment" e WHERE e."studentId" = s."id")
                OR EXISTS (SELECT 1 FROM academic."Attendance" a WHERE a."studentId" = s."id")
                OR EXISTS (SELECT 1 FROM academic."StudentGrade" g
                    JOIN academic."Enrollment" ge ON ge."id" = g."enrollmentId"
                    WHERE ge."studentId" = s."id")
                OR EXISTS (SELECT 1 FROM academic."EnrollmentEvent" ev WHERE ev."studentId" = s."id")
                OR EXISTS (SELECT 1 FROM academic."RegistrationSlip" rs WHERE rs."studentId" = s."id")
                OR EXISTS (SELECT 1 FROM academic."CreditLimitApplication" cl WHERE cl."studentId" = s."id")
                OR EXISTS (SELECT 1 FROM academic.conduct_semester_score cs WHERE cs.student_id = s."id")
                OR EXISTS (SELECT 1 FROM academic.conduct_activity ca WHERE ca.student_id = s."id"))
    ) THEN
        RAISE EXCEPTION 'V116 member4 profile carries academic history; the adopt path requires a zero-history account';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM thesis.thesis_group g
        JOIN thesis.thesis_registration_round r ON r.id = g.round_id
        WHERE g.id = 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid
            AND g.round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid
            AND g.leader_student_id = 'student-profile'
            AND g.status IN ('DRAFT', 'SUBMITTED')
            AND g.approval_status IN ('PENDING', 'REJECTED')
            -- Mirrors ThesisMutationService.requireRoundStatus: PROPOSAL_OPEN is
            -- enough on its own (the registration window has not started yet),
            -- while REGISTRATION_OPEN must additionally sit inside its stored
            -- registration window — an expired open round no longer accepts
            -- membership mutations.
            AND (r.status = 'PROPOSAL_OPEN'
                OR (r.status = 'REGISTRATION_OPEN'
                    AND CURRENT_TIMESTAMP >= r.registration_start
                    AND CURRENT_TIMESTAMP < r.registration_end))
            AND NOT g.legacy_group_size_exception
    ) THEN
        RAISE EXCEPTION 'V116 demo group is not editable; terminal or approved history is preserved';
    END IF;
    IF EXISTS (
        SELECT 1 FROM thesis.thesis_group_member m
        WHERE m.group_id = 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid
            AND (NOT EXISTS (SELECT 1 FROM v116_demo_team t WHERE t.profile_id = m.student_id)
                OR NOT m.active_participation)
    ) OR EXISTS (
        SELECT 1 FROM v116_demo_team t WHERE t.member_order <= 3
            AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member m
                WHERE m.group_id = 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid
                    AND m.student_id = t.profile_id AND m.is_leader = t.is_leader)
    ) OR EXISTS (
        SELECT 1 FROM thesis.thesis_group_member m JOIN v116_demo_team t ON t.profile_id = m.student_id
        WHERE m.round_id = 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid
            AND m.active_participation
            AND m.group_id <> 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid
    ) THEN
        RAISE EXCEPTION 'V116 demo roster conflict; unrelated memberships are preserved';
    END IF;
END;
$$;

CREATE TEMP TABLE v116_roster_before ON COMMIT DROP AS
SELECT count(*)::INTEGER AS member_count,
    count(*) <> 4 OR EXISTS (
        SELECT 1 FROM thesis.thesis_group_member m
        JOIN v116_demo_team t ON t.profile_id = m.student_id
        WHERE m.group_id = 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid
            AND (m.display_name IS DISTINCT FROM t.full_name
                OR m.contact IS DISTINCT FROM t.email
                OR m.member_order <> t.member_order OR m.is_leader <> t.is_leader)
    ) AS needs_update
FROM thesis.thesis_group_member
WHERE group_id = 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid;

INSERT INTO campuscore_auth."User" (
    "id", "email", "password", "firstName", "lastName", "status", "emailVerified",
    "isSuperAdmin", "failedLoginAttempts", "mustChangePassword", "twoFactorEnabled"
)
SELECT t.user_id, t.email, '$2a$10$raV9MB3Qmj1Rbu2Rmo1vNup7VsC2OM3AqmcTcTLzbNyMyI4r2rJBe',
    t.first_name, t.last_name, 'LOCKED', TRUE, FALSE, 0, FALSE, FALSE
FROM v116_demo_team t
WHERE t.member_order = 4 AND NOT EXISTS (SELECT 1 FROM campuscore_auth."User" u WHERE u."id" = t.user_id);

-- Adopt path: a pre-existing member4 account keeps its password, two-factor and
-- emailVerified flags, but its status is deterministically LOCKED — an adopted
-- account must never drift in as ACTIVE. Members 1-3 keep their stored status;
-- DemoAccountGate owns it on every boot.
UPDATE campuscore_auth."User"
SET "status" = 'LOCKED', "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'student-user-4' AND "status" IS DISTINCT FROM 'LOCKED';

UPDATE campuscore_auth."User" u
SET "firstName" = t.first_name, "lastName" = t.last_name, "updatedAt" = CURRENT_TIMESTAMP
FROM v116_demo_team t
WHERE u."id" = t.user_id AND u."email" = t.email
    AND (u."firstName" IS DISTINCT FROM t.first_name OR u."lastName" IS DISTINCT FROM t.last_name);

INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-student-4', t.user_id, 'role-student'
FROM v116_demo_team t
WHERE t.member_order = 4 AND NOT EXISTS (
    SELECT 1 FROM campuscore_auth."UserRole" ur WHERE ur."userId" = t.user_id AND ur."roleId" = 'role-student'
);

INSERT INTO academic."Student" (
    "id", "userId", "studentId", "curriculumId", "year", "status", "admissionDate"
)
SELECT t.profile_id, t.user_id, t.student_number, leader."curriculumId", leader."year", 'ACTIVE', leader."admissionDate"
FROM v116_demo_team t CROSS JOIN academic."Student" leader
WHERE t.member_order = 4 AND leader."id" = 'student-profile'
    AND NOT EXISTS (SELECT 1 FROM academic."Student" s WHERE s."userId" = t.user_id);

UPDATE academic."Student" s
SET "studentId" = t.student_number, "updatedAt" = CURRENT_TIMESTAMP
FROM v116_demo_team t
WHERE s."id" = t.profile_id AND s."userId" = t.user_id
    AND s."studentId" IS DISTINCT FROM t.student_number;

UPDATE thesis.thesis_group_member m
SET display_name = t.full_name, contact = t.email, member_order = t.member_order, is_leader = t.is_leader
FROM v116_demo_team t
WHERE m.group_id = 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid AND m.student_id = t.profile_id
    AND (m.display_name IS DISTINCT FROM t.full_name OR m.contact IS DISTINCT FROM t.email
        OR m.member_order <> t.member_order OR m.is_leader <> t.is_leader);

INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader,
    is_external, active_participation, display_name, contact, created_at
)
SELECT gen_random_uuid(), 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid,
    'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60'::uuid, t.profile_id, t.member_order,
    FALSE, FALSE, TRUE, t.full_name, t.email, CURRENT_TIMESTAMP
FROM v116_demo_team t
WHERE t.member_order = 4 AND NOT EXISTS (
    SELECT 1 FROM thesis.thesis_group_member m
    WHERE m.group_id = 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid AND m.student_id = t.profile_id
);

UPDATE thesis.thesis_group
SET version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE id = 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid
    AND (SELECT needs_update FROM v116_roster_before);

SELECT thesis.validate_group_membership_state('f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'::uuid);

INSERT INTO campuscore_audit."AdminAudit" (
    "id", "actorId", "actorLabel", "action", "entityType", "entityId", "summary",
    "beforeState", "afterState", "createdAt"
)
SELECT 'audit-demo-team-v116', 'system-migration', 'V116 demo team migration', 'ALIGN_DEMO_TEAM',
    'ThesisGroup', 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93',
    'Align four explicitly requested demo identities and roster; retain passwords and academic history',
    jsonb_build_object('memberCount', member_count)::text,
    '{"memberCount":4,"leaderPreserved":true,"existingPasswordsPreserved":true,"academicHistoryPreserved":true}',
    CURRENT_TIMESTAMP
FROM v116_roster_before
ON CONFLICT ("id") DO NOTHING;

-- ON CONFLICT keeps the fixed audit id idempotent on re-runs, but a collision
-- with a DIFFERENT record under that id is tampering, not idempotence — refuse
-- to absorb it silently.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM campuscore_audit."AdminAudit"
        WHERE "id" = 'audit-demo-team-v116'
            AND ("action" IS DISTINCT FROM 'ALIGN_DEMO_TEAM'
                OR "entityId" IS DISTINCT FROM 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93'
                OR "summary" IS DISTINCT FROM
                    'Align four explicitly requested demo identities and roster; retain passwords and academic history')
    ) THEN
        RAISE EXCEPTION 'V116 audit id audit-demo-team-v116 already holds a different record';
    END IF;
END;
$$;
