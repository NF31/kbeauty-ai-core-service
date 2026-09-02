package com.kbeauty.aicore.matching;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Client HTTP minimal pour l'API Voyage AI (embeddings) - meme fournisseur
 * que kbeauty-ingredients-scraper/scripts/embed_ingredients.py (Python) et
 * kbeauty-shop/app/Services/VoyageEmbeddingClient.php (PHP, indexation),
 * mais ici uniquement pour vectoriser la requete de recherche.
 */
@Component
public class VoyageEmbeddingClient {

    private static final String API_URL = "https://api.voyageai.com/v1/embeddings";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public VoyageEmbeddingClient(
            RestClient.Builder restClientBuilder,
            @Value("${voyage.api.key}") String apiKey,
            @Value("${voyage.model:voyage-3.5}") String model
    ) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * input_type=query, jamais "document" ici : c'est le mode reserve a
     * l'indexation (cote PHP/Python). Voyage traite les deux differemment
     * - melanger les deux fausse la similarite (voir
     * kbeauty-shop/docs/montee-competence/11-embeddings-rag-pgvector.md).
     */
    public float[] embedQuery(String text) {
        Map<String, Object> body = Map.of(
                "input", List.of(text),
                "model", model,
                "input_type", "query"
        );

        JsonNode response = restClient.post()
                .uri(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("content-type", "application/json")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        JsonNode embeddingNode = response.path("data").path(0).path("embedding");
        float[] embedding = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            embedding[i] = (float) embeddingNode.get(i).asDouble();
        }
        return embedding;
    }
}
