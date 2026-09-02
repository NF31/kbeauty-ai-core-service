package com.kbeauty.aicore.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Teste directement doFilter() (herite de OncePerRequestFilter) avec les
 * MockHttpServletRequest/Response de spring-test - aucun contexte Spring
 * necessaire, plus rapide que de passer par @WebMvcTest pour verifier ce
 * filtre isolement.
 */
class InternalApiKeyFilterTest {

    private static final String HEADER = "X-Internal-Api-Key";

    private final InternalApiKeyFilter filter = new InternalApiKeyFilter("secret-partage");

    @Test
    void laisse_passer_une_requete_diagnostics_avec_la_bonne_cle() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/diagnostics/analyze");
        request.addHeader(HEADER, "secret-partage");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void refuse_une_requete_diagnostics_sans_cle() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/diagnostics");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void refuse_une_requete_diagnostics_avec_une_mauvaise_cle() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/diagnostics");
        request.addHeader(HEADER, "mauvaise-cle");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void laisse_passer_les_routes_hors_diagnostics_sans_cle() throws Exception {
        // /hello (HealthController) : simple ping de sante, aucune donnee -
        // pas protege par ce filtre.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void refuse_toute_requete_diagnostics_si_aucune_cle_nest_configuree() throws Exception {
        // Fail closed : une cle vide (ex. oubli de deploiement) ne doit
        // jamais se comporter comme "aucune verification", meme si le
        // header attendu est fourni.
        InternalApiKeyFilter unconfigured = new InternalApiKeyFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/diagnostics");
        request.addHeader(HEADER, "peu-importe");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        unconfigured.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }
}
