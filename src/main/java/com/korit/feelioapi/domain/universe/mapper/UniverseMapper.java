package com.korit.feelioapi.domain.universe.mapper;

import com.korit.feelioapi.domain.universe.dto.TopCategoryDto;
import com.korit.feelioapi.domain.universe.dto.GoalRow;
import com.korit.feelioapi.domain.universe.dto.MonthKey;
import com.korit.feelioapi.domain.universe.dto.UniverseTotalDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 평행우주 시뮬 데이터 접근 (순수 SQL — UniverseMapper.xml 과 namespace/메서드명 일치).
 * goals·transactions 를 자체 SQL 로 읽는다(다른 도메인 매퍼 재사용 안 함 — 충돌 방지).
 */
@Mapper
public interface UniverseMapper {

    /** 목표 단건(소유권 검증용). 없으면 null. */
    GoalRow findGoalById(@Param("goalId") Long goalId);

    /** 거래가 있는 가장 최근 연·월. 거래 없으면 year/month 가 null. */
    MonthKey findLatestActivityMonth(@Param("userId") Long userId);

    /** 기준 월 수입·지출 합계. */
    UniverseTotalDto findMonthlyTotals(@Param("userId") Long userId,
                                       @Param("year") int year,
                                       @Param("month") int month);

    /**
     * 기준 월에 지출이 있는 카테고리 전체(금액 내림차순). 없으면 빈 목록.
     *
     * 예전에는 1건만(LIMIT 1) 가져와 그걸 강제로 '줄일 대상'으로 삼았다. 사용자가 고를 수
     * 있어야 하므로 선택지 전체를 내려준다 — 고르지 않으면 첫 번째(가장 많이 쓴 것)가 기본값이다.
     */
    List<TopCategoryDto> findExpenseCategories(@Param("userId") Long userId,
                                               @Param("year") int year,
                                               @Param("month") int month);
}
