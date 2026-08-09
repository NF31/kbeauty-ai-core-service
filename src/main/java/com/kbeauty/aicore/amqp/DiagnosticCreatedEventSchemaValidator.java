package com.kbeauty.aicore.amqp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;

/**
 * Charge contracts/rabbitmq/diagnostic-created.schema.json depuis le
 * classpath (copie par le build Maven depuis contracts/, voir pom.xml
 * <resources>) et valide chaque DiagnosticCreatedEvent avant publication.
 */
@Component
public class DiagnosticCreatedEventSchemaValidator {

    private static final String SCHEMA_CLASSPATH = "contracts/rabbitmq/diagnostic-created.schema.json";

    private final JsonSchema schema;
    private final ObjectMapper objectMapper;

    public DiagnosticCreatedEventSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(SCHEMA_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("Schema introuvable sur le classpath : " + SCHEMA_CLASSPATH);
            }
            this.schema = factory.getSchema(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @throws InvalidEventException si l'evenement ne respecte pas le schema
     */
    public void validate(DiagnosticCreatedEvent event) {
        JsonNode node = objectMapper.valueToTree(event);
        Set<ValidationMessage> errors = schema.validate(node);
        if (!errors.isEmpty()) {
            throw new InvalidEventException(errors);
        }
    }
}
