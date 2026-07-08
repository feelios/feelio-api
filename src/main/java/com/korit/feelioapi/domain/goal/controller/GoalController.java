package com.korit.feelioapi.domain.goal.controller;

import com.korit.feelioapi.domain.goal.dto.GoalDeleteResponse;
import com.korit.feelioapi.domain.goal.dto.GoalListResponse;
import com.korit.feelioapi.domain.goal.dto.GoalRequest;
import com.korit.feelioapi.domain.goal.dto.GoalResponse;
import com.korit.feelioapi.domain.goal.service.GoalService;
import com.korit.feelioapi.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 목표 API (API-CONTRACT §7). 인증 필요, 모든 접근은 토큰 주체 user_id 기준.
 */
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    /** GET /api/goals — 내 목표 목록. */
    @GetMapping
    public ApiResponse<GoalListResponse> getGoals(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(goalService.getGoals(userId));
    }

    /** POST /api/goals — 목표 생성(isMain=true 면 기존 대표 해제). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GoalResponse> createGoal(@AuthenticationPrincipal Long userId,
                                                @Valid @RequestBody GoalRequest request) {
        return ApiResponse.success(goalService.createGoal(userId, request));
    }

    /** PUT /api/goals/{goalId} — 목표 수정(POST와 동일 필드). */
    @PutMapping("/{goalId}")
    public ApiResponse<GoalResponse> updateGoal(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long goalId,
                                                @Valid @RequestBody GoalRequest request) {
        return ApiResponse.success(goalService.updateGoal(userId, goalId, request));
    }

    /** DELETE /api/goals/{goalId} — 목표 삭제. */
    @DeleteMapping("/{goalId}")
    public ApiResponse<GoalDeleteResponse> deleteGoal(@AuthenticationPrincipal Long userId,
                                                      @PathVariable Long goalId) {
        return ApiResponse.success(goalService.deleteGoal(userId, goalId));
    }
}
