package com.korit.feelioapi.domain.transaction.dto;

import java.time.LocalDateTime;

public record TransactionDto(
        Long transactionId,
        String type,
        Integer amount,
        CategoryDto category,
        EmotionDto emotion,
        String memo,
        LocalDateTime occurredAt
) {}
