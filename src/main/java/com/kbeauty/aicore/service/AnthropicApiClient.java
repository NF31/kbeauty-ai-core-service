package com.kbeauty.aicore.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Isole l'appel HTTP brut a l'API Anthropic dans son propre bean. Les
 * annotations @CircuitBreaker/@Retry de Resilience4j reposent sur un proxy
 * Spring AOP : si elles etaient posees sur une methode privee appelee via
 * "this.methode(...)" depuis AnthropicVisionService (auto-invocation), le
 * proxy ne serait jamais sollicite et les annotations seraient
 * silencieusement ignorees. D'ou cette classe a part, injectee comme
 * dependance plutot qu'appelee en interne.
 */
@Component
public class AnthropicApiClient {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final String apiKey;
    private final String workspaceId;

    public AnthropicApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${anthropic.api.key}") String apiKey,
            @Value("${anthropic.workspace.id}") String workspaceId
    ) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.workspaceId = workspaceId;
    }

    /**
     * Retry (avec backoff exponentiel) puis, en cas d'echecs repetes,
     * circuit breaker : voir resilience4j.retry/circuitbreaker.instances.
     * anthropicVision.* dans application.properties. Les deux ignorent
     * volontairement HttpClientErrorException (4xx) - une cle API invalide
     * ou un compte sans credit ne se resout pas en reessayant, retenter
     * ne ferait que retarder un echec certain et fausser la mesure de
     * sante du circuit breaker.
     */
    // .body(requestBody) plus bas : RestClient.RequestBodySpec#body(Object)
    // (spring-web, parametre @NonNull) recoit une Map non annotee - meme
    // famille de faux positif JDT que les autres suppressions de ce repo.
    @SuppressWarnings("null")
    @Retry(name = "anthropicVision")
    @CircuitBreaker(name = "anthropicVision")
    public JsonNode call(Map<String, Object> requestBody) {
        return restClient.post()
                .uri(ANTHROPIC_API_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("anthropic-workspace-id", workspaceId)
                .header("content-type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);
    }
}
