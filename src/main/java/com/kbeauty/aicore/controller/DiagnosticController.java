package com.kbeauty.aicore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbeauty.aicore.amqp.DiagnosticEventPublisher;
import com.kbeauty.aicore.entity.DiagnosticRequest;
import com.kbeauty.aicore.matching.ProductMatchingService;
import com.kbeauty.aicore.repository.DiagnosticRequestRepository;
import com.kbeauty.aicore.service.AnthropicVisionException;
import com.kbeauty.aicore.service.AnthropicVisionService;
import com.kbeauty.aicore.service.SkinAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class DiagnosticController {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticController.class);

    private final DiagnosticRequestRepository repository;
    private final DiagnosticEventPublisher publisher;
    private final AnthropicVisionService visionService;
    private final ProductMatchingService matchingService;
    private final ObjectMapper objectMapper;

    public DiagnosticController(
            DiagnosticRequestRepository repository,
            DiagnosticEventPublisher publisher,
            AnthropicVisionService visionService,
            ProductMatchingService matchingService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.visionService = visionService;
        this.matchingService = matchingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/diagnostics")
    public DiagnosticRequest create() {
        DiagnosticRequest diagnostic = new DiagnosticRequest();
        diagnostic.setStatus("pending");
        DiagnosticRequest saved = repository.save(diagnostic);
        publisher.publishDiagnosticCreated(saved.getId(), saved.getStatus());
        return saved;
    }

    @GetMapping("/diagnostics")
    public List<DiagnosticRequest> list() {
        return repository.findAll();
    }

    /**
     * Phase 5 : vraie analyse vision. L'image (multipart) est lue en
     * memoire (getBytes()) et jamais ecrite sur disque (jamais de
     * transferTo()) - seuls le texte d'analyse et les scores anonymises
     * sont persistes sur DiagnosticRequest.
     */
    @PostMapping("/diagnostics/analyze")
    public ResponseEntity<?> analyze(
            @RequestParam("image") MultipartFile image,
            @RequestParam("skin_type") String skinType
    ) {
        byte[] imageBytes;
        try {
            imageBytes = image.getBytes();
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image illisible"));
        }

        String mediaType = image.getContentType() != null ? image.getContentType() : "image/jpeg";

        SkinAnalysisResult result;
        try {
            result = visionService.analyze(imageBytes, mediaType, skinType);
        } catch (AnthropicVisionException e) {
            logger.warn("Analyse vision Anthropic echouee: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Analyse IA indisponible"));
        }

        List<Long> recommendedProductIds = matchingService.recommendProducts(result);

        DiagnosticRequest diagnostic = new DiagnosticRequest();
        diagnostic.setStatus("completed");
        diagnostic.setAnalysis(result.analysis());
        try {
            diagnostic.setScoresJson(objectMapper.writeValueAsString(result.scores()));
        } catch (Exception e) {
            diagnostic.setScoresJson("{}");
        }
        try {
            diagnostic.setRecommendedProductIdsJson(objectMapper.writeValueAsString(recommendedProductIds));
        } catch (Exception e) {
            diagnostic.setRecommendedProductIdsJson("[]");
        }

        DiagnosticRequest saved = repository.save(diagnostic);
        publisher.publishDiagnosticCreated(saved.getId(), saved.getStatus());

        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "status", saved.getStatus(),
                "analysis", result.analysis(),
                "scores", result.scores(),
                "recommendedProductIds", recommendedProductIds
        ));
    }
}
