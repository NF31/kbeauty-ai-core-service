package com.kbeauty.aicore.repository;

import com.kbeauty.aicore.entity.DiagnosticRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosticRequestRepository extends JpaRepository<DiagnosticRequest, Long> {
}
