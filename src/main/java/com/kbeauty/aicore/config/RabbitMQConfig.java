package com.kbeauty.aicore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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

    /**
     * Seul bean MessageConverter du contexte : Spring Boot l'applique
     * automatiquement au RabbitTemplate (cote publisher) ET au
     * rabbitListenerContainerFactory par defaut (cote @RabbitListener) -
     * les messages passent de texte brut a JSON des deux cotes sans
     * configuration supplementaire.
     *
     * L'ObjectMapper est explicitement celui gere par Spring (meme bean
     * qu'injecte DiagnosticCreatedEventSchemaValidator), pas un
     * ObjectMapper interne par defaut : sans ca, Jackson2JsonMessageConverter
     * en construit un a lui tout seul, SANS WRITE_DATES_AS_TIMESTAMPS
     * desactive - occurredAt (Instant) partirait sur le fil en nombre
     * (timestamp epoch) au lieu de la string ISO-8601 que le contrat exige,
     * alors meme que la validation avant publication (qui utilise le bon
     * ObjectMapper) semblerait passer. Bug reproduit en conditions reelles
     * (curl + inspection du message cote consommateur Laravel) avant ce fix.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
