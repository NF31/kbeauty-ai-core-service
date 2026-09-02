package com.kbeauty.aicore.matching;

/**
 * @param productId identifiant `products.id` cote kbeauty-shop (Laravel) -
 *                   cette base ne connait aucune autre info produit, tout
 *                   le detail (nom, prix, image...) est resolu par
 *                   kbeauty-shop lui-meme a partir de cet id.
 * @param distance   distance cosinus pgvector (0 = identique, 2 = oppose) -
 *                   exposee pour le logging/debug, pas pour l'utilisateur
 *                   final.
 */
public record ProductMatch(Long productId, double distance) {
}
