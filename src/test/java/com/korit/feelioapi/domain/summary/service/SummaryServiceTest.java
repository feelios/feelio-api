package com.korit.feelioapi.domain.summary.service;

import com.korit.feelioapi.domain.summary.dto.CalendarDayDto;
import com.korit.feelioapi.domain.summary.dto.CalendarSummaryResponse;
import com.korit.feelioapi.domain.summary.dto.EmotionDto;
import com.korit.feelioapi.domain.summary.dto.EmotionSummaryDto;
import com.korit.feelioapi.domain.summary.dto.EmotionSummaryResponse;
import com.korit.feelioapi.domain.summary.mapper.SummaryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    @Mock
    private SummaryMapper summaryMapper;

    @InjectMocks
    private SummaryService summaryService;

    @Test
    void 홈_캘린더_요약을_조회한다() {
        Long userId = 1L;
        Integer year = 2026;
        Integer month = 7;
        
        EmotionDto emotion = new EmotionDto(4L, "스트레스", "#A68BEA");
        CalendarDayDto dayDto = new CalendarDayDto(LocalDate.of(2026, 7, 1), emotion, 2, 50600L);
        
        when(summaryMapper.findCalendarSummary(userId, year, month)).thenReturn(List.of(dayDto));

        CalendarSummaryResponse response = summaryService.getCalendarSummary(userId, year, month);

        assertThat(response.days()).hasSize(1);
        assertThat(response.days().get(0).date()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.days().get(0).dominantEmotion().name()).isEqualTo("스트레스");
        assertThat(response.days().get(0).transactionCount()).isEqualTo(2);
        assertThat(response.days().get(0).totalExpense()).isEqualTo(50600L);

        verify(summaryMapper).findCalendarSummary(userId, year, month);
    }

    @Test
    void 감정_요약을_조회한다() {
        Long userId = 1L;
        Integer year = 2026;
        Integer month = 1; // 1월 조회 시 12월 조회 검증 위함

        List<EmotionSummaryDto> currEmotions = List.of(new EmotionSummaryDto(4L, "스트레스", 6, 140600L));
        List<EmotionSummaryDto> prevEmotions = List.of(new EmotionSummaryDto(4L, "스트레스", 4, 98000L));

        when(summaryMapper.findEmotionSummary(userId, 2026, 1)).thenReturn(currEmotions);
        when(summaryMapper.findEmotionSummary(userId, 2025, 12)).thenReturn(prevEmotions);

        EmotionSummaryResponse response = summaryService.getEmotionSummary(userId, year, month);

        assertThat(response.emotions()).hasSize(1);
        assertThat(response.emotions().get(0).count()).isEqualTo(6);
        assertThat(response.prevMonth()).hasSize(1);
        assertThat(response.prevMonth().get(0).amount()).isEqualTo(98000L);

        verify(summaryMapper).findEmotionSummary(userId, 2026, 1);
        verify(summaryMapper).findEmotionSummary(userId, 2025, 12);
    }
}
