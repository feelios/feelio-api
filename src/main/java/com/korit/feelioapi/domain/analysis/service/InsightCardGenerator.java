package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;

import java.util.List;

/**
 * AI 분석 화면의 문장형 카드를 만든다.
 * 구현 교체 지점: 규칙기반(RuleBasedInsightCardGenerator) ↔ GPT(GptInsightCardGenerator).
 *
 * 숫자 판정(예산 소진율·상태)은 여기서 하지 않는다. 호출 측이 계산해 넘기고 이 인터페이스는 문장만 책임진다
 * — 금액·비율을 모델이 다시 계산하게 두면 화면 숫자와 어긋난다.
 */
public interface InsightCardGenerator {

    /**
     * 팩트 리포트 한 문장.
     *
     * @param status       예산 대비 지출 상태(자바에서 판정한 값)
     * @param topCategory  지출이 가장 큰 카테고리명(없으면 null)
     * @param expense      당월 지출액
     */
    String factReport(SpendStatus status, String topCategory, long expense);

    /**
     * 이번 주에 바로 지킬 수 있는 미션 한 줄.
     *
     * @param riskRoute 과소비가 몰린 경로(예: "새벽 · 무덤덤 · 배달")
     */
    String challenge(String riskRoute);

    /**
     * 감정별 3단계 분석(발견 → 의미 → 조언).
     * 입력과 **같은 순서·같은 개수**로 돌려준다. 개수가 안 맞으면 호출 측이 폴백으로 대체한다.
     */
    List<String> emotionAnalyses(List<EmotionStatDto> emotions, String topCategory, String topTimeSlotLabel);
}
