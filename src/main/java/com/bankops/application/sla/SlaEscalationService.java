package com.bankops.application.sla;

import com.bankops.application.audit.AuditDetails;
import com.bankops.application.audit.AuditService;
import com.bankops.application.repository.ExceptionCaseRepository;
import com.bankops.application.repository.UserRepository;
import com.bankops.domain.CaseStatus;
import com.bankops.domain.ExceptionCase;
import com.bankops.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SLA'sı aşılan açık kayıtları işaretler (CLAUDE.md §9). Eskalasyon kaydın durumunu değiştirmez; yalnızca
 * {@code IsEscalated} işaretlenir ve audit'e ESCALATE yazılır, böylece uzman çalışmaya devam edebilir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaEscalationService {

    private static final List<CaseStatus> OPEN_STATUSES =
            Arrays.stream(CaseStatus.values()).filter(CaseStatus::isOpen).toList();

    private final ExceptionCaseRepository caseRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    /** Yeni eskale edilen kayıt sayısını döner. İkinci çalıştırmada aynı kayıtlar tekrar işaretlenmez. */
    @Transactional
    public int escalateBreachedCases() {
        Instant now = clock.instant();
        List<ExceptionCase> overdue = caseRepository.findByEscalatedFalseAndSlaDeadlineBeforeAndStatusIn(now, OPEN_STATUSES);
        Long teamLeadId = userRepository.findFirstByRole(Role.TeamLead).map(user -> user.getId()).orElse(null);

        for (ExceptionCase exceptionCase : overdue) {
            exceptionCase.setEscalated(true);
            exceptionCase.setUpdatedAt(now);
            auditService.record(null, "ESCALATE", "ExceptionCase", exceptionCase.getId(),
                    AuditDetails.of("reason", "sla_breached", "escalatedTo", teamLeadId));
        }
        if (!overdue.isEmpty()) {
            log.info("{} kayıt SLA aşımı nedeniyle eskale edildi", overdue.size());
        }
        return overdue.size();
    }
}
