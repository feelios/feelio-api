package com.korit.feelioapi.domain.summary.service;

/**
 * 말랑이 코멘트의 두 문장. 생성기가 문장만 만들고, 상태 판정은 서비스가 한다.
 *
 * <p>둘 중 하나라도 비면 폴백으로 갈아끼우기 위해 {@link #isUsable()} 로 판단한다.
 */
public record MallangComment(String evaluation, String encouragement) {

    public boolean isUsable() {
        return evaluation != null && !evaluation.isBlank()
                && encouragement != null && !encouragement.isBlank();
    }
}
