package com.korit.feelioapi.domain.analysis.service;

/**
 * 예산 대비 지출 상태. 팩트 리포트 말투와 소비 위험도 신호가 이 값으로 갈린다.
 * 판정은 GPT 가 아니라 자바 계산으로 한다 — 속도·비용 모두 유리하고 결과가 흔들리지 않는다.
 */
public enum SpendStatus {

    /** 지출 0원. */
    ZERO,
    /** 예산 소진율 90% 이상 — 신호등 Red. */
    OVER,
    /** 예산 소진율 70% 이상 90% 미만 — 신호등 Yellow. */
    WARNING,
    /** 예산 소진율 70% 미만 — 신호등 Green. */
    SAVING,
    /** 예산을 산출할 수 없음(활성 목표 없음·전월 기록 없음). 비율 판정 자체가 불가능하다. */
    NO_BUDGET;

    /** 소진율(%) → 상태. 예산이 0 이하면 비율을 정의할 수 없어 NO_BUDGET. */
    public static SpendStatus of(long expense, long budget) {
        if (expense <= 0) {
            return ZERO;
        }
        if (budget <= 0) {
            return NO_BUDGET;
        }
        double usageRate = expense * 100.0 / budget;
        if (usageRate >= 90.0) {
            return OVER;
        }
        if (usageRate >= 70.0) {
            return WARNING;
        }
        return SAVING;
    }

    /** 프론트 신호등 색. type=risk 카드가 이 값으로 불을 켠다. */
    public String signal() {
        return switch (this) {
            case OVER -> "RED";
            case WARNING -> "YELLOW";
            case SAVING, ZERO -> "GREEN";
            case NO_BUDGET -> "NONE";
        };
    }
}
