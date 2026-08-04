package com.korit.feelioapi.domain.summary.service;

import com.korit.feelioapi.domain.summary.dto.CalendarDayDto;
import com.korit.feelioapi.domain.summary.dto.CalendarSummaryResponse;
import com.korit.feelioapi.domain.summary.dto.EmotionDto;
import com.korit.feelioapi.domain.summary.dto.EmotionSummaryDto;
import com.korit.feelioapi.domain.summary.dto.EmotionSummaryResponse;
import com.korit.feelioapi.domain.summary.dto.SummaryAiCommentResponse;
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

    @Mock
    private SummaryAiCommentGenerator aiCommentGenerator;

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

        assertThat(response.getDays()).hasSize(1);
        assertThat(response.getDays().get(0).getDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.getDays().get(0).getDominantEmotion().getName()).isEqualTo("스트레스");
        assertThat(response.getDays().get(0).getTransactionCount()).isEqualTo(2);
        assertThat(response.getDays().get(0).getTotalExpense()).isEqualTo(50600L);

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

        assertThat(response.getEmotions()).hasSize(1);
        assertThat(response.getEmotions().get(0).getCount()).isEqualTo(6);
        assertThat(response.getPrevMonth()).hasSize(1);
        assertThat(response.getPrevMonth().get(0).getAmount()).isEqualTo(98000L);

        verify(summaryMapper).findEmotionSummary(userId, 2026, 1);
        verify(summaryMapper).findEmotionSummary(userId, 2025, 12);
    }

    @Test
    void 당월과_전월_지출로_홈_AI_멘트를_생성한다() {
        Long userId = 2L;
        LocalDate today = LocalDate.now();
        LocalDate previousMonth = today.minusMonths(1);

        when(summaryMapper.findMonthlyExpense(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(300000L);
        when(summaryMapper.findMonthlyExpense(userId, previousMonth.getYear(), previousMonth.getMonthValue()))
                .thenReturn(400000L);
        when(aiCommentGenerator.generate(today.getYear(), today.getMonthValue(), 300000L, 400000L))
                .thenReturn("지난달보다 지출이 줄었어요.");

        SummaryAiCommentResponse response = summaryService.getAiComment(userId);

        assertThat(response.comment()).isEqualTo("지난달보다 지출이 줄었어요.");
        verify(aiCommentGenerator).generate(today.getYear(), today.getMonthValue(), 300000L, 400000L);
    }

    @Test
    void 당월_지출이_없으면_AI를_호출하지_않고_빈_멘트를_반환한다() {
        Long userId = 3L;
        LocalDate today = LocalDate.now();
        LocalDate previousMonth = today.minusMonths(1);

        when(summaryMapper.findMonthlyExpense(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(0L);
        when(summaryMapper.findMonthlyExpense(userId, previousMonth.getYear(), previousMonth.getMonthValue()))
                .thenReturn(150000L);

        SummaryAiCommentResponse response = summaryService.getAiComment(userId);

        assertThat(response.comment()).isNull();
        org.mockito.Mockito.verifyNoInteractions(aiCommentGenerator);
    }
}
