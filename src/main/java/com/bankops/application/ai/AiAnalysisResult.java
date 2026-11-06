package com.bankops.application.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Modelden istenen yapılandırılmış yanıt (CLAUDE.md §10.5). Eksik gelen listeler boş listeye çevrilir. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnalysisResult(
        String summary,
        List<PossibleCause> possibleCauses,
        List<String> recommendedSteps,
        List<String> warnings,
        List<String> referencedCaseIds) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PossibleCause(String cause, List<String> supportingCaseIds) {
    }

    public AiAnalysisResult {
        possibleCauses = possibleCauses == null ? List.of() : possibleCauses;
        recommendedSteps = recommendedSteps == null ? List.of() : recommendedSteps;
        warnings = warnings == null ? List.of() : warnings;
        referencedCaseIds = referencedCaseIds == null ? List.of() : referencedCaseIds;
    }
}
