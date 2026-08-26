package io.campuscore.restfulapi.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.campuscore.restfulapi.registration.RegistrationDtos.EnrollmentRequest;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Persists concrete registration policy fixtures. These are deliberately not
 * SQL-string mocks: they prove the service uses the authoritative student and
 * semester data when deciding a mutation.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:registration_eligibility_policy;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2",
        "spring.jpa.hibernate.ddl-auto=none"
})
class RegistrationEligibilityPolicyPersistenceTest {
    private static final String STUDENT = "policy-student";
    private static final String ROUND = "policy-round";
    private static final String SEMESTER = "policy-semester";
    private static final String TARGET_COURSE = "policy-target-course";
    private static final String TARGET_SECTION = "policy-target-section";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RegistrationService registration;

    @BeforeEach
    void prepareFixture() {
        createAcademicTables();
        clearFixture();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO academic.\"Student\" (\"id\",\"year\") VALUES (?,?)", STUDENT, 2);
        jdbc.update("INSERT INTO academic.\"Course\" (\"id\",\"credits\",\"code\",\"name\") VALUES (?,?,?,?)",
                TARGET_COURSE, 3, "TARGET", "Policy target");
        jdbc.update("INSERT INTO academic.\"Section\" (\"id\",\"courseId\",\"semesterId\",\"sectionNumber\",\"capacity\",\"enrolledCount\",\"status\",\"version\") VALUES (?,?,?,?,?,?,?,0)",
                TARGET_SECTION, TARGET_COURSE, SEMESTER, "POL-01", 20, 0, "OPEN");
        jdbc.update("INSERT INTO academic.\"RegistrationRound\" (\"id\",\"semesterId\",\"status\",\"registrationStart\",\"registrationEnd\",\"addDropStart\",\"addDropEnd\",\"maxCredits\",\"institutionTimeZone\",\"version\") VALUES (?,?,?,?,?,?,?,?,?,0)",
                ROUND, SEMESTER, "OPEN", now.minusSeconds(3600), now.plusSeconds(3600),
                now.minusSeconds(3600), now.plusSeconds(3600), 28, "Asia/Ho_Chi_Minh");
    }

    @Test
    void futureCohortWindowRejectsPersistedStudentDespiteStaleSuppliedYear() {
        cohortWindow("future", "2", Instant.now().plusSeconds(600), Instant.now().plusSeconds(1800));

        assertThat(registration.eligibility(STUDENT, ROUND, 9))
                .extracting(view -> view.eligibilityState(), view -> view.reasonCode())
                .containsExactly("INELIGIBLE", "COHORT_NOT_ELIGIBLE");
    }

    @Test
    void futureCohortWindowRejectsEnrollmentAndRollsBackMutation() {
        cohortWindow("future", "2", Instant.now().plusSeconds(600), Instant.now().plusSeconds(1800));

        assertThatThrownBy(() -> registration.enroll(STUDENT, new EnrollmentRequest(TARGET_SECTION, ROUND), UUID.randomUUID()))
                .isInstanceOf(RegistrationProblemException.class)
                .extracting(error -> ((RegistrationProblemException) error).code())
                .isEqualTo("COHORT_NOT_ELIGIBLE");
        assertUnchangedAfterRejectedEnrollment();
    }

    @Test
    void expiredCohortWindowRejectsAndOnlyConfiguredCurrentCohortAllowsEligibility() {
        cohortWindow("expired", "2", Instant.now().minusSeconds(1800), Instant.now().minusSeconds(600));
        assertThat(registration.eligibility(STUDENT, ROUND, 9).eligibilityState()).isEqualTo("INELIGIBLE");

        jdbc.update("DELETE FROM academic.\"RegistrationCohortWindow\"");
        cohortWindow("current", "2", Instant.now().minusSeconds(600), Instant.now().plusSeconds(600));
        assertThat(registration.eligibility(STUDENT, ROUND, 9).eligibilityState()).isEqualTo("ELIGIBLE");

        jdbc.update("DELETE FROM academic.\"RegistrationCohortWindow\"");
        cohortWindow("other-cohort", "3", Instant.now().minusSeconds(600), Instant.now().plusSeconds(600));
        assertThat(registration.eligibility(STUDENT, ROUND, 9).eligibilityState()).isEqualTo("INELIGIBLE");

        jdbc.update("DELETE FROM academic.\"RegistrationCohortWindow\"");
        assertThat(registration.eligibility(STUDENT, ROUND, 9).eligibilityState()).isEqualTo("ELIGIBLE");
    }

    @Test
    void duplicateCompletedAttemptsForOnePrerequisiteDoNotSatisfyAnotherRequiredCourse() {
        String courseA = course("prerequisite-a");
        String courseB = course("prerequisite-b");
        String completedA1 = section("prerequisite-a-first", courseA, "prior-semester");
        String completedA2 = section("prerequisite-a-second", courseA, "prior-semester");
        requirement("prerequisite-first", TARGET_COURSE, courseA, "PREREQUISITE");
        requirement("prerequisite-second", TARGET_COURSE, courseB, "PREREQUISITE");
        enrollment("completed-a-first", completedA1, "prior-semester", "COMPLETED");
        enrollment("completed-a-second", completedA2, "prior-semester", "COMPLETED");

        assertThat(registration.validate(STUDENT, new EnrollmentRequest(TARGET_SECTION, ROUND)).violations())
                .contains("PREREQUISITE_NOT_MET");
    }

    @Test
    void priorSemesterCorequisiteDoesNotSatisfyCurrentRound() {
        String corequisite = course("corequisite");
        String priorSection = section("corequisite-prior", corequisite, "prior-semester");
        requirement("corequisite-rule", TARGET_COURSE, corequisite, "COREQUISITE");
        enrollment("corequisite-prior-enrollment", priorSection, "prior-semester", "ENROLLED");

        assertThat(registration.validate(STUDENT, new EnrollmentRequest(TARGET_SECTION, ROUND)).violations())
                .contains("COREQUISITE_NOT_MET");
    }

    @Test
    void scheduleOnlyConflictsWithActiveEnrollmentsInTheRequestedSemester() {
        String clashCourse = course("schedule-clash");
        String priorSection = section("schedule-prior", clashCourse, "prior-semester");
        sectionSchedule("target-slot", TARGET_SECTION, LocalTime.of(10, 0), LocalTime.NOON);
        sectionSchedule("prior-slot", priorSection, LocalTime.of(10, 30), LocalTime.of(11, 30));
        enrollment("schedule-prior-enrollment", priorSection, "prior-semester", "ENROLLED");

        assertThat(registration.validate(STUDENT, new EnrollmentRequest(TARGET_SECTION, ROUND)).violations())
                .doesNotContain("SCHEDULE_CONFLICT");

        String currentSection = section("schedule-current", clashCourse, SEMESTER);
        sectionSchedule("current-slot", currentSection, LocalTime.of(10, 30), LocalTime.of(11, 30));
        enrollment("schedule-current-enrollment", currentSection, SEMESTER, "ENROLLED");

        assertThat(registration.validate(STUDENT, new EnrollmentRequest(TARGET_SECTION, ROUND)).violations())
                .contains("SCHEDULE_CONFLICT");
    }

    @Test
    void adminRoundConfigurationRoundTripsStudyYearAllowlistAndGuardsMutation() {
        RegistrationDtos.RoundView current = registration.round(ROUND);
        AdminRegistrationController.AdminRoundRequest request = new AdminRegistrationController.AdminRoundRequest(
                current.semesterId(), current.registrationStart(), current.registrationEnd(),
                current.addDropStart(), current.addDropEnd(), current.maxCredits(),
                current.institutionTimeZone(), current.version(), java.util.List.of(1));

        RegistrationDtos.RoundView updated = registration.adminUpdate(ROUND, request.toService());
        assertThat(updated.cohortYears()).containsExactly(1);
        assertThat(jdbc.queryForList(
                "SELECT \"cohortCode\" FROM academic.\"RegistrationCohortWindow\" WHERE \"roundId\" = ? ORDER BY \"priorityRank\"",
                String.class, ROUND)).containsExactly("1");
        assertThatThrownBy(() -> registration.enroll(
                STUDENT, new EnrollmentRequest(TARGET_SECTION, ROUND), UUID.randomUUID()))
                .isInstanceOf(RegistrationProblemException.class)
                .extracting(error -> ((RegistrationProblemException) error).code())
                .isEqualTo("COHORT_NOT_ELIGIBLE");
        assertUnchangedAfterRejectedEnrollment();
    }

    private void assertUnchangedAfterRejectedEnrollment() {
        assertThat(count("SELECT COUNT(*) FROM academic.\"Enrollment\" WHERE \"studentId\" = ?", STUDENT)).isZero();
        assertThat(count("SELECT COUNT(*) FROM academic.\"EnrollmentOperation\" WHERE \"studentId\" = ?", STUDENT)).isZero();
        assertThat(count("SELECT COUNT(*) FROM academic.\"EnrollmentAudit\" WHERE \"studentId\" = ?", STUDENT)).isZero();
        assertThat(jdbc.queryForObject("SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\" = ?", Integer.class, TARGET_SECTION)).isZero();
    }

    private void cohortWindow(String id, String cohort, Instant start, Instant end) {
        jdbc.update("INSERT INTO academic.\"RegistrationCohortWindow\" (\"id\",\"roundId\",\"cohortCode\",\"priorityRank\",\"windowStart\",\"windowEnd\",\"version\") VALUES (?,?,?,?,?,?,0)",
                id, ROUND, cohort, 1, start, end);
    }

    private String course(String suffix) {
        String id = "policy-course-" + suffix;
        jdbc.update("INSERT INTO academic.\"Course\" (\"id\",\"credits\",\"code\",\"name\") VALUES (?,?,?,?)", id, 3, id, id);
        return id;
    }

    private String section(String suffix, String courseId, String semesterId) {
        String id = "policy-section-" + suffix;
        jdbc.update("INSERT INTO academic.\"Section\" (\"id\",\"courseId\",\"semesterId\",\"sectionNumber\",\"capacity\",\"enrolledCount\",\"status\",\"version\") VALUES (?,?,?,?,?,?,?,0)",
                id, courseId, semesterId, suffix, 20, 0, "OPEN");
        return id;
    }

    private void enrollment(String id, String sectionId, String semesterId, String status) {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO academic.\"Enrollment\" (\"id\",\"studentId\",\"sectionId\",\"semesterId\",\"roundId\",\"status\",\"enrolledAt\",\"gradeStatus\",\"createdAt\",\"updatedAt\",\"version\") VALUES (?,?,?,?,?,?,?,?,?,?,0)",
                id, STUDENT, sectionId, semesterId, ROUND, status, now, "NOT_GRADED", now, now);
    }

    private void requirement(String id, String courseId, String requiredCourseId, String type) {
        jdbc.update("INSERT INTO academic.\"CourseRequirement\" (\"id\",\"courseId\",\"requiredCourseId\",\"requirementType\",\"createdAt\") VALUES (?,?,?,?,CURRENT_TIMESTAMP)",
                id, courseId, requiredCourseId, type);
    }

    private void sectionSchedule(String id, String sectionId, LocalTime start, LocalTime end) {
        jdbc.update("INSERT INTO academic.\"SectionSchedule\" (\"id\",\"sectionId\",\"dayOfWeek\",\"startTime\",\"endTime\",\"startTimeValue\",\"endTimeValue\",\"version\") VALUES (?,?,?,?,?,?,?,0)",
                id, sectionId, 2, Time.valueOf(start), Time.valueOf(end), Time.valueOf(start), Time.valueOf(end));
    }

    private int count(String sql, Object parameter) {
        return jdbc.queryForObject(sql, Integer.class, parameter);
    }

    private void clearFixture() {
        jdbc.execute("DELETE FROM academic.\"EnrollmentAudit\"");
        jdbc.execute("DELETE FROM academic.\"EnrollmentOperation\"");
        jdbc.execute("DELETE FROM academic.\"Enrollment\"");
        jdbc.execute("DELETE FROM academic.\"SectionSchedule\"");
        jdbc.execute("DELETE FROM academic.\"CourseRequirement\"");
        jdbc.execute("DELETE FROM academic.\"RegistrationCohortWindow\"");
        jdbc.execute("DELETE FROM academic.\"RegistrationRound\"");
        jdbc.execute("DELETE FROM academic.\"Section\"");
        jdbc.execute("DELETE FROM academic.\"Course\"");
        jdbc.execute("DELETE FROM academic.\"Student\"");
    }

    private void createAcademicTables() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS academic");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS campuscore_auth");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Student\" (\"id\" VARCHAR(120) PRIMARY KEY, \"year\" INTEGER NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Course\" (\"id\" VARCHAR(120) PRIMARY KEY, \"credits\" INTEGER NOT NULL, \"code\" VARCHAR(60) NOT NULL, \"name\" VARCHAR(240) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Section\" (\"id\" VARCHAR(120) PRIMARY KEY, \"courseId\" VARCHAR(120) NOT NULL, \"semesterId\" VARCHAR(120) NOT NULL, \"sectionNumber\" VARCHAR(80) NOT NULL, \"lecturerId\" VARCHAR(120), \"classroomId\" VARCHAR(120), \"capacity\" INTEGER NOT NULL, \"enrolledCount\" INTEGER NOT NULL, \"status\" VARCHAR(40) NOT NULL, \"version\" BIGINT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Enrollment\" (\"id\" VARCHAR(120) PRIMARY KEY, \"studentId\" VARCHAR(120) NOT NULL, \"sectionId\" VARCHAR(120) NOT NULL, \"semesterId\" VARCHAR(120) NOT NULL, \"roundId\" VARCHAR(120) NOT NULL, \"status\" VARCHAR(40) NOT NULL, \"enrolledAt\" TIMESTAMP WITH TIME ZONE NOT NULL, \"droppedAt\" TIMESTAMP WITH TIME ZONE, \"gradeStatus\" VARCHAR(40) NOT NULL, \"finalGrade\" DECIMAL(5,2), \"letterGrade\" VARCHAR(16), \"createdAt\" TIMESTAMP WITH TIME ZONE NOT NULL, \"updatedAt\" TIMESTAMP WITH TIME ZONE NOT NULL, \"version\" BIGINT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Lecturer\" (\"id\" VARCHAR(120) PRIMARY KEY, \"userId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Classroom\" (\"id\" VARCHAR(120) PRIMARY KEY, \"building\" VARCHAR(160), \"roomNumber\" VARCHAR(80))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"User\" (\"id\" VARCHAR(120) PRIMARY KEY, \"firstName\" VARCHAR(120), \"lastName\" VARCHAR(120))");
        jdbc.execute("ALTER TABLE academic.\"SectionSchedule\" ADD COLUMN IF NOT EXISTS \"startTimeValue\" TIME");
        jdbc.execute("ALTER TABLE academic.\"SectionSchedule\" ADD COLUMN IF NOT EXISTS \"endTimeValue\" TIME");
        jdbc.execute("ALTER TABLE academic.\"SectionSchedule\" ADD COLUMN IF NOT EXISTS \"classroomId\" VARCHAR(120)");
    }
}
