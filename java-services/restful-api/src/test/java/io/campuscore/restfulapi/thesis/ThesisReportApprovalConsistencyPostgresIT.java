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
 * chain as inconsistent. Each scenario runs against a dedicated scratch database —
 * first migrated up to the last version before the repair so the broken state is
 * reproduced, then migrated to head so the repair can be verified. Because the
 * repair only rewrites three fixed seeded group ids, the negative cases mutate the
 * seeded rows themselves so each guard clause is exercised in isolation; separate
 * scratch databases keep the mutations from overlapping.
 */
@EnabledIfEnvironmentVariable(named = "CAMPUSCORE_E2E_POSTGRES_UPGRADE_URL", matches = "jdbc:postgresql:.+")
class ThesisReportApprovalConsistencyPostgresIT {

    private static final String PRE_REPAIR_TARGET = "135";
    private static final String ROUND = "c8f1a234-9b7e-412d-8301-1b2c3d4e5f60";
    private static final String CANONICAL_GROUP = "f9a4d567-2e0b-445a-b634-4e5f6a7b8c93";
    private static final String SEEDED_GROUP_ONE = "250b2c11-ff07-4b1b-ac54-cb6ac3392903";
    private static final String SEEDED_GROUP_TWO = "165fa3b8-46cd-4225-aabe-a0e22315e5cd";
    private static final String SEEDED_GROUP_TWO_TOPIC = "1dd3b963-26ad-46c6-a56f-df0e1690255f";
    private static final String SEEDED_GROUP_ONE_TOPIC = "d947b331-517b-44b3-adb9-6dc6d9f01777";
    private static final String DEAN_APPROVER = "truongkhoa-user-001";
    private static final UUID CONTROL_GROUP = UUID.fromString("9b1d0f4e-0f42-4b7f-9c0a-0d3f4f5a0001");
    private static final UUID DRIFT_REPORT = UUID.fromString("9b1d0f4e-0f42-4b7f-9c0a-0d3f4f5a0002");
    private static final UUID CONTROL_REPORT = UUID.fromString("9b1d0f4e-0f42-4b7f-9c0a-0d3f4f5a0003");

