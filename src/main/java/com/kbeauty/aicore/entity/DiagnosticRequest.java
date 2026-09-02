package com.kbeauty.aicore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class DiagnosticRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String status;

    /**
     * Resultat de l'analyse vision (Phase 5) : texte + scores anonymises
     * uniquement. Aucun champ image sur cette entite - la photo n'est
     * jamais persistee (contrainte RGPD), traitee en memoire le temps de
     * l'appel a l'API vision puis jetee.
     */
    @Column(columnDefinition = "TEXT")
    private String analysis;

    @Column(columnDefinition = "TEXT")
    private String scoresJson;

    /**
     * Liste JSON d'ids `products.id` (kbeauty-shop) recommandes par
     * ProductMatchingService - vide/absent si le matching a echoue ou
     * n'a rien trouve (voir ProductMatchingService.recommendProducts,
     * "best effort" par design).
     */
    @Column(columnDefinition = "TEXT")
    private String recommendedProductIdsJson;

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public String getScoresJson() {
        return scoresJson;
    }

    public void setScoresJson(String scoresJson) {
        this.scoresJson = scoresJson;
    }

    public String getRecommendedProductIdsJson() {
        return recommendedProductIdsJson;
    }

    public void setRecommendedProductIdsJson(String recommendedProductIdsJson) {
        this.recommendedProductIdsJson = recommendedProductIdsJson;
    }
}
