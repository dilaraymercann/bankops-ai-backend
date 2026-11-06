package com.bankops.application.ai;

import com.bankops.application.approvals.ResolutionApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Onay işlemi COMMIT olduktan sonra embedding'i hesaplar. Dış API çağrısı onay transaction'ının (ve kayıt kilidinin)
 * içinde yapılmaz; API çökse bile onay başarılı kalır, eksik embedding {@code embed} profiliyle sonradan doldurulur.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ResolutionEmbeddingListener {

    private final ResolutionEmbedder embedder;
    private final TextEmbedder textEmbedder;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onApproved(ResolutionApprovedEvent event) {
        if (!textEmbedder.isConfigured()) {
            log.warn("AI yapılandırılmamış: çözüm {} için embedding atlandı", event.resolutionId());
            return;
        }
        try {
            embedder.embed(event.resolutionId());
        } catch (RuntimeException e) {
            log.warn("Çözüm {} için embedding hesaplanamadı, sonradan doldurulacak: {}", event.resolutionId(), e.getMessage());
        }
    }
}