    @Test
    void reportBearingSeededGroupsAreApprovedAfterTheRepairMigration() throws Exception {
        String url = scratchDatabase("_repair");
        String user = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_USER", "campuscore");
        String password = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_PASSWORD", "postgres");

        configured(url, user, password, PRE_REPAIR_TARGET).migrate();

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            // The seeded break before the repair: both groups carry their seeded
            // report rows while sitting at PENDING.
            assertThat(approval(connection, SEEDED_GROUP_ONE)).isEqualTo("PENDING");
            assertThat(reportCount(connection, SEEDED_GROUP_ONE)).isEqualTo(1);
            assertThat(attention(url, user, password, "student-profile-006"))
                    .isEqualTo("PROGRESS_INCONSISTENT");

            // The canonical group is consistent on fresh migration data — the
            // drifted databases were the ones carrying a report it had gathered
            // while approved. Reproduce that shape here.
            assertThat(reportCount(connection, CANONICAL_GROUP)).isZero();
            insertReport(connection, DRIFT_REPORT, CANONICAL_GROUP, "student-profile");
            assertThat(attention(url, user, password, "student-profile"))
                    .isEqualTo("PROGRESS_INCONSISTENT");

            // An already-approved seeded group must keep its recorded approver;
            // the repair only touches PENDING rows.
            execute(connection, "UPDATE thesis.thesis_group SET approval_status='APPROVED',"
                    + " approved_by='lecturer-user-022', approved_at=CURRENT_TIMESTAMP"
                    + " WHERE id='" + SEEDED_GROUP_TWO + "'");
            String preservedVersion = scalar(connection,
                    "SELECT version::text FROM thesis.thesis_group WHERE id='" + SEEDED_GROUP_TWO + "'");

            // Control: an unlisted group with the same anomaly shape must survive
            // the repair untouched — the migration only rewrites seeded demo rows.
            String controlLeader = freeStudent(connection);
            insertGroup(connection, CONTROL_GROUP, controlLeader);
            insertMember(connection, UUID.randomUUID(), CONTROL_GROUP, controlLeader, 1, true);
            insertMember(connection, UUID.randomUUID(), CONTROL_GROUP, freeStudent(connection), 2, false);
            insertMember(connection, UUID.randomUUID(), CONTROL_GROUP, freeStudent(connection), 3, false);
            insertReport(connection, CONTROL_REPORT, CONTROL_GROUP.toString(), controlLeader);

            Flyway repaired = configured(url, user, password, null);
            repaired.migrate();
            assertThat(repaired.validateWithResult().validationSuccessful).isTrue();

            for (String group : List.of(CANONICAL_GROUP, SEEDED_GROUP_ONE)) {
                assertThat(approval(connection, group)).isEqualTo("APPROVED");
                assertThat(approver(connection, group)).isEqualTo(DEAN_APPROVER);
                assertThat(scalar(connection,
                        "SELECT rejection_reason FROM thesis.thesis_group WHERE id='" + group + "'"))
                        .isNull();
                assertThat(reportCount(connection, group)).isEqualTo(1);
            }
            assertThat(approval(connection, SEEDED_GROUP_TWO)).isEqualTo("APPROVED");
            assertThat(approver(connection, SEEDED_GROUP_TWO)).isEqualTo("lecturer-user-022");
            assertThat(scalar(connection,
                    "SELECT version::text FROM thesis.thesis_group WHERE id='" + SEEDED_GROUP_TWO + "'"))
                    .isEqualTo(preservedVersion);
            assertThat(approval(connection, CONTROL_GROUP.toString())).isEqualTo("PENDING");
            assertThat(reportCount(connection, CONTROL_GROUP.toString())).isEqualTo(1);
            assertThat(attention(url, user, password, "student-profile")).isEqualTo("NONE");
            assertThat(attention(url, user, password, "student-profile-006")).isEqualTo("NONE");

            // Re-running the head migration must stay a clean no-op.
            assertThat(repaired.migrate().migrationsExecuted).isZero();
            assertThat(approval(connection, CONTROL_GROUP.toString())).isEqualTo("PENDING");
        }
    }

    @Test
    void seededGroupsViolatingApprovalPreconditionsStayPending() throws Exception {
        String url = scratchDatabase("_precon");
        String user = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_USER", "campuscore");
        String password = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_PASSWORD", "postgres");

        configured(url, user, password, PRE_REPAIR_TARGET).migrate();

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            // A pending group with a report but no topic: approveGroup requires
            // a submitted topic, so the repair must leave it alone.
            insertReport(connection, DRIFT_REPORT, CANONICAL_GROUP, "student-profile");
            execute(connection,
                    "UPDATE thesis.thesis_group SET topic_id=NULL WHERE id='" + CANONICAL_GROUP + "'");

            // A pending group whose topic already carries a finalized score:
            // approving it would attach a grade the group never defended for.
            execute(connection, "UPDATE thesis.thesis_topic SET final_score=8.5, result_status='GRADED'"
                    + " WHERE id='" + SEEDED_GROUP_ONE_TOPIC + "'");

            // A pending group sitting in a round that no longer accepts
            // approval decisions (moved wholesale so member/round consistency
            // still holds at commit).
            String closedRound = scalar(connection,
                    "SELECT r.id::text FROM thesis.thesis_registration_round r"
                            + " WHERE r.status NOT IN ('PROPOSALS_PUBLISHED','REGISTRATION_OPEN','REGISTRATION_CLOSED')"
                            + " AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member m"
                            + " WHERE m.round_id = r.id AND m.student_id IN"
                            + " (SELECT student_id FROM thesis.thesis_group_member WHERE group_id='"
                            + SEEDED_GROUP_TWO + "')) ORDER BY r.id LIMIT 1");
            assertThat(closedRound).isNotNull();
            connection.setAutoCommit(false);
            execute(connection, "UPDATE thesis.thesis_group SET round_id='" + closedRound
                    + "' WHERE id='" + SEEDED_GROUP_TWO + "'");
            execute(connection, "UPDATE thesis.thesis_group_member SET round_id='" + closedRound
                    + "' WHERE group_id='" + SEEDED_GROUP_TWO + "'");
            execute(connection, "UPDATE thesis.thesis_group_report SET round_id='" + closedRound
                    + "' WHERE group_id='" + SEEDED_GROUP_TWO + "'");
            connection.commit();
            connection.setAutoCommit(true);

            Flyway repaired = configured(url, user, password, null);
            repaired.migrate();
            assertThat(repaired.validateWithResult().validationSuccessful).isTrue();

            for (String group : List.of(CANONICAL_GROUP, SEEDED_GROUP_ONE, SEEDED_GROUP_TWO)) {
                assertThat(approval(connection, group)).isEqualTo("PENDING");
                assertThat(approver(connection, group)).isNull();
                assertThat(reportCount(connection, group)).isEqualTo(1);
            }
            assertThat(scalar(connection,
                    "SELECT topic_id::text FROM thesis.thesis_group WHERE id='" + CANONICAL_GROUP + "'"))
                    .isNull();
            assertThat(scalar(connection,
                    "SELECT round_id::text FROM thesis.thesis_group WHERE id='" + SEEDED_GROUP_TWO + "'"))
                    .isEqualTo(closedRound);
        }
    }

    @Test
    void seededGroupsFailingRosterOrDecisionStateStayUnchanged() throws Exception {
        String url = scratchDatabase("_roster");
        String user = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_USER", "campuscore");
        String password = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_PASSWORD", "postgres");

        configured(url, user, password, PRE_REPAIR_TARGET).migrate();

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            // A rejected seeded group with a report: only PENDING rows may be
            // repaired, and the recorded decision must be preserved.
            insertReport(connection, DRIFT_REPORT, CANONICAL_GROUP, "student-profile");
            execute(connection, "UPDATE thesis.thesis_group SET approval_status='REJECTED',"
                    + " rejection_reason='fixture rejection' WHERE id='" + CANONICAL_GROUP + "'");

            // A pending seeded group whose roster shrank to two: approving it
            // would violate the three-to-five member contract.
            execute(connection, "DELETE FROM thesis.thesis_group_member WHERE group_id='"
                    + SEEDED_GROUP_ONE + "' AND member_order=3");

            // A pending seeded group carrying six member rows — unreachable
            // through normal writes because the order check and per-group order
            // uniqueness cap the roster at five, so the fixture lifts both and
            // bypasses the validator to reproduce corrupt legacy data.
            execute(connection, "SET session_replication_role = 'replica'");
            execute(connection, "ALTER TABLE thesis.thesis_group_member"
                    + " DROP CONSTRAINT thesis_group_member_order_valid");
            execute(connection, "DROP INDEX thesis.thesis_group_member_order_unique");
            for (int order = 4; order <= 6; order++) {
                execute(connection, "INSERT INTO thesis.thesis_group_member"
                        + " (id,group_id,round_id,student_id,member_order,is_leader,active_participation)"
                        + " VALUES (gen_random_uuid(),'" + SEEDED_GROUP_TWO + "','" + ROUND + "','"
                        + freeStudent(connection) + "'," + order + ",FALSE,TRUE)");
            }
            execute(connection, "SET session_replication_role = 'origin'");

            Flyway repaired = configured(url, user, password, null);
            repaired.migrate();
            assertThat(repaired.validateWithResult().validationSuccessful).isTrue();

            assertThat(approval(connection, CANONICAL_GROUP)).isEqualTo("REJECTED");
            assertThat(scalar(connection,
                    "SELECT rejection_reason FROM thesis.thesis_group WHERE id='" + CANONICAL_GROUP + "'"))
                    .isEqualTo("fixture rejection");
            assertThat(approval(connection, SEEDED_GROUP_ONE)).isEqualTo("PENDING");
            assertThat(memberCount(connection, SEEDED_GROUP_ONE)).isEqualTo(2);
            assertThat(approval(connection, SEEDED_GROUP_TWO)).isEqualTo("PENDING");
            assertThat(memberCount(connection, SEEDED_GROUP_TWO)).isEqualTo(6);
            for (String group : List.of(CANONICAL_GROUP, SEEDED_GROUP_ONE, SEEDED_GROUP_TWO)) {
                assertThat(approver(connection, group)).isNull();
            }
        }
    }

    @Test
    void legacyExceptionSeededGroupStaysReadOnlyWhileSiblingsRepair() throws Exception {
        String url = scratchDatabase("_legacy");
        String user = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_USER", "campuscore");
        String password = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_PASSWORD", "postgres");

        configured(url, user, password, PRE_REPAIR_TARGET).migrate();

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            // The legacy marker is immutable once V113's guard trigger exists,
            // so flag the canonical group with trigger enforcement suspended —
            // the point is that a flagged report-bearing row must be skipped
            // without failing the migration, not that the flag is reachable.
            insertReport(connection, DRIFT_REPORT, CANONICAL_GROUP, "student-profile");
            execute(connection, "SET session_replication_role = 'replica'");
            execute(connection, "UPDATE thesis.thesis_group SET legacy_group_size_exception=TRUE"
                    + " WHERE id='" + CANONICAL_GROUP + "'");
            execute(connection, "SET session_replication_role = 'origin'");

            Flyway repaired = configured(url, user, password, null);
            repaired.migrate();
            assertThat(repaired.validateWithResult().validationSuccessful).isTrue();

            assertThat(approval(connection, CANONICAL_GROUP)).isEqualTo("PENDING");
            assertThat(approver(connection, CANONICAL_GROUP)).isNull();
            assertThat(scalar(connection,
                    "SELECT legacy_group_size_exception::text FROM thesis.thesis_group WHERE id='"
                            + CANONICAL_GROUP + "'")).isEqualTo("true");
            for (String group : List.of(SEEDED_GROUP_ONE, SEEDED_GROUP_TWO)) {
                assertThat(approval(connection, group)).isEqualTo("APPROVED");
                assertThat(approver(connection, group)).isEqualTo(DEAN_APPROVER);
            }
        }
    }

    /**
     * Provisions an additional empty database next to the configured scratch
     * database so each scenario migrates from a clean slate. The derived name
     * is dropped first, so reruns stay deterministic.
     */
    private static String scratchDatabase(String suffix) throws SQLException {
        String baseUrl = System.getenv("CAMPUSCORE_E2E_POSTGRES_UPGRADE_URL");
        String user = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_USER", "campuscore");
        String password = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_PASSWORD", "postgres");
        int slash = baseUrl.lastIndexOf('/');
        String prefix = baseUrl.substring(0, slash + 1);
        String name = baseUrl.substring(slash + 1) + suffix;
        try (Connection admin = DriverManager.getConnection(prefix + "postgres", user, password);
             var statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS \"" + name + "\"");
            statement.execute("CREATE DATABASE \"" + name + "\"");
        }
        return prefix + name;
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

    private static int memberCount(Connection connection, String group) throws SQLException {
        return Integer.parseInt(scalar(connection,
                "SELECT COUNT(*)::text FROM thesis.thesis_group_member WHERE group_id='" + group + "'"));
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
