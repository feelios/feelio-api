package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 규칙기반 카드 문구(무료·즉시·결정적). GPT 를 켜도 실패·타임아웃 시 이 결과가 대신 나가므로 항상 살아 있어야 한다.
 * 페르소나 말투는 GPT 쪽이 담당하고, 여기서는 같은 뜻을 담백하게 쓴다 — 폴백이 튀면 오히려 부자연스럽다.
 *
 * factReport·challenge 는 인터페이스 메서드가 아니다(#180). {@link FactReportService}·{@link ChallengeService}
 * 가 이 빈을 구체 타입으로 주입받아 GPT 실패 시 폴백으로 호출한다.
 */
@Component
public class RuleBasedInsightCardGenerator implements InsightCardGenerator {

    /** {@link FactReportService} 의 폴백. */
    public String factReport(SpendStatus status, String topCategory, long expense) {
        return switch (status) {
            case ZERO -> "이번 달은 아직 지출 기록이 없어요.";
            case OVER -> topCategory == null
                    ? "예산이 거의 다 찼어요. 지출을 점검해보세요."
                    : String.format("'%s'에서 유독 지출이 많았어요.", topCategory);
            case WARNING -> "예산의 70%를 넘겼어요. 주의가 필요해요.";
            case SAVING -> "예산 안에서 안정적으로 소비하고 있어요.";
            case NO_BUDGET -> "꾸준히 기록을 남겨 예산 관리를 시작해보세요.";
        };
    }

    /**
     * {@link ConsumptionRiskCommentService} 의 폴백.
     *
     * 남은 금액을 되풀이하지 않는다 — 그건 옆 칸의 등급이 이미 말한 것이고, 이 자리는
     * '이번 달 소비의 무엇이 이 등급을 만들었는지'를 말하는 자리다.
     * 카테고리와 감정이 둘 다 있으면 둘을 이어서 말한다. 감정 기반 분석이 이 서비스의 초점이다.
     */
    public String riskComment(SpendStatus status, int usageRate, String topCategory, String topEmotion) {
        if (status == SpendStatus.NO_BUDGET) {
            // 예산이 안 잡히는 이유는 둘이다 — 활성 목표가 없거나, 기준선을 만들 3개월치 기록이 아직 없거나.
            // 어느 쪽인지 여기서는 알 수 없으므로 한쪽만 짚지 않는다. '목표를 정하면'만 말하면
            // 목표를 이미 세운 신규 사용자에게는 틀린 안내가 된다.
            return "목표와 3개월 기록이 모이면 속도를 봐드려요.";
        }
        if (status == SpendStatus.ZERO) {
            return "아직 이번 달 소비가 없어요.";
        }
        if (topCategory == null) {
            return switch (status) {
                case OVER -> String.format("예산의 %d%%를 벌써 썼어요.", usageRate);
                case WARNING -> String.format("소비 속도가 예산의 %d%%까지 왔어요.", usageRate);
                default -> String.format("예산의 %d%% 선에서 잘 잡고 있어요.", usageRate);
            };
        }
        if (topEmotion == null) {
            return switch (status) {
                case OVER -> String.format("'%s'이(가) 예산을 끝까지 밀어붙였어요.", topCategory);
                case WARNING -> String.format("'%s' 지출이 예산의 %d%%를 끌고 갔어요.", topCategory, usageRate);
                default -> String.format("'%s' 위주지만 아직 여유가 있어요.", topCategory);
            };
        }
        return switch (status) {
            case OVER -> String.format("'%s'일 때의 %s 소비가 예산을 다 썼어요.", topEmotion, topCategory);
            case WARNING -> String.format("'%s'일 때 쓴 %s이(가) 속도를 올렸어요.", topEmotion, topCategory);
            default -> String.format("'%s'일 때 %s에 쓰지만 아직 안정적이에요.", topEmotion, topCategory);
        };
    }

    /** {@link ChallengeService} 의 폴백. */
    public String challenge(String riskRoute) {
        if (riskRoute == null || riskRoute.isBlank()) {
            return "며칠만 기록을 이어가 보기";
        }
        return riskRoute + " 소비 3일 참아보기";
    }

    @Override
    public List<String> emotionAnalyses(List<EmotionStatDto> emotions, String topCategory, String topTimeSlotLabel) {
        // 카드가 담을 수 있는 길이(60자 안팎)에 맞춘다. 길면 화면에서 잘린다(#204).
        // GPT 문구와 나란히 보이는 자리라 결을 맞춘다 — 다정하되 숫자는 정확히 짚는다.
        // 감정마다 첫 마디와 제안을 달리한다. 같은 틀을 돌려쓰면 카드 세 장이 복사한 것처럼 보인다.
        long totalAmount = emotions.stream().mapToLong(EmotionStatDto::amount).sum();

        List<String> analyses = new ArrayList<>();
        for (EmotionStatDto emotion : emotions) {
            analyses.add(String.format("%s %s %s",
                    opening(emotion.name()), fact(emotion, totalAmount), suggestion(emotion.name())));
        }
        return analyses;
    }

    /**
     * 짚어줄 사실 한 조각. 그 감정에서 가장 눈에 띄는 수치를 고른다.
     * 금액만 나열하면 "그래서 뭐" 가 되므로, 비중이 크면 비율을, 아니면 건당 평균을 보여준다.
     */
    private String fact(EmotionStatDto emotion, long totalAmount) {
        long share = totalAmount > 0 ? Math.round(emotion.amount() * 100.0 / totalAmount) : 0;
        if (share >= 30) {
            return String.format("감정 소비의 %d%%가 여기 몰려 있어.", share);
        }
        if (emotion.count() > 0) {
            return String.format("%d번에 건당 %,d원씩 썼어.", emotion.count(), emotion.amount() / emotion.count());
        }
        return String.format("%,d원을 썼어.", emotion.amount());
    }

    /** 감정별 첫 마디. 목록에 없는 감정이 와도 무난하게 받는다. */
    private String opening(String emotion) {
        return switch (emotion) {
            case "신남" -> "신나는 날이었구나!";
            case "설렘" -> "설레는 마음이었네.";
            case "뿌듯함" -> "뿌듯할 만했어.";
            case "스트레스" -> "많이 지쳤구나.";
            case "화남" -> "속상한 일이 있었나 봐.";
            case "외로움" -> "혼자인 기분이었구나.";
            case "평온" -> "잔잔한 날이었네.";
            case "무덤덤" -> "특별할 것 없는 날에도";
            default -> "이 마음일 때";
        };
    }

    /** 감정별 다음 행동 제안. 훈계가 아니라 곁에서 권하는 어조로 둔다. */
    private String suggestion(String emotion) {
        return switch (emotion) {
            case "신남", "설렘" -> "들뜬 날은 하루 뒤에 사볼까?";
            case "뿌듯함" -> "보상 한도를 미리 정해두면 좋아.";
            case "스트레스", "화남" -> "급할 땐 10분만 미뤄볼까?";
            case "외로움" -> "장바구니에 하루 담아두면 어때?";
            case "평온" -> "고정지출이 섞이진 않았는지 볼까?";
            case "무덤덤" -> "반복되는 건 하나만 줄여볼까?";
            default -> "다음엔 한 번만 쉬어가 볼까?";
        };
    }
}
