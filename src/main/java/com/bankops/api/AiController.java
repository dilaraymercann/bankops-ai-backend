package com.bankops.api;

import com.bankops.application.ai.AiAnalysisView;
import com.bankops.application.ai.FeedbackRequest;
import com.bankops.application.ai.LlmAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AI analizi uç noktaları (CLAUDE.md §11). Controller ince tutulur; akış {@link LlmAnalysisService}'tedir. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class AiController {

    private final LlmAnalysisService analysisService;

    /** Kayıt için geçerli bir analiz varsa döner (sayfa açılışında panel dolsun diye); yoksa 204. */
    @GetMapping("/cases/{id}/ai-analysis")
    ResponseEntity<AiAnalysisView> latest(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return analysisService.latest(id, CurrentUsers.from(jwt))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/cases/{id}/ai-analysis")
    AiAnalysisView analyze(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean refresh,
                           @AuthenticationPrincipal Jwt jwt) {
        return analysisService.analyze(id, refresh, CurrentUsers.from(jwt));
    }

    @PostMapping("/ai-analyses/{id}/feedback")
    AiAnalysisView feedback(@PathVariable Long id, @Valid @RequestBody FeedbackRequest request,
                            @AuthenticationPrincipal Jwt jwt) {
        return analysisService.giveFeedback(id, request.feedback(), CurrentUsers.from(jwt));
    }
}
