package io.campuscore.restfulapi.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.campuscore.restfulapi.academic.registration.RegistrationService;
import io.campuscore.restfulapi.academic.web.AcademicConductDtos.ConductActivityDto;
import io.campuscore.restfulapi.academic.web.AcademicConductDtos.ConductSemesterScoreDto;
import io.campuscore.restfulapi.academic.web.AcademicConductDtos.StudentConductSummaryDto;
import io.campuscore.restfulapi.academic.web.AcademicMutationDtos.GradeUpdate;
import io.campuscore.restfulapi.web.DomainException;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.web.server.ResponseStatusException;

/**
 * Empirical Adversarial Challenge Test Suite for Milestone 1:
 * 1. Conduct Activity Batching & Edge Cases in AcademicConductService
 * 2. Grade Mutation Batching, Rollback Safety & Arithmetic in AcademicMutationService
 */
class AcademicBatchAdversarialChallengeTest {

    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate namedJdbc;
    private AcademicConductService conductService;
    private AcademicMutationService mutationServiceProxy;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:adversarial_batch_test_" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbc = new JdbcTemplate(dataSource);
        namedJdbc = new NamedParameterJdbcTemplate(dataSource);

        // Schemas
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");

        // Tables for AcademicConductService
        jdbc.execute("""
            CREATE TABLE "campuscore_auth"."User" (
                "id" VARCHAR(64) PRIMARY KEY,
                "firstName" VARCHAR(64),
                "lastName" VARCHAR(64)
            )
        """);

        jdbc.execute("""
            CREATE TABLE "academic"."Semester" (
                "id" VARCHAR(64) PRIMARY KEY,
                "name" VARCHAR(128),
                "startDate" DATE
            )
        """);

        jdbc.execute("""
            CREATE TABLE "academic"."Student" (
                "id" VARCHAR(64) PRIMARY KEY,
                "userId" VARCHAR(64),
                "studentId" VARCHAR(32)
            )
        """);

        jdbc.execute("""
            CREATE TABLE academic.conduct_semester_score (
                id VARCHAR(64) PRIMARY KEY,
                student_id VARCHAR(64),
                semester_id VARCHAR(64),
                criteria1_score NUMERIC(5,2),
                criteria2_score NUMERIC(5,2),
                criteria3_score NUMERIC(5,2),
                criteria4_score NUMERIC(5,2),
                criteria5_score NUMERIC(5,2),
                total_score NUMERIC(5,2),
                classification VARCHAR(64),
                classification_vi VARCHAR(64),
                status VARCHAR(32),
                evaluator_name VARCHAR(128)
            )
        """);

        jdbc.execute("""
            CREATE TABLE academic.conduct_activity (
                id VARCHAR(64) PRIMARY KEY,
                student_id VARCHAR(64),
                semester_id VARCHAR(64),
                title VARCHAR(256),
                category VARCHAR(64),
                points NUMERIC(5,2),
                activity_date DATE,
                organizer VARCHAR(128),
                certificate_url VARCHAR(256)
            )
        """);

        // Tables for AcademicMutationService
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
                "gradeStatus" VARCHAR(32),
                "finalGrade" NUMERIC(5,2),
                "letterGrade" VARCHAR(8),
                "updatedAt" TIMESTAMP
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

        // Canonical V53 uniqueness constraint
        jdbc.execute("""
            CREATE UNIQUE INDEX academic_student_grade_enrollment_item_uq
            ON "academic"."StudentGrade" ("enrollmentId", "gradeItemId")
        """);

        conductService = new AcademicConductService(namedJdbc);

        AcademicMutationService targetMutationService = new AcademicMutationService(
                namedJdbc,
                mock(AcademicEnrollmentReadService.class),
                mock(RegistrationService.class));

        // Wrap targetMutationService with Spring's TransactionInterceptor for real transaction boundary testing
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        AnnotationTransactionAttributeSource tas = new AnnotationTransactionAttributeSource();
        TransactionInterceptor ti = new TransactionInterceptor(txManager, tas);

