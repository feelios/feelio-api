package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.AiInsightsResponse;
import com.korit.feelioapi.domain.analysis.dto.AnalysisResponse;
import com.korit.feelioapi.domain.analysis.dto.AnalysisTotalDto;
import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.InsightDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStat;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStatDto;
import com.korit.feelioapi.domain.analysis.mapper.AnalysisMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 월간 분석 (API-CONTRACT §9). 지출 기준 집계 + 인사이트 문장. 항상 user_id 기준.
 */
@Service
@RequiredArgsConstructor
public class AnalysisService {

    /** 시간대 코드 → 한글 라벨 + 표시 순서(시간 순). */
    private static final List<Map.Entry<String, String>> TIME_SLOTS = List.of(
            Map.entry("DAWN", "새벽"),
            Map.entry("MORNING", "아침"),
            Map.entry("AFTERNOON", "오후"),
            Map.entry("NIGHT", "밤")
    );

    private final AnalysisMapper analysisMapper;
    private final InsightGenerator insightGenerator;
    private final com.korit.feelioapi.domain.goal.mapper.GoalMapper goalMapper;
    private final OpenAIClient openAIClient;

    @Transactional(readOnly = true)
    public AnalysisResponse getMonthlyAnalysis(Long userId, int year, int month) {
        AnalysisTotalDto totals = analysisMapper.findMonthlyTotals(userId, year, month);
        List<CategoryStatDto> byCategory = analysisMapper.findExpenseByCategory(userId, year, month);
        List<EmotionStatDto> byEmotion = analysisMapper.findExpenseByEmotion(userId, year, month);
        List<TimeSlotStatDto> byTimeSlot = toTimeSlotDtos(analysisMapper.findExpenseByTimeSlot(userId, year, month));

        List<InsightDto> insights = insightGenerator.generate(year, month, byEmotion, byCategory, byTimeSlot);

        return new AnalysisResponse(
                year, month,
                totals.totalIncome(), totals.totalExpense(),
                byCategory, byEmotion, byTimeSlot, insights
        );
    }

