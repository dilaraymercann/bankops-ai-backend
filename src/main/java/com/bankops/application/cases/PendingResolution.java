package com.bankops.application.cases;

import java.time.Instant;

/** Onay bekleyen çözüm özeti (kayıt detayında gösterilir). */
public record PendingResolution(String text, String proposedByName, Instant proposedAt) {
}
