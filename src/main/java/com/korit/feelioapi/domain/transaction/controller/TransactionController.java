package com.korit.feelioapi.domain.transaction.controller;

import com.korit.feelioapi.domain.transaction.dto.TransactionCreateRequest;
import com.korit.feelioapi.domain.transaction.dto.TransactionDeleteResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionDto;
import com.korit.feelioapi.domain.transaction.dto.TransactionListResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionResetResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionSearchCondition;
import com.korit.feelioapi.domain.transaction.service.TransactionService;
import com.korit.feelioapi.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    /** GET /api/transactions/{transactionId} — 거래 상세 조회. 인증 필요. */
    @GetMapping("/{transactionId}")
    public ApiResponse<TransactionDto> getTransaction(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long transactionId
    ) {
        return ApiResponse.success(transactionService.getTransaction(userId, transactionId));
    }

    /** PUT /api/transactions/{transactionId} — 거래 수정(POST와 동일 필드). 인증 필요. */
    @PutMapping("/{transactionId}")
    public ApiResponse<TransactionDto> updateTransaction(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long transactionId,
            @Valid @RequestBody TransactionCreateRequest request
    ) {
        return ApiResponse.success(transactionService.updateTransaction(userId, transactionId, request));
    }

    /** DELETE /api/transactions/{transactionId} — 거래 기록 삭제. 인증 필요. */
    @DeleteMapping("/{transactionId}")
    public ApiResponse<TransactionDeleteResponse> deleteTransaction(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long transactionId
    ) {
        return ApiResponse.success(transactionService.deleteTransaction(userId, transactionId));
    }

    /** DELETE /api/transactions/bulk — 다중 거래 기록 삭제. 인증 필요. */
    @DeleteMapping("/bulk")
    public ApiResponse<TransactionDeleteResponse> deleteTransactionsBulk(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody com.korit.feelioapi.domain.transaction.dto.TransactionBulkDeleteRequest request
    ) {
        return ApiResponse.success(transactionService.deleteTransactionsBulk(userId, request.transactionIds()));
    }

    /** DELETE /api/transactions — 전체 초기화. 인증 필요. */
    @DeleteMapping
    public ApiResponse<TransactionResetResponse> resetTransactions(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(transactionService.resetTransactions(userId));
    }
}
