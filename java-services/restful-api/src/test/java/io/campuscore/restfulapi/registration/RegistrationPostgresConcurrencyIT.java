package io.campuscore.restfulapi.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import io.campuscore.restfulapi.registration.RegistrationDtos.EnrollmentRequest;
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

    @Autowired
    private RegistrationService registration;

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

    @Test
    void serviceSameIdempotencyKeyCreatesOneEnrollmentAndReplaysTheWinner() throws Exception {
        Fixture fixture = fixture("service-idempotency", 2);
        fixtures.add(fixture);
        String studentId = id("student-service-idempotency");
        insertStudent(dataSource, studentId);
        UUID key = UUID.randomUUID();
        EnrollmentRequest request = new EnrollmentRequest(fixture.sectionId(), fixture.roundId());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<RegistrationService.MutationResult>> calls = List.of(
                    pool.submit(() -> serviceEnroll(studentId, request, key, ready, start)),
                    pool.submit(() -> serviceEnroll(studentId, request, key, ready, start)));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            RegistrationService.MutationResult first = calls.get(0).get(20, TimeUnit.SECONDS);
            RegistrationService.MutationResult second = calls.get(1).get(20, TimeUnit.SECONDS);
            assertEquals(first.enrollment().id(), second.enrollment().id());
            assertEquals(1, (first.replayed() ? 1 : 0) + (second.replayed() ? 1 : 0));
            assertEquals(1, scalarInt("SELECT count(*) FROM academic.\"Enrollment\" WHERE \"sectionId\" = ?",
                    fixture.sectionId()));
            assertEquals(1, scalarInt("SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\" = ?",
                    fixture.sectionId()));
            assertEquals(1, scalarInt("SELECT count(*) FROM academic.\"EnrollmentOperation\" WHERE \"studentId\" = ?",
                    studentId));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void serviceCapacityRaceHasOneWinnerAndRollsBackTheRejectedOperation() throws Exception {
        Fixture fixture = fixture("service-capacity", 1);
        fixtures.add(fixture);
        String firstStudent = id("student-service-first");
        String secondStudent = id("student-service-second");
        insertStudent(dataSource, firstStudent);
        insertStudent(dataSource, secondStudent);
        EnrollmentRequest request = new EnrollmentRequest(fixture.sectionId(), fixture.roundId());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> serviceSeatClaim(firstStudent, request, ready, start));
            Future<Boolean> second = pool.submit(() -> serviceSeatClaim(secondStudent, request, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(1, (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0));
            assertEquals(1, scalarInt("SELECT count(*) FROM academic.\"Enrollment\" WHERE \"sectionId\" = ?",
                    fixture.sectionId()));
            assertEquals(1, scalarInt("SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\" = ?",
                    fixture.sectionId()));
            assertEquals(1, scalarInt("SELECT count(*) FROM academic.\"EnrollmentOperation\" WHERE \"studentId\" IN (?, ?)",
                    firstStudent, secondStudent));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void serviceDropIsIdempotentAndDecrementsCapacityOnce() throws Exception {
        Fixture fixture = fixture("service-drop", 1);
        fixtures.add(fixture);
        String studentId = id("student-service-drop");
        insertStudent(dataSource, studentId);
        RegistrationService.MutationResult enrollment = registration.enroll(
                studentId,
                new EnrollmentRequest(fixture.sectionId(), fixture.roundId()),
                UUID.randomUUID());
        UUID dropKey = UUID.randomUUID();

        RegistrationService.DropResult first = registration.drop(studentId, enrollment.enrollment().id(), dropKey);
        RegistrationService.DropResult replay = registration.drop(studentId, enrollment.enrollment().id(), dropKey);

        assertTrue(!first.replayed());
        assertTrue(replay.replayed());
        assertEquals(0, scalarInt("SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\" = ?",
                fixture.sectionId()));
        assertEquals(1, scalarInt("SELECT count(*) FROM academic.\"EnrollmentAudit\" WHERE \"sectionId\" = ? AND \"action\" = 'DROP'",
                fixture.sectionId()));
    }

    private RegistrationService.MutationResult serviceEnroll(
            String studentId,
            EnrollmentRequest request,
            UUID key,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return registration.enroll(studentId, request, key);
    }

    private boolean serviceSeatClaim(
            String studentId,
            EnrollmentRequest request,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            registration.enroll(studentId, request, UUID.randomUUID());
            return true;
        } catch (RegistrationProblemException rejected) {
            assertEquals("SECTION_FULL", rejected.code());
            return false;
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
        String roundId = id("round-" + label);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insertSection = connection.prepareStatement(
                     "INSERT INTO academic.\"Section\" "
                             + "(\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", "
                             + "\"classroomId\", \"capacity\", \"enrolledCount\", \"status\") "
                             + "VALUES (?, ?, 'course-java-demo', 'semester-demo', 'lecturer-profile', 'classroom-a101', ?, 0, 'OPEN')");
             PreparedStatement insertRound = connection.prepareStatement(
                     "INSERT INTO academic.\"RegistrationRound\" "
                             + "(\"id\", \"semesterId\", \"status\", \"registrationStart\", \"registrationEnd\", "
                             + "\"addDropStart\", \"addDropEnd\", \"maxCredits\", \"institutionTimeZone\") "
                             + "VALUES (?, 'semester-demo', 'OPEN', ?, ?, ?, ?, 28, 'Asia/Ho_Chi_Minh')")) {
            insertSection.setString(1, sectionId);
            insertSection.setString(2, "IT-" + UUID.randomUUID());
            insertSection.setInt(3, capacity);
            insertSection.executeUpdate();
            Instant now = Instant.now();
            insertRound.setString(1, roundId);
            insertRound.setTimestamp(2, Timestamp.from(now.minusSeconds(3600)));
            insertRound.setTimestamp(3, Timestamp.from(now.plusSeconds(3600)));
            insertRound.setTimestamp(4, Timestamp.from(now.minusSeconds(3600)));
            insertRound.setTimestamp(5, Timestamp.from(now.plusSeconds(3600)));
            insertRound.executeUpdate();
        }
        return new Fixture(sectionId, roundId);
    }

    private void insertStudent(DataSource source, String studentId) throws SQLException {
        try (Connection connection = source.getConnection()) {
            insertStudent(connection, studentId);
        }
    }

    private void insertStudent(Connection connection, String studentId) throws SQLException {
        String userId = "user-" + studentId;
        try (PreparedStatement user = connection.prepareStatement(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\") "
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
        try (PreparedStatement audit = connection.prepareStatement(
                "DELETE FROM academic.\"EnrollmentAudit\" WHERE \"sectionId\" = ?")) {
            audit.setString(1, fixture.sectionId());
            audit.executeUpdate();
        }
        try (PreparedStatement slip = connection.prepareStatement(
                "DELETE FROM academic.\"RegistrationSlip\" WHERE \"roundId\" = ?")) {
            slip.setString(1, fixture.roundId());
            slip.executeUpdate();
        }
        deleteByIds(connection, "academic.\"EnrollmentOperation\"", createdStudentIds, "studentId");
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
        try (PreparedStatement round = connection.prepareStatement(
                "DELETE FROM academic.\"RegistrationRound\" WHERE \"id\" = ?")) {
            round.setString(1, fixture.roundId());
            round.executeUpdate();
        }
        deleteByIds(connection, "academic.\"Student\"", createdStudentIds);
        deleteByIds(connection, "campuscore_auth.\"User\"", createdUserIds);
    }

    private void deleteByIds(Connection connection, String table, List<String> ids) throws SQLException {
        deleteByIds(connection, table, ids, "id");
    }

    private void deleteByIds(Connection connection, String table, List<String> ids, String column) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE \"" + column + "\" IN (" + placeholders + ")")) {
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

    private int scalarInt(String sql, String first, String second) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, first);
            query.setString(2, second);
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
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

    private record Fixture(String sectionId, String roundId) { }

    @FunctionalInterface
    private interface SqlSupplier {
        void run() throws Exception;
    }
}
