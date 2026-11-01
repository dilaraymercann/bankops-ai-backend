package com.bankops.infrastructure.persistence;

import com.bankops.domain.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {
}
