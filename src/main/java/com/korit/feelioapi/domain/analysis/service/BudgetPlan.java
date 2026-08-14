package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.CategoryBaseline;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 이번 달 카테고리별 예산 배정 규칙 (#196).
 *
 * <p>한 달치 통계를 받아 삭감 계획을 한 번 세워두고, 카테고리마다 {@link #budgetFor} 로 답한다.
 * 예산 화면은 '이번 달 지출이 있는 카테고리'와 '최근에만 지출이 있던 카테고리'를 함께 다루는데,
 * 두 곳에 같은 계산이 복사돼 있어 한쪽만 고쳐지기 쉬웠다. 규칙을 여기 한 곳에 모은다.
 *
 * <h3>바뀐 점</h3>
 * <b>1. 삭감을 늘어난 항목에만 몰아준다.</b>
 * 예전에는 필요 저축액을 전월 변동지출 총액으로 나눠 모든 변동 카테고리를 같은 비율로 깎았다.
 * 전월에 2만 원 쓴 카페까지 20% 깎여 4천 원이 빠지는 식이라, 줄일 여지가 없는 항목만 성가시고
 * 정작 여지가 큰 항목은 덜 깎였다. 이슈가 말한 '평균의 함정'이다.
 * 이제 기준선보다 최근 지출이 <b>늘어난</b> 카테고리만 삭감 대상으로 삼고, 이미 줄인 카테고리는
 * 기준선을 지켜준다 — 줄인 성과를 인정하는 쪽이 다음 달 동기로도 낫다.
 *
 * <p><b>2. 예산이 0원이 되지 않게 한다.</b>
 * 목표 저축액이 전월 변동지출보다 크면 삭감률이 1.0 이 되어 모든 예산이 0원이었다.
 * 화면은 즉시 '예산 초과'가 되고 사용자가 할 수 있는 게 없다. 삭감률에 상한을 두고
 * 카테고리별로도 하한을 둬서, 목표가 아무리 커도 생활비가 사라지지는 않게 한다.
 * 그렇게 해도 못 모으는 목표는 예산을 0으로 만들 게 아니라 따로 알려줄 일이다.
 *
 * <p><b>3. 기준을 전월 한 달에서 최근 3개월 평균으로 옮긴다.</b>
 * 전월 한 달은 흔들림이 너무 컸다. 지난달에 우연히 안 쓴 카테고리는 예산이 0원이 되어 화면에서
 * '측정중'으로 빠졌고(그래서 총예산이 실제 생활비보다 한참 작았다), 여행처럼 몇 달에 한 번
 * 몰아 쓰는 항목은 그 한 달이 그대로 다음 달 목표가 됐다. 판단 근거도 전전월 비교에서
 * '기준선 대비 전월'로 바꾼다 — 같은 구간에서 나온 두 값이라 비교가 성립한다.
 *
 * <p><b>4. 소액 카테고리는 삭감하지 않는다.</b> {@link #REDUCTION_FLOOR_AMOUNT} 참고.
 */
public final class BudgetPlan {

    /**
     * 한 달에 깎을 수 있는 최대 비율.
     *
     * 0.5 는 한 달 만에 절반을 줄이라는 뜻이라 지킬 수 없는 숫자였다. 화면에서도 예산이
     * 실제 씀씀이보다 한참 낮게 잡혀 첫날부터 '초과'가 뜨는 일이 생겼다.
     * 지킬 수 있어야 다음 달에도 볼 마음이 생기므로 30% 로 낮춘다.
     */
    private static final double MAX_REDUCTION_RATIO = 0.3;

    /**
     * 카테고리별 예산 하한 — 기준선 대비.
     * 30% 는 사실상 '못 쓰게 한다'에 가까웠다. 절반은 남겨준다.
     */
    private static final double MIN_BUDGET_RATIO = 0.5;

    /**
     * 삭감 대상이 아닌 카테고리에 주는 여유분.
     *
     * 예전에는 기준 지출을 그대로 예산으로 줬다. 아껴 쓴 항목은 그 빠듯한 수준이
     * 그대로 이번 달 목표가 되어, 조금만 더 써도 바로 초과로 넘어갔다.
     * 평소와 똑같이 쓰는 것이 '초과'는 아니므로 10% 숨통을 얹는다.
     */
    private static final double SLACK_RATIO = 1.1;

    /**
     * 이 금액에 못 미치는 카테고리는 삭감 대상에서 뺀다.
     *
     * <p>비율 삭감은 절대 금액이 작은 카테고리에서 성과 없이 시끄럽기만 하다. 월 1만 원 쓰는
     * 차량·교통을 30% 깎아 봐야 3천 원이 모이는데, 예산이 7천 원이 되는 바람에 택시 한 번에
     * 250% 초과가 떠서 화면의 '초과' 헤드라인을 가져간다. 정작 사용자가 손댈 곳은 그게 아니다.
     * 저축은 여지가 있는 항목에서 모으고, 소액 항목은 평소 수준을 지켜준다.
     */
    private static final long REDUCTION_FLOOR_AMOUNT = 20_000L;

    /** 화면에 천 원 단위로 보이므로 여기서 맞춰 내보낸다. */
    private static final long ROUND_UNIT = 1000L;

    /** 삭감 대상 카테고리. 여기 없으면 기준선을 그대로 예산으로 준다. */
    private final Set<Long> reductionTargets;

    /** 대상들에 적용할 삭감률. 0 이면 깎지 않는다. */
    private final double reductionRatio;

    private BudgetPlan(Set<Long> reductionTargets, double reductionRatio) {
        this.reductionTargets = reductionTargets;
        this.reductionRatio = reductionRatio;
    }

    /**
     * @param baselines        카테고리별 기준선(최근 3개월 평균)과 전월 지출
     * @param requiredSavings  이번 달에 모아야 할 금액(진행 중인 목표들의 월 환산 합)
     */
    public static BudgetPlan of(List<CategoryBaseline> baselines, long requiredSavings) {

        // 고정지출(월세·통신비)은 깎을 수 있는 성질이 아니고, 예산 제외 항목은 애초에 대상이 아니다.
        // 소액 항목도 여기서 함께 걸러 낸다 — 깎아도 모이는 게 없고 화면만 시끄럽다.
        List<CategoryBaseline> reducible = baselines.stream()
                .filter(stat -> !stat.isFixed() && stat.isBudgetable())
                .filter(stat -> stat.baselineAmount() >= REDUCTION_FLOOR_AMOUNT)
                .toList();

        // 기준선보다 전월에 더 쓴 항목 = 늘어나는 추세. 같은 구간에서 나온 두 값이라 비교가 성립한다.
        List<CategoryBaseline> increased = reducible.stream()
                .filter(stat -> stat.lastMonthAmount() > stat.baselineAmount())
                .toList();

        // 늘어난 항목이 하나도 없어도 저축은 해야 하니 삭감 가능한 항목 전체로 되돌린다.
        List<CategoryBaseline> targets = increased.isEmpty() ? reducible : increased;

        long targetTotal = targets.stream().mapToLong(CategoryBaseline::baselineAmount).sum();
        if (targetTotal <= 0 || requiredSavings <= 0) {
            return new BudgetPlan(Set.of(), 0.0);
        }

        double ratio = Math.min(MAX_REDUCTION_RATIO, (double) requiredSavings / targetTotal);
        Set<Long> targetIds = targets.stream().map(CategoryBaseline::categoryId).collect(Collectors.toSet());
        return new BudgetPlan(targetIds, ratio);
    }

    /**
     * 카테고리 하나의 이번 달 예산.
     *
     * @param baselineAmount 기준선(최근 3개월 평균)
     * @param currentAmount  이번 달 지출. 고정지출이 이미 기준선보다 늘었다면 그 값을 써야 예산이 현실과 맞는다
     */
    public long budgetFor(Long categoryId, long baselineAmount, long currentAmount, boolean isFixed) {
        if (isFixed) {
            return Math.max(baselineAmount, currentAmount);
        }
        if (!reductionTargets.contains(categoryId)) {
            return round(Math.round(baselineAmount * SLACK_RATIO));
        }
        long floor = Math.round(baselineAmount * MIN_BUDGET_RATIO);
        long reduced = Math.round(baselineAmount * (1.0 - reductionRatio));
        return round(Math.max(reduced, floor));
    }

    private static long round(long amount) {
        return Math.round((double) amount / ROUND_UNIT) * ROUND_UNIT;
    }
}
