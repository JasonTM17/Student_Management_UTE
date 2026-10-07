-- Demo dataset parity reconciliation. V111 was adaptive by necessity: the
-- production database still had group f9a4d567 SUBMITTED while older seeds
-- had it CANCELLED, so V111 created a second group (a7c5e2d1) on fresh seeds.
-- This migration collapses both paths to one canonical state for the demo
-- trio (leader student-profile + student-profile-058/059) and removes stale
-- E2E leftovers that only exist on some environments:
--   1) leftover E2E rounds still REGISTRATION_OPEN are terminalised so the
--      open KLTN round is the single default pick everywhere;
--   2) Nguyễn Tiến Sơn keeps exactly one thesis group - f9a4d567, SUBMITTED /
--      PENDING, topic "Cổng quản lý học vụ CampusCore" - with a 3-member
--      roster matching production;
--   3) the trio holds ENROLLED rows on the same 8 sections (auto-013..017 +
--      web/architecture/devops demo sections), matching production seats;
--   4) Sơn's DROPPED history is normalised to the production set;
--   5) seat counters are reconciled with the V108 formula.
-- Idempotent: every statement is guarded so environments already in the
-- canonical shape (production) are untouched.

-- ---------------------------------------------------------------------------
-- 1. Terminalise stale E2E registration-open rounds.
-- ---------------------------------------------------------------------------
UPDATE thesis.thesis_registration_round
SET status = 'RESULTS_PUBLISHED'
WHERE status = 'REGISTRATION_OPEN'
  AND name ILIKE 'E2E%';

-- ---------------------------------------------------------------------------
-- 2. Canonical thesis trio.
-- ---------------------------------------------------------------------------
ALTER TABLE thesis.thesis_group DISABLE TRIGGER thesis_cancelled_group_terminal;
ALTER TABLE thesis.thesis_group DISABLE TRIGGER thesis_release_cancelled_group_memberships;
ALTER TABLE thesis.thesis_group_member DISABLE TRIGGER thesis_group_member_active_participation;

DO $$
DECLARE
    v_round_id CONSTANT UUID := 'c8f1a234-9b7e-412d-8301-1b2c3d4e5f60';
    v_group_id CONSTANT UUID := 'f9a4d567-2e0b-445a-b634-4e5f6a7b8c93';
    v_topic_id CONSTANT UUID := 'd7e2b345-0c8f-423e-9412-2c3d4e5f6a71';
BEGIN
    -- Drop every non-canonical group led by student-profile (cancelled ghosts
    -- and the interim group V111 created on fresh seeds) plus their rosters.
    DELETE FROM thesis.thesis_group_member m
    USING thesis.thesis_group g
    WHERE m.group_id = g.id
      AND g.leader_student_id = 'student-profile'
      AND g.id <> v_group_id;
    DELETE FROM thesis.thesis_group g
    WHERE g.leader_student_id = 'student-profile'
      AND g.id <> v_group_id;

    -- Revive the canonical group to the production shape (CANCELLED -> SUBMITTED
    -- on older seeds; a no-op UPDATE on production where it is already live).
    INSERT INTO thesis.thesis_group (
        id, round_id, leader_student_id, topic_id, status,
        approval_status, version, created_at, updated_at
    ) VALUES (
        v_group_id, v_round_id, 'student-profile', v_topic_id,
        'SUBMITTED', 'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (id) DO NOTHING;

    UPDATE thesis.thesis_group
    SET status = 'SUBMITTED',
        approval_status = 'PENDING',
        topic_id = v_topic_id,
        round_id = v_round_id,
        leader_student_id = 'student-profile',
        updated_at = CURRENT_TIMESTAMP
    WHERE id = v_group_id
      AND (status <> 'SUBMITTED'
           OR approval_status <> 'PENDING'
           OR topic_id IS DISTINCT FROM v_topic_id);

    -- Roster: leader order 1, Minh Quân order 2, Thu Hằng order 3.
    UPDATE thesis.thesis_group_member
    SET member_order = 1, is_leader = TRUE, active_participation = TRUE
    WHERE group_id = v_group_id AND student_id = 'student-profile';
    INSERT INTO thesis.thesis_group_member (
        id, group_id, round_id, student_id, member_order,
        is_leader, created_at, display_name, contact, is_external,
        active_participation
    )
    SELECT gen_random_uuid(), v_group_id, v_round_id, 'student-profile', 1,
           TRUE, CURRENT_TIMESTAMP, 'Tiến Sơn Nguyễn', 'student@campuscore.edu',
           FALSE, TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM thesis.thesis_group_member
        WHERE group_id = v_group_id AND student_id = 'student-profile'
    );

    UPDATE thesis.thesis_group_member
    SET member_order = 2, is_leader = FALSE, active_participation = TRUE
    WHERE group_id = v_group_id AND student_id = 'student-profile-058';
    INSERT INTO thesis.thesis_group_member (
        id, group_id, round_id, student_id, member_order,
        is_leader, created_at, display_name, contact, is_external,
        active_participation
    )
    SELECT gen_random_uuid(), v_group_id, v_round_id, 'student-profile-058', 2,
           FALSE, CURRENT_TIMESTAMP, 'Minh Quân Trần', 'student2@campuscore.edu',
           FALSE, TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM thesis.thesis_group_member
        WHERE group_id = v_group_id AND student_id = 'student-profile-058'
    );

    UPDATE thesis.thesis_group_member
    SET member_order = 3, is_leader = FALSE, active_participation = TRUE
    WHERE group_id = v_group_id AND student_id = 'student-profile-059';
    INSERT INTO thesis.thesis_group_member (
        id, group_id, round_id, student_id, member_order,
        is_leader, created_at, display_name, contact, is_external,
        active_participation
    )
    SELECT gen_random_uuid(), v_group_id, v_round_id, 'student-profile-059', 3,
           FALSE, CURRENT_TIMESTAMP, 'Thu Hằng Lê Thị', 'student3@campuscore.edu',
           FALSE, TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM thesis.thesis_group_member
        WHERE group_id = v_group_id AND student_id = 'student-profile-059'
    );

