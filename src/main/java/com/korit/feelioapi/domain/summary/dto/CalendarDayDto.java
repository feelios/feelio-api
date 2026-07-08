package com.korit.feelioapi.domain.summary.dto;

import java.time.LocalDate;

public record CalendarDayDto(
        LocalDate date,
        EmotionDto dominantEmotion,
        Integer transactionCount,
        Long totalExpense
) {}
