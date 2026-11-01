package com.bankops.infrastructure.config;

import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code bankops.sla.*} ayarları. */
@ConfigurationProperties("bankops.sla")
public record SlaProperties(
        LocalTime businessHoursStart,
        LocalTime businessHoursEnd,
        ZoneId timeZone,
        long checkInterval) {
}
