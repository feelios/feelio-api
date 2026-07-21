package com.korit.feelioapi.domain.transaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TransactionMergeRequest(
        @NotNull(message = "정산받은 금액은 필수입니다.")
        @PositiveOrZero(message = "정산받은 금액은 0 이상이어야 합니다.")
        Integer receivedAmount
) {}
