package com.korit.feelioapi.domain.user.dto;

/**
 * PATCH /api/users/me/onboarding 응답 data (API-CONTRACT §4).
 * { "onboardingDone": true }
 */
public record OnboardingResponse(
        boolean onboardingDone
) {
}
