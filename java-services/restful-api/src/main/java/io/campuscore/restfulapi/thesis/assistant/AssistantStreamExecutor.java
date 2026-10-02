package io.campuscore.restfulapi.thesis.assistant;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** Direct handoff keeps slow streams off servlet threads without queuing unreserved turns. */
@Component
@Profile("persistence")
public final class AssistantStreamExecutor implements DisposableBean, AutoCloseable {
    private final ThreadPoolTaskExecutor workers = new ThreadPoolTaskExecutor();

    public AssistantStreamExecutor() {
        this(4);
    }

    AssistantStreamExecutor(int parallelism) {
        workers.setCorePoolSize(parallelism);
        workers.setMaxPoolSize(parallelism);
        workers.setQueueCapacity(0);
        workers.setThreadNamePrefix("assistant-stream-");
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
