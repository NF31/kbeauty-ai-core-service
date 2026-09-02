package com.kbeauty.aicore.matching;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Interroge `shop_ingredient_embeddings` (table Laravel, meme base Neon que
 * kbeauty-shop - voir docs/montee-competence/02-bases-donnees.md) par
 * similarite cosinus pgvector. JdbcTemplate plutot que Spring Data JPA :
 * Hibernate n'a pas de mapping natif pour le type `vector(N)` (pgvector),
 * et cette requete est un simple SELECT en lecture seule - une entite JPA
 * complete serait plus de code pour rien.
 */
@Repository
public class ProductMatchRepository {

    private static final String FIND_CLOSEST_SQL = """
            SELECT sie.product_id, sie.embedding <=> ?::vector AS distance
            FROM shop_ingredient_embeddings sie
            JOIN products p ON p.id = sie.product_id
            WHERE p.status = 'published'
            ORDER BY sie.embedding <=> ?::vector
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProductMatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param queryEmbedding vecteur de la requete (Voyage AI, input_type=query)
     * @param limit          nombre maximum de produits a retourner
     */
    public List<ProductMatch> findClosestPublishedProducts(float[] queryEmbedding, int limit) {
        String vectorLiteral = toVectorLiteral(queryEmbedding);

        // ?::vector deux fois (SELECT + ORDER BY) : PostgreSQL ne permet
        // pas de reutiliser un alias de SELECT dans ORDER BY ici sans
        // recalcul, donc le meme parametre est repete plutot que
        // recalculer la distance depuis l'alias.
        return jdbcTemplate.query(
                FIND_CLOSEST_SQL,
                (rs, rowNum) -> new ProductMatch(rs.getLong("product_id"), rs.getDouble("distance")),
                vectorLiteral, vectorLiteral, limit
        );
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder(embedding.length * 8);
        builder.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        builder.append(']');
        return builder.toString();
    }
}
