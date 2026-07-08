package com.korit.feelioapi.domain.goal.mapper;

import com.korit.feelioapi.domain.goal.entity.Goal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 목표 데이터 접근 (순수 SQL — GoalMapper.xml 과 namespace/메서드명 일치).
 * 모든 조회·수정은 user_id 기준(소유권은 Service 에서 검증).
 */
@Mapper
public interface GoalMapper {

    List<Goal> findGoalsByUserId(@Param("userId") Long userId);

    Goal findById(@Param("goalId") Long goalId);

    void insertGoal(Goal goal);

    void updateGoal(Goal goal);

    /** isMain=true 요청 시, 해당 사용자의 기존 대표 목표를 모두 해제. */
    void clearMainFlag(@Param("userId") Long userId);

    void deleteGoal(@Param("goalId") Long goalId);
}
