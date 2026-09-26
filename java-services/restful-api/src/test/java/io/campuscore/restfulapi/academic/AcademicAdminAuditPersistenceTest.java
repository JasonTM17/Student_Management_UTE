package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.campuscore.restfulapi.academic.registration.RegistrationService;
import io.campuscore.restfulapi.academic.service.AcademicMutationService;
import io.campuscore.restfulapi.academic.web.AcademicMutationDtos.GradeUpdate;
import io.campuscore.restfulapi.audit.AdminAuditRecorder;
import io.campuscore.restfulapi.web.DomainException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Audit findings S4 and S3-quick: an admin hard delete leaves both an
 * EnrollmentEvent (action ADMIN_DELETE) and an AdminAudit row with the
 * before-state; an admin grade save/publish leaves an AdminAudit row with the
 * actor and section. The owning-lecturer path writes no audit row and the
 * response contracts are unchanged.
 */
class AcademicAdminAuditPersistenceTest {

    private JdbcTemplate jdbc;
    private AcademicMutationService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:academic_audit_test_" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbc = new JdbcTemplate(dataSource);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);

        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_audit\"");

        jdbc.execute("""
            CREATE TABLE "academic"."Section" (
                "id" VARCHAR(64) PRIMARY KEY,
                "semesterId" VARCHAR(64),
                "lecturerId" VARCHAR(64),
                "capacity" INT DEFAULT 50,
                "enrolledCount" INT DEFAULT 0,
                "status" VARCHAR(32) DEFAULT 'OPEN',
                "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbc.execute("""
            CREATE TABLE "academic"."Enrollment" (
                "id" VARCHAR(64) PRIMARY KEY,
                "studentId" VARCHAR(64) NOT NULL,
                "sectionId" VARCHAR(64) NOT NULL,
                "semesterId" VARCHAR(64),
                "status" VARCHAR(32) NOT NULL,
                "gradeStatus" VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
                "enrolledAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                "finalGrade" NUMERIC(5,2),
                "letterGrade" VARCHAR(8),
                "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbc.execute("""
            CREATE TABLE "academic"."EnrollmentEvent" (
                "id" VARCHAR(64) PRIMARY KEY,
                "enrollmentId" VARCHAR(64) NOT NULL,
                "studentId" VARCHAR(64) NOT NULL,
                "sectionId" VARCHAR(64) NOT NULL,
                "action" VARCHAR(16) NOT NULL,
                "actorId" VARCHAR(64) NOT NULL,
                "requestHash" CHAR(64),
                "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
            CREATE TABLE "academic"."StudentGrade" (
                "id" VARCHAR(64) PRIMARY KEY,
                "enrollmentId" VARCHAR(64) NOT NULL,
                "gradeItemId" VARCHAR(128) NOT NULL,
                "score" NUMERIC(5,2)
            )
        """);
        jdbc.execute("""
            CREATE TABLE "campuscore_audit"."AdminAudit" (
                "id" VARCHAR(64) PRIMARY KEY,
                "actorId" VARCHAR(64),
                "actorLabel" VARCHAR(240),
                "action" VARCHAR(48) NOT NULL,
                "entityType" VARCHAR(48) NOT NULL,
                "entityId" VARCHAR(64),
                "summary" VARCHAR(500),
                "beforeState" CLOB,
                "afterState" CLOB,
                "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        service = new AcademicMutationService(
                namedJdbc,
                mock(io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService.class),
                mock(RegistrationService.class),
                new AdminAuditRecorder(namedJdbc));
    }

    private void seedEnrollment(String enrollmentId, String status, String gradeStatus) {
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"semesterId\", \"lecturerId\", \"status\")"
                        + " SELECT 'sec-audit', 'sem-01', 'lec-01', 'OPEN'"
                        + " WHERE NOT EXISTS (SELECT 1 FROM \"academic\".\"Section\" WHERE \"id\" = 'sec-audit')");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"gradeStatus\")"
                        + " VALUES (?, 'stu-9', 'sec-audit', 'sem-01', ?, ?)",
                enrollmentId, status, gradeStatus);
    }

    private int auditCount(String action) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_audit\".\"AdminAudit\" WHERE \"action\" = ?",
                Integer.class, action);
        return count == null ? 0 : count;
    }

    @Test
    void adminHardDeleteWritesEnrollmentEventAndAuditRow() {
        seedEnrollment("enr-del-1", "ENROLLED", "DRAFT");

        service.deleteEnrollment("enr-del-1", "admin-user");

        assertThat(jdbc.queryForObject(
                "SELECT \"action\" FROM \"academic\".\"EnrollmentEvent\" WHERE \"enrollmentId\" = ?",
                String.class, "enr-del-1")).isEqualTo("ADMIN_DELETE");
        assertThat(jdbc.queryForObject(
                "SELECT \"actorId\" FROM \"academic\".\"EnrollmentEvent\" WHERE \"enrollmentId\" = ?",
                String.class, "enr-del-1")).isEqualTo("admin-user");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                Integer.class, "enr-del-1")).isZero();
        assertThat(auditCount("DELETED")).isEqualTo(1);
        String beforeState = jdbc.queryForObject(
                "SELECT \"beforeState\" FROM \"campuscore_audit\".\"AdminAudit\""
                        + " WHERE \"entityType\" = 'ENROLLMENT' AND \"entityId\" = ?",
                String.class, "enr-del-1");
        assertThat(beforeState).contains("stu-9").contains("ENROLLED");
    }

    @Test
    void adminGradeSaveAndPublishLeaveAuditRowsWhileLecturerPathDoesNot() {
        seedEnrollment("enr-grade-1", "ENROLLED", "DRAFT");

        // Lecturer (owner) path: no audit.
        service.updateGrades("sec-audit", "lec-01", false, "lec-01",
                List.of(new GradeUpdate("enr-grade-1", new BigDecimal("8"), new BigDecimal("9"))));
        assertThat(auditCount("GRADE_UPDATE_BY_ADMIN")).isZero();

        // Admin path (not the owning lecturer): audit with actor + section.
        service.updateGrades("sec-audit", "lec-01", true, "admin-user",
                List.of(new GradeUpdate("enr-grade-1", new BigDecimal("8"), new BigDecimal("9"))));
        assertThat(auditCount("GRADE_UPDATE_BY_ADMIN")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT \"entityId\" FROM \"campuscore_audit\".\"AdminAudit\" WHERE \"action\" = 'GRADE_UPDATE_BY_ADMIN'",
                String.class)).isEqualTo("sec-audit");

        // Publish by admin: audit row again.
        service.publishGrades("sec-audit", "lec-01", true, "admin-user");
        assertThat(auditCount("GRADE_PUBLISH_BY_ADMIN")).isEqualTo(1);

        // Publish by the owning lecturer: no audit row.
        seedEnrollment("enr-grade-2", "ENROLLED", "DRAFT");
        service.updateGrades("sec-audit", "lec-01", false, "lec-01",
                List.of(new GradeUpdate("enr-grade-2", new BigDecimal("7"), new BigDecimal("7"))));
        service.publishGrades("sec-audit", "lec-01", false, "lec-01");
        assertThat(auditCount("GRADE_PUBLISH_BY_ADMIN")).isEqualTo(1);
    }

    @Test
    void gradeMutationPermissionsAreUnchanged() {
        seedEnrollment("enr-forbid-1", "ENROLLED", "DRAFT");

        // A non-owner lecturer is still rejected exactly as before.
        try {
            service.updateGrades("sec-audit", "lec-other", false, "lec-other",
                    List.of(new GradeUpdate("enr-forbid-1", new BigDecimal("8"), new BigDecimal("9"))));
            throw new AssertionError("expected SECTION_FORBIDDEN");
        } catch (DomainException expected) {
            assertThat(expected.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(auditCount("GRADE_UPDATE_BY_ADMIN")).isZero();
    }
}
