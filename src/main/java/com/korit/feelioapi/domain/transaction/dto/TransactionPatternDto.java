package com.korit.feelioapi.domain.transaction.dto;

import java.util.List;

public record TransactionPatternDto(
        int count,
        String title,
        String emotion,
        String category,
        String time,
        String desc,
        List<TransactionDto> evidence
) {}
