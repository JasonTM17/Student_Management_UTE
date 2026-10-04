package io.campuscore.restfulapi.thesis.assistant;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Direct handoff keeps slow streams off servlet threads without queuing
 * unreserved turns. This instance serves STUDENT streams; the internal RAG
 * callback relay owns a separate {@code assistantInternalStreamExecutor} pool
 * so provider-side results can always be delivered even when every student
 * worker is busy waiting on the provider.
 */
@Component
@Primary
@Profile("persistence")
public final class AssistantStreamExecutor implements DisposableBean, AutoCloseable {
    private final ThreadPoolTaskExecutor workers = new ThreadPoolTaskExecutor();

    public AssistantStreamExecutor() {
        this("assistant-stream-", 4);
    }

    AssistantStreamExecutor(String threadNamePrefix, int parallelism) {
        workers.setCorePoolSize(parallelism);
        workers.setMaxPoolSize(parallelism);
        workers.setQueueCapacity(0);
        workers.setThreadNamePrefix(threadNamePrefix);
        workers.setDaemon(true);
        workers.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        workers.initialize();
    }

    public void execute(Runnable task) {
        workers.execute(task);
    }

    @Override
    public void destroy() {
        workers.shutdown();
    }

    @Override
    public void close() {
        destroy();
    }
}
