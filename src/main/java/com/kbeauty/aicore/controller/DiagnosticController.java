package com.kbeauty.aicore.controller;

import com.kbeauty.aicore.entity.DiagnosticRequest;
import com.kbeauty.aicore.repository.DiagnosticRequestRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DiagnosticController {

    private final DiagnosticRequestRepository repository;

    public DiagnosticController(DiagnosticRequestRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/diagnostics")
    public DiagnosticRequest create() {
        DiagnosticRequest diagnostic = new DiagnosticRequest();
        diagnostic.setStatus("pending");
        return repository.save(diagnostic);
    }

    @GetMapping("/diagnostics")
    public List<DiagnosticRequest> list() {
        return repository.findAll();
    }
}
