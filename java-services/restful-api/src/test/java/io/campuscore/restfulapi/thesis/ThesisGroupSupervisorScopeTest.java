package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * LEC-P2-1: thesis-group reads used to hand every LECTURER every group,
 * including external members' declared contact details. A lecturer now sees
 * only groups whose topic they supervise — the same rule the report-read
 * path applies.
 *
 * LEC-P2-2: createTopic used to fall back to the actor's User id when the
 * lecturerId claim was blank, seeding thesis_topic_supervisor rows that
 * never join. A LECTURER creator without the claim now gets a 400 and no
 * supervisor row is written.
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
class ThesisGroupSupervisorScopeTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_council_topic");
        jdbc.update("DELETE FROM thesis.thesis_council_member");
        jdbc.update("DELETE FROM thesis.thesis_council");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'scope-member-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'scope-user-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'scope-lecturer-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'scope-lecturer-user-%'");
        jdbc.update("DELETE FROM academic.\"Department\" WHERE \"id\" LIKE 'scope-department-%'");
        jdbc.update("DELETE FROM academic.\"Faculty\" WHERE \"id\" LIKE 'scope-faculty-%'");
    }

    @Test
    void lecturerSeesOnlyTheGroupsWhoseTopicTheySupervise() throws Exception {
        UUID roundId = insertRound("Scope Round", "REGISTRATION_OPEN");
        ensureLecturer("scope-lecturer-own");
        ensureLecturer("scope-lecturer-other");
        UUID ownTopic = insertPublishedTopic(roundId);
        UUID otherTopic = insertPublishedTopic(roundId);
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order)"
                        + " VALUES (?, ?, 'scope-lecturer-own', 1)",
                UUID.randomUUID(), ownTopic);
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order)"
                        + " VALUES (?, ?, 'scope-lecturer-other', 1)",
                UUID.randomUUID(), otherTopic);
        UUID ownGroup = insertGroupWithExternalMember(roundId, ownTopic, "scope-member-1", "ext-own@school.edu");
        UUID otherGroup = insertGroupWithExternalMember(roundId, otherTopic, "scope-member-2", "ext-other@school.edu");

        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", roundId.toString())
                        .with(lecturerJwt("scope-lecturer-own")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ownGroup.toString()))
                // The supervising lecturer still sees the declared contact.
                .andExpect(jsonPath("$[0].members[?(@.isExternal == true)].contact")
                        .value(org.hamcrest.Matchers.hasItem("ext-own@school.edu")));

        // A non-supervising lecturer cannot open the other group at all, so
        // its contact list is unreachable.
        mvc.perform(get("/api/v1/thesis/groups/{id}", otherGroup)
                        .with(lecturerJwt("scope-lecturer-own")))
                .andExpect(status().isNotFound());

        // Admin keeps the full roster view.
        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", roundId.toString())
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '%s')].members[?(@.isExternal == true)].contact"
                        .formatted(otherGroup.toString()),
                        org.hamcrest.Matchers.hasItem("ext-other@school.edu")));

        // Super Admin also keeps the full roster view.
        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", roundId.toString())
                        .with(superAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void topicSubmissionWithoutLecturerIdClaimIsRejectedWithoutSeedingASupervisorRow() throws Exception {
        UUID roundId = insertRound("Claim Scope Round", "PROPOSAL_OPEN");
        ensureLecturer("scope-lecturer-noclaim");

        MvcResult result = mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody(roundId, "Claim-less Topic"))
                        .with(lecturerJwtWithoutLecturerClaim("scope-lecturer-noclaim")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("Sign in again");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic WHERE title = 'Claim-less Topic'",
                Long.class))
                .as("no topic (and therefore no supervisor row) may be created")
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor",
                Long.class))
                .isZero();

        // With the claim the same request succeeds and seeds the supervisor.
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody(roundId, "Claimed Topic"))
                        .with(lecturerJwt("scope-lecturer-noclaim")))
                .andExpect(status().isOk());
        UUID topicId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_topic WHERE title = 'Claimed Topic'", UUID.class);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = ?"
                        + " AND lecturer_id = 'scope-lecturer-noclaim'",
                Long.class, topicId))
                .isEqualTo(1);
    }

    /**
     * GOV-P2-1: a TRUONG_KHOA account always carries the companion LECTURER
     * role, and create/updateTopic used to run the lecturer-department check
     * first — so a dean could not curate topics for other departments inside
     * their own faculty. The faculty scope now wins; an out-of-faculty
     * department still rejects.
     */
    @Test
    void facultyHeadWithCompanionLecturerRoleScopesTopicsByFacultyNotOwnDepartment() throws Exception {
        UUID roundId = insertRound("Dean Topic Scope Round", "PROPOSAL_OPEN");
        ensureLecturer("scope-lecturer-dean");
        String deanUser = "scope-lecturer-user-scope-lecturer-dean";
        ThesisAuthorityFixture.role(jdbc, deanUser, "LECTURER");
        RequestPostProcessor deanJwt = ThesisAuthorityFixture.role(jdbc, deanUser, "TRUONG_KHOA");
        ensureForeignDepartment();

        // department-other shares the dean's faculty-demo while sitting
        // outside their own department-demo.
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody(roundId, "Dean Cross-Department Topic", "department-other"))
                        .with(deanJwt))
                .andExpect(status().isOk());
        UUID topicId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_topic WHERE title = 'Dean Cross-Department Topic'",
                UUID.class);
        assertThat(topicId).isNotNull();

        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody(roundId, "Dean Foreign-Faculty Topic", "scope-department-foreign"))
                        .with(deanJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TOPIC_DEPARTMENT_FORBIDDEN"));

        mvc.perform(put("/api/v1/thesis/topics/{id}", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody(roundId, "Dean Cross-Department Topic (moved)", "department-demo"))
                        .with(deanJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value("department-demo"));
    }

    /**
     * GOV-P2-2: the governance read branch used to strip member contacts from
     * every group — even ones the dean personally supervises — and hid
     * supervised groups outside the faculty scope entirely. A supervised
     * group now keeps its contacts regardless of scope; other groups stay
     * contact-free.
     */
    @Test
    void facultyHeadKeepsContactsOnPersonallySupervisedGroupsOutsideTheirFaculty() throws Exception {
        UUID roundId = insertRound("Dean Supervised Read Round", "REGISTRATION_OPEN");
        ensureLecturer("scope-lecturer-dean2");
        String deanUser = "scope-lecturer-user-scope-lecturer-dean2";
        ThesisAuthorityFixture.role(jdbc, deanUser, "LECTURER");
        RequestPostProcessor deanJwt = ThesisAuthorityFixture.role(jdbc, deanUser, "TRUONG_KHOA");

        UUID inFacultyTopic = insertPublishedTopic(roundId);
        ensureForeignDepartment();
        UUID foreignTopic = insertPublishedTopic(roundId, "scope-department-foreign");
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order)"
                        + " VALUES (?, ?, 'scope-lecturer-dean2', 1)",
                UUID.randomUUID(), foreignTopic);
        UUID inFacultyGroup = insertGroupWithExternalMember(roundId, inFacultyTopic, "scope-member-10", "in-faculty@school.edu");
        UUID foreignGroup = insertGroupWithExternalMember(roundId, foreignTopic, "scope-member-11", "foreign@school.edu");

        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", roundId.toString())
                        .with(deanJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '%s')].members[?(@.isExternal == true)].contact"
                                .formatted(foreignGroup.toString()),
                        org.hamcrest.Matchers.hasItem("foreign@school.edu")))
                .andExpect(jsonPath("$[?(@.id == '%s')].members[*].contact"
                                .formatted(inFacultyGroup.toString()),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("in-faculty@school.edu"))));

        mvc.perform(get("/api/v1/thesis/groups/{id}", foreignGroup).with(deanJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[?(@.isExternal == true)].contact",
                        org.hamcrest.Matchers.hasItem("foreign@school.edu")));
    }

    /**
     * GOV-P2-3: updateTopic used to scope only the *request's* departmentId —
     * a dean could rewrite a foreign-faculty topic and pull it into their own
     * faculty by supplying an in-faculty departmentId. The topic's current
     * department must already be inside the dean's faculty (or personally
     * supervised) before the edit is allowed.
     */
    @Test
    void facultyHeadCannotClaimAForeignFacultyTopicThroughAnEdit() throws Exception {
        UUID roundId = insertRound("Dean Foreign Edit Round", "PROPOSAL_OPEN");
        ensureLecturer("scope-lecturer-dean3");
        String deanUser = "scope-lecturer-user-scope-lecturer-dean3";
        ThesisAuthorityFixture.role(jdbc, deanUser, "LECTURER");
        RequestPostProcessor deanJwt = ThesisAuthorityFixture.role(jdbc, deanUser, "TRUONG_KHOA");
        ensureForeignDepartment();

        UUID foreignTopic = insertPublishedTopic(roundId, "scope-department-foreign");
        // DRAFT is the only editable state; demote the fixture directly.
        jdbc.update("UPDATE thesis.thesis_topic SET status = 'DRAFT' WHERE id = ?", foreignTopic);

        mvc.perform(put("/api/v1/thesis/topics/{id}", foreignTopic)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody(roundId, "Claimed Topic", "department-demo"))
                        .with(deanJwt))
                .andExpect(status().isNotFound());

        // The same dean still edits a topic already inside their faculty.
        UUID ownTopic = insertPublishedTopic(roundId, "department-other");
        jdbc.update("UPDATE thesis.thesis_topic SET status = 'DRAFT' WHERE id = ?", ownTopic);
        mvc.perform(put("/api/v1/thesis/topics/{id}", ownTopic)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody(roundId, "Own Faculty Topic (edited)", "department-other"))
                        .with(deanJwt))
                .andExpect(status().isOk());
    }

    /**
     * GOV-AUDIT-1: isAdmin() used to include TRUONG_KHOA, so publish, archive,
     * supervisor, council-assignment, and reviewer-assignment paths ignored
     * the faculty scope that updateTopic already enforced. Every topic-level
     * governance write a dean performs on a foreign-faculty topic must now
     * return the same 404 the unscoped read path returns — while an
     * in-faculty topic stays manageable.
     */
    @Test
    void facultyHeadCannotGovernForeignFacultyTopicAcrossGovernancePaths() throws Exception {
        UUID roundId = insertRound("Dean Foreign Governance Round", "PROPOSAL_OPEN");
        ensureLecturer("scope-lecturer-dean4");
        ensureLecturer("scope-lecturer-peer4");
        ensureLecturer("scope-lecturer-chair4");
        ensureLecturer("scope-lecturer-sec4");
        String deanUser = "scope-lecturer-user-scope-lecturer-dean4";
        ThesisAuthorityFixture.role(jdbc, deanUser, "LECTURER");
        RequestPostProcessor deanJwt = ThesisAuthorityFixture.role(jdbc, deanUser, "TRUONG_KHOA");
        ensureForeignDepartment();

        // publish: a supervised foreign draft must not leave the draft state
        // under a foreign dean (the dean is not a supervisor here).
        UUID publishTarget = insertPublishedTopic(roundId, "scope-department-foreign");
        jdbc.update("UPDATE thesis.thesis_topic SET status = 'DRAFT' WHERE id = ?", publishTarget);
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order)"
                        + " VALUES (?, ?, 'scope-lecturer-peer4', 1)",
                UUID.randomUUID(), publishTarget);
        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", publishTarget).with(deanJwt))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOPIC_NOT_FOUND"));

        // archive: same 404 on a foreign topic even though it is unused.
        UUID archiveTarget = insertPublishedTopic(roundId, "scope-department-foreign");
        jdbc.update("UPDATE thesis.thesis_topic SET status = 'DRAFT' WHERE id = ?", archiveTarget);
        mvc.perform(post("/api/v1/thesis/topics/{id}/archive", archiveTarget).with(deanJwt))
                .andExpect(status().isNotFound());

        // supervisor replacement on a foreign topic is forbidden too.
        UUID supervisorTarget = insertPublishedTopic(roundId, "scope-department-foreign");
        mvc.perform(put("/api/v1/thesis/topics/{id}/supervisors", supervisorTarget)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorIds\":[\"scope-lecturer-peer4\"]}")
                        .with(deanJwt))
                .andExpect(status().isNotFound());

        // council assignment: a complete foreign-topic council assignment must
        // not be claimed by a dean outside the topic's faculty.
        UUID councilId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by)"
                        + " VALUES (?, ?, 'Scope Governance Council', 'ACTIVE', ?)",
                councilId, roundId, deanUser);
        jdbc.update(
                "INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role)"
                        + " VALUES (?, ?, 'scope-lecturer-chair4', 'CHAIR'),"
                        + " (?, ?, 'scope-lecturer-sec4', 'SECRETARY'),"
                        + " (?, ?, 'scope-lecturer-peer4', 'MEMBER')",
                UUID.randomUUID(), councilId, UUID.randomUUID(), councilId, UUID.randomUUID(), councilId);
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + supervisorTarget + "\"}")
                        .with(deanJwt))
                .andExpect(status().isNotFound());

        // counter-reviewer assignment on a foreign topic is forbidden.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", supervisorTarget)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"scope-lecturer-peer4\"}")
                        .with(deanJwt))
                .andExpect(status().isNotFound());

        // Positive control: the same dean still publishes an in-faculty topic.
        UUID ownTarget = insertPublishedTopic(roundId, "department-other");
        jdbc.update("UPDATE thesis.thesis_topic SET status = 'DRAFT' WHERE id = ?", ownTarget);
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order)"
                        + " VALUES (?, ?, 'scope-lecturer-peer4', 1)",
                UUID.randomUUID(), ownTarget);

        // Read parity: foreign DRAFT topics and their supervisors are invisible
        // (404/excluded) to a foreign dean, while in-faculty drafts remain visible.
        mvc.perform(get("/api/v1/thesis/topics")
                        .queryParam("roundId", roundId.toString())
                        .queryParam("status", "DRAFT")
                        .with(deanJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ownTarget.toString()));
        mvc.perform(get("/api/v1/thesis/topics/{id}", publishTarget).with(deanJwt))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/thesis/topics/{id}/supervisors", publishTarget).with(deanJwt))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", ownTarget).with(deanJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    /**
     * AGG-P3-1: the workload aggregation joined every group on a supervised
     * topic, so a cancelled group still inflated group_count and pending_count.
     * Cancelled groups must not count as active workload.
     */
    @Test
    void lecturerWorkloadExcludesCancelledGroupsFromCounts() throws Exception {
        UUID roundId = insertRound("Workload Round", "REGISTRATION_OPEN");
        ensureLecturer("scope-lecturer-workload");
        UUID topicId = insertPublishedTopic(roundId);
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order)"
                        + " VALUES (?, ?, 'scope-lecturer-workload', 1)",
                UUID.randomUUID(), topicId);
        jdbc.update(
                "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, 'scope-member-w1', ?, 'SUBMITTED', 'PENDING')",
                UUID.randomUUID(), roundId, topicId);
        jdbc.update(
                "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, 'scope-member-w2', ?, 'CANCELLED', 'PENDING')",
                UUID.randomUUID(), roundId, topicId);

        mvc.perform(get("/api/v1/thesis/me/workload").with(lecturerJwt("scope-lecturer-workload")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[0].groupCount").value(1))
                .andExpect(jsonPath("$.topics[0].pendingGroupCount").value(1));
    }

    // ---------- fixtures ----------

    private String topicBody(UUID roundId, String title) {
        return topicBody(roundId, title, "department-demo");
    }

    private String topicBody(UUID roundId, String title, String departmentId) {
        return "{\"roundId\":\"" + roundId + "\",\"departmentId\":\"" + departmentId + "\","
                + "\"title\":\"" + title + "\",\"description\":\"Supervisor scope fixture topic\",\"maxGroups\":2}";
    }

    private UUID insertRound(String name, String status) {
        UUID roundId = UUID.randomUUID();
        Instant windowStart = Instant.now().minusSeconds(3_600);
        Instant windowEnd = Instant.now().plusSeconds(3_600);
        Instant lecturerStart = "PROPOSAL_OPEN".equals(status)
                ? windowStart : windowStart.minusSeconds(7_200);
        Instant lecturerEnd = "PROPOSAL_OPEN".equals(status)
                ? windowEnd : windowStart.minusSeconds(3_600);
        Instant registrationStart = "PROPOSAL_OPEN".equals(status)
                ? windowEnd : windowStart;
        Instant registrationEnd = "PROPOSAL_OPEN".equals(status)
                ? windowEnd.plusSeconds(31L * 24 * 60 * 60) : windowEnd;
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?)",
                roundId, name,
                Timestamp.from(lecturerStart), Timestamp.from(lecturerEnd),
                Timestamp.from(registrationStart), Timestamp.from(registrationEnd),
                status);
        return roundId;
    }

    /** An active department in a faculty other than the dean's faculty-demo. */
    private void ensureForeignDepartment() {
        jdbc.update(
                "INSERT INTO academic.\"Faculty\" (\"id\", \"name\", \"isActive\") "
                        + "VALUES ('scope-faculty-foreign', 'Foreign Faculty', TRUE)");
        jdbc.update(
                "INSERT INTO academic.\"Department\" (\"id\", \"name\", \"facultyId\", \"isActive\") "
                        + "VALUES ('scope-department-foreign', 'Foreign Department', 'scope-faculty-foreign', TRUE)");
    }

    private UUID insertPublishedTopic(UUID roundId) {
        return insertPublishedTopic(roundId, "department-demo");
    }

    private UUID insertPublishedTopic(UUID roundId, String departmentId) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, ?, ?, 'scope fixture topic', 1, 'PUBLISHED', ?)",
                topicId, roundId, departmentId, "Scope Topic " + topicId, UUID.randomUUID());
        return topicId;
    }

    private UUID insertGroupWithExternalMember(UUID roundId, UUID topicId, String leaderStudentId, String contact) {
        UUID groupId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', 'APPROVED')",
                groupId, roundId, leaderStudentId, topicId);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 1, TRUE)",
                UUID.randomUUID(), groupId, roundId, leaderStudentId);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader,"
                        + " display_name, contact, is_external) "
                        + "VALUES (?, ?, ?, ?, 2, FALSE, 'External Member', ?, TRUE)",
                UUID.randomUUID(), groupId, roundId, "external-" + UUID.randomUUID(), contact);
        String peer = leaderStudentId + "-peer";
        ThesisAuthorityFixture.student(jdbc, peer);
        jdbc.update("INSERT INTO thesis.thesis_group_member "
                        + "(id, group_id, round_id, student_id, member_order, is_leader) VALUES (?, ?, ?, ?, 3, FALSE)",
                UUID.randomUUID(), groupId, roundId, peer);
        return groupId;
    }

    private void ensureLecturer(String lecturerId) {
        String userId = "scope-lecturer-user-" + lecturerId;
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Scope', 'Lecturer', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, lecturerId + "@campuscore.edu");
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                        + "VALUES (?, ?, 'department-demo', ?, TRUE)",
                lecturerId, userId, "GV-" + lecturerId);
    }

    private RequestPostProcessor lecturerJwt(String lecturerId) {
        return ThesisAuthorityFixture.lecturer(jdbc, lecturerId);
    }

    private RequestPostProcessor lecturerJwtWithoutLecturerClaim(String subject) {
        return jwt().jwt(token -> token
                        .subject(subject)
                        .claim("roles", List.of("LECTURER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
    }

    private RequestPostProcessor adminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "scope-admin", "ADMIN");
    }

    private RequestPostProcessor superAdminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "scope-super-admin", "SUPER_ADMIN");
    }
}
