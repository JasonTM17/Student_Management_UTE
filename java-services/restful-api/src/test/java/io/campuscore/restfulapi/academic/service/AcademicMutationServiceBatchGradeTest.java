package io.campuscore.restfulapi.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import io.campuscore.restfulapi.academic.registration.service.RegistrationService;
import io.campuscore.restfulapi.academic.dto.AcademicMutationDtos.GradeUpdate;
import io.campuscore.restfulapi.exception.DomainException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AcademicMutationServiceBatchGradeTest {

    private JdbcTemplate jdbc;
    private AcademicMutationService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:academic_batch_test_" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbc = new JdbcTemplate(dataSource);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);

        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");

        jdbc.execute("""
            CREATE TABLE "academic"."Section" (
                "id" VARCHAR(64) PRIMARY KEY,
                "semesterId" VARCHAR(64),
                "lecturerId" VARCHAR(64),
                "capacity" INT DEFAULT 50,
                "enrolledCount" INT DEFAULT 0,
                "status" VARCHAR(32) DEFAULT 'OPEN'
            )
        """);

        jdbc.execute("""
            CREATE TABLE "academic"."GradeItem" (
                "id" VARCHAR(128) PRIMARY KEY,
                "sectionId" VARCHAR(64),
                "name" VARCHAR(128),
                "type" VARCHAR(32),
                "maxScore" NUMERIC(5,2),
                "weight" NUMERIC(5,2),
                "gradedAt" TIMESTAMP
            )
        """);

        jdbc.execute("""
            CREATE TABLE "academic"."Enrollment" (
                "id" VARCHAR(64) PRIMARY KEY,
                "studentId" VARCHAR(64),
                "sectionId" VARCHAR(64),
                "status" VARCHAR(32),
                "registrationSource" VARCHAR(40),
                "gradeStatus" VARCHAR(32),
                "finalGrade" NUMERIC(5,2),
                "letterGrade" VARCHAR(8),
                "updatedAt" TIMESTAMP,
                "gradePublishedAt" TIMESTAMP
            )
        """);

        jdbc.execute("""
            CREATE TABLE "academic"."StudentGrade" (
                "id" VARCHAR(64) PRIMARY KEY,
                "enrollmentId" VARCHAR(64),
                "gradeItemId" VARCHAR(128),
                "score" NUMERIC(5,2)
            )
        """);

        service = new AcademicMutationService(
                namedJdbc,
                mock(AcademicEnrollmentReadService.class),
                mock(RegistrationService.class),
                mock(io.campuscore.restfulapi.audit.service.AdminAuditRecorder.class));
    }

    @Test
    void batchGradeUpdatePersistsAllScoresAndEnrollmentGrades() {
        // Setup section
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-01", "sem-01", "lec-01", "OPEN");

        // Setup 3 student enrollments
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, ?, ?)",
                "enr-1", "stu-1", "sec-01", "ENROLLED", "DRAFT");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, ?, ?)",
                "enr-2", "stu-2", "sec-01", "CONFIRMED", "DRAFT");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, ?, ?)",
                "enr-3", "stu-3", "sec-01", "COMPLETED", "DRAFT");

        List<GradeUpdate> grades = List.of(
                new GradeUpdate("enr-1", new BigDecimal("8.5"), new BigDecimal("9.5")),
                new GradeUpdate("enr-2", new BigDecimal("7.0"), new BigDecimal("7.0")),
                new GradeUpdate("enr-3", new BigDecimal("5.0"), new BigDecimal("6.0"))
        );

        service.updateGrades("sec-01", "lec-01", false, grades);

        // Verify StudentGrade table has 6 rows (2 per student)
        Integer gradeCount = jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"StudentGrade\"", Integer.class);
        assertEquals(6, gradeCount);

        // Verify calculated grades on enrollments
        BigDecimal grade1 = jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-1'", BigDecimal.class);
        String letter1 = jdbc.queryForObject("SELECT \"letterGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-1'", String.class);
        assertEquals(new BigDecimal("9.00"), grade1);
        assertEquals("A+", letter1);

        BigDecimal grade2 = jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-2'", BigDecimal.class);
        String letter2 = jdbc.queryForObject("SELECT \"letterGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-2'", String.class);
        assertEquals(new BigDecimal("7.00"), grade2);
        assertEquals("B", letter2);

        BigDecimal grade3 = jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-3'", BigDecimal.class);
        String letter3 = jdbc.queryForObject("SELECT \"letterGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-3'", String.class);
        assertEquals(new BigDecimal("5.50"), grade3);
        assertEquals("C", letter3);
    }

    @Test
    void batchGradeUpdateAcceptsInclusiveZeroToTenAndRejectsScoresOutsideIt() {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-bounds", "sem-01", "lec-01", "OPEN");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, ?, ?)",
                "enr-zero", "stu-zero", "sec-bounds", "ENROLLED", "DRAFT");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, ?, ?)",
                "enr-ten", "stu-ten", "sec-bounds", "ENROLLED", "DRAFT");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, ?, ?)",
                "enr-negative", "stu-negative", "sec-bounds", "ENROLLED", "DRAFT");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, ?, ?)",
                "enr-over", "stu-over", "sec-bounds", "ENROLLED", "DRAFT");

        service.updateGrades("sec-bounds", "lec-01", false, List.of(
                new GradeUpdate("enr-zero", BigDecimal.ZERO, BigDecimal.ZERO),
                new GradeUpdate("enr-ten", BigDecimal.TEN, BigDecimal.TEN)));

        assertEquals(new BigDecimal("0.00"), jdbc.queryForObject(
                "SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-zero'", BigDecimal.class));
        assertEquals("F", jdbc.queryForObject(
                "SELECT \"letterGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-zero'", String.class));
        assertEquals(new BigDecimal("10.00"), jdbc.queryForObject(
                "SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-ten'", BigDecimal.class));
        assertEquals("A+", jdbc.queryForObject(
                "SELECT \"letterGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-ten'", String.class));

        DomainException below = assertThrows(DomainException.class, () -> service.updateGrades(
                "sec-bounds", "lec-01", false,
                List.of(new GradeUpdate("enr-negative", new BigDecimal("-0.01"), BigDecimal.ZERO))));
        assertEquals(HttpStatus.BAD_REQUEST, below.getStatus());
        assertEquals("GRADE_SCORE_OUT_OF_RANGE", below.getCode());
        DomainException above = assertThrows(DomainException.class, () -> service.updateGrades(
                "sec-bounds", "lec-01", false,
                List.of(new GradeUpdate("enr-over", BigDecimal.TEN, new BigDecimal("10.01")))));
        assertEquals(HttpStatus.BAD_REQUEST, above.getStatus());
        assertEquals("GRADE_SCORE_OUT_OF_RANGE", above.getCode());
    }

    @Test
    void batchGradeUpdateWithEmptyListReturnsWithoutError() {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-01", "sem-01", "lec-01", "OPEN");
        service.updateGrades("sec-01", "lec-01", true, List.of());
    }

    @Test
    void batchGradeUpdateWithEmptyListStillValidatesSectionOwnership() {
        // Guard order: section existence and ownership are checked before the
        // empty-list early return, so a silent no-op cannot skip authorization.
        assertThrows(DomainException.class, () ->
                service.updateGrades("sec-missing", "lec-01", true, List.of()));
    }

    @Test
    void lecturerCannotUpdateOrPublishGradesForAnotherLecturersSection() {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-foreign", "sem-01", "lec-02", "OPEN");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, ?, ?)",
                "enr-foreign", "stu-01", "sec-foreign", "ENROLLED", "DRAFT");
        List<GradeUpdate> grades = List.of(
                new GradeUpdate("enr-foreign", new BigDecimal("8.0"), new BigDecimal("9.0")));

        DomainException updateDenied = assertThrows(DomainException.class,
                () -> service.updateGrades("sec-foreign", "lec-01", false, grades));
        assertEquals(HttpStatus.FORBIDDEN, updateDenied.getStatus());
        assertEquals("SECTION_FORBIDDEN", updateDenied.getCode());

        DomainException publishDenied = assertThrows(DomainException.class,
                () -> service.publishGrades("sec-foreign", "lec-01", false));
        assertEquals(HttpStatus.FORBIDDEN, publishDenied.getStatus());
        assertEquals("SECTION_FORBIDDEN", publishDenied.getCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"StudentGrade\"", Integer.class));
        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT \"gradeStatus\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-foreign'",
                String.class));
    }

    @Test
    void batchGradeUpdateWithNonExistentEnrollmentThrowsNotFound() {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-02", "sem-01", "lec-01", "OPEN");

        List<GradeUpdate> grades = List.of(
                new GradeUpdate("enr-nonexistent", new BigDecimal("8.0"), new BigDecimal("8.0"))
        );

        DomainException exception = assertThrows(DomainException.class, () ->
                service.updateGrades("sec-02", "lec-01", true, grades));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("ENROLLMENT_NOT_FOUND", exception.getCode());
    }
}
