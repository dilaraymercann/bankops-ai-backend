package com.bankops.application.knowledge;

import com.bankops.domain.ErrorCode;
import com.bankops.domain.TransactionType;
import java.time.Instant;

/** Bilgi tabanındaki bir doğrulanmış çözüm. {@code aiReferenceCount}: AI analizlerinde kaç kez dayanak olarak kullanıldı. */
public record KnowledgeBaseItem(
        Long caseId,
        String caseNumber,
        ErrorCode errorCode,
        TransactionType transactionType,
        String text,
        String proposedByName,
        String approvedByName,
        Instant approvedAt,
        long aiReferenceCount) {
}
