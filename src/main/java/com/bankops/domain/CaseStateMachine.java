package com.bankops.domain;

import static com.bankops.domain.CaseStatus.Assigned;
import static com.bankops.domain.CaseStatus.AwaitingInfo;
import static com.bankops.domain.CaseStatus.Closed;
import static com.bankops.domain.CaseStatus.Escalated;
import static com.bankops.domain.CaseStatus.InReview;
import static com.bankops.domain.CaseStatus.New;
import static com.bankops.domain.CaseStatus.Rejected;
import static com.bankops.domain.CaseStatus.SolutionProposed;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * İstisna kaydının durum makinesi (CLAUDE.md §7). İzin verilen geçişler tek bir tabloda tutulur;
 * tabloda olmayan her geçiş reddedilir. Rol ve dört göz kontrolleri {@link ApprovalPolicy} içindedir.
 */
public final class CaseStateMachine {

    private static final Map<CaseStatus, Set<CaseStatus>> ALLOWED = new EnumMap<>(CaseStatus.class);

    static {
        ALLOWED.put(New, EnumSet.of(Assigned, Escalated));
        ALLOWED.put(Assigned, EnumSet.of(InReview, Escalated));
        ALLOWED.put(InReview, EnumSet.of(AwaitingInfo, SolutionProposed, Rejected, Escalated));
        ALLOWED.put(AwaitingInfo, EnumSet.of(InReview, Escalated));
        // Closed: onay (dört göz); InReview: revizyon isteği
        ALLOWED.put(SolutionProposed, EnumSet.of(Closed, InReview, Escalated));
        // Eskalasyondan yalnızca takım lideri yeniden atayınca çıkılır
        ALLOWED.put(Escalated, EnumSet.of(InReview));
        ALLOWED.put(Closed, EnumSet.noneOf(CaseStatus.class));
        ALLOWED.put(Rejected, EnumSet.noneOf(CaseStatus.class));
    }

    public boolean canTransition(CaseStatus from, CaseStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public void validate(CaseStatus from, CaseStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidTransitionException(from, to);
        }
    }

    public Set<CaseStatus> allowedTargets(CaseStatus from) {
        return Set.copyOf(ALLOWED.get(from));
    }

    /** Genel "durum değiştir" ile seçilemeyen hedefler: atama, çözüm önerme ve onay kendi uç noktalarından geçer. */
    private static final Set<CaseStatus> DEDICATED_TARGETS = EnumSet.of(Assigned, SolutionProposed, Closed);

    /**
     * Kullanıcının durum açılır listesinden seçebileceği hedefler (sıralı). Onay bekleyen kayıttan
     * (SolutionProposed) yalnızca onay ekranıyla çıkılır: onay veya revizyon isteği.
     */
    public Set<CaseStatus> manualTargets(CaseStatus from) {
        if (from == SolutionProposed) {
            return EnumSet.noneOf(CaseStatus.class);
        }
        Set<CaseStatus> targets = EnumSet.noneOf(CaseStatus.class);
        targets.addAll(ALLOWED.get(from));
        targets.removeAll(DEDICATED_TARGETS);
        return targets;
    }

    /** İade (Rejected) ve manuel eskalasyon gerekçesiz yapılamaz. */
    public boolean requiresReason(CaseStatus target) {
        return target == Rejected || target == Escalated;
    }
}
