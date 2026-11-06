package com.bankops.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bankops.TestcontainersConfiguration;
import com.bankops.application.repository.UserRepository;
import com.bankops.application.sla.SlaEscalationService;
import com.bankops.seed.SeedDataService;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Faz 3 kabul testi: not, durum geçişi, çözüm önerme, onay (dört göz), atama, audit ve SLA eskalasyonu.
 * Veriyi değiştirdiği için kendi bağlamında (ve kendi veritabanında) çalışır; diğer testleri etkilemez.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WorkflowIntegrationTest {

    private static final String PASSWORD = "test-only-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SeedDataService seedDataService;

    @Autowired
    private SlaEscalationService escalationService;

    private final Map<String, String> tokens = new HashMap<>();

    @BeforeAll
    void seed() {
        if (userRepository.count() == 0) {
            seedDataService.seed(PASSWORD);
        }
        // Seed "şimdi"ye göre veri ürettiğinden uzmanlara düşen kayıt sayısı günün saatine göre değişir.
        // Testler birbirine karışmasın diye her biri ayrı kayıt kullanır; gerekli kayıtlar burada garanti edilir.
        ensureInReviewCases("elif.kaya", 12);
        ensureInReviewCases("can.arslan", 2);
    }

    private void ensureInReviewCases(String username, int needed) {
        int missing = needed - count("SELECT count(*) FROM exception_cases c JOIN users u ON u.id = c.assigned_to_user_id "
                + "WHERE u.username = '" + username + "' AND c.status = 'InReview'");
        if (missing > 0) {
            jdbc.update("UPDATE exception_cases SET assigned_to_user_id = ?, status = 'InReview' WHERE id IN "
                    + "(SELECT id FROM exception_cases WHERE status IN ('New', 'Assigned') ORDER BY id LIMIT ?)", userId(username), missing);
        }
    }

    // ---------------------------------------------------------------- uçtan uca akış

    @Test
    void specialistProposesAndTeamLeadApprovesAndClosesTheCase() throws Exception {
        Long caseId = inReviewCaseOf("elif.kaya", 0);
        int historyBefore = count("SELECT count(*) FROM status_history WHERE case_id = " + caseId);

        // 1) Uzman not ekler
        send(post("/api/cases/{id}/notes", caseId), "elif.kaya", "{\"text\":\"Müşteri arandı, geri dönüş bekleniyor.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[*].text", hasItem("Müşteri arandı, geri dönüş bekleniyor.")));

        // 2) Uzman çözüm önerir: kayıt onay bekler
        send(post("/api/cases/{id}/resolution", caseId), "elif.kaya", "{\"text\":\"Doğru IBAN teyit edildi, talimat yeniden gönderildi.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SolutionProposed"))
                .andExpect(jsonPath("$.pendingResolution.proposedByName").value("Elif Kaya"))
                .andExpect(jsonPath("$.allowedTransitions", empty()));

        // 3) Uzman onay uç noktalarına giremez, kendi çözümünü onaylayamaz
        send(get("/api/approvals"), "elif.kaya", null).andExpect(status().isForbidden());
        send(post("/api/approvals/{id}/approve", caseId), "elif.kaya", "{\"includeInKnowledgeBase\":true}")
                .andExpect(status().isForbidden());

        // 4) Takım lideri listede görür, incelemede dört göz kontrolü geçer
        send(get("/api/approvals"), "burak.demir", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].caseId", hasItem(caseId.intValue())));
        send(get("/api/approvals/{id}", caseId), "burak.demir", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fourEyes.passed").value(true))
                .andExpect(jsonPath("$.resolutionText").value("Doğru IBAN teyit edildi, talimat yeniden gönderildi."))
                .andExpect(jsonPath("$.recentAudit[*].action", hasItem("RESOLUTION_PROPOSE")));

        // 5) Takım lideri onaylar ve bilgi tabanına ekler
        send(post("/api/approvals/{id}/approve", caseId), "burak.demir", "{\"includeInKnowledgeBase\":true,\"note\":\"Teyit kaydı doğru.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Closed"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        // 6) Veritabanında doğrulanmış çözüm, kapatan kullanıcı, geçmiş ve audit kayıtları var
        Map<String, Object> resolution = jdbc.queryForMap(
                "SELECT is_verified, include_in_knowledge_base, approved_by_user_id, proposed_by_user_id FROM resolutions WHERE case_id = ?", caseId);
        assertThat(resolution.get("is_verified")).isEqualTo(true);
        assertThat(resolution.get("include_in_knowledge_base")).isEqualTo(true);
        assertThat(resolution.get("approved_by_user_id")).isNotEqualTo(resolution.get("proposed_by_user_id"));
        assertThat(jdbc.queryForObject("SELECT u.username FROM exception_cases c JOIN users u ON u.id = c.closed_by_user_id WHERE c.id = ?", String.class, caseId))
                .isEqualTo("burak.demir");
        // İncelemede -> ÇözümÖnerildi ve ÇözümÖnerildi -> Kapatıldı: iki yeni geçiş
        assertThat(count("SELECT count(*) FROM status_history WHERE case_id = " + caseId)).isEqualTo(historyBefore + 2);
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_type = 'ExceptionCase' AND entity_id = ?", String.class, caseId))
                .contains("NOTE_ADD", "RESOLUTION_PROPOSE", "APPROVE", "STATUS_CHANGE");

        // 7) Kapanan kayda artık not eklenemez
        send(post("/api/cases/{id}/notes", caseId), "burak.demir", "{\"text\":\"Geç not\"}").andExpect(status().isBadRequest());
    }

    @Test
    void proposerCannotApproveTheirOwnResolution() throws Exception {
        Long caseId = inReviewCaseOf("elif.kaya", 1);
        send(post("/api/cases/{id}/resolution", caseId), "elif.kaya", "{\"text\":\"Çözüm metni\"}").andExpect(status().isOk());

        // Öneren kişinin takım lideri olduğu durumu simüle et: dört göz kuralı yine de uygulanmalı
        Long burakId = userId("burak.demir");
        jdbc.update("UPDATE resolutions SET proposed_by_user_id = ? WHERE case_id = ? AND NOT is_verified", burakId, caseId);

        send(get("/api/approvals/{id}", caseId), "burak.demir", null)
                .andExpect(jsonPath("$.fourEyes.passed").value(false))
                .andExpect(jsonPath("$.fourEyes.message", containsString("onaylayamazsınız")));
        send(post("/api/approvals/{id}/approve", caseId), "burak.demir", "{\"includeInKnowledgeBase\":true}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("Dört göz")));

        assertThat(jdbc.queryForObject("SELECT status FROM exception_cases WHERE id = ?", String.class, caseId)).isEqualTo("SolutionProposed");
    }

    @Test
    void revisionRequiresANoteAndSendsTheCaseBackToTheSpecialist() throws Exception {
        Long caseId = inReviewCaseOf("elif.kaya", 2);
        send(post("/api/cases/{id}/resolution", caseId), "elif.kaya", "{\"text\":\"İlk öneri\"}").andExpect(status().isOk());

        send(post("/api/approvals/{id}/request-revision", caseId), "burak.demir", "{\"note\":\"  \"}").andExpect(status().isBadRequest());
        send(post("/api/approvals/{id}/request-revision", caseId), "burak.demir", "{\"note\":\"Teyit kaydı eksik\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("InReview"))
                .andExpect(jsonPath("$.history[-1:].reason", hasItem("Revizyon: Teyit kaydı eksik")));

        // Uzman düzeltilmiş çözümü yeniden önerebilir
        send(post("/api/cases/{id}/resolution", caseId), "elif.kaya", "{\"text\":\"Düzeltilmiş öneri\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingResolution.text").value("Düzeltilmiş öneri"));
    }

    @Test
    void onlyOneOfTwoSimultaneousApprovalsSucceeds() throws Exception {
        Long caseId = inReviewCaseOf("elif.kaya", 3);
        send(post("/api/cases/{id}/resolution", caseId), "elif.kaya", "{\"text\":\"Eşzamanlı onay testi\"}").andExpect(status().isOk());
        String burak = bearer("burak.demir");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> approve = () -> mockMvc.perform(post("/api/approvals/{id}/approve", caseId)
                            .header("Authorization", burak).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"includeInKnowledgeBase\":false}"))
                    .andReturn().getResponse().getStatus();
            Future<Integer> first = pool.submit(approve);
            Future<Integer> second = pool.submit(approve);
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'APPROVE' AND entity_id = " + caseId)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- yetki ve geçiş kuralları

    @Test
    void specialistCannotChangeSomeoneElsesCase() throws Exception {
        Long othersCase = inReviewCaseOf("can.arslan", 0);
        send(post("/api/cases/{id}/notes", othersCase), "elif.kaya", "{\"text\":\"Not\"}").andExpect(status().isForbidden());
        send(post("/api/cases/{id}/transitions", othersCase), "elif.kaya", "{\"toStatus\":\"AwaitingInfo\"}").andExpect(status().isForbidden());
        send(post("/api/cases/{id}/resolution", othersCase), "elif.kaya", "{\"text\":\"Çözüm\"}").andExpect(status().isForbidden());
    }

    @Test
    void teamLeadCannotProposeResolutions() throws Exception {
        Long caseId = inReviewCaseOf("elif.kaya", 4);
        send(post("/api/cases/{id}/resolution", caseId), "burak.demir", "{\"text\":\"Çözüm\"}").andExpect(status().isForbidden());
    }

    @Test
    void transitionRulesAreEnforced() throws Exception {
        Long assigned = caseWithStatus("Assigned", 0);
        // Durum makinesinde olmayan geçiş: 409
        send(post("/api/cases/{id}/transitions", assigned), "burak.demir", "{\"toStatus\":\"AwaitingInfo\"}").andExpect(status().isConflict());
        send(post("/api/cases/{id}/transitions", assigned), "burak.demir", "{\"toStatus\":\"Closed\"}").andExpect(status().isConflict());
        // Geçerli geçiş
        send(post("/api/cases/{id}/transitions", assigned), "burak.demir", "{\"toStatus\":\"InReview\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("InReview"));

        // Çözüm önerme ve onay genel geçiş listesinden yapılamaz (400)
        send(post("/api/cases/{id}/transitions", assigned), "burak.demir", "{\"toStatus\":\"SolutionProposed\"}").andExpect(status().isBadRequest());
        // İade ve eskalasyon gerekçe ister
        send(post("/api/cases/{id}/transitions", assigned), "burak.demir", "{\"toStatus\":\"Rejected\"}").andExpect(status().isBadRequest());
        send(post("/api/cases/{id}/transitions", assigned), "burak.demir", "{\"toStatus\":\"Escalated\"}").andExpect(status().isBadRequest());

        send(post("/api/cases/{id}/transitions", assigned), "burak.demir", "{\"toStatus\":\"Rejected\",\"reason\":\"Talimat iade edildi\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Rejected"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT u.username FROM exception_cases c JOIN users u ON u.id = c.closed_by_user_id WHERE c.id = ?", String.class, assigned))
                .isEqualTo("burak.demir");
    }

    @Test
    void manualEscalationFlagsTheCaseAndOnlyTheTeamLeadCanReopenIt() throws Exception {
        Long caseId = inReviewCaseOf("elif.kaya", 5);
        send(post("/api/cases/{id}/transitions", caseId), "elif.kaya", "{\"toStatus\":\"Escalated\",\"reason\":\"Takım lideri desteği gerekiyor\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Escalated"))
                .andExpect(jsonPath("$.escalated").value(true))
                // Eskale edilen kaydı uzman tekrar açamaz: seçenek bile sunulmaz
                .andExpect(jsonPath("$.allowedTransitions", empty()));
        send(post("/api/cases/{id}/transitions", caseId), "elif.kaya", "{\"toStatus\":\"InReview\"}").andExpect(status().isForbidden());
        send(post("/api/cases/{id}/transitions", caseId), "burak.demir", "{\"toStatus\":\"InReview\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("InReview"));
    }

    // ---------------------------------------------------------------- atama

    @Test
    void teamLeadAssignsCasesAndOnlyToSpecialists() throws Exception {
        Long unassigned = caseWithStatus("New", 0);
        Long canId = userId("can.arslan");
        Long selinId = userId("selin.aydin");

        send(post("/api/cases/{id}/assign", unassigned), "elif.kaya", "{\"userId\":" + canId + "}").andExpect(status().isForbidden());
        send(post("/api/cases/{id}/assign", unassigned), "burak.demir", "{\"userId\":" + selinId + "}").andExpect(status().isBadRequest());
        send(post("/api/cases/{id}/assign", unassigned), "burak.demir", "{\"userId\":" + canId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo.username").value("can.arslan"))
                .andExpect(jsonPath("$.status").value("Assigned"));

        // Yeni atanan uzman kaydı görebilir, diğer uzman göremez
        send(get("/api/cases/{id}", unassigned), "can.arslan", null).andExpect(status().isOk());
        send(get("/api/cases/{id}", unassigned), "elif.kaya", null).andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_id = ?", String.class, unassigned)).contains("ASSIGN");
    }

    @Test
    void reassigningAnEscalatedCaseReopensItAndClosedCasesCannotBeAssigned() throws Exception {
        List<Long> escalated = jdbc.queryForList("SELECT id FROM exception_cases WHERE status = 'Escalated' ORDER BY id LIMIT 1", Long.class);
        assumeTrue(!escalated.isEmpty(), "seed verisinde eskale edilmiş kayıt yok");
        Long canId = userId("can.arslan");

        send(post("/api/cases/{id}/assign", escalated.getFirst()), "burak.demir", "{\"userId\":" + canId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("InReview"));

        Long closed = jdbc.queryForObject("SELECT id FROM exception_cases WHERE status = 'Closed' LIMIT 1", Long.class);
        send(post("/api/cases/{id}/assign", closed), "burak.demir", "{\"userId\":" + canId + "}").andExpect(status().isBadRequest());
    }

    @Test
    void usersEndpointListsSpecialistsForTeamLeadOnly() throws Exception {
        send(get("/api/users"), "burak.demir", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].role", not(hasItem("TeamLead"))));
        send(get("/api/users"), "elif.kaya", null).andExpect(status().isForbidden());
        send(get("/api/users"), "selin.aydin", null).andExpect(status().isForbidden());
    }

    @Test
    void detailTellsTheClientWhichActionsAreAvailable() throws Exception {
        Long caseId = inReviewCaseOf("elif.kaya", 6);
        send(get("/api/cases/{id}", caseId), "elif.kaya", null)
                .andExpect(jsonPath("$.allowedTransitions", hasItems("AwaitingInfo", "Rejected", "Escalated")))
                .andExpect(jsonPath("$.canProposeResolution").value(true))
                .andExpect(jsonPath("$.canAssign").value(false));
        send(get("/api/cases/{id}", caseId), "burak.demir", null)
                .andExpect(jsonPath("$.canProposeResolution").value(false))
                .andExpect(jsonPath("$.canAssign").value(true));
    }

    @Test
    void managerCannotUseWorkflowEndpoints() throws Exception {
        Long caseId = inReviewCaseOf("elif.kaya", 7);
        send(post("/api/cases/{id}/notes", caseId), "selin.aydin", "{\"text\":\"Not\"}").andExpect(status().isForbidden());
        send(get("/api/approvals"), "selin.aydin", null).andExpect(status().isForbidden());
    }

    @Test
    void invalidRequestBodiesAreRejected() throws Exception {
        Long caseId = inReviewCaseOf("elif.kaya", 8);
        send(post("/api/cases/{id}/notes", caseId), "elif.kaya", "{\"text\":\"\"}").andExpect(status().isBadRequest());
        send(post("/api/cases/{id}/transitions", caseId), "elif.kaya", "{}").andExpect(status().isBadRequest());
        send(post("/api/cases/{id}/transitions", caseId), "elif.kaya", "{\"toStatus\":\"Bilinmeyen\"}").andExpect(status().isBadRequest());
        send(post("/api/cases/{id}/resolution", caseId), "elif.kaya", "{\"text\":\"" + "x".repeat(2001) + "\"}").andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- SLA eskalasyonu

    @Test
    void escalationJobFlagsOverdueCasesWithoutChangingTheirStatus() {
        Long caseId = jdbc.queryForObject(
                "SELECT id FROM exception_cases WHERE status = 'InReview' AND sla_deadline < now() AND is_escalated ORDER BY id LIMIT 1", Long.class);
        jdbc.update("UPDATE exception_cases SET is_escalated = FALSE WHERE id = ?", caseId);

        int escalated = escalationService.escalateBreachedCases();

        assertThat(escalated).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_escalated FROM exception_cases WHERE id = ?", Boolean.class, caseId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM exception_cases WHERE id = ?", String.class, caseId)).isEqualTo("InReview");
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'ESCALATE' AND user_id IS NULL AND entity_id = " + caseId
                + " AND details->>'reason' = 'sla_breached' AND timestamp > now() - interval '1 minute'")).isEqualTo(1);

        // İkinci çalıştırma aynı kayıtları tekrar işaretlemez
        assertThat(escalationService.escalateBreachedCases()).isZero();
    }

    @Test
    void escalationJobIgnoresClosedAndNotYetOverdueCases() {
        Long closed = jdbc.queryForObject("SELECT id FROM exception_cases WHERE status = 'Closed' AND NOT is_escalated LIMIT 1", Long.class);
        Long notOverdue = jdbc.queryForObject(
                "SELECT id FROM exception_cases WHERE status NOT IN ('Closed','Rejected') AND sla_deadline > now() AND NOT is_escalated LIMIT 1", Long.class);

        escalationService.escalateBreachedCases();

        assertThat(jdbc.queryForObject("SELECT is_escalated FROM exception_cases WHERE id = ?", Boolean.class, closed)).isFalse();
        assertThat(jdbc.queryForObject("SELECT is_escalated FROM exception_cases WHERE id = ?", Boolean.class, notOverdue)).isFalse();
    }

    // ---------------------------------------------------------------- yardımcılar

    private ResultActions send(MockHttpServletRequestBuilder request, String username, String body) throws Exception {
        request.header("Authorization", bearer(username));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private String bearer(String username) throws Exception {
        String cached = tokens.get(username);
        if (cached != null) {
            return cached;
        }
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String header = "Bearer " + JsonPath.read(response, "$.token");
        tokens.put(username, header);
        return header;
    }

    private Long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    /** Her test farklı bir kayıtla çalışsın diye {@code offset} verilir. */
    private Long inReviewCaseOf(String username, int offset) {
        return jdbc.queryForObject("""
                SELECT c.id FROM exception_cases c JOIN users u ON u.id = c.assigned_to_user_id
                WHERE u.username = ? AND c.status = 'InReview' ORDER BY c.id LIMIT 1 OFFSET ?""", Long.class, username, offset);
    }

    private Long caseWithStatus(String status, int offset) {
        return jdbc.queryForObject("SELECT id FROM exception_cases WHERE status = ? ORDER BY id LIMIT 1 OFFSET ?", Long.class, status, offset);
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
