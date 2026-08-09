package com.kbeauty.aicore.controller;

import com.kbeauty.aicore.amqp.DiagnosticEventPublisher;
import com.kbeauty.aicore.entity.DiagnosticRequest;
import com.kbeauty.aicore.repository.DiagnosticRequestRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DiagnosticController {

    private final DiagnosticRequestRepository repository;
    private final DiagnosticEventPublisher publisher;

    public DiagnosticController(DiagnosticRequestRepository repository, DiagnosticEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
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
}
