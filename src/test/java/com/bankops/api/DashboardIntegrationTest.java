package com.bankops.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bankops.TestcontainersConfiguration;
import com.bankops.application.repository.UserRepository;
import com.bankops.domain.Priority;
import com.bankops.domain.SlaCalculator;
import com.bankops.seed.SeedDataService;
import com.jayway.jsonpath.JsonPath;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

/**
 * Faz 5 kabul testi: dashboard uç noktaları seed verisi üzerinde, gerçek PostgreSQL ile. Sayılar API'den okunur ve
 * ayrı, basit SQL sorgularıyla yeniden hesaplanan değerlerle karşılaştırılır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardIntegrationTest {

    private static final String PASSWORD = "test-only-password";
    private static final List<String> ENDPOINTS = List.of("kpis", "ai-impact", "error-types", "sla-trend", "aging",
            "transaction-types", "ai-quality", "team", "hourly-heatmap");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SeedDataService seedDataService;

    @Autowired
    private SlaCalculator slaCalculator;

    @BeforeAll
    void seedIfEmpty() {
        if (userRepository.count() == 0) {
            seedDataService.seed(PASSWORD);
        }
    }

    // ---------------------------------------------------------------- yetki

    @Test
    void managerAndTeamLeadCanReadEveryEndpoint() throws Exception {
        for (String user : List.of("selin.aydin", "burak.demir")) {
            String token = bearer(user);
            for (String endpoint : ENDPOINTS) {
                mockMvc.perform(get("/api/dashboard/" + endpoint).header("Authorization", token))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test
    void specialistAndAnonymousCannotSeeTheDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/kpis").header("Authorization", bearer("elif.kaya")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/kpis")).andExpect(status().isUnauthorized());
    }

    @Test
    void periodOtherThan7_30_90IsRejected() throws Exception {
        mockMvc.perform(get("/api/dashboard/kpis").param("days", "15").header("Authorization", bearer("selin.aydin")))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ KPI

    @Test
    void kpisMatchIndependentCounts() throws Exception {
        String json = read("kpis", "selin.aydin", 30);

        assertThat(count("created_at >= now() - interval '30 days'")).isEqualTo(longAt(json, "$.totalCases"));
        assertThat(count("status NOT IN ('Closed','Rejected')")).isEqualTo(longAt(json, "$.openCases"));
        assertThat(count("status IN ('New','Assigned')")).isEqualTo(longAt(json, "$.awaitingReview"));
        assertThat(count("status NOT IN ('Closed','Rejected') AND priority IN ('Critical','High')"))
                .isEqualTo(longAt(json, "$.highPriorityOpen"));
        assertThat(count("status NOT IN ('Closed','Rejected') AND sla_deadline < now()"))
                .isEqualTo(longAt(json, "$.slaBreached"));

        // Çözüm süresi çalışma süresidir (mesai dakikası): beklenen değer SlaCalculator ile ayrıca hesaplanır
        double expectedMinutes = jdbc.query("""
                        SELECT priority, created_at, closed_at FROM exception_cases
                        WHERE status = 'Closed' AND closed_at >= now() - interval '30 days'""",
                (row, index) -> slaCalculator.workingTimeBetween(Priority.valueOf(row.getString("priority")),
                        row.getTimestamp("created_at").toInstant(), row.getTimestamp("closed_at").toInstant()).toSeconds() / 60.0)
                .stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        assertThat(((Number) JsonPath.read(json, "$.avgResolutionMinutes")).doubleValue())
                .isCloseTo(expectedMinutes, within(0.01));
    }

    @Test
    void longerPeriodsNeverShowFewerCases() throws Exception {
        long week = longAt(read("kpis", "selin.aydin", 7), "$.totalCases");
        long month = longAt(read("kpis", "selin.aydin", 30), "$.totalCases");
        long quarter = longAt(read("kpis", "selin.aydin", 90), "$.totalCases");
        assertThat(week).isLessThanOrEqualTo(month);
        assertThat(month).isLessThanOrEqualTo(quarter);
    }

    // ------------------------------------------------------------ AI etkisi

    @Test
    void aiImpactAcceptanceIsUsefulOverRated() throws Exception {
        String json = read("ai-impact", "burak.demir", 90);

        long useful = longAt(json, "$.usefulCount");
        long rated = longAt(json, "$.ratedCount");
        assertThat(rated).isPositive();
        assertThat(((Number) JsonPath.read(json, "$.acceptanceRate")).doubleValue())
                .isCloseTo(useful / (double) rated, within(1e-9));

        long closedInPeriod = jdbc.queryForObject("""
                SELECT count(*) FROM exception_cases
                WHERE status = 'Closed' AND closed_at >= now() - interval '90 days'""", Long.class);
        assertThat(longAt(json, "$.withAiCases") + longAt(json, "$.withoutAiCases")).isEqualTo(closedInPeriod);

        // Seed'in tasarım niyeti: AI destekli kayıtlar daha kısa sürede (çalışma süresiyle) çözülür
        double withAi = ((Number) JsonPath.read(json, "$.withAiMinutes")).doubleValue();
        double withoutAi = ((Number) JsonPath.read(json, "$.withoutAiMinutes")).doubleValue();
        assertThat(withAi).isLessThan(withoutAi);
    }

    // -------------------------------------------------- dağılım panelleri

    @Test
    void errorTypesAndTransactionTypesAddUpToTheTotal() throws Exception {
        long total = longAt(read("kpis", "selin.aydin", 30), "$.totalCases");

        List<Integer> errorCounts = JsonPath.read(read("error-types", "selin.aydin", 30), "$[*].count");
        assertThat(errorCounts.stream().mapToLong(Integer::longValue).sum()).isEqualTo(total);

        String types = read("transaction-types", "selin.aydin", 30);
        List<Integer> typeCounts = JsonPath.read(types, "$[*].count");
        assertThat(typeCounts.stream().mapToLong(Integer::longValue).sum()).isEqualTo(total);
        List<Number> shares = JsonPath.read(types, "$[*].share");
        assertThat(shares.stream().mapToDouble(Number::doubleValue).sum()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void agingBucketsCoverEveryOpenCase() throws Exception {
        String json = mockMvc.perform(get("/api/dashboard/aging").header("Authorization", bearer("selin.aydin")))
                .andReturn().getResponse().getContentAsString();

        List<String> buckets = JsonPath.read(json, "$[*].bucket");
        assertThat(buckets).containsExactly("UNDER_1H", "H1_TO_4", "H4_TO_8", "H8_TO_24", "D1_TO_3", "OVER_3D");
        List<Integer> counts = JsonPath.read(json, "$[*].count");
        assertThat(counts.stream().mapToLong(Integer::longValue).sum())
                .isEqualTo(count("status NOT IN ('Closed','Rejected')"));
    }

    // ------------------------------------------------------------ SLA eğilimi

    @Test
    void slaTrendHasFourteenBusinessDaysAndConsistentRates() throws Exception {
        String json = mockMvc.perform(get("/api/dashboard/sla-trend").header("Authorization", bearer("selin.aydin")))
                .andReturn().getResponse().getContentAsString();

        assertThat(((Number) JsonPath.read(json, "$.targetRate")).doubleValue()).isEqualTo(0.08);
        List<String> days = JsonPath.read(json, "$.days[*].day");
        assertThat(days).hasSize(14).isSorted();
        for (String day : days) {
            DayOfWeek weekday = LocalDate.parse(day).getDayOfWeek();
            assertThat(weekday).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        }
        List<Map<String, Object>> points = JsonPath.read(json, "$.days");
        for (Map<String, Object> point : points) {
            long total = ((Number) point.get("total")).longValue();
            long breached = ((Number) point.get("breached")).longValue();
            assertThat(breached).isLessThanOrEqualTo(total);
            if (total == 0) {
                assertThat(point.get("breachRate")).isNull();
            } else {
                assertThat(((Number) point.get("breachRate")).doubleValue())
                        .isCloseTo(breached / (double) total, within(1e-9));
            }
        }
    }

    // ----------------------------------------------- AI kalitesi, ekip, ısı

    @Test
    void aiQualityStatusFollowsAcceptanceRateAndCountsKnowledgeBase() throws Exception {
        String json = read("ai-quality", "selin.aydin", 90);

        List<Map<String, Object>> rows = JsonPath.read(json, "$");
        assertThat(rows).hasSize(10);
        long verified = 0;
        for (Map<String, Object> row : rows) {
            verified += ((Number) row.get("verifiedResolutions")).longValue();
            Object rate = row.get("acceptanceRate");
            String expected = rate == null ? "NoData"
                    : ((Number) rate).doubleValue() >= 0.75 ? "Strong"
                    : ((Number) rate).doubleValue() >= 0.50 ? "Improving" : "WeakKnowledgeBase";
            assertThat(row.get("status")).isEqualTo(expected);
        }
        long knowledgeBase = jdbc.queryForObject("""
                SELECT count(*) FROM resolutions WHERE is_verified AND include_in_knowledge_base""", Long.class);
        assertThat(verified).isEqualTo(knowledgeBase);
    }

    @Test
    void teamListsEverySpecialistWithPlausibleNumbers() throws Exception {
        String json = read("team", "selin.aydin", 30);

        List<String> names = JsonPath.read(json, "$[*].fullName");
        assertThat(names).containsExactlyInAnyOrder("Elif Kaya", "Can Arslan", "Zeynep Koç", "Mert Şahin", "Ayşe Yıldız");
        List<Integer> open = JsonPath.read(json, "$[*].openCases");
        assertThat(open.stream().mapToLong(Integer::longValue).sum())
                .isEqualTo(count("status NOT IN ('Closed','Rejected') AND assigned_to_user_id IS NOT NULL"));
    }

    @Test
    void heatmapHasOneCellPerWeekdayAndHour() throws Exception {
        String json = mockMvc.perform(get("/api/dashboard/hourly-heatmap").header("Authorization", bearer("selin.aydin")))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> cells = JsonPath.read(json, "$");
        assertThat(cells).hasSize(5 * 10);
        assertThat(cells).allSatisfy(cell -> {
            assertThat(((Number) cell.get("weekday")).intValue()).isBetween(1, 5);
            assertThat(((Number) cell.get("hour")).intValue()).isBetween(8, 17);
            assertThat(((Number) cell.get("averageCases")).doubleValue()).isNotNegative();
        });
    }

    // ---------------------------------------------------------------- yardımcılar

    private long count(String where) {
        return jdbc.queryForObject("SELECT count(*) FROM exception_cases WHERE " + where, Long.class);
    }

    private static long longAt(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private String read(String endpoint, String user, int days) throws Exception {
        return mockMvc.perform(get("/api/dashboard/" + endpoint).param("days", String.valueOf(days))
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String bearer(String username) throws Exception {
        String body = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD);
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.token");
    }
}
