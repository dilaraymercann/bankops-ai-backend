package com.bankops.application.cases;

import com.bankops.domain.CaseStatus;
import com.bankops.domain.ErrorCode;
import com.bankops.domain.Priority;
import com.bankops.domain.TransactionType;
import java.time.Instant;

/** Kuyruk tablosundaki bir satır. Tutar string olarak taşınır (CLAUDE.md §4). */
public record CaseListItem(
        Long id,
        String caseNumber,
        TransactionType transactionType,
        ErrorCode errorCode,
        String errorDescription,
        String amount,
        String currency,
        Priority priority,
        CaseStatus status,
        boolean escalated,
        Instant slaDeadline,
        SlaView sla,
        Instant createdAt,
        String assignedToName) {
}
