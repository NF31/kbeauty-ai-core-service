package com.kbeauty.aicore.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagnosticCreatedEventSchemaValidatorTest {

    // Reproduit la config du bean ObjectMapper auto-configure par Spring Boot
    // (WRITE_DATES_AS_TIMESTAMPS desactive par defaut) - sans ca, Instant se
    // serialise en nombre plutot qu'en string ISO-8601, que le schema refuse.
    private final DiagnosticCreatedEventSchemaValidator validator = new DiagnosticCreatedEventSchemaValidator(
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    );

    @Test
    void accepts_an_event_matching_the_contract() {
        DiagnosticCreatedEvent event = DiagnosticCreatedEvent.of(42L, "pending");

        assertDoesNotThrow(() -> validator.validate(event));
    }

    @Test
    void rejects_a_status_outside_the_contract_enum() {
        DiagnosticCreatedEvent event = new DiagnosticCreatedEvent("diagnostic.created", 42L, "archived", Instant.now());

        assertThrows(InvalidEventException.class, () -> validator.validate(event));
    }

    @Test
    void rejects_a_missing_diagnosticId() {
        DiagnosticCreatedEvent event = new DiagnosticCreatedEvent("diagnostic.created", null, "pending", Instant.now());

        assertThrows(InvalidEventException.class, () -> validator.validate(event));
    }

    @Test
    void rejects_an_unexpected_eventType() {
        DiagnosticCreatedEvent event = new DiagnosticCreatedEvent("diagnostic.updated", 42L, "pending", Instant.now());

        assertThrows(InvalidEventException.class, () -> validator.validate(event));
    }
}
