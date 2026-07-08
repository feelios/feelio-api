package com.korit.feelioapi.domain.goal.dto;

import java.util.List;

/**
 * GET /api/goals 응답 data (API-CONTRACT §7). isMain 은 항상 최대 1건.
 */
public record GoalListResponse(
        List<GoalResponse> goals
) {
}
