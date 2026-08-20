package io.campuscore.restfulapi.engagement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
        "migration.engagement-read.enabled=true",
        "spring.flyway.enabled=false"
})
class SupportTicketReadPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareReadOnlyFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"engagement\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "engagement"."SupportTicket" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "ticketNumber" VARCHAR(120) NOT NULL,
                    "userId" VARCHAR(120) NOT NULL,
                    "userEmail" VARCHAR(200) NOT NULL,
                    "userDisplayName" VARCHAR(200),
                    "subject" VARCHAR(240) NOT NULL,
                    "description" VARCHAR(2000) NOT NULL,
                    "category" VARCHAR(80) NOT NULL,
                    "priority" VARCHAR(40) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "assignedTo" VARCHAR(120),
                    "assignedToDisplayName" VARCHAR(200),
                    "resolvedAt" TIMESTAMP,
                    "closedAt" TIMESTAMP,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "engagement"."TicketResponse" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "ticketId" VARCHAR(120) NOT NULL,
                    "userId" VARCHAR(120) NOT NULL,
                    "userEmail" VARCHAR(200) NOT NULL,
                    "userDisplayName" VARCHAR(200),
                    "message" VARCHAR(2000) NOT NULL,
                    "isInternal" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("DELETE FROM \"engagement\".\"TicketResponse\"");
        jdbc.update("DELETE FROM \"engagement\".\"SupportTicket\"");
        insertFixture();
    }

    @Test
    void myTicketsPreserveLegacyEnvelopeOrderingAndSubjectIsolation() throws Exception {
        mvc.perform(get("/api/v1/support-tickets/my").with(userJwt("user-1", "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("ticket-2"))
                .andExpect(jsonPath("$.data[0].ticketNumber").value("TKT-00002"))
                .andExpect(jsonPath("$.data[0].user.id").value("user-1"))
                .andExpect(jsonPath("$.data[0].user.displayName").value("student@campuscore.edu"))
                .andExpect(jsonPath("$.data[1].id").value("ticket-1"))
                .andExpect(jsonPath("$.data[1].responses.length()").value(1))
                .andExpect(jsonPath("$.data[1].responses[0].user.displayName").value("Student One"))
                .andExpect(jsonPath("$.data[1].responses[0].isInternal").value(false))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.totalPages").value(1));

        mvc.perform(get("/api/v1/support-tickets/my/ticket-3").with(userJwt("user-1", "STUDENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));
    }

    @Test
    void myTicketDetailReturnsOnlyOwnedTicketWithResponses() throws Exception {
        mvc.perform(get("/api/v1/support-tickets/my/ticket-1").with(userJwt("user-1", "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ticket-1"))
                .andExpect(jsonPath("$.subject").value("Login help"))
                .andExpect(jsonPath("$.responses.length()").value(1))
                .andExpect(jsonPath("$.responses[0].createdAt").value("2026-08-20T00:01:00.000Z"))
                .andExpect(jsonPath("$.createdAt").value("2026-08-20T00:00:00.000Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-20T00:00:10.000Z"));
    }

    @Test
    void adminTicketsPreserveFiltersEnvelopeAndDetailAccess() throws Exception {
        mvc.perform(get("/api/v1/support-tickets")
                        .queryParam("status", "OPEN")
                        .queryParam("priority", "HIGH")
                        .queryParam("category", "AUTH")
                        .with(userJwt("admin-user", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("ticket-1"))
                .andExpect(jsonPath("$.data[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(1));

        mvc.perform(get("/api/v1/support-tickets/ticket-3").with(userJwt("admin-user", "SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ticket-3"))
                .andExpect(jsonPath("$.user.id").value("user-2"));

        mvc.perform(get("/api/v1/support-tickets/ticket-1").with(userJwt("admin-user", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responses.length()").value(2))
                .andExpect(jsonPath("$.responses[1].isInternal").value(true));
    }

    @Test
    void readBoundaryFailsClosedForAnonymousRolesMissingSubjectAndBadQueries() throws Exception {
        mvc.perform(get("/api/v1/support-tickets/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/support-tickets").with(userJwt("user-1", "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/support-tickets/my")
                        .with(jwt().jwt(token -> token
                                .subject("")
                                .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HTTP_403"));

        mvc.perform(get("/api/v1/support-tickets/my")
                        .queryParam("limit", "201")
                        .with(userJwt("user-1", "STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/support-tickets")
                        .queryParam("priority", "URGENT")
                        .with(userJwt("admin-user", "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/support-tickets/my")
                        .queryParam("unexpected", "value")
                        .with(userJwt("user-1", "STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private void insertFixture() {
        insertTicket(
                "ticket-1",
                "TKT-00001",
                "user-1",
                "student@campuscore.edu",
                "Student One",
                "Login help",
                "Cannot log in",
                "AUTH",
                "HIGH",
                "OPEN",
                null,
                null,
                0,
                10);
        insertTicket(
                "ticket-2",
                "TKT-00002",
                "user-1",
                "student@campuscore.edu",
                null,
                "Payment question",
                "Invoice mismatch",
                "FINANCE",
                "MEDIUM",
                "IN_PROGRESS",
                "support-1",
                "Support One",
                20,
                30);
        insertTicket(
                "ticket-3",
                "TKT-00003",
                "user-2",
                "other@campuscore.edu",
                "Other Student",
                "Course question",
                "Need course info",
                "ACADEMIC",
                "LOW",
                "RESOLVED",
                null,
                null,
                40,
                50);
        insertResponse("response-1", "ticket-1", "user-1", "student@campuscore.edu", "Student One", "Please help", false, 60);
        insertResponse("response-2", "ticket-1", "support-1", "support@campuscore.edu", "Support One", "Internal note", true, 120);
        insertResponse("response-3", "ticket-3", "support-1", "support@campuscore.edu", "Support One", "Done", false, 180);
    }

    private void insertTicket(
            String id,
            String ticketNumber,
            String userId,
            String userEmail,
            String userDisplayName,
            String subject,
            String description,
            String category,
            String priority,
            String status,
            String assignedTo,
            String assignedToDisplayName,
            long createdOffsetSeconds,
            long updatedOffsetSeconds) {
        jdbc.update(
                "INSERT INTO \"engagement\".\"SupportTicket\""
                        + " (\"id\", \"ticketNumber\", \"userId\", \"userEmail\", \"userDisplayName\","
                        + " \"subject\", \"description\", \"category\", \"priority\", \"status\","
                        + " \"assignedTo\", \"assignedToDisplayName\", \"resolvedAt\", \"closedAt\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                ticketNumber,
                userId,
                userEmail,
                userDisplayName,
                subject,
                description,
                category,
                priority,
                status,
                assignedTo,
                assignedToDisplayName,
                null,
                null,
                localDateTime(BASE_TIME.plusSeconds(createdOffsetSeconds)),
                localDateTime(BASE_TIME.plusSeconds(updatedOffsetSeconds)));
    }

    private void insertResponse(
            String id,
            String ticketId,
            String userId,
            String userEmail,
            String userDisplayName,
            String message,
            boolean isInternal,
            long createdOffsetSeconds) {
        jdbc.update(
                "INSERT INTO \"engagement\".\"TicketResponse\""
                        + " (\"id\", \"ticketId\", \"userId\", \"userEmail\", \"userDisplayName\","
                        + " \"message\", \"isInternal\", \"createdAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                ticketId,
                userId,
                userEmail,
                userDisplayName,
                message,
                isInternal,
                localDateTime(BASE_TIME.plusSeconds(createdOffsetSeconds)));
    }

    private static RequestPostProcessor userJwt(String subject, String role) {
        return jwt().jwt(token -> token
                        .subject(subject)
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static java.time.LocalDateTime localDateTime(Instant value) {
        return java.time.LocalDateTime.ofInstant(value, java.time.ZoneOffset.UTC);
    }
}
