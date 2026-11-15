package com.bankops.application.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bankops.application.dashboard.DashboardViews.AiQualityStatus;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    @Test
    void qualityStatusUsesTheThresholdsFromTheGuide() {
        assertThat(DashboardService.qualityStatus(0.91)).isEqualTo(AiQualityStatus.Strong);
        assertThat(DashboardService.qualityStatus(0.75)).isEqualTo(AiQualityStatus.Strong);
        assertThat(DashboardService.qualityStatus(0.7499)).isEqualTo(AiQualityStatus.Improving);
        assertThat(DashboardService.qualityStatus(0.50)).isEqualTo(AiQualityStatus.Improving);
        assertThat(DashboardService.qualityStatus(0.4999)).isEqualTo(AiQualityStatus.WeakKnowledgeBase);
        assertThat(DashboardService.qualityStatus(null)).isEqualTo(AiQualityStatus.NoData);
    }

    @Test
    void periodDefaultsTo30AndAcceptsOnlyTheAllowedValues() {
        assertThat(DashboardService.period(null)).isEqualTo(30);
        assertThat(DashboardService.period(7)).isEqualTo(7);
        assertThat(DashboardService.period(90)).isEqualTo(90);
        assertThatThrownBy(() -> DashboardService.period(14)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DashboardService.period(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
