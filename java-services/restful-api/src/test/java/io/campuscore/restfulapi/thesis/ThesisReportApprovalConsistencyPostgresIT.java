package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.thesis.service.ThesisProgressService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Locks the report/approval invariant on seeded thesis groups: a group carrying a
 * retained report row can never sit at PENDING, because the progress timeline reads
 * the report as evidence after a missing GROUP_APPROVED milestone and reports the
 * chain as inconsistent. The scenario runs against a dedicated scratch database —
 * first migrated up to the last version before the repair so the broken state is
 * reproduced, then migrated to head so the repair can be verified. A control group
 * shaped like the anomaly but outside the seeded allow-list proves the repair never
 * rewrites real user groups.
 */
@EnabledIfEnvironmentVariable(named = "CAMPUSCORE_E2E_POSTGRES_UPGRADE_URL", matches = "jdbc:postgresql:.+")
class ThesisReportApprovalConsistencyPostgresIT {

    private static final String PRE_REPAIR_TARGET = "135";
    private static final String ROUND = "c8f1a234-9b7e-412d-8301-1b2c3d4e5f60";
    private static final String CANONICAL_GROUP = "f9a4d567-2e0b-445a-b634-4e5f6a7b8c93";
    private static final String SEEDED_GROUP_ONE = "250b2c11-ff07-4b1b-ac54-cb6ac3392903";
    private static final String SEEDED_GROUP_TWO = "165fa3b8-46cd-4225-aabe-a0e22315e5cd";
    private static final String DEAN_APPROVER = "truongkhoa-user-001";
    private static final UUID CONTROL_GROUP = UUID.fromString("9b1d0f4e-0f42-4b7f-9c0a-0d3f4f5a0001");
    private static final UUID DRIFT_REPORT = UUID.fromString("9b1d0f4e-0f42-4b7f-9c0a-0d3f4f5a0002");
    private static final UUID CONTROL_REPORT = UUID.fromString("9b1d0f4e-0f42-4b7f-9c0a-0d3f4f5a0003");

    @Test
    void reportBearingSeededGroupsAreApprovedAfterTheRepairMigration() throws Exception {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_UPGRADE_URL");
        String user = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_USER", "campuscore");
        String password = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_PASSWORD", "postgres");

        configured(url, user, password, PRE_REPAIR_TARGET).migrate();

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            // The seeded break before the repair: both groups carry their seeded
            // report rows while sitting at PENDING.
            assertThat(approval(connection, SEEDED_GROUP_ONE)).isEqualTo("PENDING");
            assertThat(approval(connection, SEEDED_GROUP_TWO)).isEqualTo("PENDING");
            assertThat(reportCount(connection, SEEDED_GROUP_ONE)).isEqualTo(1);
            assertThat(reportCount(connection, SEEDED_GROUP_TWO)).isEqualTo(1);
            assertThat(attention(url, user, password, "student-profile-006"))
                    .isEqualTo("PROGRESS_INCONSISTENT");
            assertThat(attention(url, user, password, "student-profile-012"))
                    .isEqualTo("PROGRESS_INCONSISTENT");

            // The canonical group is consistent on fresh migration data — the
            // drifted databases were the ones carrying a report it had gathered
            // while approved. Reproduce that shape here.
            assertThat(reportCount(connection, CANONICAL_GROUP)).isZero();
            insertReport(connection, DRIFT_REPORT, CANONICAL_GROUP, "student-profile");
            assertThat(attention(url, user, password, "student-profile"))
                    .isEqualTo("PROGRESS_INCONSISTENT");

            // Control: an unlisted group with the same anomaly shape must survive
            // the repair untouched — the migration only rewrites seeded demo rows.
            String controlLeader = freeStudent(connection);
            insertGroup(connection, CONTROL_GROUP, controlLeader);
            insertMember(connection, UUID.randomUUID(), CONTROL_GROUP, controlLeader, 1, true);
            insertMember(connection, UUID.randomUUID(), CONTROL_GROUP, freeStudent(connection), 2, false);
            insertMember(connection, UUID.randomUUID(), CONTROL_GROUP, freeStudent(connection), 3, false);
            insertReport(connection, CONTROL_REPORT, CONTROL_GROUP.toString(), controlLeader);
        }

