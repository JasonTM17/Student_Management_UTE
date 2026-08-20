package io.campuscore.restfulapi.engagement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        jdbc.execute("DROP TABLE IF EXISTS \"engagement\".\"TicketResponse\"");
        jdbc.execute("DROP TABLE IF EXISTS \"engagement\".\"SupportTicket\"");
        jdbc.execute("""
                CREATE TABLE "engagement"."SupportTicket" (
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
        jdbc.execute("""
                CREATE TABLE "engagement"."TicketResponse" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "ticketId" VARCHAR(120) NOT NULL,
                    "userId" VARCHAR(120) NOT NULL,
                    "userEmail" VARCHAR(200) NOT NULL,
                    "userDisplayName" VARCHAR(200),
                    "message" VARCHAR(2000) NOT NULL,
                    "isInternal" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    CONSTRAINT "fk_ticket_response_ticket"
                        FOREIGN KEY ("ticketId")
                        REFERENCES "engagement"."SupportTicket" ("id")
                        ON DELETE CASCADE
                )
                """);
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
    void createTicketRejectsExhaustedLegacyTicketNumberRange() throws Exception {
        jdbc.update("DELETE FROM \"engagement\".\"SupportTicket\"");
        seedTicket("ticket-max", "TKT-9223372036854775807");

        mvc.perform(post("/api/v1/support-tickets")
                        .with(userJwt("user-4", "student4@campuscore.edu", "Student", "Four"))
                        .contentType("application/json")
                        .content(validBody()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        Integer malformedNumbers = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"SupportTicket\" WHERE \"ticketNumber\" LIKE 'TKT--%'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, malformedNumbers);
    }

    @Test
    void adminRespondsToOpenTicketAndMovesItInProgress() throws Exception {
        mvc.perform(post("/api/v1/support-tickets/existing-ticket/respond")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "message": "We refreshed your enrollment cache. Please try again."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketId").value("existing-ticket"))
                .andExpect(jsonPath("$.userId").value("admin-1"))
                .andExpect(jsonPath("$.user.email").value("admin@campuscore.edu"))
                .andExpect(jsonPath("$.user.displayName").value("Admin One"))
                .andExpect(jsonPath("$.message").value("We refreshed your enrollment cache. Please try again."))
                .andExpect(jsonPath("$.isInternal").value(false));

        Integer responses = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"TicketResponse\""
                        + " WHERE \"ticketId\" = 'existing-ticket' AND \"userId\" = 'admin-1'"
                        + " AND \"isInternal\" = FALSE",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, responses);

        String status = jdbc.queryForObject(
                "SELECT \"status\" FROM \"engagement\".\"SupportTicket\" WHERE \"id\" = 'existing-ticket'",
                String.class);
        org.junit.jupiter.api.Assertions.assertEquals("IN_PROGRESS", status);
    }

    @Test
    void respondPreservesInternalFlagAndDoesNotReopenClosedTickets() throws Exception {
        jdbc.update(
                "UPDATE \"engagement\".\"SupportTicket\" SET \"status\" = 'CLOSED' WHERE \"id\" = 'existing-ticket'");

        mvc.perform(post("/api/v1/support-tickets/existing-ticket/respond")
                        .with(adminJwt("admin-2", "admin2@campuscore.edu", null, null))
                        .contentType("application/json")
                        .content("""
                                {
                                  "message": "Internal note",
                                  "isInternal": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isInternal").value(true))
                .andExpect(jsonPath("$.user.displayName").value("admin2@campuscore.edu"));

        String status = jdbc.queryForObject(
                "SELECT \"status\" FROM \"engagement\".\"SupportTicket\" WHERE \"id\" = 'existing-ticket'",
                String.class);
        org.junit.jupiter.api.Assertions.assertEquals("CLOSED", status);
    }

    @Test
    void respondAllowsBlankMessageLikeLegacyValidation() throws Exception {
        mvc.perform(post("/api/v1/support-tickets/existing-ticket/respond")
                        .with(adminJwt("admin-3", "admin3@campuscore.edu", "Admin", "Three"))
                        .contentType("application/json")
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(""));

        Integer blankResponses = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"TicketResponse\""
                        + " WHERE \"ticketId\" = 'existing-ticket' AND \"userId\" = 'admin-3'"
                        + " AND \"message\" = ''",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, blankResponses);
    }

    @Test
    void adminUpdatesTicketFieldsAndResolvedTimestamp() throws Exception {
        seedResponse("response-1", "existing-ticket", "agent-1", "agent@campuscore.edu", "Agent One");

        mvc.perform(put("/api/v1/support-tickets/existing-ticket")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "subject": "Updated subject",
                                  "description": "Updated description",
                                  "category": "ACADEMIC",
                                  "priority": "HIGH",
                                  "status": "RESOLVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("existing-ticket"))
                .andExpect(jsonPath("$.subject").value("Updated subject"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.category").value("ACADEMIC"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists())
                .andExpect(jsonPath("$.closedAt").doesNotExist())
                .andExpect(jsonPath("$.responses.length()").value(1))
                .andExpect(jsonPath("$.responses[0].user.displayName").value("Agent One"));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"SupportTicket\""
                        + " WHERE \"id\" = 'existing-ticket' AND \"status\" = 'RESOLVED'"
                        + " AND \"resolvedAt\" IS NOT NULL",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    void adminClosesTicketWithoutClearingResolvedTimestamp() throws Exception {
        jdbc.update(
                "UPDATE \"engagement\".\"SupportTicket\""
                        + " SET \"status\" = 'RESOLVED', \"resolvedAt\" = ? WHERE \"id\" = 'existing-ticket'",
                localDateTime(BASE_TIME.plusSeconds(60)));

        mvc.perform(put("/api/v1/support-tickets/existing-ticket")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.resolvedAt").exists())
                .andExpect(jsonPath("$.closedAt").exists());
    }

    @Test
    void adminUpdatesBlankTextFieldsAndRejectsBlankEnumsLikeLegacyValidation() throws Exception {
        mvc.perform(put("/api/v1/support-tickets/existing-ticket")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("{\"subject\":\"\",\"description\":\"\",\"category\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(""))
                .andExpect(jsonPath("$.description").value(""))
                .andExpect(jsonPath("$.category").value(""));

        Integer blankTextRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"SupportTicket\""
                        + " WHERE \"id\" = 'existing-ticket' AND \"subject\" = ''"
                        + " AND \"description\" = '' AND \"category\" = ''",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, blankTextRows);

        mvc.perform(put("/api/v1/support-tickets/existing-ticket")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("{\"status\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(put("/api/v1/support-tickets/existing-ticket")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("{\"priority\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void adminAssignsTicketWithoutInventingDisplayName() throws Exception {
        seedResponse("response-1", "existing-ticket", "agent-1", "agent@campuscore.edu", "Agent One");

        mvc.perform(post("/api/v1/support-tickets/existing-ticket/assign")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("{\"assignedTo\":\"lecturer-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("existing-ticket"))
                .andExpect(jsonPath("$.assignedTo").value("lecturer-1"))
                .andExpect(jsonPath("$.assignedToDisplayName").doesNotExist())
                .andExpect(jsonPath("$.responses.length()").value(1));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"SupportTicket\""
                        + " WHERE \"id\" = 'existing-ticket' AND \"assignedTo\" = 'lecturer-1'"
                        + " AND \"assignedToDisplayName\" IS NULL",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
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

    @Test
    void respondBoundaryFailsClosedForStudentMissingTicketInvalidClaimsAndBadRequests() throws Exception {
        mvc.perform(post("/api/v1/support-tickets/existing-ticket/respond")
                        .with(userJwt("user-1", "student@campuscore.edu", "Student", "One"))
                        .contentType("application/json")
                        .content("{\"message\":\"student response\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(post("/api/v1/support-tickets/missing-ticket/respond")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("{\"message\":\"missing ticket\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(post("/api/v1/support-tickets/existing-ticket/respond")
                        .with(jwt().jwt(token -> token
                                .subject("admin-without-email")
                                .claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType("application/json")
                        .content("{\"message\":\"invalid claims\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(post("/api/v1/support-tickets/existing-ticket/respond")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateBoundaryFailsClosedForStudentMissingTicketInvalidStatusAndPriority() throws Exception {
        mvc.perform(put("/api/v1/support-tickets/existing-ticket")
                        .with(userJwt("user-1", "student@campuscore.edu", "Student", "One"))
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(put("/api/v1/support-tickets/missing-ticket")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(put("/api/v1/support-tickets/existing-ticket")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(put("/api/v1/support-tickets/existing-ticket")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("{\"priority\":\"URGENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void assignBoundaryFailsClosedForStudentMissingTicketAndMissingAssignee() throws Exception {
        mvc.perform(post("/api/v1/support-tickets/existing-ticket/assign")
                        .with(userJwt("user-1", "student@campuscore.edu", "Student", "One"))
                        .contentType("application/json")
                        .content("{\"assignedTo\":\"lecturer-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(post("/api/v1/support-tickets/missing-ticket/assign")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("{\"assignedTo\":\"lecturer-1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(post("/api/v1/support-tickets/existing-ticket/assign")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void adminDeletesTicketAndDatabaseCascadesResponses() throws Exception {
        seedResponse("response-1", "existing-ticket", "agent-1", "agent@campuscore.edu", "Agent One");

        mvc.perform(delete("/api/v1/support-tickets/existing-ticket")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Support ticket deleted successfully"));

        Integer tickets = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"SupportTicket\" WHERE \"id\" = 'existing-ticket'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, tickets);

        Integer responses = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"TicketResponse\" WHERE \"ticketId\" = 'existing-ticket'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, responses);
    }

    @Test
    void deleteBoundaryFailsClosedForStudentAndMissingTicket() throws Exception {
        mvc.perform(delete("/api/v1/support-tickets/existing-ticket")
                        .with(userJwt("user-1", "student@campuscore.edu", "Student", "One")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(delete("/api/v1/support-tickets/missing-ticket")
                        .with(adminJwt("admin-1", "admin@campuscore.edu", "Admin", "One")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));
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

    private static RequestPostProcessor adminJwt(
            String subject,
            String email,
            String firstName,
            String lastName) {
        return jwt().jwt(token -> {
                    token.subject(subject)
                            .claim("email", email)
                            .claim("roles", List.of("ADMIN"));
                    if (firstName != null) {
                        token.claim("firstName", firstName);
                    }
                    if (lastName != null) {
                        token.claim("lastName", lastName);
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
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

    private void seedResponse(String id, String ticketId, String userId, String email, String displayName) {
        jdbc.update(
                "INSERT INTO \"engagement\".\"TicketResponse\""
                        + " (\"id\", \"ticketId\", \"userId\", \"userEmail\", \"userDisplayName\","
                        + " \"message\", \"isInternal\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                ticketId,
                userId,
                email,
                displayName,
                "Existing response",
                false,
                localDateTime(BASE_TIME.plusSeconds(30)));
    }

    private static java.time.LocalDateTime localDateTime(Instant value) {
        return java.time.LocalDateTime.ofInstant(value, java.time.ZoneOffset.UTC);
    }
}
