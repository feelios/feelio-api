package com.korit.feelioapi.domain.transaction.service;

import com.korit.feelioapi.domain.transaction.dto.TransactionCreateRequest;
import com.korit.feelioapi.domain.transaction.dto.TransactionDeleteResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionDto;
import com.korit.feelioapi.domain.transaction.dto.TransactionListResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionSearchCondition;
import com.korit.feelioapi.domain.transaction.dto.TransactionTotalDto;
import com.korit.feelioapi.domain.transaction.entity.Transaction;
import com.korit.feelioapi.domain.transaction.mapper.TransactionMapper;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionMapper transactionMapper;

    @Transactional(readOnly = true)
    public TransactionListResponse getTransactions(Long userId, TransactionSearchCondition condition) {
        List<TransactionDto> transactions = transactionMapper.findTransactions(userId, condition);
        TransactionTotalDto totals = transactionMapper.calculateTotals(userId, condition);

        return new TransactionListResponse(
                transactions,
                totals.totalIncome(),
                totals.totalExpense()
        );
    }

    @Transactional
    public TransactionDto createTransaction(Long userId, TransactionCreateRequest request) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setEmotionId(request.emotionId());
        transaction.setCategoryId(request.categoryId());
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setMemo(request.memo());
        transaction.setOccurredAt(request.occurredAt());

        transactionMapper.insertTransaction(transaction);

        return transactionMapper.findTransactionById(transaction.getTransactionId(), userId);
    }

    @Transactional(readOnly = true)
    public TransactionDto getTransaction(Long userId, Long transactionId) {
        getOwnedOrThrow(userId, transactionId);
        return transactionMapper.findTransactionById(transactionId, userId);
    }

    @Transactional
    public TransactionDto updateTransaction(Long userId, Long transactionId, TransactionCreateRequest request) {
        getOwnedOrThrow(userId, transactionId);

        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setEmotionId(request.emotionId());
        transaction.setCategoryId(request.categoryId());
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setMemo(request.memo());
        transaction.setOccurredAt(request.occurredAt());
        transactionMapper.updateTransaction(transaction);

        return transactionMapper.findTransactionById(transactionId, userId);
    }

    @Transactional
    public TransactionDeleteResponse deleteTransaction(Long userId, Long transactionId) {
        getOwnedOrThrow(userId, transactionId);
        transactionMapper.deleteTransaction(transactionId);
        return new TransactionDeleteResponse(true);
    }

    /** 대상 존재 + 본인 소유 검증 (계약 §6: 없음 NOT_FOUND / 타인 FORBIDDEN). */
    private Transaction getOwnedOrThrow(Long userId, Long transactionId) {
        Transaction transaction = transactionMapper.findById(transactionId);
        if (transaction == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!transaction.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return transaction;
    }
}
