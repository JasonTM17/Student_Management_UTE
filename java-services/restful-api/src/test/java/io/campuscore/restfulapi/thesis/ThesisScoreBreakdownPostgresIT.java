package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.thesis.dto.ScoreBreakdownResponse;
import io.campuscore.restfulapi.thesis.service.ThesisCouncilService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Real-Postgres check for the score-breakdown read model: the named-parameter
 * IN-list inside the display-name resolver, the CASE seat ordering, the
 * correlated eligibility predicates, and the HALF_UP average must all resolve
 * identically on the production dialect. The scratch database is a throwaway
 * score1_ database migrated by Flyway, never a shared one.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisScoreBreakdownPostgresIT {

    @Autowired private ThesisCouncilService councils;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_SCBD_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_SCBD_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_SCBD_POSTGRES_PASSWORD", "postgres"));
    }

    static boolean postgresTargetConfigured() {
        return System.getenv("CAMPUSCORE_SCBD_POSTGRES_URL") != null;
    }

    private static String valueOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM thesis.thesis_topic_score WHERE lecturer_id LIKE 'scbd-%'");
        jdbc.update("DELETE FROM thesis.thesis_council_topic WHERE topic_id IN "
                + "(SELECT id FROM thesis.thesis_topic WHERE created_by LIKE 'scbd-%')");
        jdbc.update("DELETE FROM thesis.thesis_council_member WHERE lecturer_id LIKE 'scbd-%'");
        jdbc.update("DELETE FROM thesis.thesis_council WHERE created_by LIKE 'scbd-%'");
        jdbc.update("UPDATE thesis.thesis_group SET approval_status = 'PENDING' "
                + "WHERE leader_student_id LIKE 'scbd-%'");
        jdbc.update("DELETE FROM thesis.thesis_group_member WHERE student_id LIKE 'scbd-%'");
        jdbc.update("DELETE FROM thesis.thesis_group WHERE leader_student_id LIKE 'scbd-%'");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor WHERE lecturer_id LIKE 'scbd-%'");
        jdbc.update("DELETE FROM thesis.thesis_topic WHERE created_by LIKE 'scbd-%'");
        jdbc.update("DELETE FROM thesis.thesis_registration_round WHERE name LIKE 'scbd-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'scbd-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'scbd-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'scbd-%'");
    }

    @Test
    void breakdownOrdersSeatsAndAveragesExactlyAsFinalize() {
        UUID roundId = insertRound("REGISTRATION_CLOSED");
        UUID topicId = insertTopic(roundId);
        UUID councilId = insertCouncil(roundId);
        seat(councilId, "scbd-chair", "CHAIR");
        seat(councilId, "scbd-secretary", "SECRETARY");
        seat(councilId, "scbd-member", "MEMBER");
        seat(councilId, "scbd-late", "MEMBER");
        assignCouncilTopic(councilId, topicId);
        assignReviewer(topicId, "scbd-reviewer");
        // 7 + 8.5 + 9 averages to 8.17 under HALF_UP; the late member and the
        // reviewer stay pending, and the reviewer row never joins the average.
        scoreRow(councilId, topicId, "scbd-chair", "DEFENSE", "7", "chair note");
        scoreRow(councilId, topicId, "scbd-secretary", "DEFENSE", "8.5", null);
        scoreRow(councilId, topicId, "scbd-member", "DEFENSE", "9", null);
        scoreRow(null, topicId, "scbd-reviewer", "GVPB", "6", "reviewer note");

        ScoreBreakdownResponse breakdown = councils.scoreBreakdown(topicId, adminJwt());

        assertThat(breakdown.average()).isEqualByComparingTo(new BigDecimal("8.17"));
        assertThat(breakdown.graderCount()).isEqualTo(4);
        assertThat(breakdown.complete()).isFalse();
        assertThat(breakdown.graders()).extracting(ScoreBreakdownResponse.ScoreBreakdownGrader::role)
                .containsExactly("CHAIR", "SECRETARY", "MEMBER", "GVPB_REVIEWER");
        assertThat(breakdown.graders()).extracting(ScoreBreakdownResponse.ScoreBreakdownGrader::displayName)
                .containsExactly("Scbd Chair", "Scbd Secretary", "Scbd Member", "Scbd Reviewer");
        assertThat(breakdown.graders()).extracting(ScoreBreakdownResponse.ScoreBreakdownGrader::countedInAverage)
                .containsExactly(true, true, true, false);
        // The reviewer already filed, so only the late member is pending.
        assertThat(breakdown.pendingGraders())
                .extracting(ScoreBreakdownResponse.ScoreBreakdownPendingGrader::role)
                .containsExactly("MEMBER");
        assertThat(breakdown.pendingGraders())
                .extracting(ScoreBreakdownResponse.ScoreBreakdownPendingGrader::displayName)
                .containsExactly("Scbd Late");
    }

    @Test
    void displayNamesResolveForBothStoredLecturerKeyForms() {
        UUID roundId = insertRound("REGISTRATION_CLOSED");
        UUID topicId = insertTopic(roundId);
        UUID councilId = insertCouncil(roundId);
        seat(councilId, "scbd-chair", "CHAIR");
        seat(councilId, "scbd-secretary", "SECRETARY");
        seat(councilId, "scbd-member", "MEMBER");
        seat(councilId, "scbd-legacy", "MEMBER");
        assignCouncilTopic(councilId, topicId);
        // A legacy score row keyed by userId — member rows are FK'd to
        // Lecturer.id so the dual-form key only survives on the score side.
        String legacyUser = ensureLecturer("scbd-legacy");
        scoreRow(councilId, topicId, legacyUser, "DEFENSE", "8", null);

        ScoreBreakdownResponse breakdown = councils.scoreBreakdown(topicId, adminJwt());

        ScoreBreakdownResponse.ScoreBreakdownGrader stray = breakdown.graders().stream()
                .filter(grader -> legacyUser.equals(grader.lecturerId()))
                .findFirst().orElseThrow();
        assertThat(stray.displayName()).isEqualTo("Scbd Legacy");
        assertThat(stray.role()).isEqualTo("MEMBER");
        // The finalize clause joins member rows by Lecturer.id, so a
        // userId-keyed score stays visible but uncounted — and the seat it
        // belongs to correctly reads as still pending.
        assertThat(stray.countedInAverage()).isFalse();
        assertThat(breakdown.pendingGraders())
                .extracting(ScoreBreakdownResponse.ScoreBreakdownPendingGrader::displayName)
                .contains("Scbd Legacy");
    }

    @Test
    void participantStudentReadsOnlyNamesRolesAndAverageAfterPublish() {
        UUID roundId = insertRound("RESULTS_PUBLISHED");
        UUID topicId = insertTopic(roundId);
        UUID councilId = insertCouncil(roundId);
        seat(councilId, "scbd-chair", "CHAIR");
        seat(councilId, "scbd-secretary", "SECRETARY");
        seat(councilId, "scbd-member", "MEMBER");
        assignCouncilTopic(councilId, topicId);
        scoreRow(councilId, topicId, "scbd-chair", "DEFENSE", "7", "chair note");
        scoreRow(councilId, topicId, "scbd-secretary", "DEFENSE", "8.5", null);
        scoreRow(councilId, topicId, "scbd-member", "DEFENSE", "9", null);
        insertApprovedGroup(roundId, topicId);
        jdbc.update("UPDATE thesis.thesis_topic SET final_score = 8.17, "
                + "final_score_finalized_by = 'scbd-user-scbd-chair', "
                + "final_score_finalized_at = CURRENT_TIMESTAMP, result_status = 'GRADED' "
                + "WHERE id = ?", topicId);

        ScoreBreakdownResponse breakdown = councils.scoreBreakdown(topicId, studentJwt("scbd-student-1"));

        assertThat(breakdown.average()).isEqualByComparingTo(new BigDecimal("8.17"));
        assertThat(breakdown.finalScore()).isEqualByComparingTo(new BigDecimal("8.17"));
        assertThat(breakdown.finalizedBy()).isNull();
        assertThat(breakdown.pendingGraders()).isEmpty();
        assertThat(breakdown.graders()).hasSize(3);
        for (ScoreBreakdownResponse.ScoreBreakdownGrader grader : breakdown.graders()) {
            assertThat(grader.displayName()).isNotBlank();
            assertThat(grader.score()).isNull();
            assertThat(grader.comment()).isNull();
            assertThat(grader.scoredAt()).isNull();
        }
    }

    // ---------- fixtures ----------

    private Jwt adminJwt() {
        return Jwt.withTokenValue("scbd-fixture").header("alg", "none")
                .subject("scbd-user-admin")
                .claim("roles", List.of("ADMIN")).build();
    }

    private Jwt studentJwt(String studentId) {
        return Jwt.withTokenValue("scbd-fixture").header("alg", "none")
                .subject("scbd-user-" + studentId)
                .claim("roles", List.of("STUDENT"))
                .claim("studentId", studentId).build();
    }

    private UUID insertRound(String status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?)",
                id, "scbd-round-" + status,
                Timestamp.from(now.minusSeconds(10 * 86_400)), Timestamp.from(now.minusSeconds(9 * 86_400)),
                Timestamp.from(now.minusSeconds(8 * 86_400)), Timestamp.from(now.minusSeconds(7 * 86_400)),
                Timestamp.from(now.plusSeconds(86_400)), status);
        return id;
    }

    private UUID insertTopic(UUID roundId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', 'Scbd Topic', 'breakdown fixture', 2, 'PUBLISHED', 'scbd-admin')",
                id, roundId);
        return id;
    }

    private UUID insertCouncil(UUID roundId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by) "
                        + "VALUES (?, ?, 'Scbd Council', 'ACTIVE', 'scbd-admin')",
                id, roundId);
        return id;
    }

    private void seat(UUID councilId, String lecturerId, String role) {
        ensureLecturer(lecturerId);
        jdbc.update("INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role) "
                        + "VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), councilId, lecturerId, role);
    }

    private void assignCouncilTopic(UUID councilId, UUID topicId) {
        jdbc.update("INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by) "
                        + "VALUES (?, ?, ?, 'scbd-admin')",
                UUID.randomUUID(), councilId, topicId);
    }

    private void assignReviewer(UUID topicId, String lecturerId) {
        ensureLecturer(lecturerId);
        jdbc.update("UPDATE thesis.thesis_topic SET gvpb_lecturer_id = ? WHERE id = ?", lecturerId, topicId);
    }

    private void scoreRow(UUID councilId, UUID topicId, String lecturerId,
            String component, String score, String comment) {
        jdbc.update("INSERT INTO thesis.thesis_topic_score "
                        + "(id, topic_id, council_id, lecturer_id, component, score, comment) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), topicId, councilId, lecturerId, component,
                new BigDecimal(score), comment);
    }

    private void insertApprovedGroup(UUID roundId, UUID topicId) {
        UUID groupId = UUID.randomUUID();
        // The membership-state trigger rejects an APPROVED group with fewer
        // than three members, so the roster lands before the approval flip.
        jdbc.update("INSERT INTO thesis.thesis_group "
                        + "(id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', 'PENDING')",
                groupId, roundId, "scbd-student-1", topicId);
        for (int order = 1; order <= 3; order++) {
            ensureStudent("scbd-student-" + order);
            jdbc.update("INSERT INTO thesis.thesis_group_member "
                            + "(id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), groupId, roundId, "scbd-student-" + order, order, order == 1);
        }
        jdbc.update("UPDATE thesis.thesis_group SET approval_status = 'APPROVED' WHERE id = ?", groupId);
    }

    /** Lecturer profile plus its bound user id; returns the user id. */
    private String ensureLecturer(String lecturerId) {
        String userId = "scbd-user-" + lecturerId.substring(5);
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", "
                        + "\"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'it-password', ?, 'Scbd', 'ACTIVE', FALSE, FALSE, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (\"id\") DO NOTHING",
                userId, lecturerId + "@it.fixture",
                lecturerId.substring(5, 6).toUpperCase(java.util.Locale.ROOT) + lecturerId.substring(6));
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                        + "VALUES (?, ?, 'department-demo', ?, TRUE) ON CONFLICT (\"id\") DO NOTHING",
                lecturerId, userId, "SCBD-" + lecturerId);
        return userId;
    }

    private void ensureStudent(String studentId) {
        String userId = "scbd-user-" + studentId.substring(5);
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", "
                        + "\"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'it-password', 'Break', 'Student', 'ACTIVE', FALSE, FALSE, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (\"id\") DO NOTHING",
                userId, studentId + "@it.fixture");
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 4, CURRENT_TIMESTAMP) ON CONFLICT (\"id\") DO NOTHING",
                studentId, userId, "SCBD-" + studentId);
    }
}
