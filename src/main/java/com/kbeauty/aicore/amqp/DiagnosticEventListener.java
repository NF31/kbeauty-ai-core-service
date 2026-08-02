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
    public void handleDiagnosticCreated(String message) {
        logger.info("Message recu depuis RabbitMQ: {}", message);
    }
}
