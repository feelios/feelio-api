package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.AiInsightsResponse;
import com.korit.feelioapi.domain.analysis.dto.AiReportResponseDto;
import com.korit.feelioapi.domain.analysis.dto.AnalysisResponse;
import com.korit.feelioapi.domain.analysis.dto.AnalysisTotalDto;
import com.korit.feelioapi.domain.analysis.dto.CategoryBaseline;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 월간 분석 (API-CONTRACT §9). 지출 기준 집계 + 인사이트 문장. 항상 user_id 기준.
 */
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    /**
     * 예산 기준선을 만들 때 되돌아보는 개월 수.
     *
     * 1개월은 흔들림이 너무 컸고, 6개월은 반년 전 씀씀이가 이번 달 목표를 붙잡아 최근 변화를 못 따라간다.
     * 3개월이면 여행처럼 몰아 쓰는 항목도 한 번은 걸리면서 최근 추세도 남는다.
     */
    private static final int BASELINE_MONTHS = 3;

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
    private final FactReportService factReportService;
    private final ChallengeService challengeService;
    private final EmotionAnalysisService emotionAnalysisService;

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
    public AiInsightsResponse getAiInsights(Long userId, Integer reqYear, Integer reqMonth) {
        java.time.LocalDate today = java.time.LocalDate.now();
        int year = (reqYear != null) ? reqYear : today.getYear();
        int month = (reqMonth != null) ? reqMonth : today.getMonthValue();

        List<CategoryStatDto> byCategory = analysisMapper.findExpenseByCategory(userId, year, month);
        List<EmotionStatDto> byEmotion = analysisMapper.findExpenseByEmotion(userId, year, month);
        List<TimeSlotStatDto> byTimeSlot = toTimeSlotDtos(analysisMapper.findExpenseByTimeSlot(userId, year, month));

        // 소진율은 분자·분모를 한 곳에서 받는다. 예산 항목 밖의 지출을 분자에만 넣으면
        // 같은 화면의 '예산 소진율'과 다른 숫자가 나온다.
        BudgetUsage usage = budgetUsage(userId, reqYear, reqMonth);

        // 챌린지는 ChallengeService 가 최근 7일 카테고리별 지출로 만든다(ai-report 와 같은 정본).

        java.time.LocalDate targetDate = (year == today.getYear() && month == today.getMonthValue()) 
                ? today 
                : java.time.LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
        List<CategoryStatDto> weeklyCategories = analysisMapper.findWeeklyExpenseByCategory(
                userId, targetDate.minusDays(6).atStartOfDay(), targetDate.plusDays(1).atStartOfDay());


        return AiInsightsResponse.builder()
                .aiQuickInsights(quickInsightAssembler.assembleQuickInsights(
                        byEmotion, byCategory, byTimeSlot, weeklyCategories, usage.expense(), usage.budget()))
                .emotionCards(quickInsightAssembler.assembleEmotionCards(byEmotion, byCategory, byTimeSlot))
                .evidence(List.of())
                .pattern(AiInsightsResponse.AiPattern.builder().count(0).build())
                .build();
    }

    /**
     * AI 연동 전에도 프론트가 사용할 수 있는 분석 리포트 뼈대.
     * 위험도는 순수 자바 계산이며 OpenAI 클라이언트를 호출하지 않는다.
     */
    public AiReportResponseDto getAiReport(Long userId, Integer reqYear, Integer reqMonth) {
        java.time.LocalDate today = java.time.LocalDate.now();
        int year = (reqYear != null) ? reqYear : today.getYear();
        int month = (reqMonth != null) ? reqMonth : today.getMonthValue();

        // 분자·분모를 한 쌍으로 받는다(#2 소진율·위험도 색 불일치).
        BudgetUsage usage = budgetUsage(userId, reqYear, reqMonth);
        long totalExpense = usage.expense();
        long budget = usage.budget();
        SpendStatus spendStatus = SpendStatus.of(totalExpense, budget);
        List<CategoryStatDto> monthlyCategories = analysisMapper.findExpenseByCategory(userId, year, month);
        String topCategory = monthlyCategories.stream()
                .findFirst()
                .map(CategoryStatDto::name)
                .orElse(null);
        List<EmotionStatDto> monthlyEmotions = analysisMapper.findExpenseByEmotion(userId, year, month);
        String topTimeSlot = toTimeSlotDtos(analysisMapper.findExpenseByTimeSlot(userId, year, month)).stream()
                .max(java.util.Comparator.comparingLong(TimeSlotStatDto::amount))
                .map(TimeSlotStatDto::label)
                .orElse(null);
        
        java.time.LocalDate targetDate = (year == today.getYear() && month == today.getMonthValue()) 
                ? today 
                : java.time.LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
        java.time.LocalDateTime weeklyStart = targetDate.minusDays(6).atStartOfDay();
        java.time.LocalDateTime weeklyEnd = targetDate.plusDays(1).atStartOfDay();
        List<CategoryStatDto> weeklyCategories = analysisMapper.findWeeklyExpenseByCategory(
                userId, weeklyStart, weeklyEnd);
        double usageRate = budget > 0
                ? Math.round(totalExpense * 1000.0 / budget) / 10.0
                : 0.0;

        List<AiInsight> saved = analysisMapper.findInsights(userId, year, month);
        boolean isStale = saved.isEmpty() || isStale(saved, year, month);
        
        String factText = null;
        String emotionText = null;
        
        if (!isStale) {
            for (AiInsight insight : saved) {
                if ("FACT_BOMBER".equals(insight.getInsightType())) factText = insight.getContent();
                if ("EMO_BOMBER".equals(insight.getInsightType())) emotionText = insight.getContent();
            }
            if (factText == null || emotionText == null) {
                isStale = true;
            }
        }
        
        if (isStale) {
            if (totalExpense == 0) {
                factText = FactReportService.FALLBACK_MESSAGE;
                emotionText = "이번 달 소비 기록이 없어 감정 분석을 건너뜁니다.";
            } else {
                factText = factReportService.generate(spendStatus, totalExpense, budget, topCategory);
                emotionText = emotionAnalysisService.generate(monthlyEmotions, topCategory, topTimeSlot);
                
                try {
                    aiInsightStore.replaceByType(userId, year, month, "FACT_BOMBER", factText);
                    aiInsightStore.replaceByType(userId, year, month, "EMO_BOMBER", emotionText);
                } catch (Exception e) {
                    log.warn("인사이트 개별 저장 실패", e);
                }
            }
        }

        return new AiReportResponseDto(
                year,
                month,
                totalExpense,
                budget,
                usageRate,
                ConsumptionRisk.of(totalExpense, budget).name(),
                new AiReportResponseDto.AiContent(
                        factText,
                        challengeService.generate(weeklyCategories),
                        emotionText
                )
        );
    }

    public List<AiInsight> debugInsights(Long userId, int year, int month) {
        return analysisMapper.findInsights(userId, year, month);
    }

    /**
     * 예산 소진율의 분자와 분모. 둘을 <b>같은 카테고리 집합</b>에서 뽑는다.
     *
     * <p>예전에는 분모만 예산 항목의 합이고 분자는 {@code findMonthlyTotals} 의 월 전체 지출이었다.
     * 예산 대상이 아닌 카테고리(is_budgetable = 0)의 지출까지 분자에 들어가, 정의가 짝이 맞지 않는
     * 비율이 나왔다. 화면에서도 같은 달을 놓고 '예산 소진율 59%(초록)' 옆에 '소비 위험도 주의(노랑)'가
     * 나란히 떴다 — 프론트는 예산 항목만 세고 서버는 전액을 세서 생긴 차이였다.
     */
    public record BudgetUsage(long expense, long budget) {}

    /**
     * 이번 달 예산 대비 지출. 예산 항목 목록 한 벌에서 분자·분모를 함께 만든다.
     * 활성 목표가 없거나 최근 기록이 없으면 예산이 0 이 나오고, 그 경우 위험도는 '예산 미설정'이 된다.
     */
    public BudgetUsage budgetUsage(Long userId, Integer reqYear, Integer reqMonth) {
        List<com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse.BudgetItem> items =
                getBudgetStatus(userId, reqYear, reqMonth).budgetItems();
        long expense = items.stream()
                .mapToLong(item -> item.currentAmount() == null ? 0L : item.currentAmount())
                .sum();
        long budget = items.stream()
                .mapToLong(item -> item.budget() == null ? 0L : item.budget())
                .sum();
        return new BudgetUsage(expense, budget);
    }

    /**
     * 이번 달 예산 총액(A6-4 동적 예산의 카테고리별 합).
     * 소진율을 낼 때는 {@link #budgetUsage} 를 쓴다 — 분자를 따로 구하면 정의가 갈라진다.
     */
    public long totalBudget(Long userId, Integer reqYear, Integer reqMonth) {
        return budgetUsage(userId, reqYear, reqMonth).budget();
    }

    /**
     * 지출 추이 카드. <b>막대 7개는 오늘 기준으로 고정</b>하고, 요약 숫자만 조회한 달을 따라간다.
     *
     * <p>둘을 나눈 이유가 있다. 예전에는 인자를 아예 안 받아 오늘에만 묶여 있었고, 달을 바꿔 눌러도
     * 총액과 '전월 대비 N%' 가 그대로였다 — 월 전환이 동작하지 않는 것처럼 보였다.
     * 그렇다고 창까지 조회한 달을 따라 미끄러지게 하면, 막대를 누를 때마다 창이 다시 잡혀
     * 방금 누른 막대가 자리를 옮긴다. 누를 때마다 차트가 통째로 재배열되는 셈이다.
     * 창은 '최근 7개월'이라는 고정된 배경으로 두고, 그 위에서 어느 달을 보고 있는지만 바뀌는 게 맞다.
     *
     * <p>조회한 달이 창 밖일 수 있으므로(월 전환기로 7개월보다 더 거슬러 갈 수 있다)
     * SQL 구간은 창과 조회 달을 모두 덮도록 잡는다.
     */
    @Transactional(readOnly = true)
    public com.korit.feelioapi.domain.analysis.dto.MonthlyTrendResponse getMonthlyTrend(Long userId,
                                                                                        Integer reqYear,
                                                                                        Integer reqMonth) {
        java.time.LocalDate today = java.time.LocalDate.now();

        // 창의 마지막 칸은 언제나 당월. 1일로 맞춰야 말일 근처에서 달이 밀리지 않는다.
        java.time.LocalDate windowEnd = today.withDayOfMonth(1);
        java.time.LocalDate windowStart = windowEnd.minusMonths(6);

        java.time.LocalDate selected = java.time.LocalDate.of(
                (reqYear != null) ? reqYear : today.getYear(),
                (reqMonth != null) ? reqMonth : today.getMonthValue(),
                1);
        java.time.LocalDate selectedPrev = selected.minusMonths(1);

        java.time.LocalDate queryStart = windowStart.isBefore(selectedPrev) ? windowStart : selectedPrev;
        java.time.LocalDate queryEnd = (windowEnd.isAfter(selected) ? windowEnd : selected).plusMonths(1);

        List<com.korit.feelioapi.domain.analysis.dto.MonthlyDataStat> stats = analysisMapper.findMonthlyTrend(userId, queryStart, queryEnd);
        Map<String, Long> statMap = stats.stream()
                .collect(Collectors.toMap(com.korit.feelioapi.domain.analysis.dto.MonthlyDataStat::yearMonth, com.korit.feelioapi.domain.analysis.dto.MonthlyDataStat::amount));

        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM");

        List<com.korit.feelioapi.domain.analysis.dto.MonthlyTrendResponse.MonthlyData> monthlyData = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate targetMonth = windowEnd.minusMonths(i);
            String yearMonthStr = targetMonth.format(formatter);
            monthlyData.add(new com.korit.feelioapi.domain.analysis.dto.MonthlyTrendResponse.MonthlyData(
                    yearMonthStr,
                    targetMonth.getMonthValue() + "월",
                    statMap.getOrDefault(yearMonthStr, 0L)
            ));
        }

        long currentMonthAmount = statMap.getOrDefault(selected.format(formatter), 0L);
        long previousMonthAmount = statMap.getOrDefault(selectedPrev.format(formatter), 0L);

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
        } else if (currentMonthAmount > 0 && currentMonthAmount == previousMonthAmount) {
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
    public com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse getBudgetStatus(Long userId, Integer reqYear, Integer reqMonth) {
        java.time.LocalDate now = java.time.LocalDate.now();
        int currentYear = (reqYear != null) ? reqYear : now.getYear();
        int currentMonth = (reqMonth != null) ? reqMonth : now.getMonthValue();

        java.time.LocalDate targetDate = java.time.LocalDate.of(currentYear, currentMonth, 1);

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

        /*
         * 예산 기준선은 최근 3개월 평균이다(전월 한 달이 아니다).
         *
         * 구간은 모두 '조회 대상 월'을 기준으로 잡는다. 예전에는 전전월만 now.minusMonths(2) 로
         * 오늘 날짜에서 구해, 지난달 화면을 열면 엉뚱한 달과 비교하고 있었다.
         */
        java.time.LocalDateTime windowStart = targetDate.minusMonths(BASELINE_MONTHS).atStartOfDay();
        java.time.LocalDateTime windowEnd = targetDate.atStartOfDay();
        java.time.LocalDateTime lastMonthStart = targetDate.minusMonths(1).atStartOfDay();

        int activeMonths = analysisMapper.countActiveMonths(userId, windowStart, windowEnd);

        /*
         * 기준선 창이 다 차지 않으면 예산을 매기지 않는다.
         *
         * 3개월 평균을 기준으로 삼기로 했으면 3개월이 있어야 한다. 없는 달을 0원으로 세든
         * 기록이 있는 달 수로 나누든, 둘 다 실제 씀씀이보다 한참 낮은 기준선을 만든다.
         * 기록을 막 시작한 사용자의 7월 화면이 그랬다 — 창(4·5·6월)에 6월 하나뿐이라
         * 문화·취미 기준선이 14,450원으로 잡혔고, 정작 그 달에 120,600원을 써서 754% 초과가 떴다.
         * 기록 첫 달인 5월은 총예산이 8,900원인데도 화면은 그냥 '초과'라고 판정했다.
         *
         * 예산 0원은 프론트에서 '측정중'이 된다. 지출 실적은 그대로 보여주되 초과 판정만 하지 않는다 —
         * 근거가 없을 때 판정을 미루는 쪽이, 근거 없는 숫자로 초과를 선언하는 것보다 정직하다.
         */
        if (activeMonths < BASELINE_MONTHS) {
            return new com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse(currentStats.stream()
                    .filter(com.korit.feelioapi.domain.analysis.dto.CategoryCurrentStat::isBudgetable)
                    .map(stat -> new com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse.BudgetItem(
                            stat.categoryName(),
                            stat.dominantEmotion() != null ? stat.dominantEmotion() : "보통",
                            stat.currentAmount(),
                            0L,
                            0L))
                    .toList());
        }

        List<CategoryBaseline> baselines = analysisMapper
                .findRecentCategoryStats(userId, windowStart, windowEnd, lastMonthStart).stream()
                .map(stat -> stat.toBaseline(activeMonths))
                .toList();

        Map<Long, CategoryBaseline> baselineMap = baselines.stream()
                .collect(Collectors.toMap(CategoryBaseline::categoryId, b -> b));

        BudgetPlan plan = BudgetPlan.of(baselines, totalRequiredSavings);

        List<com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse.BudgetItem> budgetItems = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();

        for (com.korit.feelioapi.domain.analysis.dto.CategoryCurrentStat currentStat : currentStats) {

            if (!currentStat.isBudgetable()) {
                continue; // 예산 제외 항목
            }

            CategoryBaseline baseline = baselineMap.get(currentStat.categoryId());
            long baseAmount = baseline == null ? 0L : baseline.baselineAmount();
            long budget = plan.budgetFor(
                    currentStat.categoryId(), baseAmount, currentStat.currentAmount(), currentStat.isFixed());

            seen.add(currentStat.categoryId());
            budgetItems.add(new com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse.BudgetItem(
                    currentStat.categoryName(),
                    currentStat.dominantEmotion() != null ? currentStat.dominantEmotion() : "보통",
                    currentStat.currentAmount(),
                    baseAmount,
                    budget
            ));
        }

        /*
         * 최근에 쓰던 카테고리인데 이번 달엔 아직 지출이 없는 경우에도 줄을 만든다.
         *
         * 예전에는 이번 달 지출이 있는 카테고리만 예산을 잡았다. 그래서 매달 쓰던 식비·생활용품이
         * 월초에 통째로 총예산에서 빠졌고, 총예산이 실제 생활비보다 한참 작게 잡혀 소진율이
         * 금방 올라갔다. 위 '소비 위험도'의 분모가 되는 값이라 이게 틀리면 판정 전체가 틀린다.
         *
         * 화면이 지저분해지지는 않는다. 지출 0원이면 진행률도 0이라 목록 맨 아래로 밀리고,
         * 프론트는 급박도순 상위 5개만 노출한다 — '지금 조정해야 할 예산'이 여전히 위에 온다.
         * 이번 달 기록이 없어 감정(말랑이)은 비는데, 그 자리는 프론트가 이미 비워 그린다.
         */
        for (CategoryBaseline baseline : baselines) {
            if (!baseline.isBudgetable() || seen.contains(baseline.categoryId()) || baseline.baselineAmount() <= 0) {
                continue;
            }
            budgetItems.add(new com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse.BudgetItem(
                    baseline.categoryName(),
                    "보통",
                    0L,
                    baseline.baselineAmount(),
                    plan.budgetFor(baseline.categoryId(), baseline.baselineAmount(), 0L, baseline.isFixed())
            ));
        }

        return new com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse(budgetItems);
    }

    public List<String> getAiChatResponse(String value) {
        return List.of("말랑이가 분석 중이에요! (AI 기능 점검 중)");
    }
}
