package com.kbeauty.aicore.repository;

import com.kbeauty.aicore.entity.DiagnosticRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest ne charge que la couche JPA (pas RabbitMQ, pas les
 * controllers) et remplace automatiquement le datasource Postgres par une
 * base H2 en memoire (voir la dependance h2 dans pom.xml) - jamais de
 * connexion reelle a Neon depuis ces tests.
 */
@DataJpaTest
class DiagnosticRequestRepositoryTest {

    @Autowired
    private DiagnosticRequestRepository repository;

    @Test
    void save_genereUnIdEtPersisteTousLesChamps() {
        DiagnosticRequest diagnostic = new DiagnosticRequest();
        diagnostic.setStatus("completed");
        diagnostic.setAnalysis("Peau plutot equilibree.");
        diagnostic.setScoresJson("{\"rougeurs\":30,\"hydratation\":70}");

        DiagnosticRequest saved = repository.save(diagnostic);

        assertThat(saved.getId()).isNotNull();

        Optional<DiagnosticRequest> reloaded = repository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo("completed");
        assertThat(reloaded.get().getAnalysis()).isEqualTo("Peau plutot equilibree.");
        assertThat(reloaded.get().getScoresJson()).isEqualTo("{\"rougeurs\":30,\"hydratation\":70}");
    }

    @Test
    void findAll_retourneTousLesDiagnostiquesPersistes() {
        DiagnosticRequest pending = new DiagnosticRequest();
        pending.setStatus("pending");
        DiagnosticRequest completed = new DiagnosticRequest();
        completed.setStatus("completed");

        repository.save(pending);
        repository.save(completed);

        List<DiagnosticRequest> all = repository.findAll();

        assertThat(all).hasSize(2)
                .extracting(DiagnosticRequest::getStatus)
                .containsExactlyInAnyOrder("pending", "completed");
    }

    @Test
    void findById_retourneVideQuandLidNexistePas() {
        // GenerationType.IDENTITY demarre a 1 : aucun risque de collision
        // fortuite avec un id genere par un autre test de cette classe.
        Optional<DiagnosticRequest> result = repository.findById(999_999L);

        assertThat(result).isEmpty();
    }
}
