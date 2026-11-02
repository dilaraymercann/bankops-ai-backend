package com.bankops.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code bankops.cors.*}: API'ye yalnızca bu adreslerdeki frontend'ler erişebilir. */
@ConfigurationProperties("bankops.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
