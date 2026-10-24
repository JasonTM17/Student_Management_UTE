package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * A published round gets its real end of life through CLOSE: the audited,
 * reason-required, reversible transition RESULTS_PUBLISHED -> CLOSED that the
 * status enum always promised but no endpoint reached. CLOSED is read-only
 * for every mutation path and still serves published results; REOPEN returns
 * the round to RESULTS_PUBLISHED with its publication timestamp untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_round_close;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisRoundCloseTest {

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
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" LIKE 'cl1-%' OR \"actorId\" LIKE 'cl1-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'cl1-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'cl1-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'cl1-%'");
    }

    @Test
    void adminClosesAndReopensAPublishedRound() throws Exception {
        UUID roundId = insertRound("cl1-round-close", "RESULTS_PUBLISHED",
                Timestamp.from(Instant.now().minusSeconds(30L * 86_400)));

        mvc.perform(post("/api/v1/thesis/rounds/{id}/close", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"academic year archived\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closure.reopenAllowed").value(true))
                .andExpect(jsonPath("$.closure.closeAllowed").value(false));

        List<String> actions = auditActions(roundId);
        assertThat(actions).contains("THESIS_ROUND_CLOSED");
        String after = auditAfterState(roundId, "THESIS_ROUND_CLOSED");
        assertThat(after).contains("CLOSED").contains("academic year archived");

        mvc.perform(post("/api/v1/thesis/rounds/{id}/reopen", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"archive was premature\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESULTS_PUBLISHED"))
                .andExpect(jsonPath("$.closure.closeAllowed").value(true))
                .andExpect(jsonPath("$.closure.reopenAllowed").value(false));
        assertThat(auditActions(roundId)).contains("THESIS_ROUND_REOPENED");

        // The publication timestamp survives the close/reopen round trip —
        // reopen restores the published state, it does not republish.
        assertThat(jdbc.queryForObject(
                "SELECT results_published_at FROM thesis.thesis_registration_round WHERE id = ?",
                Timestamp.class, roundId)).isNotNull();
    }

    @Test
    void onlyGlobalAdminsMayCloseOrReopen() throws Exception {
        UUID roundId = insertRound("cl1-round-roles", "RESULTS_PUBLISHED",
                Timestamp.from(Instant.now().minusSeconds(30L * 86_400)));

        for (String role : List.of("TRUONG_KHOA", "TRUONG_BO_MON")) {
            mvc.perform(post("/api/v1/thesis/rounds/{id}/close", roundId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"nope\"}")
                            .with(ThesisAuthorityFixture.role(jdbc, "cl1-user-" + role.toLowerCase(), role)))
                    .andExpect(status().isForbidden());
        }
        ensureStudent("cl1-student-guard");
        ensureLecturer("cl1-lecturer-guard");
        mvc.perform(post("/api/v1/thesis/rounds/{id}/close", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"nope\"}")
                        .with(studentJwt("cl1-student-guard")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/close", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"nope\"}")
                        .with(lecturerJwt("cl1-lecturer-guard")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/thesis/rounds/{id}/close", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"archive\"}")
                        .with(ThesisAuthorityFixture.role(jdbc, "cl1-user-sa", "SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // Same matrix on reopen: non-admins stay refused on the way back.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/reopen", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"nope\"}")
                        .with(studentJwt("cl1-student-guard")))
                .andExpect(status().isForbidden());
    }

    @Test
    void closeOnlyAppliesToAPublishedRoundAndReopenToAClosedOne() throws Exception {
        UUID live = insertRound("cl1-round-live", "REGISTRATION_CLOSED", null);
        mvc.perform(post("/api/v1/thesis/rounds/{id}/close", live)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"too early\"}")
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

        mvc.perform(post("/api/v1/thesis/rounds/{id}/reopen", live)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"not closed\"}")
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

        // Double submit: the second close must see the committed CLOSED state.
        UUID published = insertRound("cl1-round-double", "RESULTS_PUBLISHED",
                Timestamp.from(Instant.now().minusSeconds(30L * 86_400)));
        mvc.perform(post("/api/v1/thesis/rounds/{id}/close", published)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"archive\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/close", published)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"again\"}")
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
    }

    @Test
    void closeAndReopenRequireAReason() throws Exception {
        UUID roundId = insertRound("cl1-round-reason", "RESULTS_PUBLISHED",
                Timestamp.from(Instant.now().minusSeconds(30L * 86_400)));
        for (String body : List.of("{}", "{\"reason\":\"   \"}")) {
            mvc.perform(post("/api/v1/thesis/rounds/{id}/close", roundId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(adminJwt()))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/thesis/rounds/{id}/close", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"archive\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/reopen", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closeIsRefusedWhileTheRetractGraceWindowIsOpen() throws Exception {
        // Published one hour ago: retract stays possible for 7 days, so
        // closing now would silently take that escape hatch away.
        UUID roundId = insertRound("cl1-round-grace", "RESULTS_PUBLISHED",
                Timestamp.from(Instant.now().minusSeconds(3_600)));
        mvc.perform(post("/api/v1/thesis/rounds/{id}/close", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"archive\"}")
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETRACT_WINDOW_OPEN"))
                .andExpect(jsonPath("$.details.retractWindowEndsAt").exists())
                .andExpect(jsonPath("$.details.remainingHours").isNumber());
        // The read model surfaces the same blocker without a second call.
        mvc.perform(get("/api/v1/thesis/rounds").param("status", "RESULTS_PUBLISHED").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + roundId + "')].closure.closeAllowed")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$[?(@.id=='" + roundId + "')].closure.closeBlocker")
                        .value(org.hamcrest.Matchers.hasItem("RETRACT_WINDOW_OPEN")))
                .andExpect(jsonPath("$[?(@.id=='" + roundId + "')].closure.retractWindowEndsAt")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.notNullValue())));
    }

    @Test
    void aClosedRoundIsReadOnlyButStillServesItsPublishedResults() throws Exception {
        UUID roundId = insertRound("cl1-round-ro", "RESULTS_PUBLISHED",
                Timestamp.from(Instant.now().minusSeconds(30L * 86_400)));
        ensureStudent("cl1-ro-student");
        UUID groupId = insertGroupWithMember(roundId, "cl1-ro-student");
        close(roundId);

        // Mutation surface: every write path refuses with a 409-family code.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/late-topic", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":\"department-demo\",\"title\":\"Late\","
                                + "\"description\":\"d\",\"maxGroups\":1,\"supervisorId\":\"cl1-lecturer-guard\","
                                + "\"reason\":\"late\"}")
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Council A\"}")
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
        mvc.perform(post("/api/v1/thesis/groups/{id}/roster-override", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"cl1-ro-student\",\"action\":\"REMOVE\",\"reason\":\"x\"}")
                        .with(adminJwt()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"r\",\"url\":\"https://example.test/r.pdf\"}")
                        .with(studentJwt("cl1-ro-student")))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/thesis/groups/{id}/progress", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}")
                        .with(studentJwt("cl1-ro-student")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(adminJwt()))
                .andExpect(status().isConflict());
        // Retract is gone too: the grace window closed before close was allowed.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/retract-results", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\",\"confirmation\":\"RETRACT\"}")
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
        // Council-side writes that bypass the round-mutation service must also
        // refuse: naming a counter-reviewer on an archived topic is a write.
        UUID topicId = insertTopic(roundId);
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"cl1-ro-lecturer\"}")
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

        // Read side stays open: the member can still see their results.
        mvc.perform(get("/api/v1/thesis/me/results")
                        .param("roundId", roundId.toString())
                        .with(studentJwt("cl1-ro-student")))
                .andExpect(status().isOk());
    }

    @Test
    void closedRoundsLeaveTheDefaultListButStayFilterable() throws Exception {
        UUID closed = insertRound("cl1-round-list", "RESULTS_PUBLISHED",
                Timestamp.from(Instant.now().minusSeconds(30L * 86_400)));
        insertRound("cl1-round-open", "REGISTRATION_OPEN", null);
        close(closed);

        mvc.perform(get("/api/v1/thesis/rounds").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("CLOSED"))));
        mvc.perform(get("/api/v1/thesis/rounds").param("status", "CLOSED").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.hasItem(closed.toString())))
                .andExpect(jsonPath("$[*].status").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("CLOSED"))));
    }

    // ---------- fixtures ----------

    private void close(UUID roundId) throws Exception {
        mvc.perform(post("/api/v1/thesis/rounds/{id}/close", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"archive\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    private List<String> auditActions(UUID entityId) {
        return jdbc.queryForList(
                "SELECT \"action\" FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ?",
                String.class, entityId.toString());
    }

    private String auditAfterState(UUID entityId, String action) {
        return jdbc.queryForObject(
                "SELECT \"afterState\" FROM campuscore_audit.\"AdminAudit\" "
                        + "WHERE \"entityId\" = ? AND \"action\" = ? ORDER BY \"createdAt\" DESC LIMIT 1",
                String.class, entityId.toString(), action);
    }

    private UUID insertGroupWithMember(UUID roundId, String studentId) {
        ensureStudent(studentId);
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, status, approval_status) "
                        + "VALUES (?, ?, ?, 'SUBMITTED', 'APPROVED')",
                groupId, roundId, studentId);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 1, TRUE)",
                UUID.randomUUID(), groupId, roundId, studentId);
        return groupId;
    }

    private UUID insertRound(String name, String status, Timestamp resultsPublishedAt) {
        Instant now = Instant.now();
        UUID roundId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status, results_published_at) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?, ?)",
                roundId, name,
                Timestamp.from(now.minusSeconds(14_400)), Timestamp.from(now.minusSeconds(7_200)),
                Timestamp.from(now.minusSeconds(7_200)), Timestamp.from(now.plusSeconds(86_400)),
                Timestamp.from(now.plusSeconds(172_800)),
                status, resultsPublishedAt);
        return roundId;
    }

    private UUID insertTopic(UUID roundId) {
        UUID topicId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', 'cl1 topic', 'fixture', 2, 'PUBLISHED', 'cl1-admin')",
                topicId, roundId);
        return topicId;
    }

    private void ensureStudent(String studentId) {
        String userId = "cl1-user-" + studentId;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"User\" WHERE \"id\" = ?", Integer.class, userId);
        if (count != null && count > 0) {
            return;
        }
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
        String userId = "cl1-user-" + lecturerId;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"User\" WHERE \"id\" = ?", Integer.class, userId);
        if (count != null && count > 0) {
            return;
        }
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
        return ThesisAuthorityFixture.student(jdbc, studentId);
    }

    private RequestPostProcessor lecturerJwt(String lecturerId) {
        return ThesisAuthorityFixture.lecturer(jdbc, lecturerId);
    }

    private RequestPostProcessor adminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "cl1-user-admin", "ADMIN");
    }
}
