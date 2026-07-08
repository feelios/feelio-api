package com.korit.feelioapi.domain.summary.service;

import com.korit.feelioapi.domain.summary.dto.CalendarDayDto;
import com.korit.feelioapi.domain.summary.dto.CalendarSummaryResponse;
import com.korit.feelioapi.domain.summary.dto.EmotionSummaryDto;
import com.korit.feelioapi.domain.summary.dto.EmotionSummaryResponse;
import com.korit.feelioapi.domain.summary.mapper.SummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SummaryService {

    private final SummaryMapper summaryMapper;

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
}
