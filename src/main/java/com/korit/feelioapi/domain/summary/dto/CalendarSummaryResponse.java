package com.korit.feelioapi.domain.summary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarSummaryResponse {
    private List<CalendarDayDto> days;
}
