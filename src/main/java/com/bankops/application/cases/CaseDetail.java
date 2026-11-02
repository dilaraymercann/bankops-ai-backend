package com.bankops.application.cases;

import com.bankops.application.auth.UserSummary;
import com.bankops.domain.CaseStatus;
import com.bankops.domain.ErrorCode;
import com.bankops.domain.Priority;
import java.time.Instant;
import java.util.List;

/** Kayıt detay ekranı için tüm bilgi. {@code sla} yalnızca açık kayıtlarda doludur. */
public record CaseDetail(
        Long id,
        String caseNumber,
        ErrorCode errorCode,
        String errorDescription,
        Priority priority,
        CaseStatus status,
        boolean escalated,
        Instant slaDeadline,
        SlaView sla,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        UserSummary assignedTo,
        TransactionView transaction,
        List<NoteView> notes,
        List<HistoryEntry> history) {
}
