package com.korit.feelioapi.domain.analysis.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 소비 위험도 코멘트의 규칙기반 폴백. GPT 를 꺼도(provider=rule) 실패해도 이 문장이 나가므로
 * 카드가 비지 않는 것과, '남은 금액 되풀이'로 돌아가지 않는 것을 여기서 지킨다.
 */
class ConsumptionRiskCommentTest {

    private final RuleBasedInsightCardGenerator generator = new RuleBasedInsightCardGenerator();

    @Test
    void 카테고리와_감정이_있으면_둘을_이어서_말한다() {
        String comment = generator.riskComment(SpendStatus.WARNING, 70, "여행", "설렘");

        assertThat(comment).contains("설렘").contains("여행");
    }

    @Test
    void 등급마다_다른_문장을_준다() {
        String over = generator.riskComment(SpendStatus.OVER, 95, "여행", "설렘");
        String warning = generator.riskComment(SpendStatus.WARNING, 75, "여행", "설렘");
        String saving = generator.riskComment(SpendStatus.SAVING, 40, "여행", "설렘");

        assertThat(over).isNotEqualTo(warning).isNotEqualTo(saving);
        assertThat(warning).isNotEqualTo(saving);
    }

    @Test
    void 감정이_없으면_카테고리만으로_말한다() {
        String comment = generator.riskComment(SpendStatus.WARNING, 75, "여행", null);

        assertThat(comment).contains("여행");
    }

    @Test
    void 카테고리도_감정도_없으면_소진율로_말한다() {
        String comment = generator.riskComment(SpendStatus.WARNING, 75, null, null);

        assertThat(comment).contains("75%");
    }

    @Test
    void 판정_불가와_지출_0원은_따로_안내한다() {
        assertThat(generator.riskComment(SpendStatus.NO_BUDGET, 0, "여행", "설렘")).contains("목표");
        assertThat(generator.riskComment(SpendStatus.ZERO, 0, null, null)).contains("소비가 없어요");
    }

    @Test
    void 남은_금액이나_초과액을_되풀이하지_않는다() {
        // 옆 칸의 등급이 이미 말한 것이라 여기서 반복하면 카드가 같은 말을 두 번 한다.
        for (SpendStatus status : SpendStatus.values()) {
            assertThat(generator.riskComment(status, 75, "여행", "설렘"))
                    .doesNotContain("남았어요")
                    .doesNotContain("넘게 썼어요");
        }
    }
}
