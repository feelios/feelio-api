package com.korit.feelioapi.domain.goal.dto;

/**
 * DELETE /api/goals/{goalId} 응답 data (API-CONTRACT §7).
 * { "deleted": true }
 */
public record GoalDeleteResponse(
        boolean deleted
) {
}