        ProxyFactory pf = new ProxyFactory(targetMutationService);
        pf.addAdvice(ti);
        mutationServiceProxy = (AcademicMutationService) pf.getProxy();
    }

    // =========================================================================
    // SECTION 1: AcademicConductService Adversarial Challenges
    // =========================================================================

    @Test
    @DisplayName("Conduct: Student with multiple semesters but EMPTY activities returns clean summary without errors")
    void conduct_emptyActivitiesListReturnsCleanSummary() {
        jdbc.update("INSERT INTO \"academic\".\"Semester\" (\"id\", \"name\", \"startDate\") VALUES (?, ?, ?)",
                "sem-1", "Học kỳ 1 2025-2026", Date.valueOf("2025-09-01"));
        jdbc.update("INSERT INTO \"academic\".\"Semester\" (\"id\", \"name\", \"startDate\") VALUES (?, ?, ?)",
                "sem-2", "Học kỳ 2 2025-2026", Date.valueOf("2026-02-01"));

        jdbc.update("INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"firstName\", \"lastName\") VALUES (?, ?, ?)",
                "usr-01", "Bao", "Tran");
        jdbc.update("INSERT INTO \"academic\".\"Student\" (\"id\", \"userId\", \"studentId\") VALUES (?, ?, ?)",
                "stu-01", "usr-01", "22110002");

        jdbc.update("INSERT INTO academic.conduct_semester_score (id, student_id, semester_id, total_score, classification, classification_vi, status) VALUES (?, ?, ?, 80, 'GOOD', 'Tốt', 'CONFIRMED')",
                "sc-1", "stu-01", "sem-1");
        jdbc.update("INSERT INTO academic.conduct_semester_score (id, student_id, semester_id, total_score, classification, classification_vi, status) VALUES (?, ?, ?, 90, 'EXCELLENT', 'Xuất sắc', 'CONFIRMED')",
                "sc-2", "stu-01", "sem-2");

        // Zero conduct_activity inserted!

        StudentConductSummaryDto summary = conductService.studentSummary("stu-01");

        assertNotNull(summary);
        assertEquals("22110002", summary.studentCode());
        assertEquals("Tran Bao", summary.fullName());
        assertEquals(2, summary.history().size());
        for (ConductSemesterScoreDto semScore : summary.history()) {
            assertNotNull(semScore.activities());
            assertTrue(semScore.activities().isEmpty(), "Activities must be empty list, not null");
        }
        assertEquals(new BigDecimal("85.0"), summary.cumulativeAverageScore());
        assertEquals("Tốt", summary.cumulativeClassificationVi());
    }

    @Test
    @DisplayName("Conduct: Student with zero conduct score rows returns zero average and 'Chưa có đánh giá'")
    void conduct_studentWithZeroScoresReturnsZeroAndChuaCoDanhGia() {
        jdbc.update("INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"firstName\", \"lastName\") VALUES (?, ?, ?)",
                "usr-02", "Minh", "Le");
        jdbc.update("INSERT INTO \"academic\".\"Student\" (\"id\", \"userId\", \"studentId\") VALUES (?, ?, ?)",
                "stu-02", "usr-02", "22110003");

        StudentConductSummaryDto summary = conductService.studentSummary("stu-02");

        assertNotNull(summary);
        assertEquals(BigDecimal.ZERO, summary.cumulativeAverageScore());
        assertEquals("Chưa có đánh giá", summary.cumulativeClassificationVi());
        assertNull(summary.currentSemester());
        assertTrue(summary.history().isEmpty());
    }

    @Test
    @DisplayName("Conduct: Multi-semester activities correctly grouped and ordered by activity_date DESC without cross-semester leaks")
    void conduct_multipleSemestersCorrectGroupingAndSorting() {
        jdbc.update("INSERT INTO \"academic\".\"Semester\" (\"id\", \"name\", \"startDate\") VALUES (?, ?, ?)",
                "sem-1", "HK1 2024-2025", Date.valueOf("2024-09-01"));
        jdbc.update("INSERT INTO \"academic\".\"Semester\" (\"id\", \"name\", \"startDate\") VALUES (?, ?, ?)",
                "sem-2", "HK2 2024-2025", Date.valueOf("2025-02-01"));
        jdbc.update("INSERT INTO \"academic\".\"Semester\" (\"id\", \"name\", \"startDate\") VALUES (?, ?, ?)",
                "sem-3", "HK1 2025-2026", Date.valueOf("2025-09-01"));

        jdbc.update("INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"firstName\", \"lastName\") VALUES (?, ?, ?)",
                "usr-03", "Chi", "Dang");
        jdbc.update("INSERT INTO \"academic\".\"Student\" (\"id\", \"userId\", \"studentId\") VALUES (?, ?, ?)",
                "stu-03", "usr-03", "22110004");

        // 3 semester scores
        jdbc.update("INSERT INTO academic.conduct_semester_score (id, student_id, semester_id, total_score, classification, classification_vi, status) VALUES (?, ?, ?, 80, 'GOOD', 'Tốt', 'CONFIRMED')", "sc-1", "stu-03", "sem-1");
        jdbc.update("INSERT INTO academic.conduct_semester_score (id, student_id, semester_id, total_score, classification, classification_vi, status) VALUES (?, ?, ?, 85, 'GOOD', 'Tốt', 'CONFIRMED')", "sc-2", "stu-03", "sem-2");
        jdbc.update("INSERT INTO academic.conduct_semester_score (id, student_id, semester_id, total_score, classification, classification_vi, status) VALUES (?, ?, ?, 90, 'EXCELLENT', 'Xuất sắc', 'CONFIRMED')", "sc-3", "stu-03", "sem-3");

        // Sem-1: 2 activities (earlier date and later date)
        jdbc.update("INSERT INTO academic.conduct_activity (id, student_id, semester_id, title, activity_date) VALUES (?, ?, ?, ?, ?)",
                "act-1a", "stu-03", "sem-1", "Old Activity S1", Date.valueOf(LocalDate.of(2024, 9, 10)));
        jdbc.update("INSERT INTO academic.conduct_activity (id, student_id, semester_id, title, activity_date) VALUES (?, ?, ?, ?, ?)",
                "act-1b", "stu-03", "sem-1", "New Activity S1", Date.valueOf(LocalDate.of(2024, 11, 20)));

        // Sem-2: 0 activities

        // Sem-3: 3 activities
        jdbc.update("INSERT INTO academic.conduct_activity (id, student_id, semester_id, title, activity_date) VALUES (?, ?, ?, ?, ?)",
                "act-3a", "stu-03", "sem-3", "Feb Activity S3", Date.valueOf(LocalDate.of(2025, 9, 15)));
        jdbc.update("INSERT INTO academic.conduct_activity (id, student_id, semester_id, title, activity_date) VALUES (?, ?, ?, ?, ?)",
                "act-3b", "stu-03", "sem-3", "Dec Activity S3", Date.valueOf(LocalDate.of(2025, 12, 05)));
        jdbc.update("INSERT INTO academic.conduct_activity (id, student_id, semester_id, title, activity_date) VALUES (?, ?, ?, ?, ?)",
                "act-3c", "stu-03", "sem-3", "Oct Activity S3", Date.valueOf(LocalDate.of(2025, 10, 10)));

        StudentConductSummaryDto summary = conductService.studentSummary("stu-03");

        assertEquals(3, summary.history().size());

        // Semester 1 check
        ConductSemesterScoreDto s1 = summary.history().stream().filter(s -> s.semesterId().equals("sem-1")).findFirst().orElseThrow();
        assertEquals(2, s1.activities().size());
        assertEquals("New Activity S1", s1.activities().get(0).title(), "Activity with later date must be first");
        assertEquals("Old Activity S1", s1.activities().get(1).title(), "Activity with earlier date must be second");

        // Semester 2 check
        ConductSemesterScoreDto s2 = summary.history().stream().filter(s -> s.semesterId().equals("sem-2")).findFirst().orElseThrow();
        assertTrue(s2.activities().isEmpty(), "Sem-2 must have 0 activities");

        // Semester 3 check
        ConductSemesterScoreDto s3 = summary.history().stream().filter(s -> s.semesterId().equals("sem-3")).findFirst().orElseThrow();
        assertEquals(3, s3.activities().size());
        assertEquals("Dec Activity S3", s3.activities().get(0).title());
        assertEquals("Oct Activity S3", s3.activities().get(1).title());
        assertEquals("Feb Activity S3", s3.activities().get(2).title());
    }

    @Test
    @DisplayName("Conduct: Lookup resolves identically using profile UUID or official studentCode")
    void conduct_studentProfileLookupByIdOrCode() {
        jdbc.update("INSERT INTO \"academic\".\"Semester\" (\"id\", \"name\", \"startDate\") VALUES (?, ?, ?)",
                "sem-1", "Học kỳ 1 2025-2026", Date.valueOf("2025-09-01"));
        jdbc.update("INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"firstName\", \"lastName\") VALUES (?, ?, ?)",
                "usr-04", "Lan", "Pham");
        jdbc.update("INSERT INTO \"academic\".\"Student\" (\"id\", \"userId\", \"studentId\") VALUES (?, ?, ?)",
                "stu-04-uuid", "usr-04", "22110099");
        jdbc.update("INSERT INTO academic.conduct_semester_score (id, student_id, semester_id, total_score, classification, classification_vi, status) VALUES (?, ?, ?, 88, 'GOOD', 'Tốt', 'CONFIRMED')",
                "sc-4", "stu-04-uuid", "sem-1");
        jdbc.update("INSERT INTO academic.conduct_activity (id, student_id, semester_id, title) VALUES (?, ?, ?, ?)",
                "act-4", "stu-04-uuid", "sem-1", "Olympic Tin học");

        StudentConductSummaryDto byUuid = conductService.studentSummary("stu-04-uuid");
        StudentConductSummaryDto byCode = conductService.studentSummary("22110099");

        assertEquals(byUuid.studentId(), byCode.studentId());
        assertEquals(byUuid.studentCode(), byCode.studentCode());
        assertEquals(byUuid.fullName(), byCode.fullName());
        assertEquals(byUuid.cumulativeAverageScore(), byCode.cumulativeAverageScore());
        assertEquals(1, byCode.history().get(0).activities().size());
    }

    @Test
    @DisplayName("Conduct: Unknown student ID throws ResponseStatusException 404 NOT_FOUND")
    void conduct_nonExistentStudentThrows404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                conductService.studentSummary("non-existent-student"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("Conduct: Multi-student data isolation ensures Student A never receives Student B's activities")
    void conduct_multiStudentDataIsolation() {
        jdbc.update("INSERT INTO \"academic\".\"Semester\" (\"id\", \"name\", \"startDate\") VALUES (?, ?, ?)",
                "sem-1", "Học kỳ 1 2025-2026", Date.valueOf("2025-09-01"));

        // Student A
        jdbc.update("INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"firstName\", \"lastName\") VALUES (?, ?, ?)",
                "usr-A", "A", "Nguyen");
        jdbc.update("INSERT INTO \"academic\".\"Student\" (\"id\", \"userId\", \"studentId\") VALUES (?, ?, ?)",
                "stu-A", "usr-A", "2211000A");
        jdbc.update("INSERT INTO academic.conduct_semester_score (id, student_id, semester_id, total_score, classification, classification_vi, status) VALUES (?, ?, ?, 80, 'GOOD', 'Tốt', 'CONFIRMED')",
                "sc-A", "stu-A", "sem-1");
        jdbc.update("INSERT INTO academic.conduct_activity (id, student_id, semester_id, title) VALUES (?, ?, ?, ?)",
                "act-A1", "stu-A", "sem-1", "Activity of A");

        // Student B
        jdbc.update("INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"firstName\", \"lastName\") VALUES (?, ?, ?)",
                "usr-B", "B", "Tran");
        jdbc.update("INSERT INTO \"academic\".\"Student\" (\"id\", \"userId\", \"studentId\") VALUES (?, ?, ?)",
                "stu-B", "usr-B", "2211000B");
        jdbc.update("INSERT INTO academic.conduct_semester_score (id, student_id, semester_id, total_score, classification, classification_vi, status) VALUES (?, ?, ?, 85, 'GOOD', 'Tốt', 'CONFIRMED')",
                "sc-B", "stu-B", "sem-1");
        jdbc.update("INSERT INTO academic.conduct_activity (id, student_id, semester_id, title) VALUES (?, ?, ?, ?)",
                "act-B1", "stu-B", "sem-1", "Activity of B");

        StudentConductSummaryDto summaryA = conductService.studentSummary("stu-A");
        StudentConductSummaryDto summaryB = conductService.studentSummary("stu-B");

        assertEquals(1, summaryA.history().get(0).activities().size());
        assertEquals("Activity of A", summaryA.history().get(0).activities().get(0).title());

        assertEquals(1, summaryB.history().get(0).activities().size());
        assertEquals("Activity of B", summaryB.history().get(0).activities().get(0).title());
    }

    // =========================================================================
    // SECTION 2: AcademicMutationService Batching, Arithmetic & Rollback Safety
    // =========================================================================

    @Test
    @DisplayName("Mutation: Multi-student batch correctly updates grades and verifies exact 50/50 arithmetic and letter boundaries")
    void gradeMutation_multiStudentBatchSuccessAndPrecision() {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-batch", "sem-01", "lec-01", "OPEN");

        // Insert 4 students
        for (int i = 1; i <= 4; i++) {
            jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, 'ENROLLED', 'DRAFT')",
                    "enr-" + i, "stu-" + i, "sec-batch");
        }

        List<GradeUpdate> grades = List.of(
                new GradeUpdate("enr-1", new BigDecimal("10.0"), new BigDecimal("10.0")), // 10.00 -> A+
                new GradeUpdate("enr-2", new BigDecimal("8.55"), new BigDecimal("8.55")), // 8.55 -> A
                new GradeUpdate("enr-3", new BigDecimal("8.49"), new BigDecimal("8.49")), // 8.49 -> B+
                new GradeUpdate("enr-4", new BigDecimal("3.99"), new BigDecimal("3.99"))  // 3.99 -> F
        );

        mutationServiceProxy.updateGrades("sec-batch", "lec-01", false, grades);

        // Verify total StudentGrade rows = 8 (2 components x 4 students)
        Integer totalStudentGrades = jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"StudentGrade\"", Integer.class);
        assertEquals(8, totalStudentGrades);

        // S1: 10.00 -> A+
        assertEquals(new BigDecimal("10.00"), jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-1'", BigDecimal.class));
        assertEquals("A+", jdbc.queryForObject("SELECT \"letterGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-1'", String.class));

        // S2: 8.55 -> A
        assertEquals(new BigDecimal("8.55"), jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-2'", BigDecimal.class));
        assertEquals("A", jdbc.queryForObject("SELECT \"letterGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-2'", String.class));

        // S3: 8.49 -> B+
        assertEquals(new BigDecimal("8.49"), jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-3'", BigDecimal.class));
        assertEquals("B+", jdbc.queryForObject("SELECT \"letterGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-3'", String.class));

        // S4: 3.99 -> F
        assertEquals(new BigDecimal("3.99"), jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-4'", BigDecimal.class));
        assertEquals("F", jdbc.queryForObject("SELECT \"letterGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-4'", String.class));
    }

    @Test
    @DisplayName("Mutation: Transaction Rollback when an invalid score (> 10.0) is present in batch — NO student grades persisted")
    void gradeMutation_transactionRollbackOnInvalidScore() {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-rb1", "sem-01", "lec-01", "OPEN");

        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, 'ENROLLED', 'DRAFT')",
                "enr-valid-1", "stu-1", "sec-rb1");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, 'ENROLLED', 'DRAFT')",
                "enr-invalid-2", "stu-2", "sec-rb1");

        List<GradeUpdate> grades = List.of(
                new GradeUpdate("enr-valid-1", new BigDecimal("8.0"), new BigDecimal("8.0")),
                new GradeUpdate("enr-invalid-2", new BigDecimal("10.5"), new BigDecimal("8.0")) // 10.5 is illegal!
        );

        DomainException ex = assertThrows(DomainException.class, () ->
                mutationServiceProxy.updateGrades("sec-rb1", "lec-01", false, grades));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("GRADE_SCORE_OUT_OF_RANGE", ex.getCode());

        // Verify that enr-valid-1 was NOT updated (rolled back)
        BigDecimal validGrade = jdbc.queryForObject(
                "SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-valid-1'", BigDecimal.class);
        assertNull(validGrade, "Valid enrollment finalGrade must remain null due to transaction rollback");

        Integer studentGradeCount = jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"StudentGrade\"", Integer.class);
        assertEquals(0, studentGradeCount, "Zero student grade rows must be written to DB");
    }

    @Test
    @DisplayName("Mutation: Transaction Rollback when an enrollment does not exist in batch")
    void gradeMutation_transactionRollbackOnNonExistentEnrollment() {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-rb2", "sem-01", "lec-01", "OPEN");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, 'ENROLLED', 'DRAFT')",
                "enr-ok-1", "stu-1", "sec-rb2");

        List<GradeUpdate> grades = List.of(
                new GradeUpdate("enr-ok-1", new BigDecimal("7.0"), new BigDecimal("7.0")),
                new GradeUpdate("enr-ghost-999", new BigDecimal("8.0"), new BigDecimal("8.0"))
        );

        DomainException ex = assertThrows(DomainException.class, () ->
                mutationServiceProxy.updateGrades("sec-rb2", "lec-01", false, grades));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("ENROLLMENT_NOT_FOUND", ex.getCode());

        assertNull(jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-ok-1'", BigDecimal.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"StudentGrade\"", Integer.class));
    }

    @Test
    @DisplayName("Mutation: Transaction Rollback when an enrollment belongs to a different section")
    void gradeMutation_transactionRollbackOnSectionMismatch() {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-A", "sem-01", "lec-01", "OPEN");
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-B", "sem-01", "lec-01", "OPEN");

        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, 'ENROLLED', 'DRAFT')",
                "enr-A1", "stu-1", "sec-A");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, 'ENROLLED', 'DRAFT')",
                "enr-B1", "stu-2", "sec-B");

        // Target sec-A, but include enrollment from sec-B
        List<GradeUpdate> grades = List.of(
                new GradeUpdate("enr-A1", new BigDecimal("8.0"), new BigDecimal("8.0")),
                new GradeUpdate("enr-B1", new BigDecimal("8.0"), new BigDecimal("8.0"))
        );

        DomainException ex = assertThrows(DomainException.class, () ->
                mutationServiceProxy.updateGrades("sec-A", "lec-01", false, grades));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("GRADE_SECTION_MISMATCH", ex.getCode());

        assertNull(jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-A1'", BigDecimal.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"StudentGrade\"", Integer.class));
    }

    @Test
    @DisplayName("Mutation: Transaction Rollback when an enrollment is DROPPED (not gradeable)")
    void gradeMutation_transactionRollbackOnNonGradeableStatus() {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-drop", "sem-01", "lec-01", "OPEN");

        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, 'ENROLLED', 'DRAFT')",
                "enr-active", "stu-1", "sec-drop");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, 'DROPPED', 'DRAFT')",
                "enr-dropped", "stu-2", "sec-drop");

        List<GradeUpdate> grades = List.of(
                new GradeUpdate("enr-active", new BigDecimal("8.0"), new BigDecimal("8.0")),
                new GradeUpdate("enr-dropped", new BigDecimal("8.0"), new BigDecimal("8.0"))
        );

        DomainException ex = assertThrows(DomainException.class, () ->
                mutationServiceProxy.updateGrades("sec-drop", "lec-01", false, grades));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("ENROLLMENT_NOT_GRADEABLE", ex.getCode());

        assertNull(jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-active'", BigDecimal.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"StudentGrade\"", Integer.class));
    }

    @Test
    @DisplayName("Mutation: Transaction Rollback when grades are already PUBLISHED (immutable lock)")
    void gradeMutation_transactionRollbackOnPublishedGrades() {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-pub", "sem-01", "lec-01", "OPEN");

        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\", \"finalGrade\") VALUES (?, ?, ?, 'ENROLLED', 'DRAFT', NULL)",
                "enr-draft", "stu-1", "sec-pub");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\", \"finalGrade\") VALUES (?, ?, ?, 'ENROLLED', 'PUBLISHED', 9.0)",
                "enr-published", "stu-2", "sec-pub");

        List<GradeUpdate> grades = List.of(
                new GradeUpdate("enr-draft", new BigDecimal("7.0"), new BigDecimal("7.0")),
                new GradeUpdate("enr-published", new BigDecimal("5.0"), new BigDecimal("5.0"))
        );

        DomainException ex = assertThrows(DomainException.class, () ->
                mutationServiceProxy.updateGrades("sec-pub", "lec-01", false, grades));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("GRADES_PUBLISHED_LOCKED", ex.getCode());

        assertNull(jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-draft'", BigDecimal.class));
        assertEquals(new BigDecimal("9.00"), jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-published'", BigDecimal.class));
    }

    @Test
    @DisplayName("Mutation: Re-updating grades cleanly deletes previous draft scores without orphaned records")
    void gradeMutation_idempotentReupdateReplacesComponentsWithoutDuplicates() {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-re", "sem-01", "lec-01", "OPEN");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, 'ENROLLED', 'DRAFT')",
                "enr-re-1", "stu-1", "sec-re");

        // First update: 6.0 and 6.0 -> 6.00 (C)
        mutationServiceProxy.updateGrades("sec-re", "lec-01", false, List.of(
                new GradeUpdate("enr-re-1", new BigDecimal("6.0"), new BigDecimal("6.0"))
        ));

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"StudentGrade\"", Integer.class));
        assertEquals(new BigDecimal("6.00"), jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-re-1'", BigDecimal.class));
        assertEquals("C", jdbc.queryForObject("SELECT \"letterGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-re-1'", String.class));

        // Second update: 9.0 and 9.0 -> 9.00 (A+)
        mutationServiceProxy.updateGrades("sec-re", "lec-01", false, List.of(
                new GradeUpdate("enr-re-1", new BigDecimal("9.0"), new BigDecimal("9.0"))
        ));

        // Must still have exactly 2 rows, not 4!
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"StudentGrade\"", Integer.class));
        assertEquals(new BigDecimal("9.00"), jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-re-1'", BigDecimal.class));
        assertEquals("A+", jdbc.queryForObject("SELECT \"letterGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-re-1'", String.class));
    }

    @Test
    @DisplayName("Mutation: Lecturer authorization forbids non-assigned lecturer unless admin")
    void gradeMutation_lecturerAuthorizationGuard() {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-auth", "sem-01", "lec-assigned", "OPEN");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, 'ENROLLED', 'DRAFT')",
                "enr-auth-1", "stu-1", "sec-auth");

        List<GradeUpdate> grades = List.of(
                new GradeUpdate("enr-auth-1", new BigDecimal("8.0"), new BigDecimal("8.0"))
        );

        // Non-assigned lecturer -> FORBIDDEN
        DomainException ex = assertThrows(DomainException.class, () ->
                mutationServiceProxy.updateGrades("sec-auth", "lec-other", false, grades));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("SECTION_FORBIDDEN", ex.getCode());

        // Admin flag = true -> SUCCESS even if lecturerId doesn't match
        mutationServiceProxy.updateGrades("sec-auth", "admin-usr", true, grades);
        assertEquals(new BigDecimal("8.00"), jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-auth-1'", BigDecimal.class));
    }

    @Test
    @DisplayName("Mutation: Duplicate enrollment in batch merges last-write-wins like the per-row save")
    void gradeMutation_duplicateEnrollmentInBatchMergesLastWriteWins() {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\") VALUES (?, ?, ?, ?)",
                "sec-dup", "sem-01", "lec-01", "OPEN");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"gradeStatus\") VALUES (?, ?, ?, 'ENROLLED', 'DRAFT')",
                "enr-dup-1", "stu-1", "sec-dup");

        List<GradeUpdate> gradesWithDuplicate = List.of(
                new GradeUpdate("enr-dup-1", new BigDecimal("7.0"), new BigDecimal("7.0")),
                new GradeUpdate("enr-dup-1", new BigDecimal("9.0"), new BigDecimal("9.0"))
        );

        // A replayed or double-submitted row must merge like the per-row save
        // did, never trip the (enrollmentId, gradeItemId) unique index with a 500.
        mutationServiceProxy.updateGrades("sec-dup", "lec-01", false, gradesWithDuplicate);

        // The last write wins.
        assertEquals(0, new BigDecimal("9.0").compareTo(
                jdbc.queryForObject("SELECT \"finalGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enr-dup-1'", BigDecimal.class)));
        // Exactly one PROCESS and one FINAL component survive the merge.
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"StudentGrade\"", Integer.class));
    }
}
