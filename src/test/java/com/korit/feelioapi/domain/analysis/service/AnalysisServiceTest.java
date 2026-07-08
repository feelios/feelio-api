package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.AnalysisResponse;
import com.korit.feelioapi.domain.analysis.dto.AnalysisTotalDto;
import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.InsightDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStat;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStatDto;
import com.korit.feelioapi.domain.analysis.mapper.AnalysisMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock private AnalysisMapper analysisMapper;
    @Mock private InsightGenerator insightGenerator;

    @InjectMocks private AnalysisService analysisService;

    @Test
    void 월간_분석을_집계해_응답을_조립한다() {
        when(analysisMapper.findMonthlyTotals(1L, 2026, 7))
                .thenReturn(new AnalysisTotalDto(2600000L, 320000L));
        when(analysisMapper.findExpenseByCategory(1L, 2026, 7))
                .thenReturn(List.of(new CategoryStatDto(3L, "카페", "EXPENSE", 48000L, 6L)));
        when(analysisMapper.findExpenseByEmotion(1L, 2026, 7))
                .thenReturn(List.of(new EmotionStatDto(2L, "설렘", "#F28AB7", 140600L, 6L)));
        // 매퍼는 순서 무관하게 반환 — 서비스가 시간 순으로 정렬해야 함
        when(analysisMapper.findExpenseByTimeSlot(1L, 2026, 7))
                .thenReturn(List.of(
                        new TimeSlotStat("NIGHT", 190000L, 8L),
                        new TimeSlotStat("MORNING", 30000L, 2L)
                ));
        when(insightGenerator.generate(eq(2026), eq(7), any(), any(), any()))
                .thenReturn(List.of(new InsightDto("EMOTION_FOCUS", "설렘 소비가 많았어요.")));

        AnalysisResponse response = analysisService.getMonthlyAnalysis(1L, 2026, 7);

        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.totalIncome()).isEqualTo(2600000L);
        assertThat(response.totalExpense()).isEqualTo(320000L);
        assertThat(response.byCategory()).hasSize(1);
        assertThat(response.byEmotion().get(0).name()).isEqualTo("설렘");
        assertThat(response.insights()).hasSize(1);

        // 시간대: 라벨 부여 + 시간 순(MORNING 먼저, NIGHT 나중)
        assertThat(response.byTimeSlot()).extracting(TimeSlotStatDto::slot)
                .containsExactly("MORNING", "NIGHT");
        assertThat(response.byTimeSlot().get(0).label()).isEqualTo("아침");
        assertThat(response.byTimeSlot().get(1).label()).isEqualTo("밤");
    }

    @Test
    void 기록이_없으면_빈_집계와_빈_인사이트를_반환한다() {
        when(analysisMapper.findMonthlyTotals(anyLong(), anyInt(), anyInt()))
                .thenReturn(new AnalysisTotalDto(0L, 0L));
        when(analysisMapper.findExpenseByCategory(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(analysisMapper.findExpenseByEmotion(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(analysisMapper.findExpenseByTimeSlot(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(insightGenerator.generate(anyInt(), anyInt(), any(), any(), any())).thenReturn(List.of());

        AnalysisResponse response = analysisService.getMonthlyAnalysis(1L, 2026, 1);

        assertThat(response.byCategory()).isEmpty();
        assertThat(response.byEmotion()).isEmpty();
        assertThat(response.byTimeSlot()).isEmpty();
        assertThat(response.insights()).isEmpty();
        assertThat(response.totalExpense()).isZero();
    }
}
