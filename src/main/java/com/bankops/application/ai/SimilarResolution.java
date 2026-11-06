package com.bankops.application.ai;

import com.bankops.domain.ErrorCode;
import com.bankops.domain.TransactionType;

/** Benzerlik aramasının bulduğu doğrulanmış geçmiş çözüm. {@code similarity}: 1 = aynı yönde, 0 = ilgisiz. */
public record SimilarResolution(
        Long resolutionId,
        Long caseId,
        String caseNumber,
        ErrorCode errorCode,
        TransactionType transactionType,
        String text,
        double similarity,
        String matchReason) {
}
