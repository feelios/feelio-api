package com.korit.feelioapi.domain.summary.service;

import com.korit.feelioapi.domain.analysis.service.SpendStatus;
import org.springframework.stereotype.Component;

/**
 * 규칙기반 말랑이 코멘트(무료·즉시·결정적).
 *
 * <p>GPT 를 켜도 실패·타임아웃·빈 응답 시 이 결과가 대신 나가므로 항상 살아 있어야 한다.
 * 그래서 {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty} 를 걸지 않는다.
 *
 * <p>말투는 홈 말랑이답게 부드러운 반말이고, 평가 문장에는 반드시 수치가 하나 들어간다.
 */
@Component
public class RuleMallangCommentGenerator implements MallangCommentGenerator {

    @Override
    public MallangComment generate(SpendStatus status, long expense, long budget, int usageRate, EmotionContext emotion) {
        return new MallangComment(evaluation(status, expense, usageRate), encouragement(status, emotion));
    }

    private String evaluation(SpendStatus status, long expense, int usageRate) {
        return switch (status) {
            // 지출이 0원이면 소진율이 의미가 없어 금액만 말한다.
            case ZERO -> "이번 달은 아직 쓴 게 없어.";
            // 예산을 못 구하는 경우도 근거 수치는 남긴다(지출액).
            case NO_BUDGET -> String.format("이번 달 %,d원 썼어.", expense);
            case OVER -> String.format("이번 달 %,d원 썼어. 예산의 %d%%야.", expense, usageRate);
            case WARNING -> String.format("이번 달 %,d원 썼어. 예산의 %d%%까지 왔어.", expense, usageRate);
            case SAVING -> String.format("이번 달 %,d원 썼어. 예산의 %d%%라 아직 여유 있어.", expense, usageRate);
        };
    }

    /**
     * 독려 문장. 감정 기록이 있으면 감정별 문장을 쓰고, 없으면 소비 상태로만 답한다.
     *
     * <p>GPT 를 꺼도(또는 실패해도) 개인화가 유지되도록 여기에도 감정 분기를 둔다.
     * 지출이 0원이면 감정 기록도 있을 수 없으므로 ZERO 는 상태 문장을 그대로 쓴다.
     */
    private String encouragement(SpendStatus status, EmotionContext emotion) {
        if (status != SpendStatus.ZERO && emotion != null && emotion.hasEmotion()) {
            String byEmotion = byEmotion(emotion.name());
            if (byEmotion != null) {
                return byEmotion;
            }
        }
        return byStatus(status);
    }

    /**
     * 감정 8종별 독려 문장. 감정을 고치려 들지 않고 인정하는 톤으로 쓴다.
     *
     * <p>8종 밖의 이름(커스텀·미래 추가)이 오면 null 을 돌려 상태 문장으로 물러난다.
     */
    private String byEmotion(String name) {
        return switch (name) {
            case "신남" -> "신나는 날이 많았네. 그 기분 그대로 이번 주 한 번만 아껴볼까?";
            case "설렘" -> "설렘이 자주 찾아왔네. 다음 설렘은 목표 저금으로 남겨볼까?";
            case "뿌듯함" -> "뿌듯한 날이 많았어. 그 느낌 목표에도 한 번 담아볼까?";
            case "스트레스" -> "스트레스가 자주 쌓였네. 오늘은 돈 안 드는 걸로 하나 풀어보자.";
            case "외로움" -> "혼자인 날이 많았구나. 다음엔 사람 만나는 데 한 번 써볼까?";
            case "화남" -> "화날 일이 많았네. 결제 전에 딱 10분만 미뤄보자.";
            case "평온" -> "평온한 날이 많았어. 이 흐름이면 예산도 잘 지켜질 거야.";
            case "무덤덤" -> "무덤덤한 날이 많았네. 작은 기록 하나로 흐름을 잡아볼까?";
            default -> null;
        };
    }

    private String byStatus(SpendStatus status) {
        return switch (status) {
            case ZERO -> "첫 기록을 남겨보면 내가 흐름을 읽어줄게.";
            case NO_BUDGET -> "목표를 하나 정해두면 예산도 같이 잡아줄게.";
            case OVER -> "남은 날은 조금만 천천히 가볼까?";
            case WARNING -> "이번 주는 한 번만 아껴봐도 충분해.";
            case SAVING -> "지금 흐름 그대로 가면 돼.";
        };
    }
}
