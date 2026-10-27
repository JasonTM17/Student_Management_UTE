package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
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
 * Close-registration safety (plan F1/F2): the admin previews group and report
 * counts before closing a round, and one audited escape hatch
 * (PUT report-deadline) reopens the frozen report window of a
 * REGISTRATION_CLOSED round without ever reopening roster mutation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_close_safety;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2",
        "thesis.report.storage.local-root=target/test-report-storage"
})
class ThesisCloseRegistrationSafetyTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\"");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'cs-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'cs-user-%' OR \"id\" LIKE 'thesis-fixture-student-cs-%'");
    }

    // ---------- preview ----------

    @Test
    void closePreviewReportsGroupCountsReportRowsAndDeadlineState() throws Exception {
        Instant now = Instant.now();
        Instant deadline = now.plusSeconds(86_400);
        UUID roundId = insertRound("KLTN", "REGISTRATION_OPEN", deadline);

        UUID approvedWithReport = insertGroup(roundId, "cs-leader-1", "SUBMITTED", "APPROVED");
        insertGroup(roundId, "cs-leader-2", "DRAFT", "APPROVED");
        insertGroup(roundId, "cs-leader-3", "DRAFT", "PENDING");
        insertGroup(roundId, "cs-leader-4", "CANCELLED", "APPROVED");
        insertReport(approvedWithReport, roundId, "cs-leader-1");

        mvc.perform(get("/api/v1/thesis/rounds/{id}/close-registration-preview", roundId)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupCount").value(3))
                .andExpect(jsonPath("$.approvedGroupCount").value(2))
                .andExpect(jsonPath("$.pendingGroupCount").value(1))
                .andExpect(jsonPath("$.reportCount").value(1))
                .andExpect(jsonPath("$.reportDeadline").isNotEmpty())
                .andExpect(jsonPath("$.reportDeadlinePassed").value(false));

        // A round whose authored deadline already passed warns the admin but
        // still answers — the close itself stays allowed (plan I5).
        UUID passedRound = insertRound("KLTN", "REGISTRATION_OPEN", now.minusSeconds(3_600));
        mvc.perform(get("/api/v1/thesis/rounds/{id}/close-registration-preview", passedRound)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportDeadlinePassed").value(true));

        mvc.perform(get("/api/v1/thesis/rounds/{id}/close-registration-preview", UUID.randomUUID())
                        .with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUND_NOT_FOUND"));
    }

    @Test
    void closePreviewIsStaffOnly() throws Exception {
        UUID roundId = insertRound("KLTN", "REGISTRATION_OPEN", Instant.now().plusSeconds(86_400));
        mvc.perform(get("/api/v1/thesis/rounds/{id}/close-registration-preview", roundId)
                        .with(studentJwt("cs-student")))
                .andExpect(status().isForbidden());
    }

    // ---------- escape hatch ----------

    @Test
    void extendingTheDeadlineReopensAFrozenReportWindow() throws Exception {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        UUID roundId = insertRound("KLTN", "REGISTRATION_CLOSED", now.minusSeconds(3_600));
        UUID groupId = insertGroup(roundId, "cs-leader-frozen", "SUBMITTED", "APPROVED");

        // The direct F1 regression: the report is frozen while the deadline is past.
        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/late.pdf\"}")
                        .with(studentJwt("cs-leader-frozen")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_DEADLINE_PASSED"));

        Instant newDeadline = now.plusSeconds(7L * 24 * 60 * 60);
        mvc.perform(put("/api/v1/thesis/rounds/{id}/report-deadline", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportDeadline\":\"" + newDeadline + "\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportDeadline").isNotEmpty());

        // The escape hatch is audited like every other schedule mutation.
        Integer auditRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" "
                        + "WHERE \"action\" = 'THESIS_ROUND_REPORT_DEADLINE_EXTENDED' AND \"entityId\" = ?",
                Integer.class, roundId.toString());
        assertThat(auditRows).isEqualTo(1);

        // R4 regression: the extension writes only report_deadline — the
        // shared gvpb_deadline stays frozen in the past, so the GVPB scoring
        // window is not silently reopened by a report-window rescue.
        Timestamp storedGvpb = jdbc.queryForObject(
                "SELECT gvpb_deadline FROM thesis.thesis_registration_round WHERE id = ?",
                Timestamp.class, roundId);
        assertThat(storedGvpb.toInstant()).isEqualTo(now.minusSeconds(3_600));
        Timestamp storedReportDeadline = jdbc.queryForObject(
                "SELECT report_deadline FROM thesis.thesis_registration_round WHERE id = ?",
                Timestamp.class, roundId);
        assertThat(storedReportDeadline.toInstant()).isEqualTo(newDeadline);

        // The same report write now passes the window gate.
        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/late.pdf\"}")
                        .with(studentJwt("cs-leader-frozen")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://drive.example.com/late.pdf"));
    }

    @Test
    void extendingTheDeadlineRequiresARegistrationClosedRound() throws Exception {
        Instant now = Instant.now();
        UUID open = insertRound("KLTN", "REGISTRATION_OPEN", now.plusSeconds(86_400));
        UUID published = insertRound("KLTN", "RESULTS_PUBLISHED", now.plusSeconds(86_400));
        String body = "{\"reportDeadline\":\"" + now.plusSeconds(172_800) + "\"}";

        mvc.perform(put("/api/v1/thesis/rounds/{id}/report-deadline", open)
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
        mvc.perform(put("/api/v1/thesis/rounds/{id}/report-deadline", published)
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

        // No audit row and no deadline change were produced by the rejects.
        assertThat(auditCount()).isZero();
    }

    @Test
    void extendingTheDeadlineCannotShortenTheEffectiveFutureWindow() throws Exception {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        for (String type : new String[] {"KLTN", "TLCN"}) {
            for (boolean hasOverride : new boolean[] {false, true}) {
                Instant gvpb = now.plusSeconds(3 * 86_400L);
                Instant effective = hasOverride ? now.plusSeconds(4 * 86_400L) : gvpb;
                UUID roundId = insertRound(type, "REGISTRATION_CLOSED", gvpb);
                if (hasOverride) {
                    jdbc.update("UPDATE thesis.thesis_registration_round SET report_deadline = ? WHERE id = ?",
                            Timestamp.from(effective), roundId);
                }
                Number originalVersion = jdbc.queryForObject(
                        "SELECT version FROM thesis.thesis_registration_round WHERE id = ?", Number.class, roundId);
                int originalAuditCount = auditCount();

                mvc.perform(put("/api/v1/thesis/rounds/{id}/report-deadline", roundId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reportDeadline\":\"" + effective.minusSeconds(86_400) + "\"}")
                                .with(adminJwt()))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

                Timestamp storedOverride = jdbc.queryForObject(
                        "SELECT report_deadline FROM thesis.thesis_registration_round WHERE id = ?", Timestamp.class, roundId);
                assertThat(storedOverride).isEqualTo(hasOverride ? Timestamp.from(effective) : null);
                assertThat(jdbc.queryForObject("SELECT gvpb_deadline FROM thesis.thesis_registration_round WHERE id = ?",
                        Timestamp.class, roundId).toInstant()).isEqualTo(gvpb);
                assertThat(jdbc.queryForObject("SELECT version FROM thesis.thesis_registration_round WHERE id = ?",
                        Number.class, roundId).longValue()).isEqualTo(originalVersion.longValue());
                assertThat(auditCount()).isEqualTo(originalAuditCount);

                for (Instant compatible : new Instant[] {effective, effective.plusSeconds(86_400)}) {
                    mvc.perform(put("/api/v1/thesis/rounds/{id}/report-deadline", roundId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"reportDeadline\":\"" + compatible + "\"}")
                                    .with(adminJwt()))
                            .andExpect(status().isOk());
                    assertThat(jdbc.queryForObject("SELECT report_deadline FROM thesis.thesis_registration_round WHERE id = ?",
                            Timestamp.class, roundId).toInstant()).isEqualTo(compatible);
                    assertThat(jdbc.queryForObject("SELECT gvpb_deadline FROM thesis.thesis_registration_round WHERE id = ?",
                            Timestamp.class, roundId).toInstant()).isEqualTo(gvpb);
                }
                assertThat(auditCount()).isEqualTo(originalAuditCount + 2);
                assertThat(jdbc.queryForObject("SELECT version FROM thesis.thesis_registration_round WHERE id = ?",
                        Number.class, roundId).longValue()).isEqualTo(originalVersion.longValue() + 2);
            }
        }
    }

    @Test
    void extendingTheDeadlineRejectsRoundsWithoutAGvpbDeadline() throws Exception {
        UUID monHoc = insertRound("MON_HOC", "REGISTRATION_CLOSED", null);
        mvc.perform(put("/api/v1/thesis/rounds/{id}/report-deadline", monHoc)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportDeadline\":\"" + Instant.now().plusSeconds(86_400) + "\"}")
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_HAS_NO_COUNTER_REVIEW"));
    }

    @Test
    void extendingTheDeadlineRejectsAPastDateAndStudentCallers() throws Exception {
        Instant now = Instant.now();
        UUID roundId = insertRound("KLTN", "REGISTRATION_CLOSED", now.minusSeconds(3_600));

        mvc.perform(put("/api/v1/thesis/rounds/{id}/report-deadline", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportDeadline\":\"" + now.minusSeconds(60) + "\"}")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(put("/api/v1/thesis/rounds/{id}/report-deadline", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportDeadline\":\"" + now.plusSeconds(86_400) + "\"}")
                        .with(studentJwt("cs-student")))
                .andExpect(status().isForbidden());
    }

    // ---------- fixtures ----------

    private UUID insertRound(String thesisType, String status, Instant gvpbDeadline) {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        // thesis_round_schedule_order_valid requires gvpb_deadline >=
        // registration_end, so a past deadline needs the whole window shifted
        // before it — the report gate only reads gvpb_deadline and status.
        Instant registrationEnd = gvpbDeadline != null
                ? gvpbDeadline.minusSeconds(7_200)
                : now.plusSeconds(3_600);
        Instant registrationStart = registrationEnd.minusSeconds(14_400);
        Instant lecturerEnd = registrationStart.minusSeconds(3_600);
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                roundId, "Close safety " + thesisType + " " + status, thesisType,
                Timestamp.from(lecturerEnd.minusSeconds(3_600)), Timestamp.from(lecturerEnd),
                Timestamp.from(registrationStart), Timestamp.from(registrationEnd),
                gvpbDeadline == null ? null : Timestamp.from(gvpbDeadline), status);
        return roundId;
    }

    private UUID insertGroup(UUID roundId, String leaderId, String status, String approvalStatus) {
        UUID groupId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, ?)",
                groupId, roundId, leaderId, status, approvalStatus);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 1, TRUE)",
                UUID.randomUUID(), groupId, roundId, leaderId);
        seedStudent(leaderId);
        return groupId;
    }

    private void insertReport(UUID groupId, UUID roundId, String submittedBy) {
        jdbc.update(
                "INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url) "
                        + "VALUES (?, ?, ?, ?, 'Close safety report', 'https://drive.example.com/seed.pdf')",
                UUID.randomUUID(), groupId, roundId, submittedBy);
    }

    private void seedStudent(String studentId) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Student\" WHERE \"id\" = ?", Integer.class, studentId);
        if (existing != null && existing > 0) {
            return;
        }
        String userId = "cs-user-" + studentId;
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'CS', 'Member', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, studentId + "@campuscore.edu");
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)",
                studentId, userId, "CODE-" + studentId);
    }

    private int auditCount() {
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" "
                        + "WHERE \"action\" = 'THESIS_ROUND_REPORT_DEADLINE_EXTENDED'",
                Integer.class);
        return rows == null ? 0 : rows;
    }

    private RequestPostProcessor studentJwt(String studentId) {
        return ThesisAuthorityFixture.student(jdbc, studentId);
    }

    private RequestPostProcessor adminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "cs-user-admin", "ADMIN");
    }
}
