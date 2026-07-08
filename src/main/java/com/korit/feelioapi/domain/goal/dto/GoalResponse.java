package com.korit.feelioapi.domain.goal.dto;

import com.korit.feelioapi.domain.goal.entity.Goal;

import java.time.LocalDate;

/**
 * 목표 응답 객체 (API-CONTRACT §7).
 */
public record GoalResponse(
        Long goalId,
        String name,
        Integer targetAmount,
        Integer currentAmount,
        LocalDate startDate,
        LocalDate dueDate,
        boolean isMain,
        String status
) {
    public static GoalResponse of(Goal goal) {
        return new GoalResponse(
                goal.getGoalId(),
                goal.getName(),
                goal.getTargetAmount(),
                goal.getCurrentAmount(),
                goal.getStartDate(),
                goal.getDueDate(),
                Boolean.TRUE.equals(goal.getIsMain()),
                goal.getStatus()
        );
    }
}
