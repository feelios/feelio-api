package com.korit.feelioapi.domain.analysis.mapper;

import com.korit.feelioapi.domain.analysis.dto.AnalysisTotalDto;
import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 월간 분석 집계 접근 (순수 SQL — AnalysisMapper.xml 과 namespace/메서드명 일치).
 * 모든 집계는 user_id·연·월 기준, 지출(EXPENSE) 기준.
 */
@Mapper
public interface AnalysisMapper {

    AnalysisTotalDto findMonthlyTotals(@Param("userId") Long userId,
                                       @Param("year") int year,
                                       @Param("month") int month);

    List<CategoryStatDto> findExpenseByCategory(@Param("userId") Long userId,
                                                @Param("year") int year,
                                                @Param("month") int month);

    List<EmotionStatDto> findExpenseByEmotion(@Param("userId") Long userId,
                                              @Param("year") int year,
                                              @Param("month") int month);

    List<TimeSlotStat> findExpenseByTimeSlot(@Param("userId") Long userId,
                                             @Param("year") int year,
                                             @Param("month") int month);

    List<com.korit.feelioapi.domain.analysis.dto.MonthlyDataStat> findMonthlyTrend(@Param("userId") Long userId,
                                                                                   @Param("startDate") java.time.LocalDate startDate,
                                                                                   @Param("endDate") java.time.LocalDate endDate);
}
