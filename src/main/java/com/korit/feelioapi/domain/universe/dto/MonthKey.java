package com.korit.feelioapi.domain.universe.dto;

/**
 * 최근 거래가 있는 연·월(내부용). 거래가 전혀 없으면 year/month 가 null.
 */
public record MonthKey(
        Integer year,
        Integer month
) {
}
