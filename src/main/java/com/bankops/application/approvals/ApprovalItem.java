package com.bankops.application.approvals;

import com.bankops.domain.ErrorCode;
import com.bankops.domain.TransactionType;
import java.time.Instant;

/** Onay listesindeki bir satır (ekranın solundaki kartlar). */
public record ApprovalItem(
        Long caseId,
        String caseNumber,
        ErrorCode errorCode,
        String errorDescription,
        TransactionType transactionType,
        String amount,
        String currency,
        String proposedByName,
        Instant proposedAt,
        boolean slaBreached) {
}
