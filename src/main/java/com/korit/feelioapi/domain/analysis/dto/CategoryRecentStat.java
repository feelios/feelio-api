package com.korit.feelioapi.domain.analysis.dto;

/**
 * 최근 N개월 구간을 카테고리별로 집계한 <b>원본 행</b>. 매퍼가 그대로 채워 준다.
 *
 * <p>평균으로 나누기 전 값이라 {@link CategoryBaseline} 과 나눠 둔다. 한 레코드에 합계와 평균을
 * 번갈아 담으면, 나누기 전인지 후인지가 호출 순서에만 달려 있어 읽는 쪽이 알 방법이 없다.
 *
 * @param windowAmount    구간 전체 지출 합계
 * @param lastMonthAmount 그중 전월 한 달치
 */
public record CategoryRecentStat(
        Long categoryId,
        String categoryName,
        Long windowAmount,
        Long lastMonthAmount,
        boolean isFixed,
        boolean isBudgetable
) {
    /** 기록이 있는 달 수로 나눠 기준선을 만든다. */
    public CategoryBaseline toBaseline(int activeMonths) {
        long divisor = Math.max(1, activeMonths);
        return new CategoryBaseline(
                categoryId,
                categoryName,
                Math.round((double) windowAmount / divisor),
                lastMonthAmount,
                isFixed,
                isBudgetable);
    }
}
