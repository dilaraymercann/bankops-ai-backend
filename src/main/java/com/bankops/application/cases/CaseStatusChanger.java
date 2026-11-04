package com.bankops.application.cases;

import com.bankops.application.audit.AuditDetails;
import com.bankops.application.audit.AuditService;
import com.bankops.application.repository.StatusHistoryRepository;
import com.bankops.domain.CaseStateMachine;
import com.bankops.domain.CaseStatus;
import com.bankops.domain.ExceptionCase;
import com.bankops.domain.StatusHistory;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bir kaydın durumunu değiştirmenin tek yolu (CLAUDE.md §7): geçişi durum makinesiyle doğrular,
 * {@code StatusHistory} ve {@code AuditLog}'a yazar. Çağıran, yetki kontrollerini önceden yapmış olmalıdır.
 */
@Component
@RequiredArgsConstructor
public class CaseStatusChanger {

    private final CaseStateMachine stateMachine;
    private final StatusHistoryRepository historyRepository;
    private final AuditService auditService;
    private final Clock clock;

    public void change(ExceptionCase exceptionCase, CaseStatus to, Long userId, String reason) {
        CaseStatus from = exceptionCase.getStatus();
        stateMachine.validate(from, to);

        Instant now = clock.instant();
        exceptionCase.setStatus(to);
        exceptionCase.setUpdatedAt(now);

        StatusHistory history = new StatusHistory();
        history.setCaseId(exceptionCase.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setChangedByUserId(userId);
        history.setReason(reason);
        history.setChangedAt(now);
        historyRepository.save(history);

        auditService.record(userId, "STATUS_CHANGE", "ExceptionCase", exceptionCase.getId(),
                AuditDetails.of("from", from, "to", to, "reason", reason));
    }
}
