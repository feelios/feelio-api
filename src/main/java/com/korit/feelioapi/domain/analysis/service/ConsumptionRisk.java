package com.korit.feelioapi.domain.analysis.service;

/** 프론트 신호등용 소비 위험도. AI 없이 예산 소진율만으로 결정한다. */
public enum ConsumptionRisk {
    RED,
    YELLOW,
    GREEN;

    public static ConsumptionRisk of(long expense, long budget) {
        return switch (SpendStatus.of(expense, budget)) {
            case OVER -> RED;
            case WARNING -> YELLOW;
            case SAVING, ZERO, NO_BUDGET -> GREEN;
        };
    }
}
