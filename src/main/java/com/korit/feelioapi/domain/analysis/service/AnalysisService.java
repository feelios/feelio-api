package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.AiInsightsResponse;
import com.korit.feelioapi.domain.analysis.dto.AiReportResponseDto;
import com.korit.feelioapi.domain.analysis.dto.AnalysisResponse;
import com.korit.feelioapi.domain.analysis.dto.AnalysisTotalDto;
import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.InsightDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStat;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStatDto;
import com.korit.feelioapi.domain.analysis.entity.AiInsight;
import com.korit.feelioapi.domain.analysis.mapper.AnalysisMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
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

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

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
    private final AiInsightStore aiInsightStore;
    private final AiQuickInsightAssembler quickInsightAssembler;

    /** 이번 달 인사이트를 몇 시간 뒤에 다시 만들지. 짧게 잡을수록 GPT 호출이 늘어난다. */
    @Value("${feelio.insight.ttl-hours:6}")
    private long insightTtlHours = 6;

    /**
     * 인사이트 생성기가 외부 API(GPT)일 수 있어 @Transactional 을 걸지 않는다.
     * 걸면 모델 응답을 기다리는 동안 DB 커넥션(풀 5개)을 붙잡게 된다. 집계 조회는 각각 단건 SELECT 라 문제없다.
     */
    public AnalysisResponse getMonthlyAnalysis(Long userId, int year, int month) {
        AnalysisTotalDto totals = analysisMapper.findMonthlyTotals(userId, year, month);
        List<CategoryStatDto> byCategory = analysisMapper.findExpenseByCategory(userId, year, month);
        List<EmotionStatDto> byEmotion = analysisMapper.findExpenseByEmotion(userId, year, month);
        List<TimeSlotStatDto> byTimeSlot = toTimeSlotDtos(analysisMapper.findExpenseByTimeSlot(userId, year, month));

        List<InsightDto> insights = loadOrGenerateInsights(userId, year, month, byEmotion, byCategory, byTimeSlot);

        return new AnalysisResponse(
                year, month,
                totals.totalIncome(), totals.totalExpense(),
                byCategory, byEmotion, byTimeSlot, insights
        );
    }

    /**
     * 저장된 인사이트를 쓰고, 없거나 오래됐을 때만 새로 만들어 ai_insights 에 남긴다(계약 §9).
     * 생성은 연·월당 한 번만 일어나고 이후 조회는 DB 에서 읽는다.
     */
    private List<InsightDto> loadOrGenerateInsights(Long userId,
                                                    int year,
                                                    int month,
                                                    List<EmotionStatDto> byEmotion,
                                                    List<CategoryStatDto> byCategory,
                                                    List<TimeSlotStatDto> byTimeSlot) {
        List<AiInsight> saved = analysisMapper.findInsights(userId, year, month);
        if (!saved.isEmpty() && !isStale(saved, year, month)) {
            return toInsightDtos(saved);
        }

        List<InsightDto> generated = insightGenerator.generate(year, month, byEmotion, byCategory, byTimeSlot);
        if (generated.isEmpty()) {
            // 만들 문장이 없는 달. 빈 행을 남기면 다음 조회에서 재생성이 막히므로 저장하지 않고,
            // 기존 저장본이 있으면 그대로 내보낸다(생성 실패로 화면이 비지 않게).
            return toInsightDtos(saved);
        }

        try {
            aiInsightStore.replace(userId, year, month, generated);
        } catch (DataAccessException e) {
            // 저장에 실패해도 이번 응답은 정상적으로 내보낸다. 다음 조회 때 다시 시도하게 된다.
            log.warn("인사이트 저장 실패(userId={}, {}-{}). 응답은 생성 결과로 내보낸다.", userId, year, month, e);
        }
        return generated;
    }

    /**
     * 지난 달 이전은 거래가 더 늘지 않으므로 영구 캐시한다.
     * 이번 달만 저장 후 ttl 이 지나면 다시 만든다 — 거래가 계속 쌓이는데 문장이 고정되면 안 되기 때문이다.
     */
    private boolean isStale(List<AiInsight> saved, int year, int month) {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (year != today.getYear() || month != today.getMonthValue()) {
            return false;
        }
        java.time.LocalDateTime createdAt = saved.get(0).getCreatedAt();
        if (createdAt == null) {
            return true;
        }
        return createdAt.isBefore(java.time.LocalDateTime.now().minusHours(insightTtlHours));
    }

    private List<InsightDto> toInsightDtos(List<AiInsight> rows) {
        return rows.stream()
                .map(row -> new InsightDto(row.getInsightType(), row.getContent()))
                .toList();
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

    /**
     * AI 분석 화면 상단 요약. 이번 달 집계 + 전월 지출 비교로 만든다.
     * evidence·pattern 은 프론트가 /api/transactions/patterns 에서 따로 받아가므로 여기서는 비워 둔다.
     */
    /**
     * 문장 생성이 외부 API(GPT)를 탈 수 있어 @Transactional 을 걸지 않는다(커넥션 점유 방지).
     * 소비 위험도는 예산 소진율로 자바에서 판정하고, GPT 는 문장만 만든다.
     */
    public AiInsightsResponse getAiInsights(Long userId) {
        java.time.LocalDate today = java.time.LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        List<CategoryStatDto> byCategory = analysisMapper.findExpenseByCategory(userId, year, month);
        List<EmotionStatDto> byEmotion = analysisMapper.findExpenseByEmotion(userId, year, month);
        List<TimeSlotStatDto> byTimeSlot = toTimeSlotDtos(analysisMapper.findExpenseByTimeSlot(userId, year, month));

        long currentExpense = analysisMapper.findMonthlyTotals(userId, year, month).totalExpense();

        return AiInsightsResponse.builder()
                .aiQuickInsights(quickInsightAssembler.assembleQuickInsights(
                        byEmotion, byCategory, byTimeSlot, currentExpense, totalBudget(userId)))
                .emotionCards(quickInsightAssembler.assembleEmotionCards(byEmotion, byCategory, byTimeSlot))
                .evidence(List.of())
                .pattern(AiInsightsResponse.AiPattern.builder().count(0).build())
                .build();
    }

    /**
     * AI 연동 전에도 프론트가 사용할 수 있는 분석 리포트 뼈대.
     * 위험도는 순수 자바 계산이며 OpenAI 클라이언트를 호출하지 않는다.
     */
    public AiReportResponseDto getAiReport(Long userId) {
        java.time.LocalDate today = java.time.LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        long totalExpense = analysisMapper.findMonthlyTotals(userId, year, month).totalExpense();
        long budget = totalBudget(userId);
        double usageRate = budget > 0
                ? Math.round(totalExpense * 1000.0 / budget) / 10.0
                : 0.0;

        return new AiReportResponseDto(
                year,
                month,
                totalExpense,
                budget,
                usageRate,
                ConsumptionRisk.of(totalExpense, budget).name(),
                new AiReportResponseDto.AiContent(
                        "팩트 분석을 준비 중이에요.",
                        "맞춤 챌린지를 준비 중이에요.",
                        "감정 소비 분석을 준비 중이에요."
                )
        );
    }

    /**
     * 이번 달 예산 총액(A6-4 동적 예산의 카테고리별 합).
     * 활성 목표가 없거나 전월 기록이 없으면 0 이 나오고, 그 경우 위험도는 '예산 미설정'으로 처리된다.
     */
    private long totalBudget(Long userId) {
        return getBudgetStatus(userId).budgetItems().stream()
                .mapToLong(item -> item.budget() == null ? 0L : item.budget())
                .sum();
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
