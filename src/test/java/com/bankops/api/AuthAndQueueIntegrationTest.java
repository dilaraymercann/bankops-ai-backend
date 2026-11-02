package com.bankops.api;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.bankops.TestcontainersConfiguration;
import com.bankops.application.repository.UserRepository;
import com.bankops.seed.SeedDataService;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Faz 2 kabul testi: giriş, rol bazlı yetki, kuyruk filtreleri ve kayıt detayı (gerçek PostgreSQL + seed verisi). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthAndQueueIntegrationTest {

    private static final String PASSWORD = "test-only-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SeedDataService seedDataService;

    @BeforeAll
    void seedIfEmpty() {
        if (userRepository.count() == 0) {
            seedDataService.seed(PASSWORD);
        }
    }

    // ---------------------------------------------------------------- giriş

    @Test
    void loginReturnsTokenAndUser() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("elif.kaya", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.fullName").value("Elif Kaya"))
                .andExpect(jsonPath("$.user.role").value("Specialist"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameAnswer() throws Exception {
        String wrongPassword = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("elif.kaya", "wrong")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("nobody", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(wrongPassword, "$.detail")).isEqualTo(JsonPath.<String>read(unknownUser, "$.detail"));
    }

    @Test
    void blankCredentialsAreRejectedWithValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meReturnsTheLoggedInUser() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer("burak.demir")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Burak Demir"))
                .andExpect(jsonPath("$.role").value("TeamLead"));
    }

    @Test
    void requestsWithoutOrWithTamperedTokenAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/cases")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/cases").header("Authorization", bearer("elif.kaya") + "x"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- yetki

    @Test
    void specialistSeesOnlyOwnOpenCasesSortedBySlaDeadline() throws Exception {
        String body = mockMvc.perform(get("/api/cases").param("pageSize", "100").header("Authorization", bearer("elif.kaya")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems", greaterThan(0)))
                .andExpect(jsonPath("$.items[*].assignedToName", everyItem(is("Elif Kaya"))))
                .andExpect(jsonPath("$.items[*].status", everyItem(not(is("Closed")))))
                .andReturn().getResponse().getContentAsString();

        List<String> deadlines = JsonPath.read(body, "$.items[*].slaDeadline");
        List<Instant> parsed = deadlines.stream().map(Instant::parse).toList();
        assertThat(parsed).isSorted();
    }

    @Test
    void specialistCannotOpenSomeoneElsesCase() throws Exception {
        Long othersCase = jdbc.queryForObject("""
                SELECT c.id FROM exception_cases c JOIN users u ON u.id = c.assigned_to_user_id
                WHERE u.username = 'can.arslan' LIMIT 1""", Long.class);
        mockMvc.perform(get("/api/cases/{id}", othersCase).header("Authorization", bearer("elif.kaya")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void teamLeadSeesAllOpenCasesAndCanFilterBySpecialist() throws Exception {
        mockMvc.perform(get("/api/cases").param("pageSize", "5").header("Authorization", bearer("burak.demir")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(140))
                .andExpect(jsonPath("$.items", hasSize(5)));

        Long canId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'can.arslan'", Long.class);
        mockMvc.perform(get("/api/cases").param("assignedTo", String.valueOf(canId)).param("pageSize", "100")
                        .header("Authorization", bearer("burak.demir")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].assignedToName", everyItem(is("Can Arslan"))));
    }

    @Test
    void managerCannotUseTheQueue() throws Exception {
        mockMvc.perform(get("/api/cases").header("Authorization", bearer("selin.aydin")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/cases/summary").header("Authorization", bearer("selin.aydin")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- filtreler

    @Test
    void filtersByPriorityErrorCodeAndTransactionType() throws Exception {
        mockMvc.perform(get("/api/cases").param("priority", "High").param("errorCode", "NAME_MISMATCH")
                        .param("transactionType", "Fast").param("pageSize", "100").header("Authorization", bearer("burak.demir")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].priority", everyItem(is("High"))))
                .andExpect(jsonPath("$.items[*].errorCode", everyItem(is("NAME_MISMATCH"))))
                .andExpect(jsonPath("$.items[*].transactionType", everyItem(is("Fast"))));
    }

    @Test
    void statusFilterCanShowClosedCases() throws Exception {
        mockMvc.perform(get("/api/cases").param("status", "Closed").header("Authorization", bearer("burak.demir")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems", greaterThan(2000)))
                .andExpect(jsonPath("$.items[*].sla", everyItem(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void slaStateFilterReturnsOnlyBreachedCases() throws Exception {
        mockMvc.perform(get("/api/cases").param("slaState", "Breached").param("pageSize", "100")
                        .header("Authorization", bearer("burak.demir")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems", greaterThan(0)))
                .andExpect(jsonPath("$.items[*].sla.state", everyItem(is("Breached"))))
                .andExpect(jsonPath("$.items[*].sla.remainingMinutes", everyItem(org.hamcrest.Matchers.lessThanOrEqualTo(0))));
    }

    @Test
    void searchFindsByCaseNumberAndByFormattedAmount() throws Exception {
        String firstCaseNumber = jdbc.queryForObject(
                "SELECT case_number FROM exception_cases WHERE status = 'InReview' ORDER BY id LIMIT 1", String.class);
        mockMvc.perform(get("/api/cases").param("search", firstCaseNumber.toLowerCase()).header("Authorization", bearer("burak.demir")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].caseNumber").value(firstCaseNumber));

        // "48.750,00" gibi yazılan tutar, 48750.00 olarak aranır
        java.math.BigDecimal amount = jdbc.queryForObject("""
                SELECT t.amount FROM exception_cases c JOIN payment_transactions t ON t.id = c.transaction_id
                WHERE c.status = 'InReview' LIMIT 1""", java.math.BigDecimal.class);
        String turkishFormat = String.format(java.util.Locale.forLanguageTag("tr"), "%,.2f", amount);
        mockMvc.perform(get("/api/cases").param("search", turkishFormat).header("Authorization", bearer("burak.demir")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems", greaterThan(0)));
    }

    @Test
    void paginationAndInvalidSortAreHandled() throws Exception {
        mockMvc.perform(get("/api/cases").param("page", "1").param("pageSize", "10").header("Authorization", bearer("burak.demir")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.totalPages").value(14));
        mockMvc.perform(get("/api/cases").param("pageSize", "5000").header("Authorization", bearer("burak.demir")))
                .andExpect(jsonPath("$.pageSize").value(100));
        mockMvc.perform(get("/api/cases").param("sort", "passwordHash,asc").header("Authorization", bearer("burak.demir")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/cases").param("priority", "Urgent").header("Authorization", bearer("burak.demir")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryMatchesTheQueue() throws Exception {
        mockMvc.perform(get("/api/cases/summary").header("Authorization", bearer("burak.demir")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(140))
                .andExpect(jsonPath("$.slaBreached", greaterThan(0)))
                .andExpect(jsonPath("$.slaApproaching", greaterThan(0)));

        // Uzmanın kendi kuyruğunda da sarı satırlar var
        mockMvc.perform(get("/api/cases/summary").header("Authorization", bearer("elif.kaya")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaApproaching", greaterThan(0)));

        String breached = mockMvc.perform(get("/api/cases/summary").header("Authorization", bearer("burak.demir")))
                .andReturn().getResponse().getContentAsString();
        int expectedBreached = JsonPath.read(breached, "$.slaBreached");
        mockMvc.perform(get("/api/cases").param("slaState", "Breached").header("Authorization", bearer("burak.demir")))
                .andExpect(jsonPath("$.totalItems").value(expectedBreached));
    }

    // ---------------------------------------------------------------- detay

    @Test
    void detailShowsMaskedPersonalDataAndTimeline() throws Exception {
        Long demoCaseId = jdbc.queryForObject("""
                SELECT c.id FROM exception_cases c JOIN users u ON u.id = c.assigned_to_user_id
                WHERE u.username = 'elif.kaya' AND c.error_code = 'NAME_MISMATCH' AND c.status = 'InReview'
                  AND c.sla_deadline < now() ORDER BY c.created_at LIMIT 1""", Long.class);

        String body = mockMvc.perform(get("/api/cases/{id}", demoCaseId).header("Authorization", bearer("elif.kaya")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo.fullName").value("Elif Kaya"))
                .andExpect(jsonPath("$.escalated").value(true))
                .andExpect(jsonPath("$.sla.state").value("Breached"))
                .andExpect(jsonPath("$.transaction.receiverIban", containsString("****")))
                .andExpect(jsonPath("$.transaction.senderIban", containsString("****")))
                .andExpect(jsonPath("$.history[?(@.type=='ESCALATION')].escalatedToName").value(org.hamcrest.Matchers.hasItem("Burak Demir")))
                .andReturn().getResponse().getContentAsString();

        // Ham IBAN ve ham kişi adı yanıtta hiç bulunmamalı
        String rawReceiverIban = jdbc.queryForObject(
                "SELECT t.receiver_iban FROM payment_transactions t JOIN exception_cases c ON c.transaction_id = t.id WHERE c.id = ?",
                String.class, demoCaseId);
        assertThat(body).doesNotContain(rawReceiverIban);

        List<String> times = JsonPath.read(body, "$.history[*].at");
        assertThat(times.stream().map(Instant::parse).toList()).isSorted();
    }

    @Test
    void unknownCaseReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/cases/{id}", 999999999L).header("Authorization", bearer("burak.demir")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---------------------------------------------------------------- yardımcılar

    private String credentials(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
    }

    private String bearer(String username) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.token");
    }
}
