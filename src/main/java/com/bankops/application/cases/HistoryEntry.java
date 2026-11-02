package com.bankops.application.cases;

import com.bankops.domain.CaseStatus;
import java.time.Instant;

/**
 * İşlem geçmişi satırı. {@code type}: STATUS_CHANGE veya ESCALATION (SLA aşımı). Değişikliği sistem yaptıysa
 * {@code changedByName} boştur; ESCALATION satırında {@code escalatedToName} dolu olur.
 */
public record HistoryEntry(
        String type,
        CaseStatus fromStatus,
        CaseStatus toStatus,
        String reason,
        String changedByName,
        String escalatedToName,
        Instant at) {
}
