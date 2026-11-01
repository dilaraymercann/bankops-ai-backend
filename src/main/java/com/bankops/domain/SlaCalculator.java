package com.bankops.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.Instant;

/**
 * SLA hesabı (CLAUDE.md §9). SLA mesai saatlerine göre işler: hafta içi, resmi tatil olmayan günlerde
 * mesai başlangıcı ile bitişi arası. {@link Priority#Critical} kayıtlar 7/24 işler.
 * Süreler "mesai dakikası" cinsindendir; örneğin Low için "2 iş günü" = 2 x 9 saat = 1080 dakikadır.
 */
public final class SlaCalculator {

    /** Sürenin bu oranı dolunca SLA "yaklaşıyor" sayılır. */
    private static final double APPROACHING_RATIO = 0.80;

    private final LocalTime businessStart;
    private final LocalTime businessEnd;
    private final ZoneId zone;
    private final HolidayCalendar holidays;

    public SlaCalculator(LocalTime businessStart, LocalTime businessEnd, ZoneId zone, HolidayCalendar holidays) {
        this.businessStart = businessStart;
        this.businessEnd = businessEnd;
        this.zone = zone;
        this.holidays = holidays;
    }

    /** Başlangıç anından itibaren verilen mesai süresi dolunca ulaşılan SLA bitiş zamanı. */
    public Instant calculateDeadline(Instant start, Priority priority, Duration slaDuration) {
        if (priority == Priority.Critical) {
            return start.plus(slaDuration);
        }
        LocalDateTime cursor = moveIntoBusinessHours(LocalDateTime.ofInstant(start, zone));
        Duration remaining = slaDuration;
        while (true) {
            Duration availableToday = Duration.between(cursor, cursor.toLocalDate().atTime(businessEnd));
            if (remaining.compareTo(availableToday) <= 0) {
                return cursor.plus(remaining).atZone(zone).toInstant();
            }
            remaining = remaining.minus(availableToday);
            cursor = nextBusinessDayStart(cursor.toLocalDate());
        }
    }

    /** İki an arasında geçen çalışma süresi (Critical için gerçek süre, diğerleri için yalnızca mesai). */
    public Duration workingTimeBetween(Priority priority, Instant from, Instant to) {
        if (!to.isAfter(from)) {
            return Duration.ZERO;
        }
        if (priority == Priority.Critical) {
            return Duration.between(from, to);
        }
        LocalDateTime start = LocalDateTime.ofInstant(from, zone);
        LocalDateTime end = LocalDateTime.ofInstant(to, zone);
        Duration total = Duration.ZERO;
        for (LocalDate day = start.toLocalDate(); !day.isAfter(end.toLocalDate()); day = day.plusDays(1)) {
            if (!isBusinessDay(day)) {
                continue;
            }
            LocalDateTime windowStart = max(start, day.atTime(businessStart));
            LocalDateTime windowEnd = min(end, day.atTime(businessEnd));
            if (windowEnd.isAfter(windowStart)) {
                total = total.plus(Duration.between(windowStart, windowEnd));
            }
        }
        return total;
    }

    /** SLA bitişine kalan çalışma süresi. */
    public Duration remaining(Priority priority, Instant now, Instant deadline) {
        return workingTimeBetween(priority, now, deadline);
    }

    public SlaState stateAt(Priority priority, Instant created, Instant deadline, Instant now) {
        if (!now.isBefore(deadline)) {
            return SlaState.Breached;
        }
        Duration total = workingTimeBetween(priority, created, deadline);
        if (total.isZero()) {
            return SlaState.Normal;
        }
        Duration elapsed = workingTimeBetween(priority, created, now);
        double ratio = (double) elapsed.toMillis() / total.toMillis();
        return ratio >= APPROACHING_RATIO ? SlaState.Approaching : SlaState.Normal;
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        return !weekend && !holidays.isHoliday(date);
    }

    /** Mesai dışındaki bir anı, bir sonraki mesai başlangıcına taşır; mesai içindeyse olduğu gibi bırakır. */
    private LocalDateTime moveIntoBusinessHours(LocalDateTime time) {
        LocalDate day = time.toLocalDate();
        if (!isBusinessDay(day)) {
            return nextBusinessDayStart(day);
        }
        if (time.toLocalTime().isBefore(businessStart)) {
            return day.atTime(businessStart);
        }
        if (!time.toLocalTime().isBefore(businessEnd)) {
            return nextBusinessDayStart(day);
        }
        return time;
    }

    private LocalDateTime nextBusinessDayStart(LocalDate day) {
        LocalDate next = day.plusDays(1);
        while (!isBusinessDay(next)) {
            next = next.plusDays(1);
        }
        return next.atTime(businessStart);
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
