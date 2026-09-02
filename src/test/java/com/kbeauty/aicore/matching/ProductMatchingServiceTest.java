package com.kbeauty.aicore.matching;

import com.kbeauty.aicore.service.SkinAnalysisResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductMatchingServiceTest {

    private final VoyageEmbeddingClient voyageClient = mock(VoyageEmbeddingClient.class);
    private final ProductMatchRepository repository = mock(ProductMatchRepository.class);
    private final ProductMatchingService service = new ProductMatchingService(voyageClient, repository);

    @Test
    void buildQueryText_trieLesScoresDuPlusBasAuPlusHaut() {
        // Ordre volontairement "melange" en entree (LinkedHashMap pour
        // preserver l'ordre d'insertion) : le texte genere doit trier par
        // valeur, pas reprendre l'ordre du Map.
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("eclat", 80);
        scores.put("rougeurs", 30);
        scores.put("hydratation", 55);
        SkinAnalysisResult analysis = new SkinAnalysisResult(scores, "Peau plutot equilibree.");

        String queryText = service.buildQueryText(analysis);

        assertThat(queryText)
                .contains("Peau plutot equilibree.")
                .contains("rougeurs : 30/100, hydratation : 55/100, eclat : 80/100");
    }

    // containsExactly(12L, 7L) plus bas : AssertJ infere un Function generique
    // @NonNull sur ces Long boxes - meme famille de faux positif JDT que les
    // autres suppressions de ce repo.
    @SuppressWarnings("null")
    @Test
    void recommendProducts_retourneLesIdsTriesParSimilarite() {
        SkinAnalysisResult analysis = new SkinAnalysisResult(Map.of("rougeurs", 30), "Peau sensible.");
        float[] embedding = {0.1f, 0.2f};
        when(voyageClient.embedQuery(Mockito.anyString())).thenReturn(embedding);
        when(repository.findClosestPublishedProducts(any(), anyInt())).thenReturn(List.of(
                new ProductMatch(12L, 0.1),
                new ProductMatch(7L, 0.2)
        ));

        List<Long> ids = service.recommendProducts(analysis);

        assertThat(ids).containsExactly(12L, 7L);
        verify(repository).findClosestPublishedProducts(embedding, 4);
    }

    @Test
    void recommendProducts_retourneUneListeVideSiVoyageEchoue() {
        SkinAnalysisResult analysis = new SkinAnalysisResult(Map.of("rougeurs", 30), "Peau sensible.");
        when(voyageClient.embedQuery(Mockito.anyString())).thenThrow(new RuntimeException("Voyage indisponible"));

        List<Long> ids = service.recommendProducts(analysis);

        // Best effort assume : une panne du matching ne doit jamais faire
        // remonter d'exception au controller (voir DiagnosticController,
        // le diagnostic Claude reste renvoye meme sans recommandation).
        assertThat(ids).isEmpty();
    }

    @Test
    void recommendProducts_retourneUneListeVideSiLaRechercheEchoue() {
        SkinAnalysisResult analysis = new SkinAnalysisResult(Map.of("rougeurs", 30), "Peau sensible.");
        when(voyageClient.embedQuery(Mockito.anyString())).thenReturn(new float[]{0.1f});
        when(repository.findClosestPublishedProducts(any(), anyInt()))
                .thenThrow(new RuntimeException("shop_ingredient_embeddings absente"));

        List<Long> ids = service.recommendProducts(analysis);

        assertThat(ids).isEmpty();
    }
}
