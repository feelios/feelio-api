package com.korit.feelioapi.domain.summary.mapper;

import com.korit.feelioapi.domain.summary.dto.CalendarDayDto;
import com.korit.feelioapi.domain.summary.dto.EmotionSummaryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SummaryMapper {
    List<CalendarDayDto> findCalendarSummary(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month
    );
    
    List<EmotionSummaryDto> findEmotionSummary(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month
    );

    long findMonthlyExpense(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month
    );
}
