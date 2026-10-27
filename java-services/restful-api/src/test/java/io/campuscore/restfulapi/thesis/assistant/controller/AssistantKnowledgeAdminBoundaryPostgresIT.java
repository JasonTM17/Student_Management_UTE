package io.campuscore.restfulapi.thesis.assistant.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.thesis.ThesisAuthorityFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Pins the HTTP contract of the knowledge-governance boundary: an authenticated
 * caller without ADMIN/SUPER_ADMIN authority is refused with
 * {@code ASSISTANT_ADMIN_REQUIRED}, the code the {@code ADMIN_GOVERNANCE} RLS
 * boundary produces. Real boundary enforcement needs
 * {@code assistant.rls.test-mode=false}, so this runs against PostgreSQL like
 * {@code AssistantRlsPostgresIT}. Write verbs are denied earlier at the URL
 * level and keep their {@code ACCESS_DENIED} envelope.
 */
@EnabledIfEnvironmentVariable(named = "ASSISTANT_RLS_POSTGRES_URL", matches = "jdbc:postgresql:.+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.schemas=thesis,academic,campuscore_auth",
        "spring.flyway.default-schema=thesis",
        "spring.jpa.hibernate.ddl-auto=none",
        "assistant.rls.test-mode=false",
        "assistant.rag.service-mode=true",
        "assistant.rag.service-token=rls-it-internal-token-9fb407c5",
        "assistant.retention.enabled=false",
        "assistant.supabase.enabled=false",
        "deepseek.enabled=false",
        "spring.task.scheduling.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("persistence")
class AssistantKnowledgeAdminBoundaryPostgresIT {

    private static final String[] BASE_PATHS = {
            "/api/v1/admin/assistant/knowledge",
            "/api/v1/admin/thesis/assistant/knowledge"};

    @Autowired MockMvc mvc;
    @Autowired @Qualifier("namedParameterJdbcTemplate") NamedParameterJdbcTemplate primaryJdbc;

    @Test
    void nonAdminRolesAreRefusedWithAssistantAdminRequiredOnBothListRoutes() throws Exception {
        for (String role : new String[] {"LECTURER", "TRUONG_BO_MON", "TRUONG_KHOA", "STUDENT"}) {
            RequestPostProcessor caller = caller("ka-boundary-" + role.toLowerCase(), role);
            for (String base : BASE_PATHS) {
                mvc.perform(get(base).with(caller))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("ASSISTANT_ADMIN_REQUIRED"));
            }
        }
    }

    @Test
    void nonAdminDocumentReadIsRefusedWithAssistantAdminRequiredOnBothRoutes() throws Exception {
        RequestPostProcessor caller = caller("ka-boundary-reader", "LECTURER");
        for (String base : BASE_PATHS) {
            mvc.perform(get(base + "/{id}", UUID.randomUUID()).with(caller))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ASSISTANT_ADMIN_REQUIRED"));
        }
    }

    @Test
    void nonAdminWritesKeepTheUrlLevelAccessDeniedEnvelope() throws Exception {
        RequestPostProcessor caller = caller("ka-boundary-writer", "LECTURER");
        for (String base : BASE_PATHS) {
            mvc.perform(post(base).with(caller)
                            .contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void adminsAndSuperAdminsStillListKnowledgeOnBothRoutes() throws Exception {
        RequestPostProcessor admin = caller("ka-boundary-admin", "ADMIN");
        for (String base : BASE_PATHS) {
            mvc.perform(get(base).with(admin)).andExpect(status().isOk());
        }
        mvc.perform(get(BASE_PATHS[0]).with(caller("ka-boundary-super", "SUPER_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCallersStayUnauthenticated() throws Exception {
        for (String base : BASE_PATHS) {
            mvc.perform(get(base)).andExpect(status().isUnauthorized());
        }
    }

    private RequestPostProcessor caller(String subject, String role) {
        return ThesisAuthorityFixture.role(jdbc(), subject, role);
    }

    private JdbcTemplate jdbc() {
        return primaryJdbc.getJdbcTemplate();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("ASSISTANT_RLS_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("ASSISTANT_RLS_PRIMARY_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("ASSISTANT_RLS_PRIMARY_PASSWORD", ""));
        registry.add("assistant.datasource.url", () -> requiredEnv("ASSISTANT_RLS_POSTGRES_URL"));
        registry.add("assistant.datasource.username", () -> "campuscore_assistant_runtime");
        registry.add("assistant.datasource.password", () -> requiredEnv("ASSISTANT_RLS_RUNTIME_PASSWORD"));
        registry.add("assistant.datasource.maximum-pool-size", () -> "1");
        registry.add("assistant.rls.test-mode", () -> "false");
        registry.add("security.jwt.secret", () -> requiredEnv("ASSISTANT_RLS_JWT_SECRET"));
        registry.add("security.jwt.refresh-secret", () -> requiredEnv("ASSISTANT_RLS_JWT_REFRESH_SECRET"));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for PostgreSQL RLS integration tests");
        return value;
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
