package edu.ucsal.fiadopay.config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
public class ThreadPoolConfig {
    @Bean(name="paymentExecutor")
    public Executor paymentExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();

        exec.setCorePoolSize(3);
        exec.setMaxPoolSize(8);
        exec.setQueueCapacity(50);
        exec.setKeepAliveSeconds(60);

        exec.setThreadNamePrefix("payment-");

        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationMillis(30);
        exec.initialize();

        log.info("✅ Payment executor initialized: core=3, max=8, queue=50");
        return exec;
    }

    @Bean(name="webhookExecutor")
    public Executor webhookExecutor(){
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();

        exec.setCorePoolSize(5);
        exec.setMaxPoolSize(10);
        exec.setQueueCapacity(100);
        exec.setKeepAliveSeconds(60);

        exec.setThreadNamePrefix("webhook-");

        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(60);

        exec.initialize();

        log.info("✅ Webhook executor initialized: core=5, max=10, queue=100");
        return exec;
    }
}
