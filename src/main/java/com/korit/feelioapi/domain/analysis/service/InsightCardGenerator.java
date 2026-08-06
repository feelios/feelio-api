package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;

import java.util.List;

/**
 * 감정 카드 뒷면 문구를 만든다.
 * 구현 교체 지점: 규칙기반(RuleBasedInsightCardGenerator) ↔ GPT(GptInsightCardGenerator).
 *
 * 숫자 판정(예산 소진율·상태)은 여기서 하지 않는다. 호출 측이 계산해 넘기고 이 인터페이스는 문장만 책임진다
 * — 금액·비율을 모델이 다시 계산하게 두면 화면 숫자와 어긋난다.
 *
 * 팩트 리포트·챌린지 문장은 여기 있지 않다. {@link FactReportService}·{@link ChallengeService} 가 정본이며
 * {@link AiQuickInsightAssembler} 도 그 둘을 호출한다. 예전에는 이 인터페이스에도 같은 메서드가 있었으나
 * 프론트가 ai-report 값으로 덮어써서 화면에 나오지 않는 죽은 경로였다(#180).
 */
public interface InsightCardGenerator {

    /**
     * 감정별 3단계 분석(발견 → 의미 → 조언).
     * 입력과 **같은 순서·같은 개수**로 돌려준다. 개수가 안 맞으면 호출 측이 폴백으로 대체한다.
     */
    List<String> emotionAnalyses(List<EmotionStatDto> emotions, String topCategory, String topTimeSlotLabel);
}
