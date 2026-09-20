package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.exception.ErrorCode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The faculty's ruling is 3 to 4 members per group with exactly one leader, and
 * the enforced constants already say so ({@code MIN_GROUP_MEMBERS = 3},
 * {@code MAX_GROUP_MEMBERS = 4}). The copy did not: the catalogue entry, the
 * capacity refusal and the approval refusal all stated a bare "at least three
 * members" or "at most four members" without the range, and a group below the
 * minimum — created by design, since the creator is the sole initial member —
 * failed with an opaque message that never said how many members were missing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_group_roster_copy;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisGroupRosterCopyTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'gc-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'gc-%'");
    }

    @Test
    void catalogueCopyStatesTheThreeToFourRange() {
        assertThatMessage(ErrorCode.GROUP_TOO_SMALL, HttpStatus.CONFLICT, "3 to 4");
        assertThatMessage(ErrorCode.GROUP_FULL, HttpStatus.CONFLICT, "3 to 4");
    }

    @Test
    void approvalRefusalNamesTheRangeAndTheMissingMembers() throws Exception {
        UUID groupId = singleLeaderGroup();
        // The group is below the minimum by design; the refusal must tell the
        // student what is missing instead of only that something is.
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_TOO_SMALL"))
                .andExpect(jsonPath("$.message", containsString("3 to 4")))
                .andExpect(jsonPath("$.message", containsString("has 1")))
                .andExpect(jsonPath("$.message", containsString("2 short")));
    }

    @Test
    void capacityRefusalStatesTheRange() throws Exception {
        UUID roundId = insertLiveRound("gc-capacity-round");
        UUID topicId = insertPublishedTopic(roundId);
        ensureActiveStudent("gc-leader", "gc-user-leader");
        for (int index = 2; index <= 5; index++) {
            ensureActiveStudent("gc-member-" + index, "gc-user-member-" + index);
        }
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("gc-leader")))
                .andExpect(status().isOk());
        UUID groupId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ?", UUID.class, roundId);
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt("gc-leader")))
                .andExpect(status().isOk());

        for (int index = 2; index <= 4; index++) {
            mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"studentId\":\"gc-member-" + index + "\"}")
                            .with(studentJwt("gc-leader")))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"gc-member-5\"}")
                        .with(studentJwt("gc-leader")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_FULL"))
                .andExpect(jsonPath("$.message", containsString("3 to 4")));
    }

    @Test
    void removalRefusalNamesTheRangeAndTheResultingRoster() throws Exception {
        UUID roundId = insertLiveRound("gc-removal-round");
        UUID groupId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, status, approval_status) "
                        + "VALUES (?, ?, 'gc-removal-leader', 'SUBMITTED', 'APPROVED')",
                groupId, roundId);
        insertMember(groupId, roundId, "gc-removal-leader", 1);
        insertMember(groupId, roundId, "gc-removal-member-2", 2);
        insertMember(groupId, roundId, "gc-removal-member-3", 3);

        mvc.perform(delete("/api/v1/thesis/groups/{id}/members/{studentId}", groupId, "gc-removal-member-3")
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_TOO_SMALL"))
                .andExpect(jsonPath("$.message", containsString("3 to 4")))
                .andExpect(jsonPath("$.message", containsString("would leave 2")));
    }

    // ---------- fixtures ----------

    private void assertThatMessage(ErrorCode code, HttpStatus expectedStatus, String expectedFragment) {
        assertThat(code.getHttpStatus()).isEqualTo(expectedStatus);
        assertThat(code.getDefaultMessage()).contains(expectedFragment);
    }

    /** A live round with a lone-leader group carrying a published topic, so approval is the only refusal left. */
    private UUID singleLeaderGroup() throws Exception {
        UUID roundId = insertLiveRound("gc-approval-round");
        UUID topicId = insertPublishedTopic(roundId);
        ensureActiveStudent("gc-solo-leader", "gc-user-solo-leader");
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("gc-solo-leader")))
                .andExpect(status().isOk());
        UUID groupId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ?", UUID.class, roundId);
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt("gc-solo-leader")))
                .andExpect(status().isOk());
        return groupId;
    }

    private UUID insertLiveRound(String name) {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, 'REGISTRATION_OPEN')",
                roundId, name,
                Timestamp.from(now.minusSeconds(7_200)), Timestamp.from(now.minusSeconds(3_600)),
                Timestamp.from(now.minusSeconds(3_600)), Timestamp.from(now.plusSeconds(30L * 24 * 60 * 60)),
                Timestamp.from(now.plusSeconds(31L * 24 * 60 * 60)));
        return roundId;
    }

    private UUID insertPublishedTopic(UUID roundId) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', 'Roster copy topic', 'fixture', 4, 'PUBLISHED', ?)",
                topicId, roundId, UUID.randomUUID());
        return topicId;
    }

    private void insertMember(UUID groupId, UUID roundId, String studentId, int memberOrder) {
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), groupId, roundId, studentId, memberOrder, memberOrder == 1);
    }

    private void ensureActiveStudent(String studentId, String userId) {
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Gc', 'Member', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, studentId + "@campuscore.edu");
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)",
                studentId, userId, "CODE-" + studentId);
    }

    private RequestPostProcessor studentJwt(String studentId) {
        return jwt().jwt(token -> token
                        .subject("gc-user-" + studentId)
                        .claim("roles", List.of("STUDENT"))
                        .claim("studentId", studentId))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject("gc-admin-user")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
