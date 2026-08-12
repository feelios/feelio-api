package com.korit.feelioapi.domain.summary.dto;

/**
 * 홈 말랑이 코멘트 (A8-3).
 *
 * <p>홈 말풍선 세 칸에 그대로 들어간다. empathy 는 감정 공감, evaluation 은 현황 평가,
 * encouragement 는 다음 행동 독려다.
 * 셋 다 AI 실패·비활성화 시에도 규칙기반 문장으로 채워지며 null 이 되지 않는다.
 * status 는 자바 계산 결과라 AI 응답과 무관하게 항상 신뢰할 수 있다.
 *
 * <p>emotion 은 문구의 기준이 된 당월 대표 감정이다(A12-3). 감정 기록이 없으면 null 이며,
 * 이때 문구는 소비 기준으로만 만들어진다. 프론트가 말랑이 색·표정과 문구를 맞추는 데 쓴다.
 */
public record MallangCommentResponse(
        String empathy,
        String evaluation,
        String encouragement,
        String status,
        String emotion
) {
}
