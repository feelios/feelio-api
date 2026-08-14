package com.korit.feelioapi.domain.analysis.mapper;

import com.korit.feelioapi.domain.analysis.dto.AnalysisTotalDto;
import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import com.korit.feelioapi.domain.analysis.dto.EmotionStatDto;
import com.korit.feelioapi.domain.analysis.dto.InsightDto;
import com.korit.feelioapi.domain.analysis.dto.TimeSlotStat;
import com.korit.feelioapi.domain.analysis.entity.AiInsight;
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

    List<CategoryStatDto> findWeeklyExpenseByCategory(
            @Param("userId") Long userId,
            @Param("startAt") java.time.LocalDateTime startAt,
            @Param("endAt") java.time.LocalDateTime endAt
    );

    List<EmotionStatDto> findExpenseByEmotion(@Param("userId") Long userId,
                                              @Param("year") int year,
                                              @Param("month") int month);

    List<TimeSlotStat> findExpenseByTimeSlot(@Param("userId") Long userId,
                                             @Param("year") int year,
                                             @Param("month") int month);

    List<com.korit.feelioapi.domain.analysis.dto.MonthlyDataStat> findMonthlyTrend(@Param("userId") Long userId,
                                                                                   @Param("startDate") java.time.LocalDate startDate,
                                                                                   @Param("endDate") java.time.LocalDate endDate);

    List<com.korit.feelioapi.domain.analysis.dto.CategoryCurrentStat> findCurrentCategoryStats(@Param("userId") Long userId,
                                                                                               @Param("year") int year,
                                                                                               @Param("month") int month);

    List<com.korit.feelioapi.domain.analysis.dto.CategoryPrevStat> findPrevCategoryStats(@Param("userId") Long userId,
                                                                                         @Param("year") int year,
                                                                                         @Param("month") int month);

    /**
     * 예산 기준선용 최근 구간 집계. [startAt, endAt) 반개구간이며 lastMonthStart 이후가 전월분이다.
     * 세 파라미터 모두 Service 가 조회 대상 월을 기준으로 계산해 넘긴다(오늘 날짜 기준이 아니다).
     */
    List<com.korit.feelioapi.domain.analysis.dto.CategoryRecentStat> findRecentCategoryStats(
            @Param("userId") Long userId,
            @Param("startAt") java.time.LocalDateTime startAt,
            @Param("endAt") java.time.LocalDateTime endAt,
            @Param("lastMonthStart") java.time.LocalDateTime lastMonthStart);

    /** 위 구간에서 지출 기록이 있는 달 수. 평균의 분모. */
    int countActiveMonths(@Param("userId") Long userId,
                          @Param("startAt") java.time.LocalDateTime startAt,
                          @Param("endAt") java.time.LocalDateTime endAt);

    /** 저장된 월간 인사이트 조회. 없으면 빈 리스트 — 호출 측이 생성 여부를 판단한다. */
    List<AiInsight> findInsights(@Param("userId") Long userId,
                                 @Param("year") int year,
                                 @Param("month") int month);

    /** 특정 타입의 인사이트 단건 조회 */
    AiInsight findInsightByType(@Param("userId") Long userId,
                                @Param("year") int year,
                                @Param("month") int month,
                                @Param("type") String type);

    /** 해당 월의 인사이트 모두 삭제. 새 데이터 넣기 전 중복 방지 역할을 한다. */
    void deleteInsights(@Param("userId") Long userId,
                        @Param("year") int year,
                        @Param("month") int month);

    void deleteInsightByType(@Param("userId") Long userId,
                             @Param("year") int year,
                             @Param("month") int month,
                             @Param("type") String type);

    void deleteAllInsights(@Param("userId") Long userId);

    void insertInsights(@Param("userId") Long userId,
                        @Param("year") int year,
                        @Param("month") int month,
                        @Param("insights") List<InsightDto> insights);

    void insertInsight(@Param("userId") Long userId,
                       @Param("year") int year,
                       @Param("month") int month,
                       @Param("type") String type,
                       @Param("content") String content);
}
