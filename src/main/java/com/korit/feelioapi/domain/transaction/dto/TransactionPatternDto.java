package com.korit.feelioapi.domain.transaction.dto;

public record TransactionPatternDto(
        int count,
        String title,
        String emotion,
        String category,
        String time,
        String desc
) {}
