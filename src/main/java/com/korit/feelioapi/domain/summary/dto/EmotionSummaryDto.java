package com.korit.feelioapi.domain.summary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmotionSummaryDto {
    private Long emotionId;
    private String name;
    private Integer count;
    private Long amount;
}
