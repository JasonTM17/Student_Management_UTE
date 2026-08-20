package io.campuscore.restfulapi.engagement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        "migration.engagement-write.enabled=true",
        "spring.flyway.enabled=false"
})
class SupportTicketWritePersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareWriteFixture() {
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
                    "updatedAt" TIMESTAMP NOT NULL,
                    CONSTRAINT "uk_support_ticket_number" UNIQUE ("ticketNumber")
                )
                """);
        jdbc.update("DELETE FROM \"engagement\".\"SupportTicket\"");
        jdbc.update(
                "INSERT INTO \"engagement\".\"SupportTicket\""
                        + " (\"id\", \"ticketNumber\", \"userId\", \"userEmail\", \"userDisplayName\","
                        + " \"subject\", \"description\", \"category\", \"priority\", \"status\","
                        + " \"assignedTo\", \"assignedToDisplayName\", \"resolvedAt\", \"closedAt\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "existing-ticket",
                "TKT-00001",
                "existing-user",
                "existing@campuscore.edu",
                "Existing User",
                "Existing",
                "Existing ticket",
                "GENERAL",
                "LOW",
                "OPEN",
                null,
                null,
                null,
                null,
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
    }

    @Test
    void createTicketPersistsOpenTicketWithLegacyNumberAndUserShape() throws Exception {
        mvc.perform(post("/api/v1/support-tickets")
                        .with(userJwt("user-1", "student@campuscore.edu", "Student", "One"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "subject": "Need help",
                                  "description": "Cannot open the dashboard",
                                  "category": "TECHNICAL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNumber").value("TKT-00002"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.user.email").value("student@campuscore.edu"))
                .andExpect(jsonPath("$.user.displayName").value("Student One"))
                .andExpect(jsonPath("$.subject").value("Need help"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.responses.length()").value(0));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"SupportTicket\" WHERE \"ticketNumber\" = 'TKT-00002'"
                        + " AND \"userId\" = 'user-1' AND \"priority\" = 'MEDIUM' AND \"status\" = 'OPEN'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    void createTicketPreservesProvidedPriorityAndEmailDisplayFallback() throws Exception {
        mvc.perform(post("/api/v1/support-tickets")
                        .with(userJwt("user-2", "fallback@campuscore.edu", null, null))
                        .contentType("application/json")
                        .content("""
                                {
                                  "subject": "Payment",
                                  "description": "Need invoice help",
                                  "category": "FINANCE",
                                  "priority": "CRITICAL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNumber").value("TKT-00002"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.user.displayName").value("fallback@campuscore.edu"));
    }

    @Test
    void createTicketUsesMaxLegacyTicketNumberInsteadOfRowCount() throws Exception {
        jdbc.update("DELETE FROM \"engagement\".\"SupportTicket\" WHERE \"ticketNumber\" = 'TKT-00001'");
        seedTicket("ticket-two", "TKT-00002");

        mvc.perform(post("/api/v1/support-tickets")
                        .with(userJwt("user-3", "student3@campuscore.edu", "Student", "Three"))
                        .contentType("application/json")
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNumber").value("TKT-00003"));
    }

    @Test
    void createBoundaryFailsClosedForAnonymousInvalidClaimsAndBadRequests() throws Exception {
        mvc.perform(post("/api/v1/support-tickets")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(post("/api/v1/support-tickets")
                        .with(jwt().jwt(token -> token
                                .subject("user-without-email")
                                .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType("application/json")
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(post("/api/v1/support-tickets")
                        .with(userJwt("user-1", "student@campuscore.edu", "Student", "One"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "subject": "Need help",
                                  "description": "Cannot open the dashboard",
                                  "category": "TECHNICAL",
                                  "priority": "URGENT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/v1/support-tickets")
                        .with(userJwt("user-1", "student@campuscore.edu", "Student", "One"))
                        .contentType("application/json")
                        .content("{\"description\":\"Missing subject\",\"category\":\"TECHNICAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static RequestPostProcessor userJwt(
            String subject,
            String email,
            String firstName,
            String lastName) {
        return jwt().jwt(token -> {
                    token.subject(subject)
                            .claim("email", email)
                            .claim("roles", List.of("STUDENT"));
                    if (firstName != null) {
                        token.claim("firstName", firstName);
                    }
                    if (lastName != null) {
                        token.claim("lastName", lastName);
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private static String validBody() {
        return """
                {
                  "subject": "Need help",
                  "description": "Cannot open the dashboard",
                  "category": "TECHNICAL"
                }
                """;
    }

    private void seedTicket(String id, String ticketNumber) {
        jdbc.update(
                "INSERT INTO \"engagement\".\"SupportTicket\""
                        + " (\"id\", \"ticketNumber\", \"userId\", \"userEmail\", \"userDisplayName\","
                        + " \"subject\", \"description\", \"category\", \"priority\", \"status\","
                        + " \"assignedTo\", \"assignedToDisplayName\", \"resolvedAt\", \"closedAt\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                ticketNumber,
                "existing-user",
                "existing@campuscore.edu",
                "Existing User",
                "Existing",
                "Existing ticket",
                "GENERAL",
                "LOW",
                "OPEN",
                null,
                null,
                null,
                null,
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
    }

    private static java.time.LocalDateTime localDateTime(Instant value) {
        return java.time.LocalDateTime.ofInstant(value, java.time.ZoneOffset.UTC);
    }
}
