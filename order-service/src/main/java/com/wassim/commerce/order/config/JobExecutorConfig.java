package com.wassim.commerce.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class JobExecutorConfig {


    @Bean(name = "OutboxJobExecutor" , destroyMethod = "shutdown")
    public ThreadPoolExecutor outBoxJobExecutor(@Value("${spring.scheduler.workerCount:5}") int workerCount ,@Value("${spring.scheduler.maxWorkerCount:5}") int maxWorkerCount,  @Value("${spring.scheduler.queueCapacity:10}") int queueCapacity) {
        return new ThreadPoolExecutor(
                workerCount,
                maxWorkerCount,
                0,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
