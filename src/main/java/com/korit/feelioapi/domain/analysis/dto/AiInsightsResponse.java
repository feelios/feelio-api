package com.korit.feelioapi.domain.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiInsightsResponse {
    private List<AiQuickInsight> aiQuickInsights;
    private List<EmotionCard> emotionCards;
    private List<List<String>> evidence;
    private AiPattern pattern;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiQuickInsight {
        private String label;
        private String value;
        private String note;
        private String color;
        private String type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionCard {
        private String emotion;
        private String title;
        private String desc;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiPattern {
        private int count;
        private String title;
        private String emotion;
        private String category;
        private String time;
        private String desc;
    }
}
