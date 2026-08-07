package com.korit.feelioapi.global.ai;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #197 AI 서킷 브레이커. */
class AiCallGuardTest {

    /** 실패 3회면 60초 열림 — 운영 기본값. */
    private AiCallGuard guard() {
        return new AiCallGuard(3, 60);
    }

    @Test
    void 정상일_때는_그대로_통과시킨다() {
        assertThat(guard().call("테스트", () -> "결과")).isEqualTo("결과");
    }

    @Test
    void 실패는_그대로_던져_호출부의_폴백이_받게_한다() {
        assertThatThrownBy(() -> guard().call("테스트", () -> { throw new IllegalStateException("모델 오류"); }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("모델 오류");
    }

    @Test
    void 연속_실패가_기준에_닿으면_더는_호출하지_않는다() {
        AiCallGuard guard = guard();
        AtomicInteger 호출횟수 = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> guard.call("테스트", () -> {
                호출횟수.incrementAndGet();
                throw new IllegalStateException("타임아웃");
            })).isInstanceOf(IllegalStateException.class);
        }
        assertThat(호출횟수).hasValue(3);

        // 서킷이 열렸다. 이제는 시도조차 하지 않고 즉시 떨어진다 — 사용자가 타임아웃을 기다리지 않는다.
        assertThatThrownBy(() -> guard.call("테스트", () -> {
            호출횟수.incrementAndGet();
            return "여기까지 오면 안 된다";
        })).isInstanceOf(AiUnavailableException.class);

        assertThat(호출횟수).hasValue(3);
    }

    @Test
    void 열린_시간이_지나면_다시_시도한다() {
        AiCallGuard guard = new AiCallGuard(1, 0); // 실패 1회에 열되 곧바로 만료
        AtomicInteger 호출횟수 = new AtomicInteger();

        assertThatThrownBy(() -> guard.call("테스트", () -> {
            호출횟수.incrementAndGet();
            throw new IllegalStateException("타임아웃");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(guard.call("테스트", () -> {
            호출횟수.incrementAndGet();
            return "복구";
        })).isEqualTo("복구");
        assertThat(호출횟수).hasValue(2);
    }

    @Test
    void 중간에_한_번_성공하면_실패_누적이_초기화된다() {
        AiCallGuard guard = guard();

        // 실패 2회 — 아직 기준(3)에 못 미친다.
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> guard.call("테스트", () -> { throw new IllegalStateException("일시 오류"); }))
                    .isInstanceOf(IllegalStateException.class);
        }
        guard.call("테스트", () -> "성공");

        // 초기화됐으므로 다시 2회 실패해도 서킷은 닫혀 있다.
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> guard.call("테스트", () -> { throw new IllegalStateException("일시 오류"); }))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(guard.call("테스트", () -> "여전히 통과")).isEqualTo("여전히 통과");
    }
}
