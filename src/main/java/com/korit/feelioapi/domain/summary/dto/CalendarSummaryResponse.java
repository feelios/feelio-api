package com.korit.feelioapi.domain.summary.dto;

import java.util.List;

public record CalendarSummaryResponse(
        List<CalendarDayDto> days
) {}
