package com.korit.feelioapi.domain.analysis.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumptionRiskTest {

    @Test
    void 예산_소진율_90퍼센트부터_RED다() {
        assertThat(ConsumptionRisk.of(899_999L, 1_000_000L)).isEqualTo(ConsumptionRisk.YELLOW);
        assertThat(ConsumptionRisk.of(900_000L, 1_000_000L)).isEqualTo(ConsumptionRisk.RED);
    }

    @Test
    void 예산_소진율_70퍼센트부터_YELLOW다() {
        assertThat(ConsumptionRisk.of(699_999L, 1_000_000L)).isEqualTo(ConsumptionRisk.GREEN);
        assertThat(ConsumptionRisk.of(700_000L, 1_000_000L)).isEqualTo(ConsumptionRisk.YELLOW);
    }

    @Test
    void 지출이나_예산이_없으면_GREEN이다() {
        assertThat(ConsumptionRisk.of(0L, 1_000_000L)).isEqualTo(ConsumptionRisk.GREEN);
        assertThat(ConsumptionRisk.of(500_000L, 0L)).isEqualTo(ConsumptionRisk.GREEN);
    }
}
