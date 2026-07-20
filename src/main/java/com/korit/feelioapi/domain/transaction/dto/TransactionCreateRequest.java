package com.korit.feelioapi.domain.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record TransactionCreateRequest(
        @NotBlank String type,
        @NotNull @Positive Integer amount,
        @NotNull Long categoryId,
        @NotNull Long emotionId,
        String memo,
        @NotNull @PastOrPresent LocalDateTime occurredAt,
        Long goalId
) {}
