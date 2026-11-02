package com.bankops.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code bankops.jwt.*} ayarları. Secret ortam değişkeninden gelir ve en az 32 karakter olmalıdır. */
@ConfigurationProperties("bankops.jwt")
public record JwtProperties(String issuer, long expiryMinutes, String secret) {
}
