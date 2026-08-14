package com.korit.feelioapi.domain.analysis.dto;

/**
 * 예산 산정의 기준선. 최근 3개월치를 한 줄로 요약한 값이다.
 *
 * <p>예전에는 전월 한 달({@link CategoryPrevStat})만 기준으로 삼았다. 한 달은 흔들림이 너무 커서,
 * 지난달에 우연히 안 쓴 카테고리는 예산이 0원이 되고(화면엔 '측정중') 우연히 크게 쓴 달이 끼면
 * 그 값이 그대로 다음 달 목표가 됐다. 여행처럼 몇 달에 한 번 몰아 쓰는 항목에서 특히 심했다.
 *
 * @param baselineAmount  최근 3개월 평균 지출. 기록이 있는 달 수로 나눈다 — 가입 첫 달인 사용자를
 *                        3으로 나누면 실제 씀씀이의 1/3 이 기준이 되어 첫날부터 초과가 뜬다.
 * @param lastMonthAmount 전월 지출. 평균보다 크면 '늘어나는 추세'로 보고 삭감 대상으로 삼는다.
 */
public record CategoryBaseline(
        Long categoryId,
        String categoryName,
        Long baselineAmount,
        Long lastMonthAmount,
        boolean isFixed,
        boolean isBudgetable
) {}
