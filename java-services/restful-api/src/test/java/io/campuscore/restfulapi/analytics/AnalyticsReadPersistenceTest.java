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
        createCountTable("Enrollment");
        createCountTable("Department");
        createCountTable("Faculty");
        createCountTable("AcademicYear");
        createCountTable("Semester");
        createCountTable("Classroom");
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

        for (String table : List.of(
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

        mvc.perform(get("/api/v1/analytics/overview")
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

    private void insertInvoice(String id, String status, BigDecimal total) {
        jdbc.update(
                "INSERT INTO \"public\".\"Invoice\""
                        + " (\"id\", \"status\", \"total\", \"createdAt\") VALUES (?, ?, ?, ?)",
                id,
                status,
                total,
                localDateTime(BASE_TIME));
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
