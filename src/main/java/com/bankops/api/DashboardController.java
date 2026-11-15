package com.bankops.api;

import com.bankops.application.dashboard.DashboardService;
import com.bankops.application.dashboard.DashboardViews.AgingBucket;
import com.bankops.application.dashboard.DashboardViews.AiImpact;
import com.bankops.application.dashboard.DashboardViews.AiQualityRow;
import com.bankops.application.dashboard.DashboardViews.ErrorTypeCount;
import com.bankops.application.dashboard.DashboardViews.HeatmapCell;
import com.bankops.application.dashboard.DashboardViews.Kpis;
import com.bankops.application.dashboard.DashboardViews.SlaTrend;
import com.bankops.application.dashboard.DashboardViews.TeamMember;
import com.bankops.application.dashboard.DashboardViews.TransactionTypeShare;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yönetici dashboard'u (CLAUDE.md §11, §12.5). Yetki {@code SecurityConfig}'te: Manager ve TeamLead.
 * {@code days} yalnızca 7, 30 veya 90 olabilir (varsayılan 30); dönemden bağımsız paneller bu parametreyi almaz.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
class DashboardController {

    private final DashboardService service;

    @GetMapping("/kpis")
    Kpis kpis(@RequestParam(required = false) Integer days) {
        return service.kpis(days);
    }

    @GetMapping("/ai-impact")
    AiImpact aiImpact(@RequestParam(required = false) Integer days) {
        return service.aiImpact(days);
    }

    @GetMapping("/error-types")
    List<ErrorTypeCount> errorTypes(@RequestParam(required = false) Integer days) {
        return service.errorTypes(days);
    }

    @GetMapping("/sla-trend")
    SlaTrend slaTrend() {
        return service.slaTrend();
    }

    @GetMapping("/aging")
    List<AgingBucket> aging() {
        return service.aging();
    }

    @GetMapping("/transaction-types")
    List<TransactionTypeShare> transactionTypes(@RequestParam(required = false) Integer days) {
        return service.transactionTypes(days);
    }

    @GetMapping("/ai-quality")
    List<AiQualityRow> aiQuality(@RequestParam(required = false) Integer days) {
        return service.aiQuality(days);
    }

    @GetMapping("/team")
    List<TeamMember> team(@RequestParam(required = false) Integer days) {
        return service.team(days);
    }

    @GetMapping("/hourly-heatmap")
    List<HeatmapCell> hourlyHeatmap() {
        return service.hourlyHeatmap();
    }
}
