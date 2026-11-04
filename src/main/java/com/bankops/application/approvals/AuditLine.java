package com.bankops.application.approvals;

import java.time.Instant;

/** Kaydın son audit satırlarından biri. {@code details} ham JSON metnidir. */
public record AuditLine(Instant at, String username, String action, String details) {
}
