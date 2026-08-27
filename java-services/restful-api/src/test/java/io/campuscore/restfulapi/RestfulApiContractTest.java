package io.campuscore.restfulapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
                .andExpect(jsonPath("$.paths['/api/v1/health/liveness']").exists());
    }
}
