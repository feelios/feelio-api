package com.korit.feelioapi.domain.analysis.controller;

import com.korit.feelioapi.domain.analysis.dto.AnalysisResponse;
import com.korit.feelioapi.domain.analysis.service.AnalysisService;
import com.korit.feelioapi.global.response.ApiResponse;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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

    /** GET /api/analysis/ai-insights — AI 인사이트 분석 Mock API. */
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
    @Value("${openai.key}")
    private String openAiKey;

    @PostMapping("/ai")
    public ResponseEntity<?> chat(@RequestParam String value) {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(openAiKey)
                .build();

        ResponseCreateParams params =
                ResponseCreateParams.builder().input(value).model("gpt-4o-mini").build();

        Response response = client.responses().create(params);
        List<String> outputTexts = response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text()).toList();

        return ResponseEntity.ok(ApiResponse.success(outputTexts));
    }
}
