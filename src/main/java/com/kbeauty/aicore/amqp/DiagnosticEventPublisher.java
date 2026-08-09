package com.kbeauty.aicore.amqp;

import com.kbeauty.aicore.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final DiagnosticCreatedEventSchemaValidator validator;

    public DiagnosticEventPublisher(RabbitTemplate rabbitTemplate, DiagnosticCreatedEventSchemaValidator validator) {
        this.rabbitTemplate = rabbitTemplate;
        this.validator = validator;
    }

    /**
     * Valide l'evenement contre contracts/rabbitmq/diagnostic-created.schema.json
     * avant publication : un changement de champ qui casserait le contrat
     * echoue ici (InvalidEventException), pas silencieusement chez le
     * consommateur Laravel.
     */
    public void publishDiagnosticCreated(Long diagnosticId, String status) {
        DiagnosticCreatedEvent event = DiagnosticCreatedEvent.of(diagnosticId, status);
        validator.validate(event);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                event
        );
    }
}
