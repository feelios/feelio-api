package com.korit.feelioapi.domain.summary.service;

import com.korit.feelioapi.domain.summary.dto.CalendarDayDto;
import com.korit.feelioapi.domain.summary.dto.CalendarSummaryResponse;
import com.korit.feelioapi.domain.summary.dto.EmotionDto;
import com.korit.feelioapi.domain.summary.dto.EmotionSummaryDto;
import com.korit.feelioapi.domain.summary.dto.EmotionSummaryResponse;
import com.korit.feelioapi.domain.summary.dto.MallangCommentResponse;
import com.korit.feelioapi.domain.summary.dto.SummaryAiCommentResponse;
import com.korit.feelioapi.domain.summary.mapper.SummaryMapper;
import com.korit.feelioapi.domain.analysis.service.AnalysisService;
import com.korit.feelioapi.domain.analysis.service.SpendStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    @Mock
    private SummaryMapper summaryMapper;

    @Mock
    private SummaryAiCommentGenerator aiCommentGenerator;

    @Mock
    private MallangCommentGenerator mallangCommentGenerator;

    @Mock
    private AnalysisService analysisService;

    /** 폴백은 실제 구현을 그대로 쓴다 — 문장이 비지 않는지가 검증 대상이라 mock 이면 의미가 없다. */
    private final RuleMallangCommentGenerator ruleMallangCommentGenerator = new RuleMallangCommentGenerator();

    private SummaryService summaryService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        summaryService = new SummaryService(
                summaryMapper, aiCommentGenerator, mallangCommentGenerator,
                ruleMallangCommentGenerator, analysisService);
    }

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

    // ── 말랑이 코멘트 (A8-3) ────────────────────────────────────────────

    @Test
    void 말랑이_코멘트는_AI_문장을_그대로_쓰고_상태는_서버가_정한다() {
        Long userId = 1L;
        LocalDate today = LocalDate.now();

        when(summaryMapper.findMonthlyExpense(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(320_000L);
        when(analysisService.totalBudget(userId, today.getYear(), today.getMonthValue())).thenReturn(400_000L);   // 소진율 80% → WARNING
        when(mallangCommentGenerator.generate(any(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(new MallangComment("이번 달 320,000원 썼어. 예산의 80%야.", "이번 주는 한 번만 아껴볼까?"));

        MallangCommentResponse response = summaryService.getMallangComment(userId);

        assertThat(response.evaluation()).isEqualTo("이번 달 320,000원 썼어. 예산의 80%야.");
        assertThat(response.encouragement()).isEqualTo("이번 주는 한 번만 아껴볼까?");
        assertThat(response.status()).isEqualTo(SpendStatus.WARNING.name());
    }

    @Test
    void 말랑이_코멘트는_AI가_실패해도_규칙기반_문장으로_채운다() {
        Long userId = 2L;
        LocalDate today = LocalDate.now();

        when(summaryMapper.findMonthlyExpense(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(380_000L);
        when(analysisService.totalBudget(userId, today.getYear(), today.getMonthValue())).thenReturn(400_000L);   // 95% → OVER
        when(mallangCommentGenerator.generate(any(), anyLong(), anyLong(), anyInt(), any())).thenReturn(null);

        MallangCommentResponse response = summaryService.getMallangComment(userId);

        assertThat(response.evaluation()).isNotBlank();
        assertThat(response.encouragement()).isNotBlank();
        assertThat(response.evaluation()).contains("380,000원");   // 근거 수치가 반드시 들어간다
        assertThat(response.status()).isEqualTo(SpendStatus.OVER.name());
    }

    @Test
    void 말랑이_코멘트는_AI가_빈_문장을_줘도_규칙기반으로_대체한다() {
        Long userId = 3L;
        LocalDate today = LocalDate.now();

        when(summaryMapper.findMonthlyExpense(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(100_000L);
        when(analysisService.totalBudget(userId, today.getYear(), today.getMonthValue())).thenReturn(400_000L);   // 25% → SAVING
        when(mallangCommentGenerator.generate(any(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(new MallangComment("  ", ""));

        MallangCommentResponse response = summaryService.getMallangComment(userId);

        assertThat(response.evaluation()).isNotBlank();
        assertThat(response.encouragement()).isNotBlank();
        assertThat(response.status()).isEqualTo(SpendStatus.SAVING.name());
    }

    @Test
    void 예산을_구할_수_없으면_소진율_대신_지출액만_말한다() {
        Long userId = 4L;
        LocalDate today = LocalDate.now();

        when(summaryMapper.findMonthlyExpense(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(50_000L);
        when(analysisService.totalBudget(userId, today.getYear(), today.getMonthValue())).thenReturn(0L);         // 활성 목표·전월 기록 없음
        when(mallangCommentGenerator.generate(any(), anyLong(), anyLong(), anyInt(), any())).thenReturn(null);

        MallangCommentResponse response = summaryService.getMallangComment(userId);

        assertThat(response.status()).isEqualTo(SpendStatus.NO_BUDGET.name());
        assertThat(response.evaluation()).contains("50,000원");
        assertThat(response.evaluation()).doesNotContain("%");
    }

    @Test
    void 지출이_없으면_ZERO_상태로_응답한다() {
        Long userId = 5L;
        LocalDate today = LocalDate.now();

        when(summaryMapper.findMonthlyExpense(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(0L);
        when(analysisService.totalBudget(userId, today.getYear(), today.getMonthValue())).thenReturn(400_000L);
        when(mallangCommentGenerator.generate(any(), anyLong(), anyLong(), anyInt(), any())).thenReturn(null);

        MallangCommentResponse response = summaryService.getMallangComment(userId);

        assertThat(response.status()).isEqualTo(SpendStatus.ZERO.name());
        assertThat(response.evaluation()).isNotBlank();
        assertThat(response.encouragement()).isNotBlank();
    }

    @Test
    void 말랑이_코멘트는_같은_날_두_번째_호출부터_캐시를_쓴다() {
        Long userId = 6L;
        LocalDate today = LocalDate.now();

        when(summaryMapper.findMonthlyExpense(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(320_000L);
        when(analysisService.totalBudget(userId, today.getYear(), today.getMonthValue())).thenReturn(400_000L);
        when(mallangCommentGenerator.generate(any(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(new MallangComment("평가", "독려"));

        summaryService.getMallangComment(userId);
        summaryService.getMallangComment(userId);

        verify(mallangCommentGenerator, times(1)).generate(any(), anyLong(), anyLong(), anyInt(), any());
    }

    // ── 감정 기반 개인화 (A12-3) ────────────────────────────────────────

    @Test
    void 말랑이_코멘트는_당월_최다_감정을_생성기에_넘기고_응답에_담는다() {
        Long userId = 7L;
        LocalDate today = LocalDate.now();
        LocalDate prev = today.minusMonths(1);

        when(summaryMapper.findMonthlyExpense(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(320_000L);
        when(analysisService.totalBudget(userId, today.getYear(), today.getMonthValue())).thenReturn(400_000L);
        when(summaryMapper.findEmotionSummary(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(List.of(
                        new EmotionSummaryDto(4L, "스트레스", 9, 180_000L),
                        new EmotionSummaryDto(1L, "신남", 3, 60_000L)));
        when(summaryMapper.findEmotionSummary(userId, prev.getYear(), prev.getMonthValue()))
                .thenReturn(List.of(new EmotionSummaryDto(1L, "신남", 5, 90_000L)));
        when(mallangCommentGenerator.generate(any(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(new MallangComment("평가", "독려"));

        MallangCommentResponse response = summaryService.getMallangComment(userId);

        assertThat(response.emotion()).isEqualTo("스트레스");

        ArgumentCaptor<EmotionContext> captor = ArgumentCaptor.forClass(EmotionContext.class);
        verify(mallangCommentGenerator).generate(any(), anyLong(), anyLong(), anyInt(), captor.capture());
        EmotionContext passed = captor.getValue();
        assertThat(passed.name()).isEqualTo("스트레스");
        assertThat(passed.count()).isEqualTo(9);
        assertThat(passed.amount()).isEqualTo(180_000L);
        // 지난달 1위(신남)와 다르다
        assertThat(passed.trend()).isEqualTo(EmotionContext.Trend.CHANGED);
    }

    @Test
    void 감정_기록이_없으면_emotion_은_null_이고_소비_문구만_나간다() {
        Long userId = 8L;
        LocalDate today = LocalDate.now();

        when(summaryMapper.findMonthlyExpense(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(320_000L);
        when(analysisService.totalBudget(userId, today.getYear(), today.getMonthValue())).thenReturn(400_000L);
        when(mallangCommentGenerator.generate(any(), anyLong(), anyLong(), anyInt(), any())).thenReturn(null);

        MallangCommentResponse response = summaryService.getMallangComment(userId);

        assertThat(response.emotion()).isNull();
        assertThat(response.evaluation()).isNotBlank();
        assertThat(response.encouragement()).isNotBlank();
    }

    @Test
    void AI가_실패해도_규칙기반_폴백이_감정에_맞춘_문장을_쓴다() {
        Long userId = 9L;
        LocalDate today = LocalDate.now();

        when(summaryMapper.findMonthlyExpense(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(320_000L);
        when(analysisService.totalBudget(userId, today.getYear(), today.getMonthValue())).thenReturn(400_000L);
        when(summaryMapper.findEmotionSummary(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(List.of(new EmotionSummaryDto(4L, "스트레스", 9, 180_000L)));
        when(mallangCommentGenerator.generate(any(), anyLong(), anyLong(), anyInt(), any())).thenReturn(null);

        MallangCommentResponse response = summaryService.getMallangComment(userId);

        assertThat(response.emotion()).isEqualTo("스트레스");
        // 개인화가 폴백에서도 살아 있어야 한다 — 상태 기본 문구로 떨어지면 안 된다
        assertThat(response.encouragement()).isNotEqualTo("이번 주는 한 번만 아껴봐도 충분해.");
        assertThat(response.encouragement()).contains("스트레스");
        assertThat(response.evaluation()).contains("320,000원");
    }

    @Test
    void 대표_감정이_바뀌면_같은_날이어도_문구를_다시_만든다() {
        Long userId = 10L;
        LocalDate today = LocalDate.now();

        when(summaryMapper.findMonthlyExpense(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(320_000L);
        when(analysisService.totalBudget(userId, today.getYear(), today.getMonthValue())).thenReturn(400_000L);
        when(summaryMapper.findEmotionSummary(userId, today.getYear(), today.getMonthValue()))
                .thenReturn(List.of(new EmotionSummaryDto(4L, "스트레스", 9, 180_000L)))
                .thenReturn(List.of(new EmotionSummaryDto(1L, "신남", 12, 200_000L)));
        when(mallangCommentGenerator.generate(any(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(new MallangComment("평가", "독려"));

        MallangCommentResponse first = summaryService.getMallangComment(userId);
        MallangCommentResponse second = summaryService.getMallangComment(userId);

        assertThat(first.emotion()).isEqualTo("스트레스");
        assertThat(second.emotion()).isEqualTo("신남");
        verify(mallangCommentGenerator, times(2)).generate(any(), anyLong(), anyLong(), anyInt(), any());
    }
}
