package com.bankops.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Takvim: 2026-11-02 Pazartesi. 2026-10-29 (Perşembe) resmi tatil olarak tanımlıdır.
 * Mesai: hafta içi 08:30-17:30, Europe/Istanbul.
 */
class SlaCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final Set<LocalDate> HOLIDAYS = Set.of(LocalDate.of(2026, 10, 29));

    private final SlaCalculator calculator =
            new SlaCalculator(LocalTime.of(8, 30), LocalTime.of(17, 30), ZONE, HOLIDAYS::contains);

    private static Instant at(int month, int day, int hour, int minute) {
        return LocalDateTime.of(2026, month, day, hour, minute).atZone(ZONE).toInstant();
    }

    private static Duration minutes(long m) {
        return Duration.ofMinutes(m);
    }

    // ---- Bitiş zamanı hesabı ----

    @Test
    void deadlineWithinSameBusinessDay() {
        // Pzt 09:00 + 8 saat (Medium) = Pzt 17:00
        assertThat(calculator.calculateDeadline(at(11, 2, 9, 0), Priority.Medium, minutes(480)))
                .isEqualTo(at(11, 2, 17, 0));
    }

    @Test
    void deadlineRollsOverToNextBusinessDay() {
        // Pzt 16:30: bugün 60 dk kalır, kalan 420 dk Salı 08:30'dan sayılır -> Salı 15:30
        assertThat(calculator.calculateDeadline(at(11, 2, 16, 30), Priority.Medium, minutes(480)))
                .isEqualTo(at(11, 3, 15, 30));
    }

    @Test
    void deadlineSkipsWeekend() {
        // Cuma 16:30 + 2 saat: 60 dk Cuma, 60 dk Pazartesi 08:30'dan -> Pzt 09:30
        assertThat(calculator.calculateDeadline(at(11, 6, 16, 30), Priority.High, minutes(120)))
                .isEqualTo(at(11, 9, 9, 30));
    }

    @Test
    void recordCreatedOnWeekendStartsOnMonday() {
        // Cumartesi 10:00 -> sayım Pazartesi 08:30'da başlar
        assertThat(calculator.calculateDeadline(at(11, 7, 10, 0), Priority.High, minutes(120)))
                .isEqualTo(at(11, 9, 10, 30));
    }

    @Test
    void recordCreatedBeforeOpeningStartsAtOpening() {
        assertThat(calculator.calculateDeadline(at(11, 2, 7, 0), Priority.High, minutes(120)))
                .isEqualTo(at(11, 2, 10, 30));
    }

    @Test
    void recordCreatedAfterClosingStartsNextBusinessDay() {
        assertThat(calculator.calculateDeadline(at(11, 2, 18, 0), Priority.Medium, minutes(480)))
                .isEqualTo(at(11, 3, 16, 30));
    }

    @Test
    void deadlineSkipsPublicHoliday() {
        // Çar 16:30 + 2 saat: 60 dk Çarşamba, Perşembe tatil, kalan 60 dk Cuma 08:30'dan -> Cuma 09:30
        assertThat(calculator.calculateDeadline(at(10, 28, 16, 30), Priority.High, minutes(120)))
                .isEqualTo(at(10, 30, 9, 30));
    }

    @Test
    void twoBusinessDaysForLowPriority() {
        // Low = 2 iş günü = 1080 mesai dakikası: Pzt 08:30 -> Salı 17:30
        assertThat(calculator.calculateDeadline(at(11, 2, 8, 30), Priority.Low, minutes(1080)))
                .isEqualTo(at(11, 3, 17, 30));
    }

    @Test
    void deadlineEndingExactlyAtClosingStaysOnSameDay() {
        assertThat(calculator.calculateDeadline(at(11, 2, 8, 30), Priority.Medium, minutes(540)))
                .isEqualTo(at(11, 2, 17, 30));
    }

    @Test
    void criticalRunsAroundTheClock() {
        // Cumartesi 02:00 + 30 dk, mesai kuralları uygulanmaz
        assertThat(calculator.calculateDeadline(at(11, 7, 2, 0), Priority.Critical, minutes(30)))
                .isEqualTo(at(11, 7, 2, 30));
    }

    // ---- Geçen/kalan çalışma süresi ----

    @Test
    void workingTimeSkipsNightAndWeekend() {
        // Cuma 17:00 -> Pazartesi 09:30: Cuma'dan 30 dk + Pazartesi'den 60 dk
        assertThat(calculator.workingTimeBetween(Priority.High, at(11, 6, 17, 0), at(11, 9, 9, 30)))
                .isEqualTo(minutes(90));
    }

    @Test
    void remainingTimeAcrossNight() {
        assertThat(calculator.remaining(Priority.High, at(11, 2, 17, 0), at(11, 3, 9, 30)))
                .isEqualTo(minutes(90));
    }

    @Test
    void workingTimeIsZeroWhenRangeIsReversed() {
        assertThat(calculator.workingTimeBetween(Priority.High, at(11, 2, 12, 0), at(11, 2, 10, 0)))
                .isZero();
    }

    // ---- Görsel durum ----

    @Test
    void stateIsNormalBeforeEightyPercent() {
        // 2 saatlik SLA, 80 dk geçti (%66)
        assertThat(calculator.stateAt(Priority.High, at(11, 2, 9, 0), at(11, 2, 11, 0), at(11, 2, 10, 20)))
                .isEqualTo(SlaState.Normal);
    }

    @Test
    void stateIsApproachingAtEightyPercent() {
        // 96 dk geçti = tam %80
        assertThat(calculator.stateAt(Priority.High, at(11, 2, 9, 0), at(11, 2, 11, 0), at(11, 2, 10, 36)))
                .isEqualTo(SlaState.Approaching);
    }

    @Test
    void stateIsBreachedAtAndAfterDeadline() {
        assertThat(calculator.stateAt(Priority.High, at(11, 2, 9, 0), at(11, 2, 11, 0), at(11, 2, 11, 0)))
                .isEqualTo(SlaState.Breached);
        assertThat(calculator.stateAt(Priority.High, at(11, 2, 9, 0), at(11, 2, 11, 0), at(11, 2, 15, 0)))
                .isEqualTo(SlaState.Breached);
    }

    @Test
    void stateIgnoresNightTimeForBusinessHoursSla() {
        // Pzt 16:30 -> Salı 15:30 (480 mesai dk). Salı 08:00'da yalnızca 60 mesai dk geçti (%12)
        assertThat(calculator.stateAt(Priority.Medium, at(11, 2, 16, 30), at(11, 3, 15, 30), at(11, 3, 8, 0)))
                .isEqualTo(SlaState.Normal);
    }

    @Test
    void criticalStateUsesWallClockTime() {
        // 30 dk SLA, 24 dk geçti = %80
        assertThat(calculator.stateAt(Priority.Critical, at(11, 7, 2, 0), at(11, 7, 2, 30), at(11, 7, 2, 24)))
                .isEqualTo(SlaState.Approaching);
    }
}
