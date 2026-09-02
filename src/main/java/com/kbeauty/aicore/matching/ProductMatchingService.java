package com.kbeauty.aicore.matching;

import com.kbeauty.aicore.service.SkinAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Le moteur de matching evoque depuis la Phase 4 (voir
 * kbeauty-shop/docs/montee-competence/11-embeddings-rag-pgvector.md et
 * ROADMAP.md) : transforme le resultat du diagnostic (analyse Claude +
 * scores) en requete vectorielle contre les produits kbeauty-shop deja
 * vectorises (`shop_ingredient_embeddings`, alimentee par
 * `php artisan products:embed-ingredients` cote Laravel).
 */
@Service
public class ProductMatchingService {

    private static final Logger logger = LoggerFactory.getLogger(ProductMatchingService.class);

    private static final int MAX_RESULTS = 4;

    private final VoyageEmbeddingClient voyageClient;
    private final ProductMatchRepository repository;

    public ProductMatchingService(VoyageEmbeddingClient voyageClient, ProductMatchRepository repository) {
        this.voyageClient = voyageClient;
        this.repository = repository;
    }

    /**
     * "Best effort" delibere : une panne Voyage AI, ou
     * `shop_ingredient_embeddings` pas encore peuplee (commande artisan
     * jamais lancee), ne doit jamais faire echouer le diagnostic
     * lui-meme - seulement renvoyer une liste vide. Cote kbeauty-shop,
     * DiagnosticController sait deja se replier sur son ancienne
     * selection par skin_type si cette liste est vide.
     */
    // ProductMatch::productId utilise comme Function<ProductMatch, Long> plus
    // bas : ProductMatch (record local, non annote) ne fournit pas de
    // garantie @NonNull explicite a JDT dans ce contexte generique - meme
    // famille de faux positif que les autres suppressions de ce repo, aucun
    // element de la liste retournee par findClosestPublishedProducts()
    // n'est jamais null.
    @SuppressWarnings("null")
    public List<Long> recommendProducts(SkinAnalysisResult analysis) {
        try {
            float[] queryEmbedding = voyageClient.embedQuery(buildQueryText(analysis));

            return repository.findClosestPublishedProducts(queryEmbedding, MAX_RESULTS).stream()
                    .map(ProductMatch::productId)
                    .toList();
        } catch (Exception e) {
            logger.warn("Matching produit indisponible, diagnostic renvoye sans recommandation vectorielle : {}", e.getMessage());

            return List.of();
        }
    }

    /**
     * Texte concatenant l'analyse en francais (deja descriptive, ton
     * dermatologue) et les scores tries du plus bas au plus haut - les
     * besoins les plus marques (score bas = probleme le plus present)
     * apparaissent en premier, meme si Voyage encode le texte entier sans
     * distinction de position.
     */
    String buildQueryText(SkinAnalysisResult analysis) {
        String scoresDescription = analysis.scores().entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey() + " : " + entry.getValue() + "/100")
                .collect(Collectors.joining(", "));

        return "Diagnostic peau. " + analysis.analysis() + " Scores : " + scoresDescription + ".";
    }
}
