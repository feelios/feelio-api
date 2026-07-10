package com.korit.feelioapi.domain.summary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarDayDto {
    private LocalDate date;
    private EmotionDto dominantEmotion;
    private Integer transactionCount;
    private Long totalExpense;
}
