package com.bankops.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CaseAccessPolicyTest {

    private final CaseAccessPolicy policy = new CaseAccessPolicy();

    @Test
    void specialistSeesOnlyOwnCases() {
        assertThat(policy.canView(Role.Specialist, 1L, 1L)).isTrue();
        assertThat(policy.canView(Role.Specialist, 1L, 2L)).isFalse();
        assertThat(policy.canView(Role.Specialist, 1L, null)).isFalse();
    }

    @Test
    void teamLeadSeesEverything() {
        assertThat(policy.canView(Role.TeamLead, 6L, 1L)).isTrue();
        assertThat(policy.canView(Role.TeamLead, 6L, null)).isTrue();
    }

    @Test
    void managerSeesNoCases() {
        assertThat(policy.canView(Role.Manager, 7L, 7L)).isFalse();
    }

    @Test
    void assertThrowsWhenNotAllowed() {
        assertThatThrownBy(() -> policy.assertCanView(Role.Specialist, 1L, 2L))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatCode(() -> policy.assertCanView(Role.Specialist, 1L, 1L)).doesNotThrowAnyException();
    }
}
