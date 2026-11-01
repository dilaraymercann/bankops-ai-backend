package com.bankops.domain;

/** İstisna kaydının durumu (CLAUDE.md §7). Sabit adları API ve veritabanındaki değerlerle birebir aynıdır. */
public enum CaseStatus {
    New,
    Assigned,
    InReview,
    AwaitingInfo,
    SolutionProposed,
    Escalated,
    Closed,
    Rejected;

    /** Kapanmamış (üzerinde hâlâ çalışılan) durumlar. */
    public boolean isOpen() {
        return this != Closed && this != Rejected;
    }
}
