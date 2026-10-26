package com.zzztie.careerai.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "careerai.import.enabled", havingValue = "true")
public class RagImportQueueConfig {

    public static final String QUEUE_NAME = "careerai.rag.import.v1";

    @Bean
    public Queue ragImportQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }
}
