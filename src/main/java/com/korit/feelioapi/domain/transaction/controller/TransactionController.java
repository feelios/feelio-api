package com.korit.feelioapi.domain.transaction.controller;

import com.korit.feelioapi.domain.transaction.dto.TransactionCreateRequest;
import com.korit.feelioapi.domain.transaction.dto.TransactionDto;
import com.korit.feelioapi.domain.transaction.dto.TransactionListResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionSearchCondition;
import com.korit.feelioapi.domain.transaction.service.TransactionService;
import com.korit.feelioapi.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;

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

    /** POST /api/transactions — 거래 기록 생성. 인증 필요. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionDto> createTransaction(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TransactionCreateRequest request
    ) {
        return ApiResponse.success(transactionService.createTransaction(userId, request));
    }

    /** DELETE /api/transactions/{transactionId} — 거래 기록 삭제. 인증 필요. */
    @DeleteMapping("/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransaction(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long transactionId
    ) {
        transactionService.deleteTransaction(userId, transactionId);
    }
}
