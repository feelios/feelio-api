package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.CategoryBaseline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** #196 예산 배정 규칙. 기준선은 최근 3개월 평균이고, 추세 판단은 '기준선 대비 전월'로 한다. */
class BudgetPlanTest {

    private static final long 배달 = 1L;
    private static final long 카페 = 2L;
    private static final long 쇼핑 = 3L;
    private static final long 월세 = 4L;
    private static final long 교통 = 5L;

    /** @param baseline 최근 3개월 평균 @param lastMonth 전월 지출 */
    private static CategoryBaseline 변동(long id, String name, long baseline, long lastMonth) {
        return new CategoryBaseline(id, name, baseline, lastMonth, false, true);
    }

    private static CategoryBaseline 고정(long id, String name, long baseline) {
        return new CategoryBaseline(id, name, baseline, baseline, true, true);
    }

    @Test
    void 기준선보다_전월에_더_쓴_항목에만_삭감이_들어간다() {
        List<CategoryBaseline> baselines = List.of(
                변동(배달, "배달", 300_000L, 350_000L),  // 늘어남
                변동(카페, "카페", 40_000L, 20_000L),    // 줄임 — 성과
                변동(쇼핑, "쇼핑", 100_000L, 120_000L)); // 늘어남

        BudgetPlan plan = BudgetPlan.of(baselines, 80_000L);

        // 줄인 카페는 깎지 않는다. 삭감 대상이 아니므로 기준선에 여유분만 얹힌다.
        assertThat(plan.budgetFor(카페, 40_000L, 0L, false)).isEqualTo(44_000L);

        // 늘어난 둘이 삭감을 나눠 진다. 삭감률 = 80,000 / 400,000 = 20%
        assertThat(plan.budgetFor(배달, 300_000L, 0L, false)).isEqualTo(240_000L);
        assertThat(plan.budgetFor(쇼핑, 100_000L, 0L, false)).isEqualTo(80_000L);
    }

    @Test
    void 소액_카테고리는_삭감_대상에서_빠진다() {
        // 월 1만 원 쓰는 교통을 30% 깎아야 3천 원이 모인다. 그 대가로 예산이 7천 원이 되어
        // 택시 한 번에 250% 초과가 뜨고, 화면의 '초과' 헤드라인을 이 항목이 가져갔다.
        List<CategoryBaseline> baselines = List.of(
                변동(배달, "배달", 300_000L, 400_000L),
                변동(교통, "교통", 10_000L, 15_000L));   // 늘긴 했지만 소액

        BudgetPlan plan = BudgetPlan.of(baselines, 90_000L);

        // 삭감 대상이 아니므로 기준선 + 여유분. 20,000 원 미만은 손대지 않는다.
        assertThat(plan.budgetFor(교통, 10_000L, 0L, false)).isEqualTo(11_000L);
        // 삭감률 분모에서도 빠진다: 90,000 / 300,000 = 30%
        assertThat(plan.budgetFor(배달, 300_000L, 0L, false)).isEqualTo(210_000L);
    }

    @Test
    void 예전_방식이라면_깎였을_소액_항목이_보호된다() {
        // 균등 삭감이었다면 카페도 20% 깎여 32,000 이 됐다. 평균의 함정.
        List<CategoryBaseline> baselines = List.of(
                변동(카페, "카페", 40_000L, 20_000L),
                변동(배달, "배달", 250_000L, 300_000L));

        BudgetPlan plan = BudgetPlan.of(baselines, 84_000L);

        assertThat(plan.budgetFor(카페, 40_000L, 0L, false)).isEqualTo(44_000L);
    }

    @Test
    void 저축액이_지출보다_커도_예산이_0이_되지_않는다() {
        List<CategoryBaseline> baselines = List.of(
                변동(배달, "배달", 300_000L, 300_000L),
                변동(카페, "카페", 20_000L, 20_000L));

        // 필요 저축 500,000 > 기준선 합 320,000 → 예전에는 삭감률 100% 로 전부 0원이었다.
        BudgetPlan plan = BudgetPlan.of(baselines, 500_000L);

        assertThat(plan.budgetFor(배달, 300_000L, 0L, false)).isEqualTo(210_000L); // 상한 30%
        assertThat(plan.budgetFor(카페, 20_000L, 0L, false)).isEqualTo(14_000L);
    }

