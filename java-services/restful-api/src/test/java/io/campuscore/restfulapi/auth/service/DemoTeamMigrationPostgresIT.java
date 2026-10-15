package io.campuscore.restfulapi.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import io.campuscore.restfulapi.thesis.repository.ThesisGroupReadRepository;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@EnabledIfEnvironmentVariable(named = "CAMPUSCORE_DEMO_TEAM_PG_ENABLED", matches = "true")
class DemoTeamMigrationPostgresIT {
    private static final String GROUP_ID = "f9a4d567-2e0b-445a-b634-4e5f6a7b8c93";
    private static final String ROUND_ID = "c8f1a234-9b7e-412d-8301-1b2c3d4e5f60";
    private static final String TOPIC_ID = "d7e2b345-0c8f-423e-9412-2c3d4e5f6a71";
    private static final String AUDIT_ID = "audit-demo-team-v116";
    private static final Path V116 =
            Path.of("src/main/resources/db/migration/V116__align_four_member_demo_team.sql");

    private record Member(String userId, String profileId, String email, String studentNumber,
                          String firstName, String lastName, String fullName, boolean leader, int order) { }

    private static final List<Member> TEAM = List.of(
            new Member("student-user", "student-profile", "student@campuscore.edu", "24110054",
                    "Tiến Sơn", "Nguyễn", "Nguyễn Tiến Sơn", true, 1),
            new Member("student-user-2", "student-profile-058", "student2@campuscore.edu", "24110345",
                    "Hồng Thủy", "Huỳnh", "Huỳnh Hồng Thủy", false, 2),
            new Member("student-user-3", "student-profile-059", "student3@campuscore.edu", "24110051",
                    "Ngọc Quyên", "Trần Thị", "Trần Thị Ngọc Quyên", false, 3),
            new Member("student-user-4", "student-profile-team-004", "student4@campuscore.edu", "24162050",
                    "Quốc Huy", "Vũ", "Vũ Quốc Huy", false, 4));

