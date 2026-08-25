package io.campuscore.restfulapi;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
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
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/health/readiness").header("X-Health-Key", "test-health-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.dependencies[0]").value("postgresql"));
    }

    @Test
    void protectedRoutesRejectAnonymousRequests() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void integrationContractIsPublicAndIdentifiesTheSingleJavaApi() throws Exception {
        mvc.perform(get("/api/v1/contract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("restful-api"))
                .andExpect(jsonPath("$.architecture").value("single-java-api"));
    }

    @Test
    void unknownRoutesReturnTheStableNotFoundEnvelope() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void openApiIsPublicAndUsesTheCanonicalPath() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/v1/health/liveness']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications/confirm'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications/resend'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/password-reset-requests'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/password-reset/confirm'].post").exists())
                .andExpect(jsonPath("$.components.schemas.AuthUserResponse.properties.emailVerified").exists());
    }

    @Test
    void enrollmentMutationWithoutIdempotencyKeyUsesProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/me/enrollments")
                        .with(jwt().jwt(token -> token.claim("emailVerified", true))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-1\",\"roundId\":\"round-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void unverifiedStudentTokenCannotUseRegistrationOrCompatibilityMutation() throws Exception {
        var unverified = jwt().jwt(token -> token.claim("emailVerified", false))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
        mvc.perform(get("/api/v1/registration/rounds").with(unverified))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(jwt().jwt(token -> token.claim("emailVerified", false))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .with(csrf())
                        .header("Idempotency-Key", "00000000-0000-4000-8000-000000000001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-1\"}"))
                .andExpect(status().isForbidden());
    }
}
