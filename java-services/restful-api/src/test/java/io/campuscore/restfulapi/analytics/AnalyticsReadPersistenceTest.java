package io.campuscore.restfulapi.analytics;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "migration.analytics-read.enabled=true",
        "spring.flyway.enabled=false"
})
class AnalyticsReadPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareAnalyticsFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"public\"");
        createCountTable("Student");
        createCountTable("Lecturer");
        createCountTable("Course");
        createCountTable("Section");
        createCountTable("Department");
        createCountTable("Faculty");
        createCountTable("Classroom");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."AcademicYear" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "year" INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Semester" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(200),
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
                    "academicYearId" VARCHAR(120),
                    "startDate" TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Enrollment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "semesterId" VARCHAR(120),
                    "status" VARCHAR(40),
                    "letterGrade" VARCHAR(10)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Invoice" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "status" VARCHAR(40) NOT NULL,
                    "total" DECIMAL(10, 2) NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Payment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "method" VARCHAR(80) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "amount" DECIMAL(10, 2) NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "public"."Notification" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "title" VARCHAR(200) NOT NULL,
                    "message" VARCHAR(2000) NOT NULL,
                    "type" VARCHAR(40) NOT NULL,
                    "link" VARCHAR(500),
                    "isRead" BOOLEAN NOT NULL DEFAULT FALSE,
                    "readAt" TIMESTAMP,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);

        for (String table : List.of(
                "Notification",
                "Payment",
                "Invoice",
                "Student",
                "Lecturer",
                "Course",
                "Section",
                "Enrollment",
                "Department",
                "Faculty",
                "AcademicYear",
                "Semester",
                "Classroom")) {
            jdbc.update("DELETE FROM \"public\".\"" + table + "\"");
        }
    }

    @Test
    void overviewPreservesLegacyCountShapeForAdmins() throws Exception {
        insertRows("Student", 3);
        insertRows("Lecturer", 2);
        insertRows("Course", 4);
        insertRows("Section", 5);
        insertRows("Enrollment", 6);
        insertRows("Department", 2);
        insertRows("Faculty", 1);
        insertRows("AcademicYear", 2);
        insertRows("Semester", 3);
        insertRows("Classroom", 7);

        mvc.perform(get("/api/v1/analytics/overview").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudents").value(3))
                .andExpect(jsonPath("$.totalLecturers").value(2))
                .andExpect(jsonPath("$.totalCourses").value(4))
                .andExpect(jsonPath("$.totalSections").value(5))
                .andExpect(jsonPath("$.totalEnrollments").value(6))
                .andExpect(jsonPath("$.totalDepartments").value(2))
                .andExpect(jsonPath("$.totalFaculties").value(1))
                .andExpect(jsonPath("$.totalAcademicYears").value(2))
                .andExpect(jsonPath("$.totalSemesters").value(3))
                .andExpect(jsonPath("$.totalClassrooms").value(7));
    }

    @Test
    void enrollmentsBySemesterPreservesLegacyOrderAndConfirmedCompletedFilter() throws Exception {
        insertAcademicYear("academic-year-2026", 2026);
        insertAcademicYear("academic-year-2025", 2025);
        insertSemester(
                "semester-fall-2026",
                "Fall 2026",
                "Fall 2026",
                "Hoc ky Thu 2026",
                "academic-year-2026",
                BASE_TIME.plusSeconds(86_400));
        insertSemester(
                "semester-spring-2026",
                "Spring 2026",
                "Spring 2026",
                "Hoc ky Xuan 2026",
                "academic-year-2026",
                BASE_TIME);
        insertSemester(
                "semester-fall-2025",
                "Fall 2025",
                "Fall 2025",
                "Hoc ky Thu 2025",
                "academic-year-2025",
                BASE_TIME.minusSeconds(86_400));
        insertEnrollmentInSemester("enrollment-fall-confirmed", "semester-fall-2026", "CONFIRMED");
        insertEnrollmentInSemester("enrollment-fall-completed", "semester-fall-2026", "COMPLETED");
        insertEnrollmentInSemester("enrollment-fall-pending", "semester-fall-2026", "PENDING");
        insertEnrollmentInSemester("enrollment-spring-completed", "semester-spring-2026", "COMPLETED");
        insertEnrollmentInSemester("enrollment-old-pending", "semester-fall-2025", "PENDING");

        mvc.perform(get("/api/v1/analytics/enrollments-by-semester").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].semesterId").value("semester-fall-2026"))
                .andExpect(jsonPath("$[0].semesterName").value("Fall 2026"))
                .andExpect(jsonPath("$[0].semesterNameEn").value("Fall 2026"))
                .andExpect(jsonPath("$[0].semesterNameVi").value("Hoc ky Thu 2026"))
                .andExpect(jsonPath("$[0].academicYear").value(2026))
                .andExpect(jsonPath("$[0].enrollmentCount").value(2))
                .andExpect(jsonPath("$[1].semesterId").value("semester-spring-2026"))
                .andExpect(jsonPath("$[1].enrollmentCount").value(1));
    }

    @Test
    void financeSummaryPreservesLegacyAggregatesAndFinanceOfficerAccess() throws Exception {
        insertInvoice("invoice-pending", "PENDING", BigDecimal.valueOf(1000));
        insertInvoice("invoice-overdue", "OVERDUE", BigDecimal.valueOf(700));
        insertInvoice("invoice-paid", "PAID", BigDecimal.valueOf(500));
        insertPayment("payment-card-completed", "CARD", "COMPLETED", BigDecimal.valueOf(450));
        insertPayment("payment-cash-completed", "CASH", "COMPLETED", BigDecimal.valueOf(50));
        insertPayment("payment-card-failed", "CARD", "FAILED", BigDecimal.valueOf(120));

        mvc.perform(get("/api/v1/analytics/finance-summary").with(financeOfficerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.totalInvoiced").value(2200.00))
                .andExpect(jsonPath("$.totals.paidAmount").value(500.00))
                .andExpect(jsonPath("$.totals.outstandingAmount").value(1700.00))
                .andExpect(jsonPath("$.totals.pendingInvoices").value(1))
                .andExpect(jsonPath("$.totals.overdueInvoices").value(1))
                .andExpect(jsonPath("$.totals.failedPayments").value(1))
                .andExpect(jsonPath("$.invoiceStatus.length()").value(3))
                .andExpect(jsonPath("$.paymentStatus.length()").value(2))
                .andExpect(jsonPath("$.providerFunnel.length()").value(3));
    }

    @Test
    void gradeDistributionPreservesLegacyBucketsAndPercentagesForAdmins() throws Exception {
        insertEnrollment("enrollment-a-1", "COMPLETED", "A");
        insertEnrollment("enrollment-a-2", "COMPLETED", "A");
        insertEnrollment("enrollment-b-plus", "COMPLETED", "B+");
        insertEnrollment("enrollment-f", "COMPLETED", "F");
        insertEnrollment("enrollment-confirmed", "CONFIRMED", "A");
        insertEnrollment("enrollment-ungraded", "COMPLETED", null);

        mvc.perform(get("/api/v1/analytics/grade-distribution").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].grade").value("A"))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[0].percentage").value(50))
                .andExpect(jsonPath("$[1].grade").value("A-"))
                .andExpect(jsonPath("$[1].count").value(0))
                .andExpect(jsonPath("$[1].percentage").value(0))
                .andExpect(jsonPath("$[2].grade").value("B+"))
                .andExpect(jsonPath("$[2].count").value(1))
                .andExpect(jsonPath("$[2].percentage").value(25))
                .andExpect(jsonPath("$[11].grade").value("F"))
                .andExpect(jsonPath("$[11].count").value(1))
                .andExpect(jsonPath("$[11].percentage").value(25));
    }

    @Test
    void notificationSummaryPreservesLegacyAggregateShapeForAdmins() throws Exception {
        insertNotification(
                "notification-info-read",
                "INFO",
                true,
                BASE_TIME.minusSeconds(300),
                "Welcome",
                "Welcome message");
        insertNotification(
                "notification-warning",
                "WARNING",
                false,
                BASE_TIME.minusSeconds(100),
                "Capacity warning",
                "A section is near capacity.");
        insertNotification(
                "notification-error-new",
                "ERROR",
                false,
                BASE_TIME,
                "Delivery failed",
                "Email provider failed.");
        insertNotification(
                "notification-success",
                "SUCCESS",
                true,
                BASE_TIME.minusSeconds(200),
                "Payment posted",
                "Payment notification delivered.");

        mvc.perform(get("/api/v1/analytics/notification-summary").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.unread").value(2))
                .andExpect(jsonPath("$.read").value(2))
                .andExpect(jsonPath("$.byType.length()").value(4))
                .andExpect(jsonPath("$.byType[0].type").value("ERROR"))
                .andExpect(jsonPath("$.byType[0].count").value(1))
                .andExpect(jsonPath("$.recentAttention.length()").value(2))
                .andExpect(jsonPath("$.recentAttention[0].id").value("notification-error-new"))
                .andExpect(jsonPath("$.recentAttention[0].title").value("Delivery failed"))
                .andExpect(jsonPath("$.recentAttention[0].message").value("Email provider failed."))
                .andExpect(jsonPath("$.recentAttention[0].type").value("ERROR"))
                .andExpect(jsonPath("$.recentAttention[0].createdAt").value("2026-08-20T00:00:00Z"))
                .andExpect(jsonPath("$.recentAttention[1].id").value("notification-warning"));
    }

    @Test
    void analyticsReadBoundaryFailsClosedForAnonymousRolesAndUnexpectedQueries() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/analytics/overview").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/finance-summary").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/enrollments-by-semester").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/grade-distribution").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/notification-summary").with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/analytics/overview")
                        .queryParam("months", "12")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/enrollments-by-semester")
                        .queryParam("months", "12")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/grade-distribution")
                        .queryParam("semesterId", "semester-1")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/analytics/notification-summary")
                        .queryParam("months", "12")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private void createCountTable(String table) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"public\".\"" + table + "\" ("
                + "\"id\" VARCHAR(120) PRIMARY KEY)");
    }

    private void insertRows(String table, int count) {
        for (int index = 1; index <= count; index++) {
            jdbc.update(
                    "INSERT INTO \"public\".\"" + table + "\" (\"id\") VALUES (?)",
                    table.toLowerCase() + "-" + index);
        }
    }

    private void insertAcademicYear(String id, int year) {
        jdbc.update(
                "INSERT INTO \"public\".\"AcademicYear\" (\"id\", \"year\") VALUES (?, ?)",
                id,
                year);
    }

    private void insertSemester(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String academicYearId,
            Instant startDate) {
        jdbc.update(
                "INSERT INTO \"public\".\"Semester\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"academicYearId\", \"startDate\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id,
                name,
                nameEn,
                nameVi,
                academicYearId,
                localDateTime(startDate));
    }

    private void insertInvoice(String id, String status, BigDecimal total) {
        jdbc.update(
                "INSERT INTO \"public\".\"Invoice\""
                        + " (\"id\", \"status\", \"total\", \"createdAt\") VALUES (?, ?, ?, ?)",
                id,
                status,
                total,
                localDateTime(BASE_TIME));
    }

    private void insertEnrollment(String id, String status, String letterGrade) {
        jdbc.update(
                "INSERT INTO \"public\".\"Enrollment\""
                        + " (\"id\", \"semesterId\", \"status\", \"letterGrade\") VALUES (?, ?, ?, ?)",
                id,
                null,
                status,
                letterGrade);
    }

    private void insertEnrollmentInSemester(String id, String semesterId, String status) {
        jdbc.update(
                "INSERT INTO \"public\".\"Enrollment\""
                        + " (\"id\", \"semesterId\", \"status\", \"letterGrade\") VALUES (?, ?, ?, ?)",
                id,
                semesterId,
                status,
                null);
    }

    private void insertPayment(String id, String method, String status, BigDecimal amount) {
        jdbc.update(
                "INSERT INTO \"public\".\"Payment\""
                        + " (\"id\", \"method\", \"status\", \"amount\", \"createdAt\") VALUES (?, ?, ?, ?, ?)",
                id,
                method,
                status,
                amount,
                localDateTime(BASE_TIME));
    }

    private void insertNotification(
            String id,
            String type,
            boolean read,
            Instant createdAt,
            String title,
            String message) {
        jdbc.update(
                "INSERT INTO \"public\".\"Notification\""
                        + " (\"id\", \"userId\", \"title\", \"message\", \"type\", \"link\","
                        + " \"isRead\", \"readAt\", \"createdAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                "student-user",
                title,
                message,
                type,
                null,
                read,
                read ? localDateTime(createdAt.plusSeconds(1)) : null,
                localDateTime(createdAt));
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                .subject("admin-user")
                .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor financeOfficerJwt() {
        return jwt().jwt(token -> token
                .subject("finance-user")
                .claim("roles", List.of("FINANCE_OFFICER")))
                .authorities(new SimpleGrantedAuthority("ROLE_FINANCE_OFFICER"));
    }

    private static RequestPostProcessor studentJwt() {
        return jwt().jwt(token -> token
                .subject("student-user")
                .claim("roles", List.of("STUDENT")))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private static LocalDateTime localDateTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
