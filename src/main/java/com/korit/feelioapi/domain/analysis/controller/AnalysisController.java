package com.korit.feelioapi.domain.analysis.controller;

import com.korit.feelioapi.domain.analysis.dto.AnalysisResponse;
import com.korit.feelioapi.domain.analysis.service.AnalysisService;
import com.korit.feelioapi.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 분석 API (API-CONTRACT §9). 인증 필요, 토큰 주체 user_id 기준.
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    /** GET /api/analysis/monthly?year&month — 월간 분석(집계 + 인사이트). */
    @GetMapping("/monthly")
    public ApiResponse<AnalysisResponse> getMonthly(@AuthenticationPrincipal Long userId,
                                                    @RequestParam int year,
                                                    @RequestParam int month) {
        return ApiResponse.success(analysisService.getMonthlyAnalysis(userId, year, month));
    }

    /** GET /api/analysis/ai-insights — 당월 집계 기반 AI 요약 카드·감정 카드 문구 (계약 §9). */
    @GetMapping("/ai-insights")
    public ApiResponse<com.korit.feelioapi.domain.analysis.dto.AiInsightsResponse> getAiInsights(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(analysisService.getAiInsights(userId));
    }

    /** GET /api/analysis/trend — 최근 7개월 지출 추이. */
    @GetMapping("/trend")
    public ApiResponse<com.korit.feelioapi.domain.analysis.dto.MonthlyTrendResponse> getMonthlyTrend(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(analysisService.getMonthlyTrend(userId));
    }

    /** GET /api/analysis/budget — 목표 예산 현황 API. */
    @GetMapping("/budget")
    public ApiResponse<com.korit.feelioapi.domain.analysis.dto.BudgetStatusResponse> getBudgetStatus(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(analysisService.getBudgetStatus(userId));
    }
    @PostMapping("/ai")
    public ApiResponse<List<String>> chat(@RequestParam String value) {
        return ApiResponse.success(analysisService.getAiChatResponse(value));
    }
}
