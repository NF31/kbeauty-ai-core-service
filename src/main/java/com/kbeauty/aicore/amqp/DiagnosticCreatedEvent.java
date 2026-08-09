package com.kbeauty.aicore.amqp;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
        // truncatedTo(MILLIS) : Instant.now() brut serialise en JSON avec des
        // nanosecondes (9 chiffres, ex. .358053291Z). justinrainbow/json-schema
        // (validateur cote Laravel) utilise DateTime::createFromFormat avec un
        // format microsecondes strict a 6 chiffres - au-dela, le parsing PHP
        // echoue silencieusement et rejette un message pourtant conforme a
        // RFC 3339. Bug reproduit en conditions reelles (curl + inspection du
        // message recu par le consommateur Laravel) avant ce fix. La
        // milliseconde suffit largement pour un horodatage d'evenement.
        return new DiagnosticCreatedEvent("diagnostic.created", diagnosticId, status, Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
