package io.campuscore.restfulapi.thesis;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Governance scope on the shared group-read endpoints: TRUONG_KHOA sees only
 * own-faculty groups and TRUONG_BO_MON only own-department groups — the same
 * derivation as the dean overview — and member contacts never leave the API
 * on those reads. Foreign groups answer 404 rather than leaking existence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:thesis_group_scope;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisGroupFacultyScopeTest {

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
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'gscope-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'gscope-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'gscope-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"UserRole\" WHERE \"userId\" LIKE 'gscope-%'"
                + " OR \"userId\" LIKE 'thesis-fixture-lecturer-gscope-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'thesis-fixture-lecturer-gscope-%'");
        jdbc.update("DELETE FROM academic.\"Curriculum\" WHERE \"id\" LIKE 'gscope-%'");
        jdbc.update("DELETE FROM academic.\"Department\" WHERE \"id\" LIKE 'gscope-%'");
        jdbc.update("DELETE FROM academic.\"Faculty\" WHERE \"id\" LIKE 'gscope-%'");
    }

    @Test
    void deanSeesOnlyOwnFacultyGroupsAndNeverMemberContacts() throws Exception {
        UUID roundId = insertRound("Group Scope Round");
        seedSiblingAndForeignDepartments();
        String deanUser = ensureLecturer("gscope-dean-lecturer", "department-demo");

        UUID ownTopic = insertTopic(roundId, "department-demo", "Own faculty topic");
        UUID foreignTopic = insertTopic(roundId, "gscope-dept-foreign", "Foreign faculty topic");
        UUID siblingTopic = insertTopic(roundId, "gscope-dept-sibling", "Sibling dept same faculty");

        UUID ownGroup = insertGroup(roundId, ownTopic);
        insertMember(ownGroup, roundId, ensureStudent("gscope-member-own", "curriculum-demo"), 1);
        UUID siblingGroup = insertGroup(roundId, siblingTopic);
        insertMember(siblingGroup, roundId, ensureStudent("gscope-member-sib", "gscope-curr-sibling"), 1);
        UUID foreignGroup = insertGroup(roundId, foreignTopic);
        insertMember(foreignGroup, roundId, ensureStudent("gscope-member-frg", "gscope-curr-foreign"), 1);
        UUID unassignedOwn = insertGroup(roundId, null);
        insertMember(unassignedOwn, roundId, ensureStudent("gscope-member-own2", "curriculum-demo"), 1);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader, display_name, contact, is_external) "
                        + "VALUES (?, ?, ?, 'gscope-external-holder', 2, FALSE, 'External Collaborator', 'ext@example.test', TRUE)",
                UUID.randomUUID(), unassignedOwn, roundId);
        UUID unassignedForeign = insertGroup(roundId, null);
        insertMember(unassignedForeign, roundId, ensureStudent("gscope-member-frg2", "gscope-curr-foreign"), 1);

        // Same faculty, different departments: own + sibling visible, foreign gone.
        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.role(jdbc, deanUser, "TRUONG_KHOA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(ownGroup)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(siblingGroup)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(unassignedOwn)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(foreignGroup)).isEmpty())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(unassignedForeign)).isEmpty())
                // Internal emails and declared external contacts are withheld.
                .andExpect(jsonPath("$[*].members[*].contact")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));

        // Detail read mirrors the list scope: own group visible, foreign 404.
        mvc.perform(get("/api/v1/thesis/groups/%s".formatted(ownGroup))
                        .with(ThesisAuthorityFixture.role(jdbc, deanUser, "TRUONG_KHOA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownGroup.toString()))
                .andExpect(jsonPath("$.members[*].contact")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
        mvc.perform(get("/api/v1/thesis/groups/%s".formatted(foreignGroup))
                        .with(ThesisAuthorityFixture.role(jdbc, deanUser, "TRUONG_KHOA")))
                .andExpect(status().isNotFound());
    }

    @Test
    void departmentHeadSeesOnlyOwnDepartment() throws Exception {
        UUID roundId = insertRound("Dept Scope Round");
        seedSiblingAndForeignDepartments();
        String headUser = ensureLecturer("gscope-head-lecturer", "gscope-dept-sibling");

        UUID ownTopic = insertTopic(roundId, "gscope-dept-sibling", "Sibling dept topic");
        UUID otherTopic = insertTopic(roundId, "department-demo", "Other dept topic");
        UUID ownGroup = insertGroup(roundId, ownTopic);
        insertMember(ownGroup, roundId, ensureStudent("gscope-member-dh1", "gscope-curr-sibling"), 1);
        UUID otherGroup = insertGroup(roundId, otherTopic);
        insertMember(otherGroup, roundId, ensureStudent("gscope-member-dh2", "curriculum-demo"), 1);
        UUID unassignedOwn = insertGroup(roundId, null);
        insertMember(unassignedOwn, roundId, ensureStudent("gscope-member-dh3", "gscope-curr-sibling"), 1);

        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.role(jdbc, headUser, "TRUONG_BO_MON")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(ownGroup)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(unassignedOwn)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(otherGroup)).isEmpty())
                .andExpect(jsonPath("$[*].members[*].contact")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));

        mvc.perform(get("/api/v1/thesis/groups/%s".formatted(otherGroup))
                        .with(ThesisAuthorityFixture.role(jdbc, headUser, "TRUONG_BO_MON")))
                .andExpect(status().isNotFound());
    }

    @Test
    void governanceWithoutLecturerProfileFailsClosedToEmpty() throws Exception {
        UUID roundId = insertRound("Fail Closed Round");
        UUID topic = insertTopic(roundId, "department-demo", "Any topic");
        UUID group = insertGroup(roundId, topic);
        insertMember(group, roundId, ensureStudent("gscope-member-x", "curriculum-demo"), 1);

        // TRUONG_KHOA with no bound lecturer profile fails closed — never the
        // unscoped list.
        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.role(jdbc, "gscope-profileless-dean", "TRUONG_KHOA")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/thesis/groups/%s".formatted(group))
                        .with(ThesisAuthorityFixture.role(jdbc, "gscope-profileless-dean", "TRUONG_KHOA")))
                .andExpect(status().isForbidden());

        // Admin still sees the unscoped list.
        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.role(jdbc, "gscope-admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------- fixtures ----------

    private UUID insertRound(String name) {
        UUID roundId = UUID.randomUUID();
        Instant windowStart = Instant.now().minusSeconds(3_600);
        Instant windowEnd = Instant.now().plusSeconds(3_600);
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, 'REGISTRATION_OPEN')",
                roundId, name,
                Timestamp.from(windowStart.minusSeconds(7_200)), Timestamp.from(windowStart.minusSeconds(3_600)),
                Timestamp.from(windowStart), Timestamp.from(windowEnd));
        return roundId;
    }

    private void seedSiblingAndForeignDepartments() {
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"code\", \"facultyId\", \"isActive\") "
                + "VALUES ('gscope-dept-sibling', 'Sibling Department', 'SIB', 'faculty-demo', TRUE)");
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\", \"name\", \"departmentId\") "
                + "VALUES ('gscope-curr-sibling', 'Sibling Curriculum', 'gscope-dept-sibling')");
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\", \"name\", \"isActive\") "
                + "VALUES ('gscope-faculty-foreign', 'Foreign Faculty', TRUE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"code\", \"facultyId\", \"isActive\") "
                + "VALUES ('gscope-dept-foreign', 'Foreign Department', 'FRG', 'gscope-faculty-foreign', TRUE)");
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\", \"name\", \"departmentId\") "
                + "VALUES ('gscope-curr-foreign', 'Foreign Curriculum', 'gscope-dept-foreign')");
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\", \"name\", \"departmentId\") "
                + "SELECT 'curriculum-demo', 'Software Engineering', 'department-demo' "
                + "WHERE NOT EXISTS (SELECT 1 FROM academic.\"Curriculum\" WHERE \"id\" = 'curriculum-demo')");
    }

    private UUID insertTopic(UUID roundId, String departmentId, String title) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, ?, ?, 'scope fixture topic', 3, 'PUBLISHED', ?)",
                topicId, roundId, departmentId, title, UUID.randomUUID());
        return topicId;
    }

    private UUID insertGroup(UUID roundId, UUID topicId) {
        UUID groupId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status, created_at) "
                        + "VALUES (?, ?, 'gscope-leader', ?, 'SUBMITTED', 'PENDING', CURRENT_TIMESTAMP)",
                groupId, roundId, topicId);
        return groupId;
    }

    private void insertMember(UUID groupId, UUID roundId, String studentId, int order) {
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, ?, TRUE)",
                UUID.randomUUID(), groupId, roundId, studentId, order);
    }

    private String ensureStudent(String profileId, String curriculumId) {
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Member', 'GScope', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                profileId, profileId + "@fixture.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, ?, 2, CURRENT_TIMESTAMP)",
                profileId, profileId, "GSCOPE-" + profileId.substring(profileId.length() - 6), curriculumId);
        return profileId;
    }

    /** Lecturer profile in the given department plus the bound user id. */
    private String ensureLecturer(String lecturerId, String departmentId) {
        String userId = "thesis-fixture-lecturer-" + lecturerId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Thesis', 'Fixture', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, lecturerId + "@fixture.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                        + "VALUES (?, ?, ?, ?, TRUE)",
                lecturerId, userId, departmentId, "L-" + lecturerId.substring(lecturerId.length() - 6));
        return userId;
    }
}
