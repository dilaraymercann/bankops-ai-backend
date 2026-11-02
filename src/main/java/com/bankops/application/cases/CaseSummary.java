package com.bankops.application.cases;

/** Kuyruk özet kartları. {@code awaitingReview}: henüz incelemeye alınmamış (Yeni + Atandı). */
public record CaseSummary(long open, long slaBreached, long slaApproaching, long awaitingReview, long inReview) {
}
