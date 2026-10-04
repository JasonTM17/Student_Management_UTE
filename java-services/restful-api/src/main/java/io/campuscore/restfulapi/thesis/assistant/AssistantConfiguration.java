package io.campuscore.restfulapi.thesis.assistant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({DeepSeekProperties.class, AssistantProperties.class, AssistantRagProperties.class,
        SupabaseKnowledgeProperties.class})
public class AssistantConfiguration {

    /**
     * Dedicated relay pool for internal RAG callback streams. Provider-side
     * results must never compete with student waiters for the four student
     * workers: with a shared pool, four in-flight student streams made the
     * provider's completion callback rejection-listed, dropping a finished
     * answer. The bean is deliberately NOT primary — by-type injection keeps
     * resolving to the student pool.
     */
    @org.springframework.context.annotation.Bean(name = "assistantInternalStreamExecutor")
    @org.springframework.context.annotation.Profile("persistence")
    public AssistantStreamExecutor assistantInternalStreamExecutor() {
        return new AssistantStreamExecutor("assistant-internal-stream-", 4);
    }
}
