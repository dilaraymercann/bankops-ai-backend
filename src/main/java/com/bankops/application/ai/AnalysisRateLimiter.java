package com.bankops.application.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Basit kayan pencere istek sınırı: kullanıcı başına ve tüm sistem için dakikada en fazla N analiz.
 * Demo herkese açık olduğundan ücretsiz API kotasını korur. Bellekte tutulur (tek sunucu için yeterli).
 */
@Component
@RequiredArgsConstructor
public class AnalysisRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final AiProperties properties;
    private final Clock clock;
    private final Map<Long, Deque<Instant>> perUser = new HashMap<>();
    private final Deque<Instant> global = new ArrayDeque<>();

    /** Sınır aşılmadıysa isteği sayar; aşıldıysa {@link RateLimitExceededException} fırlatır. */
    public synchronized void acquire(Long userId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        purge(global, cutoff);
        Deque<Instant> own = perUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        purge(own, cutoff);

        if (own.size() >= properties.userLimitPerMinute() || global.size() >= properties.globalLimitPerMinute()) {
            throw new RateLimitExceededException("Çok fazla analiz isteği gönderildi. Lütfen bir dakika sonra tekrar deneyin.");
        }
        own.addLast(now);
        global.addLast(now);
        perUser.values().removeIf(Deque::isEmpty);
    }

    private static void purge(Deque<Instant> timestamps, Instant cutoff) {
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.pollFirst();
        }
    }
}
