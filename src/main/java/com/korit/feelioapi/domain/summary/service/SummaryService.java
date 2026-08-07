package com.korit.feelioapi.domain.summary.service;

import com.korit.feelioapi.domain.summary.dto.CalendarDayDto;
import com.korit.feelioapi.domain.summary.dto.CalendarSummaryResponse;
import com.korit.feelioapi.domain.summary.dto.EmotionSummaryDto;
import com.korit.feelioapi.domain.summary.dto.EmotionSummaryResponse;
import com.korit.feelioapi.domain.summary.dto.MallangCommentResponse;
import com.korit.feelioapi.domain.summary.dto.SummaryAiCommentResponse;
import com.korit.feelioapi.domain.summary.mapper.SummaryMapper;
import com.korit.feelioapi.domain.analysis.service.AnalysisService;
import com.korit.feelioapi.domain.analysis.service.SpendStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SummaryService {

    private final SummaryMapper summaryMapper;
    private final SummaryAiCommentGenerator aiCommentGenerator;
    private final MallangCommentGenerator mallangCommentGenerator;
    private final RuleMallangCommentGenerator ruleMallangCommentGenerator;
    private final AnalysisService analysisService;
    private final ConcurrentHashMap<AiCommentCacheKey, SummaryAiCommentResponse> aiCommentCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AiCommentCacheKey, MallangCommentResponse> mallangCommentCache = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public CalendarSummaryResponse getCalendarSummary(Long userId, Integer year, Integer month) {
        List<CalendarDayDto> days = summaryMapper.findCalendarSummary(userId, year, month);
        return new CalendarSummaryResponse(days);
    }

    @Transactional(readOnly = true)
    public EmotionSummaryResponse getEmotionSummary(Long userId, Integer year, Integer month) {
        List<EmotionSummaryDto> currentMonthEmotions = summaryMapper.findEmotionSummary(userId, year, month);

        Integer prevYear = month == 1 ? year - 1 : year;
        Integer prevMonth = month == 1 ? 12 : month - 1;

        List<EmotionSummaryDto> prevMonthEmotions = summaryMapper.findEmotionSummary(userId, prevYear, prevMonth);

        return new EmotionSummaryResponse(currentMonthEmotions, prevMonthEmotions);
    }

    /**
     * 홈의 나머지 API와 분리해 AI 지연·실패가 홈 로딩을 막지 않게 한다.
     * DB에는 저장하지 않고 사용자별 하루 한 번만 생성한다.
     */
    public SummaryAiCommentResponse getAiComment(Long userId) {
        LocalDate today = LocalDate.now();
        AiCommentCacheKey key = new AiCommentCacheKey(userId, today);
        aiCommentCache.keySet().removeIf(existing -> existing.date().isBefore(today));

        return aiCommentCache.computeIfAbsent(key, ignored -> {
            int year = today.getYear();
            int month = today.getMonthValue();
            LocalDate previousMonth = today.minusMonths(1);
            long currentExpense = summaryMapper.findMonthlyExpense(userId, year, month);
            long previousExpense = summaryMapper.findMonthlyExpense(
                    userId, previousMonth.getYear(), previousMonth.getMonthValue());

            String comment = currentExpense == 0
                    ? null
                    : aiCommentGenerator.generate(year, month, currentExpense, previousExpense);
            return new SummaryAiCommentResponse(comment);
        });
    }

    /**
     * 홈 말랑이 코멘트 (A8-3).
     *
     * <p>상태 판정과 수치는 서버가 계산하고 AI 는 문장만 만든다.
     * AI 가 실패하거나 형식을 어기면 규칙기반 문장으로 대체해 항상 두 문장을 채워 응답한다.
     */
    @Transactional(readOnly = true)
    public MallangCommentResponse getMallangComment(Long userId) {
        LocalDate today = LocalDate.now();
        AiCommentCacheKey key = new AiCommentCacheKey(userId, today);
        mallangCommentCache.keySet().removeIf(existing -> existing.date().isBefore(today));

        return mallangCommentCache.computeIfAbsent(key, ignored -> {
            long expense = summaryMapper.findMonthlyExpense(userId, today.getYear(), today.getMonthValue());
            long budget = analysisService.totalBudget(userId, today.getYear(), today.getMonthValue());
            SpendStatus status = SpendStatus.of(expense, budget);
            int usageRate = budget > 0 ? (int) Math.round(expense * 100.0 / budget) : 0;

            MallangComment comment = mallangCommentGenerator.generate(status, expense, budget, usageRate);
            if (comment == null || !comment.isUsable()) {
                comment = ruleMallangCommentGenerator.generate(status, expense, budget, usageRate);
            }
            return new MallangCommentResponse(comment.evaluation(), comment.encouragement(), status.name());
        });
    }

    private record AiCommentCacheKey(Long userId, LocalDate date) {
    }
}
