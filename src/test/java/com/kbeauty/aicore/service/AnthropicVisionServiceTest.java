package com.kbeauty.aicore.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicVisionServiceTest {

    // apiClient a null : parseAnalysis() ne le touche jamais, seul analyze()
    // en a besoin (couvert separement par AnthropicApiClientResilienceTest).
    // AnthropicApiClient n'est pas annote @Nullable, d'ou l'avertissement
    // JDT sur ce null litteral volontaire - @SuppressWarnings car c'est
    // exactement le comportement recherche ici, pas une erreur.
    @SuppressWarnings("null")
    private final AnthropicVisionService service = new AnthropicVisionService(null, "claude-sonnet-5");

    @Test
    void parseAnalysis_extraitLeJsonBrut() {
        String text = "{\"valid_photo\": true, \"scores\": {\"rougeurs\": 30, \"hydratation\": 70}, \"analysis\": \"Peau plutot equilibree.\"}";

        SkinAnalysisResult result = service.parseAnalysis(text);

        assertThat(result.validPhoto()).isTrue();
        assertThat(result.scores()).containsEntry("rougeurs", 30).containsEntry("hydratation", 70);
        assertThat(result.analysis()).isEqualTo("Peau plutot equilibree.");
    }

    @Test
    void parseAnalysis_defautValidPhotoAVraiSiLeChampEstAbsent() {
        // Reponse "a l'ancienne" (avant l'ajout de valid_photo au prompt) :
        // ne doit pas priver un diagnostic par ailleurs correct de ses
        // recommandations produit.
        String text = "{\"scores\": {\"rougeurs\": 30}, \"analysis\": \"Peau plutot equilibree.\"}";

        SkinAnalysisResult result = service.parseAnalysis(text);

        assertThat(result.validPhoto()).isTrue();
    }

    @Test
    void parseAnalysis_validPhotoFauxQuandLimageNestPasUnVisage() {
        String text = "{\"valid_photo\": false, \"scores\": {}, \"analysis\": \"Ce n'est pas une photo de visage.\"}";

        SkinAnalysisResult result = service.parseAnalysis(text);

        assertThat(result.validPhoto()).isFalse();
        assertThat(result.scores()).isEmpty();
        assertThat(result.analysis()).isEqualTo("Ce n'est pas une photo de visage.");
    }

    @Test
    void parseAnalysis_extraitLeJsonEntoureDeMarkdown() {
        String text = """
                Voici mon analyse :
                ```json
                {"scores": {"texture": 55, "eclat": 40}, "analysis": "Manque d'eclat, texture correcte."}
                ```
                """;

        SkinAnalysisResult result = service.parseAnalysis(text);

        assertThat(result.scores()).containsEntry("texture", 55).containsEntry("eclat", 40);
        assertThat(result.analysis()).isEqualTo("Manque d'eclat, texture correcte.");
    }

    @Test
    void parseAnalysis_leveUneExceptionSiAucunJson() {
        String text = "Desole, je ne peux pas analyser cette image.";

        org.junit.jupiter.api.Assertions.assertThrows(
                AnthropicVisionException.class,
                () -> service.parseAnalysis(text)
        );
    }
}
