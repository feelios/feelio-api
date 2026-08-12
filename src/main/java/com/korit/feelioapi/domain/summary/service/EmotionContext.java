package com.korit.feelioapi.domain.summary.service;

import com.korit.feelioapi.domain.summary.dto.EmotionSummaryDto;

import java.util.Comparator;
import java.util.List;

/**
 * 말랑이 코멘트를 감정 기준으로 개인화하기 위한 컨텍스트 (A12-3).
 *
 * <p>기준은 <b>당월 대표 감정</b>(기록 횟수 최다) 하나다. 날짜별로 잡지 않는 이유는
 * 홈에서 달력 날짜를 누를 때마다 GPT 를 호출하게 되기 때문이다. 말랑이 색은 날짜를 따라
 * 즉시 바뀌되, 문구는 그날의 한마디로 고정된다.
 *
 * <p>기록이 없으면 {@link #empty()} 를 쓴다. 이때 생성기는 감정을 언급하지 않고
 * 기존 소비 기준 문구로만 답한다 — 없는 감정을 지어내지 않게 하기 위해서다.
 */
public record EmotionContext(
        String name,
        int count,
        long amount,
        Trend trend
) {

    /** 지난달 대비 대표 감정의 흐름. 문구 톤을 고르는 데만 쓴다. */
    public enum Trend {
        /** 지난달에도 같은 감정이 1위였다. */
        REPEATED,
        /** 지난달 1위와 다른 감정이 올라왔다. */
        CHANGED,
        /** 지난달 기록이 없어 비교할 수 없다. */
        UNKNOWN
    }

    private static final EmotionContext EMPTY = new EmotionContext(null, 0, 0L, Trend.UNKNOWN);

    public static EmotionContext empty() {
        return EMPTY;
    }

    /** 감정 기록이 있어 문구에 감정을 넣어도 되는 상태인지. */
    public boolean hasEmotion() {
        return name != null && !name.isBlank() && count > 0;
    }

    /**
     * 당월·전월 감정 집계에서 대표 감정을 뽑는다.
     *
     * <p>횟수가 같으면 지출액이 큰 쪽을 고른다. 둘 다 같으면 감정 이름순으로 고정한다 —
     * 호출할 때마다 대표 감정이 흔들리면 캐시 키가 매번 바뀌어 GPT 를 계속 부르게 된다.
     */
    public static EmotionContext of(List<EmotionSummaryDto> current, List<EmotionSummaryDto> previous) {
        EmotionSummaryDto top = top(current);
        if (top == null) {
            return empty();
        }

        EmotionSummaryDto prevTop = top(previous);
        Trend trend = prevTop == null
                ? Trend.UNKNOWN
                : prevTop.getName().equals(top.getName()) ? Trend.REPEATED : Trend.CHANGED;

        return new EmotionContext(
                top.getName(),
                top.getCount() == null ? 0 : top.getCount(),
                top.getAmount() == null ? 0L : top.getAmount(),
                trend
        );
    }

    private static EmotionSummaryDto top(List<EmotionSummaryDto> emotions) {
        if (emotions == null || emotions.isEmpty()) {
            return null;
        }
        return emotions.stream()
                .filter(dto -> dto != null && dto.getName() != null && dto.getCount() != null && dto.getCount() > 0)
                .max(Comparator
                        .comparingInt(EmotionSummaryDto::getCount)
                        .thenComparingLong(dto -> dto.getAmount() == null ? 0L : dto.getAmount())
                        .thenComparing(EmotionSummaryDto::getName, Comparator.reverseOrder()))
                .orElse(null);
    }
}
