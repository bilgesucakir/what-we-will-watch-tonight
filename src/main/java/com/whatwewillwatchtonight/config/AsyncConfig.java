package com.watchlistintersector.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    /**
     * Shared executor for the app's blocking I/O: watchlist fetches and
     * poster lookups. Inject it with {@code @Qualifier("ioExecutor")}, since
     * Spring Boot also auto-configures its own {@code applicationTaskExecutor}
     * bean of the same {@code Executor} type.
     *
     * @return a virtual-thread-per-task executor
     */
    @Bean
    public Executor ioExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
