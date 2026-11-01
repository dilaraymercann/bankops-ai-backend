package com.bankops.seed;

import static com.bankops.domain.CaseStatus.Assigned;
import static com.bankops.domain.CaseStatus.AwaitingInfo;
import static com.bankops.domain.CaseStatus.Closed;
import static com.bankops.domain.CaseStatus.Escalated;
import static com.bankops.domain.CaseStatus.InReview;
import static com.bankops.domain.CaseStatus.New;
import static com.bankops.domain.CaseStatus.Rejected;
import static com.bankops.domain.CaseStatus.SolutionProposed;
import static com.bankops.seed.Dice.w;

import com.bankops.domain.AiAnalysis;
import com.bankops.domain.AiFeedback;
import com.bankops.domain.AuditLog;
import com.bankops.domain.CaseStatus;
import com.bankops.domain.Channel;
import com.bankops.domain.ErrorCode;
import com.bankops.domain.ExceptionCase;
import com.bankops.domain.IbanValidator;
import com.bankops.domain.PaymentTransaction;
import com.bankops.domain.Priority;
import com.bankops.domain.PublicHoliday;
import com.bankops.domain.Resolution;
import com.bankops.domain.Role;
import com.bankops.domain.SlaCalculator;
import com.bankops.domain.SlaPolicySet;
import com.bankops.domain.StatusHistory;
import com.bankops.domain.TransactionType;
import com.bankops.domain.User;
import com.bankops.infrastructure.config.SlaProperties;
import com.bankops.infrastructure.persistence.AiAnalysisRepository;
import com.bankops.infrastructure.persistence.AuditLogRepository;
import com.bankops.infrastructure.persistence.ExceptionCaseRepository;
import com.bankops.infrastructure.persistence.PaymentTransactionRepository;
import com.bankops.infrastructure.persistence.PublicHolidayRepository;
import com.bankops.infrastructure.persistence.ResolutionRepository;
import com.bankops.infrastructure.persistence.SlaPolicyRepository;
import com.bankops.infrastructure.persistence.StatusHistoryRepository;
import com.bankops.infrastructure.persistence.UserRepository;
import com.bankops.infrastructure.sla.DatabaseHolidayCalendar;
import com.bankops.seed.Dice.Weighted;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sentetik demo verisini üretir (CLAUDE.md §13). Sabit tohum kullanıldığı için aynı gün çalıştırıldığında
 * her seferinde aynı veri oluşur. Üretim sırası zamana göredir: bilgi tabanı zamanla büyür ve bir kayıtta
 * yapılan AI analizi yalnızca o tarihten önce onaylanmış çözümlere dayanır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeedDataService {

    private static final long RANDOM_SEED = 20261101L;
    private static final int TOTAL_CASES = 2500;
    private static final int OPEN_CASES = 140;
    private static final int WINDOW_DAYS = 90;
    private static final double REJECTED_RATE = 0.025;
    private static final double SLA_BREACH_RATE = 0.07;
    private static final double AWAITING_INFO_RATE = 0.10;
    private static final double REVISION_RATE = 0.05;
    private static final int FLUSH_EVERY = 200;

    private record DemoUser(String fullName, String username, Role role) {
    }

    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("Elif Kaya", "elif.kaya", Role.Specialist),
            new DemoUser("Can Arslan", "can.arslan", Role.Specialist),
            new DemoUser("Zeynep Koç", "zeynep.koc", Role.Specialist),
            new DemoUser("Mert Şahin", "mert.sahin", Role.Specialist),
            new DemoUser("Ayşe Yıldız", "ayse.yildiz", Role.Specialist),
            new DemoUser("Burak Demir", "burak.demir", Role.TeamLead),
            new DemoUser("Selin Aydın", "selin.aydin", Role.Manager));

    /** İş yükü dağılımı (Elif ekip ortalamasının üstünde) ve her uzmanın AI kullanım oranı; sıra DEMO_USERS'taki uzmanlarla aynı. */
    private static final double[] SPECIALIST_LOAD = {0.32, 0.17, 0.17, 0.19, 0.15};
    private static final double[] SPECIALIST_AI_USAGE = {0.88, 0.92, 0.79, 0.41, 0.85};

    private static final Map<String, String> HOLIDAYS = holidays();

    // Hata türü dağılımı: NAME_MISMATCH ve DUPLICATE_SUSPECT en sık, SANCTION_HIT en seyrek
    private static final List<Weighted<ErrorCode>> CODE_WEIGHTS = List.of(
            w(ErrorCode.NAME_MISMATCH, 24), w(ErrorCode.DUPLICATE_SUSPECT, 16), w(ErrorCode.IBAN_CHECKSUM, 14),
            w(ErrorCode.TIMEOUT, 10), w(ErrorCode.BATCH_FORMAT, 8), w(ErrorCode.ACCOUNT_BLOCKED, 8),
            w(ErrorCode.FAST_LIMIT, 6), w(ErrorCode.HOLIDAY_VALUE_DATE, 6), w(ErrorCode.OUT_OF_HOURS, 5),
            w(ErrorCode.SANCTION_HIT, 3));

    /** Bilgi tabanına girecek çözüm sayısı hedefi (toplam ~475). SANCTION_HIT bilerek az: "bilgi tabanı zayıf" senaryosu. */
    private static final Map<ErrorCode, Integer> KNOWLEDGE_BASE_TARGET = new EnumMap<>(Map.of(
            ErrorCode.NAME_MISMATCH, 130, ErrorCode.IBAN_CHECKSUM, 110, ErrorCode.DUPLICATE_SUSPECT, 100,
            ErrorCode.BATCH_FORMAT, 40, ErrorCode.TIMEOUT, 35, ErrorCode.ACCOUNT_BLOCKED, 20,
            ErrorCode.FAST_LIMIT, 15, ErrorCode.HOLIDAY_VALUE_DATE, 10, ErrorCode.SANCTION_HIT, 10,
            ErrorCode.OUT_OF_HOURS, 5));

    /** AI önerisinin "faydalı" bulunma oranı (dashboard'daki hata türü tablosu için). */
    private static final Map<ErrorCode, Double> AI_ACCEPTANCE = new EnumMap<>(Map.of(
            ErrorCode.IBAN_CHECKSUM, 0.91, ErrorCode.NAME_MISMATCH, 0.84, ErrorCode.DUPLICATE_SUSPECT, 0.79,
            ErrorCode.BATCH_FORMAT, 0.61, ErrorCode.TIMEOUT, 0.52, ErrorCode.SANCTION_HIT, 0.34,
            ErrorCode.FAST_LIMIT, 0.75, ErrorCode.OUT_OF_HOURS, 0.72, ErrorCode.HOLIDAY_VALUE_DATE, 0.70,
            ErrorCode.ACCOUNT_BLOCKED, 0.68));

    private static final List<Weighted<TransactionType>> TYPE_WEIGHTS = List.of(
            w(TransactionType.Fast, 46), w(TransactionType.Transfer, 24),
            w(TransactionType.CorporateOrder, 18), w(TransactionType.BulkPayment, 12));

    // Hafta içi saat dağılımı 08..18: öğleden sonra 14:00-16:00 yoğun
    private static final List<Weighted<Integer>> WEEKDAY_HOURS = List.of(
            w(8, 4), w(9, 7), w(10, 10), w(11, 10), w(12, 6), w(13, 9), w(14, 15), w(15, 17), w(16, 13),
            w(17, 8), w(18, 3));

    private static final List<Integer> OFF_HOURS = List.of(18, 19, 20, 21, 22, 23, 0, 1, 2, 3, 4, 5, 6, 7);

    private final UserRepository userRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final ExceptionCaseRepository caseRepository;
    private final StatusHistoryRepository historyRepository;
    private final ResolutionRepository resolutionRepository;
    private final AiAnalysisRepository analysisRepository;
    private final AuditLogRepository auditLogRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final PublicHolidayRepository holidayRepository;
    private final DatabaseHolidayCalendar holidayCalendar;
    private final SlaCalculator slaCalculator;
    private final SlaProperties slaProperties;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final EntityManager entityManager;

    public record SeedSummary(int users, int cases, int openCases, int knowledgeBaseResolutions, int aiAnalyses) {
    }

    @Transactional
    public SeedSummary seed(String demoPassword) {
        return new Run(demoPassword).execute();
    }

    private static Map<String, String> holidays() {
        Map<String, String> days = new java.util.LinkedHashMap<>();
        days.put("2026-01-01", "Yılbaşı");
        days.put("2026-03-20", "Ramazan Bayramı 1. Gün");
        days.put("2026-03-21", "Ramazan Bayramı 2. Gün");
        days.put("2026-03-22", "Ramazan Bayramı 3. Gün");
        days.put("2026-04-23", "Ulusal Egemenlik ve Çocuk Bayramı");
        days.put("2026-05-01", "Emek ve Dayanışma Günü");
        days.put("2026-05-19", "Atatürk'ü Anma, Gençlik ve Spor Bayramı");
        days.put("2026-05-27", "Kurban Bayramı 1. Gün");
        days.put("2026-05-28", "Kurban Bayramı 2. Gün");
        days.put("2026-05-29", "Kurban Bayramı 3. Gün");
        days.put("2026-05-30", "Kurban Bayramı 4. Gün");
        days.put("2026-07-15", "Demokrasi ve Millî Birlik Günü");
        days.put("2026-08-30", "Zafer Bayramı");
        days.put("2026-10-29", "Cumhuriyet Bayramı");
        return days;
    }

    /** Bir durum geçişi; kayıt veritabanına yazıldıktan sonra StatusHistory ve AuditLog satırlarına çevrilir. */
    private record Move(CaseStatus from, CaseStatus to, Long byUserId, String reason, Instant at) {
    }

    /** Üretilecek bir kaydın planı: zamanlama ve karakteristikler önce belirlenir, sonra zaman sırasıyla yazılır. */
    private static final class CasePlan {
        ErrorCode code;
        TransactionType type;
        Priority priority;
        User assignee;
        boolean usesAi;
        boolean open;
        CaseStatus openStatus;
        boolean rejected;
        boolean includeInKnowledgeBase;
        Instant created;
        Instant deadline;
        Instant closedAt;
        LocalDate holiday;
    }

    /** Tek bir seed çalıştırmasının durumu (rastgelelik, sayaçlar, bilgi tabanı). */
    private final class Run {

        private final String demoPassword;
        private final Dice dice = new Dice(RANDOM_SEED);
        private final Faker faker = new Faker(Locale.of("tr", "TR"), dice.random());
        private final Instant now = clock.instant();
        private final ZoneId zone = slaProperties.timeZone();
        private final LocalDate windowStart = LocalDate.ofInstant(now, zone).minusDays(WINDOW_DAYS);

        private List<User> specialists;
        private User teamLead;
        private SlaPolicySet policies;
        private List<Weighted<LocalDate>> dayWeights;
        private List<LocalDate> holidaysInWindow;

        /** Hata koduna göre, o ana kadar bilgi tabanına girmiş kayıt numaraları. */
        private final Map<ErrorCode, List<String>> knowledgeBase = new EnumMap<>(ErrorCode.class);

        private int transactionSeq = 100000;
        private int caseSeq = 10000;
        private int persisted;
        private int openCount;
        private int knowledgeBaseCount;
        private int analysisCount;

        Run(String demoPassword) {
            this.demoPassword = demoPassword;
        }

        SeedSummary execute() {
            insertHolidays();
            List<User> users = createUsers();
            specialists = users.stream().filter(u -> u.getRole() == Role.Specialist).toList();
            teamLead = users.stream().filter(u -> u.getRole() == Role.TeamLead).findFirst().orElseThrow();
            policies = new SlaPolicySet(slaPolicyRepository.findAll());
            dayWeights = buildDayWeights();

            List<CasePlan> plans = buildPlans();
            log.info("Seed: {} kayıt planlandı, yazılıyor...", plans.size());
            for (CasePlan plan : plans) {
                persist(plan);
                if (++persisted % FLUSH_EVERY == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
            SeedSummary summary = new SeedSummary(
                    users.size(), plans.size(), openCount, knowledgeBaseCount, analysisCount);
            log.info("Seed tamamlandı: {}", summary);
            return summary;
        }

        // ---------------------------------------------------------------- temel veriler

        private void insertHolidays() {
            HOLIDAYS.forEach((date, name) -> {
                PublicHoliday holiday = new PublicHoliday();
                holiday.setDate(LocalDate.parse(date));
                holiday.setName(name);
                holidayRepository.save(holiday);
            });
            holidayRepository.flush();
            holidayCalendar.reload();
            holidaysInWindow = HOLIDAYS.keySet().stream()
                    .map(LocalDate::parse)
                    .filter(d -> d.isAfter(windowStart.plusDays(2)) && d.isBefore(LocalDate.ofInstant(now, zone).minusDays(3)))
                    .toList();
        }

        private List<User> createUsers() {
            List<User> users = new ArrayList<>();
            for (DemoUser demo : DEMO_USERS) {
                User user = new User();
                user.setFullName(demo.fullName());
                user.setUsername(demo.username());
                user.setRole(demo.role());
                user.setPasswordHash(passwordEncoder.encode(demoPassword));
                users.add(userRepository.save(user));
            }
            return users;
        }

        private List<Weighted<LocalDate>> buildDayWeights() {
            List<Weighted<LocalDate>> weights = new ArrayList<>();
            for (LocalDate day = windowStart; !day.isAfter(LocalDate.ofInstant(now, zone)); day = day.plusDays(1)) {
                double weight;
                if (HOLIDAYS.containsKey(day.toString())) {
                    weight = 0.1;
                } else if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    weight = 0.12;
                } else if (day.getDayOfWeek() == DayOfWeek.MONDAY || day.getDayOfWeek() == DayOfWeek.FRIDAY) {
                    weight = 1.3;
                } else {
                    weight = 1.0;
                }
                weights.add(w(day, weight));
            }
            return weights;
        }

        // ---------------------------------------------------------------- planlama

        private List<CasePlan> buildPlans() {
            int closedCount = TOTAL_CASES - OPEN_CASES;
            List<ErrorCode> closedCodes = new ArrayList<>();
            for (int i = 0; i < closedCount; i++) {
                closedCodes.add(dice.weighted(CODE_WEIGHTS));
            }
            Map<ErrorCode, Long> codeCounts = closedCodes.stream()
                    .collect(Collectors.groupingBy(c -> c, () -> new EnumMap<>(ErrorCode.class), Collectors.counting()));

            List<CasePlan> plans = new ArrayList<>();
            for (ErrorCode code : closedCodes) {
                plans.add(closedPlan(code, codeCounts.get(code)));
            }
            List<Weighted<ErrorCode>> openCodeWeights = CODE_WEIGHTS.stream()
                    .filter(option -> option.value() != ErrorCode.HOLIDAY_VALUE_DATE)
                    .toList();
            for (int i = 0; i < OPEN_CASES - 1; i++) {
                plans.add(openPlan(dice.weighted(openCodeWeights)));
            }
            plans.add(demoCasePlan());
            plans.sort(Comparator.comparing(plan -> plan.created));
            return plans;
        }

        private CasePlan basePlan(ErrorCode code) {
            CasePlan plan = new CasePlan();
            plan.code = code;
            List<Weighted<TransactionType>> applicable = TYPE_WEIGHTS.stream()
                    .filter(option -> code.appliesTo(option.value()))
                    .toList();
            plan.type = dice.weighted(applicable);
            plan.priority = code.defaultPriority();
            if (plan.priority != Priority.Low && dice.chance(0.10)) {
                plan.priority = Priority.values()[plan.priority.ordinal() - 1];
            }
            int assigneeIndex = pickSpecialistIndex();
            plan.assignee = specialists.get(assigneeIndex);
            plan.usesAi = dice.chance(SPECIALIST_AI_USAGE[assigneeIndex]);
            return plan;
        }

        private int pickSpecialistIndex() {
            double roll = dice.uniform(0, 1);
            for (int i = 0; i < SPECIALIST_LOAD.length; i++) {
                roll -= SPECIALIST_LOAD[i];
                if (roll < 0) {
                    return i;
                }
            }
            return SPECIALIST_LOAD.length - 1;
        }

        private CasePlan closedPlan(ErrorCode code, long countForCode) {
            CasePlan plan = basePlan(code);
            plan.rejected = dice.chance(REJECTED_RATE);
            plan.includeInKnowledgeBase = !plan.rejected
                    && dice.chance((double) KNOWLEDGE_BASE_TARGET.get(code) / countForCode);
            boolean breached = dice.chance(SLA_BREACH_RATE);

            // AI destekli kayıtlar daha kısa sürede çözülür (ortalama 38 dk, AI'sız 61 dk)
            double meanMinutes = plan.usesAi ? 38 : 61;
            long workMinutes = Math.max(15, Math.round(meanMinutes * Math.exp(0.5 * dice.gaussian() - 0.125)));
            Duration sla = policies.durationFor(plan.priority, code);
            // SLA'yı aşmayan kayıtlar süreyi aşmayacak kadar hızlı kapanır
            Duration work = Duration.ofMinutes(Math.min(workMinutes, (long) (sla.toMinutes() * 0.85)));
            Duration overshoot = Duration.ofMinutes(dice.between(10, 180));

            Instant latestCreation = now.minus(Duration.ofHours(12));
            Instant created = switch (code) {
                case OUT_OF_HOURS -> sampleCreation(latestCreation, true, plan);
                case HOLIDAY_VALUE_DATE -> sampleBeforeHoliday(latestCreation, plan);
                default -> sampleCreation(latestCreation, false, plan);
            };
            // Kapanış zamanı geleceğe taşıyorsa kayıt bir gün geriye alınır
            while (true) {
                Instant deadline = slaCalculator.calculateDeadline(created, plan.priority, sla);
                Instant closedAt = breached
                        ? slaCalculator.calculateDeadline(deadline, plan.priority, overshoot)
                        : slaCalculator.calculateDeadline(created, plan.priority, work);
                if (closedAt.isBefore(now.minus(Duration.ofMinutes(30)))) {
                    plan.created = created;
                    plan.deadline = deadline;
                    plan.closedAt = closedAt;
                    return plan;
                }
                created = created.minus(Duration.ofDays(1));
            }
        }

        private CasePlan openPlan(ErrorCode code) {
            CasePlan plan = basePlan(code);
            plan.open = true;
            // Açık kayıtların yaşı: 0-1 saat %41, 1-4 saat %29, 4-8 saat %16, 1-3 gün %10, 3 gün+ %4
            double roll = dice.uniform(0, 1);
            int ageMinutes;
            List<Weighted<CaseStatus>> statusWeights;
            if (roll < 0.41) {
                ageMinutes = dice.between(2, 60);
                statusWeights = List.of(w(New, 55), w(Assigned, 30), w(InReview, 15));
            } else if (roll < 0.70) {
                ageMinutes = dice.between(60, 240);
                statusWeights = openStatusWeights(true);
            } else if (roll < 0.86) {
                ageMinutes = dice.between(240, 480);
                statusWeights = openStatusWeights(true);
            } else {
                ageMinutes = roll < 0.96 ? dice.between(1440, 4320) : dice.between(4320, 8640);
                statusWeights = openStatusWeights(false);
            }
            plan.created = now.minus(Duration.ofMinutes(ageMinutes));
            plan.openStatus = dice.weighted(statusWeights);
            plan.deadline = slaCalculator.calculateDeadline(plan.created, plan.priority, policies.durationFor(plan.priority, code));
            if (plan.openStatus == New) {
                plan.assignee = null;
            }
            return plan;
        }

        private List<Weighted<CaseStatus>> openStatusWeights(boolean recent) {
            return recent
                    ? List.of(w(New, 15), w(Assigned, 20), w(InReview, 40), w(AwaitingInfo, 10),
                            w(SolutionProposed, 12), w(Escalated, 3))
                    : List.of(w(New, 4), w(Assigned, 8), w(InReview, 38), w(AwaitingInfo, 20),
                            w(SolutionProposed, 15), w(Escalated, 15));
        }

        /** Demo senaryosu için: Elif Kaya'nın incelemedeki, SLA'sı aşılmış NAME_MISMATCH kaydı (CLAUDE.md §18). */
        private CasePlan demoCasePlan() {
            CasePlan plan = new CasePlan();
            plan.code = ErrorCode.NAME_MISMATCH;
            plan.type = TransactionType.Fast;
            plan.priority = Priority.High;
            plan.assignee = specialists.getFirst();
            plan.usesAi = true;
            plan.open = true;
            plan.openStatus = InReview;
            Duration sla = policies.durationFor(plan.priority, plan.code);
            // SLA bitişi her zaman geçmişte kalsın (hafta sonuna denk gelirse kayıt bir gün daha geriye alınır)
            plan.created = now.minus(Duration.ofDays(2));
            plan.deadline = slaCalculator.calculateDeadline(plan.created, plan.priority, sla);
            while (!plan.deadline.isBefore(now.minus(Duration.ofMinutes(18)))) {
                plan.created = plan.created.minus(Duration.ofDays(1));
                plan.deadline = slaCalculator.calculateDeadline(plan.created, plan.priority, sla);
            }
            return plan;
        }

        // ---------------------------------------------------------------- zaman örnekleme

        private Instant sampleCreation(Instant latest, boolean offHours, CasePlan plan) {
            for (int attempt = 0; attempt < 200; attempt++) {
                LocalDate day = dice.weighted(dayWeights);
                Instant time = atHour(day, offHours ? dice.pick(OFF_HOURS) : pickHour(day));
                if (time.isBefore(latest)) {
                    return time;
                }
            }
            return latest.minus(Duration.ofDays(1));
        }

        private Instant sampleBeforeHoliday(Instant latest, CasePlan plan) {
            if (holidaysInWindow.isEmpty()) {
                return sampleCreation(latest, false, plan);
            }
            LocalDate holiday = dice.pick(holidaysInWindow);
            LocalDate day = holiday.minusDays(dice.between(1, 4));
            while (isWeekend(day)) {
                day = day.minusDays(1);
            }
            plan.holiday = holiday;
            Instant time = atHour(day, pickHour(day));
            return time.isBefore(latest) ? time : sampleCreation(latest, false, plan);
        }

        private int pickHour(LocalDate day) {
            return isWeekend(day) ? dice.between(9, 16) : dice.weighted(WEEKDAY_HOURS);
        }

        private boolean isWeekend(LocalDate day) {
            return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
        }

        private Instant atHour(LocalDate day, int hour) {
            return day.atTime(hour, dice.between(0, 59), dice.between(0, 59)).atZone(zone).toInstant();
        }

        // ---------------------------------------------------------------- yazma

        private void persist(CasePlan plan) {
            Instant created = plan.created;
            Long specialistId = plan.assignee == null ? null : plan.assignee.getId();
            Long leadId = teamLead.getId();

            List<Move> moves = new ArrayList<>();
            moves.add(new Move(null, New, null, "Kayıt oluşturuldu", created));
            CaseStatus status;
            Instant proposedAt = null;
            Instant closedAt = null;
            Instant analysisAt;

            if (plan.open) {
                status = plan.openStatus;
                Duration age = Duration.between(created, now);
                if (status != New) {
                    Duration assignDelay = age.dividedBy(2).compareTo(Duration.ofMinutes(1)) < 0
                            ? age.dividedBy(2) : Duration.ofMinutes(1);
                    moves.add(new Move(New, Assigned, null, "Otomatik dağıtım", created.plus(assignDelay)));
                }
                if (status == InReview || status == AwaitingInfo || status == SolutionProposed || status == Escalated) {
                    moves.add(new Move(Assigned, InReview, specialistId, null, created.plus(age.multipliedBy(30).dividedBy(100))));
                }
                if (status == AwaitingInfo) {
                    moves.add(new Move(InReview, AwaitingInfo, specialistId, "Müşteriden bilgi bekleniyor",
                            created.plus(age.multipliedBy(60).dividedBy(100))));
                }
                if (status == SolutionProposed) {
                    proposedAt = created.plus(age.multipliedBy(70).dividedBy(100));
                    moves.add(new Move(InReview, SolutionProposed, specialistId, null, proposedAt));
                }
                if (status == Escalated) {
                    moves.add(new Move(InReview, Escalated, specialistId,
                            "Manuel eskalasyon: takım lideri desteği gerekiyor", created.plus(age.multipliedBy(70).dividedBy(100))));
                }
                analysisAt = created.plus(age.multipliedBy(50).dividedBy(100));
            } else {
                closedAt = plan.closedAt;
                Duration total = slaCalculator.workingTimeBetween(plan.priority, created, closedAt);
                moves.add(new Move(New, Assigned, null, "Otomatik dağıtım", created.plus(Duration.ofMinutes(1))));
                moves.add(new Move(Assigned, InReview, specialistId, null, workingAt(plan, total, 0.12)));
                if (dice.chance(AWAITING_INFO_RATE)) {
                    moves.add(new Move(InReview, AwaitingInfo, specialistId, "Müşteriden bilgi bekleniyor", workingAt(plan, total, 0.30)));
                    moves.add(new Move(AwaitingInfo, InReview, specialistId, "Bilgi alındı", workingAt(plan, total, 0.45)));
                }
                if (plan.rejected) {
                    status = Rejected;
                    moves.add(new Move(InReview, Rejected, specialistId, "Talimat iade edildi", closedAt));
                } else {
                    status = Closed;
                    proposedAt = workingAt(plan, total, 0.70);
                    moves.add(new Move(InReview, SolutionProposed, specialistId, null, proposedAt));
                    if (dice.chance(REVISION_RATE)) {
                        moves.add(new Move(SolutionProposed, InReview, leadId, "Revizyon: çözüm açıklaması yetersiz", workingAt(plan, total, 0.78)));
                        proposedAt = workingAt(plan, total, 0.88);
                        moves.add(new Move(InReview, SolutionProposed, specialistId, null, proposedAt));
                    }
                    moves.add(new Move(SolutionProposed, Closed, leadId, "Çözüm onaylandı", closedAt));
                }
                analysisAt = workingAt(plan, total, dice.uniform(0.15, 0.50));
            }

            PaymentTransaction transaction = transactionRepository.save(buildTransaction(plan));
            ExceptionCase exceptionCase = buildCase(plan, transaction.getId(), status, specialistId, leadId, moves, closedAt, proposedAt);
            caseRepository.save(exceptionCase);
            if (plan.open) {
                openCount++;
            }

            List<AuditLog> audits = new ArrayList<>();
            List<StatusHistory> history = new ArrayList<>();
            for (Move move : moves) {
                history.add(toHistory(exceptionCase.getId(), move));
                audits.add(audit(move.at(), move.byUserId(), "STATUS_CHANGE", "ExceptionCase", exceptionCase.getId(),
                        "{\"from\":%s,\"to\":\"%s\"}".formatted(
                                move.from() == null ? "null" : "\"" + move.from() + "\"", move.to())));
            }
            historyRepository.saveAll(history);

            // SLA aşıldıysa sistem, süre dolduktan 1 dakika sonra takım liderine eskale etmiştir
            boolean slaBreached = plan.open ? plan.deadline.isBefore(now) : closedAt.isAfter(plan.deadline);
            if (slaBreached) {
                audits.add(audit(plan.deadline.plus(Duration.ofMinutes(1)), null, "ESCALATE", "ExceptionCase",
                        exceptionCase.getId(), "{\"reason\":\"sla_breached\",\"escalatedTo\":%d}".formatted(leadId)));
            }

            writeAiAnalysis(plan, exceptionCase, status, specialistId, analysisAt, audits);
            writeResolution(plan, exceptionCase, status, specialistId, leadId, proposedAt, closedAt, audits);
            auditLogRepository.saveAll(audits);
        }

        private ExceptionCase buildCase(CasePlan plan, Long transactionId, CaseStatus status, Long specialistId, Long leadId,
                                        List<Move> moves, Instant closedAt, Instant proposedAt) {
            ExceptionCase exceptionCase = new ExceptionCase();
            int year = LocalDate.ofInstant(plan.created, zone).getYear();
            exceptionCase.setCaseNumber("EXC-%d-%d".formatted(year, ++caseSeq));
            exceptionCase.setTransactionId(transactionId);
            exceptionCase.setErrorCode(plan.code);
            exceptionCase.setErrorDescription(dice.chance(0.5)
                    ? plan.code.description() : dice.pick(SeedTexts.errorDetails(plan.code)));
            exceptionCase.setPriority(plan.priority);
            exceptionCase.setStatus(status);
            exceptionCase.setAssignedToUserId(specialistId);
            exceptionCase.setProposedByUserId(proposedAt == null ? null : specialistId);
            exceptionCase.setClosedByUserId(switch (status) {
                case Closed -> leadId;
                case Rejected -> specialistId;
                default -> null;
            });
            exceptionCase.setSlaDeadline(plan.deadline);
            boolean slaBreached = plan.open ? plan.deadline.isBefore(now) : closedAt.isAfter(plan.deadline);
            exceptionCase.setEscalated(slaBreached || status == Escalated);
            exceptionCase.setCreatedAt(plan.created);
            exceptionCase.setUpdatedAt(moves.getLast().at());
            exceptionCase.setClosedAt(closedAt);
            return exceptionCase;
        }

        private void writeAiAnalysis(CasePlan plan, ExceptionCase exceptionCase, CaseStatus status, Long specialistId,
                                     Instant analysisAt, List<AuditLog> audits) {
            List<String> knowledge = knowledgeBase.getOrDefault(plan.code, List.of());
            boolean analyze = knowledge.size() >= 2 && (plan.open
                    ? (status == InReview || status == AwaitingInfo || status == SolutionProposed) && dice.chance(0.55)
                    : plan.usesAi);
            if (!analyze) {
                return;
            }
            List<String> shuffled = new ArrayList<>(knowledge);
            Collections.shuffle(shuffled, dice.random());
            List<String> references = shuffled.subList(0, Math.min(shuffled.size(), dice.between(2, 3)));
            int maskedFields = dice.between(3, 5);

            AiAnalysis analysis = new AiAnalysis();
            analysis.setCaseId(exceptionCase.getId());
            analysis.setResultJson(analysisJson(plan.code, references));
            analysis.setReferencedCaseIds(List.copyOf(references));
            analysis.setMaskedFieldCount(maskedFields);
            analysis.setModelName("synthetic-seed");
            analysis.setLatencyMs(dice.between(800, 4000));
            double roll = dice.uniform(0, 1);
            AiFeedback feedback = roll < 0.08 ? null
                    : dice.chance(AI_ACCEPTANCE.get(plan.code)) ? AiFeedback.Useful : AiFeedback.NotUseful;
            analysis.setFeedback(feedback);
            analysis.setCreatedAt(analysisAt);
            analysisRepository.save(analysis);
            analysisCount++;

            audits.add(audit(analysisAt, specialistId, "AI_ANALYZE", "ExceptionCase", exceptionCase.getId(),
                    "{\"referencedCaseIds\":%s,\"maskedFieldCount\":%d}".formatted(
                            SeedTexts.jsonArray(references), maskedFields)));
            if (feedback != null) {
                Instant feedbackAt = analysisAt.plus(Duration.ofMinutes(dice.between(2, 10)));
                audits.add(audit(feedbackAt.isAfter(now) ? now : feedbackAt, specialistId, "AI_FEEDBACK",
                        "AiAnalysis", analysis.getId(), "{\"feedback\":\"%s\"}".formatted(feedback)));
            }
        }

        private void writeResolution(CasePlan plan, ExceptionCase exceptionCase, CaseStatus status, Long specialistId,
                                     Long leadId, Instant proposedAt, Instant closedAt, List<AuditLog> audits) {
            if (status != Closed && status != SolutionProposed) {
                return;
            }
            Resolution resolution = new Resolution();
            resolution.setCaseId(exceptionCase.getId());
            resolution.setText(resolutionText(plan.code));
            resolution.setProposedByUserId(specialistId);
            resolution.setCreatedAt(proposedAt);
            if (status == Closed) {
                resolution.setApprovedByUserId(leadId);
                resolution.setVerified(true);
                resolution.setIncludeInKnowledgeBase(plan.includeInKnowledgeBase);
                resolution.setApprovedAt(closedAt);
                audits.add(audit(closedAt, leadId, "APPROVE", "ExceptionCase", exceptionCase.getId(),
                        "{\"includeInKnowledgeBase\":%s}".formatted(plan.includeInKnowledgeBase)));
                if (plan.includeInKnowledgeBase) {
                    knowledgeBase.computeIfAbsent(plan.code, key -> new ArrayList<>()).add(exceptionCase.getCaseNumber());
                    knowledgeBaseCount++;
                }
            }
            resolutionRepository.save(resolution);
        }

        // ---------------------------------------------------------------- yardımcılar

        /** Kayıt açıldığından itibaren toplam çalışma süresinin verilen oranı kadar sonraki an. */
        private Instant workingAt(CasePlan plan, Duration total, double fraction) {
            return slaCalculator.calculateDeadline(plan.created, plan.priority,
                    Duration.ofMillis((long) (total.toMillis() * fraction)));
        }

        private StatusHistory toHistory(Long caseId, Move move) {
            StatusHistory history = new StatusHistory();
            history.setCaseId(caseId);
            history.setFromStatus(move.from());
            history.setToStatus(move.to());
            history.setChangedByUserId(move.byUserId());
            history.setReason(move.reason());
            history.setChangedAt(move.at());
            return history;
        }

        private AuditLog audit(Instant at, Long userId, String action, String entityType, Long entityId, String details) {
            AuditLog log = new AuditLog();
            log.setTimestamp(at);
            log.setUserId(userId);
            log.setAction(action);
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setDetails(details);
            return log;
        }

        private String resolutionText(ErrorCode code) {
            return dice.pick(SeedTexts.resolutionSentences(code)) + dice.pick(SeedTexts.RESOLUTION_CLOSINGS);
        }

        private String analysisJson(ErrorCode code, List<String> references) {
            List<String> causes = pickDistinct(SeedTexts.GENERIC_CAUSES, 2);
            String possibleCauses = causes.stream()
                    .map(cause -> "{\"cause\":%s,\"supportingCaseIds\":%s}".formatted(
                            SeedTexts.quote(cause), SeedTexts.jsonArray(references.subList(0, 1))))
                    .collect(Collectors.joining(",", "[", "]"));
            return "{\"summary\":%s,\"possibleCauses\":%s,\"recommendedSteps\":%s,\"warnings\":%s,\"referencedCaseIds\":%s}"
                    .formatted(
                            SeedTexts.quote("Kayıtta \"" + code.description() + "\" hatası alındı. Benzer doğrulanmış kayıtlar incelendi.".replace("\"", "'")),
                            possibleCauses,
                            SeedTexts.jsonArray(pickDistinct(SeedTexts.GENERIC_STEPS, 3)),
                            SeedTexts.jsonArray(pickDistinct(SeedTexts.GENERIC_WARNINGS, 1)),
                            SeedTexts.jsonArray(references));
        }

        private List<String> pickDistinct(List<String> items, int count) {
            List<String> copy = new ArrayList<>(items);
            Collections.shuffle(copy, dice.random());
            return copy.subList(0, count);
        }

        // ---------------------------------------------------------------- işlem verisi

        private PaymentTransaction buildTransaction(CasePlan plan) {
            PaymentTransaction transaction = new PaymentTransaction();
            int year = LocalDate.ofInstant(plan.created, zone).getYear();
            transaction.setReference("TRX-%d-%06d".formatted(year, ++transactionSeq));
            transaction.setType(plan.type);
            transaction.setAmount(amountFor(plan));
            transaction.setCurrency("TRY");
            transaction.setSenderName(dice.chance(0.7) ? companyName() : personName());
            transaction.setSenderIban(randomIban());
            transaction.setReceiverName(dice.chance(0.6) ? personName() : companyName());
            transaction.setReceiverIban(plan.code == ErrorCode.IBAN_CHECKSUM ? withWrongCheckDigits(randomIban()) : randomIban());
            transaction.setDescription(dice.pick(SeedTexts.PAYMENT_DESCRIPTIONS));
            transaction.setChannel(channelFor(plan.type));
            transaction.setValueDate(plan.holiday != null ? plan.holiday : LocalDate.ofInstant(plan.created, zone));
            transaction.setCreatedAt(plan.created.minus(Duration.ofMinutes(1)));
            return transaction;
        }

        private BigDecimal amountFor(CasePlan plan) {
            double amount = switch (plan.code) {
                case FAST_LIMIT -> dice.logUniform(600_000, 1_200_000);
                case SANCTION_HIT -> dice.logUniform(100_000, 5_000_000);
                default -> switch (plan.type) {
                    case Transfer -> dice.logUniform(1_000, 250_000);
                    case Fast -> dice.logUniform(500, 400_000);
                    case BulkPayment -> dice.logUniform(50_000, 2_000_000);
                    case CorporateOrder -> dice.logUniform(100_000, 5_000_000);
                };
            };
            return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        }

        private Channel channelFor(TransactionType type) {
            return switch (type) {
                case BulkPayment, CorporateOrder -> dice.weighted(List.of(w(Channel.CorporateApi, 80), w(Channel.InternetBranch, 20)));
                default -> dice.weighted(List.of(w(Channel.Mobile, 45), w(Channel.InternetBranch, 35),
                        w(Channel.Branch, 10), w(Channel.CorporateApi, 10)));
            };
        }

        private String companyName() {
            return dice.pick(SeedTexts.COMPANY_PREFIXES) + " " + dice.pick(SeedTexts.COMPANY_SECTORS)
                    + " " + dice.pick(SeedTexts.COMPANY_SUFFIXES);
        }

        private String personName() {
            return faker.name().firstName() + " " + faker.name().lastName();
        }

        /** Geçerli kontrol hanesine sahip ama kurgusal IBAN: banka kodu 099xx aralığındadır. */
        private String randomIban() {
            String bankCode = "099%02d".formatted(dice.between(1, 20));
            StringBuilder account = new StringBuilder();
            for (int i = 0; i < 17; i++) {
                account.append(dice.between(0, 9));
            }
            return IbanValidator.generate(bankCode, account.toString());
        }

        private String withWrongCheckDigits(String iban) {
            int wrong = (Integer.parseInt(iban.substring(2, 4)) + 13) % 100;
            return "TR%02d%s".formatted(wrong, iban.substring(4));
        }
    }
}