    /** 매퍼 결과에 한글 라벨을 붙이고 시간 순으로 정렬(기록 없는 구간 생략). */
    private List<TimeSlotStatDto> toTimeSlotDtos(List<TimeSlotStat> rows) {
        Map<String, TimeSlotStat> bySlot = rows.stream()
                .collect(Collectors.toMap(TimeSlotStat::slot, Function.identity()));
        List<TimeSlotStatDto> result = new ArrayList<>();
        for (Map.Entry<String, String> slot : TIME_SLOTS) {
            TimeSlotStat row = bySlot.get(slot.getKey());
            if (row != null) {
                result.add(new TimeSlotStatDto(slot.getKey(), slot.getValue(), row.amount(), row.count()));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public AiInsightsResponse getAiInsights(Long userId) {
        // [F7-3 테스트용] Empty State (데이터 없음) 반환
        return AiInsightsResponse.builder()
                .aiQuickInsights(List.of()) // 빈 배열
                .emotionCards(List.of())   // 빈 배열
                .evidence(List.of())       // 빈 배열
                .pattern(AiInsightsResponse.AiPattern.builder()
                        .count(0) // 0으로 설정하여 빈 상태 트리거
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public com.korit.feelioapi.domain.analysis.dto.MonthlyTrendResponse getMonthlyTrend(Long userId) {
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.LocalDate startDate = now.minusMonths(6).withDayOfMonth(1);
        java.time.LocalDate endDate = now.plusMonths(1).withDayOfMonth(1);

        List<com.korit.feelioapi.domain.analysis.dto.MonthlyDataStat> stats = analysisMapper.findMonthlyTrend(userId, startDate, endDate);
        Map<String, Long> statMap = stats.stream()
                .collect(Collectors.toMap(com.korit.feelioapi.domain.analysis.dto.MonthlyDataStat::yearMonth, com.korit.feelioapi.domain.analysis.dto.MonthlyDataStat::amount));

        List<com.korit.feelioapi.domain.analysis.dto.MonthlyTrendResponse.MonthlyData> monthlyData = new ArrayList<>();
        Long currentMonthAmount = 0L;
        Long previousMonthAmount = 0L;

        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM");

        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate targetMonth = now.minusMonths(i);
            String yearMonthStr = targetMonth.format(formatter);
            Long amount = statMap.getOrDefault(yearMonthStr, 0L);
            monthlyData.add(new com.korit.feelioapi.domain.analysis.dto.MonthlyTrendResponse.MonthlyData(
                    targetMonth.getMonthValue() + "월",
                    amount
            ));

            if (i == 0) {
                currentMonthAmount = amount;
            } else if (i == 1) {
                previousMonthAmount = amount;
            }
        }

        Double comparedToLastMonth = 0.0;
        if (previousMonthAmount > 0) {
            comparedToLastMonth = Math.round(((double) (currentMonthAmount - previousMonthAmount) / previousMonthAmount) * 1000) / 10.0;
        } else if (currentMonthAmount > 0) {
            comparedToLastMonth = 100.0;
        }

        String trendMessage = "데이터를 모으고 있어요";
        if (currentMonthAmount > previousMonthAmount) {
            trendMessage = "저번 달보다 지출이 늘었어요";
        } else if (currentMonthAmount < previousMonthAmount && previousMonthAmount > 0) {
            trendMessage = "저번 달보다 지출이 줄었어요";
        } else if (currentMonthAmount > 0 && currentMonthAmount.equals(previousMonthAmount)) {
            trendMessage = "저번 달과 지출이 비슷해요";
        }

        return new com.korit.feelioapi.domain.analysis.dto.MonthlyTrendResponse(
                currentMonthAmount,
                comparedToLastMonth,
                trendMessage,
                monthlyData
        );
    }

    @Transactional(readOnly = true)
    public com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse getBudgetStatus(Long userId) {
        java.time.LocalDate now = java.time.LocalDate.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();

        java.time.LocalDate prevDate = now.minusMonths(1);
        int prevYear = prevDate.getYear();
        int prevMonth = prevDate.getMonthValue();

        // 1. Calculate required savings (S)
        List<com.korit.feelioapi.domain.goal.entity.Goal> goals = goalMapper.findGoalsByUserId(userId);
        long totalRequiredSavings = 0;
        for (com.korit.feelioapi.domain.goal.entity.Goal goal : goals) {
            if ("ACTIVE".equals(goal.getStatus())) {
                long remainingAmount = Math.max(0, goal.getTargetAmount() - goal.getCurrentAmount());
                int monthsToGoal = 1;
                if (goal.getDueDate() != null) {
                    monthsToGoal = (goal.getDueDate().getYear() - currentYear) * 12 + (goal.getDueDate().getMonthValue() - currentMonth);
                }
                monthsToGoal = Math.max(1, monthsToGoal);
                long monthlySaving = (long) Math.ceil((double) remainingAmount / monthsToGoal);
                totalRequiredSavings += monthlySaving;
            }
        }

        List<com.korit.feelioapi.domain.analysis.dto.CategoryCurrentStat> currentStats = analysisMapper.findCurrentCategoryStats(userId, currentYear, currentMonth);
        List<com.korit.feelioapi.domain.analysis.dto.CategoryPrevStat> prevStats = analysisMapper.findPrevCategoryStats(userId, prevYear, prevMonth);

        Map<Long, Long> prevStatMap = prevStats.stream()
                .collect(Collectors.toMap(com.korit.feelioapi.domain.analysis.dto.CategoryPrevStat::categoryId, com.korit.feelioapi.domain.analysis.dto.CategoryPrevStat::prevAmount));

        // 2. Sum up prev variable expenses (isFixed = false, isBudgetable = true)
        long variablePrevTotalExpense = prevStats.stream()
                .filter(stat -> !stat.isFixed() && stat.isBudgetable())
                .mapToLong(com.korit.feelioapi.domain.analysis.dto.CategoryPrevStat::prevAmount)
                .sum();

        List<com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse.BudgetItem> budgetItems = new ArrayList<>();
        java.util.Set<Long> processedCategories = new java.util.HashSet<>();

        for (com.korit.feelioapi.domain.analysis.dto.CategoryCurrentStat currentStat : currentStats) {
            processedCategories.add(currentStat.categoryId());
            
            if (!currentStat.isBudgetable()) {
                continue; // 예산 제외 항목
            }

            Long prevAmount = prevStatMap.getOrDefault(currentStat.categoryId(), 0L);
            long budget = 0L;
            
            if (currentStat.isFixed()) {
                budget = Math.max(prevAmount, currentStat.currentAmount());
            } else {
                if (variablePrevTotalExpense > 0) {
                    double reductionRatio = (double) totalRequiredSavings / variablePrevTotalExpense;
                    reductionRatio = Math.min(1.0, reductionRatio);
                    double rawBudget = prevAmount * (1.0 - reductionRatio);
                    budget = Math.round(rawBudget / 1000.0) * 1000L;
                }
            }

            budgetItems.add(new com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse.BudgetItem(
                    currentStat.categoryName(),
                    currentStat.dominantEmotion() != null ? currentStat.dominantEmotion() : "보통",
                    currentStat.currentAmount(),
                    prevAmount,
                    budget
            ));
        }

        for (com.korit.feelioapi.domain.analysis.dto.CategoryPrevStat prevStat : prevStats) {
            if (!processedCategories.contains(prevStat.categoryId())) {
                if (!prevStat.isBudgetable()) {
                    continue; // 예산 제외 항목
                }

                long budget = 0L;
                if (prevStat.isFixed()) {
                    budget = prevStat.prevAmount();
                } else {
                    if (variablePrevTotalExpense > 0) {
                        double reductionRatio = (double) totalRequiredSavings / variablePrevTotalExpense;
                        reductionRatio = Math.min(1.0, reductionRatio);
                        double rawBudget = prevStat.prevAmount() * (1.0 - reductionRatio);
                        budget = Math.round(rawBudget / 1000.0) * 1000L;
                    }
                }

                budgetItems.add(new com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse.BudgetItem(
                        prevStat.categoryName() != null ? prevStat.categoryName() : "기타",
                        "보통",
                        0L,
                        prevStat.prevAmount(),
                        budget
                ));
            }
        }

        return new com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse(budgetItems);
    }

    public List<String> getAiChatResponse(String value) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .instructions("너") //뭘까? :
                .temperature(0.0) //고정된 답변을 하도록
                .input(value)
                .model("gpt-4o-mini")
                .build();

        Response response = openAIClient.responses().create(params);
        return response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .toList();
    }
}
