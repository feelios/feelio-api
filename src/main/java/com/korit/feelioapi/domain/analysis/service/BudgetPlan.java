package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.CategoryPrevStat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 이번 달 카테고리별 예산 배정 규칙 (#196).
 *
 * <p>한 달치 통계를 받아 삭감 계획을 한 번 세워두고, 카테고리마다 {@link #budgetFor} 로 답한다.
 * 예산 화면은 '이번 달 지출이 있는 카테고리'와 '전월에만 있던 카테고리'를 따로 순회하는데,
 * 두 곳에 같은 계산이 복사돼 있어 한쪽만 고쳐지기 쉬웠다. 규칙을 여기 한 곳에 모은다.
 *
 * <h3>바뀐 점</h3>
 * <b>1. 삭감을 늘어난 항목에만 몰아준다.</b>
 * 예전에는 필요 저축액을 전월 변동지출 총액으로 나눠 모든 변동 카테고리를 같은 비율로 깎았다.
 * 전월에 2만 원 쓴 카페까지 20% 깎여 4천 원이 빠지는 식이라, 줄일 여지가 없는 항목만 성가시고
 * 정작 여지가 큰 항목은 덜 깎였다. 이슈가 말한 '평균의 함정'이다.
 * 이제 전전월 대비 지출이 <b>늘어난</b> 카테고리만 삭감 대상으로 삼고, 이미 줄인 카테고리는
 * 전월 수준을 지켜준다 — 줄인 성과를 인정하는 쪽이 다음 달 동기로도 낫다.
 *
 * <p><b>2. 예산이 0원이 되지 않게 한다.</b>
 * 목표 저축액이 전월 변동지출보다 크면 삭감률이 1.0 이 되어 모든 예산이 0원이었다.
 * 화면은 즉시 '예산 초과'가 되고 사용자가 할 수 있는 게 없다. 삭감률에 상한을 두고
 * 카테고리별로도 하한을 둬서, 목표가 아무리 커도 생활비가 사라지지는 않게 한다.
 * 그렇게 해도 못 모으는 목표는 예산을 0으로 만들 게 아니라 따로 알려줄 일이다.
 */
public final class BudgetPlan {

    /** 한 달에 깎을 수 있는 최대 비율. 이보다 세게 깎으면 지킬 수 없는 예산이 된다. */
    private static final double MAX_REDUCTION_RATIO = 0.5;

    /** 카테고리별 예산 하한 — 전월 지출의 30%. 어떤 항목도 0원으로 내려보내지 않는다. */
    private static final double MIN_BUDGET_RATIO = 0.3;

    /** 화면에 천 원 단위로 보이므로 여기서 맞춰 내보낸다. */
    private static final long ROUND_UNIT = 1000L;

    /** 삭감 대상 카테고리. 여기 없으면 전월 수준을 그대로 예산으로 준다. */
    private final Set<Long> reductionTargets;

    /** 대상들에 적용할 삭감률. 0 이면 깎지 않는다. */
    private final double reductionRatio;

    private BudgetPlan(Set<Long> reductionTargets, double reductionRatio) {
        this.reductionTargets = reductionTargets;
        this.reductionRatio = reductionRatio;
    }

    /**
     * @param prevStats        전월 카테고리별 지출
     * @param prevPrevStats    전전월 카테고리별 지출. 비어 있으면 증감을 알 수 없어 변동 카테고리 전체를 대상으로 삼는다
     * @param requiredSavings  이번 달에 모아야 할 금액(진행 중인 목표들의 월 환산 합)
     */
    public static BudgetPlan of(List<CategoryPrevStat> prevStats,
                                List<CategoryPrevStat> prevPrevStats,
                                long requiredSavings) {

        // 고정지출(월세·통신비)은 깎을 수 있는 성질이 아니고, 예산 제외 항목은 애초에 대상이 아니다.
        List<CategoryPrevStat> reducible = prevStats.stream()
                .filter(stat -> !stat.isFixed() && stat.isBudgetable())
                .toList();

        Map<Long, Long> prevPrevAmounts = prevPrevStats.stream()
                .collect(Collectors.toMap(CategoryPrevStat::categoryId, CategoryPrevStat::prevAmount, Long::sum));

        // 전전월 대비 늘어난 항목만 고른다. 전전월에 없던(새로 생긴) 카테고리도 늘어난 쪽이다.
        List<CategoryPrevStat> increased = reducible.stream()
                .filter(stat -> stat.prevAmount() > prevPrevAmounts.getOrDefault(stat.categoryId(), 0L))
                .toList();

        // 전전월 기록이 아예 없으면(가입 두 달째) 증감을 판단할 근거가 없다.
        // 늘어난 항목이 하나도 없을 때도 마찬가지 — 그래도 저축은 해야 하니 변동 항목 전체로 되돌린다.
        List<CategoryPrevStat> targets = (prevPrevAmounts.isEmpty() || increased.isEmpty()) ? reducible : increased;

        long targetTotal = targets.stream().mapToLong(CategoryPrevStat::prevAmount).sum();
        if (targetTotal <= 0 || requiredSavings <= 0) {
            return new BudgetPlan(Set.of(), 0.0);
        }

        double ratio = Math.min(MAX_REDUCTION_RATIO, (double) requiredSavings / targetTotal);
        Set<Long> targetIds = targets.stream().map(CategoryPrevStat::categoryId).collect(Collectors.toSet());
        return new BudgetPlan(targetIds, ratio);
    }

    /**
     * 카테고리 하나의 이번 달 예산.
     *
     * @param currentAmount 이번 달 지출. 고정지출이 이미 전월보다 늘었다면 그 값을 써야 예산이 현실과 맞는다
     */
    public long budgetFor(Long categoryId, long prevAmount, long currentAmount, boolean isFixed) {
        if (isFixed) {
            return Math.max(prevAmount, currentAmount);
        }
        if (!reductionTargets.contains(categoryId)) {
            return round(prevAmount);
        }
        long floor = Math.round(prevAmount * MIN_BUDGET_RATIO);
        long reduced = Math.round(prevAmount * (1.0 - reductionRatio));
        return round(Math.max(reduced, floor));
    }

    private static long round(long amount) {
        return Math.round((double) amount / ROUND_UNIT) * ROUND_UNIT;
    }
}
