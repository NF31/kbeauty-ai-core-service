package com.kbeauty.aicore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Appelle l'API vision de Claude (Anthropic) pour analyser une photo de
 * peau. L'image n'est jamais persistee ici : recue en memoire
 * (byte[]) par l'appelant, encodee en base64 pour l'appel HTTP, puis
 * jetee des que la reponse est parsee - conforme a la contrainte RGPD
 * de la Phase 5 (voir docs/05-diagnostic-vision-claude.md, ce repo, et
 * docs/montee-competence/13-diagnostic-vision-laravel.md dans kbeauty-shop).
 */
@Service
public class AnthropicVisionService {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    // Cherche le premier bloc JSON dans la reponse, au cas ou Claude
    // l'entoure de balises markdown (```json ... ```) malgre le prompt.
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public AnthropicVisionService(
            @Value("${anthropic.api.key}") String apiKey,
            @Value("${anthropic.model:claude-sonnet-5}") String model
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.create();
        this.objectMapper = new ObjectMapper();
    }

    public SkinAnalysisResult analyze(byte[] imageBytes, String mediaType, String skinType) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        Map<String, Object> requestBody = buildRequestBody(base64Image, mediaType, skinType);

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(ANTHROPIC_API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("content-type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            throw new AnthropicVisionException("Appel a l'API Anthropic echoue", e);
        }

        String text = extractText(response);
        return parseAnalysis(text);
    }

    private Map<String, Object> buildRequestBody(String base64Image, String mediaType, String skinType) {
        String prompt = """
                Tu es un dermatologue virtuel. Analyse cette photo de peau
                (type de peau declare par l'utilisateur : %s) et reponds
                UNIQUEMENT avec un objet JSON strict, sans texte autour, au
                format exact suivant :
                {"scores": {"rougeurs": <0-100>, "hydratation": <0-100>, "texture": <0-100>, "eclat": <0-100>}, "analysis": "<2-3 phrases en francais, ton bienveillant, conseils concrets>"}
                Les scores sont anonymises et ne doivent jamais reference d'elements identifiants de la photo.
                """.formatted(skinType);

        Map<String, Object> imageSource = Map.of(
                "type", "base64",
                "media_type", mediaType,
                "data", base64Image
        );
        Map<String, Object> imageBlock = Map.of("type", "image", "source", imageSource);
        Map<String, Object> textBlock = Map.of("type", "text", "text", prompt);

        Map<String, Object> message = Map.of(
                "role", "user",
                "content", new Object[]{imageBlock, textBlock}
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("messages", new Object[]{message});
        return body;
    }

    /**
     * Le premier bloc de "content" n'est pas forcement le texte : les
     * modeles avec "extended thinking" actif (comme claude-sonnet-5, sans
     * meme le demander explicitement via le parametre "thinking") renvoient
     * un bloc {"type": "thinking", ...} avant le bloc {"type": "text",
     * ...}. Prendre content[0] en dur renvoyait alors une chaine vide,
     * jamais l'analyse elle-meme (bug reproduit en conditions reelles).
     */
    private String extractText(JsonNode response) {
        if (response == null || !response.has("content") || response.get("content").isEmpty()) {
            throw new AnthropicVisionException("Reponse Anthropic vide ou malformee", null);
        }

        for (JsonNode block : response.get("content")) {
            if ("text".equals(block.path("type").asText())) {
                return block.path("text").asText();
            }
        }

        throw new AnthropicVisionException("Aucun bloc de type texte dans la reponse Anthropic", null);
    }

    SkinAnalysisResult parseAnalysis(String text) {
        Matcher matcher = JSON_BLOCK.matcher(text);
        if (!matcher.find()) {
            throw new AnthropicVisionException("Aucun JSON trouve dans la reponse Claude", null);
        }

        try {
            JsonNode json = objectMapper.readTree(matcher.group());
            Map<String, Integer> scores = new LinkedHashMap<>();
            json.path("scores").fields().forEachRemaining(entry ->
                    scores.put(entry.getKey(), entry.getValue().asInt())
            );
            String analysis = json.path("analysis").asText();
            return new SkinAnalysisResult(scores, analysis);
        } catch (Exception e) {
            throw new AnthropicVisionException("JSON Claude invalide", e);
        }
    }
}
