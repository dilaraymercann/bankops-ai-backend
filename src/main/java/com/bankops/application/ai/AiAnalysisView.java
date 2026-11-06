package com.bankops.application.ai;

import com.bankops.domain.AiFeedback;
import java.time.Instant;
import java.util.List;

/**
 * AI analiz paneli için yanıt. {@code available = false} ise güvenilir geçmiş kayıt bulunamamıştır:
 * model çağrılmaz, {@code message} arayüzde boş durum olarak gösterilir.
 */
public record AiAnalysisView(
        Long id,
        boolean available,
        String message,
        AiAnalysisResult result,
        List<AiReference> references,
        int similarRecordCount,
        int maskedFieldCount,
        String modelName,
        Instant createdAt,
        AiFeedback feedback,
        boolean cached) {
}
