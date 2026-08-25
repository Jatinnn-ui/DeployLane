package com.deployforge.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Thread pools.
 *
 * <p>Deployments are long running (git clone, npm install, docker build) and must never occupy a
 * request thread, so they run on a dedicated bounded pool sized by
 * {@code deployforge.deployment.worker-concurrency}. Bounded on purpose: a single node cannot build
 * ten images at once, and queueing is the correct behaviour under load.
 */
@Configuration
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    public static final String DEPLOYMENT_EXECUTOR = "deploymentExecutor";
    public static final String EVENT_EXECUTOR = "eventExecutor";

    @Bean(name = DEPLOYMENT_EXECUTOR)
    public Executor deploymentExecutor(DeployForgeProperties properties) {
        int concurrency = properties.deployment().workerConcurrency();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("df-deploy-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("deployment_executor_ready concurrency={}", concurrency);
        return executor;
    }

    /** Short lived fan-out work: WebSocket broadcasts, notifications, AI analysis kick-off. */
    @Bean(name = EVENT_EXECUTOR)
    public Executor eventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("df-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("df-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }
}
