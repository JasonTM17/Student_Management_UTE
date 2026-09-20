package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The report deliverable window, which two audited defects shared:
 *
 * <ol>
 *   <li>submission ignored the round status, so a leader could submit or
 *       replace the report while the round was still REGISTRATION_OPEN or
 *       after RESULTS_PUBLISHED;</li>
 *   <li>the freeze keyed on a non-null GVPB deadline, which MON_HOC/NCKH rounds
 *       never carry (ThesisMutationService.createRound rejects the date for
 *       them), so course-project and undergraduate-research reports stayed
 *       editable forever — and every edit deleted the previous version.</li>
 * </ol>
 *
 * The round status is therefore the window's spine: writable exactly while the
 * round is REGISTRATION_CLOSED (after registration, before results), frozen by
 * the authored GVPB deadline for the round kinds that carry one, and frozen by
 * the round's result publication for the kinds that do not. Every case below
 * describes pre-fix behaviour that returned 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_report_write_window;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2",
        "thesis.report.storage.local-root=target/test-report-storage"
})
class ThesisReportWriteWindowTest {

    private static final byte[] DOCX_BYTES = {'P', 'K', 0x03, 0x04, 'f', 'a', 'k', 'e', '-', 'd', 'o', 'c', 'x'};

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
    }

    // ---------- defect 1: the round status never gated the report ----------

    @Test
    void leaderCannotSubmitOrReplaceAReportWhileRegistrationIsStillOpen() throws Exception {
        Instant now = Instant.now();
        // A live registration window and a GVPB deadline in the future: the
        // pre-fix gates (deadline only, plus approval) both passed, so this
        // submission returned 200 while the round was still registering.
        UUID roundId = insertRound("rw-open-registration", "KLTN", "REGISTRATION_OPEN",
                now.minusSeconds(3_600), now.plusSeconds(30L * 24 * 60 * 60),
                now.plusSeconds(31L * 24 * 60 * 60));
        UUID groupId = insertApprovedGroup(roundId, "rw-leader-open");

        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/early.pdf\",\"title\":\"Early report\"}")
                        .with(studentJwt("rw-leader-open")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_WINDOW_CLOSED"));

        // The attached-document path shares the gate.
        mvc.perform(multipart("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .file(new MockMultipartFile("file", "early.docx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, DOCX_BYTES))
                        .with(studentJwt("rw-leader-open")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_WINDOW_CLOSED"));

        // Nothing was written and no stored object was replaced.
        assertThat(reportRows(groupId)).isZero();
    }

    @Test
    void reportCannotBeReplacedAfterResultsArePublished() throws Exception {
        Instant now = Instant.now();
        // GVPB deadline still in the future, so only the round status can stop
        // this replacement — which is exactly what the pre-fix code did not do.
        UUID roundId = insertRound("rw-results-published", "KLTN", "RESULTS_PUBLISHED",
                now.minusSeconds(30L * 24 * 60 * 60), now.minusSeconds(3_600),
                now.plusSeconds(86_400));
        UUID groupId = insertApprovedGroup(roundId, "rw-leader-results");
        insertReport(groupId, roundId, "rw-leader-results", "Frozen final report", "https://drive.example.com/final.pdf");

        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/rewritten.pdf\",\"title\":\"Rewritten\"}")
                        .with(studentJwt("rw-leader-results")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_WINDOW_CLOSED"));

        // DELETE-then-INSERT must not have destroyed the graded artifact.
        assertThat(jdbc.queryForObject(
                "SELECT url FROM thesis.thesis_group_report WHERE group_id = ?", String.class, groupId))
                .isEqualTo("https://drive.example.com/final.pdf");
    }

    @Test
    void reportStaysWritableWhileTheRoundIsRegistrationClosed() throws Exception {
        Instant now = Instant.now();
        UUID roundId = insertRound("rw-registration-closed", "KLTN", "REGISTRATION_CLOSED",
                now.minusSeconds(3_600), now.plusSeconds(30L * 24 * 60 * 60),
                now.plusSeconds(31L * 24 * 60 * 60));
        UUID groupId = insertApprovedGroup(roundId, "rw-leader-closed");

        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/ontime.pdf\"}")
                        .with(studentJwt("rw-leader-closed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://drive.example.com/ontime.pdf"));
    }

    // ---------- defect 2: MON_HOC/NCKH reports never froze ----------

    @ParameterizedTest(name = "{0} report freezes once the round publishes results")
    @ValueSource(strings = {"MON_HOC", "NCKH"})
    void courseAndResearchReportsFreezeWhenResultsArePublished(String thesisType) throws Exception {
        Instant now = Instant.now();
        // No GVPB deadline is possible for these round types, so the pre-fix
        // freeze branch was unreachable: this replacement returned 200 forever.
        UUID roundId = insertRound("rw-" + thesisType.toLowerCase(java.util.Locale.ROOT) + "-published",
                thesisType, "RESULTS_PUBLISHED",
                now.minusSeconds(30L * 24 * 60 * 60), now.minusSeconds(3_600), null);
        UUID groupId = insertApprovedGroup(roundId, "rw-leader-" + thesisType);
        insertReport(groupId, roundId, "rw-leader-" + thesisType, "Submitted course report",
                "https://drive.example.com/course.pdf");

        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/after-results.pdf\"}")
                        .with(studentJwt("rw-leader-" + thesisType)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_WINDOW_CLOSED"));

        assertThat(jdbc.queryForObject(
                "SELECT url FROM thesis.thesis_group_report WHERE group_id = ?", String.class, groupId))
                .isEqualTo("https://drive.example.com/course.pdf");
    }

    @ParameterizedTest(name = "{0} report is not frozen while the round is still marking")
    @ValueSource(strings = {"MON_HOC", "NCKH"})
    void courseAndResearchReportsAreNotFrozenByTheirRegistrationEnd(String thesisType) throws Exception {
        Instant now = Instant.now();
        // registration_end, the last date these round types carry, is already
        // past. It must not be mistaken for a report deadline: keying the
        // freeze to it would make the deliverable unwritable the moment it
        // becomes writable, which is worse than leaving it unfrozen.
        UUID roundId = insertRound("rw-" + thesisType.toLowerCase(java.util.Locale.ROOT) + "-marking",
                thesisType, "REGISTRATION_CLOSED",
                now.minusSeconds(30L * 24 * 60 * 60), now.minusSeconds(86_400), null);
        UUID groupId = insertApprovedGroup(roundId, "rw-leader-marking-" + thesisType);

        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/marking.pdf\"}")
                        .with(studentJwt("rw-leader-marking-" + thesisType)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://drive.example.com/marking.pdf"));
    }

    @Test
    void aRoundWithoutAnAuthoredGradingDeadlineNeverFreezesAWritableReport() throws Exception {
        // A KLTN row without gvpb_deadline predates the conditional-date
        // validator. The missing date must degrade safely: the lifecycle window
        // still bounds the report, and the report stays writable inside it.
        Instant now = Instant.now();
        UUID roundId = insertRound("rw-kltn-legacy-no-deadline", "KLTN", "REGISTRATION_CLOSED",
                now.minusSeconds(3_600), now.plusSeconds(30L * 24 * 60 * 60), null);
        UUID groupId = insertApprovedGroup(roundId, "rw-leader-legacy");

        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/legacy.pdf\"}")
                        .with(studentJwt("rw-leader-legacy")))
                .andExpect(status().isOk());
    }

    @Test
    void anAuthoredGradingDeadlineFreezesTheReportOnEveryRoundType() throws Exception {
        // A round that does carry a grading deadline is frozen by it whatever
        // its type: the date is authoritative data, not a type-dependent hint.
        Instant now = Instant.now();
        UUID roundId = insertRound("rw-mon-hoc-legacy-deadline", "MON_HOC", "REGISTRATION_CLOSED",
                now.minusSeconds(40L * 24 * 60 * 60), now.minusSeconds(2L * 24 * 60 * 60),
                now.minusSeconds(3_600));
        UUID groupId = insertApprovedGroup(roundId, "rw-leader-legacy-deadline");

        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/late.pdf\"}")
                        .with(studentJwt("rw-leader-legacy-deadline")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_DEADLINE_PASSED"));
    }

    // ---------- fixtures ----------

    private UUID insertRound(String name, String thesisType, String status,
                             Instant registrationStart, Instant registrationEnd, Instant gvpbDeadline) {
        UUID roundId = UUID.randomUUID();
        Instant lecturerEnd = registrationStart.minusSeconds(3_600);
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                roundId, name, thesisType,
                Timestamp.from(lecturerEnd.minusSeconds(3_600)), Timestamp.from(lecturerEnd),
                Timestamp.from(registrationStart), Timestamp.from(registrationEnd),
                gvpbDeadline == null ? null : Timestamp.from(gvpbDeadline),
                status);
        return roundId;
    }

    private UUID insertApprovedGroup(UUID roundId, String leaderStudentId) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', 'Report window topic', 'fixture', 1, 'PUBLISHED', ?)",
                topicId, roundId, UUID.randomUUID());
        UUID groupId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', 'APPROVED')",
                groupId, roundId, leaderStudentId, topicId);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 1, TRUE)",
                UUID.randomUUID(), groupId, roundId, leaderStudentId);
        return groupId;
    }

    private void insertReport(UUID groupId, UUID roundId, String submittedBy, String title, String url) {
        jdbc.update(
                "INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), groupId, roundId, submittedBy, title, url);
    }

    private int reportRows(UUID groupId) {
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_report WHERE group_id = ?", Integer.class, groupId);
        return rows == null ? 0 : rows;
    }

    private RequestPostProcessor studentJwt(String studentId) {
        return jwt().jwt(token -> token
                        .subject("rw-user-" + studentId)
                        .claim("roles", List.of("STUDENT"))
                        .claim("studentId", studentId))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }
}
