package com.bankops.domain;

/** Çözüm onayı kuralları: yalnızca takım lideri onaylar ve dört göz ilkesi uygulanır (CLAUDE.md §5, §7). */
public final class ApprovalPolicy {

    /** Çözümü kapatma (onay) yetkisi: TeamLead olmalı ve çözümü öneren kişi olmamalıdır. */
    public void assertCanApprove(Role actorRole, Long actorUserId, Long proposedByUserId) {
        if (actorRole != Role.TeamLead) {
            throw new ForbiddenOperationException("Çözümleri yalnızca takım lideri onaylayabilir");
        }
        if (actorUserId.equals(proposedByUserId)) {
            throw new FourEyesViolationException();
        }
    }

    /** Revizyon isteğinde onaylayanın notu zorunludur. */
    public void assertRevisionNoteProvided(String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Revizyon isteğinde not zorunludur");
        }
    }
}
