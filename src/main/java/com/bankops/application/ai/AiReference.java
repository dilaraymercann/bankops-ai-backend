package com.bankops.application.ai;

import com.bankops.domain.ErrorCode;

/** Analizin dayandığı geçmiş kayıt. Seed'den gelen analizlerde benzerlik skoru bilinmez ({@code null}). */
public record AiReference(
        String caseNumber,
        ErrorCode errorCode,
        Double similarity,
        String matchReason,
        String resolutionText) {
}
