package com.bankops.application.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AnalysisRateLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-04T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private AnalysisRateLimiter limiter(int perUser, int global) {
        return new AnalysisRateLimiter(new AiProperties(0.7, 5, 24, perUser, global, null), clock);
    }

    @Test
    void blocksTheRequestOverThePerUserLimitButNotOtherUsers() {
        AnalysisRateLimiter limiter = limiter(2, 100);

        limiter.acquire(1L);
        limiter.acquire(1L);

        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(RateLimitExceededException.class);
        limiter.acquire(2L);   // başka kullanıcı etkilenmez
    }

    @Test
    void blocksTheRequestOverTheGlobalLimit() {
        AnalysisRateLimiter limiter = limiter(10, 2);

        limiter.acquire(1L);
        limiter.acquire(2L);

        assertThatThrownBy(() -> limiter.acquire(3L)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void allowsRequestsAgainOnceTheWindowHasPassed() {
        AnalysisRateLimiter limiter = limiter(1, 100);
        limiter.acquire(1L);
        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(RateLimitExceededException.class);

        now.set(now.get().plus(Duration.ofSeconds(61)));

        limiter.acquire(1L);
        assertThat(now.get()).isAfter(Instant.parse("2026-11-04T10:01:00Z"));
    }
}
