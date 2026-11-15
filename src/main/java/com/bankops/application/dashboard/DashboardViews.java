package com.bankops.application.dashboard;

import com.bankops.domain.ErrorCode;
import com.bankops.domain.TransactionType;
import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard uç noktalarının yanıt şekilleri (CLAUDE.md §12.5). Hepsi salt okunur {@code record}'dur.
 * Süreler dakika cinsinden taşınır; birimi (dk / sa) arayüz seçer. Oranlar 0–1 arasında kesirdir.
 */
public final class DashboardViews {

    private DashboardViews() {
    }

    /** KPI kartları. İlk iki sayı seçili döneme, diğerleri o anki duruma aittir. */
    public record Kpis(
            int days,
            long totalCases,
            long openCases,
            long awaitingReview,
            long highPriorityOpen,
            long slaBreached,
            Double avgResolutionMinutes,
            Double previousAvgResolutionMinutes) {
    }

    /** AI analizi kullanılan ve kullanılmayan kapanmış kayıtların ortalama çözüm süresi + öneri kabul oranı. */
    public record AiImpact(
            int days,
            Double withAiMinutes,
            Double withoutAiMinutes,
            long withAiCases,
            long withoutAiCases,
            long analysisCount,
            long usefulCount,
            long ratedCount,
            Double acceptanceRate) {
    }

    public record ErrorTypeCount(ErrorCode errorCode, long count) {
    }

    /** Tek bir iş günü. {@code breachRate}, o gün açılan kayıt yoksa {@code null}'dır. */
    public record SlaTrendPoint(LocalDate day, long total, long breached, Double breachRate) {
    }

    public record SlaTrend(double targetRate, List<SlaTrendPoint> days) {
    }

    /** {@code bucket}: UNDER_1H, H1_TO_4, H4_TO_8, D1_TO_3 (1 gün – 3 gün) ya da OVER_3D. */
    public record AgingBucket(String bucket, long count) {
    }

    public record TransactionTypeShare(TransactionType type, long count, double share) {
    }

    public enum AiQualityStatus {
        Strong,
        Improving,
        WeakKnowledgeBase,
        NoData
    }

    public record AiQualityRow(
            ErrorCode errorCode,
            long analysisCount,
            Double acceptanceRate,
            long verifiedResolutions,
            AiQualityStatus status) {
    }

    public record TeamMember(
            long userId,
            String fullName,
            long openCases,
            long closedToday,
            Double avgResolutionMinutes,
            long slaBreached,
            Double aiUsageRate) {
    }

    /** {@code weekday}: 1 = Pazartesi … 5 = Cuma. {@code averageCases}: son 4 haftanın ortalaması. */
    public record HeatmapCell(int weekday, int hour, double averageCases) {
    }
}
