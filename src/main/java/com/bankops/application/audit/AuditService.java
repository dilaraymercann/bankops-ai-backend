package com.bankops.application.audit;

import com.bankops.application.repository.AuditLogRepository;
import com.bankops.domain.AuditLog;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Audit log'a kayıt ekler (CLAUDE.md §4: her durum değişikliği ve kritik işlem). Tablo yalnızca eklemeye açıktır. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    /** {@code userId} boşsa işlemi sistem yapmıştır (örn. otomatik eskalasyon). */
    public void record(Long userId, String action, String entityType, Long entityId, String details) {
        AuditLog log = new AuditLog();
        log.setTimestamp(clock.instant());
        log.setUserId(userId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        auditLogRepository.save(log);
    }
}
