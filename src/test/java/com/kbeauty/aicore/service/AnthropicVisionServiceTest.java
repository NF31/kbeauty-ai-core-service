package com.kbeauty.aicore.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicVisionServiceTest {

    private final AnthropicVisionService service = new AnthropicVisionService("test-key", "claude-sonnet-5");

    @Test
    void parseAnalysis_extraitLeJsonBrut() {
        String text = "{\"scores\": {\"rougeurs\": 30, \"hydratation\": 70}, \"analysis\": \"Peau plutot equilibree.\"}";

        SkinAnalysisResult result = service.parseAnalysis(text);

        assertThat(result.scores()).containsEntry("rougeurs", 30).containsEntry("hydratation", 70);
        assertThat(result.analysis()).isEqualTo("Peau plutot equilibree.");
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
