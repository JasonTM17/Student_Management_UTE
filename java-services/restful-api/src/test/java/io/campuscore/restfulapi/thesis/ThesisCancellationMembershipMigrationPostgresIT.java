package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Exercises the forward-only upgrade against a dedicated database. It never cleans or drops a
 * schema: V90's seeded member is turned into preserved cancelled history before V91 is applied.
 */
@TestMethodOrder(OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "CAMPUSCORE_E2E_POSTGRES_UPGRADE_URL", matches = "jdbc:postgresql:.+")
class ThesisCancellationMembershipMigrationPostgresIT {
    private static final UUID LEGACY_GROUP = UUID.fromString("22222222-2222-2222-2222-222222222301");
    private static final UUID LEGACY_MEMBER = UUID.fromString("22222222-2222-2222-2222-222222222302");

    @Test
    @Order(1)
    void v91PreservesCancelledMembershipHistoryAndReleasesOnlyTheActiveSeat() throws Exception {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_UPGRADE_URL");
        String user = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_USER", "campuscore");
        String password = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_PASSWORD", "postgres");

        Flyway toV90 = configured(url, user, password, "90");
        toV90.migrate();
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            if ("90".equals(currentVersion(connection))) {
                UUID round = UUID.fromString(scalar(connection,
                        "SELECT id::text FROM thesis.thesis_registration_round ORDER BY id LIMIT 1"));
                String student = scalar(connection,
                        "SELECT \"id\" FROM campuscore_auth.\"Student\" ORDER BY \"id\" LIMIT 1");
                insertGroup(connection, LEGACY_GROUP, round, student, "V90 cancelled membership history");
                insertMember(connection, LEGACY_MEMBER, LEGACY_GROUP, round, student);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE thesis.thesis_group SET status='CANCELLED' WHERE id=?")) {
                    statement.setObject(1, LEGACY_GROUP);
                    assertThat(statement.executeUpdate()).isEqualTo(1);
                }
            }
        }

        Flyway toV91 = configured(url, user, password, null);
        toV91.migrate();
        assertThat(toV91.validateWithResult().validationSuccessful).isTrue();

        UUID replacementGroup = UUID.randomUUID();
        UUID replacementMember = UUID.randomUUID();
        UUID conflictingGroup = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            assertThat(currentVersion(connection)).isEqualTo("91");
            assertThat(scalar(connection,
                    "SELECT active_participation::text FROM thesis.thesis_group_member WHERE id='" + LEGACY_MEMBER + "'"))
                    .isEqualTo("false");
            assertThat(scalar(connection,
                    "SELECT COUNT(*)::text FROM thesis.thesis_group_member WHERE id='" + LEGACY_MEMBER + "'"))
                    .isEqualTo("1");

            UUID round = UUID.fromString(scalar(connection,
                    "SELECT round_id::text FROM thesis.thesis_group WHERE id='" + LEGACY_GROUP + "'"));
            String student = scalar(connection,
                    "SELECT leader_student_id FROM thesis.thesis_group WHERE id='" + LEGACY_GROUP + "'");
            insertGroup(connection, replacementGroup, round, student, "V91 replacement group");
            insertMember(connection, replacementMember, replacementGroup, round, student);
            assertThat(scalar(connection,
                    "SELECT active_participation::text FROM thesis.thesis_group_member WHERE id='" + replacementMember + "'"))
                    .isEqualTo("true");

            insertGroup(connection, conflictingGroup, round, student, "V91 conflicting active group");
            assertThatThrownBy(() -> insertMember(connection, UUID.randomUUID(), conflictingGroup, round, student))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23505"));
            assertThatThrownBy(() -> execute(connection,
                    "UPDATE thesis.thesis_group SET status='DRAFT' WHERE id='" + LEGACY_GROUP + "'"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
            String otherStudent = scalar(connection, "SELECT \"id\" FROM campuscore_auth.\"Student\" WHERE \"id\" <> '" + student + "' AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member m WHERE m.student_id=\"Student\".\"id\" AND m.round_id='" + round + "' AND m.active_participation) LIMIT 1");
            UUID escapeGroup = UUID.randomUUID();
            insertGroup(connection, escapeGroup, round, otherStudent, "Historical row escape probe");
            assertThatThrownBy(() -> execute(connection,
                    "UPDATE thesis.thesis_group_member SET group_id='" + escapeGroup + "', student_id='" + otherStudent + "' WHERE id='" + LEGACY_MEMBER + "'"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
            assertThat(scalar(connection, "SELECT group_id::text FROM thesis.thesis_group_member WHERE id='" + LEGACY_MEMBER + "'"))
                    .isEqualTo(LEGACY_GROUP.toString());
            assertThatThrownBy(() -> execute(connection,
                    "DELETE FROM thesis.thesis_group_member WHERE id='" + LEGACY_MEMBER + "'"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
        }
    }

    @Test
    @Order(2)
    void cancellationWaitsForAnUncommittedMemberThenReleasesEverySeat() throws Exception {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_UPGRADE_URL");
        String user = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_USER", "campuscore");
        String password = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_PASSWORD", "postgres");
        UUID group = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        try (Connection setup = DriverManager.getConnection(url, user, password);
             Connection insertion = DriverManager.getConnection(url, user, password);
             Connection cancellation = DriverManager.getConnection(url, user, password)) {
            UUID round = UUID.fromString(scalar(setup, "SELECT round_id::text FROM thesis.thesis_group WHERE id='" + LEGACY_GROUP + "'"));
            String leader = scalar(setup, "SELECT \"id\" FROM campuscore_auth.\"Student\" WHERE NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member m WHERE m.student_id=\"Student\".\"id\" AND m.round_id='" + round + "' AND m.active_participation) ORDER BY \"id\" LIMIT 1");
            String joiningStudent = scalar(setup, "SELECT \"id\" FROM campuscore_auth.\"Student\" WHERE \"id\" <> '" + leader + "' AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member m WHERE m.student_id=\"Student\".\"id\" AND m.round_id='" + round + "' AND m.active_participation) ORDER BY \"id\" LIMIT 1");
            insertGroup(setup, group, round, leader, "Member insert/cancel race");
            insertMember(setup, UUID.randomUUID(), group, round, leader);
            insertion.setAutoCommit(false);
            execute(insertion, "INSERT INTO thesis.thesis_group_member(id,group_id,round_id,student_id,member_order,is_leader) VALUES('" + member + "','" + group + "','" + round + "','" + joiningStudent + "',2,FALSE)");
            String insertionPid = scalar(insertion, "SELECT pg_backend_pid()::text");
            String cancellationPid = scalar(cancellation, "SELECT pg_backend_pid()::text");
            var executor = Executors.newSingleThreadExecutor();
            try {
                var cancelling = executor.submit(() -> { execute(cancellation, "UPDATE thesis.thesis_group SET status='CANCELLED' WHERE id='" + group + "'"); return true; });
                boolean blocked = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (System.nanoTime() < deadline && !cancelling.isDone()) {
                    blocked = "1".equals(scalar(setup, "SELECT (" + insertionPid + " = ANY(pg_blocking_pids(" + cancellationPid + ")))::int::text"));
                    if (blocked) break;
                    Thread.sleep(25);
                }
                assertThat(blocked).as("parent cancellation waits for the uncommitted member's parent UPDATE lock").isTrue();
                insertion.commit();
                assertThat(cancelling.get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(scalar(setup, "SELECT COUNT(*)::text FROM thesis.thesis_group_member WHERE group_id='" + group + "' AND active_participation"))
                        .isEqualTo("0");
                assertThat(scalar(setup, "SELECT COUNT(*)::text FROM thesis.thesis_group_member WHERE group_id='" + group + "'"))
                        .isEqualTo("2");
            } finally {
                insertion.rollback();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    @Order(3)
    void concurrentMemberInsertsSerializeBeforeDeferredRosterValidation() throws Exception {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_UPGRADE_URL");
        String user = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_USER", "campuscore");
        String password = valueOr("CAMPUSCORE_E2E_POSTGRES_UPGRADE_PASSWORD", "postgres");
        UUID group = UUID.randomUUID();
        try (Connection setup = DriverManager.getConnection(url, user, password);
             Connection first = DriverManager.getConnection(url, user, password);
             Connection second = DriverManager.getConnection(url, user, password)) {
            UUID round = UUID.fromString(scalar(setup, "SELECT round_id::text FROM thesis.thesis_group WHERE id='" + LEGACY_GROUP + "'"));
            java.util.List<String> students = new java.util.ArrayList<>();
            try (var statement = setup.createStatement(); var rows = statement.executeQuery("SELECT \"id\" FROM campuscore_auth.\"Student\" WHERE NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member m WHERE m.student_id=\"Student\".\"id\" AND m.round_id='" + round + "' AND m.active_participation) ORDER BY \"id\" LIMIT 3")) {
                while (rows.next()) students.add(rows.getString(1));
            }
            assertThat(students).hasSize(3);
            insertGroup(setup, group, round, students.get(0), "Concurrent direct inserts");
            insertMember(setup, UUID.randomUUID(), group, round, students.get(0));
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            execute(first, "INSERT INTO thesis.thesis_group_member(id,group_id,round_id,student_id,member_order,is_leader) VALUES('" + UUID.randomUUID() + "','" + group + "','" + round + "','" + students.get(1) + "',2,FALSE)");
            String firstPid = scalar(first, "SELECT pg_backend_pid()::text");
            String secondPid = scalar(second, "SELECT pg_backend_pid()::text");
            var executor = Executors.newSingleThreadExecutor();
            try {
                var adding = executor.submit(() -> { execute(second, "INSERT INTO thesis.thesis_group_member(id,group_id,round_id,student_id,member_order,is_leader) VALUES('" + UUID.randomUUID() + "','" + group + "','" + round + "','" + students.get(2) + "',3,FALSE)"); second.commit(); return true; });
                boolean blocked = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (System.nanoTime() < deadline && !adding.isDone()) {
                    blocked = "1".equals(scalar(setup, "SELECT (" + firstPid + " = ANY(pg_blocking_pids(" + secondPid + ")))::int::text"));
                    if (blocked) break;
                    Thread.sleep(25);
                }
                assertThat(blocked).as("second member waits before holding a parent lock that would need an upgrade").isTrue();
                first.commit();
                assertThat(adding.get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(scalar(setup, "SELECT COUNT(*)::text FROM thesis.thesis_group_member WHERE group_id='" + group + "' AND active_participation"))
                        .isEqualTo("3");
            } finally {
                first.rollback();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                second.rollback();
            }
        }
    }

    private static Flyway configured(String url, String user, String password, String target) {
        var configuration = Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/migration").createSchemas(true).defaultSchema("thesis")
                .schemas("thesis").cleanDisabled(true);
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        return configuration.load();
    }

    private static void insertGroup(Connection connection, UUID group, UUID round, String student, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO thesis.thesis_group (id,round_id,leader_student_id,status,approval_status) VALUES (?,?,?,'DRAFT','PENDING')")) {
            statement.setObject(1, group); statement.setObject(2, round); statement.setString(3, student);
            statement.executeUpdate();
        }
    }

    private static void insertMember(Connection connection, UUID member, UUID group, UUID round, String student) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO thesis.thesis_group_member (id,group_id,round_id,student_id,member_order,is_leader) VALUES (?,?,?,?,1,TRUE)")) {
            statement.setObject(1, member); statement.setObject(2, group); statement.setObject(3, round); statement.setString(4, student);
            statement.executeUpdate();
        }
    }

    private static String currentVersion(Connection connection) throws SQLException {
        return scalar(connection, "SELECT version FROM thesis.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1");
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.executeUpdate(sql); }
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name); return value == null || value.isBlank() ? fallback : value;
    }
}
