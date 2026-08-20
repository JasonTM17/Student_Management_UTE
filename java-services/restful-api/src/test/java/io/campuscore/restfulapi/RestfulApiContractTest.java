package io.campuscore.restfulapi;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestfulApiContractTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void livenessIsPublicAndIdentifiesTheSingleApp() throws Exception {
        mvc.perform(get("/api/v1/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("restful-api"));
    }

    @Test
    void readinessRequiresTheSharedHealthKey() throws Exception {
        mvc.perform(get("/api/v1/health/readiness"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HTTP_403"));

        mvc.perform(get("/api/v1/health/readiness").header("X-Health-Key", "test-health-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.dependencies[0]").value("application-shell"));
    }

    @Test
    void protectedRoutesRejectAnonymousRequests() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void unknownRoutesReturnTheStableNotFoundEnvelope() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void notificationReadBoundaryIsDisabledByDefault() throws Exception {
        mvc.perform(get("/api/v1/notifications/my").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/notifications").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/notifications/notification-1").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(post("/api/v1/notifications")
                        .with(jwt())
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": "student-user-1",
                                  "title": "Payment posted",
                                  "message": "Your tuition payment was received.",
                                  "type": "SUCCESS"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(patch("/api/v1/notifications/my/notification-1/read").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(patch("/api/v1/notifications/my/read-all").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/v1/notifications/my/notification-1").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/v1/notifications/notification-1").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(put("/api/v1/notifications/notification-1")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"title\":\"Updated\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void engagementReadBoundaryIsDisabledByDefault() throws Exception {
        mvc.perform(get("/api/v1/announcements/my").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(post("/api/v1/announcements")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"title\":\"Welcome\",\"content\":\"Welcome\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(put("/api/v1/announcements/announcement-1")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"title\":\"Updated\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/v1/announcements/announcement-1")
                        .with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/support-tickets/my").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(post("/api/v1/support-tickets")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"subject\":\"Need help\",\"description\":\"Cannot login\",\"category\":\"AUTH\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(post("/api/v1/support-tickets/ticket-1/respond")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"message\":\"We are checking this.\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(put("/api/v1/support-tickets/ticket-1")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(post("/api/v1/support-tickets/ticket-1/assign")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"assignedTo\":\"lecturer-1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/v1/support-tickets/ticket-1")
                        .with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void academicReadBoundaryIsDisabledByDefault() throws Exception {
        mvc.perform(get("/api/v1/semesters").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/courses").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void academicEnrollmentReadBoundaryIsDisabledByDefault() throws Exception {
        mvc.perform(get("/api/v1/enrollments/my").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/enrollments").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/enrollments/my/grades").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/grades/student-grades/lecturer/my").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void peopleReadBoundaryIsDisabledByDefault() throws Exception {
        mvc.perform(get("/api/v1/students").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/lecturers").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void financeReadBoundaryIsDisabledByDefault() throws Exception {
        mvc.perform(get("/api/v1/finance/my/invoices").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/finance/invoices").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/finance/payments").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void analyticsReadBoundaryIsDisabledByDefault() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/analytics/finance-summary").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/analytics/enrollments-by-semester").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/analytics/section-occupancy").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/analytics/top-courses").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/analytics/grade-distribution").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/analytics/notification-summary").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void authLoginBoundaryIsDisabledByDefault() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"student@campuscore.edu\",\"password\":\"secret\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/auth/me")
                        .with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(put("/api/v1/auth/profile")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(post("/api/v1/auth/change-password")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"oldPassword\":\"password123\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(post("/api/v1/auth/logout")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void thesisRoundReadBoundaryIsDisabledByDefault() throws Exception {
        mvc.perform(get("/api/v1/thesis/rounds").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void thesisGroupReadBoundaryIsDisabledByDefault() throws Exception {
        mvc.perform(get("/api/v1/thesis/groups").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void thesisCouncilReadBoundaryIsDisabledByDefault() throws Exception {
        mvc.perform(get("/api/v1/thesis/councils").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void thesisAssistantBoundaryIsDisabledByDefault() throws Exception {
        mvc.perform(post("/api/v1/thesis/assistant/chat")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"message\":\"How do I choose a topic?\",\"locale\":\"en\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void identityClaimsAreAvailableToEveryFutureModule() throws Exception {
        mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token
                        .subject("user-123")
                        .claim("roles", List.of("STUDENT"))
                        .claim("permissions", List.of("thesis:read"))
                        .claim("studentId", "student-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("user-123"))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.permissions[0]").value("thesis:read"))
                .andExpect(jsonPath("$.studentId").value("student-123"));
    }

    @Test
    void authenticatedMutationUsesTheStableRestContract() throws Exception {
        mvc.perform(post("/api/v1/contract/ping")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"message\":\"shell\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.echo").value("shell"))
                .andExpect(jsonPath("$.writer").value("restful-api-shell"));
    }

    @Test
    void malformedJsonReturnsTheStableRequestEnvelope() throws Exception {
        mvc.perform(post("/api/v1/contract/ping")
                        .with(jwt())
                        .contentType("application/json")
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void malformedBearerTokensReturnTheStableAuthenticationEnvelope() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void invalidMutationReturnsAStableValidationEnvelope() throws Exception {
        mvc.perform(post("/api/v1/contract/ping")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.message").value("message is required"));
    }
}
