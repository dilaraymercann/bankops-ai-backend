package com.bankops.application.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bankops.domain.CaseStatus;
import org.junit.jupiter.api.Test;

class AuditDetailsTest {

    @Test
    void buildsJsonWithStringsEnumsNumbersBooleansAndNull() {
        String json = AuditDetails.of("from", CaseStatus.InReview, "id", 42, "ok", true, "note", null);
        assertThat(json).isEqualTo("{\"from\":\"InReview\",\"id\":42,\"ok\":true,\"note\":null}");
    }

    @Test
    void escapesQuotesBackslashesAndNewlines() {
        String json = AuditDetails.of("note", "Müşteri \"Ali\" dedi\\ ve\nçıktı");
        assertThat(json).isEqualTo("{\"note\":\"Müşteri \\\"Ali\\\" dedi\\\\ ve\\nçıktı\"}");
    }

    @Test
    void emptyCallGivesEmptyObject() {
        assertThat(AuditDetails.of()).isEqualTo("{}");
    }

    @Test
    void rejectsUnpairedArguments() {
        assertThatThrownBy(() -> AuditDetails.of("onlyKey")).isInstanceOf(IllegalArgumentException.class);
    }
}
