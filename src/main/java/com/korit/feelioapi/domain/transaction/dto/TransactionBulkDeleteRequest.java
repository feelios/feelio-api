package com.korit.feelioapi.domain.transaction.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record TransactionBulkDeleteRequest(
        @NotEmpty(message = "삭제할 거래내역 ID 목록은 비어있을 수 없습니다.")
        List<Long> transactionIds
) {}
