package com.bankops.api;

import com.bankops.application.sla.SlaEscalationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SLA'sı aşılan kayıtları periyodik olarak eskale eder (CLAUDE.md §9). İlk çalışma da bir aralık sonra olur;
 * böylece uygulama açılışında ve testlerde beklenmedik bir tetiklenme olmaz.
 */
@Component
@RequiredArgsConstructor
class SlaEscalationJob {

    private final SlaEscalationService escalationService;

    @Scheduled(fixedDelayString = "${bankops.sla.check-interval}", initialDelayString = "${bankops.sla.check-interval}")
    void run() {
        escalationService.escalateBreachedCases();
    }
}
