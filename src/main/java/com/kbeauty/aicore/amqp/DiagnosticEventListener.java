package com.kbeauty.aicore.amqp;

import com.kbeauty.aicore.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticEventListener {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticEventListener.class);

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void handleDiagnosticCreated(DiagnosticCreatedEvent event) {
        logger.info("Evenement recu depuis RabbitMQ : diagnosticId={}, status={}, occurredAt={}",
                event.diagnosticId(), event.status(), event.occurredAt());
    }
}
