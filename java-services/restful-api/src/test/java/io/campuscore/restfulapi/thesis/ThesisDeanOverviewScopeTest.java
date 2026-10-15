package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Faculty-head observation contract: a TRUONG_KHOA account sees exactly the
 * thesis groups of its own faculty — by topic department, or by member
 * curriculum while a group still has no topic. Other roles, foreign-faculty
 * groups, and a dean whose lecturer profile is missing must all be refused.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:thesis_dean_overview;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisDeanOverviewScopeTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'deanscope-member-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'deanscope-member-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'deanscope-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"UserRole\" WHERE \"userId\" LIKE 'deanscope-%'"
                + " OR \"userId\" LIKE 'thesis-fixture-lecturer-deanscope-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'deanscope-%'"
                + " OR \"id\" LIKE 'thesis-fixture-lecturer-deanscope-%'");
        jdbc.update("DELETE FROM academic.\"Curriculum\" WHERE \"id\" LIKE 'deanscope-%'");
        jdbc.update("DELETE FROM academic.\"Department\" WHERE \"id\" LIKE 'deanscope-%'");
        jdbc.update("DELETE FROM academic.\"Faculty\" WHERE \"id\" LIKE 'deanscope-%'");
    }

    @Test
    void deanSeesOwnFacultyGroupsWithRosterAndSupervisors() throws Exception {
        UUID roundId = insertRound("Dean Scope Round", "REGISTRATION_OPEN");
        seedForeignDepartment();
        String deanUser = ensureDean("deanscope-dean-lecturer");

        UUID ownTopic = insertTopic(roundId, "department-demo", "Own faculty topic");
        UUID foreignTopic = insertTopic(roundId, "deanscope-department-foreign", "Foreign faculty topic");
        ThesisAuthorityFixture.lecturer(jdbc, "deanscope-supervisor-1");
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order)"
                        + " VALUES (?, ?, 'deanscope-supervisor-1', 1)",
                UUID.randomUUID(), ownTopic);
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order)"
                        + " VALUES (?, ?, 'deanscope-supervisor-1', 1)",
                UUID.randomUUID(), foreignTopic);

        UUID ownGroup = insertGroup(roundId, ownTopic, "SUBMITTED", "PENDING");
        insertMember(ownGroup, roundId, ensureStudent("deanscope-member-own", "curriculum-demo"), 1, true);
        UUID foreignGroup = insertGroup(roundId, foreignTopic, "SUBMITTED", "PENDING");
        insertMember(foreignGroup, roundId, ensureStudent("deanscope-member-foreign", "deanscope-curriculum-foreign"), 1, true);
        UUID unassignedOwn = insertGroup(roundId, null, "DRAFT", "PENDING");
        insertMember(unassignedOwn, roundId, ensureStudent("deanscope-member-own2", "curriculum-demo"), 1, true);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader, display_name, contact, is_external) "
                        + "VALUES (?, ?, ?, 'deanscope-external-holder', 2, FALSE, 'External Collaborator', 'ext.collab@external.example', TRUE)",
                UUID.randomUUID(), unassignedOwn, roundId);
        UUID unassignedForeign = insertGroup(roundId, null, "DRAFT", "PENDING");
        insertMember(unassignedForeign, roundId, ensureStudent("deanscope-member-foreign2", "deanscope-curriculum-foreign"), 1, true);

        mvc.perform(get("/api/v1/thesis/dean/overview")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.role(jdbc, deanUser, "TRUONG_KHOA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faculty.facultyId").value("faculty-demo"))
                .andExpect(jsonPath("$.faculty.departmentId").value("department-demo"))
                .andExpect(jsonPath("$.stats.totalGroups").value(2))
                .andExpect(jsonPath("$.stats.groupsWithoutTopic").value(1))
                .andExpect(jsonPath("$.stats.groupsPendingApproval").value(2))
                .andExpect(jsonPath("$.stats.supervisorCount").value(1))
                .andExpect(jsonPath("$.supervisors[0].lecturerId").value("deanscope-supervisor-1"))
                .andExpect(jsonPath("$.supervisors[0].supervisedGroups").value(1))
                .andExpect(jsonPath("$.supervisors[0].supervisedTopics").value(1))
                // The foreign-faculty topic and foreign-member group are absent, not redacted.
                .andExpect(jsonPath("$.groups[?(@.id == '%s')]".formatted(foreignGroup)).isEmpty())
                .andExpect(jsonPath("$.groups[?(@.id == '%s')]".formatted(unassignedForeign)).isEmpty())
                .andExpect(jsonPath("$.groups[?(@.id == '%s')].topicTitle"
                        .formatted(ownGroup)).value(org.hamcrest.Matchers.hasItem("Own faculty topic")))
                .andExpect(jsonPath("$.groups[?(@.id == '%s')].supervisors[0].displayName"
                        .formatted(ownGroup)).value(org.hamcrest.Matchers.hasItem("Fixture Thesis")))
                .andExpect(jsonPath("$.groups[?(@.id == '%s')].members[0].isLeader"
                        .formatted(ownGroup)).value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.groups[?(@.id == '%s')].members[*].isExternal"
                        .formatted(unassignedOwn)).value(org.hamcrest.Matchers.hasItem(true)))
                // No member payload may carry a contact channel — internal
                // emails and external contacts alike stay out of the response.
                .andExpect(jsonPath("$.groups[*].members[*].contact")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void nonDeanRolesAndDeanWithoutLecturerProfileAreRefused() throws Exception {
        UUID roundId = insertRound("Dean Refusal Round", "REGISTRATION_OPEN");

        mvc.perform(get("/api/v1/thesis/dean/overview")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.role(jdbc, "deanscope-plain-lecturer", "LECTURER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/thesis/dean/overview")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.role(jdbc, "deanscope-plain-admin", "ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/thesis/dean/overview")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.role(jdbc, "deanscope-student", "STUDENT")))
                .andExpect(status().isForbidden());

        // TRUONG_KHOA with no bound lecturer profile must fail closed.
        mvc.perform(get("/api/v1/thesis/dean/overview")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.role(jdbc, "deanscope-profileless-dean", "TRUONG_KHOA")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FACULTY_SCOPE_UNAVAILABLE"));

        // Unknown round is a 404 even for a fully provisioned dean.
        ensureDean("deanscope-dean-404");
        mvc.perform(get("/api/v1/thesis/dean/overview")
                        .queryParam("roundId", UUID.randomUUID().toString())
                        .with(ThesisAuthorityFixture.role(jdbc, "thesis-fixture-lecturer-deanscope-dean-404", "TRUONG_KHOA")))
                .andExpect(status().isNotFound());
    }

    @Test
    void scopeResolutionFailsClosedBeforeTheRoundLookup() throws Exception {
        UUID roundId = insertRound("Dean Fail Closed Round", "REGISTRATION_OPEN");

        // A dean whose faculty row is deactivated has no valid scope.
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\", \"name\", \"isActive\") "
                + "VALUES ('deanscope-faculty-inactive', 'Retired Faculty', FALSE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"code\", \"facultyId\", \"isActive\") "
                + "VALUES ('deanscope-dept-inactive', 'Retired Department', 'RET', 'deanscope-faculty-inactive', TRUE)");
        ThesisAuthorityFixture.role(jdbc, "deanscope-dean-inactive-faculty", "TRUONG_KHOA");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                + "VALUES ('deanscope-lecturer-inactive', 'deanscope-dean-inactive-faculty',"
                + " 'deanscope-dept-inactive', 'GV-INACT', TRUE)");
        mvc.perform(get("/api/v1/thesis/dean/overview")
                        .queryParam("roundId", roundId.toString())
                        .with(deanJwtWithLecturerClaim(
                                jdbc, "deanscope-dean-inactive-faculty", "TRUONG_KHOA", "deanscope-lecturer-inactive")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FACULTY_SCOPE_UNAVAILABLE"));

        // A lecturerId claim that names another subject's profile is refused
        // even earlier than the scope check: the account-state filter compares
        // every claim with the live record and answers SESSION_INVALID.
        seedForeignDepartment();
        ThesisAuthorityFixture.lecturer(jdbc, "deanscope-lecturer-insider");
        ThesisAuthorityFixture.role(jdbc, "deanscope-dean-foreign", "TRUONG_KHOA");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                + "VALUES ('deanscope-lecturer-foreign', 'deanscope-dean-foreign',"
                + " 'deanscope-department-foreign', 'GV-FRGN', TRUE)");
        mvc.perform(get("/api/v1/thesis/dean/overview")
                        .queryParam("roundId", roundId.toString())
                        .with(deanJwtWithLecturerClaim(
                                jdbc, "deanscope-dean-foreign", "TRUONG_KHOA", "deanscope-lecturer-insider")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));

        mvc.perform(get("/api/v1/thesis/dean/overview")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.role(jdbc, "deanscope-super", "SUPER_ADMIN")))
                .andExpect(status().isForbidden());

        // Fail closed on an unresolvable scope even when the round does not
        // exist — the scope check runs before the round lookup.
        mvc.perform(get("/api/v1/thesis/dean/overview")
                        .queryParam("roundId", UUID.randomUUID().toString())
                        .with(ThesisAuthorityFixture.role(jdbc, "deanscope-profileless-unknown-round", "TRUONG_KHOA")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FACULTY_SCOPE_UNAVAILABLE"));
    }

    /**
     * Mints the dean-role JWT for a real account but overrides the lecturerId
     * claim, so the profile-vs-subject binding check is exercised exactly.
     */
    private RequestPostProcessor deanJwtWithLecturerClaim(
            JdbcTemplate jdbc, String subject, String role, String claimedLecturerId) {
        ThesisAuthorityFixture.role(jdbc, subject, role);
        var state = new io.campuscore.restfulapi.auth.repository.AuthUserRepository(
                new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc))
                .findAccountAuthorization(subject).orElseThrow();
        String email = jdbc.queryForObject(
                "SELECT \"email\" FROM campuscore_auth.\"User\" WHERE \"id\"=?", String.class, subject);
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> {
                    token.subject(subject).claim("email", email)
                            .claim("roles", state.roles())
                            .claim("permissions", state.permissions())
                            .claim("lecturerId", claimedLecturerId);
                    if (state.studentId() != null) token.claim("studentId", state.studentId());
                    if (state.studentYear() != null) {
                        token.claim("student", java.util.Map.of("year", state.studentYear()));
                    }
                })
                .authorities(state.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toArray(org.springframework.security.core.GrantedAuthority[]::new));
    }

    // ---------- fixtures ----------

    private UUID insertRound(String name, String status) {
        UUID roundId = UUID.randomUUID();
        Instant windowStart = Instant.now().minusSeconds(3_600);
        Instant windowEnd = Instant.now().plusSeconds(3_600);
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?)",
                roundId, name,
                Timestamp.from(windowStart.minusSeconds(7_200)), Timestamp.from(windowStart.minusSeconds(3_600)),
                Timestamp.from(windowStart), Timestamp.from(windowEnd),
                status);
        return roundId;
    }

    private void seedForeignDepartment() {
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\", \"name\", \"isActive\") "
                + "VALUES ('deanscope-faculty-foreign', 'Foreign Faculty', TRUE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"code\", \"facultyId\", \"isActive\") "
                + "VALUES ('deanscope-department-foreign', 'Foreign Department', 'FRG', 'deanscope-faculty-foreign', TRUE)");
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\", \"name\", \"departmentId\") "
                + "VALUES ('deanscope-curriculum-foreign', 'Foreign Curriculum', 'deanscope-department-foreign')");
        // curriculum-demo is referenced by the auth student fixture but the
        // academic table is absent in the H2 mirror; provide a minimal row.
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\", \"name\", \"departmentId\") "
                + "SELECT 'curriculum-demo', 'Software Engineering', 'department-demo' "
                + "WHERE NOT EXISTS (SELECT 1 FROM academic.\"Curriculum\" WHERE \"id\" = 'curriculum-demo')");
    }

    private UUID insertTopic(UUID roundId, String departmentId, String title) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, ?, ?, 'dean scope fixture topic', 3, 'PUBLISHED', ?)",
                topicId, roundId, departmentId, title, UUID.randomUUID());
        return topicId;
    }

    private UUID insertGroup(UUID roundId, UUID topicId, String status, String approvalStatus) {
        UUID groupId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status, created_at) "
                        + "VALUES (?, ?, 'deanscope-leader', ?, ?, ?, CURRENT_TIMESTAMP)",
                groupId, roundId, topicId, status, approvalStatus);
        return groupId;
    }

    private void insertMember(UUID groupId, UUID roundId, String studentId, int order, boolean leader) {
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), groupId, roundId, studentId, order, leader);
    }

    private String ensureStudent(String profileId, String curriculumId) {
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Member', 'DeanScope', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                profileId, profileId + "@fixture.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, ?, 2, CURRENT_TIMESTAMP)",
                profileId, profileId, "DEANSCOPE-" + profileId.substring(profileId.length() - 6), curriculumId);
        return profileId;
    }

    /**
     * A dean is a lecturer profile in the faculty plus the TRUONG_KHOA role —
     * the same shape the account-repair contract produces. Returns the bound
     * user id so the caller can mint the role-augmented JWT.
     */
    private String ensureDean(String lecturerId) {
        ThesisAuthorityFixture.lecturer(jdbc, lecturerId);
        return "thesis-fixture-lecturer-" + lecturerId;
    }
}
