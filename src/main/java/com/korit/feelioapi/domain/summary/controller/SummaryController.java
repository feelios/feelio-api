package com.korit.feelioapi.domain.summary.controller;

import com.korit.feelioapi.domain.summary.dto.CalendarSummaryResponse;
import com.korit.feelioapi.domain.summary.dto.EmotionSummaryResponse;
import com.korit.feelioapi.domain.summary.dto.MallangCommentResponse;
import com.korit.feelioapi.domain.summary.dto.SummaryAiCommentResponse;
import com.korit.feelioapi.domain.summary.service.SummaryService;
import com.korit.feelioapi.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summary")
@RequiredArgsConstructor
public class SummaryController {

    private final SummaryService summaryService;

    /** GET /api/summary/calendar?year&month — 홈 캘린더 요약. 인증 필요. */
    @GetMapping("/calendar")
    public ApiResponse<CalendarSummaryResponse> getCalendarSummary(
            @AuthenticationPrincipal Long userId,
            @RequestParam Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return ApiResponse.success(summaryService.getCalendarSummary(userId, year, month));
    }

    /** GET /api/summary/emotions?year&month — 감정 요약. 인증 필요. */
    @GetMapping("/emotions")
    public ApiResponse<EmotionSummaryResponse> getEmotionSummary(
            @AuthenticationPrincipal Long userId,
            @RequestParam Integer year,
            @RequestParam Integer month
    ) {
        return ApiResponse.success(summaryService.getEmotionSummary(userId, year, month));
    }

    /** GET /api/summary/mallang-comment — 홈 말랑이 코멘트(평가 + 독려). 인증 필요. */
    @GetMapping("/mallang-comment")
    public ApiResponse<MallangCommentResponse> getMallangComment(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(summaryService.getMallangComment(userId));
    }

    /** GET /api/summary/ai-comment — 홈과 독립적으로 늦게 채우는 당월 소비 AI 멘트. */
    @GetMapping("/ai-comment")
    public ApiResponse<SummaryAiCommentResponse> getAiComment(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(summaryService.getAiComment(userId));
    }
}
