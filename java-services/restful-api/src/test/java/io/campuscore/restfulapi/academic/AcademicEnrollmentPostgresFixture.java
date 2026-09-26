package io.campuscore.restfulapi.academic;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Self-contained seed for the enrollment Postgres ITs. Every identifier is
 * suffixed per fixture instance so concurrent or repeated runs never collide
 * with the demo data the full migration set plants on the same database.
 *
 * <p>Satisfies every NOT NULL/FK chain of the production schema:
 * AcademicYear → Faculty → Department → Curriculum/Course, Semester, Section,
 * campuscore_auth."User" → Student, RegistrationRound.
 */
final class AcademicEnrollmentPostgresFixture {

    private final NamedParameterJdbcTemplate jdbc;
    private final String suffix = "it-" + UUID.randomUUID().toString().substring(0, 8);

    final String academicYearId = "ay-" + suffix;
    final String facultyId = "faculty-" + suffix;
    final String departmentId = "department-" + suffix;
    final String curriculumId = "curriculum-" + suffix;
    final String courseId = "course-" + suffix;
    final String semesterId = "semester-" + suffix;
    final String sectionId = "section-" + suffix;
    final String roundId = "round-" + suffix;

    private AcademicEnrollmentPostgresFixture(NamedParameterJdbcTemplate jdbc, int capacity) {
        this.jdbc = jdbc;
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp farPast = Timestamp.from(Instant.parse("2020-01-01T00:00:00Z"));
        Timestamp farFuture = Timestamp.from(Instant.parse("2099-01-01T00:00:00Z"));

        jdbc.update("INSERT INTO academic.\"AcademicYear\""
                        + " (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\")"
                        + " VALUES (:id, 2026, :start, :end, TRUE)",
                params("id", academicYearId).addValue("start", now).addValue("end", farFuture));
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\", \"name\", \"code\") VALUES (:id, :name, :code)",
                params("id", facultyId).addValue("name", "IT Faculty " + suffix).addValue("code", "F-" + suffix));
        jdbc.update("INSERT INTO academic.\"Department\""
                        + " (\"id\", \"name\", \"code\", \"facultyId\") VALUES (:id, :name, :code, :facultyId)",
                params("id", departmentId)
                        .addValue("name", "Software Engineering " + suffix)
                        .addValue("code", "D-" + suffix)
                        .addValue("facultyId", facultyId));
        jdbc.update("INSERT INTO academic.\"Curriculum\""
                        + " (\"id\", \"name\", \"code\", \"departmentId\", \"academicYearId\", \"totalCredits\", \"isActive\")"
                        + " VALUES (:id, :name, :code, :departmentId, :academicYearId, 150, TRUE)",
                params("id", curriculumId)
                        .addValue("name", "Curriculum " + suffix)
                        .addValue("code", "C-" + suffix)
                        .addValue("departmentId", departmentId)
                        .addValue("academicYearId", academicYearId));
        jdbc.update("INSERT INTO academic.\"Course\""
                        + " (\"id\", \"code\", \"name\", \"credits\", \"departmentId\", \"isActive\")"
                        + " VALUES (:id, :code, :name, 3, :departmentId, TRUE)",
                params("id", courseId)
                        .addValue("code", "IT" + suffix)
                        .addValue("name", "Race Course " + suffix)
                        .addValue("departmentId", departmentId));
        jdbc.update("INSERT INTO academic.\"Semester\""
                        + " (\"id\", \"name\", \"type\", \"academicYearId\", \"startDate\", \"endDate\", \"status\")"
                        + " VALUES (:id, :name, 'FALL', :academicYearId, :start, :end, 'ACTIVE')",
                params("id", semesterId)
                        .addValue("name", "Semester " + suffix)
                        .addValue("academicYearId", academicYearId)
                        .addValue("start", now)
                        .addValue("end", farFuture));
        jdbc.update("INSERT INTO academic.\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"capacity\", \"enrolledCount\","
                        + " \"status\", \"version\")"
                        + " VALUES (:id, '01', :courseId, :semesterId, :capacity, 0, 'OPEN', 0)",
                params("id", sectionId)
                        .addValue("courseId", courseId)
                        .addValue("semesterId", semesterId)
                        .addValue("capacity", capacity));
        jdbc.update("INSERT INTO academic.\"RegistrationRound\""
                        + " (\"id\", \"semesterId\", \"name\", \"kind\", \"status\", \"windowStart\", \"windowEnd\","
                        + " \"creditLimit\", \"version\")"
                        + " VALUES (:id, :semesterId, :name, 'REGISTRATION', 'OPEN', :start, :end, 28, 0)",
                params("id", roundId)
                        .addValue("semesterId", semesterId)
                        .addValue("name", "Round " + suffix)
                        .addValue("start", farPast)
                        .addValue("end", farFuture));
    }

    static AcademicEnrollmentPostgresFixture seed(NamedParameterJdbcTemplate jdbc, int capacity) {
        return new AcademicEnrollmentPostgresFixture(jdbc, capacity);
    }

    /** Seeds one ACTIVE student profile with its auth user; returns the academic Student id. */
    String seedStudent(int index) {
        String userId = "user-" + suffix + "-" + index;
        String studentId = "student-" + suffix + "-" + index;
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO campuscore_auth.\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\")"
                        + " VALUES (:id, :email, :password, 'Race', :lastName, 'ACTIVE')",
                params("id", userId)
                        .addValue("email", userId + "@campuscore.test")
                        .addValue("password", "{noop}not-a-real-credential")
                        .addValue("lastName", "Student " + index));
        jdbc.update("INSERT INTO academic.\"Student\""
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\", \"admissionDate\")"
                        + " VALUES (:id, :userId, :studentId, :curriculumId, 2, 'ACTIVE', :now)",
                params("id", studentId)
                        .addValue("userId", userId)
                        .addValue("studentId", "SR" + suffix + String.format("%03d", index))
                        .addValue("curriculumId", curriculumId)
                        .addValue("now", now));
        return studentId;
    }

    private static MapSqlParameterSource params(String key, Object value) {
        return new MapSqlParameterSource(key, value);
    }
}
