package com.bankops.application.cases;

import com.bankops.domain.SlaState;

/**
 * Bir açık kaydın SLA görünümü. {@code remainingMinutes}: kalan çalışma süresi, süre aşıldıysa negatif (aşım).
 * {@code progress}: 0..1 arası, sürenin ne kadarının dolduğu (ilerleme çubuğu için).
 */
public record SlaView(SlaState state, long remainingMinutes, double progress) {
}
