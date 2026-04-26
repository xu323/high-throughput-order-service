package com.xu.orderservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class AsyncConfig {

    @Value("${async.core-pool-size:8}")
    private int corePoolSize;

    @Value("${async.max-pool-size:32}")
    private int maxPoolSize;

    @Value("${async.queue-capacity:500}")
    private int queueCapacity;

    @Value("${async.thread-name-prefix:hto-async-}")
    private String threadNamePrefix;

    /**
     * 全專案共用的非同步執行緒池。
     * 設計重點：
     *   - corePoolSize：常駐執行緒數量；保留即使閒置也不被回收。
     *   - maxPoolSize：尖峰時最多能擴張到的執行緒數量。
     *   - queueCapacity：當核心執行緒滿時，先放入佇列等待。
     *   - 拒絕策略：CallerRunsPolicy → 由提交任務的執行緒自行執行，
     *     避免任務直接被丟棄；同時對上游形成自然的反壓。
     */
    @Bean(name = "asyncTaskExecutor")
    public Executor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("Initialized asyncTaskExecutor core={}, max={}, queue={}", corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
}