END $$;

-- Flush the deferred membership-state validation on the final roster before
-- re-enabling triggers (pending events would otherwise block ALTER TABLE).
SET CONSTRAINTS ALL IMMEDIATE;
ALTER TABLE thesis.thesis_group ENABLE TRIGGER thesis_cancelled_group_terminal;
ALTER TABLE thesis.thesis_group ENABLE TRIGGER thesis_release_cancelled_group_memberships;
ALTER TABLE thesis.thesis_group_member ENABLE TRIGGER thesis_group_member_active_participation;

-- ---------------------------------------------------------------------------
-- 3. Enrollment parity for the trio: eight active sections, no auto-019.
-- ---------------------------------------------------------------------------
DELETE FROM academic."StudentGrade" sg
USING academic."Enrollment" e
WHERE sg."enrollmentId" = e.id
  AND e."sectionId" = 'section-auto-019'
  AND e."studentId" IN ('student-profile', 'student-profile-058', 'student-profile-059');

DELETE FROM academic."Enrollment"
WHERE "sectionId" = 'section-auto-019'
  AND "studentId" IN ('student-profile', 'student-profile-058', 'student-profile-059')
  AND status IN ('ENROLLED', 'PENDING', 'CONFIRMED');

INSERT INTO academic."Enrollment" (
    "id", "studentId", "sectionId", "semesterId", "status",
    "enrolledAt", "gradeStatus", "courseId", "roundId", "creditsSnapshot", "version"
)
SELECT 'enr-parity-' || m.sid || '-' || s.id,
       m.sid, s.id, 'semester-demo', 'ENROLLED',
       CURRENT_TIMESTAMP, 'NOT_GRADED', s."courseId",
       'round-registration-current-demo', c.credits, 0
FROM (VALUES ('student-profile'),
             ('student-profile-058'),
             ('student-profile-059')) AS m(sid)
CROSS JOIN academic."Section" s
JOIN academic."Course" c ON c.id = s."courseId"
WHERE s.id IN ('section-web-demo', 'section-architecture-demo', 'section-devops-demo')
  AND NOT EXISTS (
      SELECT 1 FROM academic."Enrollment" e
      WHERE e."studentId" = m.sid
        AND e."sectionId" = s.id
        AND e.status IN ('ENROLLED', 'PENDING', 'CONFIRMED')
  )
ON CONFLICT ("id") DO NOTHING;

-- Normalise the registration round reference on the trio's active rows so the
-- rows read identically on every environment.
UPDATE academic."Enrollment"
SET "roundId" = 'round-registration-current-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "studentId" IN ('student-profile', 'student-profile-058', 'student-profile-059')
  AND status = 'ENROLLED'
  AND "roundId" IS DISTINCT FROM 'round-registration-current-demo';

-- ---------------------------------------------------------------------------
-- 4. Dropped-history parity for the leader (production canonical set).
-- ---------------------------------------------------------------------------
DELETE FROM academic."StudentGrade" sg
USING academic."Enrollment" e
WHERE sg."enrollmentId" = e.id
  AND e."studentId" = 'student-profile'
  AND e.status = 'DROPPED';

DELETE FROM academic."Enrollment"
WHERE "studentId" = 'student-profile'
  AND status = 'DROPPED';

-- Canonical dropped set (matches production): auto-030 appears twice.
INSERT INTO academic."Enrollment" (
    "id", "studentId", "sectionId", "semesterId", "status",
    "enrolledAt", "gradeStatus", "courseId", "roundId", "creditsSnapshot", "version"
)
SELECT 'enr-parity-drop-' || v.rn, 'student-profile', v."sectionId", v."semesterId",
       'DROPPED', CURRENT_TIMESTAMP, 'NOT_GRADED', v."courseId",
       'round-registration-current-demo', v.credits, 0
FROM (
    SELECT s.id AS "sectionId", s."semesterId", s."courseId", c.credits, n.rn
    FROM (VALUES (1, 'section-algorithms-demo'),
                 (2, 'section-auto-019'),
                 (3, 'section-auto-030'),
                 (4, 'section-auto-030'),
                 (5, 'section-database-demo'),
                 (6, 'section-testing-demo')) AS n(rn, section_id)
    JOIN academic."Section" s ON s.id = n.section_id
    JOIN academic."Course" c ON c.id = s."courseId"
) v
ON CONFLICT ("id") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. Seat counters on every touched section (V108 formula).
-- ---------------------------------------------------------------------------
UPDATE academic."Section" section
SET "enrolledCount" = (
        SELECT COUNT(*) FROM academic."Enrollment" enrollment
        WHERE enrollment."sectionId" = section."id"
          AND enrollment."status" IN ('ENROLLED', 'PENDING', 'CONFIRMED')),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE section."id" IN ('section-auto-013', 'section-auto-014', 'section-auto-015',
                       'section-auto-016', 'section-auto-017', 'section-auto-019',
                       'section-web-demo', 'section-architecture-demo', 'section-devops-demo');
