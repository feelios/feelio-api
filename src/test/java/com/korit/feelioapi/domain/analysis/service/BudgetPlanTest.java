package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.CategoryPrevStat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** #196 예산 배정 규칙. */
class BudgetPlanTest {

    private static final long 배달 = 1L;
    private static final long 카페 = 2L;
    private static final long 쇼핑 = 3L;
    private static final long 월세 = 4L;

    private static CategoryPrevStat 변동(long id, String name, long amount) {
        return new CategoryPrevStat(id, name, amount, false, true);
    }

    private static CategoryPrevStat 고정(long id, String name, long amount) {
        return new CategoryPrevStat(id, name, amount, true, true);
    }

    @Test
    void 전전월보다_늘어난_항목에만_삭감이_들어간다() {
        List<CategoryPrevStat> prevPrev = List.of(
                변동(배달, "배달", 250_000L),
                변동(카페, "카페", 40_000L),
                변동(쇼핑, "쇼핑", 90_000L));
        List<CategoryPrevStat> prev = List.of(
                변동(배달, "배달", 300_000L),   // 늘어남
                변동(카페, "카페", 20_000L),    // 줄임 — 성과
                변동(쇼핑, "쇼핑", 100_000L));  // 늘어남

        BudgetPlan plan = BudgetPlan.of(prev, prevPrev, 80_000L);

        // 줄인 카페는 깎지 않는다. 삭감 대상이 아니므로 전월액에 여유분만 얹힌다.
        assertThat(plan.budgetFor(카페, 20_000L, 0L, false)).isEqualTo(22_000L);

        // 늘어난 둘이 삭감을 나눠 진다. 삭감률 = 80,000 / 400,000 = 20%
        assertThat(plan.budgetFor(배달, 300_000L, 0L, false)).isEqualTo(240_000L);
        assertThat(plan.budgetFor(쇼핑, 100_000L, 0L, false)).isEqualTo(80_000L);
    }

    @Test
    void 예전_방식이라면_깎였을_소액_항목이_보호된다() {
        // 균등 삭감이었다면 카페도 20% 깎여 16,000 이 됐다. 평균의 함정.
        List<CategoryPrevStat> prevPrev = List.of(변동(카페, "카페", 40_000L), 변동(배달, "배달", 250_000L));
        List<CategoryPrevStat> prev = List.of(변동(카페, "카페", 20_000L), 변동(배달, "배달", 300_000L));

        BudgetPlan plan = BudgetPlan.of(prev, prevPrev, 84_000L);

        // 삭감 대상이 아니므로 깎이지 않는다(전월액 + 여유분).
        assertThat(plan.budgetFor(카페, 20_000L, 0L, false)).isEqualTo(22_000L);
    }

    @Test
    void 저축액이_지출보다_커도_예산이_0이_되지_않는다() {
        List<CategoryPrevStat> prev = List.of(
                변동(배달, "배달", 300_000L),
                변동(카페, "카페", 20_000L));

        // 필요 저축 500,000 > 전월 변동지출 320,000 → 예전에는 삭감률 100% 로 전부 0원이었다.
        BudgetPlan plan = BudgetPlan.of(prev, List.of(), 500_000L);

        assertThat(plan.budgetFor(배달, 300_000L, 0L, false)).isEqualTo(210_000L); // 상한 30%
        assertThat(plan.budgetFor(카페, 20_000L, 0L, false)).isEqualTo(14_000L);
    }

    @Test
    void 삭감률이_아무리_커도_전월의_절반은_남는다() {
        List<CategoryPrevStat> prev = List.of(변동(배달, "배달", 100_000L));

        BudgetPlan plan = BudgetPlan.of(prev, List.of(), 10_000_000L);

        // 상한 30% 가 먼저 걸리므로 70,000. 하한(50,000)보다 위다.
        assertThat(plan.budgetFor(배달, 100_000L, 0L, false)).isEqualTo(70_000L);
        assertThat(plan.budgetFor(배달, 100_000L, 0L, false)).isGreaterThanOrEqualTo(50_000L);
    }

    @Test
    void 전전월_기록이_없으면_변동_항목_전체를_대상으로_삼는다() {
        List<CategoryPrevStat> prev = List.of(
                변동(배달, "배달", 300_000L),
                변동(카페, "카페", 100_000L));

        BudgetPlan plan = BudgetPlan.of(prev, List.of(), 80_000L);

        // 삭감률 = 80,000 / 400,000 = 20%. 가입 두 달째라도 예산이 나온다.
        assertThat(plan.budgetFor(배달, 300_000L, 0L, false)).isEqualTo(240_000L);
        assertThat(plan.budgetFor(카페, 100_000L, 0L, false)).isEqualTo(80_000L);
    }

    @Test
    void 모든_항목이_줄었으면_전체를_대상으로_되돌린다() {
        // 성과가 좋아도 목표 저축은 해야 한다. 대상이 비면 아무 데서도 못 모은다.
        List<CategoryPrevStat> prevPrev = List.of(변동(배달, "배달", 400_000L), 변동(카페, "카페", 100_000L));
        List<CategoryPrevStat> prev = List.of(변동(배달, "배달", 300_000L), 변동(카페, "카페", 100_000L));

        BudgetPlan plan = BudgetPlan.of(prev, prevPrev, 40_000L);

        assertThat(plan.budgetFor(배달, 300_000L, 0L, false)).isLessThan(300_000L);
    }

    @Test
    void 삭감_대상이_아니면_전월보다_한_숨_여유를_준다() {
        // 전월과 똑같이 쓰는 것은 '초과'가 아니다. 전월액을 그대로 목표로 주면
        // 지난달에 아껴 쓴 항목일수록 조금만 더 써도 바로 초과로 넘어갔다.
        List<CategoryPrevStat> prev = List.of(변동(배달, "배달", 300_000L));

        BudgetPlan plan = BudgetPlan.of(prev, List.of(), 0L);

        assertThat(plan.budgetFor(배달, 300_000L, 0L, false)).isEqualTo(330_000L);
    }

    @Test
    void 고정지출은_깎지_않고_이번_달_실제액을_따라간다() {
        List<CategoryPrevStat> prev = List.of(고정(월세, "월세", 500_000L), 변동(배달, "배달", 300_000L));

        BudgetPlan plan = BudgetPlan.of(prev, List.of(), 200_000L);

        // 월세가 올랐으면 오른 값이 예산이다. 전월 값을 고집하면 첫날부터 초과로 뜬다.
        assertThat(plan.budgetFor(월세, 500_000L, 550_000L, true)).isEqualTo(550_000L);
        assertThat(plan.budgetFor(월세, 500_000L, 0L, true)).isEqualTo(500_000L);
    }

    @Test
    void 예산_제외_항목은_삭감_대상에서_빠진다() {
        CategoryPrevStat 제외됨 = new CategoryPrevStat(쇼핑, "경조사", 200_000L, false, false);
        List<CategoryPrevStat> prev = List.of(변동(배달, "배달", 300_000L), 제외됨);

        BudgetPlan plan = BudgetPlan.of(prev, List.of(), 60_000L);

        // 삭감률은 배달 300,000 만 놓고 계산한다(20%). 제외 항목을 분모에 넣으면 삭감이 희석된다.
        assertThat(plan.budgetFor(배달, 300_000L, 0L, false)).isEqualTo(240_000L);
    }
}
