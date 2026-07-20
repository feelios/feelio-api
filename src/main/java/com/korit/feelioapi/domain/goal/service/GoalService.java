package com.korit.feelioapi.domain.goal.service;

import com.korit.feelioapi.domain.goal.dto.GoalDeleteResponse;
import com.korit.feelioapi.domain.goal.dto.GoalListResponse;
import com.korit.feelioapi.domain.goal.dto.GoalRequest;
import com.korit.feelioapi.domain.goal.dto.GoalResponse;
import com.korit.feelioapi.domain.goal.entity.Goal;
import com.korit.feelioapi.domain.goal.mapper.GoalMapper;
import com.korit.feelioapi.domain.transaction.dto.TransactionSearchCondition;
import com.korit.feelioapi.domain.transaction.dto.TransactionTotalDto;
import com.korit.feelioapi.domain.transaction.mapper.TransactionMapper;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 목표 CRUD (API-CONTRACT §7). 항상 인증 주체 user_id 기준.
 * isMain=true 면 같은 트랜잭션에서 기존 대표 목표를 해제해 대표는 항상 최대 1건.
 */
@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalMapper goalMapper;

    @Transactional(readOnly = true)
    public GoalListResponse getGoals(Long userId) {
        List<GoalResponse> goals = goalMapper.findGoalsByUserId(userId).stream()
                .map(GoalResponse::of)
                .toList();
        return new GoalListResponse(goals);
    }

    @Transactional
    public GoalResponse createGoal(Long userId, GoalRequest request) {
        if (request.mainFlag()) {
            goalMapper.clearMainFlag(userId);
        }
        Goal goal = new Goal();
        goal.setUserId(userId);
        goal.setName(request.name());
        goal.setTargetAmount(request.targetAmount());
        goal.setInitialAmount(request.initialAmount() != null ? request.initialAmount() : 0L);
        goal.setStartDate(request.startDate());
        goal.setDueDate(request.dueDate());
        goal.setIsMain(request.mainFlag());
        goalMapper.insertGoal(goal); // useGeneratedKeys → goalId 채움

        return GoalResponse.of(goalMapper.findById(goal.getGoalId()));
    }

    @Transactional
    public GoalResponse updateGoal(Long userId, Long goalId, GoalRequest request) {
        getOwnedOrThrow(userId, goalId);
        if (request.mainFlag()) {
            goalMapper.clearMainFlag(userId);
        }
        Goal goal = new Goal();
        goal.setGoalId(goalId);
        goal.setName(request.name());
        goal.setTargetAmount(request.targetAmount());
        goal.setInitialAmount(request.initialAmount() != null ? request.initialAmount() : 0L);
        goal.setStartDate(request.startDate());
        goal.setDueDate(request.dueDate());
        goal.setIsMain(request.mainFlag());
        goalMapper.updateGoal(goal);

        return GoalResponse.of(goalMapper.findById(goalId));
    }

    @Transactional
    public GoalDeleteResponse deleteGoal(Long userId, Long goalId) {
        getOwnedOrThrow(userId, goalId);
        goalMapper.deleteGoal(goalId);
        return new GoalDeleteResponse(true);
    }

    /** 대상 존재 + 본인 소유 검증 (없음 NOT_FOUND / 타인 FORBIDDEN). */
    private Goal getOwnedOrThrow(Long userId, Long goalId) {
        Goal goal = goalMapper.findById(goalId);
        if (goal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!goal.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return goal;
    }
}
