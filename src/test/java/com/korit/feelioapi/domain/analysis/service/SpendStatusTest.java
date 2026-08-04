package com.korit.feelioapi.domain.analysis.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 예산 소진율 판정. GPT 를 태우지 않고 자바에서 결정하는 부분이라 경계값을 못 박아 둔다. */
class SpendStatusTest {

    @Test
    void 지출이_없으면_ZERO다() {
        assertThat(SpendStatus.of(0L, 1_000_000L)).isEqualTo(SpendStatus.ZERO);
    }

    @Test
    void 예산이_0이면_비율을_낼_수_없어_NO_BUDGET이다() {
        assertThat(SpendStatus.of(500_000L, 0L)).isEqualTo(SpendStatus.NO_BUDGET);
    }

    @Test
    void 소진율_90퍼센트부터_OVER다() {
        assertThat(SpendStatus.of(899_999L, 1_000_000L)).isEqualTo(SpendStatus.WARNING);
        assertThat(SpendStatus.of(900_000L, 1_000_000L)).isEqualTo(SpendStatus.OVER);
    }

    @Test
    void 소진율_70퍼센트부터_WARNING이다() {
        assertThat(SpendStatus.of(699_999L, 1_000_000L)).isEqualTo(SpendStatus.SAVING);
        assertThat(SpendStatus.of(700_000L, 1_000_000L)).isEqualTo(SpendStatus.WARNING);
    }

    @Test
    void 예산을_넘겨도_OVER다() {
        assertThat(SpendStatus.of(2_000_000L, 1_000_000L)).isEqualTo(SpendStatus.OVER);
    }

    @Test
    void 신호등_색이_상태와_맞는다() {
        assertThat(SpendStatus.OVER.signal()).isEqualTo("RED");
        assertThat(SpendStatus.WARNING.signal()).isEqualTo("YELLOW");
        assertThat(SpendStatus.SAVING.signal()).isEqualTo("GREEN");
        assertThat(SpendStatus.ZERO.signal()).isEqualTo("GREEN");
        assertThat(SpendStatus.NO_BUDGET.signal()).isEqualTo("NONE");
    }
}
