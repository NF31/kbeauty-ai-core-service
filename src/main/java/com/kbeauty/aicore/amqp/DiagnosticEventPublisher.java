package com.kbeauty.aicore.amqp;

import com.kbeauty.aicore.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public DiagnosticEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishDiagnosticCreated(Long diagnosticId) {
        String message = "Diagnostic created: id=" + diagnosticId;
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                message
        );
    }
}
