package com.bankops.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bankops.TestcontainersConfiguration;
import com.bankops.domain.AiAnalysis;
import com.bankops.domain.ErrorCode;
import com.bankops.domain.IbanValidator;
import com.bankops.domain.Priority;
import com.bankops.domain.SlaCalculator;
import com.bankops.domain.SlaState;
import com.bankops.infrastructure.config.SlaProperties;
import com.bankops.application.repository.AiAnalysisRepository;
import com.bankops.seed.SeedDataService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Faz 1 kabul testi: seed gerçek bir PostgreSQL'e CLAUDE.md §13'teki veriyi yazar ve tutarlıdır. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeedDataIntegrationTest {

    @Autowired
    private SeedDataService seedDataService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AiAnalysisRepository analysisRepository;

    @Autowired
    private SlaCalculator slaCalculator;

    @Autowired
    private SlaProperties slaProperties;

    @Autowired
    private Clock clock;

    private SeedDataService.SeedSummary summary;

    @BeforeAll
    void seed() {
        summary = seedDataService.seed("test-only-password");
    }

    @Test
    void createsDemoUsersWithHashedPasswords() {
        assertThat(summary.users()).isEqualTo(7);
        List<Map<String, Object>> users = jdbc.queryForList("SELECT full_name, role, password_hash FROM users ORDER BY id");
        assertThat(users).extracting(row -> row.get("role")).containsExactly(
                "Specialist", "Specialist", "Specialist", "Specialist", "Specialist", "TeamLead", "Manager");
        assertThat(users).allSatisfy(row -> assertThat((String) row.get("password_hash")).startsWith("$2"));
    }

    @Test
    void createsAboutTwoThousandFiveHundredCasesWithAboutOneHundredFortyOpen() {
        assertThat(count("SELECT count(*) FROM exception_cases")).isEqualTo(2500);
        assertThat(count("SELECT count(*) FROM exception_cases WHERE status NOT IN ('Closed','Rejected')")).isEqualTo(140);
    }

    @Test
    void knowledgeBaseHasFewSanctionResolutions() {
        long knowledgeBase = count("SELECT count(*) FROM resolutions WHERE is_verified AND include_in_knowledge_base");
        assertThat(knowledgeBase).isBetween(300L, 500L);

        long sanction = count("""
                SELECT count(*) FROM resolutions r JOIN exception_cases c ON c.id = r.case_id
                WHERE r.is_verified AND r.include_in_knowledge_base AND c.error_code = 'SANCTION_HIT'""");
        assertThat(sanction).isBetween(5L, 15L);
    }

    @Test
    void onlyApprovedResolutionsAreVerifiedAndNeverByTheirProposer() {
        assertThat(count("SELECT count(*) FROM resolutions WHERE is_verified AND approved_by_user_id = proposed_by_user_id"))
                .isZero();
        assertThat(count("SELECT count(*) FROM resolutions WHERE NOT is_verified AND include_in_knowledge_base")).isZero();
        // Kapalı her kayıt (reddedilenler hariç) onaylanmış bir çözüme sahiptir
        assertThat(count("""
                SELECT count(*) FROM exception_cases c
                WHERE c.status = 'Closed'
                  AND NOT EXISTS (SELECT 1 FROM resolutions r WHERE r.case_id = c.id AND r.is_verified)""")).isZero();
    }

    @Test
    void ibansAreValidExceptForTheIbanChecksumErrorCases() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.error_code, t.sender_iban, t.receiver_iban
                FROM exception_cases c JOIN payment_transactions t ON t.id = c.transaction_id""");
        assertThat(rows).hasSize(2500).allSatisfy(row -> {
            assertThat(IbanValidator.isValid((String) row.get("sender_iban"))).isTrue();
            boolean receiverValid = IbanValidator.isValid((String) row.get("receiver_iban"));
            assertThat(receiverValid).isEqualTo(!ErrorCode.IBAN_CHECKSUM.name().equals(row.get("error_code")));
        });
    }

    @Test
    void amountsAreExactDecimals() {
        assertThat(count("SELECT count(*) FROM payment_transactions WHERE amount <= 0")).isZero();
        assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns "
                + "WHERE table_name='payment_transactions' AND column_name='amount'", String.class)).isEqualTo("numeric");
    }

    @Test
    void errorCodesRespectTheCatalogTransactionTypes() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.error_code, t.type
                FROM exception_cases c JOIN payment_transactions t ON t.id = c.transaction_id""");
        assertThat(rows).allSatisfy(row -> {
            ErrorCode code = ErrorCode.valueOf((String) row.get("error_code"));
            assertThat(code.applicableTypes().stream().map(Enum::name)).contains((String) row.get("type"));
        });
    }

    @Test
    void sanctionHitIsTheRarestErrorCodeAndNameMismatchTheMostCommon() {
        List<String> byFrequency = jdbc.queryForList(
                "SELECT error_code FROM exception_cases GROUP BY error_code ORDER BY count(*) DESC", String.class);
        assertThat(byFrequency.getFirst()).isEqualTo("NAME_MISMATCH");
        assertThat(byFrequency.getLast()).isEqualTo("SANCTION_HIT");
    }

    @Test
    void containsTheDemoScenarioCase() {
        assertThat(count("""
                SELECT count(*) FROM exception_cases c JOIN users u ON u.id = c.assigned_to_user_id
                WHERE u.full_name = 'Elif Kaya' AND c.error_code = 'NAME_MISMATCH'
                  AND c.status = 'InReview' AND c.sla_deadline < now() AND c.is_escalated""")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aiAnalysesOnlyReferenceKnowledgeBaseCases() {
        Set<String> knowledgeBase = new HashSet<>(jdbc.queryForList("""
                SELECT c.case_number FROM resolutions r JOIN exception_cases c ON c.id = r.case_id
                WHERE r.is_verified AND r.include_in_knowledge_base""", String.class));
        List<AiAnalysis> analyses = analysisRepository.findAll();
        assertThat(analyses).hasSizeGreaterThan(1000);
        assertThat(analyses).allSatisfy(analysis ->
                assertThat(knowledgeBase).containsAll(analysis.getReferencedCaseIds()));
    }

    @Test
    void aiAssistedCasesResolveFasterOnAverageInWorkingTime() {
        // Gece ve hafta sonları sayılmasın diye süre mesai üzerinden (SlaCalculator) ölçülür
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.priority, c.created_at, c.closed_at,
                       EXISTS (SELECT 1 FROM ai_analyses a WHERE a.case_id = c.id) AS used_ai
                FROM exception_cases c WHERE c.status = 'Closed'""");
        double withAi = averageWorkingMinutes(rows, true);
        double withoutAi = averageWorkingMinutes(rows, false);
        assertThat(withAi).isLessThan(withoutAi);
    }

    private double averageWorkingMinutes(List<Map<String, Object>> rows, boolean usedAi) {
        return rows.stream()
                .filter(row -> usedAi == (Boolean) row.get("used_ai"))
                .mapToLong(row -> slaCalculator.workingTimeBetween(
                        Priority.valueOf((String) row.get("priority")),
                        ((java.sql.Timestamp) row.get("created_at")).toInstant(),
                        ((java.sql.Timestamp) row.get("closed_at")).toInstant()).toMinutes())
                .average().orElseThrow();
    }

    @Test
    void everyStatusChangeIsAuditedAndAuditLogIsAppendOnly() {
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'STATUS_CHANGE'"))
                .isEqualTo(count("SELECT count(*) FROM status_history"));
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET action = 'X' WHERE id = (SELECT min(id) FROM audit_logs)"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("yalnızca eklemeye açık");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("yalnızca eklemeye açık");
    }

    @Test
    void queueContainsCasesWhoseSlaIsApproaching() {
        List<Map<String, Object>> openCases = jdbc.queryForList("""
                SELECT c.priority, c.created_at, c.sla_deadline, u.full_name AS assignee
                FROM exception_cases c LEFT JOIN users u ON u.id = c.assigned_to_user_id
                WHERE c.status NOT IN ('Closed','Rejected')""");
        List<Map<String, Object>> approaching = openCases.stream()
                .filter(row -> slaCalculator.stateAt(
                        Priority.valueOf((String) row.get("priority")),
                        ((Timestamp) row.get("created_at")).toInstant(),
                        ((Timestamp) row.get("sla_deadline")).toInstant(),
                        clock.instant()) == SlaState.Approaching)
                .toList();
        assertThat(approaching).hasSizeGreaterThanOrEqualTo(5);
        // Demo kullanıcısının (Elif Kaya) kuyruğunda da sarı satırlar görünmeli
        assertThat(approaching.stream().filter(row -> "Elif Kaya".equals(row.get("assignee")))).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void humanActionsHappenDuringBusinessHoursExceptForCriticalCases() {
        // Kullanıcı tarafından yapılan her işlem (durum geçişi, AI analizi, onay, geri bildirim) mesaide olmalı; Critical 7/24 çalışır
        List<Map<String, Object>> actions = jdbc.queryForList("""
                SELECT h.changed_at AS at, 'STATUS_CHANGE' AS kind FROM status_history h
                  JOIN exception_cases c ON c.id = h.case_id
                  WHERE h.changed_by_user_id IS NOT NULL AND c.priority <> 'Critical'
                UNION ALL
                SELECT a.timestamp, a.action FROM audit_logs a
                  JOIN exception_cases c ON a.entity_type = 'ExceptionCase' AND c.id = a.entity_id
                  WHERE a.user_id IS NOT NULL AND a.action IN ('AI_ANALYZE', 'APPROVE') AND c.priority <> 'Critical'
                UNION ALL
                SELECT a.timestamp, a.action FROM audit_logs a
                  JOIN ai_analyses s ON a.entity_type = 'AiAnalysis' AND s.id = a.entity_id
                  JOIN exception_cases c ON c.id = s.case_id
                  WHERE a.user_id IS NOT NULL AND c.priority <> 'Critical'""");
        assertThat(actions).hasSizeGreaterThan(5000);

        ZoneId zone = slaProperties.timeZone();
        List<String> outside = actions.stream()
                .filter(row -> {
                    LocalDateTime local = ((Timestamp) row.get("at")).toInstant().atZone(zone).toLocalDateTime();
                    boolean workingTime = !local.toLocalTime().isBefore(slaProperties.businessHoursStart())
                            && !local.toLocalTime().isAfter(slaProperties.businessHoursEnd());
                    return !(slaCalculator.isBusinessDay(local.toLocalDate()) && workingTime);
                })
                .map(row -> row.get("kind") + " @ " + row.get("at"))
                .limit(5)
                .toList();
        assertThat(outside).as("mesai dışındaki işlemler").isEmpty();
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private String averageResolutionMinutes(boolean withAi) {
        return """
                SELECT avg(extract(epoch FROM (c.closed_at - c.created_at)) / 60)
                FROM exception_cases c
                WHERE c.status = 'Closed'
                  AND %s EXISTS (SELECT 1 FROM ai_analyses a WHERE a.case_id = c.id)""".formatted(withAi ? "" : "NOT");
    }
}
