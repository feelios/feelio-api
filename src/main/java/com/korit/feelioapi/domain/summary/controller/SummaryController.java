package com.korit.feelioapi.domain.summary.controller;

import com.korit.feelioapi.domain.summary.dto.CalendarSummaryResponse;
import com.korit.feelioapi.domain.summary.dto.EmotionSummaryResponse;
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
}
