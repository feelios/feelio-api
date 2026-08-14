package com.korit.feelioapi.domain.universe.service;

/**
 * 시나리오 문장 생성에 필요한 입력 (계약 §9 universe.scenarios).
 *
 * 개월 수와 금액은 자바가 계산해 넘긴다 — 모델이 다시 계산하게 두면 화면 숫자와 문장이 어긋난다.
 * monthsToGoal 이 null 이면 도달 불가(월 저축 ≤ 0)를 뜻한다.
 *
 * 금액을 함께 넘기는 이유: 이 화면은 소비 시뮬레이션이다. 숫자 없이 문장만 만들게 하면
 * 모델이 "목표는 새로운 경험을 줍니다" 같은 소비와 무관한 말로 칸을 채운다.
 */
public record NarrationContext(
        String goalName,
        /** 줄일 대상 카테고리 이름(소비가 가장 몰린 곳). 거래가 없으면 null. */
        String focusCategoryName,
        /** 기준 월 총 지출. 카드에 이미 크게 떠 있는 값이라 문장에서는 되도록 반복하지 않는다. */
        long monthlyExpense,
        /** 목표까지 남은 금액. 카드에 없는 숫자라 문장이 새 정보를 줄 수 있다. */
        long remaining,
        /** 지금 흐름에서 매달 모으는 금액. */
        long currentSaving,
        /** 해당 소비를 줄여 실제로 아낀 금액(= 현행 지출 − 감축 지출). */
        long savedPerMonth,
        /** 줄였을 때 매달 모으게 되는 금액. */
        long reducedSaving,
        Integer currentMonths,
        Integer reducedMonths,
        Integer currentDays,
        Integer reducedDays
) {
}
