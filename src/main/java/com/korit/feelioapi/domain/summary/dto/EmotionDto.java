package com.korit.feelioapi.domain.summary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmotionDto {
    private Long emotionId;
    private String name;
    private String color;
}
