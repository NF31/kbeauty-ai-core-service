package com.kbeauty.aicore.controller;

import com.kbeauty.aicore.amqp.DiagnosticEventPublisher;
import com.kbeauty.aicore.entity.DiagnosticRequest;
import com.kbeauty.aicore.repository.DiagnosticRequestRepository;
import com.kbeauty.aicore.service.AnthropicVisionException;
import com.kbeauty.aicore.service.AnthropicVisionService;
import com.kbeauty.aicore.service.SkinAnalysisResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest ne charge que la couche web (pas JPA, pas RabbitMQ reel) : le
 * repository, le publisher d'evenements et le service vision sont mockes,
 * seul le routage/serialisation du controller est verifie ici.
 */
@WebMvcTest(DiagnosticController.class)
class DiagnosticControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagnosticRequestRepository repository;

    @MockitoBean
    private DiagnosticEventPublisher publisher;

    @MockitoBean
    private AnthropicVisionService visionService;

    @Test
    void create_enregistreUnDiagnosticEnAttenteEtPublieLevenement() throws Exception {
        when(repository.save(any(DiagnosticRequest.class))).thenAnswer(invocation -> {
            DiagnosticRequest arg = invocation.getArgument(0);
            // DiagnosticRequest n'expose pas de setId() (id genere par la
            // base, GenerationType.IDENTITY) : ReflectionTestUtils simule
            // ici l'affectation que ferait Hibernate apres un vrai INSERT.
            ReflectionTestUtils.setField(arg, "id", 1L);
            return arg;
        });

        mockMvc.perform(post("/diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("pending"));

        // L'evenement doit partir avec l'id/status reellement persistes
        // (retournes par repository.save()), pas ceux de l'objet avant
        // sauvegarde - sinon un diagnosticId=null partirait sur RabbitMQ.
        verify(publisher).publishDiagnosticCreated(eq(1L), eq("pending"));
    }

    @Test
    void list_retourneTousLesDiagnosticsDuRepository() throws Exception {
        DiagnosticRequest first = new DiagnosticRequest();
        first.setStatus("pending");
        DiagnosticRequest second = new DiagnosticRequest();
        second.setStatus("completed");
        when(repository.findAll()).thenReturn(List.of(first, second));

        mockMvc.perform(get("/diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("pending"))
                .andExpect(jsonPath("$[1].status").value("completed"));
    }

    @Test
    void analyze_retourneLanalyseEtLesScoresQuandLappelAnthropicReussit() throws Exception {
        SkinAnalysisResult result = new SkinAnalysisResult(Map.of("rougeurs", 30, "hydratation", 70), "Peau plutot equilibree.");
        when(visionService.analyze(any(byte[].class), eq("image/png"), eq("mixte"))).thenReturn(result);
        when(repository.save(any(DiagnosticRequest.class))).thenAnswer(invocation -> {
            DiagnosticRequest arg = invocation.getArgument(0);
            ReflectionTestUtils.setField(arg, "id", 42L);
            return arg;
        });

        MockMultipartFile image = new MockMultipartFile("image", "peau.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/diagnostics/analyze")
                        .file(image)
                        .param("skin_type", "mixte"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.analysis").value("Peau plutot equilibree."))
                .andExpect(jsonPath("$.scores.rougeurs").value(30));

        verify(publisher).publishDiagnosticCreated(eq(42L), eq("completed"));
    }

    @Test
    void analyze_retourne502SansPersisterNiPublierQuandAnthropicEchoue() throws Exception {
        when(visionService.analyze(any(byte[].class), eq("image/png"), eq("mixte")))
                .thenThrow(new AnthropicVisionException("Appel a l'API Anthropic echoue apres tentatives de retry", null));

        MockMultipartFile image = new MockMultipartFile("image", "peau.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/diagnostics/analyze")
                        .file(image)
                        .param("skin_type", "mixte"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Analyse IA indisponible"));

        // Un echec cote Anthropic ne doit ni creer de ligne DiagnosticRequest
        // ni publier sur RabbitMQ - le controller doit s'arreter avant.
        verifyNoInteractions(repository);
        verifyNoInteractions(publisher);
    }
}
