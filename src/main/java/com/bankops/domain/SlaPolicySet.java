package com.bankops.domain;

import java.time.Duration;
import java.util.List;

/** SLA kurallarının sorgulanması: hata koduna özel kural varsa o, yoksa önceliğin varsayılan kuralı geçerlidir. */
public final class SlaPolicySet {

    private final List<SlaPolicy> policies;

    public SlaPolicySet(List<SlaPolicy> policies) {
        this.policies = List.copyOf(policies);
    }

    public Duration durationFor(Priority priority, ErrorCode errorCode) {
        return policies.stream()
                .filter(p -> p.getPriority() == priority && p.getErrorCode() == errorCode)
                .findFirst()
                .or(() -> policies.stream()
                        .filter(p -> p.getPriority() == priority && p.getErrorCode() == null)
                        .findFirst())
                .map(p -> Duration.ofMinutes(p.getDurationMinutes()))
                .orElseThrow(() -> new IllegalStateException("SLA kuralı bulunamadı: " + priority));
    }
}
