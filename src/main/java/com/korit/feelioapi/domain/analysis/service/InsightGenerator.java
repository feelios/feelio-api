package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.InsightDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStatDto;

import java.util.List;

/**
 * 월간 집계로부터 인사이트 문장을 생성한다.
 * 구현 교체 지점: 규칙기반(RuleBasedInsightGenerator) → 추후 AI(Claude/GPT) 구현으로 스위치 가능.
 */
public interface InsightGenerator {

    List<InsightDto> generate(int year,
                              int month,
                              List<EmotionStatDto> byEmotion,
                              List<CategoryStatDto> byCategory,
                              List<TimeSlotStatDto> byTimeSlot);
}
