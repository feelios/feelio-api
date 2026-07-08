package com.korit.feelioapi.domain.summary.service;

import com.korit.feelioapi.domain.summary.dto.CalendarDayDto;
import com.korit.feelioapi.domain.summary.dto.CalendarSummaryResponse;
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
}
