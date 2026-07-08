package com.korit.feelioapi.domain.transaction.controller;

import com.korit.feelioapi.domain.transaction.dto.TransactionListResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionSearchCondition;
import com.korit.feelioapi.domain.transaction.service.TransactionService;
import com.korit.feelioapi.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /** GET /api/transactions — 거래 목록 조회. 인증 필요. */
    @GetMapping
    public ApiResponse<TransactionListResponse> getTransactions(
            @AuthenticationPrincipal Long userId,
            @ModelAttribute TransactionSearchCondition condition
    ) {
        return ApiResponse.success(transactionService.getTransactions(userId, condition));
    }
}
