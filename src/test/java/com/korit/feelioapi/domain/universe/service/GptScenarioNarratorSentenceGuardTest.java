package com.korit.feelioapi.domain.universe.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모델이 형식 예시의 자리표시자를 그대로 돌려준 적이 있다. 형식이 맞아서 파싱도 개수 검사도
 * 통과했고, 카드에 "CURRENT 1" 이 그대로 떴다. 그 값을 걸러내는 판단만 따로 본다.
 */
class GptScenarioNarratorSentenceGuardTest {

    /** 생성자 인자는 이 판단에 쓰이지 않는다. */
    private final GptScenarioNarrator narrator =
            new GptScenarioNarrator(null, null, "gpt-test", 4, null);

    @Test
    void 자리표시자는_문장이_아니다() {
        assertThat(narrator.looksLikeSentence("CURRENT 1")).isFalse();
        assertThat(narrator.looksLikeSentence("REDUCED 3")).isFalse();
    }

    @Test
    void 한글이_없거나_너무_짧으면_문장이_아니다() {
        assertThat(narrator.looksLikeSentence("")).isFalse();
        assertThat(narrator.looksLikeSentence(null)).isFalse();
        assertThat(narrator.looksLikeSentence("...")).isFalse();
        assertThat(narrator.looksLikeSentence("12345678")).isFalse();
        assertThat(narrator.looksLikeSentence("좋아요")).isFalse();
    }

    @Test
    void 사람이_읽을_문장은_통과한다() {
        assertThat(narrator.looksLikeSentence("지금 속도라면 약 20개월 뒤 제주도 여행에 닿아요.")).isTrue();
        assertThat(narrator.looksLikeSentence("이번 달 지출 2,000,000원 기준이에요.")).isTrue();
    }
}
