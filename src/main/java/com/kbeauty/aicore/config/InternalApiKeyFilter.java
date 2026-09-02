package com.kbeauty.aicore.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * kbeauty-ai-core-service n'est jamais appele directement par un navigateur -
 * uniquement en server-to-server par kbeauty-shop
 * (App\Services\AiCoreDiagnosticClient). Ce filtre exige un secret partage
 * (header X-Internal-Api-Key, meme valeur cote Laravel dans
 * config/services.php -> AI_CORE_INTERNAL_KEY) sur toutes les routes
 * /diagnostics/** - sans ca, GET /diagnostics expose les diagnostics peau
 * (donnees sensibles RGPD, voir DiagnosticRequest) de tous les utilisateurs
 * a n'importe qui, et POST /diagnostics/analyze laisse n'importe qui
 * declencher des appels payants a Anthropic/Voyage a nos frais. /hello
 * reste ouvert (simple ping de sante, aucune donnee).
 *
 * Aucune cle configuree (chaine vide, ex. oubli de deploiement) refuse tout
 * plutot que d'autoriser tout - fail closed. Comparaison en temps constant
 * (MessageDigest.isEqual) plutot que String#equals, par bonne pratique sur
 * un secret meme si ce n'est pas la cible la plus probable ici.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Api-Key";

    private final String expectedKey;

    public InternalApiKeyFilter(@Value("${internal.api.key:}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    // Parametres annotes @NonNull pour correspondre exactement a la
    // signature surchargee (OncePerRequestFilter, spring-web) - la vraie
    // correction plutot qu'une suppression, contrairement aux faux
    // positifs traites par ailleurs dans ce repo.
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/diagnostics")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (expectedKey.isBlank() || !constantTimeEquals(expectedKey, request.getHeader(HEADER))) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String expected, String provided) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = (provided != null ? provided : "").getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }
}
