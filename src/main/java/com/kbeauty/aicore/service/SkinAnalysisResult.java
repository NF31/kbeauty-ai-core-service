package com.kbeauty.aicore.service;

import java.util.Map;

/**
 * validPhoto : indique si Claude a effectivement analyse une vraie photo de
 * visage (par opposition a une capture d'ecran, un dessin, une photo sans
 * visage visible, etc.). Quand false, scores est vide ({}) - inutile de
 * scorer une "peau" que Claude n'a pas vue - et analysis explique pourquoi
 * a l'utilisateur. Voir AnthropicVisionService#buildRequestBody (prompt) et
 * DiagnosticController#analyze (saute le matching produit si false).
 */
public record SkinAnalysisResult(boolean validPhoto, Map<String, Integer> scores, String analysis) {
}
