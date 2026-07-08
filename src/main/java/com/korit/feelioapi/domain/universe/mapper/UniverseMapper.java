package com.korit.feelioapi.domain.universe.mapper;

import com.korit.feelioapi.domain.universe.dto.FocusEmotionDto;
import com.korit.feelioapi.domain.universe.dto.GoalRow;
import com.korit.feelioapi.domain.universe.dto.MonthKey;
import com.korit.feelioapi.domain.universe.dto.UniverseTotalDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    /** 기준 월 소비가 가장 몰린 감정 1건(지출 기준). 없으면 null. */
    FocusEmotionDto findFocusEmotion(@Param("userId") Long userId,
                                     @Param("year") int year,
                                     @Param("month") int month);
}
