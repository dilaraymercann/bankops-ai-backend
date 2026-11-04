package com.bankops.domain;

import static com.bankops.domain.CaseStatus.Assigned;
import static com.bankops.domain.CaseStatus.AwaitingInfo;
import static com.bankops.domain.CaseStatus.Closed;
import static com.bankops.domain.CaseStatus.Escalated;
import static com.bankops.domain.CaseStatus.InReview;
import static com.bankops.domain.CaseStatus.New;
import static com.bankops.domain.CaseStatus.Rejected;
import static com.bankops.domain.CaseStatus.SolutionProposed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CaseStateMachineTest {

    private final CaseStateMachine stateMachine = new CaseStateMachine();

    /** Beklenen tüm geçerli geçişler; bunların dışındaki her çift geçersiz olmalıdır. */
    private static final Map<CaseStatus, Set<CaseStatus>> EXPECTED = Map.of(
            New, EnumSet.of(Assigned, Escalated),
            Assigned, EnumSet.of(InReview, Escalated),
            InReview, EnumSet.of(AwaitingInfo, SolutionProposed, Rejected, Escalated),
            AwaitingInfo, EnumSet.of(InReview, Escalated),
            SolutionProposed, EnumSet.of(Closed, InReview, Escalated),
            Escalated, EnumSet.of(InReview),
            Closed, EnumSet.noneOf(CaseStatus.class),
            Rejected, EnumSet.noneOf(CaseStatus.class));

    @Test
    void everyPairMatchesTheExpectedTable() {
        for (CaseStatus from : CaseStatus.values()) {
            for (CaseStatus to : CaseStatus.values()) {
                boolean expected = EXPECTED.get(from).contains(to);
                assertThat(stateMachine.canTransition(from, to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void validTransitionDoesNotThrow() {
        assertThatCode(() -> stateMachine.validate(InReview, SolutionProposed)).doesNotThrowAnyException();
    }

    @Test
    void invalidTransitionThrows() {
        assertThatThrownBy(() -> stateMachine.validate(New, Closed))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("New")
                .hasMessageContaining("Closed");
    }

    @Test
    void closedAndRejectedAreTerminal() {
        for (CaseStatus target : CaseStatus.values()) {
            assertThat(stateMachine.canTransition(Closed, target)).isFalse();
            assertThat(stateMachine.canTransition(Rejected, target)).isFalse();
        }
    }

    @Test
    void everyOpenStateCanBeEscalatedExceptEscalatedItself() {
        for (CaseStatus status : CaseStatus.values()) {
            boolean expected = status.isOpen() && status != Escalated;
            assertThat(stateMachine.canTransition(status, Escalated)).as(status.name()).isEqualTo(expected);
        }
    }

    @Test
    void escalatedCaseOnlyGoesBackToReview() {
        assertThat(stateMachine.allowedTargets(Escalated)).containsExactly(InReview);
    }

    @Test
    void manualTargetsExcludeAssignProposeAndApprove() {
        assertThat(stateMachine.manualTargets(New)).containsExactly(Escalated);
        assertThat(stateMachine.manualTargets(Assigned)).containsExactlyInAnyOrder(InReview, Escalated);
        assertThat(stateMachine.manualTargets(InReview)).containsExactlyInAnyOrder(AwaitingInfo, Rejected, Escalated);
        assertThat(stateMachine.manualTargets(AwaitingInfo)).containsExactlyInAnyOrder(InReview, Escalated);
        assertThat(stateMachine.manualTargets(Escalated)).containsExactly(InReview);
    }

    @Test
    void pendingApprovalCanOnlyBeLeftThroughTheApprovalScreen() {
        assertThat(stateMachine.manualTargets(SolutionProposed)).isEmpty();
    }

    @Test
    void closedAndRejectedHaveNoManualTargets() {
        assertThat(stateMachine.manualTargets(Closed)).isEmpty();
        assertThat(stateMachine.manualTargets(Rejected)).isEmpty();
    }

    @Test
    void onlyRejectAndEscalateRequireAReason() {
        for (CaseStatus status : CaseStatus.values()) {
            assertThat(stateMachine.requiresReason(status))
                    .as(status.name())
                    .isEqualTo(status == Rejected || status == Escalated);
        }
    }
}
