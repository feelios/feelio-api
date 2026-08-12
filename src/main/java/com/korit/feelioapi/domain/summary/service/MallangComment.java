package com.korit.feelioapi.domain.summary.service;

/**
 * 말랑이 코멘트의 세 문장. 생성기가 문장만 만들고, 상태 판정은 서비스가 한다.
 *
 * <p>홈 말풍선이 세 칸이라 문장도 셋이다(A12-3).
 * empathy 는 감정 공감, evaluation 은 현황 평가, encouragement 는 다음 행동 독려다.
 *
 * <p>셋 중 하나라도 비면 폴백으로 갈아끼우기 위해 {@link #isUsable()} 로 판단한다.
 */
public record MallangComment(String empathy, String evaluation, String encouragement) {

    public boolean isUsable() {
        return notBlank(empathy) && notBlank(evaluation) && notBlank(encouragement);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
