package com.bankops.application.dashboard;

import com.bankops.application.dashboard.DashboardViews.AgingBucket;
import com.bankops.application.dashboard.DashboardViews.AiImpact;
import com.bankops.application.dashboard.DashboardViews.AiQualityRow;
import com.bankops.application.dashboard.DashboardViews.AiQualityStatus;
import com.bankops.application.dashboard.DashboardViews.ErrorTypeCount;
import com.bankops.application.dashboard.DashboardViews.HeatmapCell;
import com.bankops.application.dashboard.DashboardViews.Kpis;
import com.bankops.application.dashboard.DashboardViews.SlaTrend;
import com.bankops.application.dashboard.DashboardViews.SlaTrendPoint;
import com.bankops.application.dashboard.DashboardViews.TeamMember;
import com.bankops.application.dashboard.DashboardViews.TransactionTypeShare;
import com.bankops.domain.ErrorCode;
import com.bankops.domain.Priority;
import com.bankops.domain.SlaCalculator;
import com.bankops.domain.TransactionType;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Yönetici dashboard'unun hesapları (CLAUDE.md §12.5). Sorgular birleştirme ve toplama içerdiğinden JPA yerine
 * okunabilir native SQL'dir. "Şimdi" her zaman enjekte edilen {@link Clock}'tan gelir (testte sabitlenebilir).
 *
 * <p>Tanımlar:
 * <ul>
 *   <li><b>Açık kayıt</b>: Kapatıldı ya da Reddedildi dışındaki her durum.</li>
 *   <li><b>Çözüm süresi</b>: Kapatıldı durumundaki kaydın <i>çalışma süresi</i> ({@link SlaCalculator#workingTimeBetween}:
 *       yalnızca mesai dakikaları, Critical için gerçek süre). SLA ile aynı tanımdır; gece ve hafta sonu sayılmaz.</li>
 *   <li><b>SLA ihlali</b>: bitmiş kayıtta {@code closed_at > sla_deadline}; açık kayıtta {@code sla_deadline} geçmiş olması.</li>
 *   <li><b>Kabul oranı</b>: "Faydalı" oyu / oy verilmiş analizler (oy verilmeyenler paydaya girmez).</li>
 * </ul>
 */
@Service
public class DashboardService {

    /** Rehberdeki (§12.5) günlük SLA ihlal hedefi: %8. */
    static final double SLA_BREACH_TARGET = 0.08;

    private static final Set<Integer> ALLOWED_PERIODS = Set.of(7, 30, 90);
    private static final int DEFAULT_PERIOD = 30;
    private static final int TREND_BUSINESS_DAYS = 14;
    private static final int HEATMAP_WEEKS = 4;
    private static final int HEATMAP_FIRST_HOUR = 8;
    private static final int HEATMAP_LAST_HOUR = 17;

    private static final List<String> AGING_BUCKETS =
            List.of("UNDER_1H", "H1_TO_4", "H4_TO_8", "H8_TO_24", "D1_TO_3", "OVER_3D");

    private static final String OPEN = "status NOT IN ('Closed', 'Rejected')";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId zone;
    private final SlaCalculator slaCalculator;

    public DashboardService(JdbcTemplate jdbc, Clock clock, SlaCalculator slaCalculator,
                            @Value("${bankops.sla.time-zone}") ZoneId zone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.slaCalculator = slaCalculator;
        this.zone = zone;
    }

    // ------------------------------------------------------------------ KPI

    public Kpis kpis(Integer days) {
        int period = period(days);
        Instant now = clock.instant();
        Instant since = now.minus(Duration.ofDays(period));

        // Bir sorguda hem bu dönem hem bir önceki dönem; ikisi de çalışma süresiyle (mesai dakikası) ölçülür.
        List<ClosedCase> closed = closedCases(since.minus(Duration.ofDays(period)), now);
        Double currentAverage = averageMinutes(closed, c -> !c.closedAt().isBefore(since));
        Double previousAverage = averageMinutes(closed, c -> c.closedAt().isBefore(since));

        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE created_at >= ?)                                        AS total,
                       count(*) FILTER (WHERE %s)                                                     AS open,
                       count(*) FILTER (WHERE status IN ('New', 'Assigned'))                          AS awaiting,
                       count(*) FILTER (WHERE %s AND priority IN ('Critical', 'High'))                AS high,
                       count(*) FILTER (WHERE %s AND sla_deadline < ?)                                AS breached
                FROM exception_cases
                """.formatted(OPEN, OPEN, OPEN),
                (row, index) -> new Kpis(period, row.getLong("total"), row.getLong("open"), row.getLong("awaiting"),
                        row.getLong("high"), row.getLong("breached"), currentAverage, previousAverage),
                ts(since), ts(now));
    }

    // ------------------------------------------------------------ AI etkisi

    public AiImpact aiImpact(Integer days) {
        int period = period(days);
        Instant now = clock.instant();
        Instant since = now.minus(Duration.ofDays(period));

        List<ClosedCase> closed = closedCases(since, now);
        Average withAi = new Average(averageMinutes(closed, ClosedCase::usedAi), closed.stream().filter(ClosedCase::usedAi).count());
        Average withoutAi = new Average(averageMinutes(closed, c -> !c.usedAi()), closed.stream().filter(c -> !c.usedAi()).count());

        return jdbc.queryForObject("""
                SELECT count(*)                                         AS analyses,
                       count(*) FILTER (WHERE feedback = 'Useful')      AS useful,
                       count(*) FILTER (WHERE feedback IS NOT NULL)     AS rated
                FROM ai_analyses
                WHERE created_at >= ? AND created_at < ?
                """,
                (row, index) -> new AiImpact(period, withAi.minutes(), withoutAi.minutes(), withAi.cases(),
                        withoutAi.cases(), row.getLong("analyses"), row.getLong("useful"), row.getLong("rated"),
                        ratio(row.getLong("useful"), row.getLong("rated"))),
                ts(since), ts(now));
    }

    private record Average(Double minutes, long cases) {
    }

    /** Kapatılmış bir kaydın çözüm süresi (çalışma dakikası). Süreyi SQL değil {@link SlaCalculator} hesaplar. */
    private record ClosedCase(Long assignedToUserId, boolean usedAi, Instant closedAt, double workingMinutes) {
    }

    /** {@code [from, to)} aralığında kapanan (reddedilmeyen) kayıtlar. Binlerce satırı geçmez; çalışma süresi Java'da hesaplanır. */
    private List<ClosedCase> closedCases(Instant from, Instant to) {
        return jdbc.query("""
                SELECT c.assigned_to_user_id, c.priority, c.created_at, c.closed_at,
                       EXISTS (SELECT 1 FROM ai_analyses a WHERE a.case_id = c.id) AS used_ai
                FROM exception_cases c
                WHERE c.status = 'Closed' AND c.closed_at >= ? AND c.closed_at < ?
                """,
                (row, index) -> {
                    Instant created = row.getTimestamp("created_at").toInstant();
                    Instant closedAt = row.getTimestamp("closed_at").toInstant();
                    Duration working = slaCalculator.workingTimeBetween(Priority.valueOf(row.getString("priority")), created, closedAt);
                    return new ClosedCase(row.getObject("assigned_to_user_id", Long.class), row.getBoolean("used_ai"),
                            closedAt, working.toSeconds() / 60.0);
                },
                ts(from), ts(to));
    }

    /** Seçilen kayıtların ortalama çalışma süresi (dakika); seçilen kayıt yoksa {@code null}. */
    private static Double averageMinutes(Collection<ClosedCase> cases, Predicate<ClosedCase> filter) {
        OptionalDouble average = cases.stream().filter(filter).mapToDouble(ClosedCase::workingMinutes).average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    // ------------------------------------------------------- hata türleri

    public List<ErrorTypeCount> errorTypes(Integer days) {
        Instant since = clock.instant().minus(Duration.ofDays(period(days)));
        return jdbc.query("""
                SELECT error_code, count(*) AS n FROM exception_cases
                WHERE created_at >= ? GROUP BY error_code ORDER BY n DESC, error_code
                """,
                (row, index) -> new ErrorTypeCount(ErrorCode.valueOf(row.getString("error_code")), row.getLong("n")),
                ts(since));
    }

    // ------------------------------------------------------- SLA eğilimi

    /** Son 14 iş günü (hafta sonları atlanır, bugün dahil). Kayıt açılmayan günün oranı {@code null}'dır. */
    public SlaTrend slaTrend() {
        Instant now = clock.instant();
        Date today = Date.valueOf(LocalDate.now(clock.withZone(zone)));

        List<SlaTrendPoint> points = jdbc.query("""
                WITH days AS (
                    SELECT (CAST(? AS date) - g) AS day
                    FROM generate_series(0, 27) AS g
                    WHERE extract(isodow FROM (CAST(? AS date) - g)) < 6
                    ORDER BY g
                    LIMIT %d)
                SELECT days.day,
                       count(c.id) AS total,
                       count(c.id) FILTER (WHERE (c.closed_at IS NOT NULL AND c.closed_at > c.sla_deadline)
                                              OR (c.closed_at IS NULL AND c.sla_deadline < ?)) AS breached
                FROM days
                LEFT JOIN exception_cases c ON CAST(c.created_at AT TIME ZONE ? AS date) = days.day
                GROUP BY days.day
                ORDER BY days.day
                """.formatted(TREND_BUSINESS_DAYS),
                (row, index) -> {
                    long total = row.getLong("total");
                    long breached = row.getLong("breached");
                    return new SlaTrendPoint(row.getDate("day").toLocalDate(), total, breached, ratio(breached, total));
                },
                today, today, ts(now), zone.getId());
        return new SlaTrend(SLA_BREACH_TARGET, points);
    }

    // ---------------------------------------------------------- yaşlanma

    /** Açık kayıtların yaşa göre dağılımı (dönemden bağımsız, o anki durum). Boş kovalar 0 olarak döner. */
    public List<AgingBucket> aging() {
        Map<String, Long> counts = new HashMap<>();
        jdbc.query("""
                SELECT CASE WHEN age < 3600   THEN 'UNDER_1H'
                            WHEN age < 14400  THEN 'H1_TO_4'
                            WHEN age < 28800  THEN 'H4_TO_8'
                            WHEN age < 86400  THEN 'H8_TO_24'
                            WHEN age < 259200 THEN 'D1_TO_3'
                            ELSE 'OVER_3D' END AS bucket,
                       count(*) AS n
                FROM (SELECT extract(epoch FROM (? - created_at)) AS age
                      FROM exception_cases WHERE %s) open_cases
                GROUP BY 1
                """.formatted(OPEN),
                row -> {
                    counts.put(row.getString("bucket"), row.getLong("n"));
                },
                ts(clock.instant()));
        return AGING_BUCKETS.stream().map(bucket -> new AgingBucket(bucket, counts.getOrDefault(bucket, 0L))).toList();
    }

    // -------------------------------------------------------- işlem tipleri

    public List<TransactionTypeShare> transactionTypes(Integer days) {
        Instant since = clock.instant().minus(Duration.ofDays(period(days)));
        Map<TransactionType, Long> counts = new EnumMap<>(TransactionType.class);
        jdbc.query("""
                SELECT t.type, count(*) AS n
                FROM exception_cases c JOIN payment_transactions t ON t.id = c.transaction_id
                WHERE c.created_at >= ? GROUP BY t.type
                """,
                row -> {
                    counts.put(TransactionType.valueOf(row.getString("type")), row.getLong("n"));
                },
                ts(since));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<TransactionType, Long>comparingByValue().reversed())
                .map(entry -> new TransactionTypeShare(entry.getKey(), entry.getValue(),
                        total == 0 ? 0 : entry.getValue() / (double) total))
                .toList();
    }

    // ------------------------------------------------- hata türüne göre AI

    /** Hata türü başına AI analizi, kabul oranı ve (dönemden bağımsız) bilgi tabanındaki doğrulanmış çözüm sayısı. */
    public List<AiQualityRow> aiQuality(Integer days) {
        Instant since = clock.instant().minus(Duration.ofDays(period(days)));

        Map<String, Long> verified = new HashMap<>();
        jdbc.query("""
                SELECT c.error_code, count(*) AS n
                FROM resolutions r JOIN exception_cases c ON c.id = r.case_id
                WHERE r.is_verified = TRUE AND r.include_in_knowledge_base = TRUE
                GROUP BY c.error_code
                """,
                row -> {
                    verified.put(row.getString("error_code"), row.getLong("n"));
                });

        return jdbc.query("""
                SELECT c.error_code,
                       count(a.id)                                    AS analyses,
                       count(a.id) FILTER (WHERE a.feedback = 'Useful')     AS useful,
                       count(a.id) FILTER (WHERE a.feedback IS NOT NULL)    AS rated
                FROM exception_cases c
                LEFT JOIN ai_analyses a ON a.case_id = c.id AND a.created_at >= ?
                GROUP BY c.error_code
                ORDER BY analyses DESC, c.error_code
                """,
                (row, index) -> {
                    String code = row.getString("error_code");
                    Double acceptance = ratio(row.getLong("useful"), row.getLong("rated"));
                    return new AiQualityRow(ErrorCode.valueOf(code), row.getLong("analyses"), acceptance,
                            verified.getOrDefault(code, 0L), qualityStatus(acceptance));
                },
                ts(since));
    }

    /** Kabul oranı ≥ %75 güçlü, %50–74 gelişiyor, altı bilgi tabanı zayıf; oy yoksa veri yok. */
    static AiQualityStatus qualityStatus(Double acceptanceRate) {
        if (acceptanceRate == null) {
            return AiQualityStatus.NoData;
        }
        if (acceptanceRate >= 0.75) {
            return AiQualityStatus.Strong;
        }
        return acceptanceRate >= 0.50 ? AiQualityStatus.Improving : AiQualityStatus.WeakKnowledgeBase;
    }

    // ---------------------------------------------------------------- ekip

    /** Uzman bazında iş yükü. Açık kayıt, bugün kapanan ve SLA aşan o anki durumdur; süre ve AI oranı döneme aittir. */
    public List<TeamMember> team(Integer days) {
        Instant now = clock.instant();
        Instant since = now.minus(Duration.ofDays(period(days)));
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();

        // Ortalama çözüm süresi çalışma süresiyle (mesai dakikası) ölçülür; uzman başına Java'da toplanır.
        Map<Long, List<ClosedCase>> closedByUser = closedCases(since, now).stream()
                .filter(c -> c.assignedToUserId() != null)
                .collect(Collectors.groupingBy(ClosedCase::assignedToUserId));

        return jdbc.query("""
                SELECT u.id, u.full_name,
                       count(c.id) FILTER (WHERE c.status NOT IN ('Closed', 'Rejected'))                  AS open_cases,
                       count(c.id) FILTER (WHERE c.status = 'Closed' AND c.closed_at >= ? AND c.closed_at < ?) AS closed_today,
                       count(c.id) FILTER (WHERE c.status NOT IN ('Closed', 'Rejected') AND c.sla_deadline < ?) AS breached,
                       count(c.id) FILTER (WHERE c.created_at >= ?)                                        AS period_cases,
                       count(c.id) FILTER (WHERE c.created_at >= ? AND c.has_ai)                           AS period_with_ai
                FROM users u
                LEFT JOIN (SELECT e.*, EXISTS (SELECT 1 FROM ai_analyses a WHERE a.case_id = e.id) AS has_ai
                           FROM exception_cases e) c ON c.assigned_to_user_id = u.id
                WHERE u.role = 'Specialist'
                GROUP BY u.id, u.full_name
                ORDER BY u.full_name
                """,
                (row, index) -> new TeamMember(row.getLong("id"), row.getString("full_name"),
                        row.getLong("open_cases"), row.getLong("closed_today"),
                        averageMinutes(closedByUser.getOrDefault(row.getLong("id"), List.of()), c -> true),
                        row.getLong("breached"),
                        ratio(row.getLong("period_with_ai"), row.getLong("period_cases"))),
                ts(dayStart), ts(dayEnd), ts(now), ts(since), ts(since));
    }

    // ----------------------------------------------------------- ısı haritası

    /** Hafta içi × 08–17 saatleri için son 4 haftanın ortalama yeni kayıt sayısı (her hücre dolu döner). */
    public List<HeatmapCell> hourlyHeatmap() {
        Instant since = clock.instant().minus(Duration.ofDays(7L * HEATMAP_WEEKS));
        Map<Integer, Long> counts = new HashMap<>();
        jdbc.query("""
                SELECT CAST(extract(isodow FROM local_time) AS int) AS weekday,
                       CAST(extract(hour FROM local_time) AS int)   AS hour,
                       count(*)                                     AS n
                FROM (SELECT created_at AT TIME ZONE ? AS local_time FROM exception_cases WHERE created_at >= ?) t
                GROUP BY 1, 2
                """,
                row -> {
                    counts.put(row.getInt("weekday") * 100 + row.getInt("hour"), row.getLong("n"));
                },
                zone.getId(), ts(since));

        List<HeatmapCell> cells = new ArrayList<>();
        for (int weekday = 1; weekday <= 5; weekday++) {
            for (int hour = HEATMAP_FIRST_HOUR; hour <= HEATMAP_LAST_HOUR; hour++) {
                long count = counts.getOrDefault(weekday * 100 + hour, 0L);
                cells.add(new HeatmapCell(weekday, hour, count / (double) HEATMAP_WEEKS));
            }
        }
        return cells;
    }

    // ------------------------------------------------------------ yardımcılar

    /** Yalnızca 7, 30 ve 90 gün geçerlidir; verilmezse 30. Başka değer 400 döndürür. */
    static int period(Integer days) {
        int value = days == null ? DEFAULT_PERIOD : days;
        if (!ALLOWED_PERIODS.contains(value)) {
            throw new IllegalArgumentException("Dönem 7, 30 veya 90 gün olmalı");
        }
        return value;
    }

    private static Double ratio(long part, long whole) {
        return whole == 0 ? null : part / (double) whole;
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
