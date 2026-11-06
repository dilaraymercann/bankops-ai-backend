package com.bankops.application.ai;

/** AI analiz istek sınırı aşıldı (API'de 429). */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
