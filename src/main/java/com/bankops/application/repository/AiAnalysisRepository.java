package com.bankops.application.repository;

import com.bankops.domain.AiAnalysis;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    Optional<AiAnalysis> findFirstByCaseIdOrderByCreatedAtDesc(Long caseId);

    /** Seed'in ürettiği sahte analizler hariç, gerçek modelin en son analizi (önbellek için). */
    Optional<AiAnalysis> findFirstByCaseIdAndModelNameNotOrderByCreatedAtDesc(Long caseId, String modelName);
}
