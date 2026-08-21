package io.campuscore.restfulapi.finance;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "migration.finance-read.enabled=true",
        "spring.flyway.enabled=false"
})
class FinanceReadPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareFinanceFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"finance\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "finance"."Invoice" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "invoiceNumber" VARCHAR(120) UNIQUE NOT NULL,
                    "studentId" VARCHAR(120) NOT NULL,
                    "studentUserId" VARCHAR(120) NOT NULL,
                    "studentDisplayName" VARCHAR(240) NOT NULL,
                    "studentEmail" VARCHAR(320) NOT NULL,
                    "studentCode" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "semesterName" VARCHAR(240) NOT NULL,
                    "semesterNameEn" VARCHAR(240),
                    "semesterNameVi" VARCHAR(240),
                    "status" VARCHAR(40) NOT NULL,
                    "subtotal" DECIMAL(10, 2) NOT NULL,
                    "discount" DECIMAL(10, 2) NOT NULL,
                    "total" DECIMAL(10, 2) NOT NULL,
                    "dueDate" TIMESTAMP NOT NULL,
                    "paidAt" TIMESTAMP,
                    "notes" VARCHAR(1000),
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "finance"."InvoiceItem" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "invoiceId" VARCHAR(120) NOT NULL,
                    "description" VARCHAR(240) NOT NULL,
                    "quantity" INTEGER NOT NULL,
                    "unitPrice" DECIMAL(10, 2) NOT NULL,
                    "total" DECIMAL(10, 2) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "finance"."Payment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "paymentNumber" VARCHAR(120) UNIQUE NOT NULL,
                    "invoiceId" VARCHAR(120) NOT NULL,
                    "studentId" VARCHAR(120) NOT NULL,
                    "amount" DECIMAL(10, 2) NOT NULL,
                    "method" VARCHAR(80) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "paidAt" TIMESTAMP,
                    "transactionId" VARCHAR(160),
                    "paymentIntentId" VARCHAR(120),
                    "notes" VARCHAR(1000),
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("DELETE FROM \"finance\".\"Payment\"");
        jdbc.update("DELETE FROM \"finance\".\"InvoiceItem\"");
        jdbc.update("DELETE FROM \"finance\".\"Invoice\"");
    }

    @Test
    void adminInvoiceReadsPreserveLegacyEnvelopeOrderingFiltersAndHydration() throws Exception {
        insertInvoice("invoice-old", "INV-001", "student-1", "PENDING", BASE_TIME.minusSeconds(60));
        insertInvoice("invoice-new", "INV-002", "student-2", "DRAFT", BASE_TIME.plusSeconds(60));
        insertPayment("payment-new", "PAY-002", "invoice-new", "student-2", "FAILED", BigDecimal.valueOf(200),
                BASE_TIME.plusSeconds(70));
        insertPayment("payment-old", "PAY-001", "invoice-old", "student-1", "COMPLETED", BigDecimal.valueOf(300),
                BASE_TIME.minusSeconds(30));

        mvc.perform(get("/api/v1/finance/invoices")
                        .queryParam("page", "1")
                        .queryParam("limit", "1")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("invoice-new"))
                .andExpect(jsonPath("$.data[0].student.user.email").value("student-2@campuscore.edu"))
                .andExpect(jsonPath("$.data[0].semester.nameEn").value("Fall 2026"))
                .andExpect(jsonPath("$.data[0].paidAmount").value(0))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2));

        mvc.perform(get("/api/v1/finance/invoices")
                        .queryParam("studentId", "student-1")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("invoice-old"))
                .andExpect(jsonPath("$.data[0].status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.data[0].paidAmount").value(300.00))
                .andExpect(jsonPath("$.data[0].balance").value(700.00));

        mvc.perform(get("/api/v1/finance/invoices/invoice-old")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("INV-001"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Tuition"))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].paymentNumber").value("PAY-001"));
    }

    @Test
    void studentInvoiceReadsUseStudentClaimAndKeepOtherStudentsIsolated() throws Exception {
        insertInvoice("invoice-own", "INV-STUDENT", "student-1", "PENDING", BASE_TIME);
        insertInvoice("invoice-other", "INV-OTHER", "student-2", "PENDING", BASE_TIME.plusSeconds(30));

        mvc.perform(get("/api/v1/finance/my/invoices")
                        .with(studentJwt("student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("invoice-own"));

        mvc.perform(get("/api/v1/finance/my/invoices/invoice-other")
                        .with(studentJwt("student-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(get("/api/v1/finance/my/invoices")
                        .with(jwt().jwt(token -> token.subject("student-user")
                                .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HTTP_403"));
    }

    @Test
    void adminPaymentReadsPreserveLegacyEnvelopeFiltersAndInvoiceJoin() throws Exception {
        insertInvoice("invoice-paid", "INV-PAID", "student-1", "PAID", BASE_TIME);
        insertPayment("payment-old", "PAY-OLD", "invoice-paid", "student-1", "COMPLETED", BigDecimal.valueOf(600),
                BASE_TIME.minusSeconds(60));
        insertPayment("payment-new", "PAY-NEW", "invoice-paid", "student-1", "PENDING", BigDecimal.valueOf(400),
                BASE_TIME.plusSeconds(60));

        mvc.perform(get("/api/v1/finance/payments")
                        .queryParam("status", "COMPLETED")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("payment-old"))
                .andExpect(jsonPath("$.data[0].invoice.invoiceNumber").value("INV-PAID"))
                .andExpect(jsonPath("$.meta.total").value(1));

        mvc.perform(get("/api/v1/finance/payments")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("payment-new"))
                .andExpect(jsonPath("$.meta.total").value(2));

        mvc.perform(get("/api/v1/finance/payments/payment-new")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentNumber").value("PAY-NEW"))
                .andExpect(jsonPath("$.invoice.studentEmail").value("student-1@campuscore.edu"));
    }

    @Test
    void readBoundaryFailsClosedForAnonymousWrongRolesInvalidQueriesAndMissingRows() throws Exception {
        mvc.perform(get("/api/v1/finance/invoices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/finance/invoices")
                        .with(studentJwt("student-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/finance/invoices")
                        .queryParam("limit", "101")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/finance/payments")
                        .queryParam("semesterId", "semester-1")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/finance/invoices")
                        .queryParam("page", "1", "2")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/finance/payments/missing")
                        .with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));
    }

    private void insertInvoice(
            String id,
            String number,
            String studentId,
            String status,
            Instant createdAt) {
        jdbc.update(
                "INSERT INTO \"finance\".\"Invoice\""
                        + " (\"id\", \"invoiceNumber\", \"studentId\", \"studentUserId\","
                        + " \"studentDisplayName\", \"studentEmail\", \"studentCode\", \"semesterId\","
                        + " \"semesterName\", \"semesterNameEn\", \"semesterNameVi\", \"status\","
                        + " \"subtotal\", \"discount\", \"total\", \"dueDate\", \"paidAt\", \"notes\","
                        + " \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                number,
                studentId,
                "user-" + studentId,
                displayName(studentId),
                studentId + "@campuscore.edu",
                studentId.toUpperCase(),
                "semester-1",
                "Fall 2026",
                "Fall 2026",
                "Học kỳ Thu 2026",
                status,
                BigDecimal.valueOf(1000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(1000),
                localDateTime(BASE_TIME.plusSeconds(31_536_000)),
                null,
                "Seeded invoice",
                localDateTime(createdAt),
                localDateTime(createdAt));
        jdbc.update(
                "INSERT INTO \"finance\".\"InvoiceItem\""
                        + " (\"id\", \"invoiceId\", \"description\", \"quantity\", \"unitPrice\", \"total\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                "item-" + id,
                id,
                "Tuition",
                1,
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(1000));
    }

    private void insertPayment(
            String id,
            String number,
            String invoiceId,
            String studentId,
            String status,
            BigDecimal amount,
            Instant createdAt) {
        jdbc.update(
                "INSERT INTO \"finance\".\"Payment\""
                        + " (\"id\", \"paymentNumber\", \"invoiceId\", \"studentId\", \"amount\", \"method\","
                        + " \"status\", \"paidAt\", \"transactionId\", \"paymentIntentId\", \"notes\","
                        + " \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                number,
                invoiceId,
                studentId,
                amount,
                "CARD",
                status,
                "COMPLETED".equals(status) ? localDateTime(createdAt) : null,
                "txn-" + id,
                null,
                "Seeded payment",
                localDateTime(createdAt),
                localDateTime(createdAt));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                .subject("admin-user")
                .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor studentJwt(
            String studentId) {
        return jwt().jwt(token -> token
                .subject("student-user")
                .claim("roles", List.of("STUDENT"))
                .claim("studentId", studentId))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private static String displayName(String studentId) {
        return "Student " + studentId.substring(studentId.indexOf('-') + 1);
    }

    private static LocalDateTime localDateTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
