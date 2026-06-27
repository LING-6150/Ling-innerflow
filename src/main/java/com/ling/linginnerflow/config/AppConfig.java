// src/main/java/com/ling/linginnerflow/config/AppConfig.java
package com.ling.linginnerflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Dedicated thread pool for async memory compression.
     * Keeps compression LLM calls off the WebSocket / Reactor threads.
     */
    @Bean(name = "memoryCompressionExecutor")
    public Executor memoryCompressionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("mem-compress-");
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated, bounded thread pool for async emotion-image (DALL-E) generation.
     * Replaces unmanaged {@code new Thread(...).start()} on connection close so
     * image generation cannot spawn unbounded threads under load and is shut
     * down cleanly with the context.
     */
    @Bean(name = "emotionImageExecutor")
    public Executor emotionImageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("emotion-image-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}