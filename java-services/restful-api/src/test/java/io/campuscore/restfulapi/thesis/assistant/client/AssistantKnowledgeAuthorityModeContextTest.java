package io.campuscore.restfulapi.thesis.assistant.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.thesis.assistant.properties.AssistantRagProperties;
import io.campuscore.restfulapi.thesis.assistant.properties.SupabaseKnowledgeProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class AssistantKnowledgeAuthorityModeContextTest {

    @Test
    void remoteModeCreatesOnlyTheRemoteKnowledgeAuthority() {
        contextFor("remote").run(context -> {
            assertNull(context.getStartupFailure(),
                    () -> "remote mode failed to start: " + context.getStartupFailure());

            assertNotNull(context.getBean(AssistantKnowledgeAuthorityGateway.class));
            assertTrue(context.getBeansOfType(SupabaseKnowledgeAuthorityService.class).isEmpty());
        });
    }

    @Test
    void supabaseModeCreatesOnlyTheSupabaseKnowledgeAuthority() {
        contextFor("supabase").run(context -> {
            assertNull(context.getStartupFailure(),
                    () -> "supabase mode failed to start: " + context.getStartupFailure());

            assertNotNull(context.getBean(SupabaseKnowledgeAuthorityService.class));
            assertTrue(context.getBeansOfType(AssistantKnowledgeAuthorityGateway.class).isEmpty());
        });
    }

    private static ApplicationContextRunner contextFor(String authorityMode) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    Map<String, Object> environment = new HashMap<>();
                    environment.put("SPRING_PROFILES_ACTIVE", "persistence");
                    environment.put("ASSISTANT_KNOWLEDGE_AUTHORITY_MODE", authorityMode);
                    environment.put("ASSISTANT_RAG_BASE_URL", "http://127.0.0.1:4011");
                    environment.put("ASSISTANT_RAG_SERVICE_TOKEN", "test-rag-token");
                    environment.put("ASSISTANT_SUPABASE_ENABLED", "true");
                    environment.put("ASSISTANT_SUPABASE_URL", "https://project.example.invalid");
                    environment.put("ASSISTANT_SUPABASE_SERVICE_ROLE_KEY", "test-service-role-key");
                    context.getEnvironment().getPropertySources().addFirst(
                            new SystemEnvironmentPropertySource("testEnvironment", environment));
                })
                .withUserConfiguration(AuthorityModeTestConfiguration.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({AssistantRagProperties.class, SupabaseKnowledgeProperties.class})
    @Import({AssistantKnowledgeAuthorityGateway.class, SupabaseKnowledgeAuthorityService.class})
    static class AuthorityModeTestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return tools.jackson.databind.json.JsonMapper.builder().build();
        }
    }
}
