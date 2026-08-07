package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.AnalysisResponse;
import com.korit.feelioapi.domain.analysis.dto.AiReportResponseDto;
import com.korit.feelioapi.domain.analysis.dto.AnalysisTotalDto;
import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.InsightDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStat;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStatDto;
import com.korit.feelioapi.domain.analysis.entity.AiInsight;
import com.korit.feelioapi.domain.analysis.mapper.AnalysisMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock private AnalysisMapper analysisMapper;
    @Mock private InsightGenerator insightGenerator;
    @Mock private com.korit.feelioapi.domain.goal.mapper.GoalMapper goalMapper;
    @Mock private com.openai.client.OpenAIClient openAIClient;
    @Mock private AiInsightStore aiInsightStore;
    @Mock private AiQuickInsightAssembler quickInsightAssembler;
    @Mock private FactReportService factReportService;
    @Mock private ChallengeService challengeService;
    @Mock private EmotionAnalysisService emotionAnalysisService;

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

    @Test
    void 지난달_인사이트가_저장돼_있으면_생성기를_호출하지_않는다() {
        stubEmptyAggregates();
        // 지난 달은 거래가 더 늘지 않으므로 아무리 오래돼도 재생성하지 않는다.
        when(analysisMapper.findInsights(1L, 2026, 1))
                .thenReturn(List.of(savedInsight("PATTERN", "외로운 밤마다 배달 소비가 반복되고 있어요.",
                        LocalDateTime.now().minusDays(200))));

        AnalysisResponse response = analysisService.getMonthlyAnalysis(1L, 2026, 1);

        assertThat(response.insights()).hasSize(1);
        assertThat(response.insights().get(0).type()).isEqualTo("PATTERN");
        verify(insightGenerator, never()).generate(anyInt(), anyInt(), any(), any(), any());
        verify(aiInsightStore, never()).replace(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void 저장된_인사이트가_없으면_생성해서_저장한다() {
        stubEmptyAggregates();
        when(analysisMapper.findInsights(1L, 2026, 1)).thenReturn(List.of());
        when(insightGenerator.generate(eq(2026), eq(1), any(), any(), any()))
                .thenReturn(List.of(new InsightDto("EMOTION_FOCUS", "설렘 소비가 많았어요.")));

        AnalysisResponse response = analysisService.getMonthlyAnalysis(1L, 2026, 1);

        assertThat(response.insights()).hasSize(1);
        verify(aiInsightStore).replace(eq(1L), eq(2026), eq(1),
                argThat(list -> list.size() == 1 && list.get(0).type().equals("EMOTION_FOCUS")));
    }

    @Test
    void 생성된_인사이트가_비면_저장하지_않는다() {
        stubEmptyAggregates();
        when(analysisMapper.findInsights(1L, 2026, 1)).thenReturn(List.of());
        when(insightGenerator.generate(anyInt(), anyInt(), any(), any(), any())).thenReturn(List.of());

        AnalysisResponse response = analysisService.getMonthlyAnalysis(1L, 2026, 1);

        assertThat(response.insights()).isEmpty();
        // 빈 행을 남기면 다음 조회에서 재생성이 막힌다.
        verify(aiInsightStore, never()).replace(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void 이번달_인사이트가_TTL을_넘겼으면_다시_만들어_덮어쓴다() {
        LocalDate today = LocalDate.now();
        stubEmptyAggregates();
        // 기본 ttl 은 6시간. 24시간 전 생성분은 오래된 것으로 본다.
        when(analysisMapper.findInsights(1L, today.getYear(), today.getMonthValue()))
                .thenReturn(List.of(savedInsight("OLD", "예전 문장", LocalDateTime.now().minusHours(24))));
        when(insightGenerator.generate(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(List.of(new InsightDto("FRESH", "새 문장")));

        AnalysisResponse response =
                analysisService.getMonthlyAnalysis(1L, today.getYear(), today.getMonthValue());

        assertThat(response.insights().get(0).type()).isEqualTo("FRESH");
        verify(aiInsightStore).replace(eq(1L), anyInt(), anyInt(), any());
    }

    @Test
    void 이번달이라도_TTL_이내면_저장본을_재사용한다() {
        LocalDate today = LocalDate.now();
        stubEmptyAggregates();
        when(analysisMapper.findInsights(1L, today.getYear(), today.getMonthValue()))
                .thenReturn(List.of(savedInsight("RECENT", "방금 만든 문장", LocalDateTime.now().minusMinutes(30))));

        AnalysisResponse response =
                analysisService.getMonthlyAnalysis(1L, today.getYear(), today.getMonthValue());

        assertThat(response.insights().get(0).type()).isEqualTo("RECENT");
        verify(insightGenerator, never()).generate(anyInt(), anyInt(), any(), any(), any());
    }

    private AiInsight savedInsight(String type, String content, LocalDateTime createdAt) {
        AiInsight row = new AiInsight();
        row.setInsightType(type);
        row.setContent(content);
        row.setCreatedAt(createdAt);
        return row;
    }

    /** 인사이트 경로만 보는 테스트용 — 집계는 전부 비운다. */
    private void stubEmptyAggregates() {
        when(analysisMapper.findMonthlyTotals(anyLong(), anyInt(), anyInt()))
                .thenReturn(new AnalysisTotalDto(0L, 0L));
        when(analysisMapper.findExpenseByCategory(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(analysisMapper.findExpenseByEmotion(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(analysisMapper.findExpenseByTimeSlot(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
    }

    @Test
    void 모든_활성_목표의_필요저축액을_합산하여_동적_예산을_카테고리별로_분배한다() {
        // Given
        java.time.LocalDate now = java.time.LocalDate.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();
        
        java.time.LocalDate prevDate = now.minusMonths(1);
        int prevYear = prevDate.getYear();
        int prevMonth = prevDate.getMonthValue();

        com.korit.feelioapi.domain.goal.entity.Goal goal1 = new com.korit.feelioapi.domain.goal.entity.Goal();
        goal1.setStatus("ACTIVE");
        goal1.setTargetAmount(120000);
        goal1.setCurrentAmount(0); // 120,000 required
        goal1.setDueDate(now.plusMonths(1)); // monthsToGoal = 1 -> 120,000 / month

        com.korit.feelioapi.domain.goal.entity.Goal goal2 = new com.korit.feelioapi.domain.goal.entity.Goal();
        goal2.setStatus("ACTIVE");
        goal2.setTargetAmount(240000);
        goal2.setCurrentAmount(0); // 240,000 required
        goal2.setDueDate(now.plusMonths(2)); // monthsToGoal = 2 -> 120,000 / month

        // totalRequiredSavings = 240,000
        when(goalMapper.findGoalsByUserId(1L)).thenReturn(List.of(goal1, goal2));

        // Prev Stats: total expense = 1,000,000
        when(analysisMapper.findPrevCategoryStats(1L, prevYear, prevMonth))
                .thenReturn(List.of(
                        new com.korit.feelioapi.domain.analysis.dto.CategoryPrevStat(1L, "식비", 600000L, false, true),
                        new com.korit.feelioapi.domain.analysis.dto.CategoryPrevStat(2L, "교통", 400000L, false, true)
                ));

        // Current Stats
        when(analysisMapper.findCurrentCategoryStats(1L, currentYear, currentMonth))
                .thenReturn(List.of(
                        new com.korit.feelioapi.domain.analysis.dto.CategoryCurrentStat(1L, "식비", "기쁨", 10000L, false, true),
                        new com.korit.feelioapi.domain.analysis.dto.CategoryCurrentStat(2L, "교통", "슬픔", 5000L, false, true)
                ));

        // When
        com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse response = analysisService.getBudgetStatus(1L);

        // Then
        // reductionRatio = 240,000 / 1,000,000 = 0.24
        // Category 1: 600,000 * (1 - 0.24) = 600,000 * 0.76 = 456,000
        // Category 2: 400,000 * (1 - 0.24) = 400,000 * 0.76 = 304,000
        assertThat(response.budgetItems()).hasSize(2);
        
        com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse.BudgetItem item1 = response.budgetItems().get(0);
        assertThat(item1.name()).isEqualTo("식비");
        assertThat(item1.budget()).isEqualTo(456000L);

        com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse.BudgetItem item2 = response.budgetItems().get(1);
        assertThat(item2.name()).isEqualTo("교통");
        assertThat(item2.budget()).isEqualTo(304000L);
    }

    @Test
    void AI를_호출하지_않고_분석_리포트_뼈대를_반환한다() {
        LocalDate today = LocalDate.now();
        LocalDate previousMonth = today.minusMonths(1);

        when(analysisMapper.findMonthlyTotals(1L, today.getYear(), today.getMonthValue()))
                .thenReturn(new AnalysisTotalDto(0L, 250000L));
        when(goalMapper.findGoalsByUserId(1L)).thenReturn(List.of());
        when(analysisMapper.findCurrentCategoryStats(1L, today.getYear(), today.getMonthValue()))
                .thenReturn(List.of());
        when(analysisMapper.findPrevCategoryStats(1L, previousMonth.getYear(), previousMonth.getMonthValue()))
                .thenReturn(List.of());
        when(analysisMapper.findExpenseByCategory(1L, today.getYear(), today.getMonthValue()))
                .thenReturn(List.of(new CategoryStatDto(3L, "카페", "EXPENSE", 200000L, 8L)));
        when(factReportService.generate(SpendStatus.NO_BUDGET, 250000L, 0L, "카페"))
                .thenReturn("예산부터 잡으면 지갑도 방향을 찾겠는데?");
        List<CategoryStatDto> weeklyCategories = List.of(
                new CategoryStatDto(5L, "배달", "EXPENSE", 120000L, 4L));
        when(analysisMapper.findWeeklyExpenseByCategory(
                eq(1L), any(java.time.LocalDateTime.class), any(java.time.LocalDateTime.class)))
                .thenReturn(weeklyCategories);
        when(challengeService.generate(weeklyCategories)).thenReturn("이번 주 배달은 2번까지만 주문하기");
        List<EmotionStatDto> monthlyEmotions = List.of(
                new EmotionStatDto(4L, "스트레스", "#A68BEA", 180000L, 5L));
        when(analysisMapper.findExpenseByEmotion(1L, today.getYear(), today.getMonthValue()))
                .thenReturn(monthlyEmotions);
        when(analysisMapper.findExpenseByTimeSlot(1L, today.getYear(), today.getMonthValue()))
                .thenReturn(List.of(new TimeSlotStat("NIGHT", 190000L, 6L)));
        String emotionAnalysis = "① 발견: 스트레스 소비가 밤에 두드러졌어요. "
                + "② 의미: 지친 마음을 달래려는 선택이었을 수 있어요. "
                + "③ 조언: 결제 전 5분만 마음을 살펴보세요.";
        when(emotionAnalysisService.generate(monthlyEmotions, "카페", "밤"))
                .thenReturn(emotionAnalysis);

        AiReportResponseDto response = analysisService.getAiReport(1L, null, null);

        assertThat(response.totalExpense()).isEqualTo(250000L);
        assertThat(response.totalBudget()).isZero();
        assertThat(response.budgetUsageRate()).isZero();
        assertThat(response.consumptionRisk()).isEqualTo("GREEN");
        assertThat(response.ai().fact()).isEqualTo("예산부터 잡으면 지갑도 방향을 찾겠는데?");
        assertThat(response.ai().challenge()).isEqualTo("이번 주 배달은 2번까지만 주문하기");
        assertThat(response.ai().emotion()).isEqualTo(emotionAnalysis);
        verifyNoInteractions(openAIClient);
        verify(factReportService).generate(SpendStatus.NO_BUDGET, 250000L, 0L, "카페");
        verify(challengeService).generate(weeklyCategories);
        verify(emotionAnalysisService).generate(monthlyEmotions, "카페", "밤");
    }
}
