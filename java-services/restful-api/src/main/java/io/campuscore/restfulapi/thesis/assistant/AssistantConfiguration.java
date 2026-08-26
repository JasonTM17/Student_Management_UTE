package io.campuscore.restfulapi.thesis.assistant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({DeepSeekProperties.class, AssistantProperties.class, AssistantRagProperties.class})
public class AssistantConfiguration {

    /**
     * Keeps provider-backed SSE work off servlet request threads while retaining
     * a bounded local bulkhead. The provider has its own network semaphore; this
     * executor also bounds queued HTTP/SSE orchestration work and fails fast when
     * the local assistant lane is saturated.
     */
    @Bean(name = "assistantStreamExecutor")
    public ThreadPoolTaskExecutor assistantStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("campuscore-assistant-stream-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
