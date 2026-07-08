package com.korit.feelioapi.domain.summary.dto;

import java.util.List;

public record EmotionSummaryResponse(
        List<EmotionSummaryDto> emotions,
        List<EmotionSummaryDto> prevMonth
) {}
