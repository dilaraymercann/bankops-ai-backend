package com.bankops.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ApprovalPolicyTest {

    private static final Long SPECIALIST_ID = 1L;
    private static final Long OTHER_TEAM_LEAD_ID = 6L;

    private final ApprovalPolicy policy = new ApprovalPolicy();

    @Test
    void teamLeadCanApproveSomeoneElsesResolution() {
        assertThatCode(() -> policy.assertCanApprove(Role.TeamLead, OTHER_TEAM_LEAD_ID, SPECIALIST_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void proposerCannotApproveOwnResolution() {
        // Öneren kişinin kendisi takım lideri olsa bile dört göz ilkesi geçerlidir
        assertThatThrownBy(() -> policy.assertCanApprove(Role.TeamLead, OTHER_TEAM_LEAD_ID, OTHER_TEAM_LEAD_ID))
                .isInstanceOf(FourEyesViolationException.class);
    }

    @Test
    void specialistCannotApprove() {
        assertThatThrownBy(() -> policy.assertCanApprove(Role.Specialist, 2L, SPECIALIST_ID))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void managerCannotApprove() {
        assertThatThrownBy(() -> policy.assertCanApprove(Role.Manager, 7L, SPECIALIST_ID))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void revisionRequiresANote(String note) {
        assertThatThrownBy(() -> policy.assertRevisionNoteProvided(note))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revisionWithNoteIsAccepted() {
        assertThatCode(() -> policy.assertRevisionNoteProvided("Teyit kaydı eksik"))
                .doesNotThrowAnyException();
    }
}
