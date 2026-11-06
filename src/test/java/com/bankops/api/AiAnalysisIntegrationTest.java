package com.bankops.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bankops.TestcontainersConfiguration;
import com.bankops.application.ai.LlmClient;
import com.bankops.application.ai.ResolutionEmbedder;
import com.bankops.application.ai.TextEmbedder;
import com.bankops.application.repository.UserRepository;
import com.bankops.seed.SeedDataService;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Faz 4 kabul testi: benzerlik araması, LLM akışı, halüsinasyon kontrolü, fallback, önbellek, kişisel veri maskeleme,
 * geri bildirim, bilgi tabanı. Gerçek API çağrısı yapılmaz; embedding ve LLM sahte (fake) implementasyonlardır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AiAnalysisIntegrationTest.FakeAiConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiAnalysisIntegrationTest {

    private static final String PASSWORD = "test-only-password";
    private static final String INVENTED_CASE = "EXC-2026-99999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SeedDataService seedDataService;

    @Autowired
    private ResolutionEmbedder resolutionEmbedder;

    @Autowired
    private FakeAi fake;

    private final Map<String, String> tokens = new HashMap<>();

    @BeforeAll
    void seedAndEmbed() {
        if (userRepository.count() == 0) {
            seedDataService.seed(PASSWORD);
        }
        // Seed günün saatine göre dağıldığından, testlerin ayrı ayrı kullandığı kayıtlar burada garanti edilir
        ensureOpenCases("elif.kaya", "NAME_MISMATCH", 8);
        ensureOpenCases("elif.kaya", "IBAN_CHECKSUM", 1);
        ensureOpenCases("can.arslan", "NAME_MISMATCH", 1);
        ensureOpenCases("mert.sahin", null, 4);
        if (jdbc.queryForObject("SELECT count(*) FROM exception_cases WHERE error_code = 'SANCTION_HIT' AND status NOT IN ('Closed', 'Rejected')", Integer.class) == 0) {
            jdbc.update("UPDATE exception_cases SET error_code = 'SANCTION_HIT' WHERE id = (SELECT max(id) FROM exception_cases WHERE status = 'New')");
        }
        resolutionEmbedder.backfillMissing();
    }

    /** Kullanıcıya atanmış en az {@code needed} açık (İncelemede) kayıt olmasını sağlar; eksik varsa başka açık kayıtları ona atar. */
    private void ensureOpenCases(String username, String errorCode, int needed) {
        String codeFilter = errorCode == null ? "" : " AND error_code = '" + errorCode + "'";
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        int have = jdbc.queryForObject("SELECT count(*) FROM exception_cases WHERE assigned_to_user_id = " + userId
                + " AND status = 'InReview'" + codeFilter, Integer.class);
        int missing = needed - have;
        if (missing > 0) {
            jdbc.update("UPDATE exception_cases SET assigned_to_user_id = ?, status = 'InReview' WHERE id IN "
                    + "(SELECT id FROM exception_cases WHERE status IN ('New', 'Assigned', 'InReview')"
                    + " AND (assigned_to_user_id IS NULL OR assigned_to_user_id <> ?)" + codeFilter + " ORDER BY id LIMIT ?)",
                    userId, userId, missing);
        }
    }

    @BeforeEach
    void resetFake() {
        fake.reset();
    }

    // ---------------------------------------------------------------- analiz akışı

    @Test
    void analysisIsBuiltOnlyFromRetrievedVerifiedCasesAndHallucinatedIdsAreRemoved() throws Exception {
        Long caseId = openCaseOf("elif.kaya", "NAME_MISMATCH", 0);

        String body = send(post("/api/cases/{id}/ai-analysis", caseId), "elif.kaya")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.result.summary").isNotEmpty())
                .andExpect(jsonPath("$.references[*].caseNumber", not(hasItem(INVENTED_CASE))))
                .andExpect(jsonPath("$.result.referencedCaseIds", not(hasItem(INVENTED_CASE))))
                .andExpect(jsonPath("$.result.possibleCauses[0].supportingCaseIds", not(hasItem(INVENTED_CASE))))
                .andExpect(jsonPath("$.references[0].similarity").isNumber())
                .andExpect(jsonPath("$.references[0].matchReason").value(org.hamcrest.Matchers.startsWith("aynı hata kodu")))
                .andReturn().getResponse().getContentAsString();

        // Gösterilen her referans gerçekten doğrulanmış ve bilgi tabanındaki bir çözümdür, aynı hata koduna aittir
        List<String> referenced = JsonPath.read(body, "$.result.referencedCaseIds");
        assertThat(referenced).isNotEmpty();
        for (String caseNumber : referenced) {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT r.is_verified, r.include_in_knowledge_base, c.error_code
                    FROM resolutions r JOIN exception_cases c ON c.id = r.case_id WHERE c.case_number = ?""", caseNumber);
            assertThat(row.get("is_verified")).isEqualTo(true);
            assertThat(row.get("include_in_knowledge_base")).isEqualTo(true);
            assertThat(row.get("error_code")).isEqualTo("NAME_MISMATCH");
        }
        // Kayıt, audit ve veritabanına yazılan referanslar da temizdir
        assertThat(jdbc.queryForObject("SELECT referenced_case_ids::text FROM ai_analyses WHERE id = ?", String.class,
                ((Number) JsonPath.read(body, "$.id")).longValue())).doesNotContain(INVENTED_CASE);
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_type = 'ExceptionCase' AND entity_id = ?", String.class, caseId))
                .contains("AI_ANALYZE");
    }

    @Test
    void secondCallIsServedFromCacheUntilRefreshIsRequested() throws Exception {
        Long caseId = openCaseOf("elif.kaya", "NAME_MISMATCH", 1);

        send(post("/api/cases/{id}/ai-analysis", caseId), "elif.kaya").andExpect(jsonPath("$.cached").value(false));
        assertThat(fake.llmCalls).isEqualTo(1);

        send(post("/api/cases/{id}/ai-analysis", caseId), "elif.kaya").andExpect(jsonPath("$.cached").value(true));
        assertThat(fake.llmCalls).isEqualTo(1);

        // GET, sayfa açılışında hazır analizi döner
        send(get("/api/cases/{id}/ai-analysis", caseId), "elif.kaya")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true));

        send(post("/api/cases/{id}/ai-analysis?refresh=true", caseId), "elif.kaya").andExpect(jsonPath("$.cached").value(false));
        assertThat(fake.llmCalls).isEqualTo(2);
    }

    @Test
    void noAnalysisYetMeansNoContent() throws Exception {
        Long caseId = openCaseOf("elif.kaya", "IBAN_CHECKSUM", 0);
        send(get("/api/cases/{id}/ai-analysis", caseId), "elif.kaya").andExpect(status().isNoContent());
    }

    @Test
    void withoutTrustworthyHistoryTheModelIsNotCalledAndAFallbackMessageIsReturned() throws Exception {
        Long caseId = jdbc.queryForObject(
                "SELECT id FROM exception_cases WHERE error_code = 'SANCTION_HIT' AND status NOT IN ('Closed', 'Rejected') ORDER BY id LIMIT 1", Long.class);
        // Bu hata koduna ait bilgi tabanı çözümlerinin embedding'ini geçici olarak sil: kanıt kalmaz
        jdbc.update("UPDATE resolutions SET embedding = NULL WHERE case_id IN (SELECT id FROM exception_cases WHERE error_code = 'SANCTION_HIT')");
        try {
            send(post("/api/cases/{id}/ai-analysis", caseId), "burak.demir")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(false))
                    .andExpect(jsonPath("$.message").value("Güvenilir geçmiş kayıt bulunamadı. Bu kayıt için AI önerisi üretilmedi."))
                    .andExpect(jsonPath("$.result").doesNotExist())
                    .andExpect(jsonPath("$.references", hasSize(0)));
            assertThat(fake.llmCalls).isZero();
        } finally {
            resolutionEmbedder.backfillMissing();
        }
    }

    @Test
    void unverifiedAndExcludedResolutionsAreNeverUsedAsSources() throws Exception {
        Long caseId = openCaseOf("elif.kaya", "NAME_MISMATCH", 2);
        // Bilgi tabanına alınmamış bir çözüm embedding'i olsa bile aranmaz
        Long excludedCase = jdbc.queryForObject("""
                SELECT c.id FROM exception_cases c JOIN resolutions r ON r.case_id = c.id
                WHERE c.error_code = 'NAME_MISMATCH' AND r.is_verified AND r.include_in_knowledge_base LIMIT 1""", Long.class);
        String excludedNumber = jdbc.queryForObject("SELECT case_number FROM exception_cases WHERE id = ?", String.class, excludedCase);
        jdbc.update("UPDATE resolutions SET include_in_knowledge_base = FALSE WHERE case_id = ?", excludedCase);

        String body = send(post("/api/cases/{id}/ai-analysis?refresh=true", caseId), "elif.kaya")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(fake.lastPrompt).doesNotContain("[" + excludedNumber + "]");
        List<String> numbers = JsonPath.read(body, "$.references[*].caseNumber");
        assertThat(numbers).doesNotContain(excludedNumber);
    }

    @Test
    void personalDataNeverReachesTheEmbeddingServiceOrTheModel() throws Exception {
        Long caseId = openCaseOf("elif.kaya", "NAME_MISMATCH", 3);
        jdbc.update("""
                UPDATE payment_transactions SET sender_name = 'Ahmet Yılmaz', receiver_name = 'Gül Kimya A.Ş.',
                       description = 'Ahmet Yılmaz 12345678901 kimlikli, TR490001234567890123456721 hesaba, 0532 123 45 67 numarayla Gül Kimya A.Ş. için'
                WHERE id = (SELECT transaction_id FROM exception_cases WHERE id = ?)""", caseId);

        send(post("/api/cases/{id}/ai-analysis?refresh=true", caseId), "elif.kaya")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedFieldCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));

        List<String> everythingSent = new ArrayList<>(fake.embeddedQueries);
        everythingSent.add(fake.lastPrompt);
        for (String sent : everythingSent) {
            assertThat(sent).doesNotContain("Ahmet", "Yılmaz", "Gül Kimya", "12345678901", "TR4900", "0532");
        }
        assertThat(fake.lastPrompt).contains("[GÖNDEREN]", "[ALICI]", "[IBAN]", "[TCKN]", "[TELEFON]");
    }

    @Test
    void invalidModelOutputIsRetriedOnceThenFails() throws Exception {
        Long caseId = openCaseOf("elif.kaya", "NAME_MISMATCH", 4);

        // İlk yanıt bozuk, ikincisi geçerli: başarılı
        fake.replies.add("bu bir JSON değil");
        send(post("/api/cases/{id}/ai-analysis", caseId), "elif.kaya").andExpect(status().isOk());
        assertThat(fake.llmCalls).isEqualTo(2);

        // İki yanıt da bozuk: 502
        fake.reset();
        fake.replies.add("{bozuk");
        fake.replies.add("{\"summary\": \"\"}");
        send(post("/api/cases/{id}/ai-analysis?refresh=true", caseId), "elif.kaya").andExpect(status().isBadGateway());
    }

    @Test
    void unavailableProviderReturnsServiceUnavailableWithoutLeakingDetails() throws Exception {
        Long caseId = openCaseOf("elif.kaya", "NAME_MISMATCH", 5);
        fake.configured = false;
        send(post("/api/cases/{id}/ai-analysis?refresh=true", caseId), "elif.kaya").andExpect(status().isServiceUnavailable());
    }

    // ---------------------------------------------------------------- yetki ve geri bildirim

    @Test
    void specialistCannotAnalyzeSomeoneElsesCaseAndManagerCannotAnalyzeAtAll() throws Exception {
        Long othersCase = openCaseOf("can.arslan", "NAME_MISMATCH", 0);
        send(post("/api/cases/{id}/ai-analysis", othersCase), "elif.kaya").andExpect(status().isForbidden());
        send(post("/api/cases/{id}/ai-analysis", othersCase), "selin.aydin").andExpect(status().isForbidden());
        // Takım lideri her kayıt için analiz isteyebilir
        send(post("/api/cases/{id}/ai-analysis", othersCase), "burak.demir").andExpect(status().isOk());
    }

    @Test
    void feedbackIsStoredAndAudited() throws Exception {
        Long caseId = openCaseOf("elif.kaya", "NAME_MISMATCH", 6);
        long analysisId = ((Number) JsonPath.read(send(post("/api/cases/{id}/ai-analysis", caseId), "elif.kaya")
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        send(post("/api/ai-analyses/{id}/feedback", analysisId), "elif.kaya", "{\"feedback\":\"Useful\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value("Useful"));
        assertThat(jdbc.queryForObject("SELECT feedback FROM ai_analyses WHERE id = ?", String.class, analysisId)).isEqualTo("Useful");
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_type = 'AiAnalysis' AND entity_id = ?", String.class, analysisId))
                .contains("AI_FEEDBACK");

        send(post("/api/ai-analyses/{id}/feedback", analysisId), "elif.kaya", "{\"feedback\":\"Bilinmiyor\"}").andExpect(status().isBadRequest());
        // Başka uzmanın kaydındaki analize geri bildirim verilemez
        send(post("/api/ai-analyses/{id}/feedback", analysisId), "can.arslan", "{\"feedback\":\"NotUseful\"}").andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- bilgi tabanı büyümesi

    @Test
    void approvedResolutionIsEmbeddedAfterCommitAndAnApiFailureDoesNotBlockTheApproval() throws Exception {
        // 1) Normal durum: onay sonrası embedding yazılır
        Long first = inReviewCaseOf("mert.sahin", 0);
        proposeAndApprove(first, "mert.sahin", true);
        assertThat(embeddingPresent(first)).isTrue();

        // 2) Embedding servisi çökse bile onay başarılıdır; embedding eksik kalır ve sonradan doldurulur
        Long second = inReviewCaseOf("mert.sahin", 0);
        fake.failEmbedding = true;
        proposeAndApprove(second, "mert.sahin", true);
        fake.failEmbedding = false;
        assertThat(jdbc.queryForObject("SELECT status FROM exception_cases WHERE id = ?", String.class, second)).isEqualTo("Closed");
        assertThat(embeddingPresent(second)).isFalse();
        assertThat(resolutionEmbedder.backfillMissing()).isGreaterThanOrEqualTo(1);
        assertThat(embeddingPresent(second)).isTrue();

        // 3) Bilgi tabanına alınmayan çözüm embed edilmez
        Long third = inReviewCaseOf("mert.sahin", 0);
        proposeAndApprove(third, "mert.sahin", false);
        assertThat(embeddingPresent(third)).isFalse();
    }

    @Test
    void knowledgeBaseListsOnlyVerifiedResolutionsWithFiltersAndReferenceCounts() throws Exception {
        send(get("/api/knowledge-base?errorCode=NAME_MISMATCH&pageSize=5"), "elif.kaya")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[*].errorCode", not(hasItem("IBAN_CHECKSUM"))))
                .andExpect(jsonPath("$.items[0].approvedByName").isNotEmpty())
                .andExpect(jsonPath("$.items[0].aiReferenceCount").isNumber())
                .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThan(5)));

        send(get("/api/knowledge-base?search=hiç-bir-çözümde-geçmeyen-ifade"), "elif.kaya")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    // ---------------------------------------------------------------- yardımcılar

    private void proposeAndApprove(Long caseId, String specialist, boolean includeInKnowledgeBase) throws Exception {
        send(post("/api/cases/{id}/resolution", caseId), specialist, "{\"text\":\"Alıcı unvanı teyit edildi, talimat yeniden gönderildi.\"}")
                .andExpect(status().isOk());
        send(post("/api/approvals/{id}/approve", caseId), "burak.demir", "{\"includeInKnowledgeBase\":%s}".formatted(includeInKnowledgeBase))
                .andExpect(status().isOk());
    }

    private boolean embeddingPresent(Long caseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT embedding IS NOT NULL FROM resolutions WHERE case_id = ?", Boolean.class, caseId));
    }

    private Long openCaseOf(String username, String errorCode, int offset) {
        return jdbc.queryForObject("""
                SELECT c.id FROM exception_cases c JOIN users u ON u.id = c.assigned_to_user_id
                WHERE u.username = ? AND c.error_code = ? AND c.status IN ('InReview', 'Assigned', 'New', 'AwaitingInfo')
                ORDER BY c.id LIMIT 1 OFFSET ?""", Long.class, username, errorCode, offset);
    }

    private Long inReviewCaseOf(String username, int offset) {
        return jdbc.queryForObject("""
                SELECT c.id FROM exception_cases c JOIN users u ON u.id = c.assigned_to_user_id
                WHERE u.username = ? AND c.status = 'InReview' ORDER BY c.id DESC LIMIT 1 OFFSET ?""", Long.class, username, offset);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String username) throws Exception {
        return send(request, username, null);
    }

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

    // ---------------------------------------------------------------- sahte AI

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeAiConfig {

        /** Hem {@link TextEmbedder} hem {@link LlmClient} için gerçek istemcinin yerine geçer (@Primary). */
        @Bean
        @Primary
        FakeAi fakeAi() {
            return new FakeAi();
        }
    }

    /** Kelime torbası embedding'i ve komut verilebilen sahte LLM; ağa hiç çıkmaz. */
    static class FakeAi implements TextEmbedder, LlmClient {

        private static final int DIMENSIONS = 768;
        private static final Pattern CASE_NUMBER = Pattern.compile("\\[(EXC-\\d{4}-\\d+)]");

        boolean configured = true;
        boolean failEmbedding;
        int llmCalls;
        String lastPrompt = "";
        final List<String> embeddedQueries = new ArrayList<>();
        final Deque<String> replies = new ArrayDeque<>();

        void reset() {
            configured = true;
            failEmbedding = false;
            llmCalls = 0;
            lastPrompt = "";
            embeddedQueries.clear();
            replies.clear();
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public float[] embedQuery(String text) {
            embeddedQueries.add(text);
            return embed(text);
        }

        @Override
        public List<float[]> embedDocuments(List<String> texts) {
            if (failEmbedding) {
                throw new com.bankops.application.ai.AiUnavailableException("sahte embedding hatası");
            }
            return texts.stream().map(FakeAi::embed).toList();
        }

        @Override
        public Reply generateJson(String systemPrompt, String userPrompt) {
            llmCalls++;
            lastPrompt = userPrompt;
            if (!replies.isEmpty()) {
                return new Reply(replies.poll(), "fake-model");
            }
            // İstemdeki gerçek kayıt numaralarına bir de uydurma numara ekleyerek yanıt verir (halüsinasyon simülasyonu)
            List<String> real = new ArrayList<>();
            Matcher matcher = CASE_NUMBER.matcher(userPrompt);
            while (matcher.find()) {
                real.add(matcher.group(1));
            }
            String ids = String.join("\",\"", real) + "\",\"" + INVENTED_CASE;
            return new Reply("""
                    {"summary":"Sahte analiz özeti","possibleCauses":[{"cause":"Olası neden","supportingCaseIds":["%s"]}],
                     "recommendedSteps":["Teyit al"],"warnings":["Dikkat"],"referencedCaseIds":["%s"]}
                    """.formatted(ids, ids), "fake-model");
        }

        /** Aynı kelimeleri paylaşan metinler birbirine yakın vektör üretir. */
        private static float[] embed(String text) {
            float[] vector = new float[DIMENSIONS];
            for (String word : text.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
                if (!word.isBlank()) {
                    vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
                }
            }
            double norm = 0;
            for (float value : vector) {
                norm += value * value;
            }
            norm = Math.sqrt(norm);
            for (int i = 0; i < vector.length && norm > 0; i++) {
                vector[i] /= (float) norm;
            }
            return vector;
        }
    }
}
