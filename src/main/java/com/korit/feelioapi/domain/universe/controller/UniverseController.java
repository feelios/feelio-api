package com.korit.feelioapi.domain.universe.controller;

import com.korit.feelioapi.domain.universe.dto.UniverseResponse;
import com.korit.feelioapi.domain.universe.service.UniverseService;
import com.korit.feelioapi.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 평행우주 API (API-CONTRACT §9). 인증 필요, 토큰 주체 user_id 기준.
 */
@RestController
@RequestMapping("/api/universe")
@RequiredArgsConstructor
public class UniverseController {

    private final UniverseService universeService;

    /** GET /api/universe/simulation?goalId — 목표 기반 두 미래 시나리오. goalId 누락 시 VALIDATION_ERROR. */
    @GetMapping("/simulation")
    public ApiResponse<UniverseResponse> simulate(@AuthenticationPrincipal Long userId,
                                                  @RequestParam(required = false) Long goalId) {
        return ApiResponse.success(universeService.simulate(userId, goalId));
    }
}
