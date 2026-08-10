package com.kbeauty.aicore.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifie le comportement reel des annotations @Retry/@CircuitBreaker (pas
 * seulement leur config) : necessite donc un vrai contexte Spring pour que
 * le proxy AOP existe, contrairement au reste des tests de ce repo. Le
 * transport HTTP est simule via MockRestServiceServer, jamais d'appel
 * reseau reel.
 */
class AnthropicApiClientResilienceTest {

    // Fenetre large par defaut : les 2-3 echecs consommes par les seules
    // tentatives de retry d'un unique appel client.call() ne doivent pas a
    // eux seuls ouvrir le circuit dans les tests qui ne testent QUE le
    // retry - sinon la derniere tentative (censee reussir) se heurte a un
    // circuit deja ouvert par les tentatives precedentes. Le test dedie au
    // circuit breaker retrecit volontairement cette fenetre.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    CircuitBreakerAutoConfiguration.class,
                    RetryAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "resilience4j.retry.retry-aspect-order=1",
                    "resilience4j.circuitbreaker.circuit-breaker-aspect-order=2",
                    "resilience4j.retry.instances.anthropicVision.max-attempts=3",
                    "resilience4j.retry.instances.anthropicVision.wait-duration=10ms",
                    "resilience4j.retry.instances.anthropicVision.ignore-exceptions=org.springframework.web.client.HttpClientErrorException,io.github.resilience4j.circuitbreaker.CallNotPermittedException",
                    "resilience4j.circuitbreaker.instances.anthropicVision.sliding-window-size=10",
                    "resilience4j.circuitbreaker.instances.anthropicVision.minimum-number-of-calls=10",
                    "resilience4j.circuitbreaker.instances.anthropicVision.failure-rate-threshold=50",
                    "resilience4j.circuitbreaker.instances.anthropicVision.wait-duration-in-open-state=1m",
                    "resilience4j.circuitbreaker.instances.anthropicVision.ignore-exceptions=org.springframework.web.client.HttpClientErrorException"
            );

    @Test
    void retente_sur_une_erreur_5xx_puis_reussit() {
        contextRunner.run(context -> {
            AnthropicApiClient client = context.getBean(AnthropicApiClient.class);
            MockRestServiceServer server = context.getBean(MockRestServiceServer.class);

            server.expect(anything()).andRespond(withServerError());
            server.expect(anything()).andRespond(withServerError());
            server.expect(anything()).andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

            JsonNode response = client.call(Map.of("model", "test"));

            assertThat(response).isNotNull();
            server.verify();
        });
    }

    @Test
    void ne_retente_pas_sur_une_erreur_4xx() {
        contextRunner.run(context -> {
            AnthropicApiClient client = context.getBean(AnthropicApiClient.class);
            MockRestServiceServer server = context.getBean(MockRestServiceServer.class);

            // Une seule reponse programmee : si le retry se declenchait a
            // tort sur un 4xx, le 2e appel echouerait faute de reponse
            // programmee et server.verify() le detecterait.
            server.expect(anything()).andRespond(withBadRequest());

            assertThatThrownBy(() -> client.call(Map.of("model", "test")))
                    .isInstanceOf(HttpClientErrorException.class);

            server.verify();
        });
    }

    @Test
    void ouvre_le_circuit_apres_plusieurs_appels_en_echec_et_refuse_lappel_suivant() {
        // minimum-number-of-calls=4 (> max-attempts=3) : les 3 tentatives
        // de retry d'un seul client.call() ne suffisent pas a elles seules
        // a ouvrir le circuit, il faut qu'un 2e appel s'y ajoute - sinon le
        // premier appel se ferait deja refuser sur sa derniere tentative,
        // ce qui melangerait "retry epuise" et "circuit ouvert" dans le
        // meme test.
        ApplicationContextRunner narrowWindowRunner = contextRunner.withPropertyValues(
                "resilience4j.circuitbreaker.instances.anthropicVision.sliding-window-size=4",
                "resilience4j.circuitbreaker.instances.anthropicVision.minimum-number-of-calls=4"
        );
        narrowWindowRunner.run(context -> {
            AnthropicApiClient client = context.getBean(AnthropicApiClient.class);
            MockRestServiceServer server = context.getBean(MockRestServiceServer.class);

            // Appel 1 : 3 x 500 (toutes les tentatives de retry echouent) ->
            // 3 echecs cumules, sous le seuil minimum-number-of-calls=4.
            // Appel 2 : 1 x 500 sur sa 1ere tentative -> 4e echec cumule,
            // le seuil est atteint et le circuit bascule OPEN immediatement
            // apres ; sa 2e tentative de retry se fait refuser sans
            // toucher le reseau (une seule reponse 500 programmee suffit).
            for (int i = 0; i < 4; i++) {
                server.expect(anything()).andRespond(withServerError());
            }

            assertThatThrownBy(() -> client.call(Map.of("model", "test")))
                    .isInstanceOf(HttpServerErrorException.class);

            assertThatThrownBy(() -> client.call(Map.of("model", "test")))
                    .isInstanceOf(CallNotPermittedException.class);

            server.verify();
        });
    }

    @Configuration
    static class TestConfig {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        MockRestServiceServer mockRestServiceServer(RestClient.Builder builder) {
            return MockRestServiceServer.bindTo(builder).build();
        }

        @Bean
        AnthropicApiClient anthropicApiClient(RestClient.Builder builder, MockRestServiceServer mockServer) {
            // mockServer en parametre (non utilise directement) uniquement
            // pour forcer Spring a l'instancier - et donc a lier le mock au
            // builder - avant que ce bean ne construise le RestClient.
            return new AnthropicApiClient(builder, "test-key");
        }
    }
}
