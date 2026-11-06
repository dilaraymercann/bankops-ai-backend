package com.bankops.application.ai;

/** AI sağlayıcısına ulaşılamadı ya da yapılandırılmamış (API'de 503). */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
