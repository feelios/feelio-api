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
}
