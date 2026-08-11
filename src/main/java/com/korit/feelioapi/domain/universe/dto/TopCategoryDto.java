package com.korit.feelioapi.domain.universe.dto;

/**
 * 소비가 가장 몰린 카테고리 (API-CONTRACT §9 universe.topCategory). 거래가 없으면 null.
 *
 * 원래는 감정(focusEmotion)이 기준이었다. 화면이 "평온 소비를 줄이면" 처럼 감정을 내세우는데,
 * 사용자는 왜 그 감정이 뽑혔는지 알 수 없었고 줄일 대상이 손에 잡히지도 않았다.
 * 카테고리는 "배달을 줄이면" 처럼 행동으로 바로 옮길 수 있어 시나리오의 근거가 분명해진다.
 *
 * categories 테이블에는 색 컬럼이 없다. 화면 강조색은 프론트가 정한다.
 */
public record TopCategoryDto(
        Long categoryId,
        String name,
        Long monthlyAmount
) {
}
