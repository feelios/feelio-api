package com.korit.feelioapi.domain.transaction.dto;

import java.util.List;

public record TransactionSearchCondition(
        Integer year,
        Integer month,
        Integer day,
        List<Long> emotionId,
        List<Long> categoryId,
        String query,
        String sort
) {}
