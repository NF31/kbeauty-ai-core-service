package com.kbeauty.aicore.amqp;

import com.networknt.schema.ValidationMessage;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Leve quand un evenement ne respecte pas contracts/rabbitmq/*.schema.json -
 * volontairement une RuntimeException non catchee par defaut : une violation
 * du contrat est un bug de code, pas un cas metier a gerer normalement.
 */
public class InvalidEventException extends RuntimeException {

    // ValidationMessage::getMessage utilise comme Function<ValidationMessage, String>
    // ci-dessous : meme faux positif JDT que les autres suppressions de ce
    // repo (type d'une lib tierce non annote dans un contexte generique).
    @SuppressWarnings("null")
    public InvalidEventException(Set<ValidationMessage> errors) {
        super("Evenement invalide au regard du contrat JSON Schema : "
                + errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining("; ")));
    }
}
