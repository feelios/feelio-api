package com.korit.feelioapi.domain.summary.service;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * GPT 응답 파싱만 검증한다. 외부 호출은 태우지 않는다.
 *
 * <p>여기서 null 이 나오면 서비스가 규칙기반 문장으로 대체하므로,
 * "형식이 어긋나면 반드시 null" 이 지켜져야 홈 화면에 빈 코멘트가 나가지 않는다.
 *
 * <p>말풍선이 세 칸이라 문장도 셋이다(A12-3). 개수가 정확히 3이 아니면 폴백으로 넘긴다 —
 * 두 개만 오면 세 번째 말풍선이 빈 채로 뜨기 때문이다.
 */
class GptMallangCommentGeneratorTest {

    private final GptMallangCommentGenerator generator =
            new GptMallangCommentGenerator(mock(OpenAIClient.class), "gpt-4o-mini", 3L, new com.korit.feelioapi.global.ai.AiCallGuard(3, 60));

    @Test
    void 구분자로_공감_평가_독려를_가른다() {
        MallangComment comment = generator.parse(
                "설렘이 가득한 달이었네.|이번 달 320,000원 썼어. 예산의 78%야.|설레는 날 하나를 목표 저금으로 남겨볼까?");

        assertThat(comment).isNotNull();
        assertThat(comment.empathy()).isEqualTo("설렘이 가득한 달이었네.");
        assertThat(comment.evaluation()).isEqualTo("이번 달 320,000원 썼어. 예산의 78%야.");
        assertThat(comment.encouragement()).isEqualTo("설레는 날 하나를 목표 저금으로 남겨볼까?");
    }

    @Test
    void 구분자_주변_공백을_털어낸다() {
        MallangComment comment = generator.parse("  공감 문장 |  평가 문장   |   독려 문장  ");

        assertThat(comment.empathy()).isEqualTo("공감 문장");
        assertThat(comment.evaluation()).isEqualTo("평가 문장");
        assertThat(comment.encouragement()).isEqualTo("독려 문장");
    }

    @Test
    void 구분자가_없으면_null_을_반환해_폴백에_넘긴다() {
        assertThat(generator.parse("구분자가 없는 한 문장짜리 응답")).isNull();
    }

    @Test
    void 문장이_두_개뿐이면_null_을_반환한다() {
        assertThat(generator.parse("평가 문장|독려 문장")).isNull();
    }

    @Test
    void 문장이_네_개면_null_을_반환한다() {
        assertThat(generator.parse("하나|둘|셋|넷")).isNull();
    }

    @Test
    void 빈_응답은_null_을_반환한다() {
        assertThat(generator.parse(null)).isNull();
        assertThat(generator.parse("")).isNull();
        assertThat(generator.parse("   ")).isNull();
    }

    @Test
    void 한_문장이라도_비면_null_을_반환한다() {
        assertThat(generator.parse("공감|평가|")).isNull();
        assertThat(generator.parse("|평가|독려")).isNull();
        assertThat(generator.parse("공감|   |독려")).isNull();
    }

    @Test
    void 최대_길이를_넘는_문장은_잘라낸다() {
        String longSentence = "가".repeat(80);

        MallangComment comment = generator.parse(longSentence + "|" + longSentence + "|" + longSentence);

        assertThat(comment.empathy()).hasSize(60);
        assertThat(comment.evaluation()).hasSize(60);
        assertThat(comment.encouragement()).hasSize(60);
    }
}
