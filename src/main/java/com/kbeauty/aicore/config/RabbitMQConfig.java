package com.kbeauty.aicore.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_NAME = "diagnostic.created";
    public static final String EXCHANGE_NAME = "kbeauty.exchange";
    public static final String ROUTING_KEY = "diagnostic.created";

    @Bean
    public Queue diagnosticQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding binding(Queue diagnosticQueue, DirectExchange exchange) {
        return BindingBuilder.bind(diagnosticQueue).to(exchange).with(ROUTING_KEY);
    }
}
