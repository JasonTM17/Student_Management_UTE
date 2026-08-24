package io.campuscore.restfulapi.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * PostgreSQL is the authority for partial-index and row-lock behaviour. These
 * tests intentionally require an explicitly supplied, isolated JDBC URL and
 * clean up only rows created by the current test run.
 */
@EnabledIfEnvironmentVariable(named = "REGISTRATION_POSTGRES_URL", matches = "jdbc:postgresql:.+")
@SpringBootTest(properties = {
        "deepseek.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.locations=classpath:db/migration"
})
@ActiveProfiles({"test", "persistence"})
class RegistrationPostgresConcurrencyIT {
    private static final List<String> ACTIVE_STATUSES = List.of("ACTIVE", "ENROLLED", "PENDING", "CONFIRMED");

    @Autowired
    private DataSource dataSource;

    private final List<Fixture> fixtures = new ArrayList<>();
    private final List<String> createdStudentIds = new ArrayList<>();
    private final List<String> createdUserIds = new ArrayList<>();

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("REGISTRATION_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("REGISTRATION_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("REGISTRATION_POSTGRES_PASSWORD", "postgres"));
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @AfterEach
    void cleanupFixtures() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (Fixture fixture : fixtures) {
                    deleteFixture(connection, fixture);
                }
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } finally {
            fixtures.clear();
            createdStudentIds.clear();
            createdUserIds.clear();
        }
    }

    @Test
    void partialUniqueIndexRejectsEveryCapacityBearingStatus() throws Exception {
        Fixture fixture = fixture("unique", 100);
        fixtures.add(fixture);

        for (String incumbentStatus : ACTIVE_STATUSES) {
            String studentId = id("student-" + incumbentStatus.toLowerCase());
            insertStudent(dataSource, studentId);
            String incumbentId = id("incumbent-" + incumbentStatus.toLowerCase());
            insertEnrollment(dataSource, incumbentId, studentId, fixture.sectionId(), incumbentStatus);

            String duplicateId = id("duplicate-" + incumbentStatus.toLowerCase());
            SQLException violation = assertThrowsSqlState(() ->
                    insertEnrollment(dataSource, duplicateId, studentId, fixture.sectionId(), "ACTIVE"));
            assertEquals("23505", violation.getSQLState(),
                    "status " + incumbentStatus + " must participate in active-enrollment uniqueness");
        }
    }

    @Test
    void twoConcurrentSeatClaimsHaveExactlyOneWinnerAndNoCountDrift() throws Exception {
        Fixture fixture = fixture("capacity", 1);
        fixtures.add(fixture);
        String firstStudent = id("student-first");
        String secondStudent = id("student-second");
        insertStudent(dataSource, firstStudent);
        insertStudent(dataSource, secondStudent);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> claimSeat(fixture.sectionId(), firstStudent, ready, start));
            Future<Boolean> second = pool.submit(() -> claimSeat(fixture.sectionId(), secondStudent, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "workers did not reach the deterministic barrier");
            start.countDown();

            boolean firstWon = first.get(15, TimeUnit.SECONDS);
            boolean secondWon = second.get(15, TimeUnit.SECONDS);
            assertEquals(1, (firstWon ? 1 : 0) + (secondWon ? 1 : 0),
                    "row lock and capacity check must allow exactly one seat claim");
            assertEquals(1, scalarInt("SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\" = ?",
                    fixture.sectionId()));
            assertEquals(1, scalarInt("SELECT count(*) FROM academic.\"Enrollment\" WHERE \"sectionId\" = ? "
                    + "AND lower(\"status\") IN ('active','enrolled','pending','confirmed')", fixture.sectionId()));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private boolean claimSeat(String sectionId, String studentId,
                              CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int[] section = lockedSection(connection, sectionId);
                int active = scalarInt(connection, "SELECT count(*) FROM academic.\"Enrollment\" "
                        + "WHERE \"sectionId\" = ? AND lower(\"status\") IN "
                        + "('active','enrolled','pending','confirmed')", sectionId);
                if (active >= section[0]) {
                    connection.rollback();
                    return false;
                }
                insertEnrollment(connection, id("enrollment-" + studentId), studentId, sectionId, "ACTIVE");
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE academic.\"Section\" SET \"enrolledCount\" = \"enrolledCount\" + 1 "
                                + "WHERE \"id\" = ? AND \"enrolledCount\" < \"capacity\"")) {
                    update.setString(1, sectionId);
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException failure) {
                connection.rollback();
                if ("23505".equals(failure.getSQLState())) {
                    return false;
                }
                throw failure;
            }
        }
    }

    private int[] lockedSection(Connection connection, String sectionId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT \"capacity\", \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\" = ? FOR UPDATE")) {
            query.setString(1, sectionId);
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next(), "test section must exist");
                return new int[]{rows.getInt(1), rows.getInt(2)};
            }
        }
    }

    private Fixture fixture(String label, int capacity) throws SQLException {
        String sectionId = id("section-" + label);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO academic.\"Section\" "
                             + "(\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", "
                             + "\"classroomId\", \"capacity\", \"enrolledCount\", \"status\") "
                             + "VALUES (?, ?, 'course-java-demo', 'semester-demo', 'lecturer-profile', 'classroom-a101', ?, 0, 'OPEN')")) {
            insert.setString(1, sectionId);
            insert.setString(2, "IT-" + UUID.randomUUID());
            insert.setInt(3, capacity);
            insert.executeUpdate();
        }
        return new Fixture(sectionId);
    }

    private void insertStudent(DataSource source, String studentId) throws SQLException {
        try (Connection connection = source.getConnection()) {
            insertStudent(connection, studentId);
        }
    }

    private void insertStudent(Connection connection, String studentId) throws SQLException {
        String userId = "user-" + studentId;
        try (PreparedStatement user = connection.prepareStatement(
                "INSERT INTO auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\") "
                        + "VALUES (?, ?, 'test-only-password-hash', 'Postgres', 'Race', 'ACTIVE')")) {
            user.setString(1, userId);
            user.setString(2, userId + "@test.invalid");
            user.executeUpdate();
        }
        createdUserIds.add(userId);
        try (PreparedStatement student = connection.prepareStatement(
                "INSERT INTO academic.\"Student\" "
                        + "(\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)")) {
            student.setString(1, studentId);
            student.setString(2, userId);
            student.setString(3, "TEST-" + studentId);
            student.executeUpdate();
        }
        createdStudentIds.add(studentId);
    }

    private void insertEnrollment(DataSource source, String enrollmentId, String studentId,
                                  String sectionId, String status) throws SQLException {
        try (Connection connection = source.getConnection()) {
            insertEnrollment(connection, enrollmentId, studentId, sectionId, status);
        }
    }

    private void insertEnrollment(Connection connection, String enrollmentId, String studentId,
                                  String sectionId, String status) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO academic.\"Enrollment\" "
                        + "(\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\", \"gradeStatus\") "
                        + "VALUES (?, ?, ?, 'semester-demo', ?, CURRENT_TIMESTAMP, 'NOT_GRADED')")) {
            insert.setString(1, enrollmentId);
            insert.setString(2, studentId);
            insert.setString(3, sectionId);
            insert.setString(4, status);
            insert.executeUpdate();
        }
    }

    private void deleteFixture(Connection connection, Fixture fixture) throws SQLException {
        try (PreparedStatement enrollment = connection.prepareStatement(
                "DELETE FROM academic.\"Enrollment\" WHERE \"sectionId\" = ?")) {
            enrollment.setString(1, fixture.sectionId());
            enrollment.executeUpdate();
        }
        try (PreparedStatement section = connection.prepareStatement(
                "DELETE FROM academic.\"Section\" WHERE \"id\" = ?")) {
            section.setString(1, fixture.sectionId());
            section.executeUpdate();
        }
        deleteByIds(connection, "academic.\"Student\"", createdStudentIds);
        deleteByIds(connection, "auth.\"User\"", createdUserIds);
    }

    private void deleteByIds(Connection connection, String table, List<String> ids) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE \"id\" IN (" + placeholders + ")")) {
            for (int index = 0; index < ids.size(); index++) {
                delete.setString(index + 1, ids.get(index));
            }
            delete.executeUpdate();
        }
    }

    private int scalarInt(String sql, String value) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return scalarInt(connection, sql, value);
        }
    }

    private int scalarInt(Connection connection, String sql, String value) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, value);
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static SQLException assertThrowsSqlState(SqlSupplier action) throws Exception {
        try {
            action.run();
        } catch (SQLException expected) {
            return expected;
        }
        throw new AssertionError("expected PostgreSQL unique-index violation");
    }

    private static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Fixture(String sectionId) { }

    @FunctionalInterface
    private interface SqlSupplier {
        void run() throws Exception;
    }
}
