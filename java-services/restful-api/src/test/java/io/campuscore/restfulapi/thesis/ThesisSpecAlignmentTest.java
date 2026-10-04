package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Faculty-spec alignment regressions: the 1-3 member cap, the GVPB
 * counter-reviewer flow (assignment, deadline, supervisor exclusion, comment,
 * frozen reassignment), round update/cancel lifecycle, and approval audit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_spec_alignment;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisSpecAlignmentTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_topic_score");
        jdbc.update("DELETE FROM thesis.thesis_council_topic");
        jdbc.update("DELETE FROM thesis.thesis_council_member");
        jdbc.update("DELETE FROM thesis.thesis_council");
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" LIKE 'sa-%' OR \"actorId\" LIKE 'sa-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'sa-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'sa-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'sa-%'");
    }

    // ---------- R1/R2: round update + cancel lifecycle ----------

    @Test
    void draftRoundScheduleCanBeAmendedAndCancelled() throws Exception {
        UUID roundId = insertRound("sa-round-edit", "DRAFT");

        Instant now = Instant.now();
        mvc.perform(put("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Amended round\",\"thesisType\":\"KLTN\","
                                + "\"lecturerSubmitStart\":\"" + now.minusSeconds(14_400) + "\","
                                + "\"lecturerSubmitEnd\":\"" + now.minusSeconds(7_200) + "\","
                                + "\"registrationStart\":\"" + now.minusSeconds(7_200) + "\","
                                + "\"registrationEnd\":\"" + now.plusSeconds(86_400) + "\","
                                + "\"gvpbDeadline\":\"" + now.plusSeconds(129_600) + "\","
                                + "\"reportDate\":\"" + now.plusSeconds(172_800) + "\","
                                + "\"defenseDate\":\"" + now.plusSeconds(216_000) + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Amended round"));

        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // A cancelled round is terminal: a second cancel is rejected.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isConflict());
    }

    @Test
    void lecturersCannotUpdateOrCancelRounds() throws Exception {
        ensureLecturer("sa-lecturer-1");
        UUID roundId = insertRound("sa-round-guard", "DRAFT");
        Instant now = Instant.now();
        mvc.perform(put("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\",\"thesisType\":\"KLTN\","
                                + "\"lecturerSubmitStart\":\"" + now.minusSeconds(14_400) + "\","
                                + "\"lecturerSubmitEnd\":\"" + now.minusSeconds(7_200) + "\","
                                + "\"registrationStart\":\"" + now.minusSeconds(7_200) + "\","
                                + "\"registrationEnd\":\"" + now.plusSeconds(86_400) + "\","
                                + "\"gvpbDeadline\":\"" + now.plusSeconds(129_600) + "\","
                                + "\"reportDate\":\"" + now.plusSeconds(172_800) + "\","
                                + "\"defenseDate\":\"" + now.plusSeconds(216_000) + "\"}")
                        .with(lecturerJwt("sa-lecturer-1")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(lecturerJwt("sa-lecturer-1")))
                .andExpect(status().isForbidden());
    }

    // ---------- R8a: GVPB counter-review ----------

    @Test
    void gvpbAssignScoreReadAndFreeze() throws Exception {
        UUID roundId = insertRound("sa-gvpb-round", "REGISTRATION_CLOSED");
        UUID topicId = insertPublishedTopic(roundId);
        ensureLecturer("sa-supervisor-1");
        ensureLecturer("sa-reviewer-1");
        ensureLecturer("sa-outsider-1");
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                        + "VALUES (?, ?, 'sa-supervisor-1', 1)",
                UUID.randomUUID(), topicId);

        // A topic supervisor cannot be its own counter-reviewer.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"sa-supervisor-1\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPERVISOR_CANNOT_REVIEW"));

        // Governance assigns the reviewer; the assignment is auditable.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"sa-reviewer-1\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        assertThat(auditActions(topicId)).contains("GVPB_ASSIGNED");

        // An unrelated lecturer cannot submit or read the counter-review.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8.0}")
                        .with(lecturerJwt("sa-outsider-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEWER_REQUIRED"));
        mvc.perform(get("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .with(lecturerJwt("sa-outsider-1")))
                .andExpect(status().isForbidden());

        // The reviewer submits score + comment; both are readable afterwards.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8.5,\"comment\":\"Đủ yêu cầu\"}")
                        .with(lecturerJwt("sa-reviewer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(8.5))
                .andExpect(jsonPath("$.comment").value("Đủ yêu cầu"))
                .andExpect(jsonPath("$.component").value("GVPB"));
        mvc.perform(get("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .with(lecturerJwt("sa-reviewer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(8.5));
        assertThat(auditActions(topicId)).contains("GVPB_SCORE_SUBMITTED");

        // The reviewer can revise before the deadline/finalization.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":9.0}")
                        .with(lecturerJwt("sa-reviewer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(9.0));

        // Once a score exists the assignment is frozen.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"sa-outsider-1\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GVPB_SCORE_SUBMITTED"));
    }

    @Test
    void gvpbScoreIsRejectedAfterTheDeadline() throws Exception {
        UUID roundId = insertRound("sa-gvpb-late", "REGISTRATION_CLOSED");
        // The schedule-order check requires registration_end <= gvpb_deadline,
        // so the whole tail of the window moves into the past together.
        Instant now = Instant.now();
        // dates_valid demands registration_end strictly after registration_start
        // (inserted at now-2h), and schedule_order demands end <= gvpb_deadline.
        jdbc.update("UPDATE thesis.thesis_registration_round "
                        + "SET registration_end = ?, gvpb_deadline = ? WHERE id = ?",
                Timestamp.from(now.minusSeconds(5_400)),
                Timestamp.from(now.minusSeconds(3_600)), roundId);
        UUID topicId = insertPublishedTopic(roundId);
        ensureLecturer("sa-reviewer-late");
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"sa-reviewer-late\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8.0}")
                        .with(lecturerJwt("sa-reviewer-late")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GVPB_DEADLINE_PASSED"));
    }

    // ---------- R4/R7: group cap + approval audit ----------

    @Test
    void aFourthMemberIsRejectedAndApprovalWritesAnAuditRow() throws Exception {
        UUID roundId = insertRound("sa-cap-round", "REGISTRATION_OPEN");
        ensureStudent("sa-leader-1");
        for (int i = 2; i <= 4; i++) {
            ensureStudent("sa-member-" + i);
        }
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("sa-leader-1")))
                .andExpect(status().isOk());
        UUID groupId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ?", UUID.class, roundId);
        for (int i = 2; i <= 3; i++) {
            addMember(groupId, "sa-member-" + i).andExpect(status().isOk());
        }
        addMember(groupId, "sa-member-4")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_FULL"));

        UUID topicId = insertPublishedTopic(roundId);
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt("sa-leader-1")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

        List<String> actions = auditActions(groupId);
        assertThat(actions).contains("THESIS_GROUP_APPROVED");
    }

    // ---------- fixtures ----------

    private List<String> auditActions(UUID entityId) {
        return jdbc.queryForList(
                "SELECT \"action\" FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ?",
                String.class, entityId.toString());
    }

    private org.springframework.test.web.servlet.ResultActions addMember(UUID groupId, String studentId) throws Exception {
        return mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"" + studentId + "\"}")
                .with(studentJwt("sa-leader-1")));
    }

    private UUID insertRound(String name, String status) {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?)",
                roundId, name,
                Timestamp.from(now.minusSeconds(14_400)), Timestamp.from(now.minusSeconds(7_200)),
                Timestamp.from(now.minusSeconds(7_200)), Timestamp.from(now.plusSeconds(86_400)),
                Timestamp.from(now.plusSeconds(172_800)),
                status);
        return roundId;
    }

    private UUID insertPublishedTopic(UUID roundId) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', ?, 'fixture', 3, 'PUBLISHED', ?)",
                topicId, roundId, "sa-topic-" + topicId.toString().substring(0, 8), UUID.randomUUID());
        return topicId;
    }

    private void ensureStudent(String studentId) {
        String userId = "sa-user-" + studentId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" "
                        + "(\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", "
                        + "\"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Spec', 'Fixture', 'ACTIVE', FALSE, FALSE, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, studentId + "@campuscore.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" "
                        + "(\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)",
                studentId, userId, "CODE-" + studentId);
    }

    private void ensureLecturer(String lecturerId) {
        String userId = "sa-user-" + lecturerId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" "
                        + "(\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", "
                        + "\"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Spec', 'Lecturer', 'ACTIVE', FALSE, FALSE, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, lecturerId + "@campuscore.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" "
                        + "(\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                        + "VALUES (?, ?, 'department-demo', ?, TRUE)",
                lecturerId, userId, "GV-" + lecturerId);
    }

    private RequestPostProcessor studentJwt(String studentId) {
        return jwt().jwt(token -> token
                        .subject("sa-user-" + studentId)
                        .claim("roles", List.of("STUDENT"))
                        .claim("studentId", studentId))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private RequestPostProcessor lecturerJwt(String lecturerId) {
        return jwt().jwt(token -> token
                        .subject("sa-user-" + lecturerId)
                        .claim("roles", List.of("LECTURER"))
                        .claim("lecturerId", lecturerId))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
    }

    private RequestPostProcessor truongKhoaJwt() {
        return jwt().jwt(token -> token
                        .subject("sa-tk-user")
                        .claim("roles", List.of("TRUONG_KHOA")))
                .authorities(new SimpleGrantedAuthority("ROLE_TRUONG_KHOA"));
    }
}