    @Test
    void 삭감률이_아무리_커도_기준선의_절반은_남는다() {
        List<CategoryBaseline> baselines = List.of(변동(배달, "배달", 100_000L, 100_000L));

        BudgetPlan plan = BudgetPlan.of(baselines, 10_000_000L);

        // 상한 30% 가 먼저 걸리므로 70,000. 하한(50,000)보다 위다.
        assertThat(plan.budgetFor(배달, 100_000L, 0L, false)).isEqualTo(70_000L);
        assertThat(plan.budgetFor(배달, 100_000L, 0L, false)).isGreaterThanOrEqualTo(50_000L);
    }

    @Test
    void 모든_항목이_줄었으면_전체를_대상으로_되돌린다() {
        // 성과가 좋아도 목표 저축은 해야 한다. 대상이 비면 아무 데서도 못 모은다.
        List<CategoryBaseline> baselines = List.of(
                변동(배달, "배달", 400_000L, 300_000L),
                변동(카페, "카페", 100_000L, 90_000L));

        BudgetPlan plan = BudgetPlan.of(baselines, 40_000L);

        assertThat(plan.budgetFor(배달, 400_000L, 0L, false)).isLessThan(400_000L);
    }

    @Test
    void 삭감_대상이_아니면_기준선보다_한_숨_여유를_준다() {
        // 평소와 똑같이 쓰는 것은 '초과'가 아니다. 기준선을 그대로 목표로 주면
        // 아껴 쓴 항목일수록 조금만 더 써도 바로 초과로 넘어갔다.
        List<CategoryBaseline> baselines = List.of(변동(배달, "배달", 300_000L, 300_000L));

        BudgetPlan plan = BudgetPlan.of(baselines, 0L);

        assertThat(plan.budgetFor(배달, 300_000L, 0L, false)).isEqualTo(330_000L);
    }

    @Test
    void 고정지출은_깎지_않고_이번_달_실제액을_따라간다() {
        List<CategoryBaseline> baselines = List.of(고정(월세, "월세", 500_000L), 변동(배달, "배달", 300_000L, 350_000L));

        BudgetPlan plan = BudgetPlan.of(baselines, 200_000L);

        // 월세가 올랐으면 오른 값이 예산이다. 기준선을 고집하면 첫날부터 초과로 뜬다.
        assertThat(plan.budgetFor(월세, 500_000L, 550_000L, true)).isEqualTo(550_000L);
        assertThat(plan.budgetFor(월세, 500_000L, 0L, true)).isEqualTo(500_000L);
    }

    @Test
    void 예산_제외_항목은_삭감_대상에서_빠진다() {
        CategoryBaseline 제외됨 = new CategoryBaseline(쇼핑, "경조사", 200_000L, 250_000L, false, false);
        List<CategoryBaseline> baselines = List.of(변동(배달, "배달", 300_000L, 350_000L), 제외됨);

        BudgetPlan plan = BudgetPlan.of(baselines, 60_000L);

        // 삭감률은 배달 300,000 만 놓고 계산한다(20%). 제외 항목을 분모에 넣으면 삭감이 희석된다.
        assertThat(plan.budgetFor(배달, 300_000L, 0L, false)).isEqualTo(240_000L);
    }

    @Test
    void 몇_달에_한_번_몰아_쓰는_항목은_기준선이_눌러_준다() {
        // 여행: 5월 0 · 6월 0 · 7월 276,300 → 전월 기준이면 예산이 276,300 에 붙어
        // 이번 달에도 그만큼 써도 된다고 말하게 된다. 평균이면 92,100 에서 출발한다.
        List<CategoryBaseline> baselines = List.of(변동(쇼핑, "여행", 92_100L, 276_300L));

        BudgetPlan plan = BudgetPlan.of(baselines, 30_000L);

        // 늘어난 항목이라 삭감까지 들어간다. 삭감률 = 30,000 / 92,100 = 32.6% → 상한 30%
        assertThat(plan.budgetFor(쇼핑, 92_100L, 0L, false)).isEqualTo(64_000L);
    }
}
