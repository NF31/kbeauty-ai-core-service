package com.kbeauty.aicore.amqp;

import java.time.Instant;

/**
 * Forme exacte definie par contracts/rabbitmq/diagnostic-created.schema.json.
 * Toute modification de champ (nom, type, valeurs autorisees) doit etre
 * repercutee dans ce schema des deux cotes (voir contracts/README.md) -
 * DiagnosticCreatedEventSchemaValidator refuse sinon la publication.
 */
public record DiagnosticCreatedEvent(
        String eventType,
        Long diagnosticId,
        String status,
        Instant occurredAt
) {
    public static DiagnosticCreatedEvent of(Long diagnosticId, String status) {
        return new DiagnosticCreatedEvent("diagnostic.created", diagnosticId, status, Instant.now());
    }
}
