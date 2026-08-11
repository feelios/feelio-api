package com.korit.feelioapi.domain.universe.service;

/**
 * 시나리오 문장 생성에 필요한 입력 (계약 §9 universe.scenarios).
 *
 * 개월 수는 자바가 계산해 넘긴다 — 모델이 다시 계산하게 두면 화면 숫자와 문장이 어긋난다.
 * monthsToGoal 이 null 이면 도달 불가(월 저축 ≤ 0)를 뜻한다.
 */
public record NarrationContext(
        String goalName,
        /** 줄일 대상 카테고리 이름(소비가 가장 몰린 곳). 거래가 없으면 null. */
        String focusCategoryName,
        Integer currentMonths,
        Integer reducedMonths
) {
}
