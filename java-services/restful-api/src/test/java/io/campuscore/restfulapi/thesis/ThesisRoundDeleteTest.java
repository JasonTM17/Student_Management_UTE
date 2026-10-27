package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Round deletion is the narrow escape hatch for a round created by mistake:
 * only an EMPTY round in DRAFT or CANCELLED may be hard-deleted, the audit row
 * is written in the same transaction, and every dependent kind blocks with
 * ROUND_HAS_DATA plus the per-kind counts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_round_delete;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisRoundDeleteTest {

    private static final String REASON_BODY = "{\"reason\":\"created by mistake\"}";

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
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" LIKE 'rd-%' OR \"actorId\" LIKE 'rd-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'rd-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'rd-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'rd-%'");
    }

    @Test
    void anAdminDeletesAnEmptyDraftRound() throws Exception {
        UUID roundId = insertRound("rd-round-draft", "DRAFT");

        mvc.perform(delete("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON).content(REASON_BODY)
                        .with(adminJwt()))
                .andExpect(status().isNoContent());

        assertThat(roundCount(roundId)).isZero();
        assertThat(auditActions(roundId)).contains("THESIS_ROUND_DELETED");
        String before = auditBeforeState(roundId);
        assertThat(before).contains("rd-round-draft").contains("created by mistake");
    }

    @Test
    void aFacultyHeadDeletesAnEmptyCancelledRound() throws Exception {
        UUID roundId = insertRound("rd-round-cancelled", "REGISTRATION_CLOSED");
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON).content(REASON_BODY)
                        .with(truongKhoaJwt()))
                .andExpect(status().isNoContent());

        assertThat(roundCount(roundId)).isZero();
        assertThat(auditActions(roundId)).contains("THESIS_ROUND_CANCELLED", "THESIS_ROUND_DELETED");
    }

    @Test
    void aLiveOrTerminalRoundIsNeverDeletable() throws Exception {
        for (String status : List.of(
                "PROPOSAL_OPEN", "PROPOSALS_PUBLISHED", "REGISTRATION_OPEN",
                "REGISTRATION_CLOSED", "RESULTS_PUBLISHED", "CLOSED")) {
            UUID roundId = insertRound("rd-round-" + status.toLowerCase(), status);
            mvc.perform(delete("/api/v1/thesis/rounds/{id}", roundId)
                            .contentType(MediaType.APPLICATION_JSON).content(REASON_BODY)
                            .with(adminJwt()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROUND_NOT_DELETABLE"));
            assertThat(roundCount(roundId)).as("round %s must survive", status).isEqualTo(1);
        }
    }

    @Test
    void aRoundWithTopicsIsRefusedWithCounts() throws Exception {
        UUID roundId = insertRound("rd-round-topic", "DRAFT");
        insertTopic(roundId, "rd-topic-1");

        mvc.perform(delete("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON).content(REASON_BODY)
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_HAS_DATA"))
                .andExpect(jsonPath("$.details.topics").value(1));

        assertThat(roundCount(roundId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic WHERE round_id = ?",
                Integer.class, roundId)).isEqualTo(1);
        assertThat(auditActions(roundId)).doesNotContain("THESIS_ROUND_DELETED");
    }

    @Test
    void aRoundWithGroupsMembersAndReportsIsRefusedWithCounts() throws Exception {
        UUID roundId = insertRound("rd-round-data", "DRAFT");
        UUID groupId = insertGroupWithMember(roundId, "rd-student-1");
        jdbc.update("INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, url) "
                        + "VALUES (?, ?, ?, 'rd-student-1', 'report://x')",
                UUID.randomUUID(), groupId, roundId);

        mvc.perform(delete("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON).content(REASON_BODY)
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_HAS_DATA"))
                .andExpect(jsonPath("$.details.groups").value(1))
                .andExpect(jsonPath("$.details.members").value(1))
                .andExpect(jsonPath("$.details.reports").value(1));

        assertThat(roundCount(roundId)).isEqualTo(1);
    }

    @Test
    void aRoundWithACouncilIsRefusedWithCounts() throws Exception {
        UUID roundId = insertRound("rd-round-council", "DRAFT");
        jdbc.update("INSERT INTO thesis.thesis_council (id, round_id, name, created_by) "
                        + "VALUES (?, ?, 'Hội đồng 1', 'rd-user-admin')",
                UUID.randomUUID(), roundId);

        mvc.perform(delete("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON).content(REASON_BODY)
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_HAS_DATA"))
                .andExpect(jsonPath("$.details.councils").value(1));
    }

    @Test
    void aReasonIsRequired() throws Exception {
        UUID roundId = insertRound("rd-round-reason", "DRAFT");

        mvc.perform(delete("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(roundCount(roundId)).isEqualTo(1);
        assertThat(auditActions(roundId)).doesNotContain("THESIS_ROUND_DELETED");
    }

    @Test
    void aMissingRoundReturnsNotFound() throws Exception {
        mvc.perform(delete("/api/v1/thesis/rounds/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(REASON_BODY)
                        .with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUND_NOT_FOUND"));
    }

    @Test
    void nonGovernanceRolesAreRefused() throws Exception {
        ensureStudent("rd-student-guard");
        ensureLecturer("rd-lecturer-guard");
        UUID roundId = insertRound("rd-round-guard", "DRAFT");

        mvc.perform(delete("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON).content(REASON_BODY)
                        .with(studentJwt("rd-student-guard")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON).content(REASON_BODY)
                        .with(lecturerJwt("rd-lecturer-guard")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON).content(REASON_BODY)
                        .with(ThesisAuthorityFixture.role(jdbc, "rd-user-tbm", "TRUONG_BO_MON")))
                .andExpect(status().isForbidden());

        assertThat(roundCount(roundId)).isEqualTo(1);
    }

    @Test
    void aFacultyHeadWithoutABoundLecturerProfileIsRefused() throws Exception {
        UUID roundId = insertRound("rd-round-foreign", "DRAFT");

        mvc.perform(delete("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON).content(REASON_BODY)
                        .with(ThesisAuthorityFixture.role(jdbc, "rd-user-tk-scopeless", "TRUONG_KHOA")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FACULTY_PROFILE_REQUIRED"));

        assertThat(roundCount(roundId)).isEqualTo(1);
    }

    @Test
    void theReadModelExposesDeletabilityAndBlockerCounts() throws Exception {
        UUID empty = insertRound("rd-round-empty", "DRAFT");
        UUID blocked = insertRound("rd-round-blocked", "DRAFT");
        insertTopic(blocked, "rd-topic-2");

        String body = mvc.perform(get("/api/v1/thesis/rounds").with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> rounds = com.jayway.jsonpath.JsonPath.read(body, "$");
        Map<String, Object> emptyDeletion = deletionOf(rounds, empty);
        Map<String, Object> blockedDeletion = deletionOf(rounds, blocked);
        assertThat(emptyDeletion.get("deletable")).isEqualTo(true);
        assertThat(blockedDeletion.get("deletable")).isEqualTo(false);
        assertThat(((Number) blockedDeletion.get("topics")).intValue()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deletionOf(List<Map<String, Object>> rounds, UUID id) {
        Map<String, Object> row = rounds.stream()
                .filter(entry -> id.toString().equals(entry.get("id")))
                .findFirst().orElseThrow();
        return (Map<String, Object>) row.get("deletion");
    }

    private int roundCount(UUID roundId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_registration_round WHERE id = ?",
                Integer.class, roundId);
        return count == null ? 0 : count;
    }

    private List<String> auditActions(UUID entityId) {
        return jdbc.queryForList(
                "SELECT \"action\" FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ?",
                String.class, entityId.toString());
    }

    private String auditBeforeState(UUID entityId) {
        return jdbc.queryForObject(
                "SELECT \"beforeState\" FROM campuscore_audit.\"AdminAudit\" "
                        + "WHERE \"entityId\" = ? AND \"action\" = 'THESIS_ROUND_DELETED' "
                        + "ORDER BY \"createdAt\" DESC LIMIT 1",
                String.class, entityId.toString());
    }

    private UUID insertTopic(UUID roundId, String title) {
        UUID topicId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', ?, 'desc', 'OPEN', 'rd-lecturer')",
                topicId, roundId, title);
        return topicId;
    }

    private UUID insertGroupWithMember(UUID roundId, String studentId) {
        ensureStudent(studentId);
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, status, approval_status) "
                        + "VALUES (?, ?, ?, 'SUBMITTED', 'PENDING')",
                groupId, roundId, studentId);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 1, TRUE)",
                UUID.randomUUID(), groupId, roundId, studentId);
        return groupId;
    }

    private UUID insertRound(String name, String status) {
        Instant now = Instant.now();
        UUID roundId = UUID.randomUUID();
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

    private void ensureStudent(String studentId) {
        String userId = "rd-user-" + studentId;
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
        String userId = "rd-user-" + lecturerId;
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

    private RequestPostProcessor adminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "rd-user-admin", "ADMIN");
    }

    private RequestPostProcessor studentJwt(String studentId) {
        return ThesisAuthorityFixture.student(jdbc, studentId);
    }

    private RequestPostProcessor lecturerJwt(String lecturerId) {
        return ThesisAuthorityFixture.lecturer(jdbc, lecturerId);
    }

    private RequestPostProcessor truongKhoaJwt() {
        ThesisAuthorityFixture.role(jdbc, "rd-user-tk", "TRUONG_KHOA");
        Integer bound = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = 'rd-tk-lecturer'",
                Integer.class);
        if (bound == null || bound == 0) {
            jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                    + "VALUES ('rd-tk-lecturer', 'rd-user-tk', 'department-demo', 'GV-rd-tk', TRUE)");
        }
        return ThesisAuthorityFixture.role(jdbc, "rd-user-tk", "TRUONG_KHOA");
    }
}