    /** Scenarios expected to migrate successfully; everything else must abort atomically. */
    private static final Set<String> SUCCESS_SCENARIOS =
            Set.of("clean", "preexisting_member4_clean", "proposal_open_expired_window");
    /** A deliberately different bcrypt so the adopt path can prove it does not reseed the password. */
    private static final String ADOPTED_FIXTURE_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @ParameterizedTest
    @ValueSource(strings = {"clean", "email_identity_collision", "student_number_collision",
            "terminal_round", "unrelated_roster", "competing_group", "approved_group",
            "preexisting_member4_history", "preexisting_member4_clean",
            "expired_registration_window", "proposal_open_expired_window", "audit_id_collision"})
    void v116AlignsTheFourMemberDemoTeamAtomically(String scenario) throws Exception {
        String adminUrl = System.getenv("CAMPUSCORE_DEMO_TEAM_PG_ADMIN_URL");
        URI target = URI.create(adminUrl.replaceFirst("^jdbc:", ""));
        assertTrue(List.of("localhost", "127.0.0.1", "::1").contains(target.getHost()));
        assertEquals("/postgres", target.getPath());
        assertNull(target.getUserInfo());
        String user = System.getenv("CAMPUSCORE_DEMO_TEAM_PG_USER");
        String password = System.getenv("CAMPUSCORE_DEMO_TEAM_PG_PASSWORD");
        String database = "demo_team_test_" + scenario + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        assertTrue(database.length() <= 63, "PostgreSQL identifiers must not be truncated");
        try (Connection admin = DriverManager.getConnection(adminUrl, user, password)) {
            execute(admin, "CREATE DATABASE " + database);
        }
        String url = adminUrl.substring(0, adminUrl.lastIndexOf('/') + 1) + database;
        flyway(url, user, password, "115").migrate();
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            assertEquals(database, scalar(connection, "SELECT current_database()"));
            assertBaseline(connection);
            applyFixture(connection, scenario);
            Map<String, String> before = fingerprints(connection);
            if (SUCCESS_SCENARIOS.contains(scenario)) {
                flyway(url, user, password, "116").migrate();
                assertTrue(flyway(url, user, password, "116").validateWithResult().validationSuccessful);
                assertAligned(connection, url, user, password, before);
                assertScriptRerunIsIdempotent(connection);
                flyway(url, user, password, "116").migrate();
                assertAlignedRoster(connection);
                if (scenario.equals("preexisting_member4_clean")) {
                    // Adopt, never reseed: the pre-provisioned password hash
                    // survives the migration byte for byte.
                    assertEquals(ADOPTED_FIXTURE_HASH, scalar(connection,
                            "SELECT \"password\" FROM campuscore_auth.\"User\" WHERE \"id\"='student-user-4'"));
                    assertEquals("LOCKED", scalar(connection,
                            "SELECT \"status\" FROM campuscore_auth.\"User\" WHERE \"id\"='student-user-4'"));
                }
            } else {
                assertThrows(FlywayException.class,
                        () -> flyway(url, user, password, "116").migrate());
                assertEquals(before, fingerprints(connection));
                if (!scenario.startsWith("preexisting_member4")) {
                    assertEquals("0", scalar(connection,
                            "SELECT count(*) FROM campuscore_auth.\"User\" WHERE \"id\"='student-user-4'"));
                    assertEquals("0", scalar(connection,
                            "SELECT count(*) FROM academic.\"Student\" WHERE \"id\"='student-profile-team-004'"));
                } else {
                    // The aborted migration must leave the seeded member4 rows
                    // exactly as the fixture planted them.
                    assertEquals("1", scalar(connection,
                            "SELECT count(*) FROM campuscore_auth.\"User\" WHERE \"id\"='student-user-4'"));
                    assertEquals("1", scalar(connection,
                            "SELECT count(*) FROM academic.\"Student\" WHERE \"id\"='student-profile-team-004'"));
                    if (scenario.equals("preexisting_member4_history")) {
                        assertEquals("1", scalar(connection,
                                "SELECT count(*) FROM academic.\"Enrollment\""
                                        + " WHERE \"studentId\"='student-profile-team-004'"));
                    }
                }
                assertEquals(scenario.equals("audit_id_collision") ? "1" : "0", scalar(connection,
                        "SELECT count(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"id\"='" + AUDIT_ID + "'"));
                if (scenario.equals("audit_id_collision")) {
                    assertEquals("FIXTURE_PREEXISTING_ACTION", scalar(connection,
                            "SELECT \"action\" FROM campuscore_audit.\"AdminAudit\" WHERE \"id\"='" + AUDIT_ID + "'"));
                }
                assertEquals("0", scalar(connection,
                        "SELECT count(*) FROM thesis.flyway_schema_history WHERE \"version\"='116'"));
            }
        }
    }

    private static void assertBaseline(Connection connection) throws Exception {
        assertEquals("SUBMITTED", scalar(connection,
                "SELECT status FROM thesis.thesis_group WHERE id='" + GROUP_ID + "'"));
        assertEquals("PENDING", scalar(connection,
                "SELECT approval_status FROM thesis.thesis_group WHERE id='" + GROUP_ID + "'"));
        assertEquals("false", scalar(connection,
                "SELECT legacy_group_size_exception::text FROM thesis.thesis_group WHERE id='" + GROUP_ID + "'"));
        assertEquals("REGISTRATION_OPEN", scalar(connection,
                "SELECT status FROM thesis.thesis_registration_round WHERE id='" + ROUND_ID + "'"));
        assertEquals("3", scalar(connection,
                "SELECT count(*) FROM thesis.thesis_group_member WHERE group_id='" + GROUP_ID + "'"));
        assertEquals("1", scalar(connection,
                "SELECT count(*) FROM thesis.thesis_group_member WHERE group_id='" + GROUP_ID
                        + "' AND student_id='student-profile' AND is_leader AND active_participation"));
        assertEquals("0", scalar(connection,
                "SELECT count(*) FROM campuscore_auth.\"User\" WHERE \"id\"='student-user-4'"));
    }

    private static void applyFixture(Connection connection, String scenario) throws Exception {
        switch (scenario) {
            case "email_identity_collision" -> execute(connection,
                    "UPDATE campuscore_auth.\"User\" SET \"email\"='student2-reassigned@campuscore.edu'"
                            + " WHERE \"id\"='student-user-2'");
            case "student_number_collision" -> execute(connection,
                    "UPDATE academic.\"Student\" SET \"studentId\"='24110345' WHERE \"id\"='" + unrelatedProfile(connection) + "'");
            case "terminal_round" -> execute(connection,
                    "UPDATE thesis.thesis_registration_round SET status='CLOSED', updated_at=CURRENT_TIMESTAMP"
                            + " WHERE id='" + ROUND_ID + "'");
            case "unrelated_roster" -> execute(connection,
                    "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order,"
                            + " is_leader, is_external, active_participation, display_name, contact, created_at)"
                            + " VALUES (gen_random_uuid(), '" + GROUP_ID + "', '" + ROUND_ID + "', '"
                            + unrelatedProfile(connection) + "', 5, FALSE, FALSE, FALSE,"
                            + " 'Fixture Extra', 'extra@fixture.local', CURRENT_TIMESTAMP)");
            case "competing_group" -> {
                String competingGroup = UUID.randomUUID().toString();
                String leader = unrelatedProfile(connection);
                execute(connection,
                        "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status,"
                                + " approval_status, version, created_at, updated_at)"
                                + " VALUES ('" + competingGroup + "', '" + ROUND_ID + "', '" + leader + "', '"
                                + TOPIC_ID + "', 'SUBMITTED', 'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                execute(connection,
                        "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order,"
                                + " is_leader, is_external, active_participation, created_at)"
                                + " VALUES (gen_random_uuid(), '" + competingGroup + "', '" + ROUND_ID + "', '"
                                + leader + "', 1, TRUE, FALSE, TRUE, CURRENT_TIMESTAMP)");
                execute(connection,
                        "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order,"
                                + " is_leader, is_external, active_participation, created_at)"
                                + " VALUES (gen_random_uuid(), '" + competingGroup + "', '" + ROUND_ID + "',"
                                + " 'student-profile-team-004', 2, FALSE, FALSE, TRUE, CURRENT_TIMESTAMP)");
            }
            case "approved_group" -> execute(connection,
                    "UPDATE thesis.thesis_group SET approval_status='APPROVED', updated_at=CURRENT_TIMESTAMP"
                            + " WHERE id='" + GROUP_ID + "'");
            case "preexisting_member4_history" -> {
                seedMember4Account(connection);
                execute(connection,
                        "INSERT INTO academic.\"Enrollment\" (\"id\",\"studentId\",\"sectionId\",\"semesterId\",\"status\","
                                + " \"enrolledAt\",\"gradeStatus\",\"courseId\",\"roundId\",\"creditsSnapshot\",\"version\")"
                                + " SELECT 'enr-m4-history-fixture', 'student-profile-team-004', src.\"sectionId\","
                                + " src.\"semesterId\", 'ENROLLED', CURRENT_TIMESTAMP, 'NOT_GRADED', src.\"courseId\","
                                + " src.\"roundId\", src.\"creditsSnapshot\", 0 FROM academic.\"Enrollment\" src"
                                + " WHERE src.\"studentId\"='student-profile' AND src.\"status\"='ENROLLED' LIMIT 1");
            }
            case "preexisting_member4_clean" -> seedMember4Account(connection);
            case "expired_registration_window" -> execute(connection,
                    "UPDATE thesis.thesis_registration_round"
                            + " SET registration_end = CURRENT_TIMESTAMP - INTERVAL '1 day',"
                            + " updated_at = CURRENT_TIMESTAMP WHERE id='" + ROUND_ID + "'");
            case "proposal_open_expired_window" -> execute(connection,
                    "UPDATE thesis.thesis_registration_round"
                            + " SET status = 'PROPOSAL_OPEN',"
                            + " registration_end = CURRENT_TIMESTAMP - INTERVAL '1 day',"
                            + " updated_at = CURRENT_TIMESTAMP WHERE id='" + ROUND_ID + "'");
            case "audit_id_collision" -> execute(connection,
                    "INSERT INTO campuscore_audit.\"AdminAudit\""
                            + " (\"id\",\"actorId\",\"actorLabel\",\"action\",\"entityType\",\"entityId\",\"summary\",\"createdAt\")"
                            + " VALUES ('" + AUDIT_ID + "','fixture','fixture seed','FIXTURE_PREEXISTING_ACTION',"
                            + " 'ThesisGroup','" + GROUP_ID + "','a different record under the fixed audit id',"
                            + " CURRENT_TIMESTAMP)");
            default -> { }
        }
        if (scenario.equals("clean")) {
            String fixtureHash = new BCryptPasswordEncoder().encode("local-fixture-" + UUID.randomUUID());
            try (var statement = connection.prepareStatement(
                    "UPDATE campuscore_auth.\"User\" SET \"password\"=?, \"twoFactorEnabled\"=TRUE,"
                            + " \"emailVerified\"=FALSE, \"failedLoginAttempts\"=2, \"status\"='LOCKED'"
                            + " WHERE \"id\"='student-user-2'")) {
                statement.setString(1, fixtureHash);
                statement.executeUpdate();
            }
        }
    }

    /**
     * Seeds a pre-provisioned member4 exactly as an adoptable account looks: the
     * canonical ids/email/names and STUDENT role, but a password hash the
     * migration did not publish — so a successful run can prove the adopt path
     * locks the account without reseeding its credentials.
     */
    private static void seedMember4Account(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement(
                "INSERT INTO campuscore_auth.\"User\" (\"id\",\"email\",\"password\",\"firstName\",\"lastName\","
                        + " \"status\",\"emailVerified\",\"isSuperAdmin\",\"failedLoginAttempts\","
                        + " \"mustChangePassword\",\"twoFactorEnabled\")"
                        + " VALUES ('student-user-4','student4@campuscore.edu', ?, 'Quốc Huy','Vũ',"
                        + " 'ACTIVE', TRUE, FALSE, 0, FALSE, FALSE)")) {
            statement.setString(1, ADOPTED_FIXTURE_HASH);
            statement.executeUpdate();
        }
        execute(connection,
                "INSERT INTO campuscore_auth.\"UserRole\" (\"id\",\"userId\",\"roleId\")"
                        + " VALUES ('user-role-student-4','student-user-4','role-student')");
        execute(connection,
                "INSERT INTO academic.\"Student\" (\"id\",\"userId\",\"studentId\",\"curriculumId\",\"year\","
                        + " \"status\",\"admissionDate\")"
                        + " SELECT 'student-profile-team-004','student-user-4','24162050', leader.\"curriculumId\","
                        + " leader.\"year\", 'ACTIVE', leader.\"admissionDate\""
                        + " FROM academic.\"Student\" leader WHERE leader.\"id\"='student-profile'");
    }

    private static String unrelatedProfile(Connection connection) throws Exception {
        String picked = scalar(connection,
                "SELECT s.\"id\" FROM academic.\"Student\" s WHERE s.\"id\" NOT IN"
                        + " ('student-profile','student-profile-058','student-profile-059','student-profile-team-004')"
                        + " AND s.\"id\" NOT IN (SELECT m.student_id FROM thesis.thesis_group_member m"
                        + " WHERE m.round_id='" + ROUND_ID + "') ORDER BY s.\"id\" LIMIT 1");
        assertNotNull(picked, "the seed must provide an unrelated student profile for the fixture");
        return picked;
    }

    private static Map<String, String> fingerprints(Connection connection) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        List<Member> existing = TEAM.subList(0, 3);
        String userIds = TEAM.stream().map(m -> "'" + m.userId() + "'").collect(Collectors.joining(","));
        String existingUserIds = existing.stream().map(m -> "'" + m.userId() + "'").collect(Collectors.joining(","));
        String profileIds = TEAM.stream().map(m -> "'" + m.profileId() + "'").collect(Collectors.joining(","));
        String existingProfileIds = existing.stream().map(m -> "'" + m.profileId() + "'").collect(Collectors.joining(","));
        map.put("enrollment", fingerprint(connection, "SELECT to_jsonb(r)::text FROM academic.\"Enrollment\" r"));
        map.put("attendance", fingerprint(connection, "SELECT to_jsonb(r)::text FROM academic.\"Attendance\" r"));
        map.put("section", fingerprint(connection, "SELECT to_jsonb(r)::text FROM academic.\"Section\" r"));
        map.put("studentGrade", fingerprint(connection, "SELECT to_jsonb(r)::text FROM academic.\"StudentGrade\" r"));
        map.put("unrelatedUsers", fingerprint(connection,
                "SELECT to_jsonb(r)::text FROM campuscore_auth.\"User\" r WHERE r.\"id\" NOT IN (" + userIds + ")"));
        map.put("teamUsersStable", fingerprint(connection,
                "SELECT to_jsonb(x)::text FROM (SELECT \"id\",\"email\",\"password\",\"phone\",\"gender\",\"status\","
                        + " \"emailVerified\",\"isSuperAdmin\",\"failedLoginAttempts\",\"mustChangePassword\","
                        + " \"twoFactorEnabled\" FROM campuscore_auth.\"User\" WHERE \"id\" IN (" + existingUserIds + ")) x"));
        map.put("unrelatedStudents", fingerprint(connection,
                "SELECT to_jsonb(r)::text FROM academic.\"Student\" r WHERE r.\"id\" NOT IN (" + profileIds + ")"));
        map.put("teamStudentsStable", fingerprint(connection,
                "SELECT to_jsonb(x)::text FROM (SELECT \"id\",\"userId\",\"curriculumId\",\"year\",\"status\","
                        + " \"admissionDate\" FROM academic.\"Student\" WHERE \"id\" IN (" + existingProfileIds + ")) x"));
        map.put("unrelatedUserRoles", fingerprint(connection,
                "SELECT to_jsonb(r)::text FROM campuscore_auth.\"UserRole\" r WHERE r.\"userId\" NOT IN (" + userIds + ")"));
        map.put("teamUserRoles", fingerprint(connection,
                "SELECT to_jsonb(r)::text FROM campuscore_auth.\"UserRole\" r WHERE r.\"userId\" IN (" + existingUserIds + ")"));
        map.put("unrelatedGroups", fingerprint(connection,
                "SELECT to_jsonb(r)::text FROM thesis.thesis_group r WHERE r.id <> '" + GROUP_ID + "'"));
        map.put("unrelatedMembers", fingerprint(connection,
                "SELECT to_jsonb(r)::text FROM thesis.thesis_group_member r WHERE r.group_id <> '" + GROUP_ID + "'"));
        map.put("otherAudit", fingerprint(connection,
                "SELECT to_jsonb(r)::text FROM campuscore_audit.\"AdminAudit\" r WHERE r.\"id\" <> '" + AUDIT_ID + "'"));
        map.put("canonicalGroupStable", fingerprint(connection,
                "SELECT to_jsonb(x)::text FROM (SELECT id, round_id, leader_student_id, topic_id, status,"
                        + " approval_status FROM thesis.thesis_group WHERE id='" + GROUP_ID + "') x"));
        map.put("teamUsersFull", fingerprint(connection,
                "SELECT to_jsonb(r)::text FROM campuscore_auth.\"User\" r WHERE r.\"id\" IN (" + existingUserIds + ")"));
        map.put("teamStudentsFull", fingerprint(connection,
                "SELECT to_jsonb(r)::text FROM academic.\"Student\" r WHERE r.\"id\" IN (" + existingProfileIds + ")"));
        map.put("canonicalGroupFull", fingerprint(connection,
                "SELECT to_jsonb(r)::text FROM thesis.thesis_group r WHERE r.id='" + GROUP_ID + "'"));
        map.put("canonicalMembersFull", fingerprint(connection,
                "SELECT to_jsonb(r)::text FROM thesis.thesis_group_member r WHERE r.group_id='" + GROUP_ID + "'"));
        return map;
    }

    private static void assertAligned(Connection connection, String url, String user, String password,
                                      Map<String, String> before) throws Exception {
        Map<String, String> after = fingerprints(connection);
        assertEquals(before.get("enrollment"), after.get("enrollment"));
        assertEquals(before.get("attendance"), after.get("attendance"));
        assertEquals(before.get("section"), after.get("section"));
        assertEquals(before.get("studentGrade"), after.get("studentGrade"));
        assertEquals(before.get("unrelatedUsers"), after.get("unrelatedUsers"));
        assertEquals(before.get("teamUsersStable"), after.get("teamUsersStable"));
        assertEquals(before.get("unrelatedStudents"), after.get("unrelatedStudents"));
        assertEquals(before.get("teamStudentsStable"), after.get("teamStudentsStable"));
        assertEquals(before.get("unrelatedUserRoles"), after.get("unrelatedUserRoles"));
        assertEquals(before.get("teamUserRoles"), after.get("teamUserRoles"));
        assertEquals(before.get("unrelatedGroups"), after.get("unrelatedGroups"));
        assertEquals(before.get("unrelatedMembers"), after.get("unrelatedMembers"));
        assertEquals(before.get("otherAudit"), after.get("otherAudit"));
        assertEquals(before.get("canonicalGroupStable"), after.get("canonicalGroupStable"));

        for (Member m : TEAM) {
            assertEquals(m.email(), scalar(connection,
                    "SELECT \"email\" FROM campuscore_auth.\"User\" WHERE \"id\"='" + m.userId() + "'"));
            assertEquals(m.firstName(), scalar(connection,
                    "SELECT \"firstName\" FROM campuscore_auth.\"User\" WHERE \"id\"='" + m.userId() + "'"));
            assertEquals(m.lastName(), scalar(connection,
                    "SELECT \"lastName\" FROM campuscore_auth.\"User\" WHERE \"id\"='" + m.userId() + "'"));
            assertEquals(m.studentNumber(), scalar(connection,
                    "SELECT \"studentId\" FROM academic.\"Student\" WHERE \"id\"='" + m.profileId()
                            + "' AND \"userId\"='" + m.userId() + "'"));
        }
        assertEquals("LOCKED", scalar(connection,
                "SELECT \"status\" FROM campuscore_auth.\"User\" WHERE \"id\"='student-user-4'"));
        assertEquals("1", scalar(connection,
                "SELECT count(*) FROM campuscore_auth.\"UserRole\" WHERE \"userId\"='student-user-4'"
                        + " AND \"roleId\"='role-student'"));
        assertEquals("1", scalar(connection,
                "SELECT count(*) FROM campuscore_auth.\"UserRole\" r WHERE r.\"userId\"='student-user-4'"));
        assertEquals("0", scalar(connection,
                "SELECT count(*) FROM academic.\"Enrollment\" WHERE \"studentId\"='student-profile-team-004'"));
        assertEquals("0", scalar(connection,
                "SELECT count(*) FROM academic.\"StudentGrade\" g JOIN academic.\"Enrollment\" e"
                        + " ON e.\"id\"=g.\"enrollmentId\" WHERE e.\"studentId\"='student-profile-team-004'"));
        assertEquals("1", scalar(connection,
                "SELECT count(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"id\"='" + AUDIT_ID + "'"));

        assertAlignedRoster(connection);

        var jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(url, user, password));
        GroupResponse group = new ThesisGroupReadRepository(jdbc).findById(UUID.fromString(GROUP_ID));
        assertNotNull(group);
        assertEquals(4, group.members().size());
        for (Member m : TEAM) {
            var detail = group.members().stream().filter(d -> m.profileId().equals(d.studentId())).findFirst()
                    .orElseThrow(() -> new AssertionError("member " + m.profileId() + " missing from API roster"));
            assertEquals(m.studentNumber(), detail.studentNumber());
            assertEquals(m.fullName(), detail.displayName());
            assertEquals(m.leader(), detail.isLeader());
            assertEquals(m.order(), detail.memberOrder());
        }

        String nonDemoEmails = DemoAccountGate.DEMO_EMAILS.stream()
                .map(email -> "'" + email + "'").collect(Collectors.joining(","));
        String realUserStatuses = fingerprint(connection,
                "SELECT to_jsonb(x)::text FROM (SELECT \"id\",\"status\" FROM campuscore_auth.\"User\""
                        + " WHERE \"email\" NOT IN (" + nonDemoEmails + ")) x");
        new DemoAccountGate(jdbc, true, "STUDENT").run(null);
        for (Member m : TEAM) {
            assertEquals("ACTIVE", scalar(connection,
                    "SELECT \"status\" FROM campuscore_auth.\"User\" WHERE \"id\"='" + m.userId() + "'"));
        }
        assertEquals(realUserStatuses, fingerprint(connection,
                "SELECT to_jsonb(x)::text FROM (SELECT \"id\",\"status\" FROM campuscore_auth.\"User\""
                        + " WHERE \"email\" NOT IN (" + nonDemoEmails + ")) x"));
        new DemoAccountGate(jdbc, false, "").run(null);
        for (Member m : TEAM) {
            assertEquals("LOCKED", scalar(connection,
                    "SELECT \"status\" FROM campuscore_auth.\"User\" WHERE \"id\"='" + m.userId() + "'"));
        }
        assertEquals(realUserStatuses, fingerprint(connection,
                "SELECT to_jsonb(x)::text FROM (SELECT \"id\",\"status\" FROM campuscore_auth.\"User\""
                        + " WHERE \"email\" NOT IN (" + nonDemoEmails + ")) x"));
    }

    private static void assertAlignedRoster(Connection connection) throws Exception {
        assertEquals("4", scalar(connection,
                "SELECT count(*) FROM thesis.thesis_group_member WHERE group_id='" + GROUP_ID + "'"));
        for (Member m : TEAM) {
            assertEquals("1", scalar(connection,
                    "SELECT count(*) FROM thesis.thesis_group_member WHERE group_id='" + GROUP_ID
                            + "' AND student_id='" + m.profileId() + "' AND member_order=" + m.order()
                            + " AND is_leader=" + m.leader() + " AND active_participation"
                            + " AND display_name='" + m.fullName() + "' AND contact='" + m.email() + "'"));
        }
        assertEquals("1", scalar(connection,
                "SELECT count(*) FROM thesis.thesis_group_member WHERE group_id='" + GROUP_ID + "' AND is_leader"));
        assertEquals("1", scalar(connection,
                "SELECT count(*) FROM campuscore_auth.\"User\" WHERE \"id\"='student-user-4'"));
    }

    private static void assertScriptRerunIsIdempotent(Connection connection) throws Exception {
        long versionBefore = Long.parseLong(scalar(connection,
                "SELECT version FROM thesis.thesis_group WHERE id='" + GROUP_ID + "'"));
        Map<String, String> beforeRerun = fingerprints(connection);
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
            statement.execute(Files.readString(V116));
        }
        connection.commit();
        connection.setAutoCommit(true);
        assertEquals(beforeRerun, fingerprints(connection));
        assertAlignedRoster(connection);
        assertEquals(String.valueOf(versionBefore), scalar(connection,
                "SELECT version FROM thesis.thesis_group WHERE id='" + GROUP_ID + "'"));
        assertEquals("1", scalar(connection,
                "SELECT count(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"id\"='" + AUDIT_ID + "'"));
        assertEquals("4", scalar(connection,
                "SELECT count(*) FROM thesis.thesis_group_member WHERE group_id='" + GROUP_ID + "'"));
        assertEquals("1", scalar(connection,
                "SELECT count(*) FROM campuscore_auth.\"UserRole\" WHERE \"userId\"='student-user-4'"));
    }

    private static Flyway flyway(String url, String user, String password, String version) {
        return Flyway.configure().dataSource(url, user, password).schemas("thesis").defaultSchema("thesis")
                .locations("classpath:db/migration").target(version).cleanDisabled(true).load();
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private static String scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    private static String fingerprint(Connection connection, String sql) throws Exception {
        return scalar(connection, "SELECT md5(COALESCE(string_agg(value,E'\\n' ORDER BY value),'')) FROM (" + sql + ") data(value)");
    }
}
