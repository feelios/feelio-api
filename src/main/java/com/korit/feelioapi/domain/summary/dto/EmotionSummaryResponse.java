package com.korit.feelioapi.domain.summary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmotionSummaryResponse {
    private List<EmotionSummaryDto> emotions;
    private List<EmotionSummaryDto> prevMonth;
}