        Flyway repaired = configured(url, user, password, null);
        repaired.migrate();
        assertThat(repaired.validateWithResult().validationSuccessful).isTrue();

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            for (String group : List.of(CANONICAL_GROUP, SEEDED_GROUP_ONE, SEEDED_GROUP_TWO)) {
                assertThat(approval(connection, group)).isEqualTo("APPROVED");
                assertThat(approver(connection, group)).isEqualTo(DEAN_APPROVER);
                assertThat(scalar(connection,
                        "SELECT rejection_reason FROM thesis.thesis_group WHERE id='" + group + "'"))
                        .isNull();
                assertThat(reportCount(connection, group)).isEqualTo(1);
            }
            assertThat(approval(connection, CONTROL_GROUP.toString())).isEqualTo("PENDING");
            assertThat(reportCount(connection, CONTROL_GROUP.toString())).isEqualTo(1);
            assertThat(attention(url, user, password, "student-profile")).isEqualTo("NONE");
            assertThat(attention(url, user, password, "student-profile-006")).isEqualTo("NONE");
            assertThat(attention(url, user, password, "student-profile-012")).isEqualTo("NONE");
        }

        // Re-running the head migration must stay a clean no-op.
        assertThat(repaired.migrate().migrationsExecuted).isZero();
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            assertThat(approval(connection, CONTROL_GROUP.toString())).isEqualTo("PENDING");
        }
    }

    private static Flyway configured(String url, String user, String password, String target) {
        var configuration = Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/migration").createSchemas(true).defaultSchema("thesis")
                .schemas("thesis").cleanDisabled(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private static String attention(String url, String user, String password, String studentId) {
        var dataSource = new DriverManagerDataSource(url, user, password);
        var service = new ThesisProgressService(new NamedParameterJdbcTemplate(dataSource));
        Jwt actor = Jwt.withTokenValue("it").header("alg", "none").claim("studentId", studentId).build();
        return service.get(UUID.fromString(ROUND), actor).attentionState();
    }

    private static String freeStudent(Connection connection) throws SQLException {
        return scalar(connection, "SELECT \"id\" FROM campuscore_auth.\"Student\" s"
                + " WHERE NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member m"
                + " WHERE m.student_id = s.\"id\" AND m.round_id = '" + ROUND + "'"
                + " AND m.active_participation) ORDER BY s.\"id\" LIMIT 1");
    }

    private static void insertGroup(Connection connection, UUID group, String leader) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO thesis.thesis_group (id,round_id,leader_student_id,status,approval_status)"
                        + " VALUES (?,?,?,'SUBMITTED','PENDING')")) {
            statement.setObject(1, group);
            statement.setObject(2, UUID.fromString(ROUND));
            statement.setString(3, leader);
            statement.executeUpdate();
        }
    }

    private static void insertMember(Connection connection, UUID member, UUID group,
            String student, int order, boolean leader) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO thesis.thesis_group_member (id,group_id,round_id,student_id,member_order,is_leader)"
                        + " VALUES (?,?,'" + ROUND + "',?,?,?)")) {
            statement.setObject(1, member);
            statement.setObject(2, group);
            statement.setString(3, student);
            statement.setInt(4, order);
            statement.setBoolean(5, leader);
            statement.executeUpdate();
        }
    }

    private static void insertReport(Connection connection, UUID report, String group, String submitter)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO thesis.thesis_group_report (id,group_id,round_id,submitted_by,title,url)"
                        + " VALUES (?,?,'" + ROUND + "',?,'Regression report','https://example.invalid/report.pdf')")) {
            statement.setObject(1, report);
            statement.setObject(2, UUID.fromString(group));
            statement.setString(3, submitter);
            statement.executeUpdate();
        }
    }

    private static String approval(Connection connection, String group) throws SQLException {
        return scalar(connection,
                "SELECT approval_status FROM thesis.thesis_group WHERE id='" + group + "'");
    }

    private static String approver(Connection connection, String group) throws SQLException {
        return scalar(connection,
                "SELECT approved_by FROM thesis.thesis_group WHERE id='" + group + "'");
    }

    private static int reportCount(Connection connection, String group) throws SQLException {
        return Integer.parseInt(scalar(connection,
                "SELECT COUNT(*)::text FROM thesis.thesis_group_report WHERE group_id='" + group + "'"));
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
