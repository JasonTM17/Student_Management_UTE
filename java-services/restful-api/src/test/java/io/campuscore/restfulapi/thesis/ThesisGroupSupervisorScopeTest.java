package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'scope-member-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'scope-user-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'scope-lecturer-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'scope-lecturer-user-%'");
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
    }

    @Test
    void topicSubmissionWithoutLecturerIdClaimIsRejectedWithoutSeedingASupervisorRow() throws Exception {
        UUID roundId = insertRound("Claim Scope Round", "PROPOSAL_OPEN");
        ensureLecturer("scope-lecturer-noclaim");

        MvcResult result = mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody(roundId, "Claim-less Topic"))
                        .with(lecturerJwtWithoutLecturerClaim("scope-lecturer-noclaim")))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("lecturerId");
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

    // ---------- fixtures ----------

    private String topicBody(UUID roundId, String title) {
        return "{\"roundId\":\"" + roundId + "\",\"departmentId\":\"department-demo\","
                + "\"title\":\"" + title + "\",\"description\":\"Supervisor scope fixture topic\",\"maxGroups\":2}";
    }

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
                Timestamp.from(windowStart), Timestamp.from(windowEnd),
                Timestamp.from(windowStart), Timestamp.from(windowEnd),
                status);
        return roundId;
    }

    private UUID insertPublishedTopic(UUID roundId) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', ?, 'scope fixture topic', 1, 'PUBLISHED', ?)",
                topicId, roundId, "Scope Topic " + topicId, UUID.randomUUID());
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
        return jwt().jwt(token -> token
                        .subject(lecturerId)
                        .claim("roles", List.of("LECTURER"))
                        .claim("lecturerId", lecturerId))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
    }

    private RequestPostProcessor lecturerJwtWithoutLecturerClaim(String subject) {
        return jwt().jwt(token -> token
                        .subject(subject)
                        .claim("roles", List.of("LECTURER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject("scope-admin")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
